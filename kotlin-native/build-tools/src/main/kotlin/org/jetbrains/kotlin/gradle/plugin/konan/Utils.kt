/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.konan

import org.gradle.api.file.Directory

/**
 * Prepare `this` to be an output for the task:
 * * delete if exists
 * * make sure all parent directories exist
 */
internal fun Directory.prepareAsOutputDirectory() = with(asFile) {
    val deleted = deleteRecursively()
    check(deleted) { "Failed to delete $absolutePath" }
    check(parentFile.exists()) { "Parent directory for $absolutePath does not exist" }
}

internal fun Collection<String>.toPrettyString(): String = buildString {
    append('[')
    if (this@toPrettyString.isNotEmpty()) append('\n')
    this@toPrettyString.forEach { append('\t').append(it.toPrettyString()).append('\n') }
    append(']')
}

internal fun String.toPrettyString(): String =
        when {
            isEmpty() -> "\"\""
            any { it == '"' || it.isWhitespace() } -> '"' + escapeStringCharacters() + '"'
            else -> this
        }

private fun String.escapeStringCharacters(): String {
    val buffer = StringBuilder(length)
    escapeStringCharacters(length, "\"", true, true, buffer)
    return buffer.toString()
}

private fun String.escapeStringCharacters(
        length: Int,
        additionalChars: String?,
        escapeSlash: Boolean,
        escapeUnicode: Boolean,
        buffer: StringBuilder
): StringBuilder {
    var prev = 0.toChar()
    for (idx in 0..<length) {
        val ch = this[idx]
        when (ch) {
            '\b' -> buffer.append("\\b")
            '\t' -> buffer.append("\\t")
            '\n' -> buffer.append("\\n")
            '\u000c' -> buffer.append("\\f")
            '\r' -> buffer.append("\\r")
            else -> if (escapeSlash && ch == '\\') {
                buffer.append("\\\\")
            } else if (additionalChars != null && additionalChars.indexOf(ch) > -1 && (escapeSlash || prev != '\\')) {
                buffer.append("\\").append(ch)
            } else if (escapeUnicode && !isPrintableUnicode(ch)) {
                val hexCode: CharSequence = Integer.toHexString(ch.code).uppercase()
                buffer.append("\\u")
                var paddingCount = 4 - hexCode.length
                while (paddingCount-- > 0) {
                    buffer.append(0)
                }
                buffer.append(hexCode)
            } else {
                buffer.append(ch)
            }
        }
        prev = ch
    }
    return buffer
}

private fun isPrintableUnicode(c: Char): Boolean {
    val t = Character.getType(c)
    return t != Character.UNASSIGNED.toInt() &&
            t != Character.LINE_SEPARATOR.toInt() &&
            t != Character.PARAGRAPH_SEPARATOR.toInt() &&
            t != Character.CONTROL.toInt() &&
            t != Character.FORMAT.toInt() &&
            t != Character.PRIVATE_USE.toInt() &&
            t != Character.SURROGATE.toInt()
}