/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.statistics

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Schedules statistics collection, reporting, and logging (if enabled) with [LLStatisticsCollector] and [LLStatisticsReporter].
 *
 * The scheduler communicates the beginning and end of a collection period to the collector and reporter, and also sets up recurring
 * scheduled tasks for them.
 */
class LLStatisticsScheduler(private val statisticsService: LLStatisticsService) {
    private var areStatisticsScheduled: Boolean = false

    private var collectStatisticsFuture: ScheduledFuture<*>? = null

    private var reportStatisticsFuture: ScheduledFuture<*>? = null

    fun start() {
        synchronized(this) {
            if (areStatisticsScheduled) return

            startCollection(statisticsService.collector)
            startReporting(statisticsService.reporter)

            areStatisticsScheduled = true
        }
    }

    private fun startCollection(collector: LLStatisticsCollector) {
        collector.start()

        val collectStatistics = Runnable {
            ApplicationManager.getApplication().runReadAction {
                collector.collect()
            }
        }

        collectStatisticsFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            collectStatistics,
            20,
            20,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun startReporting(reporter: LLStatisticsReporter) {
        reporter.start()

        val logStatistics = Runnable { reporter.log() }

        reportStatisticsFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            logStatistics,
            1,
            1,
            TimeUnit.SECONDS,
        )
    }

    fun stop() {
        synchronized(this) {
            if (!areStatisticsScheduled) return

            collectStatisticsFuture?.cancel(true)
            collectStatisticsFuture = null

            reportStatisticsFuture?.cancel(true)
            reportStatisticsFuture = null

            stopCollection(statisticsService.collector)
            stopReporting(statisticsService.reporter)

            areStatisticsScheduled = false
        }
    }

    private fun stopCollection(collector: LLStatisticsCollector) {
        collector.stop()
    }

    private fun stopReporting(reporter: LLStatisticsReporter) {
        reporter.stop()
    }
}
