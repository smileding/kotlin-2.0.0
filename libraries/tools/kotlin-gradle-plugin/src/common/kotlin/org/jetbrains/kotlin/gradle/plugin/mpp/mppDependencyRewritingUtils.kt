/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import groovy.util.Node
import groovy.util.NodeList
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.internal.attributes.artifactGroupAttribute
import org.jetbrains.kotlin.gradle.internal.attributes.artifactIdAttribute
import org.jetbrains.kotlin.gradle.internal.attributes.artifactVersionAttribute
import org.jetbrains.kotlin.gradle.internal.attributes.rootArtifactGroupAttribute
import org.jetbrains.kotlin.gradle.internal.attributes.rootArtifactIdAttribute
import org.jetbrains.kotlin.gradle.internal.attributes.rootArtifactVersionAttribute
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetComponent
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider.Companion.kotlinPropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsageContext.MavenScope
import org.jetbrains.kotlin.gradle.utils.LazyResolvedConfiguration
import org.jetbrains.kotlin.gradle.utils.getValue
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
    private val lazyResolvedConfigurations: List<LazyResolvedConfiguration>
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

    val associate = lazyResolvedConfiguration
        .allResolvedDependencies
        .mapNotNull { resolvedDependency ->
            val resolvedVariant =
                lazyResolvedConfiguration.getArtifacts(resolvedDependency).singleOrNull()?.variant?.lastExternalVariantOrSelf()
                    ?: resolvedDependency.resolvedVariant.lastExternalVariantOrSelf()
            val requestedDependency = resolvedDependency.requested

            createAssociationBetweenRequestedAndResolvedDependency(requestedDependency, resolvedVariant)
        }.associate { it }
    return associate
}


private fun createAssociationBetweenRequestedAndResolvedDependency(
    requestedDependency: ComponentSelector,
    resolvedVariant: ResolvedVariantResult,
): Pair<ModuleCoordinates, ModuleCoordinates>? {
    return when (requestedDependency) {
        is ProjectComponentSelector -> {
            ModuleCoordinates(
                resolvedVariant.attributes.getAttribute(rootArtifactGroupAttribute),
                resolvedVariant.attributes.getAttribute(rootArtifactIdAttribute) ?: "undefined",
                resolvedVariant.attributes.getAttribute(rootArtifactVersionAttribute)
            ) to ModuleCoordinates(
                resolvedVariant.attributes.getAttribute(artifactGroupAttribute),
                resolvedVariant.attributes.getAttribute(artifactIdAttribute) ?: "undefined",
                resolvedVariant.attributes.getAttribute(artifactVersionAttribute)
            )
        }
        is ModuleComponentSelector -> {
            val requestedCoordinates = ModuleCoordinates(
                requestedDependency.group,
                requestedDependency.module,
                requestedDependency.version
            )
            // with this check we separate project-to-project dependencies from the external dependencies
            if (resolvedVariant.attributes.getAttribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE) == "kotlin-publication-coordinates") {
                requestedCoordinates to ModuleCoordinates(
                    resolvedVariant.attributes.getAttribute(artifactGroupAttribute),
                    resolvedVariant.attributes.getAttribute(artifactIdAttribute) ?: "undefined",
                    resolvedVariant.attributes.getAttribute(artifactVersionAttribute)
                )
            } else {
                val resolvedDependencyVariant = resolvedVariant.owner as? ModuleComponentIdentifier ?: return null
                requestedCoordinates to ModuleCoordinates(
                    resolvedDependencyVariant.group,
                    resolvedDependencyVariant.module,
                    resolvedDependencyVariant.version
                )
            }
        }
        else -> return null
    }
}