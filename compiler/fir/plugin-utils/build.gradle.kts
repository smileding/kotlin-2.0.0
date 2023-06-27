plugins {
    kotlin("jvm")
    id("jps-compatible")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    explicitApiWarning()
}

dependencies {
    api(project(":core:compiler.common"))
    api(project(":compiler:resolution.common"))
    api(project(":compiler:fir:cones"))
    api(project(":compiler:fir:tree"))
    api(project(":compiler:fir:providers"))
    api(project(":compiler:fir:semantics"))
    implementation(project(":core:util.runtime"))

    compileOnly(commonDependency("com.google.guava:guava"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}
