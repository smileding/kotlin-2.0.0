/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.test.cases.components.visibilityChecker

import org.jetbrains.kotlin.analysis.api.impl.base.test.SymbolByFqName
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.test.framework.base.AbstractAnalysisApiBasedTest
import org.jetbrains.kotlin.analysis.test.framework.services.expressionMarkerProvider
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions

/**
 * To find the element for the use-site position, the visibility checker test looks for an element called "useSite" in the main module if
 * the main file doesn't or cannot contain a caret marker, e.g. in files from binary libraries. The target name is case-insensitive, so
 * classes called `UseSite` will be found as well.
 */
private const val USE_SITE_ELEMENT_NAME = "usesite"

/**
 * Checks whether a declaration is visible from a specific use-site file and element.
 *
 * The declaration symbol is found via a symbol name at the bottom of the test file, such as `// class: Declaration` (see [SymbolByFqName]).
 */
abstract class AbstractVisibilityCheckerTest : AbstractAnalysisApiBasedTest() {
    override fun doTestByMainFile(mainFile: KtFile, mainModule: TestModule, testServices: TestServices) {
        val actualText = analyseForTest(mainFile) {
            val declarationSymbol = with(SymbolByFqName.getSymbolDataFromFile(testDataPath)) {
                toSymbols(mainFile).singleOrNull() as? KtSymbolWithVisibility
                    ?: error("Expected a single target `${KtSymbolWithVisibility::class.simpleName}` to be specified.")
            }

            val useSiteElement = testServices.expressionMarkerProvider.getElementOfTypeAtCaretOrNull<KtExpression>(mainFile)
                ?: findFirstUseSiteElement(mainFile)
                ?: error("Cannot find use-site element to check visibility at.")

            val useSiteFileSymbol = mainFile.getFileSymbol()

            val visible = isVisible(declarationSymbol, useSiteFileSymbol, null, useSiteElement)
            """
                Declaration: ${(declarationSymbol as KtDeclarationSymbol).render()}
                At usage site: ${useSiteElement.text}
                Is visible: $visible
            """.trimIndent()
        }

        testServices.assertions.assertEqualsToTestDataFileSibling(actualText)
    }

    private fun findFirstUseSiteElement(ktFile: KtFile): KtNamedDeclaration? =
        ktFile.findDescendantOfType<KtNamedDeclaration> { it.name?.lowercase() == USE_SITE_ELEMENT_NAME }
}
