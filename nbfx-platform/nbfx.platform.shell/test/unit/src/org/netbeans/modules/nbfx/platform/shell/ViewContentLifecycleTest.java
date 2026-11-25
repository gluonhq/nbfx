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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import org.junit.Test;
import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.assertTrue;

/** A view tab publishes its open and close to a bound lifecycle exactly once. */
public class ViewContentLifecycleTest {

    @Test
    public void viewTabsPublishOpenAndClose() throws InterruptedException {
        // NbfxTabPane extends a JavaFX Control; load it only after the toolkit exists.
        JavaFxTestSupport.onFx(() -> {
        });
        List<ViewProvider> opened = new CopyOnWriteArrayList<>();
        List<ViewProvider> closed = new CopyOnWriteArrayList<>();
        NbfxTabPane.ViewLifecycle lifecycle = new NbfxTabPane.ViewLifecycle() {
            @Override
            public void viewOpened(ViewProvider view) {
                opened.add(view);
            }

            @Override
            public void viewClosed(ViewProvider view) {
                closed.add(view);
            }
        };
        NbfxTabPane.addViewLifecycle(lifecycle);
        try {
            ViewProvider view = new FakeView("lifecycle-view", "Lifecycle");
            AtomicReference<Tab> tab = new AtomicReference<>();

            JavaFxTestSupport.onFx(() -> {
                TabPane pane = new NbfxTabPane(PaneRole.NAVIGATOR);
                Tab created = ViewTabs.create(view, "tip");
                NbfxTabPane.attachTab(pane, created);
                tab.set(created);
                return null;
            });

            assertTrue("a created view tab is published as opened", opened.contains(view));

            JavaFxTestSupport.onFx(() -> {
                NbfxTabPane.closeTab(tab.get());
                return null;
            });
            // closeTab defers the removal, which defers unregistering: drain one pulse in between.
            JavaFxTestSupport.onFx(() -> {
            });
            JavaFxTestSupport.onFx(() -> {
                assertTrue("a closed view tab is published as closed", closed.contains(view));
                return null;
            });
        } finally {
            NbfxTabPane.removeViewLifecycle(lifecycle);
        }
    }

    private static final class FakeView implements ViewProvider {

        private final String id;
        private final String title;

        FakeView(String id, String title) {
            this.id = id;
            this.title = title;
        }

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
            return new Pane();
        }
    }
}