/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("NOTHING_TO_INLINE")
package org.jetbrains.kotlin.fir.declarations

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

interface FirTypeParameterRef : FirElement {
    override val source: KtSourceElement?
    val symbol: FirTypeParameterSymbol

}
