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
package org.netbeans.modules.nbfx.platform.output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.KeyMap;
import org.openide.util.test.MockLookup;

/**
 * Verifies the Output view on the headless JavaFX toolkit: a tab per console, styled as the panes
 * of the main window, showing the console text; the tool bar following the selected console's
 * re-run and stop actions; closing a tab forgetting its console.
 */
public class OutputViewTest {

    @Test
    public void everyConsoleGetsATabShowingItsText() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleOutput output = new ConsoleOutput();
            output.console("Build").append("before the view\n");
            OutputView view = new OutputView(output, new OutputSettings());

            output.console("Run").append("run:\nBUILD SUCCESSFUL\n");
            output.console("Build").append("after");

            assertTrue(view.tabs().getStyleClass().contains("nbfx-tab-pane"));
            assertEquals(2, view.tabs().getTabs().size());
            Tab build = view.tabs().getTabs().get(0);
            Tab run = view.tabs().getTabs().get(1);
            assertEquals("Build", build.getText());
            assertEquals("before the view\nafter", ((ConsolePane) build.getContent()).shownText());
            assertEquals("Run", run.getText());
            assertEquals("run:\nBUILD SUCCESSFUL\n", ((ConsolePane) run.getContent()).shownText());
            assertSame(run, view.tabs().getSelectionModel().getSelectedItem());

            output.console("Build").show();
            assertSame(build, view.tabs().getSelectionModel().getSelectedItem());

            output.console("Run").clear();
            assertEquals("", ((ConsolePane) run.getContent()).shownText());
            return null;
        });
    }

    @Test
    public void toolBarFollowsTheSelectedConsole() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleOutput output = new ConsoleOutput();
            OutputView view = new OutputView(output, new OutputSettings());
            assertTrue(view.rerunButton().isDisable());
            assertTrue(view.stopButton().isDisable());

            AtomicInteger reruns = new AtomicInteger();
            AtomicInteger stops = new AtomicInteger();
            ConsolePanel run = output.console("Run");
            run.setRerun(reruns::incrementAndGet);
            assertFalse(view.rerunButton().isDisable());
            assertTrue(view.stopButton().isDisable());

            // Running: it can be stopped, not started again.
            run.setStop(stops::incrementAndGet);
            assertTrue(view.rerunButton().isDisable());
            assertFalse(view.stopButton().isDisable());
            view.stopButton().fire();
            assertEquals(1, stops.get());

            run.setStop(null);
            view.rerunButton().fire();
            assertEquals(1, reruns.get());

            // Another console is selected: it has nothing to run again.
            output.console("Debug");
            assertTrue(view.rerunButton().isDisable());
            view.tabs().getSelectionModel().select(0);
            assertFalse(view.rerunButton().isDisable());
            return null;
        });
    }

    @Test
    public void closingATabForgetsItsConsole() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleOutput output = new ConsoleOutput();
            OutputView view = new OutputView(output, new OutputSettings());
            ConsoleModel run = (ConsoleModel) output.console("Run");
            run.append("first");
            assertTrue(view.tabs().isVisible());

            output.close(run);

            assertTrue(view.tabs().getTabs().isEmpty());
            assertFalse(view.tabs().isVisible());
            assertTrue(view.rerunButton().isDisable());

            output.console("Run").append("second");
            assertEquals(1, view.tabs().getTabs().size());
            assertEquals("second", ((ConsolePane) view.tabs().getTabs().get(0).getContent()).shownText());
            return null;
        });
    }

    /** Focus in the console text marks the tab pane active, as focus on its tab header does. */
    @Test
    public void focusInTheTextMarksTheTabsActive() throws Exception {
        PseudoClass active = PseudoClass.getPseudoClass("active");
        FxTestSupport.onFx(() -> {
            ConsoleOutput output = new ConsoleOutput();
            OutputView view = new OutputView(output, new OutputSettings());
            Button elsewhere = new Button();
            Stage stage = new Stage();
            stage.setScene(new Scene(new VBox(elsewhere, view), 400, 300));
            stage.show();
            output.console("Run").append("some output");
            ConsolePane pane = (ConsolePane) view.tabs().getTabs().get(0).getContent();
            try {
                elsewhere.requestFocus();
                assertFalse(view.tabs().getPseudoClassStates().contains(active));

                pane.textArea().requestFocus();
                assertTrue(pane.textArea().isFocused());
                assertTrue(view.tabs().getPseudoClassStates().contains(active));

                elsewhere.requestFocus();
                assertFalse(view.tabs().getPseudoClassStates().contains(active));
            } finally {
                stage.hide();
            }
            return null;
        });
    }

    /** The keys of NetBeans' Output window, with the platform's shortcut modifier. */
    @Test
    public void keysActOnTheConsole() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleOutput output = new ConsoleOutput();
            OutputSettings settings = new OutputSettings();
            OutputView view = new OutputView(output, settings);
            ConsolePanel run = output.console("Run");
            run.append("some output");
            ConsolePane pane = (ConsolePane) view.tabs().getTabs().get(0).getContent();

            press(pane, KeyCode.R);
            assertTrue(settings.wrapTextProperty().get());
            assertTrue(pane.textArea().isWrapText());

            press(pane, KeyCode.UP);
            assertEquals(OutputSettings.DEFAULT_FONT_SIZE + 1, settings.fontSizeProperty().get(), 0);
            press(pane, KeyCode.DOWN);
            assertEquals(OutputSettings.DEFAULT_FONT_SIZE, settings.fontSizeProperty().get(), 0);

            press(pane, KeyCode.L);
            assertEquals("", run.text());
            assertEquals("", pane.shownText());

            press(pane, KeyCode.F4);
            assertTrue(view.tabs().getTabs().isEmpty());
            return null;
        });
    }

    /** The shortcuts are the keymap's: a shortcut the user changed replaces the default one. */
    @Test
    public void keysFollowTheKeymap() throws Exception {
        Map<String, KeyCombination> overrides = new HashMap<>();
        MockLookup.setInstances(new KeyMap() {
            @Override
            public KeyCombination accelerator(String actionId, KeyCombination defaultAccelerator) {
                return overrides.containsKey(actionId) ? overrides.get(actionId) : defaultAccelerator;
            }

            @Override
            public void setAccelerator(String actionId, KeyCombination accelerator) {
                overrides.put(actionId, accelerator);
            }

            @Override
            public void reset(String actionId) {
                overrides.remove(actionId);
            }

            @Override
            public Map<String, KeyCombination> overrides() {
                return overrides;
            }
        });
        try {
            FxTestSupport.onFx(() -> {
                ConsoleOutput output = new ConsoleOutput();
                OutputSettings settings = new OutputSettings();
                OutputView view = new OutputView(output, settings);
                output.console("Run").append("some output");
                ConsolePane pane = (ConsolePane) view.tabs().getTabs().get(0).getContent();

                overrides.put(OutputCommands.WRAP_TEXT.getId(), new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
                press(pane, KeyCode.R);
                assertFalse(settings.wrapTextProperty().get());
                press(pane, KeyCode.W);
                assertTrue(settings.wrapTextProperty().get());

                // The user removed the shortcut.
                overrides.put(OutputCommands.CLEAR.getId(), null);
                press(pane, KeyCode.L);
                assertEquals("some output", pane.shownText());
                return null;
            });
        } finally {
            MockLookup.setInstances();
        }
    }

    /** The commands the Keymap options list act on the console that has the focus, and only then. */
    @Test
    public void commandsActOnTheFocusedConsole() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleOutput output = new ConsoleOutput();
            OutputView view = new OutputView(output, new OutputSettings());
            Button elsewhere = new Button();
            Stage stage = new Stage();
            stage.setScene(new Scene(new VBox(elsewhere, view), 400, 300));
            stage.show();
            ConsolePanel run = output.console("Run");
            run.append("some output");
            ConsolePane pane = (ConsolePane) view.tabs().getTabs().get(0).getContent();
            try {
                assertTrue(new OutputCommandsProvider().createCommands().containsAll(OutputCommands.all()));

                elsewhere.requestFocus();
                assertTrue(OutputCommands.CLEAR.isDisabled());
                OutputCommands.CLEAR.run();
                assertEquals("some output", run.text());

                pane.textArea().requestFocus();
                assertFalse(OutputCommands.CLEAR.isDisabled());
                OutputCommands.CLEAR.run();
                assertEquals("", run.text());

                OutputCommands.CLOSE.run();
                assertTrue(view.tabs().getTabs().isEmpty());
                assertTrue(OutputCommands.CLOSE.isDisabled());
            } finally {
                stage.hide();
            }
            return null;
        });
    }

    /** Presses {@code code} with the shortcut modifier, whichever key the platform uses for it. */
    private static void press(ConsolePane pane, KeyCode code) {
        boolean meta = new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN)
                .match(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, true));
        Event.fireEvent(pane.textArea(),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, !meta, false, meta));
    }

    @Test
    public void fontSizeAndWrapAreSharedByTheConsoles() throws Exception {
        FxTestSupport.onFx(() -> {
            OutputSettings settings = new OutputSettings();
            settings.fontSizeProperty().set(OutputSettings.MAX_FONT_SIZE - 1);

            settings.largerFont();
            settings.largerFont();
            assertEquals(OutputSettings.MAX_FONT_SIZE, settings.fontSizeProperty().get(), 0);

            settings.fontSizeProperty().set(OutputSettings.MIN_FONT_SIZE);
            settings.smallerFont();
            assertEquals(OutputSettings.MIN_FONT_SIZE, settings.fontSizeProperty().get(), 0);
            return null;
        });
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.Assert.assertSame(expected, actual);
    }
}
