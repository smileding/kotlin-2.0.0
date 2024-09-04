group = "pom-rewriter"
version = "1.0.0"
plugins {
    kotlin("multiplatform")
    `maven-publish`
}

kotlin {
    jvm()
    sourceSets.commonMain.dependencies {
        api("test:included:1.0")
        api("test:substituted:1.0")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    }
}

publishing {
    repositories {
        maven {
            name = "custom"
            url = uri("<localRepo>")
        }
    }
}


configurations.all {
    resolutionStrategy {
        dependencySubstitution {
            substitute(module("test:substituted")).using(project(":local"))
        }
    }
}