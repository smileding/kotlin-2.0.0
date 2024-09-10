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
import org.gradle.api.model.ObjectFactory
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
import org.jetbrains.kotlin.gradle.plugin.konan.prepareAsOutput
import org.jetbrains.kotlin.gradle.plugin.konan.runKonanTool
import org.jetbrains.kotlin.gradle.plugin.konan.usesIsolatedClassLoadersService
import org.jetbrains.kotlin.nativeDistribution.NativeDistributionProperty
import org.jetbrains.kotlin.nativeDistribution.nativeDistributionProperty
import javax.inject.Inject

private abstract class KonanCacheAction : WorkAction<KonanCacheAction.Parameters> {
    interface Parameters : WorkParameters {
        val isolatedClassLoadersService: Property<KonanCliRunnerIsolatedClassLoadersService>
        val compilerDistribution: NativeDistributionProperty
        val args: ListProperty<String>
    }

    override fun execute() {
        parameters.isolatedClassLoadersService.get().getIsolatedClassLoader(parameters.compilerDistribution.get().compilerClasspath.files).runKonanTool(
                logger = Logging.getLogger(this::class.java),
                useArgFile = false,
                toolName = "konanc",
                args = parameters.args.get()
        )
    }
}

@CacheableTask
open class KonanCacheTask @Inject constructor(
        private val workerExecutor: WorkerExecutor,
        objectFactory: ObjectFactory,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val klib: DirectoryProperty = objectFactory.directoryProperty()

    @get:Input
    val target: Property<String> = objectFactory.property(String::class.java)

    @get:Internal // used to construct the output place
    val moduleName: Property<String> = objectFactory.property(String::class.java)

    @get:Internal // used to construct the output place
    val cacheRootDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:OutputDirectory
    val outputDirectory: Provider<Directory> = cacheRootDirectory.dir(target.zip(moduleName) { target, name -> "$target-gSTATIC/$name-cache" })

    /**
     * Kotlin/Native distribution to use.
     */
    @get:Internal // proper dependencies will be specified below: `compilerClasspath`
    val compilerDistribution: NativeDistributionProperty = objectFactory.nativeDistributionProperty()

    @get:Classpath // Depends on the compiler jar.
    @Suppress("unused")
    protected val compilerClasspath: Provider<FileCollection> = compilerDistribution.map { it.compilerClasspath }

    @get:InputDirectory // Depends on libraries used during code generation
    @get:PathSensitive(PathSensitivity.NONE)
    @Suppress("unused")
    protected val llvmCodegenLibs: Provider<Directory> = compilerDistribution.map { it.nativeLibs }

    @get:InputFile // Depends on properties file with compilation flags used during code generation
    @get:PathSensitive(PathSensitivity.NONE)
    @Suppress("unused")
    protected val konanProperties: Provider<RegularFile> = compilerDistribution.map { it.konanProperties }

    @get:ServiceReference
    protected val isolatedClassLoadersService = usesIsolatedClassLoadersService()

    @TaskAction
    fun compile() {
        // Compiler doesn't create a cache if the cacheFile already exists. So we need to remove it manually.
        outputDirectory.get().asFile.prepareAsOutput()

        val args = buildList {
            add("-g")
            add("-target")
            add(target.get())
            add("-produce")
            add("static_cache")
            add("-Xadd-cache=${klib.asFile.get().absolutePath}")
            add("-Xcache-directory=${outputDirectory.get().asFile.parentFile.absolutePath}")
            PlatformManager(compilerDistribution.get().root.asFile.absolutePath).apply {
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