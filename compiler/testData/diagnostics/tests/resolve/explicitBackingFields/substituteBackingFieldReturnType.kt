// LANGUAGE: +ExplicitBackingFields
// WITH_STDLIB
// FIR_IDENTICAL
class Test<T>(t: T) {
    val prop: String
        field: T = t
        get() = field.toString()

    fun testOther(other: Test<Int>): Int {
        return other.prop
    }
}
