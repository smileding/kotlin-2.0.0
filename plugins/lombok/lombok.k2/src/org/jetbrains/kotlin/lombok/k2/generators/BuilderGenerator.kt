/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.lombok.k2.generators

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.createCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.builder.buildOuterClassTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.java.JavaScopeProvider
import org.jetbrains.kotlin.fir.java.declarations.*
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.toEffectiveVisibility
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.lombok.k2.config.LombokService
import org.jetbrains.kotlin.lombok.k2.config.lombokService
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

class BuilderGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    companion object {
        private const val TO_BUILDER = "toBuilder"
    }

    private val lombokService: LombokService
        get() = session.lombokService

    private val builderClassCache: FirCache<FirClassSymbol<*>, FirJavaClass?, Nothing?> =
        session.firCachesFactory.createCache(::createBuilderClass)

    private val functionsCache: FirCache<FirClassSymbol<*>, Map<Name, List<FirJavaMethod>>?, Nothing?> =
        session.firCachesFactory.createCache(::createFunctions)

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
        if (!classSymbol.isSuitableJavaClass()) return emptySet()
        return functionsCache.getValue(classSymbol)?.keys.orEmpty()
    }

    override fun getNestedClassifiersNames(classSymbol: FirClassSymbol<*>, context: NestedClassGenerationContext): Set<Name> {
        if (!classSymbol.isSuitableJavaClass()) return emptySet()
        val name = builderClassCache.getValue(classSymbol)?.name ?: return emptySet()
        return setOf(name)
    }

    override fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> {
        val classSymbol = context?.owner ?: return emptyList()
        return functionsCache.getValue(classSymbol)?.get(callableId.callableName).orEmpty().map { it.symbol }
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        if (!owner.isSuitableJavaClass()) return null
        return builderClassCache.getValue(owner)?.symbol
    }

    private fun createFunctions(classSymbol: FirClassSymbol<*>): Map<Name, List<FirJavaMethod>>? {
        val builder = lombokService.getBuilder(classSymbol) ?: return null
        val functions = mutableListOf<FirJavaMethod>()
        val classId = classSymbol.classId
        val builderClassName = builder.builderClassName.replace("*", classId.shortClassName.asString())
        val builderClassId = classId.createNestedClassId(Name.identifier(builderClassName))

        val builderType = builderClassId.constructClassLikeType(emptyArray(), isNullable = false)
        val visibility = builder.visibility.toVisibility()
        functions += classSymbol.createJavaMethod(
            Name.identifier(builder.builderMethodName),
            valueParameters = emptyList(),
            returnTypeRef = builderType.toFirResolvedTypeRef(),
            visibility = visibility,
            modality = Modality.FINAL,
            dispatchReceiverType = null,
            isStatic = true
        )

        if (builder.requiresToBuilder) {
            functions += classSymbol.createJavaMethod(
                Name.identifier(TO_BUILDER),
                valueParameters = emptyList(),
                returnTypeRef = builderType.toFirResolvedTypeRef(),
                visibility = visibility,
                modality = Modality.FINAL,
            )
        }

        return functions.groupBy { it.name }
    }

    @OptIn(SymbolInternals::class)
    private fun createBuilderClass(classSymbol: FirClassSymbol<*>): FirJavaClass? {
        val javaClass = classSymbol.fir as? FirJavaClass ?: return null
        val builder = lombokService.getBuilder(classSymbol) ?: return null
        val builderName = Name.identifier(builder.builderClassName.replace("*", classSymbol.name.asString()))
        val visibility = builder.visibility.toVisibility()
        val builderClass = classSymbol.createJavaClass(
            session,
            builderName,
            visibility,
            Modality.FINAL,
            isStatic = true,
            superTypeRefs = listOf(session.builtinTypes.anyType)
        )?.apply {
            declarations += symbol.createDefaultJavaConstructor(visibility)
            declarations += symbol.createJavaMethod(
                Name.identifier(builder.buildMethodName),
                valueParameters = emptyList(),
                returnTypeRef = classSymbol.defaultType().toFirResolvedTypeRef(),
                visibility = visibility,
                modality = Modality.FINAL
            )
            val fields = javaClass.declarations.filterIsInstance<FirJavaField>()
            for (field in fields) {
                when (val singular = lombokService.getSingular(field.symbol)) {
                    null -> createSetterMethod(builder.setterPrefix, field, symbol, declarations, builder.visibility.toVisibility())
                    else -> createMethodsForSingularFields(
                        builder.setterPrefix,
                        singular,
                        field,
                        symbol,
                        declarations,
                        symbol.defaultType().toFirResolvedTypeRef()
                    )
                }
            }

        } ?: return null


        return builderClass
    }
}

@OptIn(SymbolInternals::class)
private fun FirClassSymbol<*>.createJavaClass(
    session: FirSession,
    name: Name,
    visibility: Visibility,
    modality: Modality,
    isStatic: Boolean,
    superTypeRefs: List<FirTypeRef>,
): FirJavaClass? {
    val containingClass = this.fir as? FirJavaClass ?: return null
    val classId = containingClass.classId.createNestedClassId(name)
    return buildJavaClass {
        moduleData = containingClass.moduleData
        symbol = FirRegularClassSymbol(classId)
        this.name = name
        isFromSource = true
        this.visibility = visibility
        this.modality = modality
        this.isStatic = isStatic
        classKind = ClassKind.CLASS
        javaTypeParameterStack = containingClass.javaTypeParameterStack
        scopeProvider = JavaScopeProvider
        if (!isStatic) {
            typeParameters += containingClass.typeParameters.map {
                buildOuterClassTypeParameterRef { symbol = it.symbol }
            }
        }
        this.superTypeRefs += superTypeRefs
        val effectiveVisibility = containingClass.effectiveVisibility.lowerBound(
            visibility.toEffectiveVisibility(this@createJavaClass, forClass = true),
            session.typeContext
        )
        isTopLevel = false
        status = FirResolvedDeclarationStatusImpl(
            visibility,
            modality,
            effectiveVisibility
        ).apply {
            this.isInner = !isTopLevel && !this@buildJavaClass.isStatic
            isCompanion = false
            isData = false
            isInline = false
            isFun = classKind == ClassKind.INTERFACE
        }
    }
}
