plugins {
    kotlin("jvm")
}

description = "Swift Export Frontend"

dependencies {
    compileOnly(kotlinStdlib())

    api(project(":native:swift:sir"))
    api(project(":native:swift:sir-compiler-bridge"))
    api(project(":native:swift:sir-passes"))
    api(project(":native:swift:sir-printer"))
    api(project(":native:swift:sir-analysis-api"))

    api(project(":analysis:analysis-api"))
    implementation(project(":analysis:analysis-api-standalone"))

    testApi(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.api)

    testRuntimeOnly(projectTests(":analysis:low-level-api-fir"))
    testRuntimeOnly(projectTests(":analysis:analysis-api-impl-base"))
    testImplementation(projectTests(":analysis:analysis-api-fir"))
    testImplementation(projectTests(":analysis:analysis-test-framework"))
    testImplementation(projectTests(":compiler:tests-common"))
    testImplementation(projectTests(":compiler:tests-common-new"))
}

sourceSets {
    "main" { projectDefault() }
    "test" {
        projectDefault()
        generatedTestDir()
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}


projectTest(jUnitMode = JUnitMode.JUnit5) {
    dependsOn(":dist")
    workingDir = rootDir
    useJUnitPlatform { }
}

testsJar()