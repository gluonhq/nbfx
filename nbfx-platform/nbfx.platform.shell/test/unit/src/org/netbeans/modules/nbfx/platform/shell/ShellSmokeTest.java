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

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.modules.nbfx.platform.launcher.AppProvider;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane.PaneRole;
import org.netbeans.modules.nbfx.platform.workspace.DockArea;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * Smoke test of the platform-only shell: the dock area, the tab panes and a view tab are built
 * without any IDE module. Runs on the headless JavaFX toolkit.
 */
public class ShellSmokeTest {

    @Test
    public void defaultProviderStartsTheGenericShell() {
        AppProvider provider = new ShellAppProvider();
        assertSame(ShellApplication.class, provider.applicationClass());
        assertEquals(0, provider.priority());
    }

    @Test
    public void shellBuildsWithoutIdeModules() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            NbfxTabPane navigator = new NbfxTabPane(PaneRole.NAVIGATOR);
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);

            DockArea<TabPane> area = Docking.create(navigator, main);
            assertNotNull("dock area", area);
            assertNotNull("sliding area", Docking.slides());
            assertSame(navigator, Docking.primary(PaneRole.NAVIGATOR));
            assertSame(main, Docking.primary(PaneRole.MAIN));

            ViewProvider view = new ViewProvider() {
                @Override
                public String getTitle() {
                    return "Smoke";
                }

                @Override
                public Node getView() {
                    return new Label("smoke");
                }
            };
            Tab tab = ViewTabs.create(view, "smoke tooltip");
            assertEquals("Smoke", NbfxTabPane.titleOf(tab));
            assertNotNull("view id", NbfxTabPane.viewId(tab));

            NbfxTabPane.attachTab(main, tab);
            assertEquals(1, main.getTabs().size());
            assertSame(tab, main.getTabs().get(0));
            return null;
        });
    }
}
