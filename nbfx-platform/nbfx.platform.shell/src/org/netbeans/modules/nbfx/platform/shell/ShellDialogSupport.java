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

import java.util.Objects;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import org.netbeans.modules.nbfx.platform.theme.NbfxTheme;
import org.openide.util.NbBundle;

/**
 * Shared setup for the shell's dialogs: the owner window, the dialog title, the shell dialog
 * stylesheet and the current theme. {@link CloseConfirmation} and {@link ShutdownDialog} use it so
 * both dialogs follow the same light/dark theme and localization rules.
 */
final class ShellDialogSupport {

    private static final String STYLESHEET = Objects.requireNonNull(
            ShellDialogSupport.class.getResource("dialogs.css")).toExternalForm();

    private ShellDialogSupport() {
    }

    /** Applies the owner, title, stylesheet and theme to a dialog. */
    static void install(Dialog<?> dialog, String title) {
        Window owner = owner();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(title);
        DialogPane pane = dialog.getDialogPane();
        if (!pane.getStylesheets().contains(STYLESHEET)) {
            pane.getStylesheets().add(STYLESHEET);
        }
        NbfxTheme.apply(pane);
    }

    /** The first showing window, used as the dialog owner. */
    static Window owner() {
        return Window.getWindows().stream().filter(Window::isShowing)
                .findFirst().orElse(null);
    }

    /** A message from the shell bundle. */
    static String message(String key, Object... args) {
        return NbBundle.getMessage(ShellDialogSupport.class, key, args);
    }
}
