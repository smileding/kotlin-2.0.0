/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.platform.statistics

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.analysis.api.platform.KaEngineService

/**
 * Collects and reports statistics about the Analysis API's internal components. Statistics collection needs to be started and stopped using
 * the [start] and [stop] methods, as the service doesn't start automatically.
 *
 * Statistics collection needs to be enabled with the registry key `kotlin.analysisApi.statistics` ([areStatisticsEnabled]). This is needed
 * because statistics incur an overhead even outside of collection periods controlled with [start] and [stop]. If [areStatisticsEnabled] is
 * `false`, [getInstance] will return `null`.
 *
 * [KaStatisticsService] uses the platform's [OpenTelemetry][io.opentelemetry.api.OpenTelemetry] instance (provided by
 * [KotlinOpenTelemetryProvider]) to report metrics. If no OpenTelemetry provider is available, reporting will not be performed.
 *
 * If statistics collection is not stopped explicitly, it will be stopped during project service disposal.
 *
 * Statistics collection is only implemented for the K2 backend. In the K1 backend, [getInstance] will always return `null`.
 */
public interface KaStatisticsService : KaEngineService {
    /**
     * Starts statistics collection and reporting, but only if it's [enabled][areStatisticsEnabled]. If statistics collection is already in
     * progress, this call does nothing.
     *
     * The function implementation is thread-safe and may be called from any thread.
     */
    public fun start()

    /**
     * Stops ongoing statistics collection and reporting. If no statistics collection is in progress, this call does nothing.
     *
     * While eager reporting is stopped, asynchronous OpenTelemetry instruments are kept alive. This allows an exporter to measure an
     * instrument after reporting is stopped, as the metric may not be requested during active reporting. In such a case, the instrument
     * will report the *last* state of the metric when statistics collection was stopped.
     *
     * The function implementation is thread-safe and may be called from any thread.
     */
    public fun stop()

    public companion object {
        public val areStatisticsEnabled: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
            Registry.`is`("kotlin.analysisApi.statistics", false)
        }

        public fun getInstance(project: Project): KaStatisticsService? = if (areStatisticsEnabled) project.serviceOrNull() else null
    }
}
