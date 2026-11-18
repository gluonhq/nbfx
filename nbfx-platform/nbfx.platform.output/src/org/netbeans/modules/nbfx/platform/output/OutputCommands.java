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

import java.util.List;
import java.util.function.Consumer;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.netbeans.api.nbfx.actions.AbstractCommand;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.KeyMap;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The commands of the Output window that have a shortcut: Wrap Text, Larger / Smaller Font, Save
 * As, Clear and Close, under their stable {@link ActionIds}. They are registered commands, so
 * Tools ▸ Options ▸ Keymap lists them and the user can change their shortcuts, but they are in no
 * menu of the main window: they act on the <em>active</em> console - the one with the keyboard
 * focus, or whose context menu is being used - and are disabled while there is none. That is what
 * lets them share a key with a command of the main menu (Save As with Save), as in NetBeans.
 * <p>
 * {@link Command#run()} is the one way a command runs: a console's keys and its context menu make
 * the console the active one and run the registered command, as a menu or a tool bar bound to the
 * command would.
 */
final class OutputCommands {

    /** The active console, or {@code null}; declared first, as the commands bind to it. Used on the JavaFX Application Thread. */
    private static final ObjectProperty<ConsolePane> ACTIVE = new SimpleObjectProperty<>();

    static final ConsoleCommand WRAP_TEXT = new ConsoleCommand(ActionIds.OUTPUT_WRAP_TEXT, KeyCode.R, ConsolePane::toggleWrapText);
    static final ConsoleCommand LARGER_FONT = new ConsoleCommand(ActionIds.OUTPUT_LARGER_FONT, KeyCode.UP, ConsolePane::largerFont);
    static final ConsoleCommand SMALLER_FONT = new ConsoleCommand(ActionIds.OUTPUT_SMALLER_FONT, KeyCode.DOWN, ConsolePane::smallerFont);
    static final ConsoleCommand SAVE_AS = new ConsoleCommand(ActionIds.OUTPUT_SAVE_AS, KeyCode.S, ConsolePane::saveAs);
    static final ConsoleCommand CLEAR = new ConsoleCommand(ActionIds.OUTPUT_CLEAR, KeyCode.L, ConsolePane::clear);
    static final ConsoleCommand CLOSE = new ConsoleCommand(ActionIds.OUTPUT_CLOSE, KeyCode.F4, ConsolePane::close);

    private static final List<ConsoleCommand> ALL = List.of(WRAP_TEXT, LARGER_FONT, SMALLER_FONT, SAVE_AS, CLEAR, CLOSE);

    private OutputCommands() {
    }

    /** Every command, in the order of the console's context menu. */
    static List<ConsoleCommand> all() {
        return ALL;
    }

    /** The commands, as the {@link org.netbeans.api.nbfx.actions.CommandsProvider} registers them. */
    static List<Command> commands() {
        return List.copyOf(ALL);
    }

    /** Makes {@code pane} the console the commands act on: it took the keyboard focus, or its keys or menu are used. */
    static void activate(ConsolePane pane) {
        ACTIVE.set(pane);
    }

    /** {@code pane} lost the keyboard focus or went away: the commands act on no console, unless another took over. */
    static void deactivate(ConsolePane pane) {
        if (ACTIVE.get() == pane) {
            ACTIVE.set(null);
        }
    }

    /** A command acting on the active console. */
    static final class ConsoleCommand extends AbstractCommand {

        private final Consumer<ConsolePane> action;

        private ConsoleCommand(String id, KeyCode key, Consumer<ConsolePane> action) {
            super(id, NbBundle.getMessage(OutputCommands.class, "CMD_" + id),
                    new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN));
            this.action = action;
            bindDisabled(ACTIVE.isNull());
        }

        /** The shortcut in use: the one the user gave the command in the Keymap options, or its default; {@code null} for none. */
        KeyCombination shortcut() {
            KeyMap keyMap = Lookup.getDefault().lookup(KeyMap.class);
            return keyMap == null ? getAccelerator() : keyMap.accelerator(getId(), getAccelerator());
        }

        @Override
        public void run() {
            ConsolePane pane = ACTIVE.get();
            if (pane != null) {
                action.accept(pane);
            }
        }
    }
}
