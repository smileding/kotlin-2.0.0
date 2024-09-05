/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.internal.attributes

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails

internal val withArtifactIdAttribute = Attribute.of("org.jetbrains.kotlin.publish.has-artifact-id", Boolean::class.javaObjectType)
internal val artifactGroupAttribute = Attribute.of("org.jetbrains.kotlin.publish.artifact-group", String::class.java)
internal val artifactIdAttribute = Attribute.of("org.jetbrains.kotlin.publish.artifact-id", String::class.java)
internal val artifactVersionAttribute = Attribute.of("org.jetbrains.kotlin.publish.artifact-version", String::class.java)

internal val rootArtifactIdAttribute = Attribute.of("org.jetbrains.kotlin.publish.root-artifact-id", String::class.java)

internal fun setupGavAttributesMatchingStrategy(attributesSchema: AttributesSchema) {
    attributesSchema.attribute(withArtifactIdAttribute) { strategy ->
        strategy.compatibilityRules.add(WithArtifactIdAttributeCompatibilityRule::class.java)
        strategy.disambiguationRules.add(WithArtifactIdAttributeDisambiguationRule::class.java)
    }
}

internal class WithArtifactIdAttributeCompatibilityRule : AttributeCompatibilityRule<Boolean> {
    override fun execute(details: CompatibilityCheckDetails<Boolean>) = with(details) {
        if (consumerValue == true && producerValue == false) {
            compatible()
        }
    }
}

internal class WithArtifactIdAttributeDisambiguationRule : AttributeDisambiguationRule<Boolean> {
    override fun execute(details: MultipleCandidatesDetails<Boolean>) = with(details) {
        consumerValue?.let { closestMatch(it) } ?: return@with
    }
}