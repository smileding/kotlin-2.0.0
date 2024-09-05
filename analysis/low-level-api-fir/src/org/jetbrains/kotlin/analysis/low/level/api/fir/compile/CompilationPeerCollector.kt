/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.compile

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.getContainingFile
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.contracts.FirContractDescription
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.hasBody
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirResolvable
import org.jetbrains.kotlin.fir.expressions.impl.FirContractCallBlock
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.lazyResolveToPhase
import org.jetbrains.kotlin.fir.unwrapSubstitutionOverrides
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * This exception indicates that two or more source modules contain inline functions, and they reference each other.
 * When we run the backend JVM IR CodeGen, it has to fill the inline function from source modules. If we have a cyclic
 * dependency between inline functions, the backend cannot fill them. For example, inline function A needs inline
 * function B's JVM IR, but B also needs A's JVM IR. In this case, it will throw this exception.
 */
class CyclicInlineDependencyException(message: String) : IllegalStateException(message)

/**
 * Processes the declaration, collecting files that would need to be submitted to the backend (or handled specially)
 * in case if the declaration is compiled.
 *
 * Besides the file that owns the declaration, the visitor also recursively collects source files with called inline functions.
 * In addition, the visitor collects a list of inlined local classes. Such a list might be useful in [GenerationState.GenerateClassFilter]
 * to filter out class files unrelated to the current compilation.
 *
 * Note that compiled declarations are not analyzed, as the backend can inline them natively.
 */
object CompilationPeerCollector {
    fun process(declaration: FirDeclaration): CompilationPeerData {
        return processWithRecursiveInlineDependency(declaration, HashSet(), HashSet())
    }

    private fun processWithRecursiveInlineDependency(
        declaration: FirDeclaration,
        visited: HashSet<FirDeclaration>,
        collected: HashSet<FirDeclaration>,
    ): CompilationPeerData {
        val visitor = CompilationPeerCollectingVisitor()
        visitor.process(declaration)
        visited.add(declaration)
        val dependencyPeerData = visitor.dependencyFilesContainingInlineFunction.mapNotNull { file ->
            if (file in collected) return@mapNotNull null
            if (file in visited) {
                throw CyclicInlineDependencyException("Source library modules containing inline functions have a cyclic dependency:\n${
                    visited.map { it.render() }
                }")
            }
            processWithRecursiveInlineDependency(file, visited, collected)
        }

        collected.add(declaration)
        val files = (dependencyPeerData.flatMap { it.files } + visitor.files).toSet()
        val inlinedClasses = (dependencyPeerData.flatMap { it.inlinedClasses } + visitor.inlinedClasses).toSet()
        val dependencyFilesForDeclaration = visitor.dependencyFilesContainingInlineFunction.mapNotNull { it.psi as? KtFile }
        return CompilationPeerData(files.toList(),
                                   inlinedClasses,
                                   dependencyPeerData.flatMap { it.dependencyFilesContainingInlineFunction } + dependencyFilesForDeclaration)
    }
}

class CompilationPeerData(
    /** File with the original declaration and all files with called inline functions. */
    val files: List<KtFile>,

    /** Local classes inlined as a part of inline functions. */
    val inlinedClasses: Set<KtClassOrObject>,

    /**
     * Files containing inline functions that are declared in source library modules. [CompilationPeerCollector.process] recursively
     * collects them and keeps them in [dependencyFilesContainingInlineFunction] in a post order. For example,
     *  - A is main source module. A has dependency on source module libraries B and C.
     *  - B contains an inline function. B has dependency on a source module library C.
     *  - C contains an inline function.
     *  - [dependencyFilesContainingInlineFunction] will be {C, B, A}.
     *
     * i-th element of [dependencyFilesContainingInlineFunction] will not have dependency on any j-th element of
     * [dependencyFilesContainingInlineFunction], where j > i.
     */
    val dependencyFilesContainingInlineFunction: List<KtFile>
)

private class CompilationPeerCollectingVisitor : FirDefaultVisitorVoid() {
    private val processed = mutableSetOf<FirDeclaration>()
    private val queue = ArrayDeque<FirDeclaration>()

    private val collectedFiles = mutableSetOf<KtFile>()
    private val collectedInlinedClasses = mutableSetOf<KtClassOrObject>()
    private var isInlineFunctionContext = false
    private lateinit var targetFir: FirDeclaration
    private val dependencyFilesContainingInline = mutableSetOf<FirFile>()

    val files: List<KtFile>
        get() = collectedFiles.toList()

    val inlinedClasses: Set<KtClassOrObject>
        get() = collectedInlinedClasses

    val dependencyFilesContainingInlineFunction: List<FirFile>
        get() = dependencyFilesContainingInline.toList()

    fun process(declaration: FirDeclaration) {
        targetFir = declaration

        processSingle(declaration)

        while (queue.isNotEmpty()) {
            processSingle(queue.removeFirst())
        }
    }

    private fun processSingle(declaration: FirDeclaration) {
        ProgressManager.checkCanceled()

        if (processed.add(declaration)) {
            val containingFile = declaration.psi?.containingFile
            if (containingFile is KtFile && !containingFile.isCompiled) {
                collectedFiles.add(containingFile)
                declaration.accept(this)
            }
        }
    }

    override fun visitElement(element: FirElement) {
        if (element is FirResolvable) {
            processResolvable(element)
        }

        element.acceptChildren(this)
    }

    override fun visitBlock(block: FirBlock) {
        if (block !is FirContractCallBlock) {
            super.visitBlock(block)
        }
    }

    override fun visitContractDescription(contractDescription: FirContractDescription) {
        // Skip contract description.
        // Contract blocks are skipped in BE, so we would never need to inline contract DSL calls.
    }

    override fun visitConstructor(constructor: FirConstructor) {
        constructor.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)

        super.visitConstructor(constructor)
    }

    override fun visitSimpleFunction(simpleFunction: FirSimpleFunction) {
        simpleFunction.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)

        val oldIsInlineFunctionContext = isInlineFunctionContext
        try {
            isInlineFunctionContext = simpleFunction.isInline
            super.visitFunction(simpleFunction)
        } finally {
            isInlineFunctionContext = oldIsInlineFunctionContext
        }
    }

    override fun visitProperty(property: FirProperty) {
        property.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)

        super.visitProperty(property)
    }

    override fun visitClass(klass: FirClass) {
        super.visitClass(klass)

        if (isInlineFunctionContext) {
            collectedInlinedClasses.addIfNotNull(klass.psi as? KtClassOrObject)
        }
    }

    override fun visitFunctionCall(functionCall: FirFunctionCall) {
        super.visitFunctionCall(functionCall)
        val symbol = functionCall.calleeReference.symbol as? FirNamedFunctionSymbol ?: return
        collectKtFileContainingInlineFunction(symbol.fir)
    }

    override fun visitCallableReferenceAccess(callableReferenceAccess: FirCallableReferenceAccess) {
        super.visitCallableReferenceAccess(callableReferenceAccess)
        val symbol = callableReferenceAccess.calleeReference.symbol as? FirNamedFunctionSymbol ?: return
        collectKtFileContainingInlineFunction(symbol.fir)
    }

    private fun collectKtFileContainingInlineFunction(fir: FirFunction) {
        if (fir.isInline) {
            // If fir and targetFir are in the same module, fir is not in a dependency.
            assert(::targetFir.isInitialized)
            if (fir.moduleData == targetFir.moduleData) return

            fir.getContainingFile()?.let { dependencyFilesContainingInline.add(it) }
        }
    }

    @OptIn(SymbolInternals::class)
    private fun processResolvable(element: FirResolvable) {
        fun addToQueue(function: FirFunction?) {
            val original = function?.unwrapSubstitutionOverrides() ?: return
            if (original.isInline && original.hasBody) {
                queue.add(function)
            }
        }

        val reference = element.calleeReference
        if (reference !is FirResolvedNamedReference) {
            return
        }

        val symbol = reference.resolvedSymbol
        if (symbol is FirCallableSymbol<*>) {
            when (val fir = symbol.fir) {
                is FirFunction -> {
                    addToQueue(fir)
                }
                is FirProperty -> {
                    addToQueue(fir.getter)
                    addToQueue(fir.setter)
                }
                else -> {}
            }
        }
    }
}