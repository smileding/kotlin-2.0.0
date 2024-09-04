/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.extended

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.isLhsOfAssignment
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.utils.memberDeclarationNameOrNull
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.types.isNothing
import org.jetbrains.kotlin.fir.types.isNullableNothing
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.resolvedType

object CheckReturnValue : FirBasicExpressionChecker(MppCheckerKind.Common) {
    override fun check(expression: FirStatement, context: CheckerContext, reporter: DiagnosticReporter) {
        if (expression !is FirExpression) return // TODO: are we sure that these are all cases?
        if (expression.isLhsOfAssignment(context)) return
        if (expression is FirAnnotation) return

        // 1. Check only resolved references
        val calleeReference = expression.toReference(context.session) ?: return
        val resolvedReference = calleeReference.resolved ?: return

        // If not the outermost call, then it is used as an argument
        if (context.callsOrAssignments.lastOrNull { it != expression } != null) return

        val outerExpression = context.containingElements.lastOrNull { it != expression }

        // Used directly in return expression, property or parameter declaration
        if (outerExpression.isTerminal) return

        // Safe calls for some reason are not part of context.callsOrAssignments, so have to be checked separately
        if (outerExpression is FirSafeCallExpression && outerExpression.receiver == expression) return

        // Ignore Unit or Nothing?/Nothing
        if (expression.resolvedType.run { isNothing || isNullableNothing || isUnit }) return

        reporter.reportOn(expression.source, FirErrors.RETURN_VALUE_NOT_USED, resolvedReference.name, context)
        if (resolvedReference.name.toString() == "stringF") {
            val outer = context.containingDeclarations.lastOrNull()
//            println(outer?.render())
//            println(context.callsOrAssignments.joinToString { it.render() })
            //        val referencedSymbol = resolvedReference.resolvedSymbol
        }
//        TODO("Not yet implemented")
    }


    // FirSmartCastExpressionImpl????
    private val FirElement?.isTerminal: Boolean get() = this is FirReturnExpression || this is FirProperty || this is FirValueParameter || this is FirArgumentList

}


//fun stringF() = ""
//
//class Inits(val used: String = stringF()) {
//    val init1 = stringF()
//
//    val explicit: String
//        get() = stringF()
//
//    val unused: String
//        get() {
//            stringF()
//            return ""
//        }
//}
