/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.jps.build;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("jps-plugin/testData/incremental/lookupTracker")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class LookupTrackerTestGenerated extends AbstractLookupTrackerTest {
    public void testAllFilesPresentInLookupTracker() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/lookupTracker"), Pattern.compile("^([^\\.]+)$"), false);
    }

    @TestMetadata("classifierMembers")
    public void testClassifierMembers() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("jps-plugin/testData/incremental/lookupTracker/classifierMembers/");
        doTest(fileName);
    }

    @TestMetadata("conventions")
    public void testConventions() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("jps-plugin/testData/incremental/lookupTracker/conventions/");
        doTest(fileName);
    }

    @TestMetadata("expressionType")
    public void testExpressionType() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("jps-plugin/testData/incremental/lookupTracker/expressionType/");
        doTest(fileName);
    }

    @TestMetadata("java")
    public void testJava() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("jps-plugin/testData/incremental/lookupTracker/java/");
        doTest(fileName);
    }

    @TestMetadata("localDeclarations")
    public void testLocalDeclarations() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("jps-plugin/testData/incremental/lookupTracker/localDeclarations/");
        doTest(fileName);
    }

    @TestMetadata("packageDeclarations")
    public void testPackageDeclarations() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("jps-plugin/testData/incremental/lookupTracker/packageDeclarations/");
        doTest(fileName);
    }

    @TestMetadata("simple")
    public void testSimple() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("jps-plugin/testData/incremental/lookupTracker/simple/");
        doTest(fileName);
    }

    @TestMetadata("syntheticProperties")
    public void testSyntheticProperties() throws Exception {
        String fileName = KotlinTestUtils.navigationMetadata("jps-plugin/testData/incremental/lookupTracker/syntheticProperties/");
        doTest(fileName);
    }
}
