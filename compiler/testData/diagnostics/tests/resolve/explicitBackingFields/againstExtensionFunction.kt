// LANGUAGE: +ExplicitBackingFields
// WITH_STDLIB
// FIR_IDENTICAL

class Base {
    val prop: List<String>
        field = mutableListOf()
        get() = field.toList()

    val String.prop: Int
        get() = 42

    fun String.withReceiver() {
        prop.inc() // if Int
        prop.<!UNRESOLVED_REFERENCE!>get<!>(0) // if List
        prop.<!UNRESOLVED_REFERENCE!>add<!>("") // if MutableList
    }
}
