/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.sir

import org.jetbrains.kotlin.sir.util.SirSwiftModule

sealed interface SirType

open class SirNominalType(
    val typeDeclaration: SirNamedDeclaration,
    val typeArguments: List<SirType> = emptyList(),
    val parent: SirNominalType? = null,
) : SirType {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other != null && (this::class != other::class || other is SirOptionalType)) return false

        other as SirNominalType

        if (typeDeclaration != other.typeDeclaration) return false
        if (parent != other.parent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = typeDeclaration.hashCode()
        result = 31 * result + (parent?.hashCode() ?: 0)
        return result
    }
}

class SirOptionalType(type: SirType): SirNominalType(
    typeDeclaration = SirSwiftModule.optional,
    typeArguments = listOf(type)
) {
    val wrappedType: SirType = super.typeArguments.single()

    override fun equals(other: Any?): Boolean = super.equals(other)
}

class SirExistentialType(
    // TODO: Protocols. For now, only `any Any` is supported
) : SirType {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other != null && this::class != other::class) return false
        return true
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}

/**
 * A synthetic type for unknown Kotlin types. For example,
 * it might be an incomplete declaration in IDE or declaration from a not imported library.
 *
 */
class SirErrorType(val reason: String) : SirType

/**
 * A synthetic type for not yet supported Kotlin types.
 */
data object SirUnsupportedType : SirType

fun SirType.optional(): SirNominalType = SirOptionalType(this)
