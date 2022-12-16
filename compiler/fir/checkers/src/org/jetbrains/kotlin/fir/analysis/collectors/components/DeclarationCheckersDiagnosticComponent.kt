/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.collectors.components

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.CheckersComponentInternal
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkersComponent
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.utils.exceptions.withFirEntry
import org.jetbrains.kotlin.utils.exceptions.rethrowExceptionWithDetails

@OptIn(CheckersComponentInternal::class)
class DeclarationCheckersDiagnosticComponent(
    session: FirSession,
    reporter: DiagnosticReporter,
    checkers: DeclarationCheckers,
) : AbstractDiagnosticCollectorComponent(session, reporter) {
    private val allBasicDeclarationCheckers = checkers.allBasicDeclarationCheckers.toTypedArray()
    private val allFileCheckers = checkers.allFileCheckers.toTypedArray()
    private val allPropertyCheckers = checkers.allPropertyCheckers.toTypedArray()
    private val allClassCheckers = checkers.allClassCheckers.toTypedArray()
    private val allRegularClassCheckers = checkers.allRegularClassCheckers.toTypedArray()
    private val allSimpleFunctionCheckers = checkers.allSimpleFunctionCheckers.toTypedArray()
    private val allTypeAliasCheckers = checkers.allTypeAliasCheckers.toTypedArray()
    private val allConstructorCheckers = checkers.allConstructorCheckers.toTypedArray()
    private val allAnonymousFunctionCheckers = checkers.allAnonymousFunctionCheckers.toTypedArray()
    private val allPropertyAccessorCheckers = checkers.allPropertyAccessorCheckers.toTypedArray()
    private val allBackingFieldCheckers = checkers.allBackingFieldCheckers.toTypedArray()
    private val allValueParameterCheckers = checkers.allValueParameterCheckers.toTypedArray()
    private val allTypeParameterCheckers = checkers.allTypeParameterCheckers.toTypedArray()
    private val allEnumEntryCheckers = checkers.allEnumEntryCheckers.toTypedArray()
    private val allAnonymousObjectCheckers = checkers.allAnonymousObjectCheckers.toTypedArray()
    private val allAnonymousInitializerCheckers = checkers.allAnonymousInitializerCheckers.toTypedArray()
    private val allCallableDeclarationCheckers = checkers.allCallableDeclarationCheckers.toTypedArray()
    private val allScriptCheckers = checkers.allScriptCheckers.toTypedArray()

    constructor(session: FirSession, reporter: DiagnosticReporter, mppKind: MppCheckerKind) : this(
        session,
        reporter,
        when (mppKind) {
            MppCheckerKind.Common -> session.checkersComponent.commonDeclarationCheckers
            MppCheckerKind.Platform -> session.checkersComponent.platformDeclarationCheckers
        }
    )

    override fun visitElement(element: FirElement, data: CheckerContext) {
        if (element is FirDeclaration) {
            error("${element::class.simpleName} should call parent checkers inside ${this::class.simpleName}")
        }
    }

    override fun visitDeclaration(declaration: FirDeclaration, data: CheckerContext) {
        allBasicDeclarationCheckers.check(declaration, data)
    }

    override fun visitFile(file: FirFile, data: CheckerContext) {
        allFileCheckers.check(file, data)
    }

    override fun visitProperty(property: FirProperty, data: CheckerContext) {
        allPropertyCheckers.check(property, data)
    }

    override fun visitClass(klass: FirClass, data: CheckerContext) {
        allClassCheckers.check(klass, data)
    }

    override fun visitRegularClass(regularClass: FirRegularClass, data: CheckerContext) {
        allRegularClassCheckers.check(regularClass, data)
    }

    override fun visitSimpleFunction(simpleFunction: FirSimpleFunction, data: CheckerContext) {
        allSimpleFunctionCheckers.check(simpleFunction, data)
    }

    override fun visitTypeAlias(typeAlias: FirTypeAlias, data: CheckerContext) {
        allTypeAliasCheckers.check(typeAlias, data)
    }

    override fun visitConstructor(constructor: FirConstructor, data: CheckerContext) {
        allConstructorCheckers.check(constructor, data)
    }

    override fun visitErrorPrimaryConstructor(errorPrimaryConstructor: FirErrorPrimaryConstructor, data: CheckerContext) {
        allConstructorCheckers.check(errorPrimaryConstructor, data)
    }

    override fun visitAnonymousFunction(anonymousFunction: FirAnonymousFunction, data: CheckerContext) {
        allAnonymousFunctionCheckers.check(anonymousFunction, data)
    }

    override fun visitPropertyAccessor(propertyAccessor: FirPropertyAccessor, data: CheckerContext) {
        allPropertyAccessorCheckers.check(propertyAccessor, data)
    }

    override fun visitBackingField(backingField: FirBackingField, data: CheckerContext) {
        allBackingFieldCheckers.check(backingField, data)
    }

    override fun visitValueParameter(valueParameter: FirValueParameter, data: CheckerContext) {
        allValueParameterCheckers.check(valueParameter, data)
    }

    override fun visitTypeParameter(typeParameter: FirTypeParameter, data: CheckerContext) {
        allTypeParameterCheckers.check(typeParameter, data)
    }

    override fun visitEnumEntry(enumEntry: FirEnumEntry, data: CheckerContext) {
        allEnumEntryCheckers.check(enumEntry, data)
    }

    override fun visitAnonymousObject(anonymousObject: FirAnonymousObject, data: CheckerContext) {
        allAnonymousObjectCheckers.check(anonymousObject, data)
    }

    override fun visitAnonymousInitializer(anonymousInitializer: FirAnonymousInitializer, data: CheckerContext) {
        allAnonymousInitializerCheckers.check(anonymousInitializer, data)
    }

    override fun visitField(field: FirField, data: CheckerContext) {
        allCallableDeclarationCheckers.check(field, data)
    }

    override fun visitDanglingModifierList(danglingModifierList: FirDanglingModifierList, data: CheckerContext) {
        allBasicDeclarationCheckers.check(danglingModifierList, data)
    }

    override fun visitErrorProperty(errorProperty: FirErrorProperty, data: CheckerContext) {
        allCallableDeclarationCheckers.check(errorProperty, data)
    }

    override fun visitScript(script: FirScript, data: CheckerContext) {
        allScriptCheckers.check(script, data)
    }

    override fun visitCodeFragment(codeFragment: FirCodeFragment, data: CheckerContext) {
        allBasicDeclarationCheckers.check(codeFragment, data)
    }

    private inline fun <reified D : FirDeclaration> Array<FirDeclarationChecker<D>>.check(
        declaration: D,
        context: CheckerContext
    ) {
        for (checker in this) {
            try {
                checker.check(declaration, context, reporter)
            } catch (e: Exception) {
                rethrowExceptionWithDetails("Exception in declaration checker", e) {
                    withFirEntry("declaration", declaration)
                    context.containingFilePath?.let { withEntry("file", it) }
                }
            }
        }
    }
}
