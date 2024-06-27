// LANGUAGE: +ExplicitBackingFields
// WITH_STDLIB
// FIR_IDENTICAL
interface I {
    val prop: String
}

class Test : I {
    final override val prop: String
        field: Int = 1
        get() = ""

    fun test(): String {
        return (this as I).prop
    }
}