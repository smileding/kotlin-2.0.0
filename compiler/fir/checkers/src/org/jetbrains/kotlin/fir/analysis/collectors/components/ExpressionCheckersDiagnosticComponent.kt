/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.collectors.components

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.CheckersComponentInternal
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkersComponent
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.utils.exceptions.withFirEntry
import org.jetbrains.kotlin.utils.exceptions.rethrowExceptionWithDetails

@OptIn(CheckersComponentInternal::class)
class ExpressionCheckersDiagnosticComponent(
    session: FirSession,
    reporter: DiagnosticReporter,
    checkers: ExpressionCheckers,
) : AbstractDiagnosticCollectorComponent(session, reporter) {
    private val allBasicExpressionCheckers = checkers.allBasicExpressionCheckers.toTypedArray()
    private val allTypeOperatorCallCheckers = checkers.allTypeOperatorCallCheckers.toTypedArray()
    private val allLiteralExpressionCheckers = checkers.allLiteralExpressionCheckers.toTypedArray()
    private val allAnnotationCheckers = checkers.allAnnotationCheckers.toTypedArray()
    private val allAnnotationCallCheckers = checkers.allAnnotationCallCheckers.toTypedArray()
    private val allQualifiedAccessExpressionCheckers = checkers.allQualifiedAccessExpressionCheckers.toTypedArray()
    private val allPropertyAccessExpressionCheckers = checkers.allPropertyAccessExpressionCheckers.toTypedArray()
    private val allFunctionCallCheckers = checkers.allFunctionCallCheckers.toTypedArray()
    private val allIntegerLiteralOperatorCallCheckers = checkers.allIntegerLiteralOperatorCallCheckers.toTypedArray()
    private val allCallableReferenceAccessCheckers = checkers.allCallableReferenceAccessCheckers.toTypedArray()
    private val allThisReceiverExpressionCheckers = checkers.allThisReceiverExpressionCheckers.toTypedArray()
    private val allInaccessibleReceiverCheckers = checkers.allInaccessibleReceiverCheckers.toTypedArray()
    private val allResolvedQualifierCheckers = checkers.allResolvedQualifierCheckers.toTypedArray()
    private val allWhenExpressionCheckers = checkers.allWhenExpressionCheckers.toTypedArray()
    private val allWhileLoopCheckers = checkers.allWhileLoopCheckers.toTypedArray()
    private val allDoWhileLoopCheckers = checkers.allDoWhileLoopCheckers.toTypedArray()
    private val allLoopExpressionCheckers = checkers.allLoopExpressionCheckers.toTypedArray()
    private val allBooleanOperatorExpressionCheckers = checkers.allBooleanOperatorExpressionCheckers.toTypedArray()
    private val allArrayLiteralCheckers = checkers.allArrayLiteralCheckers.toTypedArray()
    private val allStringConcatenationCallCheckers = checkers.allStringConcatenationCallCheckers.toTypedArray()
    private val allCheckNotNullCallCheckers = checkers.allCheckNotNullCallCheckers.toTypedArray()
    private val allElvisExpressionCheckers = checkers.allElvisExpressionCheckers.toTypedArray()
    private val allSafeCallExpressionCheckers = checkers.allSafeCallExpressionCheckers.toTypedArray()
    private val allTryExpressionCheckers = checkers.allTryExpressionCheckers.toTypedArray()
    private val allClassReferenceExpressionCheckers = checkers.allClassReferenceExpressionCheckers.toTypedArray()
    private val allGetClassCallCheckers = checkers.allGetClassCallCheckers.toTypedArray()
    private val allEqualityOperatorCallCheckers = checkers.allEqualityOperatorCallCheckers.toTypedArray()
    private val allVariableAssignmentCheckers = checkers.allVariableAssignmentCheckers.toTypedArray()
    private val allReturnExpressionCheckers = checkers.allReturnExpressionCheckers.toTypedArray()
    private val allLoopJumpCheckers = checkers.allLoopJumpCheckers.toTypedArray()
    private val allBlockCheckers = checkers.allBlockCheckers.toTypedArray()
    private val allCallCheckers = checkers.allCallCheckers.toTypedArray()
    private val allThrowExpressionCheckers = checkers.allThrowExpressionCheckers.toTypedArray()
    private val allSmartCastExpressionCheckers = checkers.allSmartCastExpressionCheckers.toTypedArray()

    constructor(session: FirSession, reporter: DiagnosticReporter, mppKind: MppCheckerKind) : this(
        session,
        reporter,
        when (mppKind) {
            MppCheckerKind.Common -> session.checkersComponent.commonExpressionCheckers
            MppCheckerKind.Platform -> session.checkersComponent.platformExpressionCheckers
        }
    )

    override fun visitElement(element: FirElement, data: CheckerContext) {
        if (element is FirExpression) {
            error("${element::class.simpleName} should call parent checkers inside ${this::class.simpleName}")
        }
    }

    override fun visitExpression(expression: FirExpression, data: CheckerContext) {
        allBasicExpressionCheckers.check(expression, data)
    }

    override fun visitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall, data: CheckerContext) {
        allTypeOperatorCallCheckers.check(typeOperatorCall, data)
    }

    override fun visitLiteralExpression(literalExpression: FirLiteralExpression, data: CheckerContext) {
        allLiteralExpressionCheckers.check(literalExpression, data)
    }

    override fun visitAnnotation(annotation: FirAnnotation, data: CheckerContext) {
        allAnnotationCheckers.check(annotation, data)
    }

    override fun visitAnnotationCall(annotationCall: FirAnnotationCall, data: CheckerContext) {
        allAnnotationCallCheckers.check(annotationCall, data)
    }

    override fun visitErrorAnnotationCall(errorAnnotationCall: FirErrorAnnotationCall, data: CheckerContext) {
        allAnnotationCallCheckers.check(errorAnnotationCall, data)
    }

    override fun visitQualifiedAccessExpression(qualifiedAccessExpression: FirQualifiedAccessExpression, data: CheckerContext) {
        allQualifiedAccessExpressionCheckers.check(qualifiedAccessExpression, data)
    }

    override fun visitPropertyAccessExpression(propertyAccessExpression: FirPropertyAccessExpression, data: CheckerContext) {
        allPropertyAccessExpressionCheckers.check(propertyAccessExpression, data)
    }

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: CheckerContext) {
        allFunctionCallCheckers.check(functionCall, data)
    }

    override fun visitComponentCall(componentCall: FirComponentCall, data: CheckerContext) {
        allFunctionCallCheckers.check(componentCall, data)
    }

    override fun visitIntegerLiteralOperatorCall(integerLiteralOperatorCall: FirIntegerLiteralOperatorCall, data: CheckerContext) {
        allIntegerLiteralOperatorCallCheckers.check(integerLiteralOperatorCall, data)
    }

    override fun visitImplicitInvokeCall(implicitInvokeCall: FirImplicitInvokeCall, data: CheckerContext) {
        allFunctionCallCheckers.check(implicitInvokeCall, data)
    }

    override fun visitCallableReferenceAccess(callableReferenceAccess: FirCallableReferenceAccess, data: CheckerContext) {
        allCallableReferenceAccessCheckers.check(callableReferenceAccess, data)
    }

    override fun visitThisReceiverExpression(thisReceiverExpression: FirThisReceiverExpression, data: CheckerContext) {
        allThisReceiverExpressionCheckers.check(thisReceiverExpression, data)
    }

    override fun visitInaccessibleReceiverExpression(
        inaccessibleReceiverExpression: FirInaccessibleReceiverExpression,
        data: CheckerContext,
    ) {
        allInaccessibleReceiverCheckers.check(inaccessibleReceiverExpression, data)
    }

    override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier, data: CheckerContext) {
        allResolvedQualifierCheckers.check(resolvedQualifier, data)
    }

    override fun visitErrorResolvedQualifier(errorResolvedQualifier: FirErrorResolvedQualifier, data: CheckerContext) {
        allResolvedQualifierCheckers.check(errorResolvedQualifier, data)
    }

    override fun visitWhenExpression(whenExpression: FirWhenExpression, data: CheckerContext) {
        allWhenExpressionCheckers.check(whenExpression, data)
    }

    override fun visitWhileLoop(whileLoop: FirWhileLoop, data: CheckerContext) {
        allWhileLoopCheckers.check(whileLoop, data)
    }

    override fun visitDoWhileLoop(doWhileLoop: FirDoWhileLoop, data: CheckerContext) {
        allDoWhileLoopCheckers.check(doWhileLoop, data)
    }

    override fun visitErrorLoop(errorLoop: FirErrorLoop, data: CheckerContext) {
        allLoopExpressionCheckers.check(errorLoop, data)
    }

    override fun visitBooleanOperatorExpression(booleanOperatorExpression: FirBooleanOperatorExpression, data: CheckerContext) {
        allBooleanOperatorExpressionCheckers.check(booleanOperatorExpression, data)
    }

    override fun visitArrayLiteral(arrayLiteral: FirArrayLiteral, data: CheckerContext) {
        allArrayLiteralCheckers.check(arrayLiteral, data)
    }

    override fun visitStringConcatenationCall(stringConcatenationCall: FirStringConcatenationCall, data: CheckerContext) {
        allStringConcatenationCallCheckers.check(stringConcatenationCall, data)
    }

    override fun visitCheckNotNullCall(checkNotNullCall: FirCheckNotNullCall, data: CheckerContext) {
        allCheckNotNullCallCheckers.check(checkNotNullCall, data)
    }

    override fun visitElvisExpression(elvisExpression: FirElvisExpression, data: CheckerContext) {
        allElvisExpressionCheckers.check(elvisExpression, data)
    }

    override fun visitSafeCallExpression(safeCallExpression: FirSafeCallExpression, data: CheckerContext) {
        allSafeCallExpressionCheckers.check(safeCallExpression, data)
    }

    override fun visitTryExpression(tryExpression: FirTryExpression, data: CheckerContext) {
        allTryExpressionCheckers.check(tryExpression, data)
    }

    override fun visitClassReferenceExpression(classReferenceExpression: FirClassReferenceExpression, data: CheckerContext) {
        allClassReferenceExpressionCheckers.check(classReferenceExpression, data)
    }

    override fun visitGetClassCall(getClassCall: FirGetClassCall, data: CheckerContext) {
        allGetClassCallCheckers.check(getClassCall, data)
    }

    override fun visitEqualityOperatorCall(equalityOperatorCall: FirEqualityOperatorCall, data: CheckerContext) {
        allEqualityOperatorCallCheckers.check(equalityOperatorCall, data)
    }

    override fun visitVariableAssignment(variableAssignment: FirVariableAssignment, data: CheckerContext) {
        allVariableAssignmentCheckers.check(variableAssignment, data)
    }

    override fun visitReturnExpression(returnExpression: FirReturnExpression, data: CheckerContext) {
        allReturnExpressionCheckers.check(returnExpression, data)
    }

    override fun visitBreakExpression(breakExpression: FirBreakExpression, data: CheckerContext) {
        allLoopJumpCheckers.check(breakExpression, data)
    }

    override fun visitContinueExpression(continueExpression: FirContinueExpression, data: CheckerContext) {
        allLoopJumpCheckers.check(continueExpression, data)
    }

    override fun visitBlock(block: FirBlock, data: CheckerContext) {
        allBlockCheckers.check(block, data)
    }

    override fun visitDelegatedConstructorCall(delegatedConstructorCall: FirDelegatedConstructorCall, data: CheckerContext) {
        allCallCheckers.check(delegatedConstructorCall, data)
    }

    override fun visitMultiDelegatedConstructorCall(multiDelegatedConstructorCall: FirMultiDelegatedConstructorCall, data: CheckerContext) {
        allCallCheckers.check(multiDelegatedConstructorCall, data)
    }

    override fun visitThrowExpression(throwExpression: FirThrowExpression, data: CheckerContext) {
        allThrowExpressionCheckers.check(throwExpression, data)
    }

    override fun visitVarargArgumentsExpression(varargArgumentsExpression: FirVarargArgumentsExpression, data: CheckerContext) {
        allBasicExpressionCheckers.check(varargArgumentsExpression, data)
    }

    override fun visitSamConversionExpression(samConversionExpression: FirSamConversionExpression, data: CheckerContext) {
        allBasicExpressionCheckers.check(samConversionExpression, data)
    }

    override fun visitWrappedExpression(wrappedExpression: FirWrappedExpression, data: CheckerContext) {
        allBasicExpressionCheckers.check(wrappedExpression, data)
    }

    override fun visitWrappedArgumentExpression(wrappedArgumentExpression: FirWrappedArgumentExpression, data: CheckerContext) {
        allBasicExpressionCheckers.check(wrappedArgumentExpression, data)
    }

    override fun visitSpreadArgumentExpression(spreadArgumentExpression: FirSpreadArgumentExpression, data: CheckerContext) {
        allBasicExpressionCheckers.check(spreadArgumentExpression, data)
    }

    override fun visitNamedArgumentExpression(namedArgumentExpression: FirNamedArgumentExpression, data: CheckerContext) {
        allBasicExpressionCheckers.check(namedArgumentExpression, data)
    }

    override fun visitSmartCastExpression(smartCastExpression: FirSmartCastExpression, data: CheckerContext) {
        allSmartCastExpressionCheckers.check(smartCastExpression, data)
    }

    override fun visitWhenSubjectExpression(whenSubjectExpression: FirWhenSubjectExpression, data: CheckerContext) {
        allBasicExpressionCheckers.check(whenSubjectExpression, data)
    }

    override fun visitResolvedReifiedParameterReference(
        resolvedReifiedParameterReference: FirResolvedReifiedParameterReference,
        data: CheckerContext
    ) {
        allBasicExpressionCheckers.check(resolvedReifiedParameterReference, data)
    }

    override fun visitComparisonExpression(comparisonExpression: FirComparisonExpression, data: CheckerContext) {
        allBasicExpressionCheckers.check(comparisonExpression, data)
    }

    override fun visitDesugaredAssignmentValueReferenceExpression(
        desugaredAssignmentValueReferenceExpression: FirDesugaredAssignmentValueReferenceExpression,
        data: CheckerContext
    ) {
        allBasicExpressionCheckers.check(desugaredAssignmentValueReferenceExpression, data)
    }

    override fun visitCheckedSafeCallSubject(checkedSafeCallSubject: FirCheckedSafeCallSubject, data: CheckerContext) {
        allBasicExpressionCheckers.check(checkedSafeCallSubject, data)
    }

    override fun visitErrorExpression(errorExpression: FirErrorExpression, data: CheckerContext) {
        allBasicExpressionCheckers.check(errorExpression, data)
    }

    override fun visitQualifiedErrorAccessExpression(
        qualifiedErrorAccessExpression: FirQualifiedErrorAccessExpression,
        data: CheckerContext
    ) {
        allBasicExpressionCheckers.check(qualifiedErrorAccessExpression, data)
    }

    private inline fun <reified E : FirStatement> Array<FirExpressionChecker<E>>.check(
        expression: E,
        context: CheckerContext
    ) {
        for (checker in this) {
            try {
                checker.check(expression, context, reporter)
            } catch (e: Exception) {
                rethrowExceptionWithDetails("Exception in expression checker", e) {
                    withFirEntry("expression", expression)
                    context.containingFilePath?.let { withEntry("file", it) }
                }
            }
        }
    }
}
