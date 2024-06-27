// LANGUAGE: +ExplicitBackingFields
// WITH_STDLIB
// FIR_IDENTICAL
class Test<in T>(t: T) {
    val prop: String
        field: T = t
        get() = field.toString()

    fun test() {
        acceptT(prop)
    }

    private fun acceptT(t: T) {}

    fun <U> testOther(other: Test<U>): U {
        return other.<!INVISIBLE_REFERENCE!>prop<!>
    }
}
