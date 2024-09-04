/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.konan

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.konan.properties.resolvablePropertyString
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.util.DependencyDirectories
import java.io.File
import java.util.Properties

internal class KonanOutOfProcessCInteropRunner(
        private val execOperations: ExecOperations,
        private val classpath: Set<File>,
        private val konanProperties: File,
        private val logger: Logger,
        private val konanHome: String,
) : KonanCliRunner {
    override fun run(args: List<String>) {
        val mainClass = "org.jetbrains.kotlin.cli.utilities.MainKt"
        val jvmArgs = buildList {
            add("-ea")
            add("-Xmx3G")

            // Disable C2 compiler for HotSpot VM to improve compilation speed.
            System.getProperty("java.vm.name")?.let { vmName ->
                if (vmName.contains("HotSpot", true)) add("-XX:TieredStopAtLevel=1")
            }
        }
        val ignoredSystemProperties = setOf(
                "java.endorsed.dirs",       // Fix for KT-25887
                "user.dir",                 // Don't propagate the working dir of the current Gradle process
                "java.system.class.loader",  // Don't use custom class loaders
                "runFromDaemonPropertyName"
        )
        val systemProperties = System.getProperties()
                /* Capture 'System.getProperties()' current state to avoid potential 'ConcurrentModificationException' */
                .snapshot()
                .asSequence()
                .map { (k, v) -> k.toString() to v.toString() }
                .filter { (k, _) -> k !in ignoredSystemProperties }
                .escapeQuotesForWindows()
                .toMap() + mapOf("konan.home" to konanHome)
        val environment = buildMap {
            this["LIBCLANG_DISABLE_CRASH_RECOVERY"] = "1"
            if (HostManager.host == KonanTarget.MINGW_X64) {
                Properties().apply {
                    konanProperties.inputStream().use(::load)
                }.resolvablePropertyString("llvmHome.mingw_x64")?.let { toolchainDir ->
                    val llvmExecutablesPath = DependencyDirectories.defaultDependenciesRoot
                            .resolve("$toolchainDir/bin")
                            .absolutePath
                    this["PATH"] = "$llvmExecutablesPath;${System.getenv("PATH")}"
                }
            }
        }

        logger.log(
                LogLevel.INFO,
                """|Run "cinterop" tool in a separate JVM process
                   |Main class = $mainClass
                   |Arguments = ${args.toPrettyString()}
                   |Classpath = ${classpath.map { it.absolutePath }.toPrettyString()}
                   |JVM options = ${jvmArgs.toPrettyString()}
                   |Java system properties = ${systemProperties.toPrettyString()}
                   |Custom ENV variables = ${environment.toPrettyString()}
                """.trimMargin()
        )

        execOperations.javaexec {
            this.mainClass.set(mainClass)
            this.classpath(this@KonanOutOfProcessCInteropRunner.classpath)
            this.jvmArgs(jvmArgs)
            this.systemProperties(systemProperties)
            this.environment(environment)
            this.args(listOf("cinterop") + args)
        }
    }

    companion object {
        private fun String.escapeQuotes() = replace("\"", "\\\"")

        private fun Sequence<Pair<String, String>>.escapeQuotesForWindows() =
                if (HostManager.hostIsMingw) map { (key, value) -> key.escapeQuotes() to value.escapeQuotes() } else this

        private fun Properties.snapshot(): Properties = clone() as Properties
    }
}