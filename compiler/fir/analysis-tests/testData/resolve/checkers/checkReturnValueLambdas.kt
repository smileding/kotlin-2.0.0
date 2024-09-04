// WITH_STDLIB

fun stringF(): String = ""

fun Any.consume(): Unit = Unit

fun returnsExp() = stringF()

fun returnsBody(): String {
    return stringF()
}

fun vals() {
    val used: String
    used = stringF()
    lateinit var used2: String
    used2 = stringF()
}

class Inits {
    val init1 = stringF()

    val explicit: String
        get() = stringF()

    val unused: String
        get() {
            <!RETURN_VALUE_NOT_USED!>stringF()<!>
            return ""
        }
}

fun defaultValue(param: String = stringF()) {}

fun main() {
    val used = stringF() // used
    println(stringF()) // used
    stringF().consume() // used
    <!RETURN_VALUE_NOT_USED!>stringF().toString()<!> // unused
    <!RETURN_VALUE_NOT_USED!>stringF()<!> // unused
}
