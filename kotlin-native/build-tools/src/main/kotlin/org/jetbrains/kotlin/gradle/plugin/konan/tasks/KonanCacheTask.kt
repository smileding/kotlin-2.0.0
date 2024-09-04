/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.konan.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.plugin.konan.KonanCliCompilerRunner
import org.jetbrains.kotlin.konan.library.defaultResolver
import org.jetbrains.kotlin.konan.target.CompilerOutputKind
import org.jetbrains.kotlin.konan.target.Distribution
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.library.uniqueName
import org.jetbrains.kotlin.gradle.plugin.konan.KonanCliRunnerIsolatedClassLoadersService
import org.jetbrains.kotlin.gradle.plugin.konan.konanClasspath
import org.jetbrains.kotlin.gradle.plugin.konan.prepareAsOutput
import org.jetbrains.kotlin.util.Logger
import java.io.File
import java.util.*
import javax.inject.Inject

enum class KonanCacheKind(val outputKind: CompilerOutputKind) {
    STATIC(CompilerOutputKind.STATIC_CACHE),
    DYNAMIC(CompilerOutputKind.DYNAMIC_CACHE)
}

abstract class KonanCacheTask @Inject constructor(
        private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    abstract val originalKlib: DirectoryProperty

    @get:Input
    lateinit var klibUniqName: String

    @get:Input
    lateinit var cacheRoot: String

    @get:Input
    lateinit var target: String

    @get:Internal
    // TODO: Reuse NativeCacheKind from Big Kotlin plugin when it is available.
    val cacheDirectory: File
        get() = File("$cacheRoot/$target-g$cacheKind")

    @get:OutputDirectory
    val cacheFile: File
        get() = cacheDirectory.resolve(if (makePerFileCache) "${klibUniqName}-per-file-cache" else "${klibUniqName}-cache")

    @get:Input
    var cacheKind: KonanCacheKind = KonanCacheKind.STATIC

    @get:Input
    var makePerFileCache: Boolean = false

    /**
     * Kotlin/Native distribution to use.
     */
    @get:Internal // proper dependencies will be specified below
    abstract val compilerDistribution: DirectoryProperty

    @get:Input
    /** Path to a compiler distribution that is used to build this cache. */
    protected val compilerDistributionPath: Provider<File>
        get() = compilerDistribution.asFile

    @get:Input
    var cachedLibraries: Map<File, File> = emptyMap()

    private val isolatedClassLoadersService = KonanCliRunnerIsolatedClassLoadersService.attachingToTask(this)

    @TaskAction
    fun compile() {
        val dist = compilerDistribution.get()
        // This code uses bootstrap version of util-klib and fails due to the older default ABI than library being used
        // A possible solution is to read it manually from manifest file or this check should be done by the compiler itself
//        check(klibUniqName == readKlibUniqNameFromManifest()) {
//            "klibUniqName mismatch: configured '$klibUniqName', resolved '${readKlibUniqNameFromManifest()}'"
//        }

        // Compiler doesn't create a cache if the cacheFile already exists. So we need to remove it manually.
        cacheFile.prepareAsOutput()
        val additionalCacheFlags = PlatformManager(dist.asFile.absolutePath).let {
            it.targetByName(target).let(it::loader).additionalCacheFlags
        }
        require(originalKlib.isPresent)
        val args = mutableListOf(
            "-g",
            "-target", target,
            "-produce", cacheKind.outputKind.name.lowercase(Locale.getDefault()),
            "-Xadd-cache=${originalKlib.asFile.get().absolutePath}",
            "-Xcache-directory=${cacheDirectory.absolutePath}"
        )
        if (makePerFileCache)
            args += "-Xmake-per-file-cache"
        args += additionalCacheFlags
        args += cachedLibraries.map { "-Xcached-library=${it.key},${it.value}" }
        KonanCliCompilerRunner(execOperations, dist.konanClasspath.files, logger, isolatedClassLoadersService, dist.asFile.absolutePath).run(args)
    }
}