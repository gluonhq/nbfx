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

import javafx.beans.value.ObservableValue;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.modules.nbfx.platform.shell.DisposableChrome;
import org.netbeans.modules.nbfx.platform.shell.ShellMenuBar;
import org.netbeans.modules.nbfx.platform.shell.ShellToolBars;
import org.netbeans.modules.nbfx.platform.shell.ToolBarContainer;
import org.netbeans.modules.nbfx.platform.shell.ToolBarHost;
import org.netbeans.modules.nbfx.platform.shell.api.DetachedBarsContext;
import org.netbeans.modules.nbfx.platform.shell.api.ShellContext;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 * Builds the chrome of one detached editor window with the generic shell builders and a reduced
 * profile. The window's scoped commands are owned by the window environment's command sink.
 */
public final class DetachedBars {

    private DetachedBars() {
    }

    public static Region create(DetachedBarsContext context, ToolBarHost mainToolBars) {
        ObservableValue<EditorDocument> scope = context.activeDocument(EditorDocument.class);
        ActionRegistry registry = context.services().lookup(ActionRegistry.class);
        Lookup base = new ProxyLookup(Lookups.fixed(
                new IdeShellCommandResolver(registry, scope, null, context.scopedCommands()),
                new DetachedChromeProfile(),
                new IdeShellIconProvider()), context.services());
        ShellContext shellContext = new ShellContext(base);

        ShellToolBars toolBars = new ShellToolBars(shellContext);
        ShellContext menuContext = new ShellContext(new ProxyLookup(
                Lookups.fixed(toolBars.container()), base));
        ShellMenuBar menu = new ShellMenuBar(menuContext);
        return new DetachedChrome(menu, toolBars.container(), mainToolBars);
    }

    private static final class DetachedChrome extends VBox implements DisposableChrome {

        private final ShellMenuBar menu;
        private final ToolBarContainer toolBars;

        DetachedChrome(ShellMenuBar menu, ToolBarContainer toolBars, ToolBarHost mainToolBars) {
            super(menu.menuBar(), toolBars);
            this.menu = menu;
            this.toolBars = toolBars;
            mirrorVisibility(mainToolBars);
        }

        private void mirrorVisibility(ToolBarHost mainToolBars) {
            if (mainToolBars == null) {
                return;
            }
            for (ToolBar detached : toolBars.getToolBars()) {
                for (ToolBar main : mainToolBars.toolBars().getToolBars()) {
                    if (detached.getId() != null && detached.getId().equals(main.getId())) {
                        detached.visibleProperty().bind(main.visibleProperty());
                        detached.managedProperty().bind(detached.visibleProperty());
                        break;
                    }
                }
            }
        }

        @Override
        public void dispose() {
            for (ToolBar bar : toolBars.getToolBars()) {
                bar.visibleProperty().unbind();
                bar.managedProperty().unbind();
            }
            menu.dispose();
        }
    }
}
