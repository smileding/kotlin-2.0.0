// LANGUAGE: +ExplicitBackingFields
// WITH_STDLIB
// FIR_IDENTICAL

class Base {
    val prop: (Int) -> Int
        field: (String) -> String = { "" }
        get() = { field(it.toString()).toInt() }


    fun test(): String {
        return prop("s")
    }
}
