/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.internal.attributes

import org.gradle.api.attributes.Attribute

internal val withArtifactIdAttribute = Attribute.of("org.jetbrains.kotlin.publish.has-artifact-id", Boolean::class.javaObjectType)
internal val artifactGroupAttribute = Attribute.of("org.jetbrains.kotlin.publish.artifact-group", String::class.java)
internal val artifactIdAttribute = Attribute.of("org.jetbrains.kotlin.publish.artifact-id", String::class.java)
internal val artifactVersionAttribute = Attribute.of("org.jetbrains.kotlin.publish.artifact-version", String::class.java)