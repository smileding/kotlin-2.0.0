/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("NOTHING_TO_INLINE")
package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

interface FirAnnotationContainer : FirElement {
    override val source: KtSourceElement?
    val annotations: List<FirAnnotation>


    fun replaceAnnotations(newAnnotations: List<FirAnnotation>)
}

inline fun <D> FirAnnotationContainer.transformAnnotations(transformer: FirTransformer<D>, data: D): FirAnnotationContainer  = 
    apply { replaceAnnotations(annotations.transform(transformer, data)) }
