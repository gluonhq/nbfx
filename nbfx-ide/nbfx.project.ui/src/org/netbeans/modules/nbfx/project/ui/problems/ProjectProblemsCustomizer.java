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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.netbeans.api.nbfx.dialogs.ErrorReporter;
import org.netbeans.api.nbfx.status.StatusMessageService;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.ProjectProblem;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.Result;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.Status;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * The JavaFX replacement for the Swing {@code BrokenReferencesCustomizer}: the list of problems of
 * the selected project(s), the description of the selected problem, and a resolver that runs the
 * provider's {@link org.netbeans.spi.project.ui.ProjectProblemResolver} off the JavaFX thread.
 * <p>
 * The dialog stays open while unresolved problems remain and closes by itself once the last one is
 * gone. It is built and shown on the JavaFX thread.
 */
final class ProjectProblemsCustomizer extends Dialog<Void> {

    private static final Logger LOG = Logger.getLogger(ProjectProblemsCustomizer.class.getName());
    private static final RequestProcessor RP =
            new RequestProcessor(ProjectProblemsCustomizer.class.getName(), 1);
    private static final String STATUS_ERROR = "problem-status-error";
    private static final String STATUS_WARNING = "problem-status-warning";
    private static final String SEVERITY_ERROR = "problem-severity-error";
    private static final String SEVERITY_WARNING = "problem-severity-warning";

    private final ProjectProblemsModel model;
    private final ListView<ProjectProblemsModel.ProblemItem> problems;
    private final Label detailTitle;
    private final Label severity;
    private final TextArea description;
    private final Label statusLine;
    private final BooleanProperty resolving = new SimpleBooleanProperty(this, "resolving", false);

    ProjectProblemsCustomizer(Window owner, ProjectProblemsModel model) {
        this.model = model;
        ProjectProblemsUi.install(this, owner, title(model));

        problems = new ListView<>(model.getProblems());
        problems.setCellFactory(view -> ProjectProblemsUi.problemCell(model.hasMultipleProjects()));
        problems.setAccessibleText(message("LBL_ProjectProblems"));
        problems.setPrefWidth(250);

        Label problemsLabel = new Label(message("LBL_ProjectProblems"));
        problemsLabel.setLabelFor(problems);
        VBox problemList = new VBox(4, problemsLabel, problems);
        VBox.setVgrow(problems, Priority.ALWAYS);

        detailTitle = new Label();
        detailTitle.setWrapText(true);
        detailTitle.getStyleClass().add("problem-title");

        severity = new Label();
        severity.getStyleClass().add("problem-severity");

        Label descriptionLabel = new Label(message("LBL_Description"));
        description = new TextArea();
        description.setEditable(false);
        description.setWrapText(true);
        description.setPrefRowCount(8);
        descriptionLabel.setLabelFor(description);

        VBox detail = new VBox(6, detailTitle, severity, descriptionLabel, description);
        detail.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(description, Priority.ALWAYS);

        SplitPane split = new SplitPane(problemList, detail);
        split.setDividerPositions(0.38);
        split.setPrefSize(700, 360);

        statusLine = new Label();
        statusLine.setWrapText(true);
        statusLine.getStyleClass().add("problem-status");
        statusLine.setVisible(false);
        statusLine.setManaged(false);

        VBox content = new VBox(8, split, statusLine);
        VBox.setVgrow(split, Priority.ALWAYS);

        DialogPane pane = getDialogPane();
        pane.setContent(content);
        pane.setPrefWidth(720);
        pane.setPrefHeight(460);

        ButtonType resolve = new ButtonType(message("CTL_Resolve"), ButtonData.OTHER);
        ButtonType close = new ButtonType(message("CTL_Close"), ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(resolve, close);

        Button resolveButton = (Button) pane.lookupButton(resolve);
        resolveButton.setAccessibleText(message("CTL_Resolve"));
        resolveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            resolveSelected();
        });
        resolveButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> resolving.get() || !canResolve(problems.getSelectionModel().getSelectedItem()),
                problems.getSelectionModel().selectedItemProperty(), resolving));
        problems.disableProperty().bind(resolving);

        Button closeButton = (Button) pane.lookupButton(close);
        closeButton.setAccessibleText(message("CTL_Close"));

        problems.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, value) -> updateDetails());
        model.getProblems().addListener((ListChangeListener<ProjectProblemsModel.ProblemItem>) change -> {
            if (model.getProblems().isEmpty()) {
                if (isShowing()) {
                    showStatus(message("MSG_AllProblemsResolved"), null);
                    publishStatus(message("MSG_AllProblemsResolved"));
                    Platform.runLater(() -> {
                        if (model.getProblems().isEmpty()) {
                            close();
                        }
                    });
                }
            } else if (problems.getSelectionModel().getSelectedItem() == null) {
                problems.getSelectionModel().selectFirst();
            } else {
                updateDetails();
            }
        });

        problems.getSelectionModel().selectFirst();
        updateDetails();
        setOnShown(event -> problems.requestFocus());
    }

    /** The problem list, for tests. */
    ListView<ProjectProblemsModel.ProblemItem> problems() {
        return problems;
    }

    private static boolean canResolve(ProjectProblemsModel.ProblemItem item) {
        return item != null && item.isResolvable();
    }

    private void resolveSelected() {
        ProjectProblemsModel.ProblemItem item = problems.getSelectionModel().getSelectedItem();
        if (!canResolve(item) || resolving.get()) {
            return;
        }
        String name = item.displayName(model.hasMultipleProjects());
        resolving.set(true);
        showStatus(message("MSG_Resolving", name), null);

        ProjectProblem problem = item.problem();
        RP.post(() -> {
            Result result = null;
            Throwable failure = null;
            try {
                Future<Result> future = problem.resolve();
                if (future != null) {
                    result = future.get();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failure = ex;
            } catch (ExecutionException ex) {
                failure = ex.getCause() == null ? ex : ex.getCause();
            } catch (RuntimeException ex) {
                failure = ex;
            }
            Result resolved = result;
            Throwable causes = failure;
            Platform.runLater(() -> afterResolve(name, resolved, causes));
        });
    }

    private void afterResolve(String name, Result result, Throwable failure) {
        resolving.set(false);
        if (failure != null) {
            String text = message("MSG_ResolutionFailed", name);
            showStatus(text, STATUS_ERROR);
            publishStatus(text);
            LOG.log(Level.WARNING, "Could not resolve " + name, failure);
            ErrorReporter.report(message("MSG_ProjectProblems_Title"), text,
                    failure.getMessage(), failure);
        } else if (result == null) {
            String text = message("MSG_ResolutionFailed", name);
            showStatus(text, STATUS_ERROR);
            publishStatus(text);
        } else {
            Status status = result.getStatus();
            String resultMessage = result.getMessage();
            if (status == Status.UNRESOLVED) {
                String text = resultMessage != null
                        ? resultMessage : message("MSG_ProblemUnresolved", name);
                showStatus(text, STATUS_ERROR);
                publishStatus(text);
            } else if (status == Status.RESOLVED_WITH_WARNING) {
                String text = resultMessage != null
                        ? resultMessage : message("MSG_ProblemResolvedWithWarning", name);
                showStatus(text, STATUS_WARNING);
                publishStatus(text);
            } else {
                String text = resultMessage != null
                        ? resultMessage : message("MSG_ProblemResolved");
                showStatus(text, null);
                publishStatus(text);
            }
        }
        model.refresh();
        if (problems.getSelectionModel().getSelectedItem() == null
                && !model.getProblems().isEmpty()) {
            problems.getSelectionModel().selectFirst();
        }
        updateDetails();
    }

    private void updateDetails() {
        ProjectProblemsModel.ProblemItem item = problems.getSelectionModel().getSelectedItem();
        if (item == null) {
            detailTitle.setText("");
            severity.setText("");
            severity.getStyleClass().removeAll(SEVERITY_ERROR, SEVERITY_WARNING);
            description.clear();
            return;
        }
        detailTitle.setText(item.displayName(model.hasMultipleProjects()));
        boolean error = item.isError();
        severity.setText(message(error ? "LBL_Error" : "LBL_Warning"));
        severity.getStyleClass().removeAll(SEVERITY_ERROR, SEVERITY_WARNING);
        severity.getStyleClass().add(error ? SEVERITY_ERROR : SEVERITY_WARNING);
        description.setText(item.description());
        description.positionCaret(0);
    }

    private void showStatus(String text, String styleClass) {
        statusLine.getStyleClass().removeAll(STATUS_ERROR, STATUS_WARNING);
        if (text == null || text.isBlank()) {
            statusLine.setText("");
            statusLine.setVisible(false);
            statusLine.setManaged(false);
            return;
        }
        statusLine.setText(text);
        if (styleClass != null) {
            statusLine.getStyleClass().add(styleClass);
        }
        statusLine.setVisible(true);
        statusLine.setManaged(true);
    }

    private static void publishStatus(String text) {
        StatusMessageService service = Lookup.getDefault().lookup(StatusMessageService.class);
        if (service != null) {
            service.setMessage(text);
        }
    }

    private static String title(ProjectProblemsModel model) {
        List<org.netbeans.api.project.Project> projects = model.getProjects();
        if (projects.size() == 1) {
            return message("TITLE_ResolveProjectProblemsFor",
                    ProjectProblemsModel.ProblemItem.projectName(projects.get(0)));
        }
        return message("TITLE_ResolveProjectProblems");
    }

    private static String message(String key, Object... arguments) {
        return NbBundle.getMessage(ProjectProblemsCustomizer.class, key, arguments);
    }
}
