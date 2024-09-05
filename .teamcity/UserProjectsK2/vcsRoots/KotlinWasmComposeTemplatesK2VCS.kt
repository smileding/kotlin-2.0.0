package UserProjectsK2.vcsRoots

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

object KotlinWasmComposeTemplatesK2VCS : GitVcsRoot({
    name = "KotlinWasmComposeTemplates.k2"
    url = "git@github.com:Kotlin/kotlin-wasm-compose-template.git"
    branch = "kotlin-community/k2/dev"
    branchSpec = "+:refs/heads/(*)"
    authMethod = uploadedKey {
        uploadedKey = "default teamcity key"
    }
})
