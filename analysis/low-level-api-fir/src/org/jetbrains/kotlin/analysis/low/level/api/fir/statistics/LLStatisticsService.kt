/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.statistics

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.platform.statistics.KaStatisticsService

/**
 * [LLStatisticsService] is the entry point to low-level API statistics collection and reporting. The service manages the scheduler,
 * collector, and reporter, and additionally stores the accumulated [LLStatistics].
 *
 * This class is the only IntelliJ project service registered for low-level API statistics collection. The single entry point simplifies
 * handling of whether statistics are enabled (see [KaStatisticsService.areStatisticsEnabled]).
 */
class LLStatisticsService(internal val project: Project) : Disposable {
    internal val scheduler: LLStatisticsScheduler = LLStatisticsScheduler(this)

    internal val collector: LLStatisticsCollector = LLStatisticsCollector(this)

    internal val reporter: LLStatisticsReporter = LLStatisticsReporter(this)

    internal val statistics: LLStatistics = LLStatistics(collector.combinedSymbolProviderCacheStatsCounter)

    fun start() {
        scheduler.start()
    }

    fun stop() {
        scheduler.stop()
    }

    override fun dispose() {
        stop()
    }

    companion object {
        fun getInstance(project: Project): LLStatisticsService? =
            if (KaStatisticsService.areStatisticsEnabled) project.service() else null
    }
}
