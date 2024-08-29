/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.statistics

import com.github.benmanes.caffeine.cache.stats.StatsCounter

/**
 * Collects statistics from LL components, both passively and actively in a scheduled task, and stores them in [LLStatistics].
 *
 * The collector is not thread-safe. [start], [stop], and [collect] should only be called from a single thread.
 */
internal class LLStatisticsCollector(private val statisticsService: LLStatisticsService) {
    private val combinedSymbolProviderCaffeineStatsCounter: LLCaffeineStatsCounter = LLCaffeineStatsCounter()

    val combinedSymbolProviderCacheStatsCounter: StatsCounter
        get() = combinedSymbolProviderCaffeineStatsCounter

    fun start() {
        combinedSymbolProviderCaffeineStatsCounter.start()
    }

    fun stop() {
        combinedSymbolProviderCaffeineStatsCounter.stop()
    }

    /**
     * Performs periodic, active statistics collection. The function should only be called by the thread scheduled by
     * [LLStatisticsScheduler] in a *read action*.
     */
    fun collect() {
    }
}
