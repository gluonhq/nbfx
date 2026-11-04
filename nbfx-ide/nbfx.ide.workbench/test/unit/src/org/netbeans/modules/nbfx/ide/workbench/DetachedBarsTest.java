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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.VBox;
import junit.framework.Test;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.nbfx.platform.shell.ToolBarContainer;
import org.netbeans.modules.nbfx.platform.shell.api.DetachedBarsContext;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Detached windows use the generic builders with a reduced profile, not the old ActionBars path.
 */
public class DetachedBarsTest extends NbTestCase {

    public DetachedBarsTest(String name) {
        super(name);
    }

    public static Test suite() {
        return NbModuleSuite.createConfiguration(DetachedBarsTest.class)
                .clusters(".*")
                .enableModules(".*")
                .gui(false)
                .suite();
    }

    public void testDetachedProfileAndToolBars() throws Exception {
        startFx();
        ScenarioSupport.onFx(() -> {
            RecordingRegistry registry = new RecordingRegistry();
            List<Command> scoped = new ArrayList<>();
            DetachedBarsContext context = new DetachedBarsContext(
                    Lookups.fixed(registry), new SimpleObjectProperty<EditorDocument>(), scoped);
            Node chrome = DetachedBars.create(context, null);
            VBox box = (VBox) chrome;
            MenuBar menuBar = (MenuBar) box.getChildren().get(0);
            ToolBarContainer toolBars = (ToolBarContainer) box.getChildren().get(1);

            assertEquals(List.of("File", "Edit", "View", "Window"), menuIds(menuBar));
            Menu file = menu(menuBar, "File");
            assertNotNull("File menu missing", file);
            assertEquals(List.of(ActionIds.SAVE, ActionIds.SAVE_ALL), itemTexts(file));
            assertFalse("recent projects must not be in a detached File menu",
                    hasText(file, "Open Recent Project"));
            assertFalse("project actions must not be in a detached File menu",
                    hasText(file, "Open Project"));

            assertEquals(List.of("file", "clipboard", "edit"),
                    toolBars.getToolBars().stream().map(ToolBar::getId).toList());
            for (ToolBar bar : toolBars.getToolBars()) {
                if (!bar.getItems().isEmpty()) {
                    assertFalse("detached tool bars must not have drag handles",
                            bar.getItems().get(0).getStyleClass().contains("toolbar-drag-handle"));
                }
            }
            return null;
        });
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

    private static List<String> menuIds(MenuBar bar) {
        return bar.getMenus().stream().map(Menu::getId).toList();
    }

    private static Menu menu(MenuBar bar, String id) {
        return bar.getMenus().stream().filter(menu -> id.equals(menu.getId())).findFirst().orElse(null);
    }

    private static List<String> itemTexts(Menu menu) {
        return menu.getItems().stream().map(MenuItem::getText).toList();
    }

    private static boolean hasText(Menu menu, String text) {
        return menu.getItems().stream().anyMatch(item -> text.equals(item.getText()));
    }

    private static final class RecordingRegistry implements ActionRegistry {

        private final Map<String, Command> commands = new LinkedHashMap<>();

        @Override
        public void register(Command command) {
            commands.put(command.getId(), command);
        }

        @Override
        public Optional<Command> find(String id) {
            return Optional.of(commands.computeIfAbsent(id,
                    key -> new RunnableCommand(key, key, null, () -> {
                    })));
        }

        @Override
        public Collection<Command> getCommands() {
            return commands.values();
        }

        @Override
        public Optional<Command> createScoped(String id, ObservableValue<EditorDocument> activeDocument) {
            return Optional.empty();
        }
    }
}
