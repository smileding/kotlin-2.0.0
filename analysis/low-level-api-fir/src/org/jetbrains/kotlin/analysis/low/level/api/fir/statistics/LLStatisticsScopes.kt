/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.statistics

internal abstract class LLStatisticsScope(val name: String)

internal object MetricsScope : LLStatisticsScope("analysisApi.metrics") {
    object Caches : LLStatisticsScope("$name.caches") {
        object CombinedSymbolProviders : LLStatisticsScope("$name.combinedSymbolProviders") {
            object HitCount : LLStatisticsScope("$name.hitCount")
        }
    }
}
