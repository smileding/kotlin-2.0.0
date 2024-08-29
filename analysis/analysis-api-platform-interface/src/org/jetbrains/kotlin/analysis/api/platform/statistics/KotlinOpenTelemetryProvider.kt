/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.platform.statistics

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import io.opentelemetry.api.OpenTelemetry
import org.jetbrains.kotlin.analysis.api.platform.KotlinOptionalPlatformComponent

/**
 * Provides an initialized [OpenTelemetry] instance. The Analysis API uses this instance to report statistics when statistics collection
 * with [KaStatisticsService] is enabled. If the platform doesn't support OpenTelemetry, this service doesn't need to be registered.
 */
public interface KotlinOpenTelemetryProvider : KotlinOptionalPlatformComponent {
    public val openTelemetry: OpenTelemetry

    public companion object {
        public fun getInstance(project: Project): KotlinOpenTelemetryProvider? = project.serviceOrNull()
    }
}
