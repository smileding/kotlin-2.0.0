/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package samples.time

import samples.Sample
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class MeasureTime {

    @Sample
    fun measureTimeSample() {
        fun slowFunction() = Thread.sleep(1000L)
        val elapsed = measureTime {
            slowFunction()
        }
        println("Time elapsed: ${elapsed.inWholeSeconds} second ($elapsed)")
    }

    @Sample
    fun measureTimedValueSample() {
        fun slowFunction() = Thread.sleep(1000L)
        val result = measureTimedValue {
            slowFunction()
        }
        println("Computed result: ${result.value}, time elapsed: ${result.duration}")
    }

    @Sample
    fun explicitMeasureTimeSample() {
        val testSource = TestTimeSource()
        val elapsed = testSource.measureTime {
            println("Pretending this function executes 10 seconds")
            testSource += 10.seconds
        }
        println("Time elapsed: ${elapsed.inWholeSeconds} second ($elapsed)")
    }

    @Sample
    fun explicitMeasureTimedValueSample() {
        val testSource = TestTimeSource()
        val result = testSource.measureTimedValue {
            println("Pretending this function executes 10 seconds")
            testSource += 10.seconds
            42
        }
        println("Computed result: ${result.value}, time elapsed: ${result.duration}")
    }

    @Sample
    fun monotonicMeasureTimeSample() {
        fun slowFunction() = Thread.sleep(1000L)
        val elapsed = TimeSource.Monotonic.measureTime {
            slowFunction()
        }
        println("Time elapsed: ${elapsed.inWholeSeconds} second ($elapsed)")
    }

    @Sample
    fun monotonicMeasureTimedValueSample() {
        fun slowFunction() = Thread.sleep(1000L)
        val result = TimeSource.Monotonic.measureTimedValue() {
            slowFunction()
            42
        }
        println("Computed result: ${result.value}, time elapsed: ${result.duration}")
    }
}