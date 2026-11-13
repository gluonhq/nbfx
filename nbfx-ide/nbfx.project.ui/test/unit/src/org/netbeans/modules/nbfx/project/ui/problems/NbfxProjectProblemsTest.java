/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.nbfx.project.ui.problems;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.netbeans.spi.project.ui.ProjectProblemsImplementation;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.ProjectProblem;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.Status;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the service selection and the alert/customizer contracts of
 * {@link NbfxProjectProblems}, including the FX-thread deadlock rule of
 * {@code ProjectProblems.showCustomizer()}.
 */
public class NbfxProjectProblemsTest {

    private static final String SUPPRESS_PROPERTY = "BrokenReferencesSupport.suppressBrokenRefAlert";

    private final NbfxProjectProblems implementation = new NbfxProjectProblems();
    private FileObject root;
    private TestProject.Provider provider;
    private TestProject project;

    @Before
    public void setUp() throws IOException {
        root = FileUtil.createMemoryFileSystem().getRoot().createFolder("proj");
        provider = new TestProject.Provider();
        project = new TestProject(root, provider);
    }

    @After
    public void tearDown() {
        ProjectProblemsSettings.setShowAgain(true);
        System.clearProperty(SUPPRESS_PROPERTY);
    }

    @Test
    public void noProviderCompletesImmediately() {
        TestProject plain = new TestProject(root, null);
        assertTrue(implementation.showAlert(plain).isDone());
    }

    @Test
    public void warningOnlyDoesNotAlert() {
        provider.setProblems(List.of(ProjectProblem.createWarning("Old platform", "Details")));
        assertTrue(implementation.showAlert(project).isDone());
    }

    @Test
    public void thePreferenceSuppressesTheAlert() {
        provider.setProblems(List.of(error()));
        ProjectProblemsSettings.setShowAgain(false);
        assertTrue(implementation.showAlert(project).isDone());
    }

    @Test
    public void theCompatibilityPropertySuppressesTheAlert() {
        provider.setProblems(List.of(error()));
        System.setProperty(SUPPRESS_PROPERTY, "true");
        assertTrue(implementation.showAlert(project).isDone());
    }

    @Test
    public void customizerWithoutProblemsDoesNotDeadlockOnTheFxThread() throws InterruptedException {
        TestProject plain = new TestProject(root, null);
        assertTrue(FxTestSupport.onFx(() -> implementation.showCustomizer(plain).isDone()));
    }

    @Test
    public void customizerWithoutProblemsCompletesOffTheFxThread() throws Exception {
        TestProject plain = new TestProject(root, null);
        implementation.showCustomizer(plain).get(10, TimeUnit.SECONDS);
    }

    @Test
    public void dialogsAreJavaFxNotSwing() {
        assertEquals(javafx.scene.control.Dialog.class,
                ProjectProblemsDialog.class.getSuperclass());
        assertEquals(javafx.scene.control.Dialog.class,
                ProjectProblemsCustomizer.class.getSuperclass());
    }

    @Test
    public void uiClassesReferenceNeitherSwingNorAwt() throws Exception {
        for (Class<?> type : List.of(NbfxProjectProblems.class, ProjectProblemsDialog.class,
                ProjectProblemsCustomizer.class, ProjectProblemsMenuContributor.class)) {
            try (var in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
                assertNotNull("class file for " + type, in);
                String constantPool = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertTrue(type.getName() + " must not use Swing",
                        !constantPool.contains("javax/swing"));
                assertTrue(type.getName() + " must not use AWT",
                        !constantPool.contains("java/awt"));
            }
        }
    }

    @Test
    public void serviceRegistrationSupersedesTheSwingImplementation() throws Exception {
        var resource = NbfxProjectProblems.class.getResource(
                "/META-INF/services/" + ProjectProblemsImplementation.class.getName());
        assertNotNull("service registration exists", resource);
        String content;
        try (var in = resource.openStream()) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(content.contains(NbfxProjectProblems.class.getName()));
        assertTrue("the Swing implementation is superseded",
                content.contains("#-org.netbeans.modules.project.ui.problems.BrokenReferencesImpl"));

        ProjectProblemsImplementation found = Lookups.metaInfServices(
                NbfxProjectProblems.class.getClassLoader()).lookup(ProjectProblemsImplementation.class);
        assertNotNull(found);
        assertTrue(found instanceof NbfxProjectProblems);
    }

    @Test
    public void preferenceRoundTripsThroughNbPreferences() {
        ProjectProblemsSettings.setShowAgain(false);
        assertEquals(false, ProjectProblemsSettings.isShowAgain());
        ProjectProblemsSettings.setShowAgain(true);
        assertTrue(ProjectProblemsSettings.isShowAgain());
    }

    private static ProjectProblem error() {
        return ProjectProblem.createError("Missing path", "The resource is missing.",
                new TestProject.ImmediateResolver(Status.RESOLVED, null));
    }
}
