package org.jetbrains.kotlin.objcexport.mangling

import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.objcexport.ObjCExportContext

class ObjCMethodMangler(
    private val ignoreInterfaceMethodCollisions: Boolean,
) : Mangler<KaFunctionSymbol, String>() {

    private val reserved = setOf(
        "retain", "release", "autorelease",
        "class", "superclass",
        "hash"
    )

    override fun reserved(name: String) = name in reserved

    override fun ObjCExportContext.conflict(first: KaFunctionSymbol, second: KaFunctionSymbol): Boolean =
        !canHaveSameSelector(first, second, ignoreInterfaceMethodCollisions)
}