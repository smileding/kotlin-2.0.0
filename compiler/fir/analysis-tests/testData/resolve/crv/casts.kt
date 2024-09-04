// WITH_STDLIB

fun foo(a: Any) {
    <!RETURN_VALUE_NOT_USED!>a<!> as String
}
