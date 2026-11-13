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
package org.netbeans.modules.nbfx.project.ui.problems;

import java.util.Objects;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.netbeans.modules.nbfx.platform.theme.NbfxTheme;

/**
 * Shared setup for the project problems dialogs: owner window, modality, stylesheet, theme and
 * problem cell rendering. Kept separate so the alert and the resolver look and behave alike.
 */
final class ProjectProblemsUi {

    private static final String STYLESHEET = Objects.requireNonNull(
            ProjectProblemsUi.class.getResource("problems.css")).toExternalForm();

    private ProjectProblemsUi() {
    }

    /** Configures the owner, modality, title, stylesheet and theme of a dialog. */
    static void install(Dialog<?> dialog, Window owner, String title) {
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        } else {
            dialog.initModality(Modality.APPLICATION_MODAL);
        }
        dialog.setTitle(title);
        dialog.setResizable(true);
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("project-problems-dialog");
        if (!pane.getStylesheets().contains(STYLESHEET)) {
            pane.getStylesheets().add(STYLESHEET);
        }
        if (owner != null && owner.getScene() != null) {
            for (String stylesheet : owner.getScene().getStylesheets()) {
                if (!pane.getStylesheets().contains(stylesheet)) {
                    pane.getStylesheets().add(stylesheet);
                }
            }
        }
        NbfxTheme.apply(pane);
    }

    /** The focused showing stage, or the first other showing window, as the dialog owner. */
    static Window owner() {
        Window focused = Window.getWindows().stream()
                .filter(window -> window instanceof Stage stage && stage.isShowing()
                        && stage.isFocused())
                .findFirst().orElse(null);
        if (focused != null) {
            return focused;
        }
        return Window.getWindows().stream()
                .filter(window -> window instanceof Stage stage && stage.isShowing())
                .findFirst().orElse(null);
    }

    /** A list cell showing a problem display name and its description as a tooltip. */
    static ListCell<ProjectProblemsModel.ProblemItem> problemCell(boolean includeProject) {
        return new ListCell<>() {
            @Override
            protected void updateItem(ProjectProblemsModel.ProblemItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setAccessibleText(null);
                    setAccessibleHelp(null);
                    return;
                }
                String name = item.displayName(includeProject);
                setText(name);
                setAccessibleText(name);
                setAccessibleHelp(item.description());
                Tooltip tooltip = getTooltip();
                if (tooltip == null) {
                    tooltip = new Tooltip();
                    setTooltip(tooltip);
                }
                tooltip.setText(item.description());
            }
        };
    }
}
