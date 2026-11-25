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
package org.netbeans.modules.nbfx.platform.shell;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.netbeans.api.nbfx.content.Content;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.ContentManager;
import org.netbeans.api.nbfx.layout.DockGroupId;
import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.api.nbfx.shell.WindowManager;
import org.netbeans.api.nbfx.views.ViewContent;

/**
 * The {@link WindowManager} over the shell's dock groups and the content manager. It translates
 * stable content and group identities into the tab-pane operations the runtime already supports.
 */
final class DefaultWindowManager implements WindowManager {

    private final ContentManager contentManager;

    DefaultWindowManager(ContentManager contentManager) {
        this.contentManager = Objects.requireNonNull(contentManager, "contentManager");
    }

    @Override
    public List<DockGroupId> groups() {
        return NbfxTabPane.panesByRole().stream()
                .filter(NbfxTabPane.class::isInstance)
                .map(pane -> ((NbfxTabPane) pane).groupId())
                .toList();
    }

    @Override
    public Optional<DockGroupId> primary(PaneRole role) {
        if (role == null) {
            return Optional.empty();
        }
        TabPane pane = NbfxTabPane.paneWithRole(role);
        return pane instanceof NbfxTabPane nbfx ? Optional.of(nbfx.groupId()) : Optional.empty();
    }

    @Override
    public Optional<DockGroupId> groupOf(ContentId content) {
        return findTab(content)
                .map(Tab::getTabPane)
                .filter(NbfxTabPane.class::isInstance)
                .map(pane -> ((NbfxTabPane) pane).groupId());
    }

    @Override
    public void select(ContentId content) {
        findTab(content).ifPresent(NbfxTabPane::selectTabAndMoveToFront);
    }

    @Override
    public void move(ContentId content, DockGroupId target, int index) {
        Tab tab = findTab(content).orElse(null);
        TabPane pane = paneForGroup(target);
        if (tab == null || pane == null) {
            return;
        }
        NbfxTabPane.moveTab(pane, tab, index);
    }

    @Override
    public void attach(ContentId content, DockGroupId group, int index) {
        Tab tab = findTab(content).orElse(null);
        TabPane pane = paneForGroup(group);
        if (tab == null || pane == null) {
            return;
        }
        if (index < 0 || index >= pane.getTabs().size()) {
            NbfxTabPane.attachTab(pane, tab);
        } else {
            NbfxTabPane.moveTab(pane, tab, index);
            NbfxTabPane.selectTabAndMoveToFront(tab);
        }
    }

    private Optional<Tab> findTab(ContentId contentId) {
        if (contentId == null) {
            return Optional.empty();
        }
        Content content = contentManager.find(contentId).orElse(null);
        if (content instanceof ViewContent viewContent) {
            return NbfxTabPane.findViewTab(viewContent.view().getId());
        }
        if (content instanceof EditorContent editorContent) {
            return NbfxTabPane.findTab(editorContent.document());
        }
        // Fallback: scan tabs by view id derived from content id convention.
        String value = contentId.value();
        if (value.startsWith("nbfx:view.")) {
            String viewId = value.substring("nbfx:view.".length());
            return NbfxTabPane.findViewTab(viewId);
        }
        return Optional.empty();
    }

    private static TabPane paneForGroup(DockGroupId groupId) {
        if (groupId == null) {
            return null;
        }
        for (TabPane pane : NbfxTabPane.panesByRole()) {
            if (pane instanceof NbfxTabPane nbfx && groupId.equals(nbfx.groupId())) {
                return pane;
            }
        }
        return null;
    }
}
