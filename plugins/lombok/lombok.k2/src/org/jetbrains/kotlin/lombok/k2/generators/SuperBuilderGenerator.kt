/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.lombok.k2.generators

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toRegularClassSymbol
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.createCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildTypeParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.java.JavaScopeProvider
import org.jetbrains.kotlin.fir.java.declarations.*
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.toEffectiveVisibility
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.lombok.k2.config.LombokService
import org.jetbrains.kotlin.lombok.k2.config.lombokService
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

class SuperBuilderGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    companion object {
        private const val TO_BUILDER = "toBuilder"
        private const val IMPL_SUFFIX = "Impl"
    }

    private val lombokService: LombokService
        get() = session.lombokService

    private data class BuilderClasses(val builder: FirJavaClass, val builderImpl: FirJavaClass)

    private val builderClassesCache: FirCache<FirClassSymbol<*>, BuilderClasses?, Nothing?> =
        session.firCachesFactory.createCache(::createBuilderClasses)

    private val functionsCache: FirCache<FirClassSymbol<*>, Map<Name, List<FirJavaMethod>>?, Nothing?> =
        session.firCachesFactory.createCache(::createFunctions)

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
        if (!classSymbol.isSuitableJavaClass()) return emptySet()
        return functionsCache.getValue(classSymbol)?.keys.orEmpty()
    }

    override fun getNestedClassifiersNames(classSymbol: FirClassSymbol<*>, context: NestedClassGenerationContext): Set<Name> {
        if (!classSymbol.isSuitableJavaClass()) return emptySet()
        val classes = builderClassesCache.getValue(classSymbol) ?: return emptySet()
        return setOf(classes.builder.name, classes.builderImpl.name)
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
        val builderClasses = builderClassesCache.getValue(owner) ?: return null
        return if (name.identifier.endsWith(IMPL_SUFFIX))
            builderClasses.builderImpl.symbol
        else
            builderClasses.builder.symbol
    }

    private fun createFunctions(classSymbol: FirClassSymbol<*>): Map<Name, List<FirJavaMethod>>? {
        val builder = lombokService.getSuperBuilder(classSymbol) ?: return null
        val functions = mutableListOf<FirJavaMethod>()
        val classId = classSymbol.classId
        val builderClassName = builder.superBuilderClassName.replace("*", classId.shortClassName.asString())
        val builderClassId = classId.createNestedClassId(Name.identifier(builderClassName))

        val builderType = builderClassId.constructClassLikeType(arrayOf(ConeStarProjection, ConeStarProjection), isNullable = false)
        val visibility = Visibilities.DEFAULT_VISIBILITY
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
    private fun createBuilderClasses(classSymbol: FirClassSymbol<*>): BuilderClasses? {
        val javaClass = classSymbol.fir as? FirJavaClass ?: return null
        val superBuilders = javaClass.superTypeRefs.firstNotNullOfOrNull { superTypeRef ->
            val superTypeSymbol = superTypeRef.toRegularClassSymbol(session) ?: return@firstNotNullOfOrNull null
            builderClassesCache.getValue(superTypeSymbol)
        }
        val builder = lombokService.getSuperBuilder(classSymbol) ?: return null
        val builderNameString = builder.superBuilderClassName.replace("*", classSymbol.name.asString())
        val visibility = Visibilities.DEFAULT_VISIBILITY
        val builderClass = classSymbol.createJavaBuilder(
            session,
            Name.identifier(builderNameString),
            visibility,
            Modality.FINAL,
            superBuilder = superBuilders?.builder,
        )?.apply {
            declarations += symbol.createDefaultJavaConstructor(visibility)
            declarations += symbol.createJavaMethod(
                Name.identifier("self"),
                valueParameters = emptyList(),
                returnTypeRef = symbol.typeParameterSymbols[1].defaultType.toFirResolvedTypeRef(),
                visibility = visibility,
                modality = Modality.FINAL
            )
            declarations += symbol.createJavaMethod(
                Name.identifier(builder.buildMethodName),
                valueParameters = emptyList(),
                returnTypeRef = symbol.typeParameterSymbols[0].defaultType.toFirResolvedTypeRef(),
                visibility = visibility,
                modality = Modality.FINAL
            )
            val fields = javaClass.declarations.filterIsInstance<FirJavaField>()
            for (field in fields) {
                when (val singular = lombokService.getSingular(field.symbol)) {
                    null -> createSetterMethod(builder.setterPrefix, field, symbol, declarations, Visibilities.DEFAULT_VISIBILITY)
                    else -> createMethodsForSingularFields(
                        builder.setterPrefix,
                        singular,
                        field,
                        symbol,
                        declarations,
                        symbol.typeParameterSymbols[1].defaultType.toFirResolvedTypeRef(),
                    )
                }
            }

        } ?: return null

        val builderImplClass = classSymbol.createJavaBuilderImpl(
            session,
            Name.identifier(builderNameString + IMPL_SUFFIX),
            visibility,
            Modality.FINAL,
            builderClassSymbol = builderClass.symbol,
        )?.apply {
            declarations += symbol.createDefaultJavaConstructor(visibility)
            declarations += symbol.createJavaMethod(
                Name.identifier("self"),
                valueParameters = emptyList(),
                returnTypeRef = defaultType().toFirResolvedTypeRef(),
                visibility = visibility,
                modality = Modality.FINAL
            )
            declarations += symbol.createJavaMethod(
                Name.identifier(builder.buildMethodName),
                valueParameters = emptyList(),
                returnTypeRef = classSymbol.defaultType().toFirResolvedTypeRef(),
                visibility = visibility,
                modality = Modality.FINAL
            )
        } ?: return null

        return BuilderClasses(builderClass, builderImplClass)
    }
}

@OptIn(SymbolInternals::class)
private fun FirClassSymbol<*>.createJavaBuilder(
    session: FirSession,
    name: Name,
    visibility: Visibility,
    modality: Modality,
    superBuilder: FirJavaClass?,
): FirJavaClass? {
    return createJavaClass(
        session,
        name,
        visibility,
        modality,
        builderClassSymbol = null,
        superBuilder = superBuilder,
    )
}

@OptIn(SymbolInternals::class)
private fun FirClassSymbol<*>.createJavaBuilderImpl(
    session: FirSession,
    name: Name,
    visibility: Visibility,
    modality: Modality,
    builderClassSymbol: FirRegularClassSymbol,
): FirJavaClass? {
    return createJavaClass(
        session,
        name,
        visibility,
        modality,
        builderClassSymbol,
        superBuilder = null
    )
}

@OptIn(SymbolInternals::class)
private fun FirClassSymbol<*>.createJavaClass(
    session: FirSession,
    name: Name,
    visibility: Visibility,
    modality: Modality,
    builderClassSymbol: FirRegularClassSymbol?,
    superBuilder: FirJavaClass?,
): FirJavaClass? {
    val containingClass = this.fir as? FirJavaClass ?: return null
    val classId = containingClass.classId.createNestedClassId(name)
    val classSymbol = FirRegularClassSymbol(classId)
    return buildJavaClass {
        moduleData = containingClass.moduleData
        symbol = classSymbol
        this.name = name
        isFromSource = true
        this.visibility = visibility
        this.modality = modality
        this.isStatic = true
        classKind = ClassKind.CLASS
        javaTypeParameterStack = containingClass.javaTypeParameterStack
        scopeProvider = JavaScopeProvider
        if (builderClassSymbol == null) {
            val classTypeParameterSymbol = FirTypeParameterSymbol()
            val builderTypeParameterSymbol = FirTypeParameterSymbol()
            typeParameters += buildTypeParameter {
                moduleData = session.moduleData
                origin = FirDeclarationOrigin.Synthetic.PluginFile
                resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES
                this.name = Name.identifier("C")
                symbol = classTypeParameterSymbol
                containingDeclarationSymbol = classSymbol
                variance = Variance.INVARIANT
                isReified = false
                bounds += buildResolvedTypeRef {
                    coneType = defaultType().toFirResolvedTypeRef().coneType
                }
            }
            typeParameters += buildTypeParameter {
                moduleData = session.moduleData
                origin = FirDeclarationOrigin.Synthetic.PluginFile
                resolvePhase = FirResolvePhase.ANALYZED_DEPENDENCIES
                this.name = Name.identifier("B")
                symbol = builderTypeParameterSymbol
                containingDeclarationSymbol = classSymbol
                variance = Variance.INVARIANT
                isReified = false
                bounds += buildResolvedTypeRef {
                    coneType = classSymbol.constructType(
                        typeArguments = arrayOf(
                            ConeTypeParameterTypeImpl(classTypeParameterSymbol.toLookupTag(), isNullable = false),
                            ConeTypeParameterTypeImpl(builderTypeParameterSymbol.toLookupTag(), isNullable = false),
                        ),
                        isNullable = false
                    )
                }
            }
            val superTypeRef = if (superBuilder != null) {
                superBuilder.symbol.constructType(
                    typeArguments = arrayOf(
                        typeParameters[0].symbol.defaultType.toFirResolvedTypeRef().coneType,
                        typeParameters[1].symbol.defaultType.toFirResolvedTypeRef().coneType,
                    ),
                    isNullable = false
                ).toFirResolvedTypeRef()
            } else {
                session.builtinTypes.anyType
            }
            this.superTypeRefs += listOf(superTypeRef)
        } else {
            this.superTypeRefs += buildResolvedTypeRef {
                coneType = builderClassSymbol.constructType(
                    typeArguments = arrayOf(
                        this@createJavaClass.defaultType(),
                        ConeClassLikeTypeImpl(classSymbol.toLookupTag(), arrayOf(), isNullable = false),
                    ),
                    isNullable = false
                )
            }
        }
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
