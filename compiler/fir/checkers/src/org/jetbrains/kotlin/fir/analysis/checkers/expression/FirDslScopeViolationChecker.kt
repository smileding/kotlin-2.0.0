/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toReference
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.matchingParameterFunctionType
import org.jetbrains.kotlin.fir.references.FirThisReference
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitReceiverValue
import org.jetbrains.kotlin.fir.resolve.directExpansionType
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.CompilerConeAttributes
import org.jetbrains.kotlin.fir.types.ConeCapturedType
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeDefinitelyNotNullType
import org.jetbrains.kotlin.fir.types.ConeFlexibleType
import org.jetbrains.kotlin.fir.types.ConeIntersectionType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ProjectionKind
import org.jetbrains.kotlin.fir.types.abbreviatedTypeOrSelf
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.isSomeFunctionType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.typeArgumentsOfLowerBoundIfFlexible
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * See https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-dsl-marker/ for more details and
 * /compiler/testData/diagnostics/tests/resolve/dslMarker for the test files.
 */
object FirDslScopeViolationChecker : FirQualifiedAccessExpressionChecker(MppCheckerKind.Common) {
    private val dslMarkerClassId = ClassId.fromString("kotlin/DslMarker")

    override fun check(expression: FirQualifiedAccessExpression, context: CheckerContext, reporter: DiagnosticReporter) {
        val callableSymbol = expression.toResolvedCallableSymbol() ?: return

        fun checkReceiver(receiver: FirExpression?) {
            val thisReference = receiver?.toReference(context.session) as? FirThisReference ?: return
            if (thisReference.isImplicit) {
                checkImpl(
                    expression,
                    callableSymbol,
                    context,
                    reporter,
                    { getDslMarkersOfImplicitReceiver(thisReference.boundSymbol, receiver.resolvedType, context.session) }
                ) {
                    it.receiverExpression.resolvedType == receiver.resolvedType
                }
            }
        }
        checkReceiver(expression.dispatchReceiver)
        checkReceiver(expression.extensionReceiver)

        // For value of builtin functional type with implicit extension receiver, the receiver is passed as the first argument rather than
        // an extension receiver of the `invoke` call. Hence, we need to specially handle this case.
        // For example, consider the following
        // ```
        // @DslMarker
        // annotation class MyDsl
        //
        // @MyDsl
        // class X
        // fun x(block: X.() -> Unit) {}
        //
        // @MyDsl
        // class A
        // fun a(block: A.() -> Unit) {}
        //
        // val useX: X.() -> Unit
        //
        // fun test() {
        //   x {
        //     a {
        //       useX() // DSL_SCOPE_VIOLATION because `useX` needs "extension receiver" `X`.
        //     }
        //   }
        // }
        // ```
        // `useX()` is a call to `invoke` with `useX` as the dispatch receiver. In the FIR tree, extension receiver is represented as an
        // implicit `this` expression passed as the first argument.
        if (
            expression.dispatchReceiver?.resolvedType
                ?.fullyExpandedType(context.session)
                ?.isSomeFunctionType(context.session) == true
            && (callableSymbol as? FirNamedFunctionSymbol)?.name == OperatorNameConventions.INVOKE
        ) {
            val firstArg = (expression as? FirFunctionCall)?.arguments?.firstOrNull() as? FirThisReceiverExpression ?: return
            if (!firstArg.isImplicit) return
            checkImpl(
                expression,
                callableSymbol,
                context,
                reporter,
                { firstArg.getDslMarkersOfThisReceiverExpression(context.session) }
            ) { it.boundSymbol == firstArg.calleeReference.boundSymbol }
        }
    }

    /**
     * Checks whether the implicit receiver (represented as an object of type `T`) violates DSL scope rules.
     */
    private fun checkImpl(
        expression: FirQualifiedAccessExpression,
        callableSymbol: FirCallableSymbol<*>,
        context: CheckerContext,
        reporter: DiagnosticReporter,
        getDslMarkersProvider: () -> Set<ClassId>,
        isImplicitReceiverMatching: (ImplicitReceiverValue<*>) -> Boolean,
    ) {
        val resolvedReceiverIndex = context.implicitReceiverStack.indexOfLast { isImplicitReceiverMatching(it) }
        if (resolvedReceiverIndex == -1) return
        val closerReceivers = context.implicitReceiverStack.drop(resolvedReceiverIndex + 1)
        if (closerReceivers.isEmpty()) return
        val dslMarkers = getDslMarkersProvider()
        if (dslMarkers.isEmpty()) return
        if (closerReceivers.any { receiver -> receiver.getDslMarkersOfImplicitReceiver(context.session).any { it in dslMarkers } }) {
            reporter.reportOn(expression.source, FirErrors.DSL_SCOPE_VIOLATION, callableSymbol, context)
        }
    }

    private fun ImplicitReceiverValue<*>.getDslMarkersOfImplicitReceiver(session: FirSession): Set<ClassId> {
        return getDslMarkersOfImplicitReceiver(boundSymbol, type, session)
    }

    @OptIn(SymbolInternals::class)
    private fun getDslMarkersOfImplicitReceiver(
        boundSymbol: FirBasedSymbol<*>?,
        type: ConeKotlinType,
        session: FirSession,
    ): Set<ClassId> {
        return buildSet {
            (boundSymbol as? FirAnonymousFunctionSymbol)?.fir?.matchingParameterFunctionType?.let {
                // collect annotations in the function type at declaration site. For example, the `@A` and `@B` in the following code.
                // ```
                // fun <T> body(block: @A ((@B T).() -> Unit)) { ... }
                // ```

                // Collect the annotation on the function type, or `@A` in the example above.
                collectDslMarkerAnnotations(session, it.customAnnotations)

                // Collect the annotation on the extension receiver, or `@B` in the example above.
                if (CompilerConeAttributes.ExtensionFunctionType in it.attributes) {
                    it.typeArgumentsOfLowerBoundIfFlexible.firstOrNull()?.type?.let { receiverType ->
                        collectDslMarkerAnnotations(session, receiverType)
                    }
                }
            }

            // Collect annotations on the actual receiver type.
            collectDslMarkerAnnotations(session, type)
        }
    }

    private fun FirThisReceiverExpression.getDslMarkersOfThisReceiverExpression(session: FirSession): Set<ClassId> {
        return buildSet {
            collectDslMarkerAnnotations(session, resolvedType)
        }
    }

    private fun MutableSet<ClassId>.collectDslMarkerAnnotations(session: FirSession, type: ConeKotlinType) {
        val originalType = type.abbreviatedTypeOrSelf
        collectDslMarkerAnnotations(session, originalType.customAnnotations)
        when (originalType) {
            is ConeFlexibleType -> {
                collectDslMarkerAnnotations(session, originalType.lowerBound)
                collectDslMarkerAnnotations(session, originalType.upperBound)
            }
            is ConeCapturedType -> {
                if (originalType.constructor.projection.kind == ProjectionKind.OUT) {
                    originalType.constructor.supertypes?.forEach { collectDslMarkerAnnotations(session, it) }
                }
            }
            is ConeDefinitelyNotNullType -> collectDslMarkerAnnotations(session, originalType.original)
            is ConeIntersectionType -> originalType.intersectedTypes.forEach { collectDslMarkerAnnotations(session, it) }
            is ConeClassLikeType -> {
                val classDeclaration = originalType.toSymbol(session) ?: return
                collectDslMarkerAnnotations(session, classDeclaration.resolvedAnnotationsWithClassIds)
                when (classDeclaration) {
                    is FirClassSymbol -> {
                        for (superType in classDeclaration.resolvedSuperTypes) {
                            collectDslMarkerAnnotations(session, superType)
                        }
                    }
                    is FirTypeAliasSymbol -> {
                        originalType.directExpansionType(session)?.let {
                            collectDslMarkerAnnotations(session, it)
                        }
                    }
                }
            }
            else -> return
        }
    }

    private fun MutableSet<ClassId>.collectDslMarkerAnnotations(session: FirSession, annotations: Collection<FirAnnotation>) {
        for (annotation in annotations) {
            val annotationClass =
                annotation.annotationTypeRef.coneType.fullyExpandedType(session).toClassSymbol(session)
                    ?: continue
            if (annotationClass.hasAnnotation(dslMarkerClassId, session)) {
                add(annotationClass.classId)
            }
        }
    }
}