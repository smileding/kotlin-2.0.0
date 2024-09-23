/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.lombok.k2.generators

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.containingClassForStaticMemberAttr
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.builder.buildConstructedClassTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.java.declarations.FirJavaConstructor
import org.jetbrains.kotlin.fir.java.declarations.FirJavaField
import org.jetbrains.kotlin.fir.java.declarations.FirJavaMethod
import org.jetbrains.kotlin.fir.java.declarations.buildJavaConstructor
import org.jetbrains.kotlin.fir.java.declarations.buildJavaMethod
import org.jetbrains.kotlin.fir.java.declarations.buildJavaValueParameter
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.toEffectiveVisibility
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.ConeSimpleKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.jvm.FirJavaTypeRef
import org.jetbrains.kotlin.load.java.structure.JavaClass
import org.jetbrains.kotlin.load.java.structure.JavaClassifierType
import org.jetbrains.kotlin.load.java.structure.JavaType
import org.jetbrains.kotlin.lombok.k2.config.ConeLombokAnnotations.Singular
import org.jetbrains.kotlin.lombok.k2.config.ConeLombokAnnotations.SuperBuilder
import org.jetbrains.kotlin.lombok.k2.java.DummyJavaClassType
import org.jetbrains.kotlin.lombok.k2.java.JavaClasses
import org.jetbrains.kotlin.lombok.k2.java.NullabilityJavaAnnotation
import org.jetbrains.kotlin.lombok.k2.java.toRef
import org.jetbrains.kotlin.lombok.k2.java.withAnnotations
import org.jetbrains.kotlin.lombok.utils.LombokNames
import org.jetbrains.kotlin.lombok.utils.capitalize
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

class ConeLombokValueParameter(val name: Name, val typeRef: FirTypeRef)

fun FirClassSymbol<*>.createJavaMethod(
    name: Name,
    valueParameters: List<ConeLombokValueParameter>,
    returnTypeRef: FirTypeRef,
    visibility: Visibility,
    modality: Modality,
    dispatchReceiverType: ConeSimpleKotlinType? = this.defaultType(),
    isStatic: Boolean = false
): FirJavaMethod {
    return buildJavaMethod {
        moduleData = this@createJavaMethod.moduleData
        this.returnTypeRef = returnTypeRef
        this.dispatchReceiverType = dispatchReceiverType
        this.name = name
        symbol = FirNamedFunctionSymbol(CallableId(classId, name))
        status = FirResolvedDeclarationStatusImpl(visibility, modality, visibility.toEffectiveVisibility(this@createJavaMethod)).apply {
            this.isStatic = isStatic
        }
        isFromSource = true
        annotationBuilder = { emptyList() }
        for (valueParameter in valueParameters) {
            this.valueParameters += buildJavaValueParameter {
                moduleData = this@createJavaMethod.moduleData
                this.returnTypeRef = valueParameter.typeRef
                containingFunctionSymbol = this@buildJavaMethod.symbol
                this.name = valueParameter.name
                annotationBuilder = { emptyList() }
                isVararg = false
                isFromSource = true
            }
        }
    }.apply {
        if (isStatic) {
            containingClassForStaticMemberAttr = this@createJavaMethod.toLookupTag()
        }
    }
}

fun FirClassSymbol<*>.createDefaultJavaConstructor(
    visibility: Visibility,
): FirJavaConstructor {
    val outerClassSymbol = this
    return buildJavaConstructor {
        moduleData = outerClassSymbol.moduleData
        isFromSource = true
        symbol = FirConstructorSymbol(classId)
        isInner = outerClassSymbol.rawStatus.isInner
        status = FirResolvedDeclarationStatusImpl(
            visibility,
            Modality.FINAL,
            visibility.toEffectiveVisibility(outerClassSymbol)
        ).apply {
            isExpect = false
            isActual = false
            isOverride = false
            isInner = this@buildJavaConstructor.isInner
        }
        isPrimary = false
        returnTypeRef = buildResolvedTypeRef {
            coneType = outerClassSymbol.defaultType()
        }
        dispatchReceiverType = if (isInner) outerClassSymbol.defaultType() else null
        typeParameters += outerClassSymbol.typeParameterSymbols.map { buildConstructedClassTypeParameterRef { symbol = it } }
        annotationBuilder = { emptyList() }
    }
}

fun createMethodsForSingularFields(
    setterPrefix: String?,
    singular: Singular,
    field: FirJavaField,
    builderClassSymbol: FirRegularClassSymbol,
    destination: MutableList<FirDeclaration>,
    returnTypeRef: FirTypeRef,
) {
    val fieldJavaTypeRef = field.returnTypeRef as? FirJavaTypeRef ?: return
    val javaClassifierType = fieldJavaTypeRef.type as? JavaClassifierType ?: return
    val typeName = (javaClassifierType.classifier as? JavaClass)?.fqName?.asString() ?: return

    val nameInSingularForm = (singular.singularName ?: field.name.identifier.singularForm)?.let(Name::identifier) ?: return

    val addMultipleParameterType: FirTypeRef
    val valueParameters: List<ConeLombokValueParameter>

    val fallbackParameterType = DummyJavaClassType.ObjectType.takeIf { javaClassifierType.isRaw }
    val source = builderClassSymbol.source?.fakeElement(KtFakeSourceElementKind.Enhancement)

    when (typeName) {
        in LombokNames.SUPPORTED_COLLECTIONS -> {
            val parameterType = javaClassifierType.parameterType(0) ?: fallbackParameterType ?: return
            valueParameters = listOf(
                ConeLombokValueParameter(nameInSingularForm, parameterType.toRef(source))
            )

            val baseType = when (typeName) {
                in LombokNames.SUPPORTED_GUAVA_COLLECTIONS -> JavaClasses.Iterable
                else -> JavaClasses.Collection
            }

            addMultipleParameterType = DummyJavaClassType(baseType, typeArguments = listOf(parameterType))
                .withProperNullability(singular.allowNull)
                .toRef(source)
        }

        in LombokNames.SUPPORTED_MAPS -> {
            val keyType = javaClassifierType.parameterType(0) ?: fallbackParameterType ?: return
            val valueType = javaClassifierType.parameterType(1) ?: fallbackParameterType ?: return
            valueParameters = listOf(
                ConeLombokValueParameter(Name.identifier("key"), keyType.toRef(source)),
                ConeLombokValueParameter(Name.identifier("value"), valueType.toRef(source)),
            )

            addMultipleParameterType = DummyJavaClassType(JavaClasses.Map, typeArguments = listOf(keyType, valueType))
                .withProperNullability(singular.allowNull)
                .toRef(source)
        }

        in LombokNames.SUPPORTED_TABLES -> {
            val rowKeyType = javaClassifierType.parameterType(0) ?: fallbackParameterType ?: return
            val columnKeyType = javaClassifierType.parameterType(1) ?: fallbackParameterType ?: return
            val valueType = javaClassifierType.parameterType(2) ?: fallbackParameterType ?: return

            valueParameters = listOf(
                ConeLombokValueParameter(Name.identifier("rowKey"), rowKeyType.toRef(source)),
                ConeLombokValueParameter(Name.identifier("columnKey"), columnKeyType.toRef(source)),
                ConeLombokValueParameter(Name.identifier("value"), valueType.toRef(source)),
            )

            addMultipleParameterType = DummyJavaClassType(
                JavaClasses.Table,
                typeArguments = listOf(rowKeyType, columnKeyType, valueType)
            ).withProperNullability(singular.allowNull).toRef(source)
        }

        else -> return
    }

    val visibility = Visibilities.DEFAULT_VISIBILITY

    destination += builderClassSymbol.createJavaMethod(
        name = nameInSingularForm.toMethodName(setterPrefix),
        valueParameters,
        returnTypeRef = returnTypeRef,
        modality = Modality.FINAL,
        visibility = visibility
    )

    destination += builderClassSymbol.createJavaMethod(
        name = field.name.toMethodName(setterPrefix),
        valueParameters = listOf(ConeLombokValueParameter(field.name, addMultipleParameterType)),
        returnTypeRef = returnTypeRef,
        modality = Modality.FINAL,
        visibility = visibility
    )

    destination += builderClassSymbol.createJavaMethod(
        name = Name.identifier("clear${field.name.identifier.capitalize()}"),
        valueParameters = listOf(),
        returnTypeRef = returnTypeRef,
        modality = Modality.FINAL,
        visibility = visibility
    )
}

fun Name.toMethodName(setterPrefix: String?): Name {
    return if (setterPrefix.isNullOrBlank()) {
        this
    } else {
        Name.identifier("${setterPrefix}${identifier.capitalize()}")
    }
}

private val String.singularForm: String?
    get() = StringUtil.unpluralize(this)

private fun JavaClassifierType.parameterType(index: Int): JavaType? {
    return typeArguments.getOrNull(index)
}

private fun JavaType.withProperNullability(allowNull: Boolean): JavaType {
    return if (allowNull) makeNullable() else makeNotNullable()
}

private fun JavaType.makeNullable(): JavaType = withAnnotations(annotations + NullabilityJavaAnnotation.Nullable)
private fun JavaType.makeNotNullable(): JavaType = withAnnotations(annotations + NullabilityJavaAnnotation.NotNull)

fun createSetterMethod(
    setterPrefix: String?,
    field: FirJavaField,
    builderClassSymbol: FirRegularClassSymbol,
    destination: MutableList<FirDeclaration>,
    visibility: Visibility = Visibilities.DEFAULT_VISIBILITY,
) {
    val fieldName = field.name
    val setterName = fieldName.toMethodName(setterPrefix)
    destination += builderClassSymbol.createJavaMethod(
        name = setterName,
        valueParameters = listOf(ConeLombokValueParameter(fieldName, field.returnTypeRef)),
        returnTypeRef = builderClassSymbol.defaultType().toFirResolvedTypeRef(),
        modality = Modality.FINAL,
        visibility = visibility
    )
}