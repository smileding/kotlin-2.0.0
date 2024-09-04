/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import groovy.util.Node
import groovy.util.NodeList
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.internal.attributes.artifactGroupAttribute
import org.jetbrains.kotlin.gradle.internal.attributes.artifactIdAttribute
import org.jetbrains.kotlin.gradle.internal.attributes.artifactVersionAttribute
import org.jetbrains.kotlin.gradle.internal.attributes.withArtifactIdAttribute
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetComponent
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
    project: Project,
    component: KotlinTargetComponent,
) {

    // Get the dependencies mapping according to the component's UsageContexts:
    private val dependenciesMappingForEachUsageContext by project.provider {
        component.internal.usages.toList().mapNotNull { usage ->
            // When maven scope is not set, we can shortcut immediately here, since no dependencies from that usage context
            // will be present in maven pom, e.g. from sourcesElements
            val mavenScope = usage.mavenScope ?: return@mapNotNull null
            associateDependenciesWithActualModuleDependencies(usage.compilation, mavenScope)
                // We are only interested in dependencies that are mapped to some other dependencies:
                .filter { (from, to) -> Triple(from.group, from.name, from.version) != Triple(to.group, to.name, to.version) }
        }
    }

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
                dependenciesNode.remove(dependencyNode)
            }
        }
    }
}

private fun associateDependenciesWithActualModuleDependencies(
    compilation: KotlinCompilation<*>,
    mavenScope: MavenScope,
): Map<ModuleCoordinates, ModuleCoordinates> {
    val project = compilation.target.project

    val targetDependenciesConfiguration = project.configurations.getByName(
        when (mavenScope) {
            MavenScope.COMPILE -> compilation.compileDependencyConfigurationName
            MavenScope.RUNTIME -> compilation.runtimeDependencyConfigurationName ?: return emptyMap()
        }
    )

    return LazyResolvedConfiguration(targetDependenciesConfiguration)
        .allResolvedDependencies
        .mapNotNull { resolvedDependency ->
            val requestedDependency = resolvedDependency.requested as? ModuleComponentSelector ?: return@mapNotNull null
            val resolvedVariant = resolvedDependency.resolvedVariant.lastExternalVariantOrSelf()
            // with this check we separate project-to-project dependencies from the external depencencies
            if (resolvedVariant.attributes.contains(withArtifactIdAttribute)) {
                if (resolvedVariant.attributes.getAttribute(withArtifactIdAttribute) != true) return@mapNotNull null
                ModuleCoordinates(
                    requestedDependency.group,
                    requestedDependency.module,
                    requestedDependency.version
                ) to ModuleCoordinates(
                    resolvedVariant.attributes.getAttribute(artifactGroupAttribute),
                    resolvedVariant.attributes.getAttribute(artifactIdAttribute) ?: "undefined",
                    resolvedVariant.attributes.getAttribute(artifactVersionAttribute)
                )
            } else {
                val resolvedDependencyVariant = resolvedVariant.owner as? ModuleComponentIdentifier ?: return@mapNotNull null
                ModuleCoordinates(
                    requestedDependency.group,
                    requestedDependency.module,
                    requestedDependency.version
                ) to ModuleCoordinates(
                    resolvedDependencyVariant.group,
                    resolvedDependencyVariant.module,
                    resolvedDependencyVariant.version
                )
            }
        }.associate { it }
}

private fun KotlinTargetComponent.findUsageContext(configurationName: String): UsageContext? {
    val usageContexts = when (this) {
        is SoftwareComponentInternal -> usages
        else -> emptySet()
    }
    return usageContexts.find { usageContext ->
        if (usageContext !is KotlinUsageContext) return@find false
        val compilation = usageContext.compilation
        val outgoingConfigurations = mutableListOf(
            compilation.target.apiElementsConfigurationName,
            compilation.target.runtimeElementsConfigurationName
        )
        if (compilation is KotlinJvmAndroidCompilation) {
            val androidVariant = compilation.androidVariant
            outgoingConfigurations += listOf(
                "${androidVariant.name}ApiElements",
                "${androidVariant.name}RuntimeElements",
            )
        }
        configurationName in outgoingConfigurations
    }
}