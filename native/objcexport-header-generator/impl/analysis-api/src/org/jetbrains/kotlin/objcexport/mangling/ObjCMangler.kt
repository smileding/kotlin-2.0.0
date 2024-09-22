package org.jetbrains.kotlin.objcexport.mangling

import org.jetbrains.kotlin.objcexport.ObjCExportContext

/**
 * Instance of [ObjCMangler] holds references to symbols which names potentially must be mangled.
 *
 * Every group of symbols has it's own implementation:
 * - [ObjCMethodMangler]
 * - [SwiftMethodMangler]
 *
 * See K1 implementation [org.jetbrains.kotlin.backend.konan.objcexport.ObjCExportNamerImpl.Mapping]
 */
internal abstract class ObjCMangler<in T : Any, N> {

    /**
     * See [local] field at [org.jetbrains.kotlin.backend.konan.objcexport.ObjCExportNamerImpl]
     * The field needs to be either removed or properly set
     */
    private val local = false

    private val elementToName = mutableMapOf<T, N>()
    private val nameToElements = mutableMapOf<N, MutableList<T>>()

    abstract fun ObjCExportContext.conflict(first: T, second: T): Boolean
    open fun reserved(name: N) = false

    /**
     * [nameCandidates] must returns sequence which consists of:
     * 1. Non mangled first element, it will be used for symbol which met for the first time during traversal
     * 2. All other elements, where every next element consists of `previous + _`
     */
    protected fun getOrPut(context: ObjCExportContext, element: T, nameCandidates: () -> Sequence<N>): N {
        val cached = getIfAssigned(element)
        if (cached != null) return cached

        nameCandidates().forEach {
            if (with(context) { tryAssign(element, it) }) {
                return it
            }
        }

        error("name candidates run out")
    }

    fun nameExists(name: N) = nameToElements.containsKey(name)

    fun getIfAssigned(element: T): N? = elementToName[element]

    fun ObjCExportContext.tryAssign(element: T, name: N): Boolean {

        if (element in elementToName) error(element)
        if (reserved(name)) return false
        if (nameToElements[name].orEmpty().any { conflict(element, it) }) return false

        if (!local) {
            nameToElements.getOrPut(name) { mutableListOf() } += element
            elementToName[element] = name
        }

        return true
    }

    fun forceAssign(element: T, name: N) {
        if (name in nameToElements || element in elementToName) error(element)

        nameToElements[name] = mutableListOf(element)
        elementToName[element] = name
    }
}