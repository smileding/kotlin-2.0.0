/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.contracts.impl

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.contracts.FirContractDescription
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor

/*
 * If a function has this contract description, it may mean two things:
 * 1. None of `contract {}` call or `contract []` block was found in the function at raw FIR stage
 * 2. In lazy bodies mode, the body was not comuted yet, so potentially function may have `contact {}` call in it
 */
object FirEmptyContractDescription : FirContractDescription() {
    override val source: KtSourceElement? get() = null

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {}

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirContractDescription {
        return this
    }
}

/*
 * If a function has this contract description, it means that at raw FIR stage it was detected that there is a `contact {}` call
 *   in the body, but this call is not correct `kotlin.contracts.contract` call. So there was an attempt to resolve the contract
 *   and it ended with lack of contract
 *
 * Having two different type of empty contract description is needed to distinguish those two situations in lazy resolution mode in AA
 * 1. Contract is resolved to empty description (`FirEmptyResolvedContractDescription`)
 * 2. Raw contract was not computed yet because of lazy body was not calculated yet (`FirEmptyContractDescription`)
 */
object FirEmptyResolvedContractDescription : FirContractDescription() {
    override val source: KtSourceElement? get() = null

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {}

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirContractDescription {
        return this
    }
}

val FirContractDescription.isEmpty: Boolean
    get() = this === FirEmptyContractDescription || this === FirEmptyResolvedContractDescription
