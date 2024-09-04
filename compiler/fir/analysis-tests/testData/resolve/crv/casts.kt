// WITH_STDLIB

fun getAny(): Any = ""

fun foo(a: Any) {
    a as String
}

fun bar(a: Any) {
    a is String
}

fun foo2() {
    getAny() as String
}

fun bar2() {
    getAny() is String
}
