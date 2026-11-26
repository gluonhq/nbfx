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


import org.netbeans.api.nbfx.project.OpenProject;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import org.netbeans.api.nbfx.views.DockLocation;
import org.netbeans.api.nbfx.views.ViewKind;
import org.netbeans.api.nbfx.views.ViewRegistration;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.api.nbfx.view.ViewManager;
import org.netbeans.api.nbfx.view.ViewProvider;
import java.nio.file.Path;
import javafx.scene.Node;
import org.netbeans.modules.nbfx.project.context.ProjectContext;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Git status window: the selected project's branch and changed files. It is opened as in
 * NetBeans, with Team ▸ Show Changes ({@link GitMenuRegistrations}) or Git ▸ Show Changes on a
 * project, folder or file ({@link GitMenuContributor}); its tab names the project, the repository
 * and the branch it shows.
 *
 * @since 1.0
 */
@ServiceProvider(service = ViewProvider.class)
@ViewRegistration(id = GitStatusViewProvider.ID, displayName = "Git",
        iconName = GitIcons.VIEW_RESOURCE, kind = ViewKind.TOOL_ID,
        location = DockLocation.CENTER_BOTTOM, position = 28)
public final class GitStatusViewProvider implements ViewProvider {

    /** The stable id of the view, used by the layout persistence and the view manager. */
    public static final String ID = "git";

    private final StringProperty title = new SimpleStringProperty(
            NbBundle.getMessage(GitStatusViewProvider.class, "Git.title"));
    private GitStatusView view;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(GitStatusViewProvider.class, "Git.title");
    }

    @Override
    public ObservableValue<String> titleProperty() {
        return title;
    }

    @Override
    public DockLocation getDefaultLocation() {
        return DockLocation.CENTER_BOTTOM;
    }

    @Override
    public synchronized Node getView() {
        if (view == null) {
            view = new GitStatusView();
            title.bind(view.titleProperty());
        }
        return view;
    }

    /**
     * Brings the Git view on screen showing the changes of {@code project}, which it keeps showing
     * until it is opened for another one; with {@code null} it keeps what it shows. Must run on the
     * JavaFX Application Thread.
     */
    public void show(OpenProject project) {
        front().show(project);
    }

    /**
     * Brings the Git view on screen showing the changes around {@code file}: those of its project,
     * or of the working tree it is in when it is part of no open project. Must run on the JavaFX
     * Application Thread.
     */
    public void show(FileObject file) {
        OpenProject project = ProjectContext.projectOf(file);
        Path projectFolder = GitMenus.folderOf(project);
        Path folder = GitMenus.changesFolder(file, projectFolder);
        boolean ofProject = folder != null && folder.equals(projectFolder);
        front().show(folder, ofProject ? project.getDisplayName() : null);
    }

    private GitStatusView front() {
        ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
        if (manager != null) {
            manager.show(this);
        }
        // The view exists once it was shown; without a view manager, as in a test, create it.
        return (GitStatusView) getView();
    }

    /** The registered instance, or {@code null} when the module is not loaded. */
    public static GitStatusViewProvider instance() {
        return Lookup.getDefault().lookupAll(ViewProvider.class).stream()
                .filter(GitStatusViewProvider.class::isInstance)
                .map(GitStatusViewProvider.class::cast)
                .findFirst()
                .orElse(null);
    }
}
