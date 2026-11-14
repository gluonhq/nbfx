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

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.openide.util.NbBundle;

/**
 * The JavaFX replacement for the Swing {@code BrokenReferencesAlertPanel}: an alert that lists the
 * errors found in the opened projects, offers to open the resolver and remembers the user's
 * "show this warning again" choice.
 * <p>
 * The dialog is modal; it is built and shown on the JavaFX thread.
 */
final class ProjectProblemsDialog extends Dialog<ButtonType> {

    private final ButtonType resolve;
    private final ListView<ProjectProblemsModel.ProblemItem> problems;

    ProjectProblemsDialog(Window owner, ProjectProblemsModel model) {
        ProjectProblemsUi.install(this, owner, message("MSG_ProjectProblems_Title"));

        Label intro = new Label(message("MSG_ProjectProblems_Intro"));
        intro.setWrapText(true);
        intro.getStyleClass().add("project-problems-intro");

        problems = new ListView<>(model.getProblems());
        problems.setCellFactory(view -> ProjectProblemsUi.problemCell(model::hasMultipleProjects));
        problems.setPrefHeight(180);
        problems.setAccessibleText(message("ACSN_ProjectProblems_List"));

        CheckBox showAgain = new CheckBox(message("CTL_ShowAgain"));
        showAgain.setSelected(ProjectProblemsSettings.isShowAgain());
        showAgain.setTooltip(new Tooltip(message("TIP_ShowAgain")));
        showAgain.setAccessibleHelp(message("TIP_ShowAgain"));
        showAgain.selectedProperty().addListener(
                (observable, oldValue, value) -> ProjectProblemsSettings.setShowAgain(value));

        VBox content = new VBox(12, intro, problems, showAgain);
        content.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(problems, Priority.ALWAYS);

        DialogPane pane = getDialogPane();
        pane.setHeaderText(message("MSG_ProjectProblems_Header"));
        pane.setContent(content);
        pane.setPrefWidth(560);

        resolve = new ButtonType(message("CTL_ResolveProblems"), ButtonData.OK_DONE);
        ButtonType close = new ButtonType(message("CTL_Close"), ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(resolve, close);

        Button resolveButton = (Button) pane.lookupButton(resolve);
        resolveButton.setDefaultButton(true);
        resolveButton.setAccessibleText(message("CTL_ResolveProblems"));
        Button closeButton = (Button) pane.lookupButton(close);
        closeButton.setAccessibleText(message("CTL_Close"));
        setOnShown(event -> problems.requestFocus());
    }

    /** The button type the user chose to open the resolver. */
    ButtonType resolveButtonType() {
        return resolve;
    }

    /** The problem list, for tests. */
    ListView<ProjectProblemsModel.ProblemItem> problems() {
        return problems;
    }

    private static String message(String key, Object... arguments) {
        return NbBundle.getMessage(ProjectProblemsDialog.class, key, arguments);
    }
}
