// LANGUAGE: +ExplicitBackingFields
// WITH_STDLIB
// FIR_IDENTICAL

interface I {
    val prop: List<String>
}

abstract class Base : I {
    final override val prop: List<String>
        field = mutableListOf()
        get() = field.toList()

    fun testSmartCast(obj: I) {
        obj.prop.<!UNRESOLVED_REFERENCE!>add<!>("")
        if (obj is Base) {
            obj.prop.add("")
            if (obj is I2) {
                obj.prop.add("")
            }
        }
    }
}

interface I2 : I {
    override val prop: List<String>
        get() = listOf()
}
