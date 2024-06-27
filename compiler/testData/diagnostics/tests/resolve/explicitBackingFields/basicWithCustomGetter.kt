// LANGUAGE: +ExplicitBackingFields
// WITH_STDLIB
// FIR_IDENTICAL

class Base {
    val prop: String
        field: Int = 42
        get() = field.toString()


    fun inside(): Int {
        return prop
    }
}

fun outside(b: Base): Int {
    return <!RETURN_TYPE_MISMATCH!>b.prop<!>
}
