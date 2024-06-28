/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.findClosestClassOrObject
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.FirBackingField
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyBackingField
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.symbols.impl.isExtension
import org.jetbrains.kotlin.name.ClassId

object FirExplicitBackingFieldForbiddenChecker : FirBackingFieldChecker(MppCheckerKind.Common) {
    override fun check(declaration: FirBackingField, context: CheckerContext, reporter: DiagnosticReporter) {
        if (declaration is FirDefaultPropertyBackingField) {
            return
        }

        if (declaration.propertySymbol.isAbstract) {
            reporter.reportOn(declaration.source, getProperDiagnostic(context), context)
        }

        if (declaration.propertySymbol.isExtension) {
            reporter.reportOn(declaration.source, FirErrors.EXPLICIT_BACKING_FIELD_IN_EXTENSION, context)
        }
        val propertyDeclarationSource = declaration.propertySymbol.source
        if (!declaration.propertySymbol.isEffectivelyFinal(context.session)) {
            reporter.reportOn(propertyDeclarationSource, FirErrors.EXPLICIT_BACKING_FIELD_COMMON_PROHIBITION, "non-final property", context)
        }
        if (declaration.visibility != Visibilities.Private) {
            reporter.reportOn(declaration.source, FirErrors.EXPLICIT_BACKING_FIELD_COMMON_PROHIBITION, "non-private backing field", context)
        }
        if (declaration.propertySymbol.isExpect) { // Probably this check is redundant and this already covered
            reporter.reportOn(propertyDeclarationSource, FirErrors.EXPLICIT_BACKING_FIELD_COMMON_PROHIBITION, "expect property", context)
        }
        if (declaration.propertySymbol.isExternal) { // Probably this check is redundant and this already covered
            reporter.reportOn(propertyDeclarationSource, FirErrors.EXPLICIT_BACKING_FIELD_COMMON_PROHIBITION, "external property", context)
        }
        val ann = declaration.annotations.getAnnotationByClassId(jvmFieldAnnotationClassId, context.session)
        if (ann != null) {
            reporter.reportOn(ann.source, FirErrors.EXPLICIT_BACKING_FIELD_COMMON_PROHIBITION, "@JvmField annotation on property", context)
        }
    }

    private fun getProperDiagnostic(context: CheckerContext): KtDiagnosticFactory0 {
        return if (context.findClosestClassOrObject()?.classKind == ClassKind.INTERFACE) {
            FirErrors.EXPLICIT_BACKING_FIELD_IN_INTERFACE
        } else {
            FirErrors.EXPLICIT_BACKING_FIELD_IN_ABSTRACT_PROPERTY
        }
    }

    private val jvmFieldAnnotationClassId = ClassId.fromString("kotlin/jvm/JvmField")
}
