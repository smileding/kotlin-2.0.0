/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.konan.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.gradle.plugin.konan.KonanCliRunnerIsolatedClassLoadersService
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.gradle.plugin.konan.konanClasspath
import org.jetbrains.kotlin.gradle.plugin.konan.konanLLVMLibs
import org.jetbrains.kotlin.gradle.plugin.konan.konanProperties
import org.jetbrains.kotlin.gradle.plugin.konan.prepareAsOutput
import org.jetbrains.kotlin.gradle.plugin.konan.runKonanTool
import org.jetbrains.kotlin.gradle.plugin.konan.usesIsolatedClassLoadersService
import javax.inject.Inject

private abstract class KonanCacheAction : WorkAction<KonanCacheAction.Parameters> {
    interface Parameters : WorkParameters {
        val isolatedClassLoadersService: Property<KonanCliRunnerIsolatedClassLoadersService>
        val compilerDistribution: DirectoryProperty
        val args: ListProperty<String>
    }

    override fun execute() {
        parameters.isolatedClassLoadersService.get().getIsolatedClassLoader(parameters.compilerDistribution.get().konanClasspath.files).runKonanTool(
                logger = Logging.getLogger(this::class.java),
                useArgFile = false,
                toolName = "konanc",
                args = parameters.args.get()
        )
    }
}

@CacheableTask
abstract class KonanCacheTask @Inject constructor(
        private val workerExecutor: WorkerExecutor,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val klib: DirectoryProperty

    @get:Input
    abstract val target: Property<String>

    @get:Internal // used to construct the output place
    abstract val moduleName: Property<String>

    @get:Internal // used to construct the output place
    abstract val cacheRootDirectory: DirectoryProperty

    @get:OutputDirectory
    val outputDirectory: Provider<Directory>
        get() = cacheRootDirectory.dir(target.flatMap { target -> moduleName.map { name -> "$target-gSTATIC/$name-cache" } })

    /**
     * Kotlin/Native distribution to use.
     */
    @get:Internal // proper dependencies will be specified below: `compilerClasspath`
    abstract val compilerDistribution: DirectoryProperty

    @get:Classpath // Depends on the compiler jar.
    @Suppress("unused")
    protected val compilerClasspath: Provider<FileCollection>
        get() = compilerDistribution.map { it.konanClasspath }

    @get:InputDirectory // Depends on libraries used during code generation
    @get:PathSensitive(PathSensitivity.NONE)
    @Suppress("unused")
    protected val llvmCodegenLibs: Provider<Directory>
        get() = compilerDistribution.map { it.konanLLVMLibs }

    @get:InputFile // Depends on properties file with compilation flags used during code generation
    @get:PathSensitive(PathSensitivity.NONE)
    @Suppress("unused")
    protected val konanProperties: Provider<RegularFile>
        get() = compilerDistribution.map { it.konanProperties }

    @get:ServiceReference
    protected val isolatedClassLoadersService = usesIsolatedClassLoadersService()

    @TaskAction
    fun compile() {
        // Compiler doesn't create a cache if the cacheFile already exists. So we need to remove it manually.
        outputDirectory.get().asFile.prepareAsOutput()

        val dist = compilerDistribution.get()

        val args = buildList {
            add("-g")
            add("-target")
            add(target.get())
            add("-produce")
            add("static_cache")
            add("-Xadd-cache=${klib.asFile.get().absolutePath}")
            add("-Xcache-directory=${outputDirectory.get().asFile.parentFile.absolutePath}")
            PlatformManager(dist.asFile.absolutePath).apply {
                addAll(platform(targetByName(target.get())).additionalCacheFlags)
            }
        }

        val workQueue = workerExecutor.noIsolation()
        workQueue.submit(KonanCacheAction::class.java) {
            this.compilerDistribution.set(this@KonanCacheTask.compilerDistribution)
            this.isolatedClassLoadersService.set(this@KonanCacheTask.isolatedClassLoadersService)
            this.args.addAll(args)
        }
    }
}