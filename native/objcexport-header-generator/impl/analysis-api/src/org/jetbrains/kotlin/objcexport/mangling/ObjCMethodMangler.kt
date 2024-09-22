package org.jetbrains.kotlin.objcexport.mangling

import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.objcexport.ObjCExportContext

internal class ObjCMethodMangler(
    private val ignoreInterfaceMethodCollisions: Boolean,
) : ObjCMangler<KaFunctionSymbol, String>() {

    private val reserved = setOf(
        "retain", "release", "autorelease",
        "class", "superclass",
        "hash"
    )

    override fun reserved(name: String) = name in reserved

    override fun ObjCExportContext.conflict(first: KaFunctionSymbol, second: KaFunctionSymbol): Boolean =
        !canHaveSameSelector(first, second, ignoreInterfaceMethodCollisions)

    fun mangeName(name: String, symbol: KaFunctionSymbol, noParameters: Boolean, context: ObjCExportContext): String {
        return getOrPut(context, symbol) {
            generateSequence(name) { selector ->
                buildString {
                    append(selector)
                    if (noParameters) append('_') else insert(lastIndex, '_')
                }
            }
        }
    }
}