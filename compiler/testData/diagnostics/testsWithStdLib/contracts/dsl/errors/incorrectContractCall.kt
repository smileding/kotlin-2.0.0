// FIR_DUMP
// OPT_IN: kotlin.contracts.ExperimentalContracts
import kotlin.contracts.*

fun test_1() {
    <!ERROR_IN_CONTRACT_DESCRIPTION!>contract<!NO_VALUE_FOR_PARAMETER!>()<!><!>
}

fun test_2() {
    contract(<!CONSTANT_EXPECTED_TYPE_MISMATCH, ERROR_IN_CONTRACT_DESCRIPTION!>10<!>)
}

fun test_3() {
    contract(<!ERROR_IN_CONTRACT_DESCRIPTION, TYPE_MISMATCH!>""<!>) <!TOO_MANY_ARGUMENTS!>{}<!>
}

fun test_4() {
    contract { <!ERROR_IN_CONTRACT_DESCRIPTION!>return@contract <!CONSTANT_EXPECTED_TYPE_MISMATCH, CONSTANT_EXPECTED_TYPE_MISMATCH!>10<!><!> }
}
