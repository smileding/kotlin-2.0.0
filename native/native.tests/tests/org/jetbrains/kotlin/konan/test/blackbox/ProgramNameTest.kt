package org.jetbrains.kotlin.konan.test.blackbox

import org.jetbrains.kotlin.konan.test.blackbox.support.TestCase
import org.jetbrains.kotlin.konan.test.blackbox.support.TestKind
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.TestCompilationResult.Companion.assertSuccess
import org.jetbrains.kotlin.konan.test.blackbox.support.settings.executor
import org.jetbrains.kotlin.konan.test.blackbox.support.util.ClangDistribution
import org.jetbrains.kotlin.konan.test.blackbox.support.util.compileWithClang
import org.jetbrains.kotlin.native.executors.runProcess
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@Tag("program-name")
class ProgramNameTest : AbstractNativeSimpleTest() {

    @Test
    fun programNameTest() {
        // 1. Compile main.c to main.cexe

        val cExecutable = buildDir.resolve("main.cexe")
        compileWithClang(
            clangDistribution = ClangDistribution.Llvm,
            sourceFiles = listOf(sourceDir.resolve("main.c")),
            outputFile = cExecutable,
            additionalClangFlags = listOf("-Wall", "-Werror"),
        ).assertSuccess()

        // 2. Compile kotlinPrintEntryPoint.kt to kotlinPrintEntryPoint.kexe

        val kotlinCompilation = compileToExecutableInOneStage(
            generateTestCaseWithSingleFile(
                sourceFile = sourceDir.resolve("kotlinPrintEntryPoint.kt"),
                testKind = TestKind.STANDALONE_NO_TR,
                extras = TestCase.NoTestRunnerExtras("main")
            )
        ).assertSuccess()

        // 3. run main.cexe (with different parameters) to call kotlin executable

        fun validate(expected: String, vararg args: String) {
            val binaryName = kotlinCompilation.resultingArtifact.executableFile.path
            val result = testRunSettings.executor.runProcess(cExecutable.absolutePath, binaryName, *args) {
                timeout = 60.seconds
            }
            assertEquals("calling exec...\n$expected", result.stdout)
            assertEquals("", result.stderr)
        }

        // kotlinPrintEntryPoint removes .kexe
        validate("programName: app\nargs:", "app.kexe")

        // Simulate a custom program name, see e.g. https://busybox.net/downloads/BusyBox.html#usage
        validate("programName: customProgramName\nargs:", "customProgramName")
        validate("programName: customProgramName\nargs: firstArg, secondArg", "customProgramName", "firstArg", "secondArg")

        // No program name - this would not be POSIX compliant, see https://pubs.opengroup.org/onlinepubs/9699919799/functions/exec.html:
        // "[...] requires a Strictly Conforming POSIX Application to pass at least one argument to the exec function"
        // However, we should not crash the Kotlin runtime because of this.
        validate("programName: null\nargs:")
    }

    companion object {
        private val sourceDir = File("native/native.tests/testData/programName")
    }
}