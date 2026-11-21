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
import java.util.Optional;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.api.nbfx.shell.WindowManager;
import org.netbeans.api.nbfx.shell.WindowPaneHost;

/**
 * The {@link WindowManager} over the shell's pane registry: the stable API for the tab operations
 * that used to be static methods on {@link NbfxTabPane}.
 */
final class DefaultWindowManager implements WindowManager {

    @Override
    public List<WindowPaneHost> panes() {
        return NbfxTabPane.panesByRole().stream().map(DefaultWindowManager::host).toList();
    }

    @Override
    public Optional<WindowPaneHost> primary(PaneRole role) {
        return Optional.ofNullable(NbfxTabPane.paneWithRole(role)).map(DefaultWindowManager::host);
    }

    @Override
    public Optional<WindowPaneHost> paneOf(Tab tab) {
        return tab == null
                ? Optional.empty()
                : Optional.ofNullable(tab.getTabPane()).map(DefaultWindowManager::host);
    }

    @Override
    public Optional<Tab> findViewTab(String viewId) {
        return NbfxTabPane.findViewTab(viewId);
    }

    @Override
    public String viewId(Tab tab) {
        return NbfxTabPane.viewId(tab);
    }

    @Override
    public void select(Tab tab) {
        NbfxTabPane.selectTabAndMoveToFront(tab);
    }

    @Override
    public void attach(WindowPaneHost pane, Tab tab) {
        NbfxTabPane.attachTab(pane.pane(), tab);
    }

    @Override
    public void move(Tab tab, WindowPaneHost target, int index) {
        NbfxTabPane.moveTab(target.pane(), tab, index);
    }

    private static WindowPaneHost host(TabPane pane) {
        return pane instanceof WindowPaneHost host
                ? host
                : new SimpleHost(NbfxTabPane.roleOf(pane), pane);
    }

    private record SimpleHost(PaneRole role, TabPane pane) implements WindowPaneHost {
    }
}
