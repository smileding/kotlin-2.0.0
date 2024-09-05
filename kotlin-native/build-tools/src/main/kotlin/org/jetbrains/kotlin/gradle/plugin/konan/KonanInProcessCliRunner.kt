/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.konan

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Files

private const val runFromDaemonPropertyName = "kotlin.native.tool.runFromDaemon"

internal class KonanInProcessCliRunner(
        private val toolName: String,
        private val classLoader: URLClassLoader,
        private val logger: Logger,
        private val useArgFile: Boolean,
) {
    fun run(args: List<String>) {
        System.setProperty(runFromDaemonPropertyName, "true")
        val transformedArgs = if (useArgFile) {
            val argFile = Files.createTempFile(/* prefix = */ "konancArgs", /* suffix = */ ".lst").toFile().apply { deleteOnExit() }
            argFile.printWriter().use { w ->
                for (arg in args) {
                    val escapedArg = arg
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                    w.println("\"$escapedArg\"")
                }
            }

            listOf("@${argFile.absolutePath}")
        } else {
            args
        }

        val mainClass = "org.jetbrains.kotlin.cli.utilities.MainKt"
        val daemonEntryPoint = "daemonMain"

        logger.log(
                LogLevel.INFO,
                """|Run in-process tool "$toolName"
                   |Entry point method = $mainClass.$daemonEntryPoint
                   |Classpath = ${classLoader.urLs.map { it.file }.toPrettyString()}
                   |Arguments = ${args.toPrettyString()}
                   |Transformed arguments = ${if (transformedArgs == args) "same as arguments" else transformedArgs.toPrettyString()}
                """.trimMargin()
        )

        try {
            val mainClass = classLoader.loadClass(mainClass)
            val entryPoint = mainClass.methods
                    .singleOrNull { it.name == daemonEntryPoint } ?: error("Couldn't find daemon entry point '$daemonEntryPoint'")

            entryPoint.invoke(null, (listOf(toolName) + transformedArgs).toTypedArray())
        } catch (t: InvocationTargetException) {
            throw t.targetException
        }
    }
}