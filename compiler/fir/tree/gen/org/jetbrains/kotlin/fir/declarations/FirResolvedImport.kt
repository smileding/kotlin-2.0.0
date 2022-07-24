/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("NOTHING_TO_INLINE")
package org.jetbrains.kotlin.fir.declarations

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirPureAbstractElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

abstract class FirResolvedImport : FirPureAbstractElement(), FirImport {
    abstract override val source: KtSourceElement?
    abstract override val importedFqName: FqName?
    abstract override val isAllUnder: Boolean
    abstract override val aliasName: Name?
    abstract override val aliasSource: KtSourceElement?
    abstract val delegate: FirImport
    abstract val packageFqName: FqName
    abstract val relativeParentClassName: FqName?
    abstract val resolvedParentClassId: ClassId?
    abstract val importedName: Name?


    abstract fun replaceDelegate(newDelegate: FirImport)
}

inline fun <D> FirResolvedImport.transformDelegate(transformer: FirTransformer<D>, data: D): FirResolvedImport  = 
    apply { replaceDelegate(delegate.transform(transformer, data)) }
