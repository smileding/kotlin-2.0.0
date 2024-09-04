/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.konan.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.plugin.konan.KonanInProcessCliRunner
import org.jetbrains.kotlin.gradle.plugin.konan.konanClasspath
import org.jetbrains.kotlin.gradle.plugin.konan.prepareAsOutput
import org.jetbrains.kotlin.gradle.plugin.konan.usesIsolatedClassLoadersService
import javax.inject.Inject

/**
 * A task compiling the target library using Kotlin/Native compiler
 */
@CacheableTask
abstract class KonanCompileTask @Inject constructor(
        private val objectFactory: ObjectFactory,
) : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val extraOpts: ListProperty<String>

    /**
     * Kotlin/Native distribution to use.
     */
    @get:Internal // proper dependencies will be specified below: `compilerClasspath`
    abstract val compilerDistribution: DirectoryProperty

    @get:Classpath // Since this task only compiles into klib, it's enough to depend only on the compiler jar.
    protected val compilerClasspath: Provider<FileCollection>
        get() = compilerDistribution.map { it.konanClasspath }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sourceSets: NamedDomainObjectContainer<SourceDirectorySet> = objectFactory.domainObjectContainer(SourceDirectorySet::class.java) {
        objectFactory.sourceDirectorySet(it, it).apply {
            filter.include("**/*.kt")
        }
    }

    @get:ServiceReference
    protected val isolatedClassLoadersService = usesIsolatedClassLoadersService()

    @TaskAction
    fun run() {
        outputDirectory.get().asFile.prepareAsOutput()

        val args = buildList {
            add("-nopack")
            add("-Xmulti-platform")
            add("-output")
            add(outputDirectory.asFile.get().canonicalPath)
            add("-produce")
            add("library")

            addAll(extraOpts.get())
            add(sourceSets.joinToString(",", prefix = "-Xfragments=") { it.name })

            val fragmentSources = sequence {
                for (s in sourceSets) {
                    for (f in s.files) {
                        yield("${s.name}:${f.absolutePath}")
                    }
                }
            }
            add(fragmentSources.joinToString(",", prefix = "-Xfragment-sources="))

            sourceSets.flatMap { it.files }.mapTo(this) { it.absolutePath }
        }
        KonanInProcessCliRunner(
                toolName = "konanc",
                classLoader = isolatedClassLoadersService.get().getIsolatedClassLoader(compilerClasspath.get().files),
                logger = logger,
                useArgFile = true
        ).run(args)
    }
}
