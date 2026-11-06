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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.netbeans.modules.nbfx.platform.shell.AppState;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane.PaneRole;
import org.netbeans.modules.nbfx.platform.shell.SessionRestorer;
import org.netbeans.modules.nbfx.platform.shell.ViewTabs;
import org.netbeans.modules.nbfx.platform.shell.spi.WindowPane;
import org.netbeans.modules.nbfx.platform.shell.spi.WindowPaneContext;
import org.netbeans.modules.nbfx.platform.shell.spi.WindowPaneProvider;
import org.netbeans.modules.nbfx.platform.workspace.ViewRegistry;
import org.netbeans.modules.nbfx.platform.workspace.ViewSpec;
import javafx.scene.control.Tab;
import org.openide.util.NbBundle;

/**
 * Creates the IDE navigator pane and fills it from the persisted layout or the registered
 * navigator views. The shell only knows the registered {@code NAVIGATOR} role; the product owns the
 * pane's content.
 */
final class NavigatorPaneProvider implements WindowPaneProvider {

    private final SessionRestorer sessionRestorer;
    private final List<NavigatorView> providers;
    private final Consumer<NbfxTabPane> paneSink;

    NavigatorPaneProvider(SessionRestorer sessionRestorer,
            Collection<? extends NavigatorView> providers,
            Consumer<NbfxTabPane> paneSink) {
        this.sessionRestorer = sessionRestorer;
        this.providers = List.copyOf(providers);
        this.paneSink = paneSink;
    }

    @Override
    public NbfxTabPane createPane(WindowPane pane, WindowPaneContext context) {
        return PaneRole.NAVIGATOR.name().equals(pane.id()) ? createNavigator() : null;
    }

    private NbfxTabPane createNavigator() {
        NbfxTabPane navigator = new NbfxTabPane(PaneRole.NAVIGATOR);
        paneSink.accept(navigator);
        // Every persisted navigator tab is docked here first, whichever pane the layout last put it
        // in; opening a project then redistributes them (restoreLayout). This keeps every navigator
        // view reachable even when no project is ever opened.
        AppState.Layout layout = sessionRestorer.startupLayout();
        // On the first run there is no persisted layout, so the order and the set of navigators come
        // from the layer (position and navigator flag declared with @ViewRegistration); afterwards
        // the persisted order, which the user arranged, wins.
        List<String> order = navigatorIdsOf(layout);
        if (layout.isEmpty()) {
            order = registeredNavigatorIds();
        }
        for (NavigatorView provider : orderedProviders(order)) {
            // A provider missing from a persisted layout was closed by the user; Reset Windows brings
            // it back. Only the absence of any layout at all (first run) docks every provider - an
            // empty order means the user closed them all, and docking them here would flash them into
            // the pane until the project's own layout removed them again.
            if (layout.isEmpty() || order.contains(provider.getId())) {
                NbfxTabPane.attachTab(navigator, createTab(provider, providers));
            }
        }
        String selected = navigatorSelectedOf(layout);
        if (selected != null) {
            for (Tab tab : navigator.getTabs()) {
                if (selected.equals(NbfxTabPane.viewId(tab))) {
                    navigator.getSelectionModel().select(tab);
                    break;
                }
            }
        }
        return navigator;
    }

    /** The view provider ids held by {@code layout}, in pane order then tab order. */
    private static List<String> navigatorIdsOf(AppState.Layout layout) {
        List<String> ids = new ArrayList<>();
        for (AppState.PaneLayout pane : layout.panes()) {
            for (AppState.TabEntry tab : pane.tabs()) {
                if (tab.kind() == AppState.TabKind.VIEW) {
                    ids.add(tab.id());
                }
            }
        }
        return ids;
    }

    /**
     * The ids of the navigators registered in the layer, in declared position order, so the first
     * run docks them where {@code @ViewRegistration} says. Views that are not navigators, and
     * navigators not declared in the layer, are not included.
     */
    private static List<String> registeredNavigatorIds() {
        List<String> ids = new ArrayList<>();
        for (ViewSpec registration : ViewRegistry.discover()) {
            if (registration.navigator()) {
                ids.add(registration.id());
            }
        }
        return ids;
    }

    /** The navigator provider selected in {@code layout}'s navigator pane, or {@code null}. */
    private static String navigatorSelectedOf(AppState.Layout layout) {
        for (AppState.PaneLayout pane : layout.panes()) {
            if (pane.role() != PaneRole.NAVIGATOR) {
                continue;
            }
            AppState.TabEntry active = pane.activeIndex() >= 0 && pane.activeIndex() < pane.tabs().size()
                    ? pane.tabs().get(pane.activeIndex()) : null;
            return active != null && active.kind() == AppState.TabKind.VIEW ? active.id() : null;
        }
        return null;
    }

    /** Providers ordered by {@code order} (persisted ids); unknown providers keep their natural order at the end. */
    /** Builds a navigator tab for {@code provider}, naming its shortcut in the tooltip. */
    static Tab createTab(NavigatorView provider, Collection<? extends NavigatorView> providers) {
        int index = new ArrayList<>(providers).indexOf(provider);
        return ViewTabs.create(provider, NbBundle.getMessage(NavigatorPaneProvider.class,
                "Tab.navigator.tooltip", provider.getDescription(),
                NbfxTabPane.SYM_SHIFT, NbfxTabPane.SYM_CMD, String.valueOf(index + 1)));
    }

    private List<NavigatorView> orderedProviders(List<String> order) {
        List<NavigatorView> ordered = new ArrayList<>(providers);
        if (!order.isEmpty()) {
            ordered.sort((first, second) -> {
                int firstIndex = order.indexOf(first.getId());
                int secondIndex = order.indexOf(second.getId());
                return Integer.compare(
                        firstIndex < 0 ? Integer.MAX_VALUE : firstIndex,
                        secondIndex < 0 ? Integer.MAX_VALUE : secondIndex);
            });
        }
        return ordered;
    }
}
