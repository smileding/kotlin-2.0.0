/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin

import kotlinBuildProperties
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.nativeDistribution.NativeDistribution
import javax.inject.Inject

open class PlatformManagerProvider @Inject constructor(
        project: Project,
        private val objectFactory: ObjectFactory,
) {
    @get:Internal // Marked as input via [konanProperties]
    val nativeProtoDistribution = NativeDistribution(project.project(":kotlin-native").layout.projectDirectory)

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    @Suppress("unused")
    protected val konanProperties = nativeProtoDistribution.konanProperties

    @get:Input
    @get:Optional
    @Suppress("unused")
    protected val konanDataDir = project.kotlinBuildProperties.getOrNull("konan.data.dir") as String?

    @get:Internal // Marked as input via [konanProperties] and [konanDataDir]
    val platformManager = PlatformManager(nativeProtoDistribution.root.asFile.absolutePath, konanDataDir)

    @get:Internal
    val execClang // Marked as input via [konanProperties] and [konanDataDir]
        get() = ExecClang.create(objectFactory, platformManager)

    @get:Internal
    val enabledTargets // Marked as input via [konanProperties] and [konanDataDir]
        get() = platformManager.enabled.filterNot {
            it in KonanTarget.deprecatedTargets && it !in KonanTarget.toleratedDeprecatedTargets
        }

    @get:Internal
    val enabledTargetsWithSanitizers // Marked as input via [konanProperties] and [konanDataDir]
        get() = enabledTargets.flatMap { target ->
            listOf(target.withSanitizer()) + target.supportedSanitizers().map {
                target.withSanitizer(it)
            }
        }

    @get:Internal
    val allTargets // Marked as input via [konanProperties] and [konanDataDir]
        get() = platformManager.targetValues

    @get:Internal
    val cacheableTargetNames // Marked as input via [konanProperties] and [konanDataDir]
        get() = platformManager.hostPlatform.cacheableTargets
}

open class PlatformManagerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("platformManagerProvider", PlatformManagerProvider::class.java, project)
    }
}
