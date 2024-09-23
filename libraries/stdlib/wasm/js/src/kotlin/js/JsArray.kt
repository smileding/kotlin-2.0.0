/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.js

/** JavaScript Array */
@JsName("Array")
public external class JsArray<T : JsAny?> : JsAny {
    public val length: Int
}

public operator fun <T : JsAny?> JsArray<T>.get(index: Int): T? =
    jsArrayGet(this, index)

public operator fun <T : JsAny?> JsArray<T>.set(index: Int, value: T) {
    jsArraySet(this, index, value)
}

@Suppress("RedundantNullableReturnType", "UNUSED_PARAMETER")
private fun <T : JsAny?> jsArrayGet(array: JsArray<T>, index: Int): T? =
    js("array[index]")

@Suppress("UNUSED_PARAMETER")
private fun <T : JsAny?> jsArraySet(array: JsArray<T>, index: Int, value: T) {
    js("array[index] = value")
}

/** Returns a new array containing all the elements of this array. */
public fun <T : JsAny?> JsArray<T>.toArray(): Array<T> {
    val length = this.length
    val destination = arrayOfNulls<T>(length)
    for (i in 0 until length) {
        destination[i] = this[i]
    }
    @Suppress("UNCHECKED_CAST")
    return destination as Array<T>
}

/** Returns a new array containing all the elements of this array. */
public fun <T : JsAny?> Array<T>.toJsArray(): JsArray<T> {
    val destination = JsArray<T>()
    for (i in this.indices) {
        destination[i] = this[i]
    }
    return destination
}

/** Returns a new list containing all the elements of this array. */
public fun <T : JsAny?> JsArray<T>.toList(): List<T> {
    @Suppress("UNCHECKED_CAST")
    return List(length) { this[it] as T }
}

/** Returns a new array containing all the elements of this list. */
public fun <T : JsAny?> List<T>.toJsArray(): JsArray<T> {
    val destination = JsArray<T>()
    for (i in this.indices) {
        destination[i] = this[i]
    }
    return destination
}