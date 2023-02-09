/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("unused")  // Used by compiler

package kotlin.wasm.internal

internal const val TYPE_INFO_ELEMENT_SIZE = 4

internal const val TYPE_INFO_TYPE_PACKAGE_NAME_LENGTH_OFFSET = 0
internal const val TYPE_INFO_TYPE_PACKAGE_NAME_ID_OFFSET = TYPE_INFO_TYPE_PACKAGE_NAME_LENGTH_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_TYPE_PACKAGE_NAME_PRT_OFFSET = TYPE_INFO_TYPE_PACKAGE_NAME_ID_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_TYPE_SIMPLE_NAME_LENGTH_OFFSET = TYPE_INFO_TYPE_PACKAGE_NAME_PRT_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_TYPE_SIMPLE_NAME_ID_OFFSET = TYPE_INFO_TYPE_SIMPLE_NAME_LENGTH_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_TYPE_SIMPLE_NAME_PRT_OFFSET = TYPE_INFO_TYPE_SIMPLE_NAME_ID_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_SUPER_TYPE_LIST_SIZE_OFFSET = TYPE_INFO_TYPE_SIMPLE_NAME_PRT_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_INTERFACE_LIST_SIZE_OFFSET = TYPE_INFO_SUPER_TYPE_LIST_SIZE_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_SUPER_TYPE_LIST_OFFSET = TYPE_INFO_INTERFACE_LIST_SIZE_OFFSET + TYPE_INFO_ELEMENT_SIZE

internal class TypeInfoData(val typeId: Int, val packageName: String, val typeName: String)

internal val TypeInfoData.isInterfaceType
    get() = typeId < 0

internal fun getTypeInfoTypeDataByPtr(typeInfoPtr: Int): TypeInfoData {
    val packageName = getPackageName(typeInfoPtr)
    val simpleName = getSimpleName(typeInfoPtr)
    return TypeInfoData(typeInfoPtr, packageName, simpleName)
}

internal fun getSimpleName(typeInfoPtr: Int) = getString(
    typeInfoPtr,
    TYPE_INFO_TYPE_SIMPLE_NAME_LENGTH_OFFSET,
    TYPE_INFO_TYPE_SIMPLE_NAME_ID_OFFSET,
    TYPE_INFO_TYPE_SIMPLE_NAME_PRT_OFFSET
)

internal fun getPackageName(typeInfoPtr: Int) = getString(
    typeInfoPtr,
    TYPE_INFO_TYPE_PACKAGE_NAME_LENGTH_OFFSET,
    TYPE_INFO_TYPE_PACKAGE_NAME_ID_OFFSET,
    TYPE_INFO_TYPE_PACKAGE_NAME_PRT_OFFSET
)

private fun getString(typeInfoPtr: Int, lengthOffset: Int, idOffset: Int, ptrOffset: Int): String {
    val length = wasm_i32_load(typeInfoPtr + lengthOffset)
    val id = wasm_i32_load(typeInfoPtr + idOffset)
    val ptr = wasm_i32_load(typeInfoPtr + ptrOffset)
    return stringLiteral(id, ptr, length)
}

internal fun isInterfaceById(obj: Any, interfaceId: Int): Boolean {
    val superTypeListSize = wasm_i32_load(obj.typeInfo + TYPE_INFO_SUPER_TYPE_LIST_SIZE_OFFSET)
    val interfacesListSize = wasm_i32_load(obj.typeInfo + TYPE_INFO_INTERFACE_LIST_SIZE_OFFSET)

    val interfaceListPtr = obj.typeInfo + TYPE_INFO_SUPER_TYPE_LIST_OFFSET + superTypeListSize * TYPE_INFO_ELEMENT_SIZE
    val interfaceListEndPtr = interfaceListPtr + interfacesListSize * TYPE_INFO_ELEMENT_SIZE

    var currentPtr = interfaceListPtr
    while (currentPtr < interfaceListEndPtr) {
        if (interfaceId == wasm_i32_load(currentPtr)) {
            return true
        }
        currentPtr += TYPE_INFO_ELEMENT_SIZE
    }
    return false
}

internal fun isSupertypeByTypeInfo(obj: Any, typeData: TypeInfoData): Boolean {
    val objPtr = obj.typeInfo
    val typeDataPtr = typeData.typeId
    val objSuperTypesListSize = wasm_i32_load(objPtr + TYPE_INFO_SUPER_TYPE_LIST_SIZE_OFFSET)
    val typeDataTypesListSize = wasm_i32_load(typeDataPtr + TYPE_INFO_SUPER_TYPE_LIST_SIZE_OFFSET)
    if (objSuperTypesListSize < typeDataTypesListSize) return false
    val superTypeOnPositionPtr = objPtr + TYPE_INFO_SUPER_TYPE_LIST_OFFSET + (typeDataTypesListSize - 1) * TYPE_INFO_ELEMENT_SIZE
    return wasm_i32_load(superTypeOnPositionPtr) == typeDataPtr
}

@Suppress("UNUSED_PARAMETER")
@ExcludedFromCodegen
internal fun <T> wasmIsInterface(obj: Any): Boolean =
    implementedAsIntrinsic

@ExcludedFromCodegen
internal fun <T> wasmTypeId(): Int =
    implementedAsIntrinsic
