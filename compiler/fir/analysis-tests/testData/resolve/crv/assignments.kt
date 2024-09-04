// WITH_STDLIB

fun stringF(): String = ""
fun nsf(): String? = "null"

fun Any.consume(): Unit = Unit

//fun lhs(map: MutableMap<String, String>) {
//    map["a"] = stringF()
//    map[stringF()] = "a"
//    map[stringF()] = nsf()!!
//}
//
//fun nested(map: List<MutableMap<String, String>>) {
//    map[0]["b"] = stringF()
//    map[0][stringF()] = stringF()
//}
//
//fun test_1(cards: List<List<MutableList<String>>>) {
//    cards[0][0][0] = stringF()
//}
//
//fun test_2(cards: List<MutableList<String>>) {
//    cards[0][0] = stringF()
//}

// MutableList.set
fun test_3(cards: MutableList<String>) {
    <!RETURN_VALUE_NOT_USED!>cards[0] = stringF()<!>
}
