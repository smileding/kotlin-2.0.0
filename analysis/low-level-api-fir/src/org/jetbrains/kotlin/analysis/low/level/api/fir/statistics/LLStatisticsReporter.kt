/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.statistics

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongCounter
import org.jetbrains.kotlin.analysis.api.platform.statistics.KotlinOpenTelemetryProvider

/**
 * Reports statistics from [LLStatistics] to OpenTelemetry and provides a periodic, logged summary.
 */
internal class LLStatisticsReporter(private val statisticsService: LLStatisticsService) : Disposable {
    private var asynchronousInstruments: List<AutoCloseable>? = null

    init {
        Disposer.register(statisticsService, this)
    }

    fun start() {
        if (asynchronousInstruments == null) {
            registerAsynchronousInstruments()
        }
    }

    /**
     * Asynchronous instruments are registered once when statistics reporting is started for the first time and only closed during the
     * disposal of the statistics service. This allows the OpenTelemetry exporter to read metrics from instruments even after the scheduled
     * period.
     */
    private fun registerAsynchronousInstruments() {
        val openTelemetry = KotlinOpenTelemetryProvider.getInstance(statisticsService.project)?.openTelemetry ?: return
        val meter = openTelemetry.getMeter(MetricsScope.Caches.name)

        asynchronousInstruments = listOf(
            meter.asyncCounter(MetricsScope.Caches.CombinedSymbolProviders.HitCount) { it.combinedSymbolProviderCacheStats.hitCount() },
        )
    }

    private inline fun Meter.asyncCounter(
        scope: LLStatisticsScope,
        crossinline getValue: (LLStatistics) -> Long,
    ): ObservableLongCounter {
        return counterBuilder(scope.name).buildWithCallback { measurement ->
            withStatistics { measurement.record(getValue(it)) }
        }
    }

    fun stop() {
    }

    fun log() {
        val statistics = statisticsService.statistics

        LOG.info(
            """
            -- Cache stats for combined symbol providers --
            ${statistics.combinedSymbolProviderCacheStats}
            """.trimIndent()
        )
    }

    override fun dispose() {
        asynchronousInstruments?.forEach { it.close() }
        asynchronousInstruments = null
    }

    private inline fun withStatistics(f: (LLStatistics) -> Unit) {
        statisticsService.statistics.let(f)
    }

    companion object {
        private val LOG = Logger.getInstance(LLStatisticsReporter::class.java)
    }
}
