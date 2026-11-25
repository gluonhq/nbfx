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

import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import javafx.stage.Stage;
import org.junit.After;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.shell.ShellFrame;
import org.netbeans.modules.nbfx.platform.status.StatusBar;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The platform-only conformance fixture: the default shell, without an IDE or an application
 * subclass, picks up the annotated action, menu, tool bar, dynamic section and status element.
 */
public class PlatformChromeFixtureTest {

    /** The started shell leaves its window open; see {@link JavaFxTestSupport#hideWindows()}. */
    @After
    public void hideWindows() throws InterruptedException {
        JavaFxTestSupport.hideWindows();
    }

    @Test
    public void defaultShellShowsThePlatformContributions() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            ShellApplication application = new ShellApplication();
            application.start(new Stage());
            ShellFrame frame = application.getShellFrame();
            assertNotNull("declarative frame missing", frame);

            MenuBar menuBar = menuBar(frame.root());
            assertNotNull("no menu bar", menuBar);
            Menu conformance = menu(menuBar, "Conformance");
            assertNotNull("the annotated menu is missing", conformance);
            assertTrue("the dynamic section is missing", contains(conformance,
                    PlatformChromeFixture.DYNAMIC_TEXT));
            assertTrue("the annotated action is missing",
                    Lookup.getDefault().lookup(ActionRegistry.class)
                            .find(PlatformChromeFixture.ACTION).isPresent());

            assertTrue("the annotated tool bar is missing",
                    toolBar(frame.root(), "conformance") != null);
            ToolBarContainer toolBars = findNode(frame.root(), ToolBarContainer.class);
            assertNotNull("toolbar lifecycle container missing", toolBars);
            assertTrue("View menu toolbar controls missing",
                    !new ViewMenuFactory().create(Lookups.singleton(toolBars)).items().isEmpty());

            StatusBar statusBar = findNode(frame.root(), StatusBar.class);
            assertTrue("the annotated status element is missing",
                    statusBar.elements().stream()
                            .anyMatch(element -> "conformance.status".equals(element.id())));
            return null;
        });
    }

    private static MenuBar menuBar(Node node) {
        if (node instanceof MenuBar menuBar) {
            return menuBar;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
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

    private static ToolBar toolBar(Node node, String id) {
        if (node instanceof ToolBar bar && id.equals(bar.getId())) {
            return bar;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ToolBar found = toolBar(child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T> T findNode(Node node, Class<T> type) {
        if (type.isInstance(node)) {
            return type.cast(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = findNode(child, type);
                if (found != null) {
                    return found;
                }
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
