/*
 * Copyright 2023-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Copyright (C) 2020-2023 Brian Norman
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.jetbrains.kotlin.powerassert

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.parentClassId
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.powerassert.builder.call.CallBuilder
import org.jetbrains.kotlin.powerassert.builder.call.LambdaCallBuilder
import org.jetbrains.kotlin.powerassert.builder.call.SamConversionLambdaCallBuilder
import org.jetbrains.kotlin.powerassert.builder.call.SimpleCallBuilder
import org.jetbrains.kotlin.powerassert.builder.explanation.*
import org.jetbrains.kotlin.powerassert.diagram.*

class PowerAssertCallTransformer(
    private val sourceFile: SourceFile,
    private val context: IrPluginContext,
    private val configuration: PowerAssertConfiguration,
    private val builtIns: PowerAssertBuiltIns,
    private val factory: ExplainCallFunctionFactory,
) : IrElementTransformerVoidWithContext() {
    private val irTypeSystemContext = IrTypeSystemContextImpl(context.irBuiltIns)
    private val explanationFactory = ExplanationFactory(builtIns)

    private class PowerAssertScope(scope: Scope, irElement: IrElement) : ScopeWithIr(scope, irElement) {
        val variables = mutableMapOf<IrVariable, IrVariable>()
    }

    override fun createScope(declaration: IrSymbolOwner): ScopeWithIr {
        return PowerAssertScope(Scope(declaration.symbol), declaration)
    }

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        super.visitFunctionNew(declaration)

        // TODO !!! HACK? !!! this works but is really ugly how the temporary variables get added to the scope
        //  Maybe local variable scoping should be done via a pre-pass?
        //  What about loops which contain variables?
        val scope = currentScope as PowerAssertScope
        val body = declaration.body
        if (body is IrBlockBody) {
            for (variable in scope.variables.values.reversed()) {
                body.statements.add(0, variable)
            }
        }

        return declaration
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        declaration.transformChildrenVoid()

        if (
            !declaration.hasAnnotation(builtIns.explainClass) &&
            (declaration.parent as? IrAnnotationContainer)?.hasAnnotation(builtIns.explainClass) != true
            // TODO inherit through lambda type argument?
        ) return declaration

        // TODO FIR checks
        if (declaration.initializer == null) {
            configuration.messageCollector.warn(element = declaration, message = "Variable annotated with @Explain must have an initializer.")
            return declaration
        }
        if (declaration.isVar) {
            configuration.messageCollector.warn(element = declaration, message = "Variable annotated with @Explain must be val.")
            return declaration
        }

        val diagramVariable = buildForAnnotated(declaration) ?: return declaration

        val variables = (currentScope as PowerAssertScope).variables
        variables.put(declaration, diagramVariable)
        return declaration
    }

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid()

        val function = expression.symbol.owner
        val fqName = function.kotlinFqName
        return when {
            function.valueParameters.isEmpty() -> expression
            function.hasAnnotation(builtIns.explainCallClass) -> buildForAnnotated(expression, function)
            fqName in configuration.functions -> buildForOverride(expression, function)
            else -> expression
        }
    }

    private fun buildForAnnotated(
        originalCall: IrCall,
        function: IrSimpleFunction,
    ): IrExpression {
        val synthetic = factory.find(function)
        if (synthetic == null) {
            configuration.messageCollector.warn(
                element = originalCall,
                message = "Called function '${function.kotlinFqName}' was not compiled with the power-assert compiler-plugin.",
            )
            return originalCall
        }

        val variableDiagrams = allScopes.mapNotNull { it as? PowerAssertScope }.flatMap { it.variables.entries }.associate { it.toPair() }
        val diagramBuilder = CallDiagramParameterBuilder(explanationFactory, sourceFile, originalCall, variableDiagrams)
        val callBuilder = SimpleCallBuilder(synthetic, originalCall)
        return buildPowerAssertCall(originalCall, function, callBuilder, diagramBuilder)
    }

    private fun buildForAnnotated(
        variable: IrVariable,
    ): IrVariable? {
        val initializer = variable.initializer!!

        val expressionRoot = buildTree(parameter = null, initializer).child
        if (expressionRoot == null) {
            configuration.messageCollector.info(initializer, "Expression is constant and will not be power-assert transformed")
            return null
        }

        val currentScope = currentScope!!
        val builder = DeclarationIrBuilder(context, currentScope.scope.scopeOwnerSymbol, initializer.startOffset, initializer.endOffset)

        val diagramVariable = currentScope.scope.createTemporaryVariableDeclaration(
            nameHint = variable.name.asString() + "Explanation",
            irType = builtIns.variableExplanationType,
            origin = EXPLANATION,
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
        )

        val variableDiagrams = allScopes.mapNotNull { it as? PowerAssertScope }.flatMap { it.variables.entries }.associate { it.toPair() }
        variable.initializer = builder.buildDiagramNesting(sourceFile, expressionRoot) { argument, newVariables ->
            +irSet(diagramVariable, builder.irVariableExplanation(explanationFactory, sourceFile, variable, newVariables, variableDiagrams))
            argument
        }

        return diagramVariable
    }

    private fun buildForOverride(originalCall: IrCall, function: IrSimpleFunction): IrExpression {
        // Find a valid delegate function or do not translate
        // TODO better way to determine which delegate to actually use
        val callBuilders = findCallBuilders(function, originalCall)
        val callBuilder = callBuilders.maxByOrNull { it.function.valueParameters.size }
        if (callBuilder == null) {
            val fqName = function.kotlinFqName
            val valueTypesTruncated = function.valueParameters.subList(0, function.valueParameters.size - 1)
                .joinToString("") { it.type.render() + ", " }
            val valueTypesAll = function.valueParameters.joinToString("") { it.type.render() + ", " }
            configuration.messageCollector.warn(
                element = originalCall,
                message = """
              |Unable to find overload of function $fqName for power-assert transformation callable as:
              | - $fqName(${valueTypesTruncated}String)
              | - $fqName($valueTypesTruncated() -> String)
              | - $fqName(${valueTypesAll}String)
              | - $fqName($valueTypesAll() -> String)
            """.trimMargin(),
            )
            return originalCall
        }

        val messageArgument = when (callBuilder.function.valueParameters.size) {
            function.valueParameters.size -> originalCall.getValueArgument(originalCall.valueArgumentsCount - 1)
            else -> null
        }
        val variableDiagrams = allScopes.mapNotNull { it as? PowerAssertScope }.flatMap { it.variables.entries }.associate { it.toPair() }
        val diagramBuilder = CallDiagramParameterBuilder(explanationFactory, sourceFile, originalCall, variableDiagrams)
        val stringBuilder = StringParameterBuilder(explanationFactory, function, messageArgument, diagramBuilder)
        return buildPowerAssertCall(originalCall, function, callBuilder, stringBuilder)
    }

    private fun buildPowerAssertCall(
        originalCall: IrCall,
        function: IrSimpleFunction,
        callBuilder: CallBuilder,
        parameterBuilder: ParameterBuilder,
    ): IrExpression {
        val dispatchRoot = function.dispatchReceiverParameter?.let { getRootNode(it, originalCall.dispatchReceiver) }
        val extensionRoot = function.extensionReceiverParameter?.let { getRootNode(it, originalCall.extensionReceiver) }
        val argumentRoots = when (callBuilder.function.valueParameters.size) {
            function.valueParameters.size -> {
                (0 until originalCall.valueArgumentsCount - 1)
                    .map { getRootNode(function.valueParameters[it], originalCall.getValueArgument(it)) }
            }
            else -> {
                (0 until originalCall.valueArgumentsCount)
                    .map { getRootNode(function.valueParameters[it], originalCall.getValueArgument(it)) }
            }
        }

        // If all roots are null, there are no transformable parameters
        if (dispatchRoot?.child == null && extensionRoot?.child == null && argumentRoots.all { it.child == null }) {
            configuration.messageCollector.info(originalCall, "Expression is constant and will not be power-assert transformed")
            return originalCall
        }

        val symbol = currentScope!!.scope.scopeOwnerSymbol
        val builder = DeclarationIrBuilder(context, symbol, originalCall.startOffset, originalCall.endOffset)
        return builder.diagram(
            originalCall = originalCall,
            callBuilder = callBuilder,
            parameterBuilder = parameterBuilder,
            dispatchRoot = dispatchRoot,
            extensionRoot = extensionRoot,
            argumentRoots = argumentRoots,
        )
    }

    private fun <T> buildTree(parameter: T, argument: IrExpression?): RootNode<T> {
        return buildTree(configuration.constTracker, sourceFile, parameter, argument)
    }

    private fun getRootNode(parameter: IrValueParameter, argument: IrExpression?): RootNode<IrValueParameter> {
        // Check if the parameter or parameter type should be ignored.
        if (
            parameter.hasAnnotation(builtIns.explainIgnoreClass) ||
            parameter.type.getClass()?.hasAnnotation(builtIns.explainIgnoreClass) == true
        ) return RootNode(parameter)

        return buildTree(parameter, argument)
    }

    private fun DeclarationIrBuilder.diagram(
        originalCall: IrCall,
        callBuilder: CallBuilder,
        parameterBuilder: ParameterBuilder,
        dispatchRoot: RootNode<IrValueParameter>? = null,
        extensionRoot: RootNode<IrValueParameter>? = null,
        argumentRoots: List<RootNode<IrValueParameter>>,
    ): IrExpression {
        return buildReceiverDiagram(sourceFile, dispatchRoot?.child) { dispatch, dispatchVariables ->
            buildReceiverDiagram(sourceFile, extensionRoot?.child) { extension, extensionVariables ->

                fun recursive(
                    index: Int,
                    arguments: PersistentList<IrExpression?>,
                    argumentVariables: PersistentMap<String, List<IrTemporaryVariable>>,
                ): IrExpression {
                    if (index >= argumentRoots.size) {
                        val diagram = parameterBuilder.build(this, dispatchVariables, extensionVariables, argumentVariables)
                        return callBuilder.buildCall(this, dispatch, extension, arguments, diagram)
                    } else {
                        val root = argumentRoots[index]
                        val child = root.child
                        val name = root.parameter.name.toString()
                        if (child == null) {
                            val newArguments = arguments.add(originalCall.getValueArgument(index))
                            val newArgumentVariables = argumentVariables
                            return recursive(index + 1, newArguments, newArgumentVariables)
                        } else {
                            return buildDiagramNesting(sourceFile, child) { argument, newVariables ->
                                val newArguments = arguments.add(argument)
                                val newArgumentVariables = argumentVariables.put(name, newVariables)
                                recursive(index + 1, newArguments, newArgumentVariables)
                            }
                        }
                    }
                }

                recursive(0, persistentListOf(), persistentMapOf())
            }
        }
    }

    private fun findCallBuilders(function: IrFunction, original: IrCall): List<CallBuilder> {
        val values = function.valueParameters
        if (values.isEmpty()) return emptyList()

        // Java static functions require searching by class
        val parentClassFunctions = (
                function.parentClassId
                    ?.let { context.referenceClass(it) }
                    ?.functions ?: emptySequence()
                )
            .filter { it.owner.kotlinFqName == function.kotlinFqName }
            .toList()
        val possible = (context.referenceFunctions(function.callableId) + parentClassFunctions)
            .distinct()

        return possible.mapNotNull { overload ->
            // Type parameters must be compatible.
            if (function.typeParameters.size != overload.owner.typeParameters.size) {
                return@mapNotNull null
            }
            for (i in function.typeParameters.indices) {
                val functionSuperTypes = function.typeParameters[i].superTypes
                val overloadDefaultType = overload.owner.typeParameters[i].defaultType
                if (functionSuperTypes.any { !it.isAssignableTo(overloadDefaultType) }) {
                    return@mapNotNull null
                }
            }

            fun IrType.remap(): IrType = remapTypeParameters(overload.owner, function)

            // Dispatch receivers must always match exactly
            if (function.dispatchReceiverParameter?.type != overload.owner.dispatchReceiverParameter?.type?.remap()) {
                return@mapNotNull null
            }

            // Extension receiver may only be assignable
            if (!function.extensionReceiverParameter?.type.isAssignableTo(overload.owner.extensionReceiverParameter?.type?.remap())) {
                return@mapNotNull null
            }

            val parameters = overload.owner.valueParameters
            if (parameters.size !in values.size..values.size + 1) return@mapNotNull null
            for (i in values.indices) {
                if (!values[i].type.isAssignableTo(parameters[i].type.remap())) {
                    return@mapNotNull null
                }
            }

            val messageType = parameters.last().type
            return@mapNotNull when {
                isStringSupertype(messageType) -> SimpleCallBuilder(overload, original)
                isStringFunction(messageType) -> LambdaCallBuilder(overload, original, messageType)
                isStringJavaSupplierFunction(messageType) -> SamConversionLambdaCallBuilder(overload, original, messageType)
                else -> null
            }
        }
    }

    private fun isStringFunction(type: IrType): Boolean =
        type.isFunctionOrKFunction() && type is IrSimpleType && (type.arguments.size == 1 && isStringSupertype(type.arguments.first()))

    private fun isStringJavaSupplierFunction(type: IrType): Boolean {
        val javaSupplier = context.referenceClass(ClassId.topLevel(FqName("java.util.function.Supplier")))
        return javaSupplier != null && type.isSubtypeOfClass(javaSupplier) &&
                type is IrSimpleType && (type.arguments.size == 1 && isStringSupertype(type.arguments.first()))
    }

    private fun isStringSupertype(argument: IrTypeArgument): Boolean =
        argument is IrTypeProjection && isStringSupertype(argument.type)

    private fun isStringSupertype(type: IrType): Boolean =
        context.irBuiltIns.stringType.isSubtypeOf(type, irTypeSystemContext)

    private fun IrType?.isAssignableTo(type: IrType?): Boolean {
        if (this != null && type != null) {
            if (isSubtypeOf(type, irTypeSystemContext)) return true
            val superTypes = (type.classifierOrNull as? IrTypeParameterSymbol)?.owner?.superTypes
            return superTypes != null && superTypes.all { isSubtypeOf(it, irTypeSystemContext) }
        } else {
            return this == null && type == null
        }
    }

    private fun MessageCollector.info(element: IrElement, message: String) {
        report(element, CompilerMessageSeverity.INFO, message)
    }

    private fun MessageCollector.warn(element: IrElement, message: String) {
        report(element, CompilerMessageSeverity.WARNING, message)
    }

    private fun MessageCollector.report(element: IrElement, severity: CompilerMessageSeverity, message: String) {
        report(severity, message, sourceFile.getCompilerMessageLocation(element))
    }
}

val IrFunction.callableId: CallableId
    get() {
        val parentClass = parent as? IrClass
        val classId = parentClass?.classId
        return if (classId != null && !parentClass.isFileClass) {
            CallableId(classId, name)
        } else {
            CallableId(parent.kotlinFqName, name)
        }
    }
