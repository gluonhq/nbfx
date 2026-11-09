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
import java.util.List;
import java.util.Set;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.netbeans.api.nbfx.view.ViewInfo;
import org.netbeans.api.nbfx.view.ViewManager;
import org.netbeans.api.nbfx.views.DynamicMenu;
import org.netbeans.api.nbfx.views.DynamicMenuRegistration;
import org.netbeans.api.nbfx.views.ViewKind;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Window menu's list of open views. One checked entry per open view; the entries are grouped by
 * {@link ViewKind} and activating one brings its tab - and its detached window - to the front.
 *
 * <p>Products get this section without writing their own registry code. A product that wants a
 * filtered list can build its own {@link DynamicMenu.Factory} on top of
 * {@link ViewManager#openViews()} and {@link ViewInfo#kind()}.</p>
 *
 * @since 1.0
 */
@ServiceProvider(service = DynamicMenu.Factory.class)
@DynamicMenuRegistration(path = "Menus/Window", id = "open-views", position = 90,
        separatorBefore = true)
public final class OpenViewsMenuFactory implements DynamicMenu.Factory {

    @Override
    public DynamicMenu create(Lookup context) {
        ViewManager manager = context.lookup(ViewManager.class);
        if (manager == null) {
            manager = Lookup.getDefault().lookup(ViewManager.class);
        }
        return manager == null ? new EmptyMenu() : new OpenViewsMenu(manager, null);
    }

    /** A menu section over open views, optionally restricted to {@code kinds}; {@code null} means all. */
    static final class OpenViewsMenu implements DynamicMenu {

        private final ViewManager manager;
        private final Set<ViewKind> kinds;
        private final ObservableList<MenuItem> items = FXCollections.observableArrayList();
        private final Runnable listener = this::rebuild;

        OpenViewsMenu(ViewManager manager, Set<ViewKind> kinds) {
            this.manager = manager;
            this.kinds = kinds == null ? null : Set.copyOf(kinds);
            manager.addOpenViewsListener(listener);
            rebuild();
        }

        @Override
        public ObservableList<MenuItem> items() {
            return items;
        }

        @Override
        public void dispose() {
            manager.removeOpenViewsListener(listener);
        }

        private void rebuild() {
            List<MenuItem> rebuilt = new ArrayList<>();
            ViewKind previous = null;
            for (ViewInfo info : manager.openViews()) {
                if (kinds != null && !kinds.contains(info.kind())) {
                    continue;
                }
                if (previous != null && !previous.equals(info.kind())) {
                    rebuilt.add(new SeparatorMenuItem());
                }
                previous = info.kind();
                CheckMenuItem item = new CheckMenuItem(info.displayName());
                item.setSelected(info.active());
                if (info.detached()) {
                    item.setText(NbBundle.getMessage(OpenViewsMenuFactory.class,
                        "OpenViewsMenu.detached", info.displayName()));
                }
                item.setOnAction(event -> {
                    manager.activate(info.id());
                    item.setSelected(true);
                    Platform.runLater(OpenViewsMenu.this::rebuild);
                });
                rebuilt.add(item);
            }
            items.setAll(rebuilt);
        }
    }

    /** A section with no items, used before the view manager is available. */
    private static final class EmptyMenu implements DynamicMenu {

        @Override
        public ObservableList<MenuItem> items() {
            return FXCollections.emptyObservableList();
        }
    }
}
