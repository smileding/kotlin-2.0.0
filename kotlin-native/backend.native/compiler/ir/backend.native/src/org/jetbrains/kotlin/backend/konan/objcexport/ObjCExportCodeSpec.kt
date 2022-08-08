/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.backend.konan.objcexport

import org.jetbrains.kotlin.backend.konan.descriptors.contributedMethods
import org.jetbrains.kotlin.backend.konan.descriptors.enumEntries
import org.jetbrains.kotlin.backend.konan.descriptors.isArray
import org.jetbrains.kotlin.backend.konan.descriptors.isInterface
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny


internal class ObjCCodeSpecBuilder(
        private val objcInterface: ObjCExportedInterface,
        private val symbolTable: SymbolTable
) {
    private val classToType = mutableMapOf<ClassDescriptor, ObjCTypeForKotlinType>()
    private val namer: ObjCExportNamer = objcInterface.namer

    private fun createObjCMethods(methods: List<FunctionDescriptor>) = methods.map {
        ObjCMethodForKotlinMethod(
                createObjCMethodSpecBaseMethod(
                        objcInterface.mapper,
                        namer,
                        symbolTable.referenceSimpleFunction(it),
                        it
                )
        )
    }

    private fun List<CallableMemberDescriptor>.toObjCMethods() = createObjCMethods(this.flatMap {
        when (it) {
            is PropertyDescriptor -> listOfNotNull(
                    it.getter,
                    it.setter?.takeIf(objcInterface.mapper::shouldBeExposed) // Similar to [ObjCExportTranslatorImpl.buildProperty].
            )

            is FunctionDescriptor -> listOf(it)
            else -> error(it)
        }
    })

    private fun getType(descriptor: ClassDescriptor): ObjCTypeForKotlinType = classToType.getOrPut(descriptor) {
        val methods: MutableList<ObjCMethodSpec> = mutableListOf()

        // Note: contributedMethods includes fake overrides too.
        val allBaseMethods = descriptor.contributedMethods.filter { objcInterface.mapper.shouldBeExposed(it) }
                .flatMap { objcInterface.mapper.getBaseMethods(it) }.distinct()

        methods += createObjCMethods(allBaseMethods)

        val binaryName = namer.getClassOrProtocolName(descriptor).binaryName
        val irClassSymbol = symbolTable.referenceClass(descriptor)

        if (descriptor.isInterface) {
            ObjCProtocolForKotlinInterface(binaryName, irClassSymbol, methods)
        } else {
            descriptor.constructors.filter { objcInterface.mapper.shouldBeExposed(it) }.mapTo(methods) {
                val irConstructorSymbol = symbolTable.referenceConstructor(it)
                val baseMethod = createObjCMethodSpecBaseMethod(objcInterface.mapper, namer, irConstructorSymbol, it)

                if (descriptor.isArray) {
                    ObjCFactoryMethodForKotlinArrayConstructor(baseMethod)
                } else {
                    ObjCInitMethodForKotlinConstructor(baseMethod)
                }
            }

            if (descriptor.kind == ClassKind.OBJECT) {
                methods += ObjCGetterForObjectInstance(namer.getObjectInstanceSelector(descriptor), irClassSymbol)
                methods += ObjCGetterForObjectInstance(namer.getObjectPropertySelector(descriptor), irClassSymbol)
            }

            if (descriptor.needCompanionObjectProperty(namer, objcInterface.mapper)) {
                methods += ObjCGetterForObjectInstance(namer.getCompanionObjectPropertySelector(descriptor),
                        symbolTable.referenceClass(descriptor.companionObjectDescriptor!!))
            }

            if (descriptor.kind == ClassKind.ENUM_CLASS) {
                descriptor.enumEntries.mapTo(methods) {
                    ObjCGetterForKotlinEnumEntry(symbolTable.referenceEnumEntry(it), namer.getEnumEntrySelector(it))
                }

                descriptor.getEnumValuesFunctionDescriptor()?.let {
                    methods += ObjCClassMethodForKotlinEnumValues(
                            symbolTable.referenceSimpleFunction(it),
                            namer.getEnumValuesSelector(it)
                    )
                }
            }

            if (KotlinBuiltIns.isThrowable(descriptor)) {
                methods += ObjCKotlinThrowableAsErrorMethod
            }

            val categoryMethods = objcInterface.categoryMembers[descriptor].orEmpty().toObjCMethods()

            val superClassNotAny = descriptor.getSuperClassNotAny()
                    ?.let { getType(it) as ObjCClassForKotlinClass }

            ObjCClassForKotlinClass(binaryName, irClassSymbol, methods, categoryMethods, superClassNotAny)
        }
    }

    fun build(): ObjCExportCodeSpec {
        val files = objcInterface.topLevel.map { (sourceFile, declarations) ->
            val binaryName = objcInterface.namer.getFileClassName(sourceFile).binaryName
            val methods = declarations.toObjCMethods()
            ObjCClassForKotlinFile(binaryName, sourceFile, methods)
        }
        val types = objcInterface.generatedClasses.map(::getType)
        return ObjCExportCodeSpec(files, types)
    }
}


internal fun <S : IrFunctionSymbol> createObjCMethodSpecBaseMethod(
        mapper: ObjCExportMapper,
        namer: ObjCExportNamer,
        symbol: S,
        descriptor: FunctionDescriptor
): ObjCMethodSpec.BaseMethod<S> {
    require(mapper.isBaseMethod(descriptor))

    val selector = namer.getSelector(descriptor)
    val bridge = mapper.bridgeMethod(descriptor)

    return ObjCMethodSpec.BaseMethod(symbol, bridge, selector)
}

internal class ObjCExportCodeSpec(
        val files: List<ObjCClassForKotlinFile>,
        val types: List<ObjCTypeForKotlinType>
)

internal sealed class ObjCMethodSpec {
    /**
     * Aggregates base method (as defined by [ObjCExportMapper.isBaseMethod])
     * and details required to generate code for bridges between Kotlin and Obj-C methods.
     */
    data class BaseMethod<out S : IrFunctionSymbol>(val symbol: S, val bridge: MethodBridge, val selector: String)
}

internal class ObjCMethodForKotlinMethod(val baseMethod: BaseMethod<IrSimpleFunctionSymbol>) : ObjCMethodSpec() {
    override fun toString(): String =
            "ObjC spec of method `${baseMethod.selector}` for `${baseMethod.symbol}`"
}

internal class ObjCInitMethodForKotlinConstructor(val baseMethod: BaseMethod<IrConstructorSymbol>) : ObjCMethodSpec() {
    override fun toString(): String =
            "ObjC spec of method `${baseMethod.selector}` for `${baseMethod.symbol}`"
}

internal class ObjCFactoryMethodForKotlinArrayConstructor(
        val baseMethod: BaseMethod<IrConstructorSymbol>
) : ObjCMethodSpec() {
    override fun toString(): String =
            "ObjC spec of factory ${baseMethod.selector} for ${baseMethod.symbol}"
}

internal class ObjCGetterForKotlinEnumEntry(
        val irEnumEntrySymbol: IrEnumEntrySymbol,
        val selector: String
) : ObjCMethodSpec() {
    override fun toString(): String =
            "ObjC spec of getter `$selector` for `$irEnumEntrySymbol`"
}

internal class ObjCClassMethodForKotlinEnumValues(
        val valuesFunctionSymbol: IrFunctionSymbol,
        val selector: String
) : ObjCMethodSpec() {
    override fun toString(): String =
            "ObjC spec of method `$selector` for $valuesFunctionSymbol"
}

internal class ObjCGetterForObjectInstance(val selector: String, val classSymbol: IrClassSymbol) : ObjCMethodSpec() {
    override fun toString(): String =
            "ObjC spec of instance getter `$selector` for $classSymbol"
}

internal object ObjCKotlinThrowableAsErrorMethod : ObjCMethodSpec() {
    override fun toString(): String =
            "ObjC spec for ThrowableAsError method"
}

internal sealed class ObjCTypeSpec(val binaryName: String)

internal sealed class ObjCTypeForKotlinType(
        binaryName: String,
        val irClassSymbol: IrClassSymbol,
        val methods: List<ObjCMethodSpec>
) : ObjCTypeSpec(binaryName)

internal class ObjCClassForKotlinClass(
        binaryName: String,
        irClassSymbol: IrClassSymbol,
        methods: List<ObjCMethodSpec>,
        val categoryMethods: List<ObjCMethodForKotlinMethod>,
        val superClassNotAny: ObjCClassForKotlinClass?
) : ObjCTypeForKotlinType(binaryName, irClassSymbol, methods) {
    override fun toString(): String =
            "ObjC spec of class `$binaryName` for `$irClassSymbol`"
}

internal class ObjCProtocolForKotlinInterface(
        binaryName: String,
        irClassSymbol: IrClassSymbol,
        methods: List<ObjCMethodSpec>
) : ObjCTypeForKotlinType(binaryName, irClassSymbol, methods) {

    override fun toString(): String =
            "ObjC spec of protocol `$binaryName` for `$irClassSymbol`"
}

internal class ObjCClassForKotlinFile(
        binaryName: String,
        private val sourceFile: SourceFile,
        val methods: List<ObjCMethodForKotlinMethod>
) : ObjCTypeSpec(binaryName) {
    override fun toString(): String =
            "ObjC spec of class `$binaryName` for `${sourceFile.name}`"
}