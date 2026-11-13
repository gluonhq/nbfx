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
import org.junit.Before;
import org.junit.Test;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.ProjectProblem;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.Status;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies the neutral model that replaced the Swing {@code BrokenReferencesModel}. */
public class ProjectProblemsModelTest {

    private TestProject project;
    private TestProject.Provider provider;

    @Before
    public void setUp() throws IOException {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot().createFolder("proj");
        provider = new TestProject.Provider();
        project = new TestProject(root, provider);
    }

    @Test
    public void alertModelKeepsErrorsOnly() throws InterruptedException {
        provider.setProblems(List.of(error("Missing path"), warning("Old platform")));
        FxTestSupport.onFx(() -> {
            ProjectProblemsModel model = new ProjectProblemsModel(List.of(project), false);
            try {
                assertEquals(1, model.getProblems().size());
                assertTrue(model.getProblems().get(0).isError());
                assertTrue(model.hasErrors());
            } finally {
                model.dispose();
            }
        });
    }

    @Test
    public void resolverModelKeepsWarningsToo() throws InterruptedException {
        provider.setProblems(List.of(error("Missing path"), warning("Old platform")));
        FxTestSupport.onFx(() -> {
            ProjectProblemsModel model = new ProjectProblemsModel(List.of(project), true);
            try {
                assertEquals(2, model.getProblems().size());
                assertEquals("Old platform", model.getProblems().get(1).displayName(false));
                assertTrue(model.getProblems().get(0).displayName(true).contains("proj"));
            } finally {
                model.dispose();
            }
        });
    }

    @Test
    public void identicalProblemsAreDeduplicated() throws InterruptedException {
        TestProject.ImmediateResolver resolver =
                new TestProject.ImmediateResolver(Status.RESOLVED, null);
        ProjectProblem problem = ProjectProblem.createError("Missing path", "Details", resolver);
        provider.setProblems(List.of(problem, problem));
        FxTestSupport.onFx(() -> {
            ProjectProblemsModel model = new ProjectProblemsModel(List.of(project), false);
            try {
                assertEquals(1, model.getProblems().size());
            } finally {
                model.dispose();
            }
        });
    }

    @Test
    public void providerChangeRefreshesTheList() throws InterruptedException {
        provider.setProblems(List.of(error("Missing path")));
        FxTestSupport.onFx(() -> {
            ProjectProblemsModel model = new ProjectProblemsModel(List.of(project), false);
            try {
                assertEquals(1, model.getProblems().size());
                provider.setProblems(List.of());
                assertTrue(model.getProblems().isEmpty());
                assertFalse(model.hasErrors());
            } finally {
                model.dispose();
            }
        });
    }

    @Test
    public void problemDisplayNameIncludesTheProjectOnRequest() throws InterruptedException {
        provider.setProblems(List.of(error("Missing path")));
        FxTestSupport.onFx(() -> {
            ProjectProblemsModel model = new ProjectProblemsModel(List.of(project), false);
            try {
                ProjectProblemsModel.ProblemItem item = model.getProblems().get(0);
                assertEquals("Missing path", item.displayName(false));
                assertTrue(item.displayName(true).contains("Missing path"));
                assertTrue(item.displayName(true).contains("proj"));
            } finally {
                model.dispose();
            }
        });
    }

    private static ProjectProblem error(String name) {
        return ProjectProblem.createError(name, "The resource is missing.",
                new TestProject.ImmediateResolver(Status.RESOLVED, null));
    }

    private static ProjectProblem warning(String name) {
        return ProjectProblem.createWarning(name, "The platform is old.",
                new TestProject.ImmediateResolver(Status.RESOLVED_WITH_WARNING, "Updated."));
    }
}
