// FIR_DUMP
// OPT_IN: kotlin.contracts.ExperimentalContracts
import kotlin.contracts.*

fun test_1() {
    contract<!NO_VALUE_FOR_PARAMETER!>()<!>
}

fun test_2() {
    contract(<!ARGUMENT_TYPE_MISMATCH!>10<!>)
}

fun test_3() {
    contract(<!ARGUMENT_TYPE_MISMATCH!>""<!>) <!TOO_MANY_ARGUMENTS!>{}<!>
}

fun test_4() {
    contract { return@contract <!ARGUMENT_TYPE_MISMATCH, RETURN_TYPE_MISMATCH!>10<!> }
}
