// LANGUAGE: +ExplicitBackingFields
// WITH_STDLIB
// FIR_IDENTICAL
class Test {
    val prop: String
        field: Int = 42
        get() {
            val callPropInsideGetter: Int = prop
            return callPropInsideGetter.toString()
        }

    val prop2: String
        field: Int = idInt(prop2)
        get() = field.toString()

    fun idInt(i: Int): Int = i
}
