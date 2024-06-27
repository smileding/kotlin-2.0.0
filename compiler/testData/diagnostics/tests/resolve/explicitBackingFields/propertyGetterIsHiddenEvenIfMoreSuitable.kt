// LANGUAGE: +ExplicitBackingFields
// WITH_STDLIB
// FIR_IDENTICAL
class Test {
    val prop: String
        field: Int = 1
        get() = field.toString()


    operator fun String.invoke(): String = this

    fun test(): String {
        return <!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>prop<!>()
    }
}
