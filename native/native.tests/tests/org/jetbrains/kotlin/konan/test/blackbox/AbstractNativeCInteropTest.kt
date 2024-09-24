/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.test.blackbox

import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.testFramework.TestDataFile
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.test.blackbox.support.TestCInteropArgs
import org.jetbrains.kotlin.konan.test.blackbox.support.TestCompilerArgs
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.TestCompilationResult
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.TestCompilationResult.Companion.assertSuccess
import org.jetbrains.kotlin.konan.test.blackbox.support.util.getAbsoluteFile
import org.jetbrains.kotlin.konan.test.blackbox.support.util.dumpMetadata
import org.jetbrains.kotlin.konan.util.CInteropHints
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEquals
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEqualsToFile
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import java.io.File

abstract class AbstractNativeCInteropFModulesTest : AbstractNativeCInteropTest() {
    override val fmodules = true

    override val defFileName: String = "pod1.def"
}

abstract class AbstractNativeCInteropNoFModulesTest : AbstractNativeCInteropTest() {
    override val fmodules = false

    override val defFileName: String = "pod1.def"
}

abstract class AbstractNativeCInteropIncludeCategoriesTest : AbstractNativeCInteropTest() {
    override val fmodules: Boolean
        get() = false

    override val defFileName: String
        get() = "dependency.def"
}

// This test checks that cinterop-generated declarations have an experimental annotation.
abstract class AbstractNativeCInteropExperimentalTest : AbstractNativeCInteropTest() {
    override val fmodules: Boolean
        get() = false

    override val defFileName: String
        get() = "dependency.def"

    override val ignoreExperimentalForeignApi: Boolean
        get() = false
}

@Tag("cinterop")
abstract class AbstractNativeCInteropTest : AbstractNativeCInteropBaseTest() {
    abstract val fmodules: Boolean

    abstract val defFileName: String

    // All declarations generated by cinterop now have ExperimentalForeignApi annotations.
    // There is no sense in cluttering every test expected data with it, so we simply ignore it
    // in the actual data by default:
    open val ignoreExperimentalForeignApi: Boolean
        get() = true

    @Synchronized
    protected fun runTest(@TestDataFile testPath: String) {
        // FIXME: check the following failures under Android with -fmodules
        // fatal error: could not build module 'std'
        Assumptions.assumeFalse(
            this is AbstractNativeCInteropFModulesTest &&
                    targets.testTarget.family == Family.ANDROID
        )
        val testPathFull = getAbsoluteFile(testPath)
        val testDataDir = testPathFull.parentFile.parentFile
        val includeFolder = testDataDir.resolve("include")
        val defFile = testPathFull.resolve(defFileName)
        val defContents = defFile.readText().split("\n").map { it.trim() }

        muteCInteropTestIfNecessary(defFile, targets.testTarget)

        val defHasHeaders = defContents.any { it.startsWith("headers") }
        Assumptions.assumeFalse(fmodules && defHasHeaders)

        val goldenFile = if (testDataDir.name == "builtins")
            getBuiltinsGoldenFile(testPathFull)
        else
            getGoldenFile(testPathFull)
        val fmodulesArgs = if (fmodules) TestCInteropArgs("-compiler-option", "-fmodules") else TestCompilerArgs.EMPTY
        val includeArgs = if (testDataDir.name.startsWith("framework"))
            TestCInteropArgs("-compiler-option", "-F${testDataDir.canonicalPath}")
        else
            TestCInteropArgs("-compiler-option", "-I${includeFolder.canonicalPath}")

        val testCompilationResult = cinteropToLibrary(targets, defFile, buildDir, includeArgs + fmodulesArgs)
        // If we are running fmodules-specific test without -fmodules then we want to be sure that cinterop fails the way we want it to.
        if (!fmodules && testPath.endsWith("FModules/")) {
            val loggedData = (testCompilationResult as TestCompilationResult.CompilationToolFailure).loggedData
            val prettyMessage = CInteropHints.fmodulesHint
            assertTrue(loggedData.toString().contains(prettyMessage)) {
                "Test failed. CInterop compilation result was: $testCompilationResult"
            }
        } else {
            val metadata = testCompilationResult.assertSuccess().resultingArtifact
                .dumpMetadata(kotlinNativeClassLoader.classLoader, false, null)

            val filteredMetadata = if (ignoreExperimentalForeignApi)
                metadata.lineSequence().filterNot { it.trim() == "@kotlinx/cinterop/ExperimentalForeignApi" }.joinToString("\n")
            else
                metadata

            assertEqualsToFile(goldenFile, filteredMetadata)
        }
    }

    private fun getGoldenFile(testPathFull: File): File {
        return testPathFull.resolve("contents.gold.txt")
    }

    private fun getBuiltinsGoldenFile(testPathFull: File): File {
        val goldenFilePart = when (targets.testTarget) {
            KonanTarget.ANDROID_ARM32 -> "ARM32"
            KonanTarget.ANDROID_ARM64 -> "ARM64"
            KonanTarget.ANDROID_X64 -> "X64"
            KonanTarget.ANDROID_X86 -> "CPointerByteVar"
            KonanTarget.IOS_ARM64 -> "CPointerByteVar"
            KonanTarget.IOS_SIMULATOR_ARM64 -> "CPointerByteVar"
            KonanTarget.IOS_X64 -> "X64"
            KonanTarget.LINUX_ARM32_HFP -> "ARM32"
            KonanTarget.LINUX_ARM64 -> "ARM64"
            KonanTarget.OHOS_ARM64 -> "ARM64" // dingxiao
            KonanTarget.LINUX_X64 -> "X64"
            KonanTarget.MACOS_ARM64 -> "CPointerByteVar"
            KonanTarget.MACOS_X64 -> "X64"
            KonanTarget.MINGW_X64 -> "CPointerByteVar"
            KonanTarget.TVOS_ARM64 -> "CPointerByteVar"
            KonanTarget.TVOS_SIMULATOR_ARM64 -> "CPointerByteVar"
            KonanTarget.TVOS_X64 -> "X64"
            KonanTarget.WATCHOS_ARM32 -> "CPointerByteVar"
            KonanTarget.WATCHOS_ARM64 -> "CPointerByteVar"
            KonanTarget.WATCHOS_DEVICE_ARM64 -> "CPointerByteVar"
            KonanTarget.WATCHOS_SIMULATOR_ARM64 -> "CPointerByteVar"
            KonanTarget.WATCHOS_X64 -> "X64"
        }
        return testPathFull.resolve("contents.gold.${goldenFilePart}.txt")
    }
}

internal fun muteCInteropTestIfNecessary(defFile: File, target: KonanTarget) {
    if (target.family.isAppleFamily) return

    defFile.readLines().forEach { line ->
        if (line.startsWith("---")) return

        val parts = line.split('=')
        if (parts.size == 2
            && parts[0].trim().equals("language", ignoreCase = true)
            && parts[1].trim().equals("Objective-C", ignoreCase = true)
        ) {
            Assumptions.abort<Nothing>("C-interop tests with Objective-C are not supported at non-Apple targets, def file: $defFile")
        }
    }
}
//Assumptions.assumeFalse(defHasObjC && !targets.testTarget.family.isAppleFamily)
