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


import org.netbeans.api.nbfx.views.ViewArea;
import org.netbeans.api.nbfx.view.ViewManager;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.modules.nbfx.platform.workspace.ViewRegistry;
import org.netbeans.modules.nbfx.platform.workspace.ViewSpec;
import java.util.logging.Logger;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.openide.util.lookup.ServiceProvider;

/**
 * The {@link ViewManager}: shows a view's tab where it currently is - any pane, docked or detached -
 * or docks a new one at the view's default location.
 */
@ServiceProvider(service = ViewManager.class)
public class ViewManagerService implements ViewManager {

    private static final Logger LOG = Logger.getLogger(ViewManagerService.class.getName());

    @Override
    public void show(ViewProvider view) {
        ViewSpec spec = ViewRegistry.find(view.getId());
        show(view, spec == null ? null : spec.area(),
                spec == null ? Integer.MAX_VALUE : spec.position());
    }

    @Override
    public void show(ViewProvider view, ViewArea area, int position) {
        Tab existing = NbfxTabPane.findViewTab(view.getId()).orElse(null);
        if (existing != null) {
            NbfxTabPane.selectTabAndMoveToFront(existing);
            NbfxTabPane.focusDocument(existing);
            return;
        }
        ViewSpec spec = ViewRegistry.find(view.getId());
        if (area == null && spec != null) {
            area = spec.area();
            if (position == Integer.MAX_VALUE) {
                position = spec.position();
            }
        }
        TabPane target = area == null ? null : Docking.paneOf(area);
        if (target == null) {
            // A legacy view without a registered area, or an area whose declaration vanished: fall
            // back to the location, then to the default view pane. The tab is never dropped when a
            // registered area is missing.
            target = Docking.paneAt(area == null ? view.getDefaultLocation() : area.location());
        }
        if (target == null) {
            WindowComposition composition = Docking.composition();
            NbfxTabPane.PaneRole fallback = composition == null
                    ? WindowPaneRegistry.defaultViewRole()
                    : composition.defaultViewRole();
            target = NbfxTabPane.paneWithRole(fallback);
        }
        if (target == null) {
            LOG.warning(() -> "No pane to show the view " + view.getId() + " in");
            return;
        }
        Tab tab = ViewTabs.create(view, view.getDescription());
        NbfxTabPane.attachTabAt(target, tab, insertionIndex(target, position, view.getId()));
        NbfxTabPane.focusDocument(tab);
    }

    /** The position of a new view tab among the view tabs already in {@code target}. */
    private static int insertionIndex(TabPane target, int position, String id) {
        for (int i = 0; i < target.getTabs().size(); i++) {
            String otherId = NbfxTabPane.viewId(target.getTabs().get(i));
            if (otherId == null) {
                continue;
            }
            ViewSpec other = ViewRegistry.find(otherId);
            if (other == null) {
                continue;
            }
            if (other.position() > position
                    || (other.position() == position && other.id().compareTo(id) > 0)) {
                return i;
            }
        }
        return target.getTabs().size();
    }

    @Override
    public void hide(ViewProvider view) {
        NbfxTabPane.findViewTab(view.getId()).ifPresent(NbfxTabPane::closeTab);
    }

    @Override
    public boolean isShowing(ViewProvider view) {
        return NbfxTabPane.findViewTab(view.getId()).isPresent();
    }
}
