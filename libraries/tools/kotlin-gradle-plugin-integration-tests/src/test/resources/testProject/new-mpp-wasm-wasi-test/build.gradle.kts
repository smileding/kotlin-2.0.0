plugins {
    kotlin("multiplatform").version("<pluginMarkerVersion>")
}

with(org.jetbrains.kotlin.gradle.targets.js.d8.D8RootPlugin.apply(rootProject)) {
    // Test that we can set the version and it is a String.
    // But use the default version since update this place every time anyway.
    version = (version as String)
}

allprojects.forEach {
    it.tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask>().configureEach {
        args.add("--ignore-engines")
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {

    wasmWasi {
        nodejs {}
        binaries.executable()
    }

    sourceSets {
        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test")
            }
        }
    }
}

tasks.named<org.jetbrains.kotlin.gradle.targets.js.npm.LockCopyTask>("kotlinStorePackageLock") {
    //A little hacky way to make yarn results
    inputFile.fileValue(projectDir.resolve("packageLockStub"))
}
