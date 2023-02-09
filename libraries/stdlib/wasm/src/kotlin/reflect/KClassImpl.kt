/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package kotlin.wasm.internal

import kotlin.reflect.KClass

internal class KClassImpl<T : Any> @WasmPrimitiveConstructor constructor(internal val typeData: TypeInfoData) : KClass<T> {
    override val simpleName: String get() = typeData.typeName
    override val qualifiedName: String
        get() = if (typeData.packageName.isEmpty()) typeData.typeName else "${typeData.packageName}.${typeData.typeName}"

    override fun isInstance(value: Any?): Boolean {
        if (value !is Any) return false
        return when (typeData.isInterfaceType) {
            true -> isInterfaceById(value, typeData.typeId)
            false -> isSupertypeByTypeInfo(value, typeData)
        }
    }

    override fun equals(other: Any?): Boolean =
        (this === other) || (other is KClassImpl<*> && other.typeData.typeId == typeData.typeId)

    override fun hashCode(): Int = typeData.typeId

    override fun toString(): String = "class $qualifiedName"
}