/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.test

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.scripting.test.repl.FirTestReplCompilerExtensionRegistrar
import org.jetbrains.kotlin.scripting.test.repl.TestReplCompilerPluginRegistrar
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.ConfigurationDirectives.WITH_STDLIB
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.runners.AbstractFirDiagnosticTestBase
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirScriptCodegenTest
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

open class AbstractReplWithCustomDefDiagnosticsTestBase : AbstractFirDiagnosticTestBase(FirParser.Psi) {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        with(builder) {
            useConfigurators(
                ::ReplConfigurator
            )
            defaultDirectives {
                +WITH_STDLIB
            }
        }
    }
}

@OptIn(ExperimentalCompilerApi::class)
private class ReplConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        configuration.add(CompilerPluginRegistrar.COMPILER_PLUGIN_REGISTRARS, TestReplCompilerPluginRegistrar())
    }
}
