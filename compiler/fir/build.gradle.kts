plugins {
    id("org.jetbrains.kotlinx.kover")
}

val projectsAllowedToUseFirFromSymbol = listOf(
    "analysis-tests",
    "dump",
    "fir-deserialization",
    "fir-serialization",
    "fir2ir",
    "java",
    "jvm",
    "raw-fir",
    "providers",
    "semantics",
    "resolve",
    "tree",
    "jvm-backend",
    "light-tree2fir",
    "psi2fir",
    "raw-fir.common"
)

subprojects {
    if (name in projectsAllowedToUseFirFromSymbol) {
        tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().configureEach {
            kotlinOptions {
                freeCompilerArgs += "-opt-in=org.jetbrains.kotlin.fir.symbols.SymbolInternals"
            }
        }
    }
}

//subprojects {
//    apply(plugin = "org.jetbrains.kotlinx.kover")
//}

projectTest {
    ignoreFailures = true
}