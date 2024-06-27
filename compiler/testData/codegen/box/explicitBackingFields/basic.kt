// LANGUAGE: +ExplicitBackingFields
// IGNORE_BACKEND_K1: ANY
package test

interface I
class Impl : I
class Wrapper(i: Impl) : I by i

class Base {
    val prop: I
        field: Impl = Impl()
        get() = Wrapper(field)

    fun inside() = prop
}

fun outside(b: Base) = b.prop

fun box(): String {
    val v1 = Base().inside()
    if (v1::class.qualifiedName != "test.Impl") {
        return "FAIL on 1 condition"
    }
    val v2 = outside(Base())
    if (v2::class.qualifiedName != "test.Wrapper") {
        return "FAIL on 2 condition"
    }
    return "OK"
}
