// WITH_STDLIB

fun stringF(): String = ""
fun nsf(): String? = "null"

fun Any.consume(): Unit = Unit

fun returnsString(): String {
    nsf()?.let { return it } // inferred to Nothing
    return ""
}

fun main() {
    <!RETURN_VALUE_NOT_USED!>stringF().let { it }<!>
    <!RETURN_VALUE_NOT_USED!>stringF().let { 2 }<!>
}
