/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("NOTHING_TO_INLINE")
package org.jetbrains.kotlin.fir.expressions

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

abstract class FirLambdaArgumentExpression : FirWrappedArgumentExpression() {
    abstract override val source: KtSourceElement?
    abstract override val typeRef: FirTypeRef
    abstract override val annotations: List<FirAnnotation>
    abstract override val expression: FirExpression
    abstract override val isSpread: Boolean


    abstract override fun replaceTypeRef(newTypeRef: FirTypeRef)

    abstract override fun replaceAnnotations(newAnnotations: List<FirAnnotation>)

    abstract override fun replaceExpression(newExpression: FirExpression)
}

inline fun <D> FirLambdaArgumentExpression.transformTypeRef(transformer: FirTransformer<D>, data: D): FirLambdaArgumentExpression  = 
    apply { replaceTypeRef(typeRef.transform(transformer, data)) }

inline fun <D> FirLambdaArgumentExpression.transformAnnotations(transformer: FirTransformer<D>, data: D): FirLambdaArgumentExpression  = 
    apply { replaceAnnotations(annotations.transform(transformer, data)) }

inline fun <D> FirLambdaArgumentExpression.transformExpression(transformer: FirTransformer<D>, data: D): FirLambdaArgumentExpression  = 
    apply { replaceExpression(expression.transform(transformer, data)) }
