package org.jetbrains.kotlin.objcexport.mangling

import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.objcexport.ObjCExportContext

internal class SwiftMethodMangler(
    private val disableMemberMangling: Boolean,
    private val ignoreInterfaceMethodCollisions: Boolean,
) : ObjCMangler<KaFunctionSymbol, String>() {

    override fun ObjCExportContext.conflict(first: KaFunctionSymbol, second: KaFunctionSymbol): Boolean {
        if (disableMemberMangling) return false
        return !canHaveSameSelector(first, second, ignoreInterfaceMethodCollisions)
    }

    fun mangeName(name: String, symbol: KaFunctionSymbol, context: ObjCExportContext): String {
        return getOrPut(context, symbol) {
            generateSequence(name) { selector ->
                buildString {
                    append(selector)
                    insert(lastIndex - 1, '_')
                }
            }
        }
    }
}