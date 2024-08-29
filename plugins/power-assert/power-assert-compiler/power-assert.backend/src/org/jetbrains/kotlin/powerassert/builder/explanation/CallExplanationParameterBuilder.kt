/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.powerassert.builder.explanation

import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.powerassert.diagram.IrTemporaryVariable
import org.jetbrains.kotlin.powerassert.diagram.SourceFile
import org.jetbrains.kotlin.powerassert.sourceRange

class CallDiagramParameterBuilder(
    private val factory: ExplanationFactory,
    private val sourceFile: SourceFile,
    private val originalCall: IrCall,
    private val variableDiagrams: Map<IrVariable, IrVariable>,
) : ParameterBuilder {
    override fun build(
        builder: IrBuilderWithScope,
        dispatchReceiver: List<IrTemporaryVariable>?,
        extensionReceiver: List<IrTemporaryVariable>?,
        valueParameters: Map<String, List<IrTemporaryVariable>>,
    ): IrExpression {
        return builder.irCallDiagram(
            factory,
            sourceFile,
            originalCall,
            dispatchReceiver,
            extensionReceiver,
            valueParameters,
            variableDiagrams,
        )
    }
}

private fun IrBuilderWithScope.irCallDiagram(
    factory: ExplanationFactory,
    sourceFile: SourceFile,
    call: IrCall,
    dispatchReceiver: List<IrTemporaryVariable>?,
    extensionReceiver: List<IrTemporaryVariable>?,
    valueParameters: Map<String, List<IrTemporaryVariable>>,
    variableDiagrams: Map<IrVariable, IrVariable>,
): IrExpression {
    val callInfo = sourceFile.getSourceRangeInfo(call)

    // Get call source string starting at the very beginning of the first line.
    // This is so multiline calls all start from the same column offset.
    val startOffset = callInfo.startOffset - callInfo.startColumnNumber
    val source = sourceFile.getText(startOffset, callInfo.endOffset)
        .clearSourcePrefix(callInfo.startColumnNumber)

    return with(factory) {
        irCallDiagram(
            offset = startOffset,
            source = irString(source),
            dispatchReceiver = dispatchReceiver?.let { variables ->
                val sourceRange = call.dispatchReceiver!!.sourceRange
                irReceiver(
                    startOffset = sourceRange.start - startOffset,
                    endOffset = sourceRange.endInclusive - startOffset,
                    expressions = dispatchReceiver.map { irExpression(it, startOffset, variableDiagrams) },
                )
            },
            extensionReceiver = extensionReceiver?.let { variables ->
                val sourceRange = call.extensionReceiver!!.sourceRange
                irReceiver(
                    startOffset = sourceRange.start - startOffset,
                    endOffset = sourceRange.endInclusive - startOffset,
                    expressions = extensionReceiver.map { irExpression(it, startOffset, variableDiagrams) },
                )
            },
            valueParameters = valueParameters.mapValues { (name, variables) ->
                val parameter = call.symbol.owner.valueParameters.single { it.name.asString() == name }
                val argument = call.getValueArgument(parameter.index)!!
                val sourceRange = argument.sourceRange
                irValueParameter(
                    startOffset = sourceRange.start - startOffset,
                    endOffset = sourceRange.endInclusive - startOffset,
                    expressions = variables.map { irExpression(it, startOffset, variableDiagrams) },
                )
            },
        )
    }
}

fun String.clearSourcePrefix(offset: Int): String = buildString {
    for ((i, c) in this@clearSourcePrefix.withIndex()) {
        when {
            i >= offset -> {
                // Append the remaining characters and exit.
                append(this@clearSourcePrefix.substring(i))
                break
            }
            c == '\t' -> append('\t') // Preserve tabs.
            else -> append(' ') // Replace all other characters with spaces.
        }
    }
}
