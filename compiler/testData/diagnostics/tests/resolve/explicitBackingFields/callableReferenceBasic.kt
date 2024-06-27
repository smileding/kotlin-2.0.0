// LANGUAGE: +ExplicitBackingFields
// FIR_IDENTICAL

class Test {
    val prop: String
        field: Int = 42
        get() = field.toString()

    fun inside() {
        val p: () -> Int = ::<!UNSUPPORTED!>prop<!>
        val p2: () -> String = ::<!UNSUPPORTED!>prop<!>
    }
}

fun outside(t: Test) {
    val p: () -> Int = <!INITIALIZER_TYPE_MISMATCH!>t::prop<!>
    val p2: () -> String = t::prop
}
