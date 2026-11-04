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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.nbfx.platform.shell.DefaultShellChrome;
import org.netbeans.modules.nbfx.platform.shell.ShellApplication;
import org.netbeans.modules.nbfx.platform.shell.ShellChrome;

/**
 * The IDE's main window uses the generic shell chrome: the menu and tool bars come from the layer
 * and its contributions, not from a launcher-specific builder.
 */
public class AppChromeTest extends NbTestCase {

    public AppChromeTest(String name) {
        super(name);
    }

    public static Test suite() {
        return NbModuleSuite.createConfiguration(AppChromeTest.class)
                .clusters(".*")
                .enableModules(".*")
                .gui(false)
                .suite();
    }

    public void testMainWindowUsesTheGenericChrome() throws Exception {
        startFx();
        JavaFXLaunchApp application = new JavaFXLaunchApp();
        ScenarioSupport.onFx(() -> {
            application.start(new Stage());
            return null;
        });
        ShellChrome installed = chrome(application);
        assertTrue("the IDE did not use the generic chrome", installed instanceof DefaultShellChrome);
        DefaultShellChrome chrome = (DefaultShellChrome) installed;
        assertNotNull(chrome.top());

        MenuBar menuBar = menuBar(chrome.top());
        assertNotNull("the main window has no menu bar", menuBar);
        assertEquals(List.of("File", "Edit", "View", "Build", "Navigate", "Debug", "Tools", "Window"),
                menuIds(menuBar));
        assertTrue("the recent-projects section is missing",
                hasMenu(menu(menuBar, "File"), "Open Recent Project"));
        assertTrue("the Configure Window section is missing",
                hasMenu(menu(menuBar, "Window"), "Configure Window"));

        assertFalse("the main window has no tool bars", chrome.toolBars().getToolBars().isEmpty());
        assertEquals(List.of("file", "clipboard", "edit", "build", "debug"),
                chrome.toolBars().getToolBars().stream().map(toolBar -> toolBar.getId()).toList());
        assertNotNull("the main window has no status bar", chrome.bottom());
    }

    private static ShellChrome chrome(JavaFXLaunchApp application) throws Exception {
        Method getter = ShellApplication.class.getDeclaredMethod("getChrome");
        getter.setAccessible(true);
        return (ShellChrome) getter.invoke(application);
    }

    private static void startFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        assertTrue("the JavaFX toolkit did not start", latch.await(30, TimeUnit.SECONDS));
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

    private static List<String> menuIds(MenuBar menuBar) {
        List<String> ids = new ArrayList<>();
        for (Menu menu : menuBar.getMenus()) {
            if (!menu.getItems().isEmpty()) {
                ids.add(menu.getId());
            }
        }
        return ids;
    }

    private static Menu menu(MenuBar menuBar, String id) {
        for (Menu menu : menuBar.getMenus()) {
            if (id.equals(menu.getId())) {
                return menu;
            }
        }
        return null;
    }

    private static boolean hasMenu(Menu parent, String text) {
        if (parent == null) {
            return false;
        }
        for (MenuItem item : parent.getItems()) {
            if (item instanceof Menu menu && text.equals(menu.getText())) {
                return true;
            }
        }
        return false;
    }
}
