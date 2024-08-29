/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.statistics

import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.stats.CacheStats
import com.github.benmanes.caffeine.cache.stats.ConcurrentStatsCounter
import com.github.benmanes.caffeine.cache.stats.StatsCounter
import org.checkerframework.checker.index.qual.NonNegative

/**
 * A delegate Caffeine [StatsCounter] which only collects stats during the scheduled period. It also supports resetting statistics at the
 * beginning of a collection period so that it can be reused.
 *
 * We need this support because [StatsCounter] cannot be easily replaced in existing Caffeine caches. We have two critical points:
 *
 *  1. A Caffeine cache may already exist when we want to start collecting statistics. Because the Caffeine cache needs to be initialized
 *  with the [StatsCounter], we need it to exist before statistics collection is scheduled. To collect statistics only for the scheduled
 *  period, we need to avoid recording metrics before a scheduled period starts. we need to reset the counter at the beginning.
 *  2. At the end of a collection period, the Caffeine cache may need to continue existing, and it'll continue to use the same
 *  [StatsCounter]. Hence, we need to stop recording metrics at the end of the collection period.
 *
 * While [LLCaffeineStatsCounter] metrics recording is thread-safe, [start] and [stop] are not.
 */
internal class LLCaffeineStatsCounter : StatsCounter {
    /**
     * [backingCounter] is non-null exactly during the scheduled period.
     */
    @Volatile
    private var backingCounter: StatsCounter? = null

    private var lastResult: CacheStats? = null

    /**
     * Starts cache stats collection. The function should only be used under a lock.
     */
    fun start() {
        if (backingCounter == null) {
            backingCounter = ConcurrentStatsCounter()
        }
    }

    /**
     * Stops cache stats collection. The function should only be used under a lock.
     */
    fun stop() {
        val counter = backingCounter
        if (counter != null) {
            lastResult = counter.snapshot()
            backingCounter = null
        }
    }

    override fun recordHits(count: @NonNegative Int) {
        backingCounter?.recordHits(count)
    }

    override fun recordMisses(count: @NonNegative Int) {
        backingCounter?.recordMisses(count)
    }

    override fun recordLoadSuccess(loadTime: @NonNegative Long) {
        backingCounter?.recordLoadSuccess(loadTime)
    }

    override fun recordLoadFailure(loadTime: @NonNegative Long) {
        backingCounter?.recordLoadFailure(loadTime)
    }

    @Deprecated("Deprecated in Caffeine")
    override fun recordEviction() {
        @Suppress("DEPRECATION")
        backingCounter?.recordEviction()
    }

    override fun recordEviction(weight: @NonNegative Int, cause: RemovalCause?) {
        backingCounter?.recordEviction(weight, cause)
    }

    override fun snapshot(): CacheStats = backingCounter?.snapshot() ?: lastResult ?: CacheStats.empty()
}
