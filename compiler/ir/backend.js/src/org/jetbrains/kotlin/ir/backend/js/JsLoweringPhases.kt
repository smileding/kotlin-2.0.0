/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js

import org.jetbrains.kotlin.backend.common.ir.Symbols.Companion.isTypeOfIntrinsic
import org.jetbrains.kotlin.backend.common.ir.isReifiable
import org.jetbrains.kotlin.backend.common.lower.*
import org.jetbrains.kotlin.backend.common.lower.coroutines.AddContinuationToLocalSuspendFunctionsLowering
import org.jetbrains.kotlin.backend.common.lower.coroutines.AddContinuationToNonLocalSuspendFunctionsLowering
import org.jetbrains.kotlin.backend.common.lower.inline.LocalClassesExtractionFromInlineFunctionsLowering
import org.jetbrains.kotlin.backend.common.lower.inline.LocalClassesInInlineFunctionsLowering
import org.jetbrains.kotlin.backend.common.lower.inline.LocalClassesInInlineLambdasLowering
import org.jetbrains.kotlin.backend.common.lower.inline.OuterThisInInlineFunctionsSpecialAccessorLowering
import org.jetbrains.kotlin.backend.common.lower.loops.ForLoopsLowering
import org.jetbrains.kotlin.backend.common.phaser.*
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.KlibConfigurationKeys
import org.jetbrains.kotlin.ir.backend.js.lower.*
import org.jetbrains.kotlin.ir.backend.js.lower.calls.CallsLowering
import org.jetbrains.kotlin.ir.backend.js.lower.cleanup.CleanupLowering
import org.jetbrains.kotlin.ir.backend.js.lower.coroutines.*
import org.jetbrains.kotlin.ir.backend.js.lower.inline.*
import org.jetbrains.kotlin.ir.backend.js.utils.compileSuspendAsJsGenerator
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.inline.*
import org.jetbrains.kotlin.ir.interpreter.IrInterpreterConfiguration
import org.jetbrains.kotlin.platform.js.JsPlatforms

private fun List<CompilerPhase<JsIrBackendContext, IrModuleFragment, IrModuleFragment>>.toCompilerPhase() =
    reduce { acc, lowering -> acc.then(lowering) }

private val validateIrBeforeLowering = makeIrModulePhase<JsIrBackendContext>(
    ::IrValidationBeforeLoweringPhase,
    name = "ValidateIrBeforeLowering",
    description = "Validate IR before lowering"
)

private val validateIrAfterInliningOnlyPrivateFunctions = makeIrModulePhase(
    { context: JsIrBackendContext ->
        IrValidationAfterInliningOnlyPrivateFunctionsPhase(
            context,
            checkInlineFunctionCallSites = { inlineFunctionUseSite ->
                val inlineFunction = inlineFunctionUseSite.symbol.owner
                when {
                    // TODO: remove this condition after the fix of KT-69457:
                    inlineFunctionUseSite is IrFunctionReference && !inlineFunction.isReifiable() -> true // temporarily permitted

                    // Call sites of only non-private functions are allowed at this stage.
                    else -> !inlineFunction.isConsideredAsPrivateForInlining()
                }
            }
        )
    },
    name = "IrValidationAfterInliningOnlyPrivateFunctionsPhase",
    description = "Validate IR after only private functions have been inlined",
)

private val dumpSyntheticAccessorsPhase = makeIrModulePhase<JsIrBackendContext>(
    ::DumpSyntheticAccessors,
    name = "DumpSyntheticAccessorsPhase",
    description = "Dump synthetic accessors and their call sites (used only for testing and debugging)",
)

private val validateIrAfterInliningAllFunctions = makeIrModulePhase(
    { context: JsIrBackendContext ->
        IrValidationAfterInliningAllFunctionsPhase(
            context,
            checkInlineFunctionCallSites = { inlineFunctionUseSite ->
                // No inline function call sites should remain at this stage.
                val inlineFunction = inlineFunctionUseSite.symbol.owner
                when {
                    // TODO: remove this condition after the fix of KT-69457:
                    inlineFunctionUseSite is IrFunctionReference && !inlineFunction.isReifiable() -> true // temporarily permitted

                    // TODO: remove this condition after the fix of KT-70361:
                    isTypeOfIntrinsic(inlineFunction.symbol) -> true // temporarily permitted

                    else -> false // forbidden
                }
            }
        )
    },
    name = "IrValidationAfterInliningAllFunctionsPhase",
    description = "Validate IR after all functions have been inlined",
)

private val validateIrAfterLowering = makeIrModulePhase<JsIrBackendContext>(
    ::IrValidationAfterLoweringPhase,
    name = "ValidateIrAfterLowering",
    description = "Validate IR after lowering"
)

private val collectClassDefaultConstructorsPhase = makeIrModulePhase(
    ::CollectClassDefaultConstructorsLowering,
    name = "CollectClassDefaultConstructorsLowering",
    description = "Collect classes default constructors to add it to metadata on code generating phase"
)

private val prepareCollectionsToExportLowering = makeIrModulePhase(
    ::PrepareCollectionsToExportLowering,
    name = "PrepareCollectionsToExportLowering",
    description = "Add @JsImplicitExport to exportable collections all the declarations which we don't want to export such as `Enum.entries` or `DataClass::componentN`",
)

private val removeImplicitExportsFromCollections = makeIrModulePhase(
    ::RemoveImplicitExportsFromCollections,
    name = "RemoveImplicitExportsFromCollections",
    description = "Remove @JsImplicitExport from unused collections if there is no strict-mode for TypeScript",
)

private val preventExportOfSyntheticDeclarationsLowering = makeIrModulePhase(
    ::ExcludeSyntheticDeclarationsFromExportLowering,
    name = "ExcludeSyntheticDeclarationsFromExportLowering",
    description = "Exclude synthetic declarations which we don't want to export such as `Enum.entries` or `DataClass::componentN`",
)

private val jsStaticLowering = makeIrModulePhase(
    ::JsStaticLowering,
    name = "JsStaticLowering",
    description = "Make for each @JsStatic declaration inside the companion object a proxy declaration inside its parent class static scope",
)

val createScriptFunctionsPhase = makeIrModulePhase(
    ::CreateScriptFunctionsPhase,
    name = "CreateScriptFunctionsPhase",
    description = "Create functions for initialize and evaluate script"
)

private val collectClassIdentifiersLowering = makeIrModulePhase(
    ::JsCollectClassIdentifiersLowering,
    name = "CollectClassIdentifiersLowering",
    description = "Save classId before all the lowerings",
)

private val inventNamesForLocalClassesPhase = makeIrModulePhase(
    ::JsInventNamesForLocalClasses,
    name = "InventNamesForLocalClasses",
    description = "Invent names for local classes and anonymous objects",
)

private val annotationInstantiationLowering = makeIrModulePhase(
    ::JsAnnotationImplementationTransformer,
    name = "AnnotationImplementation",
    description = "Create synthetic annotations implementations and use them in annotations constructor calls"
)

private val expectDeclarationsRemovingPhase = makeIrModulePhase(
    ::ExpectDeclarationsRemoveLowering,
    name = "ExpectDeclarationsRemoving",
    description = "Remove expect declaration from module fragment"
)

private val stringConcatenationLoweringPhase = makeIrModulePhase(
    ::JsStringConcatenationLowering,
    name = "JsStringConcatenationLowering",
    description = "Call toString() for values of some types when concatenating strings"
)

private val lateinitNullableFieldsPhase = makeIrModulePhase(
    ::NullableFieldsForLateinitCreationLowering,
    name = "LateinitNullableFields",
    description = "Create nullable fields for lateinit properties"
)

private val lateinitDeclarationLoweringPhase = makeIrModulePhase(
    ::NullableFieldsDeclarationLowering,
    name = "LateinitDeclarations",
    description = "Reference nullable fields from properties and getters + insert checks"
)

private val lateinitUsageLoweringPhase = makeIrModulePhase(
    ::LateinitUsageLowering,
    name = "LateinitUsage",
    description = "Insert checks for lateinit field references"
)

private val kotlinNothingValueExceptionPhase = makeIrModulePhase(
    ::KotlinNothingValueExceptionLowering,
    name = "KotlinNothingValueException",
    description = "Throw proper exception for calls returning value of type 'kotlin.Nothing'"
)

private val stripTypeAliasDeclarationsPhase = makeIrModulePhase<JsIrBackendContext>(
    { StripTypeAliasDeclarationsLowering() },
    name = "StripTypeAliasDeclarations",
    description = "Strip typealias declarations"
)

private val jsCodeOutliningPhase = makeIrModulePhase(
    ::JsCodeOutliningLowering,
    name = "JsCodeOutliningLowering",
    description = "Outline js() calls where JS code references Kotlin locals"
)

private val inlineCallableReferenceToLambdaPhase = makeIrModulePhase<JsIrBackendContext>(
    ::JsInlineCallableReferenceToLambdaPhase,
    name = "JsInlineCallableReferenceToLambdaPhase",
    description = "Transform all callable reference (including defaults) to inline lambdas, mark inline lambdas for later passes"
)

private val arrayConstructorPhase = makeIrModulePhase(
    ::ArrayConstructorLowering,
    name = "ArrayConstructor",
    description = "Transform `Array(size) { index -> value }` into a loop",
    prerequisite = setOf(inlineCallableReferenceToLambdaPhase)
)

private val sharedVariablesLoweringPhase = makeIrModulePhase(
    ::SharedVariablesLowering,
    name = "SharedVariablesLowering",
    description = "Box captured mutable variables",
    prerequisite = setOf(lateinitDeclarationLoweringPhase, lateinitUsageLoweringPhase)
)

private val outerThisSpecialAccessorInInlineFunctionsPhase = makeIrModulePhase(
    ::OuterThisInInlineFunctionsSpecialAccessorLowering,
    name = "OuterThisInInlineFunctionsSpecialAccessorLowering",
    description = "Generate a special private member accessor for outer@this implicit value parameter in inline functions"
)

private val localClassesInInlineLambdasPhase = makeIrModulePhase(
    ::LocalClassesInInlineLambdasLowering,
    name = "LocalClassesInInlineLambdasPhase",
    description = "Extract local classes from inline lambdas",
    prerequisite = setOf()
)

private val localClassesInInlineFunctionsPhase = makeIrModulePhase(
    ::LocalClassesInInlineFunctionsLowering,
    name = "LocalClassesInInlineFunctionsPhase",
    description = "Extract local classes from inline functions",
    prerequisite = setOf()
)

private val localClassesExtractionFromInlineFunctionsPhase = makeIrModulePhase(
    { context -> LocalClassesExtractionFromInlineFunctionsLowering(context) },
    name = "localClassesExtractionFromInlineFunctionsPhase",
    description = "Move local classes from inline functions into nearest declaration container",
    prerequisite = setOf(localClassesInInlineFunctionsPhase)
)

private val legacySyntheticAccessorLoweringPhase = makeIrModulePhase(
    ::LegacySyntheticAccessorLowering,
    name = "LegacySyntheticAccessorLowering",
    description = "Wrap top level inline function to access through them from inline functions (legacy lowering)"
)

private val wrapInlineDeclarationsWithReifiedTypeParametersLowering = makeIrModulePhase(
    ::WrapInlineDeclarationsWithReifiedTypeParametersLowering,
    name = "WrapInlineDeclarationsWithReifiedTypeParametersLowering",
    description = "Wrap inline declarations with reified type parameters"
)

private val replaceSuspendIntrinsicLowering = makeIrModulePhase(
    ::ReplaceSuspendIntrinsicLowering,
    name = "ReplaceSuspendIntrinsicLowering",
    description = "Replace suspend intrinsic for generator based coroutines"
)

private val inlineOnlyPrivateFunctionsPhase = makeIrModulePhase(
    { context: JsIrBackendContext ->
        FunctionInlining(
            context,
            JsInlineFunctionResolver(context, inlineMode = InlineMode.PRIVATE_INLINE_FUNCTIONS),
            produceOuterThisFields = false,
        )
    },
    name = "InlineOnlyPrivateFunctions",
    description = "The first phase of inlining (inline only private functions)",
    prerequisite = setOf(outerThisSpecialAccessorInInlineFunctionsPhase)
)

internal val syntheticAccessorGenerationPhase = makeIrModulePhase(
    lowering = ::SyntheticAccessorLowering,
    name = "SyntheticAccessorGeneration",
    description = "Generate synthetic accessors from private declarations referenced from inline functions",
    prerequisite = setOf(inlineOnlyPrivateFunctionsPhase),
)

// TODO: KT-67220: consider removing it
private val cacheInlineFunctionsBeforeInliningOnlyPrivateFunctionsPhase = makeIrModulePhase(
    { context: JsIrBackendContext ->
        SaveInlineFunctionsBeforeInlining(context, cacheOnlyPrivateFunctions = true)
    },
    name = "CacheInlineFunctionsBeforeInliningOnlyPrivateFunctionsPhase",
    description = "Cache copies of inline functions before InlineOnlyPrivateFunctions phase",
    prerequisite = setOf(
        sharedVariablesLoweringPhase,
        localClassesInInlineLambdasPhase,
        wrapInlineDeclarationsWithReifiedTypeParametersLowering
    )
)

// TODO: KT-67220: consider removing it
private val cacheInlineFunctionsBeforeInliningAllFunctionsPhase = makeIrModulePhase(
    { context: JsIrBackendContext ->
        SaveInlineFunctionsBeforeInlining(context, cacheOnlyPrivateFunctions = false)
    },
    name = "CacheInlineFunctionsBeforeInliningAllFunctionsPhase",
    description = "Cache copies of inline functions before InlineAllFunctions phase",
    prerequisite = setOf(
        sharedVariablesLoweringPhase,
        localClassesInInlineLambdasPhase,
        wrapInlineDeclarationsWithReifiedTypeParametersLowering
    )
)

private val inlineAllFunctionsPhase = makeIrModulePhase(
    { context: JsIrBackendContext ->
        FunctionInlining(
            context,
            JsInlineFunctionResolver(context, inlineMode = InlineMode.ALL_INLINE_FUNCTIONS),
            produceOuterThisFields = false,
        )
    },
    name = "InlineAllFunctions",
    description = "The second phase of inlining (inline all functions)",
    prerequisite = setOf(cacheInlineFunctionsBeforeInliningAllFunctionsPhase, outerThisSpecialAccessorInInlineFunctionsPhase)
)

private val copyInlineFunctionBodyLoweringPhase = makeIrModulePhase(
    ::CopyInlineFunctionBodyLowering,
    name = "CopyInlineFunctionBody",
    description = "Copy inline function body",
    prerequisite = setOf(inlineAllFunctionsPhase)
)

private val removeInlineDeclarationsWithReifiedTypeParametersLoweringPhase = makeIrModulePhase(
    { RemoveInlineDeclarationsWithReifiedTypeParametersLowering() },
    name = "RemoveInlineFunctionsWithReifiedTypeParametersLowering",
    description = "Remove Inline functions with reified parameters from context",
    prerequisite = setOf(inlineAllFunctionsPhase)
)

private val captureStackTraceInThrowablesPhase = makeIrModulePhase(
    ::CaptureStackTraceInThrowables,
    name = "CaptureStackTraceInThrowables",
    description = "Capture stack trace in Throwable constructors"
)

private val throwableSuccessorsLoweringPhase = makeIrModulePhase(
    { context ->
        context.run {
            val extendThrowableSymbol =
                if (es6mode) setPropertiesToThrowableInstanceSymbol else extendThrowableSymbol

            ThrowableLowering(this, extendThrowableSymbol)
        }
    },
    name = "ThrowableLowering",
    description = "Link kotlin.Throwable and JavaScript Error together to provide proper interop between language and platform exceptions",
    prerequisite = setOf(captureStackTraceInThrowablesPhase)
)

private val tailrecLoweringPhase = makeIrModulePhase(
    ::TailrecLowering,
    name = "TailrecLowering",
    description = "Replace `tailrec` callsites with equivalent loop"
)

private val enumClassConstructorLoweringPhase = makeIrModulePhase(
    ::EnumClassConstructorLowering,
    name = "EnumClassConstructorLowering",
    description = "Transform Enum Class into regular Class"
)

private val enumClassConstructorBodyLoweringPhase = makeIrModulePhase(
    ::EnumClassConstructorBodyTransformer,
    name = "EnumClassConstructorBodyLowering",
    description = "Transform Enum Class into regular Class"
)

private val enumEntryInstancesLoweringPhase = makeIrModulePhase(
    ::EnumEntryInstancesLowering,
    name = "EnumEntryInstancesLowering",
    description = "Create instance variable for each enum entry initialized with `null`",
    prerequisite = setOf(enumClassConstructorLoweringPhase)
)

private val enumEntryInstancesBodyLoweringPhase = makeIrModulePhase(
    ::EnumEntryInstancesBodyLowering,
    name = "EnumEntryInstancesBodyLowering",
    description = "Insert enum entry field initialization into correxposnding class constructors",
    prerequisite = setOf(enumEntryInstancesLoweringPhase)
)

private val enumClassCreateInitializerLoweringPhase = makeIrModulePhase(
    ::EnumClassCreateInitializerLowering,
    name = "EnumClassCreateInitializerLowering",
    description = "Create initializer for enum entries",
    prerequisite = setOf(enumClassConstructorLoweringPhase)
)

private val enumEntryCreateGetInstancesFunsLoweringPhase = makeIrModulePhase(
    ::EnumEntryCreateGetInstancesFunsLowering,
    name = "EnumEntryCreateGetInstancesFunsLowering",
    description = "Create enumEntry_getInstance functions",
    prerequisite = setOf(enumClassConstructorLoweringPhase)
)

private val enumSyntheticFunsLoweringPhase = makeIrModulePhase(
    ::EnumSyntheticFunctionsAndPropertiesLowering,
    name = "EnumSyntheticFunctionsAndPropertiesLowering",
    description = "Implement `valueOf, `values` and `entries`",
    prerequisite = setOf(
        enumClassConstructorLoweringPhase,
        enumClassCreateInitializerLoweringPhase,
        enumEntryCreateGetInstancesFunsLoweringPhase,
    )
)

private val enumUsageLoweringPhase = makeIrModulePhase(
    ::EnumUsageLowering,
    name = "EnumUsageLowering",
    description = "Replace enum access with invocation of corresponding function",
    prerequisite = setOf(enumEntryCreateGetInstancesFunsLoweringPhase)
)

private val externalEnumUsageLoweringPhase = makeIrModulePhase(
    ::ExternalEnumUsagesLowering,
    name = "ExternalEnumUsagesLowering",
    description = "Replace external enum entry accesses with field accesses"
)

private val enumEntryRemovalLoweringPhase = makeIrModulePhase(
    ::EnumClassRemoveEntriesLowering,
    name = "EnumEntryRemovalLowering",
    description = "Replace enum entry with corresponding class",
    prerequisite = setOf(enumUsageLoweringPhase)
)

private val callableReferenceLowering = makeIrModulePhase(
    ::CallableReferenceLowering,
    name = "CallableReferenceLowering",
    description = "Build a lambda/callable reference class",
    prerequisite = setOf(inlineAllFunctionsPhase, wrapInlineDeclarationsWithReifiedTypeParametersLowering)
)

private val returnableBlockLoweringPhase = makeIrModulePhase(
    ::JsReturnableBlockLowering,
    name = "JsReturnableBlockLowering",
    description = "Introduce temporary variable for result and change returnable block's type to Unit",
    prerequisite = setOf(inlineAllFunctionsPhase)
)

private val rangeContainsLoweringPhase = makeIrModulePhase(
    ::RangeContainsLowering,
    name = "RangeContainsLowering",
    description = "[Optimization] Optimizes calls to contains() for ClosedRanges"
)

private val forLoopsLoweringPhase = makeIrModulePhase(
    ::ForLoopsLowering,
    name = "ForLoopsLowering",
    description = "[Optimization] For loops lowering"
)

private val enumWhenPhase = makeIrModulePhase(
    ::EnumWhenLowering,
    name = "EnumWhenLowering",
    description = "[Optimization] Replace `when` subjects of enum types with their ordinals"
)

private val propertyLazyInitLoweringPhase = makeIrModulePhase(
    ::PropertyLazyInitLowering,
    name = "PropertyLazyInitLowering",
    description = "Make property init as lazy"
)

private val removeInitializersForLazyProperties = makeIrModulePhase(
    ::RemoveInitializersForLazyProperties,
    name = "RemoveInitializersForLazyProperties",
    description = "Remove property initializers if they was initialized lazily"
)

private val propertyAccessorInlinerLoweringPhase = makeIrModulePhase(
    ::JsPropertyAccessorInlineLowering,
    name = "PropertyAccessorInlineLowering",
    description = "[Optimization] Inline property accessors"
)

private val copyPropertyAccessorBodiesLoweringPass = makeIrModulePhase(
    ::CopyAccessorBodyLowerings,
    name = "CopyAccessorBodyLowering",
    description = "Copy accessor bodies so that ist can be safely read in PropertyAccessorInlineLowering",
    prerequisite = setOf(propertyAccessorInlinerLoweringPhase)
)

private val booleanPropertyInExternalLowering = makeIrModulePhase(
    ::BooleanPropertyInExternalLowering,
    name = "BooleanPropertyInExternalLowering",
    description = "Lowering which wrap boolean in external declarations with Boolean() call and add diagnostic for such cases"
)

private val localDelegatedPropertiesLoweringPhase = makeIrModulePhase<JsIrBackendContext>(
    { LocalDelegatedPropertiesLowering() },
    name = "LocalDelegatedPropertiesLowering",
    description = "Transform Local Delegated properties"
)

private val localDeclarationsLoweringPhase = makeIrModulePhase(
    { context -> LocalDeclarationsLowering(context, suggestUniqueNames = false) },
    name = "LocalDeclarationsLowering",
    description = "Move local declarations into nearest declaration container",
    prerequisite = setOf(sharedVariablesLoweringPhase, localDelegatedPropertiesLoweringPhase)
)

private val localClassExtractionPhase = makeIrModulePhase(
    { context -> LocalClassPopupLowering(context) },
    name = "LocalClassExtractionPhase",
    description = "Move local declarations into nearest declaration container",
    prerequisite = setOf(localDeclarationsLoweringPhase)
)

private val innerClassesLoweringPhase = makeIrModulePhase<JsIrBackendContext>(
    ::InnerClassesLowering,
    name = "InnerClassesLowering",
    description = "Capture outer this reference to inner class"
)

private val innerClassesMemberBodyLoweringPhase = makeIrModulePhase(
    ::InnerClassesMemberBodyLowering,
    name = "InnerClassesMemberBody",
    description = "Replace `this` with 'outer this' field references",
    prerequisite = setOf(innerClassesLoweringPhase)
)

private val innerClassConstructorCallsLoweringPhase = makeIrModulePhase<JsIrBackendContext>(
    ::InnerClassConstructorCallsLowering,
    name = "InnerClassConstructorCallsLowering",
    description = "Replace inner class constructor invocation"
)

private val suspendFunctionsLoweringPhase = makeIrModulePhase<JsIrBackendContext>(
    { context ->
        if (context.compileSuspendAsJsGenerator) {
            JsSuspendFunctionWithGeneratorsLowering(context)
        } else {
            JsSuspendFunctionsLowering(context)
        }
    },
    name = "SuspendFunctionsLowering",
    description = "Transform suspend functions into CoroutineImpl instance and build state machine or into GeneratorCoroutineImpl and ES2015 generators"
)

private val addContinuationToNonLocalSuspendFunctionsLoweringPhase = makeIrModulePhase(
    ::AddContinuationToNonLocalSuspendFunctionsLowering,
    name = "AddContinuationToNonLocalSuspendFunctionsLowering",
    description = "Add explicit continuation as last parameter of non-local suspend functions"
)

private val addContinuationToLocalSuspendFunctionsLoweringPhase = makeIrModulePhase(
    ::AddContinuationToLocalSuspendFunctionsLowering,
    name = "AddContinuationToLocalSuspendFunctionsLowering",
    description = "Add explicit continuation as last parameter of local suspend functions"
)


private val addContinuationToFunctionCallsLoweringPhase = makeIrModulePhase(
    ::AddContinuationToFunctionCallsLowering,
    name = "AddContinuationToFunctionCallsLowering",
    description = "Replace suspend function calls with calls with continuation",
    prerequisite = setOf(
        addContinuationToLocalSuspendFunctionsLoweringPhase,
        addContinuationToNonLocalSuspendFunctionsLoweringPhase,
    )
)

private val privateMembersLoweringPhase = makeIrModulePhase(
    ::PrivateMembersLowering,
    name = "PrivateMembersLowering",
    description = "Extract private members from classes"
)

private val privateMemberUsagesLoweringPhase = makeIrModulePhase(
    ::PrivateMemberBodiesLowering,
    name = "PrivateMemberUsagesLowering",
    description = "Rewrite the private member usages"
)

private val propertyReferenceLoweringPhase = makeIrModulePhase(
    ::PropertyReferenceLowering,
    name = "PropertyReferenceLowering",
    description = "Transform property references",
)

private val interopCallableReferenceLoweringPhase = makeIrModulePhase(
    ::InteropCallableReferenceLowering,
    name = "InteropCallableReferenceLowering",
    description = "Interop layer for function references and lambdas",
    prerequisite = setOf(
        suspendFunctionsLoweringPhase,
        localDeclarationsLoweringPhase,
        localDelegatedPropertiesLoweringPhase,
        callableReferenceLowering
    )
)

private val defaultArgumentStubGeneratorPhase = makeIrModulePhase(
    ::JsDefaultArgumentStubGenerator,
    name = "DefaultArgumentStubGenerator",
    description = "Generate synthetic stubs for functions with default parameter values"
)

private val defaultArgumentPatchOverridesPhase = makeIrModulePhase(
    ::DefaultParameterPatchOverridenSymbolsLowering,
    name = "DefaultArgumentsPatchOverrides",
    description = "Patch overrides for fake override dispatch functions",
    prerequisite = setOf(defaultArgumentStubGeneratorPhase)
)

private val defaultParameterInjectorPhase = makeIrModulePhase(
    ::JsDefaultParameterInjector,
    name = "DefaultParameterInjector",
    description = "Replace callsite with default parameters with corresponding stub function",
    prerequisite = setOf(interopCallableReferenceLoweringPhase, innerClassesLoweringPhase)
)

private val defaultParameterCleanerPhase = makeIrModulePhase(
    ::DefaultParameterCleaner,
    name = "DefaultParameterCleaner",
    description = "Clean default parameters up"
)

private val varargLoweringPhase = makeIrModulePhase(
    ::VarargLowering,
    name = "VarargLowering",
    description = "Lower vararg arguments",
    prerequisite = setOf(interopCallableReferenceLoweringPhase)
)

private val propertiesLoweringPhase = makeIrModulePhase<JsIrBackendContext>(
    { PropertiesLowering() },
    name = "PropertiesLowering",
    description = "Move fields and accessors out from its property"
)

private val primaryConstructorLoweringPhase = makeIrModulePhase(
    ::PrimaryConstructorLowering,
    name = "PrimaryConstructorLowering",
    description = "Creates primary constructor if it doesn't exist",
    prerequisite = setOf(enumClassConstructorLoweringPhase)
)

private val delegateToPrimaryConstructorLoweringPhase = makeIrModulePhase(
    ::DelegateToSyntheticPrimaryConstructor,
    name = "DelegateToSyntheticPrimaryConstructor",
    description = "Delegates to synthetic primary constructor",
    prerequisite = setOf(primaryConstructorLoweringPhase)
)

private val annotationConstructorLowering = makeIrModulePhase(
    ::AnnotationConstructorLowering,
    name = "AnnotationConstructorLowering",
    description = "Generate annotation constructor body"
)

private val initializersLoweringPhase = makeIrModulePhase(
    ::InitializersLowering,
    name = "InitializersLowering",
    description = "Merge init block and field initializers into [primary] constructor",
    prerequisite = setOf(
        enumClassConstructorLoweringPhase, primaryConstructorLoweringPhase, annotationConstructorLowering, localClassExtractionPhase
    )
)

private val initializersCleanupLoweringPhase = makeIrModulePhase(
    ::InitializersCleanupLowering,
    name = "InitializersCleanupLowering",
    description = "Remove non-static anonymous initializers and field init expressions",
    prerequisite = setOf(initializersLoweringPhase)
)

private val multipleCatchesLoweringPhase = makeIrModulePhase(
    ::MultipleCatchesLowering,
    name = "MultipleCatchesLowering",
    description = "Replace multiple catches with single one"
)

private val bridgesConstructionPhase = makeIrModulePhase(
    ::JsBridgesConstruction,
    name = "BridgesConstruction",
    description = "Generate bridges",
    prerequisite = setOf(suspendFunctionsLoweringPhase)
)

private val singleAbstractMethodPhase = makeIrModulePhase(
    ::JsSingleAbstractMethodLowering,
    name = "SingleAbstractMethod",
    description = "Replace SAM conversions with instances of interface-implementing classes"
)

private val typeOperatorLoweringPhase = makeIrModulePhase(
    ::TypeOperatorLowering,
    name = "TypeOperatorLowering",
    description = "Lower IrTypeOperator with corresponding logic",
    prerequisite = setOf(
        bridgesConstructionPhase,
        removeInlineDeclarationsWithReifiedTypeParametersLoweringPhase,
        singleAbstractMethodPhase,
        interopCallableReferenceLoweringPhase,
    )
)

private val secondaryConstructorLoweringPhase = makeIrModulePhase(
    ::SecondaryConstructorLowering,
    name = "SecondaryConstructorLoweringPhase",
    description = "Generate static functions for each secondary constructor",
    prerequisite = setOf(innerClassesLoweringPhase)
)

private val secondaryFactoryInjectorLoweringPhase = makeIrModulePhase(
    ::SecondaryFactoryInjectorLowering,
    name = "SecondaryFactoryInjectorLoweringPhase",
    description = "Replace usage of secondary constructor with corresponding static function",
    prerequisite = setOf(innerClassesLoweringPhase)
)

private val constLoweringPhase = makeIrModulePhase(
    ::ConstLowering,
    name = "ConstLowering",
    description = "Wrap Long and Char constants into constructor invocation"
)
private val inlineClassDeclarationLoweringPhase = makeIrModulePhase<JsIrBackendContext>(
    { InlineClassLowering(it).inlineClassDeclarationLowering },
    name = "InlineClassDeclarationLowering",
    description = "Handle inline class declarations"
)

private val inlineClassUsageLoweringPhase = makeIrModulePhase(
    { InlineClassLowering(it).inlineClassUsageLowering },
    name = "InlineClassUsageLowering",
    description = "Handle inline class usages",
    prerequisite = setOf(
        // Const lowering generates inline class constructors for unsigned integers
        // which should be lowered by this lowering
        constLoweringPhase
    )
)

private val expressionBodyTransformer = makeIrModulePhase(
    ::ExpressionBodyTransformer,
    name = "ExpressionBodyTransformer",
    description = "Replace IrExpressionBody with IrBlockBody"
)

private val autoboxingTransformerPhase = makeIrModulePhase<JsIrBackendContext>(
    { AutoboxingTransformer(it, replaceTypesInsideInlinedFunctionBlock = true) },
    name = "AutoboxingTransformer",
    description = "Insert box/unbox intrinsics"
)

private val blockDecomposerLoweringPhase = makeIrModulePhase(
    ::JsBlockDecomposerLowering,
    name = "BlockDecomposerLowering",
    description = "Transform statement-like-expression nodes into pure-statement to make it easily transform into JS",
    prerequisite = setOf(typeOperatorLoweringPhase, suspendFunctionsLoweringPhase)
)

private val jsClassUsageInReflectionPhase = makeIrModulePhase(
    ::JsClassUsageInReflectionLowering,
    name = "JsClassUsageInReflectionLowering",
    description = "[Optimization] Eliminate ClassReference and GetClassExpression usages in a simple case of usage raw js constructor",
    prerequisite = setOf(inlineAllFunctionsPhase)
)

private val classReferenceLoweringPhase = makeIrModulePhase(
    ::JsClassReferenceLowering,
    name = "JsClassReferenceLowering",
    description = "Handle class references",
    prerequisite = setOf(jsClassUsageInReflectionPhase)
)

private val primitiveCompanionLoweringPhase = makeIrModulePhase(
    ::PrimitiveCompanionLowering,
    name = "PrimitiveCompanionLowering",
    description = "Replace common companion object access with platform one"
)

private val callsLoweringPhase = makeIrModulePhase(
    ::CallsLowering,
    name = "CallsLowering",
    description = "Handle intrinsics"
)

private val staticMembersLoweringPhase = makeIrModulePhase(
    ::StaticMembersLowering,
    name = "StaticMembersLowering",
    description = "Move static member declarations to top-level"
)

private val objectDeclarationLoweringPhase = makeIrModulePhase(
    ::ObjectDeclarationLowering,
    name = "ObjectDeclarationLowering",
    description = "Create lazy object instance generator functions",
    prerequisite = setOf(enumClassCreateInitializerLoweringPhase)
)

private val invokeStaticInitializersPhase = makeIrModulePhase(
    ::InvokeStaticInitializersLowering,
    name = "IntroduceStaticInitializersLowering",
    description = "Invoke companion object's initializers from companion object in object constructor",
    prerequisite = setOf(objectDeclarationLoweringPhase)
)

private val es6AddBoxParameterToConstructorsLowering = makeIrModulePhase(
    ::ES6AddBoxParameterToConstructorsLowering,
    name = "ES6AddBoxParameterToConstructorsLowering",
    description = "Add box parameter to a constructor if needed",
)

private val es6ConstructorLowering = makeIrModulePhase(
    ::ES6ConstructorLowering,
    name = "ES6ConstructorLowering",
    description = "Lower constructors declarations to support ES classes",
    prerequisite = setOf(es6AddBoxParameterToConstructorsLowering)
)

private val es6ConstructorUsageLowering = makeIrModulePhase(
    ::ES6ConstructorCallLowering,
    name = "ES6ConstructorCallLowering",
    description = "Lower constructor usages to support ES classes",
    prerequisite = setOf(es6ConstructorLowering)
)

private val objectUsageLoweringPhase = makeIrModulePhase(
    ::ObjectUsageLowering,
    name = "ObjectUsageLowering",
    description = "Transform IrGetObjectValue into instance generator call",
    prerequisite = setOf(primaryConstructorLoweringPhase)
)

private val escapedIdentifiersLowering = makeIrModulePhase(
    ::EscapedIdentifiersLowering,
    name = "EscapedIdentifiersLowering",
    description = "Convert global variables with invalid names access to globalThis member expression"
)

private val implicitlyExportedDeclarationsMarkingLowering = makeIrModulePhase(
    ::ImplicitlyExportedDeclarationsMarkingLowering,
    name = "ImplicitlyExportedDeclarationsMarkingLowering",
    description = "Add @JsImplicitExport annotation to declarations which are not exported but are used inside other exported declarations as a type",
)

private val cleanupLoweringPhase = makeIrModulePhase<JsIrBackendContext>(
    { CleanupLowering() },
    name = "CleanupLowering",
    description = "Clean up IR before codegen"
)

private val jsSuspendArityStorePhase = makeIrModulePhase(
    ::JsSuspendArityStoreLowering,
    name = "JsSuspendArityStoreLowering",
    description = "Store arity for suspend functions to not remove it during DCE"
)

val constEvaluationPhase = makeIrModulePhase<JsIrBackendContext>(
    { context ->
        val configuration = IrInterpreterConfiguration(
            printOnlyExceptionMessage = true,
            platform = JsPlatforms.defaultJsPlatform,
        )
        ConstEvaluationLowering(context, configuration = configuration)
    },
    name = "ConstEvaluationLowering",
    description = "Evaluate functions that are marked as `IntrinsicConstEvaluation`",
)

val mainFunctionCallWrapperLowering = makeIrModulePhase<JsIrBackendContext>(
    ::MainFunctionCallWrapperLowering,
    name = "MainFunctionCallWrapperLowering",
    description = "Generate main function call inside the wrapper-function"
)

fun getJsLowerings(
    configuration: CompilerConfiguration
): List<SimpleNamedCompilerPhase<JsIrBackendContext, IrModuleFragment, IrModuleFragment>> = listOfNotNull(
    // BEGIN: Common Native/JS prefix.
    validateIrBeforeLowering,
    jsCodeOutliningPhase,
    lateinitNullableFieldsPhase,
    lateinitDeclarationLoweringPhase,
    lateinitUsageLoweringPhase,
    sharedVariablesLoweringPhase,
    outerThisSpecialAccessorInInlineFunctionsPhase,
    localClassesInInlineLambdasPhase,
    localClassesInInlineFunctionsPhase.takeIf { configuration.getBoolean(KlibConfigurationKeys.NO_DOUBLE_INLINING) },
    localClassesExtractionFromInlineFunctionsPhase.takeIf { configuration.getBoolean(KlibConfigurationKeys.NO_DOUBLE_INLINING) },
    inlineCallableReferenceToLambdaPhase,
    arrayConstructorPhase,
    legacySyntheticAccessorLoweringPhase.takeIf { configuration.getBoolean(KlibConfigurationKeys.NO_DOUBLE_INLINING) },
    wrapInlineDeclarationsWithReifiedTypeParametersLowering,
    cacheInlineFunctionsBeforeInliningOnlyPrivateFunctionsPhase.takeUnless { configuration.getBoolean(KlibConfigurationKeys.NO_DOUBLE_INLINING) },
    inlineOnlyPrivateFunctionsPhase.takeUnless { configuration.getBoolean(KlibConfigurationKeys.NO_DOUBLE_INLINING) },
    syntheticAccessorGenerationPhase.takeUnless { configuration.getBoolean(KlibConfigurationKeys.NO_DOUBLE_INLINING) },
    // Note: The validation goes after both `inlineOnlyPrivateFunctionsPhase` and `syntheticAccessorGenerationPhase`
    // just because it goes so in Native.
    validateIrAfterInliningOnlyPrivateFunctions.takeUnless { configuration.getBoolean(KlibConfigurationKeys.NO_DOUBLE_INLINING) },
    dumpSyntheticAccessorsPhase.takeIf {
        !configuration.getBoolean(KlibConfigurationKeys.NO_DOUBLE_INLINING) &&
                configuration[KlibConfigurationKeys.SYNTHETIC_ACCESSORS_DUMP_DIR] != null
    },
    cacheInlineFunctionsBeforeInliningAllFunctionsPhase,
    inlineAllFunctionsPhase,
    validateIrAfterInliningAllFunctions,
    // END: Common Native/JS prefix.

    constEvaluationPhase,
    copyInlineFunctionBodyLoweringPhase,
    removeInlineDeclarationsWithReifiedTypeParametersLoweringPhase,
    replaceSuspendIntrinsicLowering,
    prepareCollectionsToExportLowering,
    preventExportOfSyntheticDeclarationsLowering,
    jsStaticLowering,
    inventNamesForLocalClassesPhase,
    collectClassIdentifiersLowering,
    annotationInstantiationLowering,
    expectDeclarationsRemovingPhase,
    stripTypeAliasDeclarationsPhase,
    createScriptFunctionsPhase,
    stringConcatenationLoweringPhase,
    callableReferenceLowering,
    singleAbstractMethodPhase,
    tailrecLoweringPhase,
    enumClassConstructorLoweringPhase,
    enumClassConstructorBodyLoweringPhase,
    localDelegatedPropertiesLoweringPhase,
    localDeclarationsLoweringPhase,
    localClassExtractionPhase,
    innerClassesLoweringPhase,
    innerClassesMemberBodyLoweringPhase,
    innerClassConstructorCallsLoweringPhase,
    jsClassUsageInReflectionPhase,
    propertiesLoweringPhase,
    primaryConstructorLoweringPhase,
    delegateToPrimaryConstructorLoweringPhase,
    annotationConstructorLowering,
    initializersLoweringPhase,
    initializersCleanupLoweringPhase,
    kotlinNothingValueExceptionPhase,
    collectClassDefaultConstructorsPhase,
    // Common prefix ends
    enumWhenPhase,
    enumEntryInstancesLoweringPhase,
    enumEntryInstancesBodyLoweringPhase,
    enumClassCreateInitializerLoweringPhase,
    enumEntryCreateGetInstancesFunsLoweringPhase,
    enumSyntheticFunsLoweringPhase,
    enumUsageLoweringPhase,
    externalEnumUsageLoweringPhase,
    enumEntryRemovalLoweringPhase,
    suspendFunctionsLoweringPhase,
    propertyReferenceLoweringPhase,
    interopCallableReferenceLoweringPhase,
    jsSuspendArityStorePhase,
    addContinuationToNonLocalSuspendFunctionsLoweringPhase,
    addContinuationToLocalSuspendFunctionsLoweringPhase,
    addContinuationToFunctionCallsLoweringPhase,
    returnableBlockLoweringPhase,
    rangeContainsLoweringPhase,
    forLoopsLoweringPhase,
    primitiveCompanionLoweringPhase,
    propertyLazyInitLoweringPhase,
    removeInitializersForLazyProperties,
    propertyAccessorInlinerLoweringPhase,
    copyPropertyAccessorBodiesLoweringPass,
    booleanPropertyInExternalLowering,
    privateMembersLoweringPhase,
    privateMemberUsagesLoweringPhase,
    defaultArgumentStubGeneratorPhase,
    defaultArgumentPatchOverridesPhase,
    defaultParameterInjectorPhase,
    defaultParameterCleanerPhase,
    captureStackTraceInThrowablesPhase,
    throwableSuccessorsLoweringPhase,
    varargLoweringPhase,
    multipleCatchesLoweringPhase,
    bridgesConstructionPhase,
    typeOperatorLoweringPhase,
    secondaryConstructorLoweringPhase,
    secondaryFactoryInjectorLoweringPhase,
    classReferenceLoweringPhase,
    constLoweringPhase,
    inlineClassDeclarationLoweringPhase,
    inlineClassUsageLoweringPhase,
    expressionBodyTransformer,
    autoboxingTransformerPhase,
    objectDeclarationLoweringPhase,
    blockDecomposerLoweringPhase,
    invokeStaticInitializersPhase,
    objectUsageLoweringPhase,
    es6AddBoxParameterToConstructorsLowering,
    es6ConstructorLowering,
    es6ConstructorUsageLowering,
    callsLoweringPhase,
    escapedIdentifiersLowering,
    implicitlyExportedDeclarationsMarkingLowering,
    removeImplicitExportsFromCollections,
    mainFunctionCallWrapperLowering,
    cleanupLoweringPhase,
    validateIrAfterLowering,
)

fun getJsPhases(
    configuration: CompilerConfiguration
): NamedCompilerPhase<JsIrBackendContext, IrModuleFragment> = SameTypeNamedCompilerPhase(
    name = "IrModuleLowering",
    description = "IR module lowering",
    lower = getJsLowerings(configuration).toCompilerPhase(),
    actions = DEFAULT_IR_ACTIONS,
    nlevels = 1
)

private val es6CollectConstructorsWhichNeedBoxParameterLowering = makeIrModulePhase(
    ::ES6CollectConstructorsWhichNeedBoxParameters,
    name = "ES6CollectConstructorsWhichNeedBoxParameters",
    description = "[Optimization] Collect all of the constructors which requires box parameter",
)

private val es6BoxParameterOptimization = makeIrModulePhase(
    ::ES6ConstructorBoxParameterOptimizationLowering,
    name = "ES6ConstructorBoxParameterOptimizationLowering",
    description = "[Optimization] Remove box parameter from the constructors which don't require box parameter",
    prerequisite = setOf(es6CollectConstructorsWhichNeedBoxParameterLowering)
)

private val es6CollectPrimaryConstructorsWhichCouldBeOptimizedLowering = makeIrModulePhase(
    ::ES6CollectPrimaryConstructorsWhichCouldBeOptimizedLowering,
    name = "ES6CollectPrimaryConstructorsWhichCouldBeOptimizedLowering",
    description = "[Optimization] Collect all of the constructors which could be translated into a regular constructor",
)

private val es6PrimaryConstructorOptimizationLowering = makeIrModulePhase(
    ::ES6PrimaryConstructorOptimizationLowering,
    name = "ES6PrimaryConstructorOptimizationLowering",
    description = "[Optimization] Replace synthetically generated static fabric method with a plain old ES6 constructors whenever it's possible",
    prerequisite = setOf(es6CollectPrimaryConstructorsWhichCouldBeOptimizedLowering)
)

private val es6PrimaryConstructorUsageOptimizationLowering = makeIrModulePhase(
    ::ES6PrimaryConstructorUsageOptimizationLowering,
    name = "ES6PrimaryConstructorUsageOptimizationLowering",
    description = "[Optimization] Replace usage of synthetically generated static fabric method with a plain old ES6 constructors whenever it's possible",
    prerequisite = setOf(es6BoxParameterOptimization, es6PrimaryConstructorOptimizationLowering)
)

private val purifyObjectInstanceGetters = makeIrModulePhase(
    ::PurifyObjectInstanceGettersLowering,
    name = "PurifyObjectInstanceGettersLowering",
    description = "[Optimization] Make object instance getter functions pure whenever it's possible",
)

private val inlineObjectsWithPureInitialization = makeIrModulePhase(
    ::InlineObjectsWithPureInitializationLowering,
    name = "InlineObjectsWithPureInitializationLowering",
    description = "[Optimization] Inline object instance fields getters whenever it's possible",
    prerequisite = setOf(purifyObjectInstanceGetters)
)

val optimizationLoweringList = listOf<SimpleNamedCompilerPhase<JsIrBackendContext, IrModuleFragment, IrModuleFragment>>(
    es6CollectConstructorsWhichNeedBoxParameterLowering,
    es6CollectPrimaryConstructorsWhichCouldBeOptimizedLowering,
    es6BoxParameterOptimization,
    es6PrimaryConstructorOptimizationLowering,
    es6PrimaryConstructorUsageOptimizationLowering,
    purifyObjectInstanceGetters,
    inlineObjectsWithPureInitialization
)

val jsOptimizationPhases = SameTypeNamedCompilerPhase(
    name = "IrModuleOptimizationLowering",
    description = "IR module optimization lowering",
    lower = optimizationLoweringList.toCompilerPhase(),
    actions = DEFAULT_IR_ACTIONS,
    nlevels = 1
)
