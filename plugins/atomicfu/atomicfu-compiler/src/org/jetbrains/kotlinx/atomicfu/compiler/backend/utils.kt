/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.atomicfu.compiler.backend

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parents
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlinx.atomicfu.compiler.backend.common.AbstractAtomicfuTransformer.Companion.AFU_PKG
import org.jetbrains.kotlinx.atomicfu.compiler.backend.common.AbstractAtomicfuTransformer.Companion.APPEND
import org.jetbrains.kotlinx.atomicfu.compiler.backend.common.AbstractAtomicfuTransformer.Companion.ATOMICFU
import org.jetbrains.kotlinx.atomicfu.compiler.backend.common.AbstractAtomicfuTransformer.Companion.ATOMIC_ARRAY_TYPES
import org.jetbrains.kotlinx.atomicfu.compiler.backend.common.AbstractAtomicfuTransformer.Companion.ATOMIC_HANDLER
import org.jetbrains.kotlinx.atomicfu.compiler.backend.common.AbstractAtomicfuTransformer.Companion.ATOMIC_VALUE_FACTORY
import org.jetbrains.kotlinx.atomicfu.compiler.backend.common.AbstractAtomicfuTransformer.Companion.ATOMIC_VALUE_TYPES
import org.jetbrains.kotlinx.atomicfu.compiler.backend.common.AbstractAtomicfuTransformer.Companion.GET
import org.jetbrains.kotlinx.atomicfu.compiler.backend.common.AbstractAtomicfuTransformer.Companion.INVOKE
import org.jetbrains.kotlinx.atomicfu.compiler.backend.common.AbstractAtomicfuTransformer.Companion.TRACE_BASE_TYPE
import org.jetbrains.kotlinx.atomicfu.compiler.diagnostic.AtomicfuErrorMessages.CONSTRAINTS_MESSAGE

fun IrFunction.isFromKotlinxAtomicfuPackage(): Boolean = parentDeclarationContainer.kotlinFqName.asString().startsWith(AFU_PKG)

fun isPropertyOfAtomicfuType(declaration: IrDeclaration): Boolean =
    declaration is IrProperty && declaration.backingField?.type?.classFqName?.parent()?.asString() == AFU_PKG

fun IrProperty.isAtomic(): Boolean =
    !isDelegated && backingField?.type?.isAtomicValueType() ?: false

fun IrProperty.isDelegatedToAtomic(): Boolean =
    isDelegated && backingField?.type?.isAtomicValueType() ?: false

fun IrProperty.isAtomicArray(): Boolean =
    backingField?.type?.isAtomicArrayType() ?: false

fun IrProperty.isTrace(): Boolean =
    backingField?.type?.isTraceBaseType() ?: false

fun IrType.isAtomicValueType() =
    classFqName?.let {
        it.parent().asString() == AFU_PKG && it.shortName().asString() in ATOMIC_VALUE_TYPES
    } ?: false

fun IrType.isAtomicArrayType() =
    classFqName?.let {
        it.parent().asString() == AFU_PKG && it.shortName().asString() in ATOMIC_ARRAY_TYPES
    } ?: false

fun IrType.isTraceBaseType() =
    classFqName?.let {
        it.parent().asString() == AFU_PKG && it.shortName().asString() == TRACE_BASE_TYPE
    } ?: false

fun IrCall.isTraceInvoke(): Boolean =
    symbol.owner.isFromKotlinxAtomicfuPackage() &&
            symbol.owner.name.asString() == INVOKE &&
            symbol.owner.dispatchReceiverParameter?.type?.isTraceBaseType() == true

fun IrCall.isTraceAppend(): Boolean =
    symbol.owner.isFromKotlinxAtomicfuPackage() &&
            symbol.owner.name.asString() == APPEND &&
            symbol.owner.dispatchReceiverParameter?.type?.isTraceBaseType() == true

fun IrStatement.isTraceCall() = this is IrCall && (isTraceInvoke() || isTraceAppend())

fun IrCall.isArrayElementGetter(): Boolean =
    dispatchReceiver?.let {
        it.type.isAtomicArrayType() && symbol.owner.name.asString() == GET
    } ?: false

fun IrCall.isAtomicFactoryCall(): Boolean =
    symbol.owner.isFromKotlinxAtomicfuPackage() && symbol.owner.name.asString() == ATOMIC_VALUE_FACTORY &&
            type.isAtomicValueType()

fun IrFunction.isAtomicExtension(): Boolean =
    if (extensionReceiverParameter != null && extensionReceiverParameter!!.type.isAtomicValueType()) {
        require(this.isInline) {
            "Non-inline extension functions on kotlinx.atomicfu.Atomic* classes are not allowed, " +
                    "please add inline modifier to the function ${this.render()}."
        }
        require(this.visibility == DescriptorVisibilities.PRIVATE || this.visibility == DescriptorVisibilities.INTERNAL) {
            "Only private or internal extension functions on kotlinx.atomicfu.Atomic* classes are allowed, " +
                    "please make the extension function ${this.render()} private or internal."
        }
        true
    } else false

fun IrCall.getCorrespondingProperty(): IrProperty =
    symbol.owner.correspondingPropertySymbol?.owner
        ?: error("Atomic property accessor ${this.render()} expected to have non-null correspondingPropertySymbol" + CONSTRAINTS_MESSAGE)

fun IrExpression.isThisReceiver() =
    this is IrGetValue && symbol.owner.name.asString() == "<this>"

val IrDeclaration.parentDeclarationContainer: IrDeclarationContainer
    get() = parents.filterIsInstance<IrDeclarationContainer>().firstOrNull()
        ?: error("In the sequence of parents for ${this.render()} no IrDeclarationContainer was found" + CONSTRAINTS_MESSAGE)

val IrFunction.firstNonLocalFunctionForLambdaParent: IrFunction
    get() {
        if (this.origin != IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA) return this
        return parents.filterIsInstance<IrFunction>().firstOrNull {
            it.origin != IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }
            ?: error("In the sequence of parents for the local function ${this.render()} no containing function was found" + CONSTRAINTS_MESSAGE)
    }

// atomic(value = 0) -> 0
fun IrExpression.getAtomicFactoryValueArgument(): IrExpression {
    require(this is IrCall) { "Expected atomic factory invocation but found: ${this.render()}" }
    return getValueArgument(0)?.deepCopyWithSymbols()
        ?: error("Atomic factory should take at least one argument: ${this.render()}" + CONSTRAINTS_MESSAGE)
}

// AtomicIntArray(size = 10) -> 10
fun IrExpression.getArraySizeArgument(): IrExpression {
    require(this is IrFunctionAccessExpression) {
        "Expected atomic array factory invocation, but found: ${this.render()}."
    }
    return getValueArgument(0)?.deepCopyWithSymbols()
        ?: error("Atomic array factory should take at least one argument: ${this.render()}" + CONSTRAINTS_MESSAGE)
}

fun mangleAtomicExtension(name: String, atomicHandlerType: AtomicHandlerType, valueType: IrType) =
    name + "$" + ATOMICFU + "$" + atomicHandlerType + "$" + if (valueType.isPrimitiveType()) valueType.classFqName?.shortName() else "Any"

fun IrFunction.isTransformedAtomicExtension(): Boolean =
    name.asString().contains("\$$ATOMICFU") && valueParameters.isNotEmpty() && valueParameters[0].name.asString() == ATOMIC_HANDLER

fun IrValueParameter.capture(): IrGetValue = IrGetValueImpl(startOffset, endOffset, symbol.owner.type, symbol)

fun IrProperty.atomicfuRender(): String =
    (if (isVar) "var" else "val") + " " + name.asString() + ": " + backingField?.type?.render()