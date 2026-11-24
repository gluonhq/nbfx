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
package org.netbeans.modules.nbfx.ide.vcs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.event.Event;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.netbeans.api.nbfx.actions.ActionContext;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.api.nbfx.views.DynamicMenu;
import org.netbeans.api.nbfx.views.DynamicMenuRegistration;
import org.netbeans.modules.nbfx.project.context.ProjectContext;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Git entries of the Team menu, as in NetBeans: Show Changes while the selected project is in a
 * Git working tree, and Git &#9656; Initialize Repository... while it is not.
 *
 * @since 1.2
 */
@ServiceProvider(service = DynamicMenu.Factory.class)
@DynamicMenuRegistration(path = "Menus/Team", id = "git", position = 10)
public final class GitTeamMenuFactory implements DynamicMenu.Factory {

    @Override
    public DynamicMenu create(Lookup context) {
        return new TeamMenu();
    }

    /**
     * Both entries, one of them visible. They are told apart again when the selection changes and
     * when the menu opens, as a repository can be made or removed outside the IDE.
     */
    static final class TeamMenu implements DynamicMenu {

        private final MenuItem showChanges = new MenuItem(message("CTL_SelectGitCommand"),
                GitIcons.menuView(GitIcons.SHOW_CHANGES));
        private final MenuItem initialize = new MenuItem(message("Git.init.menu"));
        private final Menu git = new Menu(message("Git.menu"), null, initialize);
        private final ObservableList<MenuItem> items = FXCollections.observableArrayList(showChanges, git);
        private final List<Lookup.Result<?>> contextResults = new ArrayList<>();
        private final LookupListener contextListener = event -> Platform.runLater(this::refresh);
        private final EventHandler<Event> showing = event -> refresh();
        private Menu parent;

        TeamMenu() {
            showChanges.setOnAction(event -> {
                GitStatusViewProvider view = GitStatusViewProvider.instance();
                if (view != null) {
                    view.show(ProjectContext.selectedProject());
                }
            });
            initialize.setOnAction(event -> GitInitDialog.show(GitMenus.folderOf(ProjectContext.selectedProject()),
                    this::refresh));
            ActionContext context = ActionContext.getDefault();
            if (context != null) {
                for (Class<?> type : List.of(FileObject.class, EditorDocument.class)) {
                    Lookup.Result<?> result = context.lookupResult(type);
                    result.addLookupListener(contextListener);
                    contextResults.add(result);
                }
            }
            showChanges.parentMenuProperty().subscribe(this::follow);
            refresh();
        }

        @Override
        public ObservableList<MenuItem> items() {
            return items;
        }

        @Override
        public void dispose() {
            contextResults.forEach(result -> result.removeLookupListener(contextListener));
            contextResults.clear();
            follow(null);
        }

        /** Shows the entry that fits the selected project. */
        void refresh() {
            OpenProject project = ProjectContext.selectedProject();
            Path folder = GitMenus.folderOf(project);
            boolean repository = folder != null && GitMenus.inWorkingTree(folder.toFile());
            showChanges.setVisible(repository);
            git.setVisible(!repository);
        }

        private void follow(Menu menu) {
            if (parent != null) {
                parent.removeEventHandler(Menu.ON_SHOWING, showing);
            }
            parent = menu;
            if (parent != null) {
                parent.addEventHandler(Menu.ON_SHOWING, showing);
            }
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitTeamMenuFactory.class, key);
    }
}
