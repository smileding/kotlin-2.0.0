/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.standalone.fir.test.cases.generated.cases.components.completionCandidateChecker;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.analysis.api.standalone.fir.test.configurators.AnalysisApiFirStandaloneModeTestConfiguratorFactory;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisApiTestConfiguratorFactoryData;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisApiTestConfigurator;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.TestModuleKind;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.FrontendKind;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisSessionMode;
import org.jetbrains.kotlin.analysis.test.framework.test.configurators.AnalysisApiMode;
import org.jetbrains.kotlin.analysis.api.impl.base.test.cases.components.completionCandidateChecker.AbstractCompletionCandidateExtensionChecker;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.analysis.api.GenerateAnalysisApiTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable")
@TestDataPath("$PROJECT_ROOT")
public class FirStandaloneNormalAnalysisSourceModuleCompletionCandidateExtensionCheckerGenerated extends AbstractCompletionCandidateExtensionChecker {
    @NotNull
    @Override
    public AnalysisApiTestConfigurator getConfigurator() {
        return AnalysisApiFirStandaloneModeTestConfiguratorFactory.INSTANCE.createConfigurator(
            new AnalysisApiTestConfiguratorFactoryData(
                FrontendKind.Fir,
                TestModuleKind.Source,
                AnalysisSessionMode.Normal,
                AnalysisApiMode.Standalone
            )
        );
    }

    @Test
    public void testAllFilesPresentInCheckExtensionIsSuitable() throws Exception {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable"), Pattern.compile("^(.+)\\.kt$"), null, true);
    }

    @Test
    @TestMetadata("explicitReceiver.kt")
    public void testExplicitReceiver() throws Exception {
        runTest("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable/explicitReceiver.kt");
    }

    @Test
    @TestMetadata("explicitReceiverCompanionObject.kt")
    public void testExplicitReceiverCompanionObject() throws Exception {
        runTest("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable/explicitReceiverCompanionObject.kt");
    }

    @Test
    @TestMetadata("explicitReceiverObject.kt")
    public void testExplicitReceiverObject() throws Exception {
        runTest("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable/explicitReceiverObject.kt");
    }

    @Test
    @TestMetadata("implicitReceiver.kt")
    public void testImplicitReceiver() throws Exception {
        runTest("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable/implicitReceiver.kt");
    }

    @Nested
    @TestMetadata("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable/callableReference")
    @TestDataPath("$PROJECT_ROOT")
    public class CallableReference {
        @Test
        public void testAllFilesPresentInCallableReference() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable/callableReference"), Pattern.compile("^(.+)\\.kt$"), null, true);
        }

        @Test
        @TestMetadata("explicitReceiver.kt")
        public void testExplicitReceiver() throws Exception {
            runTest("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable/callableReference/explicitReceiver.kt");
        }

        @Test
        @TestMetadata("explicitReceiverObject.kt")
        public void testExplicitReceiverObject() throws Exception {
            runTest("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable/callableReference/explicitReceiverObject.kt");
        }

        @Test
        @TestMetadata("explicitReceiverTypeReference.kt")
        public void testExplicitReceiverTypeReference() throws Exception {
            runTest("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable/callableReference/explicitReceiverTypeReference.kt");
        }

        @Test
        @TestMetadata("implicitReceiver.kt")
        public void testImplicitReceiver() throws Exception {
            runTest("analysis/analysis-api/testData/components/completionCandidateChecker/checkExtensionIsSuitable/callableReference/implicitReceiver.kt");
        }
    }
}
