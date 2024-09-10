/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

import org.jetbrains.kotlin.gradle.plugin.konan.tasks.KonanCacheTask
import org.jetbrains.kotlin.gradle.plugin.konan.tasks.KonanInteropTask
import org.jetbrains.kotlin.PlatformInfo
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.konan.util.*
import org.jetbrains.kotlin.nativeDistribution.nativeDistribution
import org.jetbrains.kotlin.platformLibs.*
import org.jetbrains.kotlin.platformManager
import org.jetbrains.kotlin.utils.capitalized

plugins {
    id("base")
    id("platform-manager")
}

// region: Util functions.
fun KonanTarget.defFiles() = familyDefFiles(family).map { DefFile(it, this) }

fun defFileToLibName(target: String, name: String) = "$target-$name"

private fun interopTaskName(libName: String, targetName: String) = "compileKonan${libName.capitalized}${targetName.capitalized}"
private fun cacheTaskName(target: String, name: String) = "${defFileToLibName(target, name)}Cache"

// endregion

if (HostManager.host == KonanTarget.MACOS_ARM64) {
    project.configureJvmToolchain(JdkMajorVersion.JDK_17_0)
}

val cacheableTargetNames = platformManager.hostPlatform.cacheableTargets

val updateDefFileDependenciesTask = tasks.register("updateDefFileDependencies")
val updateDefFileTasksPerFamily = if (HostManager.hostIsMac) {
    registerUpdateDefFileDependenciesForAppleFamiliesTasks(updateDefFileDependenciesTask)
} else {
    emptyMap()
}


enabledTargets(platformManager).forEach { target ->
    val targetName = target.visibleName
    val installTasks = mutableListOf<TaskProvider<out Task>>()
    val cacheTasks = mutableListOf<TaskProvider<out Task>>()

    target.defFiles().forEach { df ->
        val libName = defFileToLibName(targetName, df.name)
        val fileNamePrefix = PlatformLibsInfo.namePrefix
        val artifactName = "${fileNamePrefix}${df.name}"

        val libTask = tasks.register(interopTaskName(libName, targetName), KonanInteropTask::class.java) {
            group = BasePlugin.BUILD_GROUP
            description = "Build the Kotlin/Native platform library '$libName' for '$target'"

            this.compilerDistribution.set(nativeDistribution)
            dependsOn(":kotlin-native:${targetName}CrossDist")
            updateDefFileTasksPerFamily[target.family]?.let { dependsOn(it) }

            this.target.set(targetName)
            this.outputDirectory.set(
                    layout.buildDirectory.dir("konan/libs/$targetName/${fileNamePrefix}${df.name}")
            )
            df.file?.let { this.defFile.set(it) }
            df.config.depends.forEach { defName ->
                this.klibFiles.from(tasks.named(interopTaskName(defFileToLibName(targetName, defName), targetName)))
            }
            // Keep the path relative to hit build cache.
            val fmodulesCache = project.layout.buildDirectory.dir("clangModulesCache").get().asFile.toRelativeString(project.layout.projectDirectory.asFile)
            this.extraOpts.addAll(
                    "-Xpurge-user-libs",
                    "-Xshort-module-name", df.name,
                    "-Xdisable-experimental-annotation",
                    "-no-default-libs",
                    "-no-endorsed-libs",
                    "-compiler-option", "\"-fmodules-cache-path=$fmodulesCache\"" // "" are required for command-line parsing on Windows.
            )
        }

        val klibInstallTask = tasks.register(libName, Sync::class.java) {
            from(libTask)
            into(nativeDistribution.map { it.platformLib(name = artifactName, target = targetName) })
        }
        installTasks.add(klibInstallTask)

        if (target.name in cacheableTargetNames) {
            val cacheTask = tasks.register(cacheTaskName(targetName, df.name), KonanCacheTask::class.java) {
                val dist = nativeDistribution

                compilerDistribution.set(dist)
                dependsOn(":kotlin-native:${targetName}CrossDist")

                klib.fileProvider(libTask.map { it.outputs.files.singleFile })
                this.target.set(targetName)
                this.moduleName.set(artifactName)
                this.cacheRootDirectory.set(dist.map { it.cachesRoot })

                dependsOn(":kotlin-native:${targetName}StdlibCache")
                inputs.dir(dist.map { it.stdlibCache(targetName) })

                df.config.depends.forEach { dep ->
                    // Depend on installed dependency cache
                    tasks.named<KonanCacheTask>(cacheTaskName(targetName, dep)).apply {
                        dependsOn(this)
                        inputs.dir(flatMap { it.outputDirectory })
                    }
                    // And on installed dependency klib as well.
                    tasks.named<Sync>(defFileToLibName(targetName, dep)).apply {
                        dependsOn(this)
                        inputs.dir(map { it.destinationDir })
                    }
                }
            }
            cacheTasks.add(cacheTask)
        }
    }

    tasks.register("${targetName}Install") {
        dependsOn(installTasks)
    }

    if (target.name in cacheableTargetNames) {
        tasks.register("${targetName}Cache") {
            dependsOn(cacheTasks)

            group = BasePlugin.BUILD_GROUP
            description = "Builds the compilation cache for platform: $targetName"
        }
    }
}

val hostInstall by tasks.registering {
    dependsOn("${PlatformInfo.hostName}Install")
}

val hostCache by tasks.registering {
    dependsOn("${PlatformInfo.hostName}Cache")
}

val cache by tasks.registering {
    dependsOn(tasks.withType(KonanCacheTask::class.java))

    group = BasePlugin.BUILD_GROUP
    description = "Builds all the compilation caches"
}
