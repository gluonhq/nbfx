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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.MenuItem;
import org.netbeans.api.nbfx.actions.FileContextMenuContributor;
import org.netbeans.api.nbfx.dialogs.ErrorReporter;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.ProjectProblems;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * Adds "Resolve Project Problems..." to the context menu of a broken project root. This is the way
 * back into the resolver after the alert was dismissed; the alert's {@code Resolve Problems...}
 * button is the primary path.
 */
@ServiceProvider(service = FileContextMenuContributor.class, position = 100)
public final class ProjectProblemsMenuContributor implements FileContextMenuContributor {

    private static final Logger LOG =
            Logger.getLogger(ProjectProblemsMenuContributor.class.getName());

    @Override
    public List<MenuItem> itemsFor(FileObject file) {
        return List.of();
    }

    @Override
    public List<MenuItem> itemsForFolders(List<FileObject> folders) {
        if (folders.size() != 1) {
            return List.of();
        }
        Project project = findProject(folders.get(0));
        if (project == null || !isBroken(project)) {
            return List.of();
        }
        MenuItem item = new MenuItem(NbBundle.getMessage(ProjectProblemsMenuContributor.class,
                "CTL_ResolveProjectProblems"));
        item.setOnAction(event -> {
            try {
                ProjectProblems.showCustomizer(project);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Could not show the project problems resolver", ex);
                ErrorReporter.report(
                        NbBundle.getMessage(ProjectProblemsMenuContributor.class,
                                "MSG_ProjectProblems_Title"),
                        NbBundle.getMessage(ProjectProblemsMenuContributor.class,
                                "MSG_ResolveProjectProblemsFailed"),
                        ex.getMessage(), ex);
            }
        });
        return List.of(item);
    }

    private static boolean isBroken(Project project) {
        try {
            return ProjectProblems.isBroken(project);
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "Could not check the project problems", ex);
            return false;
        }
    }

    private static Project findProject(FileObject folder) {
        if (folder == null || !folder.isFolder()) {
            return null;
        }
        try {
            return ProjectManager.getDefault().findProject(folder);
        } catch (Exception ex) {
            return null;
        }
    }
}
