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

import org.netbeans.modules.nbfx.platform.shell.api.DynamicMenu;

import java.io.File;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.netbeans.api.nbfx.views.DynamicMenuRegistration;
import org.netbeans.modules.nbfx.platform.shell.AppState;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The File menu's "Open Recent Project" section. The section rebuilds itself whenever the user
 * opens it, so the list always reflects the current recent projects without the launcher having to
 * push refreshes into the chrome.
 */
@DynamicMenuRegistration(path = "Menus/File", id = "open-recent", position = 30)
final class RecentProjectsMenuFactory implements DynamicMenu.Factory {

    private final Supplier<List<AppState.RecentProject>> recentProjects;
    private final Consumer<File> openProject;
    private final Runnable clearRecent;
    private final BiFunction<File, String, Node> projectIcon;

    RecentProjectsMenuFactory(Supplier<List<AppState.RecentProject>> recentProjects,
            Consumer<File> openProject, Runnable clearRecent,
            BiFunction<File, String, Node> projectIcon) {
        this.recentProjects = recentProjects;
        this.openProject = openProject;
        this.clearRecent = clearRecent;
        this.projectIcon = projectIcon;
    }

    @Override
    public DynamicMenu create(Lookup context) {
        return new RecentMenu();
    }

    private final class RecentMenu implements DynamicMenu {

        private final Menu menu = new Menu(message("Menu.openRecent"));
        private final ObservableList<MenuItem> items = FXCollections.observableArrayList(menu);

        RecentMenu() {
            menu.setOnShowing(event -> rebuild());
            rebuild();
        }

        private void rebuild() {
            menu.getItems().clear();
            List<AppState.RecentProject> recent = recentProjects.get();
            if (recent == null || recent.isEmpty()) {
                MenuItem empty = new MenuItem(message("Menu.openRecent.empty"));
                empty.setDisable(true);
                menu.getItems().add(empty);
                return;
            }
            for (AppState.RecentProject project : recent) {
                File dir = new File(project.path());
                MenuItem item = new MenuItem(dir.getName());
                // Project names are user data: an underscore in one must not become a mnemonic.
                item.setMnemonicParsing(false);
                if (projectIcon != null && project.iconName() != null) {
                    Node icon = projectIcon.apply(dir, project.iconName());
                    if (icon != null) {
                        item.setGraphic(icon);
                    }
                }
                item.setOnAction(event -> openProject.accept(dir));
                menu.getItems().add(item);
            }
            menu.getItems().add(new SeparatorMenuItem());
            MenuItem clear = new MenuItem(message("Menu.openRecent.clear"));
            clear.setOnAction(event -> clearRecent.run());
            menu.getItems().add(clear);
        }

        @Override
        public ObservableList<MenuItem> items() {
            return items;
        }

        @Override
        public void dispose() {
            menu.setOnShowing(null);
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(RecentProjectsMenuFactory.class, key);
    }
}
