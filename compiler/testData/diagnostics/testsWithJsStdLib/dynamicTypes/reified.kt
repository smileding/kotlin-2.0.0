// FIR_IGNORE
// FIR_IDENTICAL
// !DIAGNOSTICS: -UNUSED_PARAMETER -REIFIED_TYPE_PARAMETER_NO_INLINE

fun <reified T> foo(t: T) {}
class C<reified T>(t: T)

fun test(d: dynamic) {
    foo<<!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>dynamic<!>>(d)
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!>(d)

    C<<!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>dynamic<!>>(d)
    <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>C<!>(d)
}