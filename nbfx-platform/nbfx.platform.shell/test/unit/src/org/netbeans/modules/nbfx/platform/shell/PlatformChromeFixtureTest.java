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
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.modules.nbfx.platform.shell.api.ShellChrome;
import org.netbeans.modules.nbfx.platform.status.StatusBar;
import org.openide.util.Lookup;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The platform-only conformance fixture: the default shell, without an IDE or an application
 * subclass, picks up the annotated action, menu, tool bar, dynamic section and status element.
 */
public class PlatformChromeFixtureTest {

    @Test
    public void defaultShellShowsThePlatformContributions() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            ShellApplication application = new ShellApplication();
            application.start(new Stage());
            ShellChrome chrome = application.getChrome();

            MenuBar menuBar = menuBar(chrome.top());
            assertNotNull("no menu bar", menuBar);
            Menu conformance = menu(menuBar, "Conformance");
            assertNotNull("the annotated menu is missing", conformance);
            assertTrue("the dynamic section is missing", contains(conformance,
                    PlatformChromeFixture.DYNAMIC_TEXT));
            assertTrue("the annotated action is missing",
                    Lookup.getDefault().lookup(ActionRegistry.class)
                            .find(PlatformChromeFixture.ACTION).isPresent());

            ToolBarHost host = (ToolBarHost) chrome;
            assertTrue("the annotated tool bar is missing",
                    host.toolBars().getToolBars().stream()
                            .anyMatch(bar -> "conformance".equals(bar.getId())));

            StatusBar statusBar = (StatusBar) chrome.bottom();
            assertTrue("the annotated status element is missing",
                    statusBar.elements().stream()
                            .anyMatch(element -> "conformance.status".equals(element.id())));
            return null;
        });
    }

    private static MenuBar menuBar(Node top) {
        if (top instanceof MenuBar menuBar) {
            return menuBar;
        }
        if (top instanceof VBox box) {
            for (Node child : box.getChildren()) {
                MenuBar found = menuBar(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Menu menu(MenuBar menuBar, String id) {
        for (Menu menu : menuBar.getMenus()) {
            if (id.equals(menu.getId())) {
                return menu;
            }
        }
        return null;
    }

    private static boolean contains(Menu menu, String text) {
        for (MenuItem item : menu.getItems()) {
            if (text.equals(item.getText())) {
                return true;
            }
        }
        return false;
    }
}
