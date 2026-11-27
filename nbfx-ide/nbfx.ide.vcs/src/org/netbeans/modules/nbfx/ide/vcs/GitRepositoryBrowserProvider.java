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

import javafx.scene.Node;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.api.nbfx.view.ViewManager;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.api.nbfx.views.DockLocation;
import org.netbeans.api.nbfx.views.ViewKind;
import org.netbeans.api.nbfx.views.ViewRegistration;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Git Repository Browser window: the repositories of the open projects with their branches,
 * tags, stashes and remotes. It is opened as in NetBeans, with Team &#9656; Repository &#9656;
 * Repository Browser ({@link GitTeamMenuFactory}).
 *
 * @since 1.4
 */
@ServiceProvider(service = ViewProvider.class)
@ViewRegistration(id = GitRepositoryBrowserProvider.ID, displayName = "#Git.browser.title",
        iconName = GitRepositoryBrowserProvider.ICON_RESOURCE, kind = ViewKind.TOOL_ID,
        location = DockLocation.CENTER_BOTTOM, position = 29)
public final class GitRepositoryBrowserProvider implements ViewProvider {

    /** The stable id of the view, used by the layout persistence and the view manager. */
    public static final String ID = "git-repository-browser";
    /** The resource path of the icon of the tab: that of a repository. */
    static final String ICON_RESOURCE = "org/netbeans/modules/nbfx/ide/vcs/icons/repository.png";

    private GitRepositoryBrowser view;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(GitRepositoryBrowserProvider.class, "Git.browser.title");
    }

    @Override
    public DockLocation getDefaultLocation() {
        return DockLocation.CENTER_BOTTOM;
    }

    @Override
    public synchronized Node getView() {
        if (view == null) {
            view = new GitRepositoryBrowser();
        }
        return view;
    }

    /**
     * Brings the browser on screen with the repository of {@code project} selected and opened; with
     * {@code null}, or a project in no repository, as it was. Must run on the JavaFX Application Thread.
     */
    public void show(OpenProject project) {
        ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
        if (manager != null) {
            manager.show(this);
        }
        // The view exists once it was shown; without a view manager, as in a test, create it.
        ((GitRepositoryBrowser) getView()).select(GitMenus.folderOf(project));
    }

    /** The registered instance, or {@code null} when the module is not loaded. */
    public static GitRepositoryBrowserProvider instance() {
        return Lookup.getDefault().lookupAll(ViewProvider.class).stream()
                .filter(GitRepositoryBrowserProvider.class::isInstance)
                .map(GitRepositoryBrowserProvider.class::cast)
                .findFirst()
                .orElse(null);
    }
}
