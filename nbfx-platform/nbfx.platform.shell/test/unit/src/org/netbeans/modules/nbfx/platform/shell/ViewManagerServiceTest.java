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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import org.junit.Test;
import org.netbeans.api.nbfx.view.ViewInfo;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.api.nbfx.views.ViewKind;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane.PaneRole;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the live open-view model: the list reports registered and dynamic views, activation
 * selects the tab, and listeners follow tab and selection changes.
 */
public class ViewManagerServiceTest {

    @Test
    public void reportsOpenViewsAndNotifies() throws InterruptedException {
        Fixture fixture = FxTestSupport.onFx(() -> {
            NbfxTabPane pane = new NbfxTabPane(PaneRole.DOCKED);
            Tab first = ViewTabs.create(view("test.view", "Test View"), "");
            Tab second = ViewTabs.create(view("test.other", "Other View"), "");
            pane.getTabs().addAll(first, second);
            pane.getSelectionModel().select(second);

            ViewManagerService manager = new ViewManagerService();
            AtomicInteger changes = new AtomicInteger();
            Runnable listener = changes::incrementAndGet;
            manager.addOpenViewsListener(listener);

            List<ViewInfo> views = manager.openViews().stream()
                    .filter(info -> info.id().startsWith("test."))
                    .toList();
            assertEquals(2, views.size());
            ViewInfo info = views.stream()
                    .filter(candidate -> candidate.id().equals("test.view"))
                    .findFirst().orElseThrow();
            assertEquals("Test View", info.displayName());
            assertEquals(ViewKind.CUSTOM, info.kind());
            assertFalse(info.active());

            manager.activate("test.view");
            return new Fixture(pane, first, manager, listener, changes);
        });

        FxTestSupport.onFx(() -> {
            try {
                assertTrue(fixture.first().isSelected());
                assertTrue("selection change reaches the listener",
                        fixture.changes().get() > 0);
                fixture.manager().removeOpenViewsListener(fixture.listener());
            } finally {
                NbfxTabPane.unregister(fixture.pane());
            }
            return null;
        });
    }

    @Test
    public void filtersByViewKind() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            NbfxTabPane pane = new NbfxTabPane(PaneRole.DOCKED);
            try {
                Tab tab = ViewTabs.create(view("test.filtered", "Filtered View"), "");
                pane.getTabs().add(tab);
                ViewManagerService manager = new ViewManagerService();

                OpenViewsMenuFactory.OpenViewsMenu included =
                        new OpenViewsMenuFactory.OpenViewsMenu(manager, Set.of(ViewKind.CUSTOM));
                assertTrue(included.items().stream()
                        .anyMatch(item -> "Filtered View".equals(item.getText())));

                OpenViewsMenuFactory.OpenViewsMenu excluded =
                        new OpenViewsMenuFactory.OpenViewsMenu(manager, Set.of(ViewKind.OUTPUT));
                assertFalse(excluded.items().stream()
                        .anyMatch(item -> "Filtered View".equals(item.getText())));

                included.dispose();
                excluded.dispose();
            } finally {
                NbfxTabPane.unregister(pane);
            }
            return null;
        });
    }

    private record Fixture(NbfxTabPane pane, Tab first, ViewManagerService manager,
            Runnable listener, AtomicInteger changes) {
    }

    private static ViewProvider view(String id, String title) {
        return new ViewProvider() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public Node getView() {
                return null;
            }
        };
    }
}
