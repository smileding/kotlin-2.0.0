package org.jetbrains.kotlin.objcexport.mangling

import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.objcexport.ObjCExportContext

class SwiftMethodMangler(
    private val disableMemberMangling: Boolean,
    private val ignoreInterfaceMethodCollisions: Boolean,
) : Mangler<KaFunctionSymbol, String>() {

    override fun ObjCExportContext.conflict(first: KaFunctionSymbol, second: KaFunctionSymbol): Boolean {
        if (disableMemberMangling) return false
        return !canHaveSameSelector(first, second, ignoreInterfaceMethodCollisions)
    }
}