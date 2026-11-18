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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.collections.MapChangeListener;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.TextPos;
import org.netbeans.modules.nbfx.platform.output.OutputCommands.ConsoleCommand;
import org.netbeans.modules.nbfx.platform.theme.NbfxTheme;
import org.openide.util.NbBundle;

/**
 * The content of a console tab: the console text in a read-only {@link RichTextArea} over a
 * {@link ConsoleTextModel}, with the context menu and the keys of NetBeans' Output window. New
 * text scrolls into view as long as the caret sits at the end, so moving the caret up to read
 * stops the view from following the output.
 */
final class ConsolePane extends StackPane {

    private static final Logger LOG = Logger.getLogger(ConsolePane.class.getName());

    private static final KeyCombination COPY = shortcut(KeyCode.C);
    private static final KeyCombination PASTE = shortcut(KeyCode.V);
    private static final KeyCombination FIND = shortcut(KeyCode.F);
    private static final KeyCombination FIND_NEXT = shortcut(KeyCode.G);
    private static final KeyCombination FIND_PREVIOUS =
            new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination FILTER = new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN);

    private final ConsoleModel console;
    private final OutputSettings settings;
    private final Runnable close;
    private final ConsoleTextModel text = new ConsoleTextModel();
    private final RichTextArea area = new RichTextArea(text);
    /** Kept in a field so that {@link #dispose} can remove it from the shared settings. */
    private final ChangeListener<Number> fontSizeListener = (obs, was, now) -> text.setFontSize(now.doubleValue());

    /** Recolours the text when the scene's theme changes, as the code editor does. */
    private final MapChangeListener<Object, Object> themeListener = change -> {
        if (NbfxTheme.THEME_URL_KEY.equals(change.getKey()) && change.wasAdded()) {
            text.refreshStyles();
        }
    };

    /**
     * @param console  the console to show
     * @param settings the look the consoles share
     * @param select   brings this pane's tab to the front
     * @param close    closes this pane's tab
     */
    ConsolePane(ConsoleModel console, OutputSettings settings, Runnable select, Runnable close) {
        this.console = console;
        this.settings = settings;
        this.close = close;
        area.getStyleClass().add("output-console");
        area.setEditable(false);
        area.wrapTextProperty().bind(settings.wrapTextProperty());
        text.setFontSize(settings.fontSizeProperty().get());
        settings.fontSizeProperty().addListener(fontSizeListener);
        getChildren().add(area);
        sceneProperty().addListener((obs, oldScene, scene) -> {
            if (oldScene != null) {
                oldScene.getProperties().removeListener(themeListener);
            }
            if (scene != null) {
                scene.getProperties().addListener(themeListener);
            }
        });
        installContextMenu();
        area.addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
        focusWithinProperty().subscribe(within -> OutputCommands.focusChanged(this, within));
        console.attach(this::append, text::clear, select);
    }

    /** Stops following the console and the shared settings; called when the tab goes away. */
    void dispose() {
        OutputCommands.focusChanged(this, false);
        console.detach();
        area.wrapTextProperty().unbind();
        settings.fontSizeProperty().removeListener(fontSizeListener);
    }

    /** Moves the keyboard focus to the text. */
    void requestTextFocus() {
        area.requestFocus();
    }

    /** The text area, for tests. */
    RichTextArea textArea() {
        return area;
    }

    /** The text as the view holds it, for tests. */
    String shownText() {
        return text.text();
    }

    private void append(String delta) {
        TextPos caret = area.getCaretPosition();
        boolean follow = caret == null || caret.isSameInsertionIndex(text.end());
        text.append(delta);
        if (follow) {
            area.select(text.end());
        }
    }

    // --- actions ---

    private void rerun() {
        Runnable rerun = console.rerunProperty().get();
        if (rerun != null && console.stopProperty().get() == null) {
            rerun.run();
        }
    }

    private void stop() {
        Runnable stop = console.stopProperty().get();
        if (stop != null) {
            stop.run();
        }
    }

    void toggleWrapText() {
        settings.wrapTextProperty().set(!settings.wrapTextProperty().get());
    }

    void largerFont() {
        settings.largerFont();
    }

    void smallerFont() {
        settings.smallerFont();
    }

    /** Clears the console: its text is gone for good. */
    void clear() {
        console.clear();
    }

    /** Closes this pane's tab. */
    void close() {
        close.run();
    }

    /** Writes the console text, as UTF-8, to a file the user picks. */
    void saveAs() {
        if (getScene() == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(message("TITLE_SaveAs"));
        chooser.setInitialFileName(console.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt");
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                Files.writeString(file.toPath(), text.text(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Cannot save the output to " + file, ex);
            }
        }
    }

    /** Runs the {@linkplain OutputCommands command} whose shortcut, as the keymap has it, was pressed. */
    private void keyPressed(KeyEvent event) {
        for (ConsoleCommand command : OutputCommands.all()) {
            KeyCombination shortcut = command.shortcut();
            if (shortcut != null && shortcut.match(event)) {
                command.runOn(this);
                event.consume();
                return;
            }
        }
    }

    // --- context menu ---

    /**
     * NetBeans' Output window menu. It is shown from an event filter, as the rich text area's
     * behaviour consumes the context-menu request itself, and a press in the text hides it, as a
     * popup anchored on a node does not auto-hide on clicks inside that node. The accelerators
     * are what the items display - for the {@link OutputCommands}, the shortcuts of the keymap -;
     * {@link #keyPressed} is what handles the keys.
     */
    private void installContextMenu() {
        MenuItem rerun = item("CTX_Rerun", null, this::rerun);
        // TODO needs the "run with parameters" dialog of the build tool behind the console
        MenuItem rerunWith = todo("CTX_RerunWith", null);
        MenuItem stop = item("CTX_Stop", null, this::stop);
        // TODO needs the options dialog page of the build tool behind the console
        MenuItem toolSettings = todo("CTX_ToolSettings", null);

        MenuItem copy = item("CTX_Copy", COPY, area::copy);
        // TODO needs console input (the process's stdin)
        MenuItem paste = todo("CTX_Paste", PASTE);

        // TODO Find and Filter need the output find bar / filter dialog
        MenuItem find = todo("CTX_Find", FIND);
        MenuItem findNext = todo("CTX_FindNext", FIND_NEXT);
        MenuItem findPrevious = todo("CTX_FindPrevious", FIND_PREVIOUS);
        MenuItem filter = todo("CTX_Filter", FILTER);

        CheckMenuItem wrap = new CheckMenuItem(message("CTX_WrapText"));
        wrap.selectedProperty().bindBidirectional(settings.wrapTextProperty());
        MenuItem larger = item("CTX_LargerFont", null, this::largerFont);
        MenuItem smaller = item("CTX_SmallerFont", null, this::smallerFont);
        // TODO needs the Output page of the Options window
        MenuItem outputSettings = todo("CTX_Settings", null);

        MenuItem saveAs = item("CTX_SaveAs", null, this::saveAs);
        MenuItem clear = item("CTX_Clear", null, this::clear);
        MenuItem closeItem = item("CTX_Close", null, this::close);

        // TODO folds: the text model hides the folded lines, a left side decorator draws the handles
        Menu folds = new Menu(message("CTX_OutputFolds"));
        folds.setDisable(true);

        ContextMenu menu = new ContextMenu(
                rerun, rerunWith, stop, toolSettings,
                new SeparatorMenuItem(), copy, paste,
                new SeparatorMenuItem(), find, findNext, findPrevious, filter,
                new SeparatorMenuItem(), wrap, larger, smaller, outputSettings,
                new SeparatorMenuItem(), saveAs, clear, closeItem,
                new SeparatorMenuItem(), folds);
        menu.setOnShowing(e -> {
            // The shortcuts as the keymap has them now.
            wrap.setAccelerator(OutputCommands.WRAP_TEXT.shortcut());
            larger.setAccelerator(OutputCommands.LARGER_FONT.shortcut());
            smaller.setAccelerator(OutputCommands.SMALLER_FONT.shortcut());
            saveAs.setAccelerator(OutputCommands.SAVE_AS.shortcut());
            clear.setAccelerator(OutputCommands.CLEAR.shortcut());
            closeItem.setAccelerator(OutputCommands.CLOSE.shortcut());
            boolean running = console.stopProperty().get() != null;
            rerun.setDisable(running || console.rerunProperty().get() == null);
            stop.setDisable(!running);
            copy.setDisable(!area.hasNonEmptySelection());
            larger.setDisable(settings.fontSizeProperty().get() >= OutputSettings.MAX_FONT_SIZE);
            smaller.setDisable(settings.fontSizeProperty().get() <= OutputSettings.MIN_FONT_SIZE);
        });
        area.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
            if (!area.isFocused()) {
                area.requestFocus();
            }
            menu.hide();
            menu.show(area, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        area.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> menu.hide());
    }

    private static MenuItem item(String key, KeyCombination accelerator, Runnable action) {
        MenuItem item = new MenuItem(message(key));
        item.setAccelerator(accelerator);
        item.setOnAction(e -> action.run());
        return item;
    }

    /** An item NetBeans has and nbfx does not yet: listed in its place, disabled. */
    private static MenuItem todo(String key, KeyCombination accelerator) {
        MenuItem item = new MenuItem(message(key));
        item.setAccelerator(accelerator);
        item.setDisable(true);
        return item;
    }

    private static KeyCombination shortcut(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN);
    }

    private static String message(String key) {
        return NbBundle.getMessage(ConsolePane.class, key);
    }
}
