/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.ir2wasm

import org.jetbrains.kotlin.backend.wasm.utils.StronglyConnectedComponents
import org.jetbrains.kotlin.utils.yieldIfNotNull
import org.jetbrains.kotlin.wasm.ir.*

typealias RecursiveTypeGroup = List<WasmTypeDeclaration>

private fun WasmType.toTypeDeclaration(): WasmTypeDeclaration? {
    val heapType = when (val type = this) {
        is WasmRefType -> type.heapType
        is WasmRefNullType -> type.heapType
        else -> null
    }
    return (heapType as? WasmHeapType.Type)?.type?.owner
}

private fun dependencyTypes(type: WasmTypeDeclaration): Sequence<WasmTypeDeclaration> = sequence {
    when (type) {
        is WasmStructDeclaration -> {
            for (field in type.fields) {
                yieldIfNotNull(field.type.toTypeDeclaration())
            }
            yieldIfNotNull(type.superType?.owner)
        }
        is WasmArrayDeclaration -> {
            yieldIfNotNull(type.field.type.toTypeDeclaration())
        }
        is WasmFunctionType -> {
            for (parameter in type.parameterTypes) {
                yieldIfNotNull(parameter.toTypeDeclaration())
            }
            for (parameter in type.resultTypes) {
                yieldIfNotNull(parameter.toTypeDeclaration())
            }
        }
    }
}

private fun wasmTypeDeclarationOrderKey(declaration: WasmTypeDeclaration): Int {
    return when (declaration) {
        is WasmArrayDeclaration -> 0
        is WasmFunctionType -> 0
        is WasmStructDeclaration ->
            // Subtype depth
            declaration.superType?.let { wasmTypeDeclarationOrderKey(it.owner) + 1 } ?: 0
    }
}


fun createRecursiveTypeGroups(types: Sequence<WasmTypeDeclaration>): List<RecursiveTypeGroup> {
    val componentFinder = StronglyConnectedComponents(::dependencyTypes)
    types.forEach(componentFinder::visit)

    val components = componentFinder.findComponents()

    components.forEach { component ->
        component.sortBy(::wasmTypeDeclarationOrderKey)
    }

    return components
}