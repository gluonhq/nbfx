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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.ProjectProblem;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.Status;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that the real JavaFX project problems dialogs can be shown and display the model. Uses
 * {@code show()} rather than the modal {@code showAndWait()}, so the headless FX thread is never
 * blocked by a nested event loop.
 */
public class ProjectProblemsDialogTest {

    private TestProject.Provider provider;
    private TestProject project;

    @Before
    public void setUp() throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot().createFolder("proj");
        provider = new TestProject.Provider();
        project = new TestProject(root, provider);
        FxTestSupport.onFx(() -> { });
    }

    @Test
    public void alertDialogOpensWithTheErrorProblems() throws Exception {
        provider.setProblems(List.of(error()));
        ProjectProblemsModel model = load(false);
        try {
            FxTestSupport.onFx(() -> {
                ProjectProblemsDialog dialog = new ProjectProblemsDialog(null, model);
                try {
                    dialog.show();
                    assertTrue("alert is showing", dialog.isShowing());
                    assertEquals("Project Problems", dialog.getTitle());
                    assertEquals(1, dialog.problems().getItems().size());
                    assertEquals(2, dialog.getDialogPane().getButtonTypes().size());
                } finally {
                    dialog.close();
                    assertFalse("alert is closed", dialog.isShowing());
                }
            });
        } finally {
            FxTestSupport.onFx(model::dispose);
        }
    }

    @Test
    public void customizerDialogOpensWithErrorsAndWarnings() throws Exception {
        provider.setProblems(List.of(error(), warning()));
        ProjectProblemsModel model = load(true);
        try {
            FxTestSupport.onFx(() -> {
                ProjectProblemsCustomizer dialog = new ProjectProblemsCustomizer(null, model);
                try {
                    dialog.show();
                    assertTrue("customizer is showing", dialog.isShowing());
                    assertTrue(dialog.getTitle().startsWith("Resolve Project Problems"));
                    assertEquals(2, dialog.problems().getItems().size());
                    assertEquals(2, dialog.getDialogPane().getButtonTypes().size());
                } finally {
                    dialog.close();
                    assertFalse("customizer is closed", dialog.isShowing());
                }
            });
        } finally {
            FxTestSupport.onFx(model::dispose);
        }
    }

    @Test
    public void customizerPopulatesAfterTheBackgroundLoad() throws Exception {
        provider.setProblems(List.of(error(), warning()));
        ProjectProblemsModel model = new ProjectProblemsModel(List.of(project), true);
        AtomicReference<ProjectProblemsCustomizer> dialog = new AtomicReference<>();
        FxTestSupport.onFx(() -> {
            ProjectProblemsCustomizer customizer = new ProjectProblemsCustomizer(null, model);
            customizer.show();
            dialog.set(customizer);
        });
        try {
            model.loaded().get(10, TimeUnit.SECONDS);
            FxTestSupport.onFx(() -> {
                assertTrue(dialog.get().isShowing());
                assertEquals(2, dialog.get().problems().getItems().size());
                dialog.get().close();
            });
        } finally {
            FxTestSupport.onFx(model::dispose);
        }
    }

    private ProjectProblemsModel load(boolean includeWarnings) throws Exception {
        ProjectProblemsModel model = new ProjectProblemsModel(List.of(project), includeWarnings);
        model.loaded().get(10, TimeUnit.SECONDS);
        return model;
    }

    private static ProjectProblem error() {
        return ProjectProblem.createError("Missing path", "The resource is missing.",
                new TestProject.ImmediateResolver(Status.RESOLVED, null));
    }

    private static ProjectProblem warning() {
        return ProjectProblem.createWarning("Old platform", "The platform is old.",
                new TestProject.ImmediateResolver(Status.RESOLVED_WITH_WARNING, "Updated."));
    }
}
