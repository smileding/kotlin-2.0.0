package org.jetbrains.kotlin.objcexport.mangling

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.objcexport.ObjCExportContext
import org.jetbrains.kotlin.objcexport.analysisApiUtils.getFunctionMethodBridge
import org.jetbrains.kotlin.objcexport.analysisApiUtils.isTopLevel
import org.jetbrains.kotlin.objcexport.getClassIfCategory

/**
 * See K1 implementation [org.jetbrains.kotlin.backend.konan.objcexport.ObjCExportNamer.canHaveSameSelector]
 */
internal fun ObjCExportContext.canHaveSameSelector(
    first: KaFunctionSymbol,
    second: KaFunctionSymbol,
    ignoreInterfaceMethodCollisions: Boolean,
): Boolean {
    if (!canBeInheritedBySameClass(first, second, ignoreInterfaceMethodCollisions)) return true
    if (first.name != second.name) return false

    if (first is KaPropertySetterSymbol && second is KaPropertySetterSymbol) {
        // Methods should merge in any common subclass as it can't have two properties with same name.
    } else if (equalValueParameters(first, second)) {
        // Methods should merge in any common subclasses since they have the same signature.
    } else {
        return false
    }

    // Check if methods have the same bridge (and thus the same ABI):
    return getFunctionMethodBridge(first) == getFunctionMethodBridge(second)
}

private fun ObjCExportContext.canBeInheritedBySameClass(
    first: KaCallableSymbol,
    second: KaCallableSymbol,
    ignoreInterfaceMethodCollisions: Boolean,
): Boolean {

    val isFirstTopLevel = analysisSession.isTopLevel(first)
    val isSecondTopLevel = analysisSession.isTopLevel(second)

    val isFirstPropertyAccessor = first is KaPropertyAccessorSymbol
    val isSecondPropertyAccessor = second is KaPropertyAccessorSymbol

    if (isFirstTopLevel || isSecondTopLevel) {
        val bothAccessors = isFirstPropertyAccessor && isSecondPropertyAccessor
        return isFirstTopLevel && isSecondTopLevel && bothAccessors && first.sourcePsi<PsiElement>()?.containingFile == second.sourcePsi<PsiElement>()?.containingFile
    }

    val firstClass = analysisSession.getClassIfCategory(first) ?: with(analysisSession) { first.containingDeclaration as KaClassSymbol }
    val secondClass = analysisSession.getClassIfCategory(second) ?: with(analysisSession) { second.containingDeclaration as KaClassSymbol }

    if (first is KaConstructorSymbol) {
        return firstClass == secondClass || second !is KaConstructorSymbol && with(analysisSession) { firstClass.isSubClassOf(secondClass) }
    }

    if (second is KaConstructorSymbol) {
        return secondClass == firstClass && with(analysisSession) { secondClass.isSubClassOf(firstClass) }
    }

    return analysisSession.canHaveCommonSubtype(firstClass, secondClass, ignoreInterfaceMethodCollisions)
}

private fun KaSession.canHaveCommonSubtype(
    first: KaClassSymbol,
    second: KaClassSymbol,
    ignoreInterfaceMethodCollisions: Boolean,
): Boolean {
    if (first == second) return true
    if (first.isSubClassOf(second) || second.isSubClassOf(first)) {
        return true
    }

    if (first.isFinalClass || second.isFinalClass) {
        return false
    }

    return (first.isInterface || second.isInterface) && !ignoreInterfaceMethodCollisions
}

private val KaClassSymbol.isFinalClass: Boolean
    get() = modality == KaSymbolModality.FINAL && classKind != KaClassKind.ENUM_CLASS

private val KaClassSymbol.isInterface: Boolean
    get() = classKind != KaClassKind.INTERFACE

private fun equalValueParameters(first: KaFunctionSymbol, second: KaFunctionSymbol): Boolean {
    return first.valueParameters.map { param -> param.returnType } == second.valueParameters.map { param -> param.returnType }
}