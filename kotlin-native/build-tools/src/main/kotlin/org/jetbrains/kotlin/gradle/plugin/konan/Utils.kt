/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.konan

import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection

/**
 * Get K/N compiler classpath when `this` is a compiler distribution directory.
 */
internal val Directory.konanClasspath: FileCollection
    get() = dir("konan/lib").asFileTree.matching {
        include("trove4j.jar")
        include("kotlin-native-compiler-embeddable.jar")
    }