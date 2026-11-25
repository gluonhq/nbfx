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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.netbeans.api.nbfx.content.Content;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.ContentManager;
import org.netbeans.api.nbfx.content.DockPlacement;
import org.netbeans.api.nbfx.layout.DockGroupId;
import org.netbeans.api.nbfx.shell.DockDecision;
import org.netbeans.api.nbfx.shell.DockPolicy;
import org.netbeans.api.nbfx.shell.DockRequest;
import org.netbeans.api.nbfx.shell.DockResult;
import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.api.nbfx.shell.WindowManager;
import org.netbeans.api.nbfx.views.ViewContent;
import org.openide.util.Lookup;

/**
 * The {@link WindowManager} over the shell's dock groups and the content manager. It translates
 * stable content and group identities into the tab-pane operations the runtime already supports.
 */
final class DefaultWindowManager implements WindowManager {

    private final ContentManager contentManager;
    private final Supplier<Collection<? extends DockPolicy>> policies;

    DefaultWindowManager(ContentManager contentManager) {
        this(contentManager, () -> Lookup.getDefault().lookupAll(DockPolicy.class));
    }

    DefaultWindowManager(ContentManager contentManager, Supplier<Collection<? extends DockPolicy>> policies) {
        this.contentManager = Objects.requireNonNull(contentManager, "contentManager");
        this.policies = Objects.requireNonNull(policies, "policies");
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

    @Override
    public DockDecision canDock(DockRequest request) {
        if (request == null || request.content() == null) {
            return DockDecision.reject("dock.request.invalid");
        }
        if (contentManager.find(request.content()).isEmpty()) {
            return DockDecision.reject("dock.content.missing");
        }
        DockGroupId target = resolveTargetGroup(request);
        if (target == null || paneForGroup(target) == null) {
            return DockDecision.reject("dock.target.missing");
        }
        DockPlacement placement = request.placement();
        if (placement != DockPlacement.INTO_GROUP && placement != DockPlacement.TAB_STRIP) {
            return DockDecision.reject("dock.placement.unsupported");
        }
        DockDecision decision = DockDecision.allow(placement);
        for (DockPolicy policy : policies.get()) {
            if (policy == null) {
                continue;
            }
            DockDecision policyDecision = policy.evaluate(request);
            if (policyDecision == null) {
                continue;
            }
            if (!policyDecision.allowed()) {
                return policyDecision;
            }
            decision = policyDecision;
        }
        return decision;
    }

    @Override
    public DockResult dock(DockRequest request) {
        DockDecision decision = canDock(request);
        if (!decision.allowed()) {
            return DockResult.rejected(decision);
        }
        DockGroupId target = resolveTargetGroup(request);
        Tab tab = findTab(request.content()).orElse(null);
        TabPane pane = paneForGroup(target);
        if (tab == null || pane == null) {
            return DockResult.rejected(DockDecision.reject("dock.target.missing"));
        }
        NbfxTabPane.attachTab(pane, tab);
        return DockResult.accepted(decision, target);
    }

    private DockGroupId resolveTargetGroup(DockRequest request) {
        if (request.targetGroup() != null) {
            return request.targetGroup();
        }
        if (request.targetArea() != null) {
            TabPane pane = NbfxTabPane.paneWithRole(request.targetArea());
            if (pane instanceof NbfxTabPane nbfx) {
                return nbfx.groupId();
            }
        }
        return null;
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
