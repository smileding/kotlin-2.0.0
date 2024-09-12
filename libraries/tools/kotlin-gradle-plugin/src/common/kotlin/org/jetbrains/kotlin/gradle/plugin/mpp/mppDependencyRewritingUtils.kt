/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import groovy.util.Node
import groovy.util.NodeList
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetComponent
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsageContext.MavenScope
import org.jetbrains.kotlin.gradle.utils.JsonUtils
import org.jetbrains.kotlin.gradle.utils.LazyResolvedConfiguration
import org.jetbrains.kotlin.gradle.utils.lastExternalVariantOrSelf

internal data class ModuleCoordinates(
    private val moduleGroup: String?,
    private val moduleName: String,
    private val moduleVersion: String?,
) : ModuleVersionIdentifier {
    override fun getGroup() = moduleGroup ?: "unspecified"
    override fun getName() = moduleName
    override fun getVersion() = moduleVersion ?: "unspecified"

    override fun getModule(): ModuleIdentifier = object : ModuleIdentifier {
        override fun getGroup(): String = moduleGroup ?: "unspecified"
        override fun getName(): String = moduleName
    }
}

internal class PomDependenciesRewriter(
    private val lazyResolvedConfigurations: List<LazyResolvedConfiguration>,
) {


    fun rewritePomMppDependenciesToActualTargetModules(
        pomXml: XmlProvider,
        includeOnlySpecifiedDependencies: Provider<Set<ModuleCoordinates>>? = null,
    ) {
        val dependenciesNode = (pomXml.asNode().get("dependencies") as NodeList).filterIsInstance<Node>().singleOrNull() ?: return

        val dependencyNodes = (dependenciesNode.get("dependency") as? NodeList).orEmpty().filterIsInstance<Node>()

        val dependencyByNode = mutableMapOf<Node, ModuleCoordinates>()

        // Collect all the dependencies from the nodes:
        val dependencies = dependencyNodes.map { dependencyNode ->
            fun Node.getSingleChildValueOrNull(childName: String): String? =
                ((get(childName) as NodeList?)?.singleOrNull() as Node?)?.text()

            val groupId = dependencyNode.getSingleChildValueOrNull("groupId")
            val artifactId = dependencyNode.getSingleChildValueOrNull("artifactId")
                ?: error("unexpected dependency in POM with no artifact ID: $dependenciesNode")
            val version = dependencyNode.getSingleChildValueOrNull("version")
            (ModuleCoordinates(groupId, artifactId, version)).also { dependencyByNode[dependencyNode] = it }
        }.toSet()

        val dependenciesMappingForEachUsageContext = lazyResolvedConfigurations.map {
            associateDependenciesWithActualModuleDependencies(it)
                .filter { (from, to) -> Triple(from.group, from.name, from.version) != Triple(to.group, to.name, to.version) }
        }
        val resultDependenciesForEachUsageContext = dependencies.mapNotNull { key ->
            val value = dependenciesMappingForEachUsageContext.find { key in it }?.get(key)
                ?: dependenciesMappingForEachUsageContext.find { it -> key in it.values }
                    ?.map { it.value }
                    ?.filter { key == it }
                    ?.firstOrNull()
                ?: return@mapNotNull null
            key to value
        }.toMap()

        val includeOnlySpecifiedDependenciesSet = includeOnlySpecifiedDependencies?.get()

        // Rewrite the dependency nodes according to the mapping:
        dependencyNodes.forEach { dependencyNode ->
            val moduleDependency = dependencyByNode[dependencyNode]

            if (moduleDependency != null) {
                if (includeOnlySpecifiedDependenciesSet != null && moduleDependency !in includeOnlySpecifiedDependenciesSet) {
                    dependenciesNode.remove(dependencyNode)
                    return@forEach
                }
            }

            val mapDependencyTo = resultDependenciesForEachUsageContext.get(moduleDependency)

            if (mapDependencyTo != null) {
                fun Node.setChildNodeByName(name: String, value: String?) {
                    val childNode: Node? = (get(name) as NodeList?)?.firstOrNull() as Node?
                    if (value != null) {
                        (childNode ?: appendNode(name)).setValue(value)
                    } else {
                        childNode?.let { remove(it) }
                    }
                }

                dependencyNode.setChildNodeByName("groupId", mapDependencyTo.group)
                dependencyNode.setChildNodeByName("artifactId", mapDependencyTo.name)
                dependencyNode.setChildNodeByName("version", mapDependencyTo.version)
            } else {
                // TODO(Dmitrii Krasnov): добваить ворнинг и оставить как есть
//                dependenciesNode.remove(dependencyNode)
            }
        }
    }
}

internal fun createLazyResolvableConfiguration(
    project: Project,
    component: KotlinTargetComponent,
): List<LazyResolvedConfiguration> {
    return component.internal.usages.mapNotNull { usage ->
        val mavenScope = usage.mavenScope ?: return@mapNotNull null
        val compilation = usage.compilation
        LazyResolvedConfiguration(
            project.configurations.getByName(
                when (compilation) {
                    is KotlinJvmAndroidCompilation -> {
                        // TODO handle Android configuration names in a general way once we drop AGP < 3.0.0
                        val variantName = compilation.name
                        when (mavenScope) {
                            MavenScope.COMPILE -> variantName + "CompileClasspath"
                            MavenScope.RUNTIME -> variantName + "RuntimeClasspath"
                        }
                    }
                    else -> when (mavenScope) {
                        MavenScope.COMPILE -> compilation.compileDependencyConfigurationName
                        MavenScope.RUNTIME -> compilation.runtimeDependencyConfigurationName ?: return@mapNotNull null
                    }
                }
            ),
            project.provider { "kotlin-publication-coordinates" }
        )
    }

}

private fun associateDependenciesWithActualModuleDependencies(
    lazyResolvedConfiguration: LazyResolvedConfiguration,
): Map<ModuleCoordinates, ModuleCoordinates> {

    val resolvedDependenciesByComponentIdentifier =
        lazyResolvedConfiguration.allResolvedDependencies.associateBy { it.resolvedVariant.owner }

    val mapForProjectCoordinatesMap = lazyResolvedConfiguration
        .resolvedArtifacts
        .mapNotNull { resolvedArtifact ->
            val resolvedDependency = resolvedDependenciesByComponentIdentifier.get(resolvedArtifact.variant.owner) ?: return@mapNotNull null
            val publicationCoordinates = resolvedArtifact.file.let { artifact ->
                JsonUtils.gson.fromJson(artifact.readText(), PublicationCoordinates::class.java)
            } ?: return@mapNotNull null
            val fromModuleCoordinates = when (val requested = resolvedDependency.requested) {
                is ProjectComponentSelector -> ModuleCoordinates(
                    publicationCoordinates.rootPublicationGAV.group,
                    publicationCoordinates.rootPublicationGAV.artifactId,
                    publicationCoordinates.rootPublicationGAV.version
                )
                is ModuleComponentSelector -> ModuleCoordinates(requested.group, requested.module, requested.version)
                else -> return@mapNotNull null
            }
            fromModuleCoordinates to ModuleCoordinates(
                publicationCoordinates.targetPublicationGAV.group,
                publicationCoordinates.targetPublicationGAV.artifactId,
                publicationCoordinates.targetPublicationGAV.version
            )
        }.associate { it }

    val externalLibraryCoordinatesMap = lazyResolvedConfiguration
        .allResolvedDependencies
        // externalVariant is not available for project dependency
        .filter { resolvedDependency -> resolvedDependency.resolvedVariant.externalVariant.isPresent }
        .filter { resolvedDependency -> resolvedDependency.resolvedVariant.owner is ModuleComponentIdentifier }
        .mapNotNull { resolvedDependency ->
            val componentSelector = resolvedDependency.requested as? ModuleComponentSelector ?: return@mapNotNull null
            val resolvedId =
                resolvedDependency.resolvedVariant.lastExternalVariantOrSelf().owner as? ModuleComponentIdentifier ?: return@mapNotNull null
            ModuleCoordinates(componentSelector.group, componentSelector.module, componentSelector.version) to
                    ModuleCoordinates(resolvedId.group, resolvedId.module, resolvedId.version)
        }.associate { it }

    return mapForProjectCoordinatesMap + externalLibraryCoordinatesMap
}