/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See compiler/ir/bir.tree/tree-generator/ReadMe.md.
// DO NOT MODIFY IT MANUALLY.

package org.jetbrains.kotlin.bir.expressions

import org.jetbrains.kotlin.bir.BirElement
import org.jetbrains.kotlin.bir.BirElementClass
import org.jetbrains.kotlin.bir.BirElementVisitor
import org.jetbrains.kotlin.bir.accept
import org.jetbrains.kotlin.bir.symbols.BirReturnTargetSymbol

/**
 * A leaf IR tree element.
 *
 * Generated from: [org.jetbrains.kotlin.bir.generator.BirTree.return]
 */
abstract class BirReturn : BirExpression(), BirElement {
    abstract var value: BirExpression?
    abstract var returnTargetSymbol: BirReturnTargetSymbol

    override fun <D> acceptChildren(visitor: BirElementVisitor<D>, data: D) {
        value?.accept(data, visitor)
    }

    companion object : BirElementClass(BirReturn::class.java, 74, true)
}
