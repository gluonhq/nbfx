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

import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.KeyMap;
import org.netbeans.api.nbfx.icons.ThemedImageView;
import org.netbeans.api.nbfx.output.ConsolePanel;
import org.netbeans.api.nbfx.output.ConsoleRun;
import org.netbeans.api.nbfx.output.OutputConsole;
import org.netbeans.api.nbfx.output.OutputKind;
import org.netbeans.modules.nbfx.platform.theme.NbfxTheme;
import org.openide.util.RequestProcessor;
import org.openide.util.test.MockLookup;

/**
 * Verifies the Output view on the headless JavaFX toolkit: a tab per console, styled as the panes
 * of the main window, showing the console text; the tool bar following the selected console's
 * re-run and stop actions; closing a tab forgetting its console.
 */
public class OutputViewTest {

    @Test
    public void everyConsoleGetsATabShowingItsText() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            ConsoleRegistry output = new ConsoleRegistry();
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

    /** Each stretch of a line is drawn as the kind it was appended as, each kind in its theme colour. */
    @Test
    public void textIsColouredByKind() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            ConsoleRegistry output = new ConsoleRegistry();
            OutputView view = new OutputView(output, new OutputSettings());
            ConsolePanel build = output.console("Build");
            build.append("run:\n", OutputKind.MUTED);
            build.append("compiling ");
            build.append("failed\n", OutputKind.ERROR);
            build.append("\nBUILD SUCCESSFUL", OutputKind.SUCCESS);
            ConsoleTextModel model = ((ConsolePane) view.tabs().getTabs().get(0).getContent()).textModel();

            assertEquals(List.of(new ConsoleLines.Segment("run:", OutputKind.MUTED)), model.segments(0));
            assertEquals(List.of(new ConsoleLines.Segment("compiling ", OutputKind.NORMAL),
                    new ConsoleLines.Segment("failed", OutputKind.ERROR)), model.segments(1));
            assertEquals(List.of(new ConsoleLines.Segment("BUILD SUCCESSFUL", OutputKind.SUCCESS)), model.segments(3));
            // The paragraphs the area draws: a segment per stretch, and one for an empty line.
            assertEquals(1, model.getParagraph(0).getSegmentCount());
            assertEquals(2, model.getParagraph(1).getSegmentCount());
            assertEquals(1, model.getParagraph(2).getSegmentCount());
            assertEquals("failed", model.getParagraph(1).getSegment(1).getText());
            return null;
        });
    }

    /** Every kind has its own theme token; the theme module's test checks that each has a default. */
    @Test
    public void everyKindHasAThemeToken() {
        Set<String> seen = new HashSet<>();
        for (OutputKind kind : OutputKind.values()) {
            String token = ConsoleTextModel.token(kind);
            assertTrue(token, token.startsWith("-nbfx-output-"));
            assertTrue(token, seen.add(token));
        }
    }

    @Test
    public void toolBarFollowsTheSelectedConsole() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            ConsoleRegistry output = new ConsoleRegistry();
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
            ConsoleRun first = run.startRun(stops::incrementAndGet);
            assertTrue(view.rerunButton().isDisable());
            assertFalse(view.stopButton().isDisable());
            view.stopButton().fire();
            assertEquals(1, stops.get());

            // The next run starts before the first one reports its end: Stop stays.
            ConsoleRun second = run.startRun(stops::incrementAndGet);
            first.finish();
            assertFalse(view.stopButton().isDisable());
            assertTrue(view.rerunButton().isDisable());

            second.finish();
            assertTrue(view.stopButton().isDisable());
            view.rerunButton().fire();
            assertEquals(1, reruns.get());

            // A run that cannot be stopped is still a run.
            ConsoleRun unstoppable = run.startRun(null);
            assertTrue(view.stopButton().isDisable());
            assertTrue(view.rerunButton().isDisable());
            unstoppable.finish();

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
        JavaFxTestSupport.onFx(() -> {
            ConsoleRegistry output = new ConsoleRegistry();
            OutputView view = new OutputView(output, new OutputSettings());
            ConsoleModel run = output.console("Run");
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

    /** One console: no inner tab header, and the Output tab is titled after the console, as NetBeans. */
    @Test
    public void aSingleConsoleIsNamedByTheOutputTab() throws Exception {
        PseudoClass single = PseudoClass.getPseudoClass("single");
        JavaFxTestSupport.onFx(() -> {
            ConsoleRegistry output = new ConsoleRegistry();
            OutputView view = new OutputView(output, new OutputSettings());
            assertEquals("Output", view.titleProperty().get());
            assertFalse(view.tabs().getPseudoClassStates().contains(single));

            ConsoleModel run = output.console("Run (mavenproject5)");
            assertEquals("Output - Run (mavenproject5)", view.titleProperty().get());
            assertTrue(view.tabs().getPseudoClassStates().contains(single));

            ConsoleModel build = output.console("Build (mavenproject5)");
            assertEquals("Output", view.titleProperty().get());
            assertFalse(view.tabs().getPseudoClassStates().contains(single));

            output.close(run);
            assertEquals("Output - Build (mavenproject5)", view.titleProperty().get());
            assertTrue(view.tabs().getPseudoClassStates().contains(single));

            output.close(build);
            assertEquals("Output", view.titleProperty().get());
            return null;
        });
    }

    /** The tab's own close button, as the user presses it, forgets the console as closing it any other way does. */
    @Test
    public void theTabCloseButtonForgetsTheConsole() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            ConsoleRegistry output = new ConsoleRegistry();
            OutputView view = new OutputView(output, new OutputSettings());
            Stage stage = new Stage();
            stage.setScene(themed(new Scene(view, 400, 300)));
            stage.show();
            ConsoleModel run = output.console("Run");
            run.setRerun(() -> {
            });
            output.console("Build");
            try {
                view.applyCss();
                view.layout();
                Tab tab = view.tabs().getTabs().get(0);
                ConsolePane pane = (ConsolePane) tab.getContent();
                Node close = view.tabs().lookupAll(".tab").stream()
                        .filter(header -> header.lookup(".tab-label") instanceof Labeled label
                                && "Run".equals(label.getText()))
                        .map(header -> header.lookup(".tab-close-button"))
                        .findFirst().orElseThrow();
                for (EventType<MouseEvent> type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
                    Event.fireEvent(close, new MouseEvent(type, 2, 2, 2, 2, MouseButton.PRIMARY, 1,
                            false, false, false, false, type == MouseEvent.MOUSE_PRESSED, false, false,
                            false, false, false, null));
                }

                assertEquals(1, view.tabs().getTabs().size());
                assertEquals("Build", view.tabs().getTabs().get(0).getText());
                assertEquals(List.of(output.console("Build")), output.consoles());
                assertNull(run.rerunProperty().get());
                // The closed console is no longer shown: what its activity still writes goes nowhere.
                run.append("late");
                assertEquals("", pane.shownText());
            } finally {
                stage.hide();
            }
            return null;
        });
    }

    /**
     * The view shows the platform's consoles whatever the Lookup holds as the console service -
     * nothing yet, or somebody else's - and whenever it is created.
     */
    @Test
    public void theViewDoesNotDependOnTheServiceInTheLookup() throws Exception {
        String name = "OutputViewTest-" + System.nanoTime();
        try {
            MockLookup.setInstances();
            OutputView view = JavaFxTestSupport.onFx(() -> (OutputView) new OutputViewProvider().getView());
            MockLookup.setInstances((OutputConsole) (id, consoleName) -> {
                throw new AssertionError("the view must not ask a foreign service for consoles");
            });

            JavaFxTestSupport.onFx(() -> {
                ConsoleModel console = ConsoleRegistry.getDefault().console(name);
                console.append("platform console");
                Tab tab = view.tabs().getTabs().stream()
                        .filter(candidate -> name.equals(candidate.getText()))
                        .findFirst().orElseThrow();
                assertEquals("platform console", ((ConsolePane) tab.getContent()).shownText());
                ConsoleRegistry.getDefault().close(console);
                return null;
            });
        } finally {
            MockLookup.setInstances();
        }
    }

    /** A new theme on the scene rebuilds the paragraphs, whose colours the area resolved under the old one. */
    @Test
    public void aThemeSwitchRestylesTheText() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            ConsoleRegistry output = new ConsoleRegistry();
            OutputView view = new OutputView(output, new OutputSettings());
            Stage stage = new Stage();
            Scene scene = themed(new Scene(view, 400, 300));
            stage.setScene(scene);
            stage.show();
            output.console("Run").append("some output");
            ConsolePane pane = (ConsolePane) view.tabs().getTabs().get(0).getContent();
            AtomicInteger restyles = new AtomicInteger();
            pane.textArea().getModel().addListener(change -> {
                if (!change.isEdit()) {
                    restyles.incrementAndGet();
                }
            });
            try {
                scene.getProperties().put(NbfxTheme.THEME_URL_KEY, "file:/theme/dark.css");
                assertEquals(1, restyles.get());
            } finally {
                stage.hide();
            }
            return null;
        });
    }

    /** The tool bar icons have a drawing for dark themes next to the one for light themes. */
    @Test
    public void toolBarIconsAreThemed() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            OutputView view = new OutputView(new ConsoleRegistry(), new OutputSettings());

            assertEquals(4, view.toolBarButtons().size());
            for (Button button : view.toolBarButtons()) {
                assertTrue(button.getGraphic() instanceof ThemedImageView);
                ThemedImageView icon = (ThemedImageView) button.getGraphic();
                assertNotNull(icon.getLightImage());
                assertNotNull("no dark drawing for " + button.getTooltip().getText(), icon.getDarkImage());
                assertNotEquals(icon.getLightImage().getUrl(), icon.getDarkImage().getUrl());
            }
            return null;
        });
    }

    /** Save As writes the whole console text, off the JavaFX Application Thread. */
    @Test
    public void saveAsWritesTheConsoleText() throws Exception {
        Path file = Files.createTempFile("nbfx-output", ".txt");
        try {
            RequestProcessor.Task task = JavaFxTestSupport.onFx(() -> {
                ConsoleRegistry output = new ConsoleRegistry();
                OutputView view = new OutputView(output, new OutputSettings());
                output.console("Run").append("run:\nBUILD SUCCESSFUL\n");
                return ((ConsolePane) view.tabs().getTabs().get(0).getContent()).saveTo(file);
            });
            task.waitFinished();

            assertEquals("run:\nBUILD SUCCESSFUL\n", Files.readString(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** Focus in the console text marks the tab pane active, as focus on its tab header does. */
    @Test
    public void focusInTheTextMarksTheTabsActive() throws Exception {
        PseudoClass active = PseudoClass.getPseudoClass("active");
        JavaFxTestSupport.onFx(() -> {
            ConsoleRegistry output = new ConsoleRegistry();
            OutputView view = new OutputView(output, new OutputSettings());
            Button elsewhere = new Button();
            Stage stage = new Stage();
            stage.setScene(themed(new Scene(new VBox(elsewhere, view), 400, 300)));
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
        JavaFxTestSupport.onFx(() -> {
            ConsoleRegistry output = new ConsoleRegistry();
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
            JavaFxTestSupport.onFx(() -> {
                ConsoleRegistry output = new ConsoleRegistry();
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
        JavaFxTestSupport.onFx(() -> {
            ConsoleRegistry output = new ConsoleRegistry();
            OutputView view = new OutputView(output, new OutputSettings());
            Button elsewhere = new Button();
            Stage stage = new Stage();
            stage.setScene(themed(new Scene(new VBox(elsewhere, view), 400, 300)));
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
    /** Gives the scene the theme, as the application does: output.css only names theme tokens. */
    private static Scene themed(Scene scene) {
        NbfxTheme.apply(scene);
        return scene;
    }

    private static void press(ConsolePane pane, KeyCode code) {
        boolean meta = new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN)
                .match(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, true));
        Event.fireEvent(pane.textArea(),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, !meta, false, meta));
    }

    @Test
    public void fontSizeAndWrapAreSharedByTheConsoles() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            OutputSettings settings = new OutputSettings();
            settings.fontSizeProperty().set(OutputSettings.MAX_FONT_SIZE - 1);

            settings.largerFont();
            settings.largerFont();
            assertEquals(OutputSettings.MAX_FONT_SIZE, settings.fontSizeProperty().get(), 0);

            settings.fontSizeProperty().set(OutputSettings.MIN_FONT_SIZE);
            settings.smallerFont();
            assertEquals(OutputSettings.MIN_FONT_SIZE, settings.fontSizeProperty().get(), 0);
            assertEquals("the preferences follow nbfx.<capability>.<key>", "nbfx/output", OutputSettings.NODE);
            return null;
        });
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.Assert.assertSame(expected, actual);
    }
}
