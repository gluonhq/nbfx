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
package org.netbeans.modules.nbfx.ide.workbench;

import org.netbeans.api.nbfx.views.DynamicMenu;

import java.util.List;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.util.Subscription;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.api.nbfx.project.ProjectRegistry;
import org.netbeans.api.nbfx.views.DynamicMenuRegistration;
import org.openide.util.Lookup;

/**
 * The Window menu's list of open projects: one checked entry per project, marking the selected one.
 * It follows the registry, so opening, closing or selecting a project needs no launcher wiring.
 */
@DynamicMenuRegistration(path = "Menus/Window", id = "open-projects", position = 200)
final class OpenProjectsMenuFactory implements DynamicMenu.Factory {

    private final ProjectRegistry registry;
    private final ProjectSwitcher switcher;
    private final Function<OpenProject, Node> projectIcon;

    OpenProjectsMenuFactory(ProjectRegistry registry, ProjectSwitcher switcher,
            Function<OpenProject, Node> projectIcon) {
        this.registry = registry;
        this.switcher = switcher;
        this.projectIcon = projectIcon;
    }

    @Override
    public DynamicMenu create(Lookup context) {
        return new OpenProjectsMenu();
    }

    private final class OpenProjectsMenu implements DynamicMenu {

        private final ObservableList<MenuItem> items = FXCollections.observableArrayList();
        private final ListChangeListener<OpenProject> projectsListener = change -> rebuild();
        private final Subscription selectionSubscription;

        OpenProjectsMenu() {
            rebuild();
            registry.getOpenProjects().addListener(projectsListener);
            selectionSubscription = registry.selectedProjectProperty().subscribe(project -> markSelected());
        }

        private void rebuild() {
            List<OpenProject> open = List.copyOf(registry.getOpenProjects());
            if (open.isEmpty()) {
                items.clear();
                return;
            }
            List<MenuItem> rebuilt = new java.util.ArrayList<>(open.size() + 1);
            rebuilt.add(new SeparatorMenuItem());
            ToggleGroup group = new ToggleGroup();
            for (OpenProject project : open) {
                rebuilt.add(createItem(project, group));
            }
            items.setAll(rebuilt);
        }

        /** A checked entry for {@code project}: its name, icon, and the current selection. */
        private RadioMenuItem createItem(OpenProject project, ToggleGroup group) {
            RadioMenuItem item = new RadioMenuItem(project.getDisplayName());
            // Project names are user data: an underscore in one must not become a mnemonic.
            item.setMnemonicParsing(false);
            item.setToggleGroup(group);
            item.setUserData(project);
            item.setSelected(project.equals(registry.getSelected()));
            if (projectIcon != null) {
                Node icon = projectIcon.apply(project);
                if (icon != null) {
                    item.setGraphic(icon);
                }
            }
            item.setOnAction(event -> {
                if (switcher != null) {
                    switcher.switchTo(project);
                }
            });
            return item;
        }

        /** Moves the check mark to the selected project without rebuilding the menu. */
        private void markSelected() {
            OpenProject selected = registry.getSelected();
            for (MenuItem item : items) {
                if (item instanceof RadioMenuItem radio) {
                    radio.setSelected(radio.getUserData() != null && radio.getUserData().equals(selected));
                }
            }
        }

        @Override
        public ObservableList<MenuItem> items() {
            return items;
        }

        @Override
        public void dispose() {
            registry.getOpenProjects().removeListener(projectsListener);
            selectionSubscription.unsubscribe();
        }
    }
}
