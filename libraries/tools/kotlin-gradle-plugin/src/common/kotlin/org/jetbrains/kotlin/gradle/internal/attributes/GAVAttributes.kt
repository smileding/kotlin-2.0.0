/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.internal.attributes

import org.gradle.api.attributes.*

internal val WITH_PUBLISH_COORDINATES_ATTRIBUTE = Attribute.of("org.jetbrains.kotlin.publish.has-publish-coordinates", Boolean::class.javaObjectType)

internal fun setupGavAttributesMatchingStrategy(attributesSchema: AttributesSchema) {
    attributesSchema.attribute(WITH_PUBLISH_COORDINATES_ATTRIBUTE) { strategy ->
        strategy.compatibilityRules.add(WithArtifactIdAttributeCompatibilityRule::class.java)
        strategy.disambiguationRules.add(WithArtifactIdAttributeDisambiguationRule::class.java)
    }
}

internal class WithArtifactIdAttributeCompatibilityRule : AttributeCompatibilityRule<Boolean> {
    override fun execute(details: CompatibilityCheckDetails<Boolean>) = with(details) {
        if (consumerValue == true && producerValue == false) {
            compatible()
        }

        if (consumerValue == false && producerValue == null) {
            compatible()
        }
    }
}

internal class WithArtifactIdAttributeDisambiguationRule : AttributeDisambiguationRule<Boolean> {
    override fun execute(details: MultipleCandidatesDetails<Boolean>) = with(details) {
        if (consumerValue == null){
            closestMatch(false)
        }
        consumerValue?.let { closestMatch(it) } ?: return@with
    }
}