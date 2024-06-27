// LANGUAGE: +ExplicitBackingFields
// WITH_STDLIB
// FIR_IDENTICAL

// FILE: JavaParent.java
public class JavaParent {
    public final String prop = ""; // (1)
}

// FILE: main.kt
class Test : JavaParent() {
    val prop: String
        field: Int = 1
        get() = "" // (2)


    operator fun String.invoke(): String = this

    fun test(): String {
        return prop() // resolved to (1)
    }
}
