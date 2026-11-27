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

import java.util.List;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.netbeans.api.nbfx.actions.FileContextMenuContributor;
import org.netbeans.modules.nbfx.project.context.ProjectContext;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The version control submenu on the files, folders and projects of the Projects and Files views, as
 * in NetBeans. In a Git working tree it is Git &#9656; Show Changes, which opens the Git status
 * window, and Show History, which opens the history of the node in a tab; outside one it is Versioning &#9656; Initialize Git Repository..., which creates one.
 *
 * @since 1.2
 */
@ServiceProvider(service = FileContextMenuContributor.class)
public final class GitMenuContributor implements FileContextMenuContributor {

    @Override
    public List<MenuItem> itemsFor(FileObject file) {
        if (file == null) {
            return List.of();
        }
        return List.of(GitMenus.inWorkingTree(file) ? gitMenu(file) : versioningMenu(file));
    }

    @Override
    public List<MenuItem> itemsForFolders(List<FileObject> folders) {
        if (folders == null || folders.isEmpty()) {
            return List.of();
        }
        if (folders.stream().allMatch(GitMenus::inWorkingTree)) {
            return List.of(gitMenu(folders.getFirst()));
        }
        // One repository is made at a time, so it is offered for one folder only.
        return folders.size() == 1 ? List.of(versioningMenu(folders.getFirst())) : List.of();
    }

    private static Menu gitMenu(FileObject file) {
        MenuItem showChanges = new MenuItem(message("CTL_SelectGitCommand"), GitIcons.view(GitIcons.SHOW_CHANGES));
        showChanges.setOnAction(event -> {
            GitStatusViewProvider view = GitStatusViewProvider.instance();
            if (view != null) {
                // The changes of the project the clicked node belongs to, or of its working tree.
                view.show(file);
            }
        });
        MenuItem showHistory = new MenuItem(message("Git.history.menu"));
        // The history of the clicked node: of the file, or of all in the folder or the project.
        showHistory.setOnAction(event -> GitHistoryView.show(file));
        return new Menu(message("Git.menu"), null, showChanges, showHistory);
    }

    private static Menu versioningMenu(FileObject file) {
        MenuItem initialize = new MenuItem(message("Git.init.contextMenu"));
        initialize.setOnAction(event ->
                GitInitDialog.show(GitMenus.proposedRoot(file,
                        GitMenus.folderOf(ProjectContext.projectOf(file))), null));
        return new Menu(message("Git.versioning.menu"), null, initialize);
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitMenuContributor.class, key);
    }
}
