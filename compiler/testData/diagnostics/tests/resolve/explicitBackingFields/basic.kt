// LANGUAGE: +ExplicitBackingFields
// WITH_STDLIB
// FIR_IDENTICAL

class Base {
    val prop: List<String>
        field: MutableList<String> = mutableListOf()


    fun inside(): MutableList<String> {
        return prop
    }
}

fun outside(b: Base): MutableList<String> {
    return <!RETURN_TYPE_MISMATCH!>b.prop<!>
}
