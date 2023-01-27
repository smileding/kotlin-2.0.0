/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    groovy
    id("org.gradle.kotlin.kotlin-dsl")
    id("org.jetbrains.kotlin.plugin.sam.with.receiver")
}

buildscript {
    val rootBuildDirectory by extra(project.file("../.."))

    apply(from = rootBuildDirectory.resolve("kotlin-native/gradle/loadRootProperties.gradle"))
    dependencies {
        classpath("com.google.code.gson:gson:2.8.9")
    }
}

val kotlinVersion = project.bootstrapKotlinVersion
val metadataVersion = "0.0.1-dev-10"

repositories {
    maven("https://cache-redirector.jetbrains.com/maven-central")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-dependencies")
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    api(gradleApi())

    api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion") { isTransitive = false }
    implementation("org.jetbrains.kotlin:kotlin-build-gradle-plugin:${kotlinBuildProperties.buildGradlePluginVersion}")

    val versionProperties = Properties()
    project.rootProject.projectDir.resolve("../gradle/versions.properties").inputStream().use { propInput ->
        versionProperties.load(propInput)
    }
    implementation("com.google.code.gson:gson:2.8.9")
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.google.code.gson" && requested.name == "gson") {
                useVersion(versionProperties["versions.gson"] as String)
                because("Force using same gson version because of https://github.com/google/gson/pull/1991")
            }
        }
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")
    implementation("org.jetbrains.kotlin:kotlin-native-utils:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-klib:$metadataVersion")

    api(project(":kotlin-native-shared"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

val compileKotlin: KotlinCompile by tasks
val compileGroovy: GroovyCompile by tasks

compileKotlin.apply {
    kotlinOptions {
        freeCompilerArgs += listOf(
                "-Xskip-prerelease-check",
                "-Xsuppress-version-warnings",
                "-opt-in=kotlin.ExperimentalStdlibApi")
    }
}

// Add Kotlin classes to a classpath for the Groovy compiler
compileGroovy.apply {
    classpath += project.files(compileKotlin.destinationDirectory)
    dependsOn(compileKotlin)
}


gradlePlugin {
    plugins {
        create("compileToBitcode") {
            id = "compile-to-bitcode"
            implementationClass = "CompileToBitcodePlugin"
        }
        create("runtimeTesting") {
            id = "runtime-testing"
            implementationClass = "RuntimeTestingPlugin"
        }
        create("compilationDatabase") {
            id = "compilation-database"
            implementationClass = "CompilationDatabasePlugin"
        }
        create("konan") {
            id = "konan"
            implementationClass = "org.jetbrains.kotlin.gradle.plugin.konan.KonanPlugin"
        }
        // We bundle a shaded version of kotlinx-serialization plugin
        create("kotlinx-serialization-native") {
            id = "kotlinx-serialization-native"
            implementationClass = "shadow.org.jetbrains.kotlinx.serialization.gradle.SerializationGradleSubplugin"
        }

        create("org.jetbrains.kotlin.konan") {
            id = "org.jetbrains.kotlin.konan"
            implementationClass = "org.jetbrains.kotlin.gradle.plugin.konan.KonanPlugin"
        }

        create("native") {
            id = "native"
            implementationClass = "org.jetbrains.gradle.plugins.tools.NativePlugin"
        }

        create("native-interop-plugin") {
            id = "native-interop-plugin"
            implementationClass = "org.jetbrains.kotlin.NativeInteropPlugin"
        }
    }
}
