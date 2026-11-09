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


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.netbeans.api.nbfx.view.ViewInfo;
import org.netbeans.api.nbfx.view.ViewManager;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.api.nbfx.views.ViewArea;
import org.netbeans.api.nbfx.views.ViewKind;
import org.netbeans.modules.nbfx.platform.workspace.ViewRegistry;
import org.netbeans.modules.nbfx.platform.workspace.ViewSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import org.openide.util.lookup.ServiceProvider;

/**
 * The {@link ViewManager}: shows a view's tab where it currently is - any pane, docked or detached -
 * or docks a new one at the view's default location. It also reports the live open views and keeps
 * an open-view menu current.
 */
@ServiceProvider(service = ViewManager.class)
public class ViewManagerService implements ViewManager {

    private static final Logger LOG = Logger.getLogger(ViewManagerService.class.getName());

    private final List<Runnable> openViewsListeners = new CopyOnWriteArrayList<>();

    public ViewManagerService() {
        NbfxTabPane.addActiveTabListener(this::fireOpenViewsChanged);
        NbfxTabPane.addViewTabsListener(this::fireOpenViewsChanged);
    }

    @Override
    public void show(ViewProvider view) {
        Map<String, ViewSpec> specs = specsById();
        ViewSpec spec = specs.get(view.getId());
        show(view, spec == null ? null : spec.area(),
                spec == null ? Integer.MAX_VALUE : spec.position(), specs);
    }

    @Override
    public void show(ViewProvider view, ViewArea area, int position) {
        show(view, area, position, specsById());
    }

    private void show(ViewProvider view, ViewArea area, int position, Map<String, ViewSpec> specs) {
        Tab existing = NbfxTabPane.findViewTab(view.getId()).orElse(null);
        if (existing != null) {
            NbfxTabPane.selectTabAndMoveToFront(existing);
            NbfxTabPane.focusDocument(existing);
            return;
        }
        ViewSpec spec = specs.get(view.getId());
        TabPane target = ViewPlacement.homeFor(spec, view, area);
        if (target == null) {
            LOG.warning(() -> "No pane to show the view " + view.getId() + " in");
            return;
        }
        Tab tab = ViewTabs.create(view, view.getDescription());
        NbfxTabPane.attachTabAt(target, tab, insertionIndex(target, position, view.getId(), specs));
        NbfxTabPane.focusDocument(tab);
    }

    /** The registrations of all views, once per show call. */
    private static Map<String, ViewSpec> specsById() {
        Map<String, ViewSpec> specs = new LinkedHashMap<>();
        for (ViewSpec spec : ViewRegistry.discover()) {
            specs.put(spec.id(), spec);
        }
        return specs;
    }

    /** The position of a new view tab among the view tabs already in {@code target}. */
    private static int insertionIndex(TabPane target, int position, String id,
            Map<String, ViewSpec> specs) {
        for (int i = 0; i < target.getTabs().size(); i++) {
            String otherId = NbfxTabPane.viewId(target.getTabs().get(i));
            if (otherId == null) {
                continue;
            }
            ViewSpec other = specs.get(otherId);
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

    @Override
    public List<ViewInfo> openViews() {
        Map<String, ViewSpec> specs = specsById();
        TabPane focused = NbfxTabPane.focusedPane();
        List<ViewInfo> result = new ArrayList<>();
        for (TabPane pane : NbfxTabPane.tabPanes()) {
            boolean detached = NbfxTabPane.roleOf(pane) == NbfxTabPane.PaneRole.DETACHED;
            for (Tab tab : pane.getTabs()) {
                if (!(tab.getProperties().get(NbfxTabPane.VIEW_PROVIDER_KEY)
                        instanceof ViewProvider view)) {
                    continue;
                }
                ViewSpec spec = specs.get(view.getId());
                ViewKind kind = spec == null ? ViewKind.CUSTOM : spec.kind();
                ViewArea area = spec == null
                        ? ViewArea.defaultFor(view.getDefaultLocation()).orElse(ViewArea.EXPLORER)
                        : spec.area();
                int position = spec == null ? Integer.MAX_VALUE : spec.position();
                String displayName = spec == null ? view.getTitle() : spec.displayName();
                String iconName = spec == null ? "" : spec.iconName();
                boolean active = pane == focused
                        && pane.getSelectionModel().getSelectedItem() == tab;
                result.add(new ViewInfo(view.getId(), displayName, iconName, kind, area,
                        position, active, detached));
            }
        }
        result.sort(Comparator.comparing((ViewInfo info) -> info.kind().id())
                .thenComparingInt(ViewInfo::position)
                .thenComparing(ViewInfo::id));
        return List.copyOf(result);
    }

    @Override
    public Optional<ViewInfo> activeView() {
        return openViews().stream().filter(ViewInfo::active).findFirst();
    }

    @Override
    public void activate(String viewId) {
        if (viewId != null) {
            NbfxTabPane.findViewTab(viewId).ifPresent(NbfxTabPane::selectTabAndMoveToFront);
        }
    }

    @Override
    public void addOpenViewsListener(Runnable listener) {
        if (listener != null) {
            openViewsListeners.add(listener);
        }
    }

    @Override
    public void removeOpenViewsListener(Runnable listener) {
        if (listener != null) {
            openViewsListeners.remove(listener);
        }
    }

    private void fireOpenViewsChanged() {
        if (openViewsListeners.isEmpty()) {
            return;
        }
        for (Runnable listener : List.copyOf(openViewsListeners)) {
            listener.run();
        }
    }
}
