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
package org.netbeans.modules.nbfx.java.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.List;
import javafx.scene.control.TreeItem;
import org.junit.Test;
import org.netbeans.modules.nbfx.java.navigation.ProjectEntry.BADGE;
import org.netbeans.modules.nbfx.java.navigation.ProjectEntry.Type;

/**
 * Verifies how the "project files" group of a project node is brought in line with the project's
 * folder: replaced in place, removed, added ahead of the libraries, and left alone while the
 * project node's children are not loaded.
 */
public class ProjectFilesNodeTest {

    @Test
    public void theGroupIsReplacedInPlaceAndStaysExpanded() {
        TreeItem<ProjectEntry> sources = item("Source Packages", Type.GROUP);
        TreeItem<ProjectEntry> old = group("pom.xml", "nbactions.xml");
        old.setExpanded(true);
        TreeItem<ProjectEntry> libraries = item("Dependencies", Type.LIBRARIES);
        TreeItem<ProjectEntry> project = project(sources, old, libraries);
        TreeItem<ProjectEntry> fresh = group("pom.xml");

        ProjectNavigator.replaceProjectFilesNode(project, fresh);

        assertEquals(List.of(sources, fresh, libraries), project.getChildren());
        assertTrue("the group stays as the user had it", fresh.isExpanded());
        assertEquals(List.of("pom.xml"), names(fresh));
    }

    @Test
    public void aCollapsedGroupStaysCollapsed() {
        TreeItem<ProjectEntry> old = group("pom.xml", "nbactions.xml");
        TreeItem<ProjectEntry> project = project(item("Source Packages", Type.GROUP), old);
        TreeItem<ProjectEntry> fresh = group("pom.xml");
        fresh.setExpanded(true);

        ProjectNavigator.replaceProjectFilesNode(project, fresh);

        assertTrue(!fresh.isExpanded());
    }

    @Test
    public void theGroupGoesWhenNoneOfItsFilesIsLeft() {
        TreeItem<ProjectEntry> sources = item("Source Packages", Type.GROUP);
        TreeItem<ProjectEntry> libraries = item("Dependencies", Type.LIBRARIES);
        TreeItem<ProjectEntry> project = project(sources, group("nbactions.xml"), libraries);

        ProjectNavigator.replaceProjectFilesNode(project, null);

        assertEquals(List.of(sources, libraries), project.getChildren());
    }

    @Test
    public void aNewGroupGoesAheadOfTheLibraries() {
        TreeItem<ProjectEntry> sources = item("Source Packages", Type.GROUP);
        TreeItem<ProjectEntry> libraries = item("Dependencies", Type.LIBRARIES);
        TreeItem<ProjectEntry> project = project(sources, libraries);
        TreeItem<ProjectEntry> fresh = group("pom.xml");

        ProjectNavigator.replaceProjectFilesNode(project, fresh);

        assertEquals(List.of(sources, fresh, libraries), project.getChildren());
    }

    @Test
    public void aNewGroupGoesLastWithoutLibraries() {
        TreeItem<ProjectEntry> sources = item("Source Packages", Type.GROUP);
        TreeItem<ProjectEntry> project = project(sources);
        TreeItem<ProjectEntry> fresh = group("pom.xml");

        ProjectNavigator.replaceProjectFilesNode(project, fresh);

        assertEquals(List.of(sources, fresh), project.getChildren());
        assertSame(project, fresh.getParent());
    }

    /** A project node not expanded yet has no children: they are built, group included, when it is. */
    @Test
    public void aProjectNodeNotLoadedYetIsLeftAlone() {
        TreeItem<ProjectEntry> project = project();

        ProjectNavigator.replaceProjectFilesNode(project, group("pom.xml"));
        ProjectNavigator.replaceProjectFilesNode(project, null);

        assertTrue(project.getChildren().isEmpty());
    }

    @SafeVarargs
    private static TreeItem<ProjectEntry> project(TreeItem<ProjectEntry>... children) {
        TreeItem<ProjectEntry> project = item("app", Type.NAME);
        project.getChildren().addAll(List.of(children));
        return project;
    }

    private static TreeItem<ProjectEntry> group(String... files) {
        TreeItem<ProjectEntry> group = new TreeItem<>(new ProjectEntry(null, "Project Files", Type.FILES, BADGE.PROJECTFILES_BADGE));
        for (String file : files) {
            group.getChildren().add(item(file, Type.FILE));
        }
        return group;
    }

    private static TreeItem<ProjectEntry> item(String name, Type type) {
        return new TreeItem<>(new ProjectEntry(null, name, type, BADGE.NO_BADGE));
    }

    private static List<String> names(TreeItem<ProjectEntry> item) {
        return item.getChildren().stream().map(child -> child.getValue().getName()).toList();
    }
}
