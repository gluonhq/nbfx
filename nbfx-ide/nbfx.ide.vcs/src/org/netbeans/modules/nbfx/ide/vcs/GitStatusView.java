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
package org.netbeans.modules.nbfx.ide.vcs;

import org.netbeans.api.nbfx.dialogs.ErrorReporter;
import org.netbeans.api.nbfx.actions.ActionContext;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;
import org.netbeans.modules.nbfx.project.context.ProjectContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.openide.filesystems.FileObject;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * The Git view: the selected project's branch and the files changed in its folder - not those of the
 * other projects of its repository - with Refresh and Commit, which takes those changes. It runs
 * the {@code git} command line off the FX thread and reflects the result in the table.
 *
 * @since 1.0
 */
final class GitStatusView extends BorderPane {

    private static final RequestProcessor RP = new RequestProcessor("nbfx-git", 1);

    /** What the table compares; the commit takes every change, so it is the last commit against the disk. */
    private static final VcsCompareMode MODE = VcsCompareMode.HEAD_VS_WORKING_TREE;

    private final ObservableList<VcsFileStatus> changes = FXCollections.observableArrayList();
    private final TableView<VcsFileStatus> table = new TableView<>(changes);
    private final Label branch = new Label();
    private final Button commit = new Button(message("Git.commit"));
    /** Counts the reads asked for: what an earlier one found is not shown over a later one. */
    private final AtomicLong reads = new AtomicLong();
    /** The project folder the rows are of, which is what Commit commits; {@code null} without rows read. */
    private Path shown;

    GitStatusView() {
        getStyleClass().add("git-view");

        TableColumn<VcsFileStatus, String> status = new TableColumn<>(message("Git.column.status"));
        status.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().change(MODE).map(GitStatusView::label).orElse("")));
        status.setPrefWidth(140);
        TableColumn<VcsFileStatus, String> file = new TableColumn<>(message("Git.column.file"));
        file.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().path(MODE)));
        table.getColumns().add(status);
        table.getColumns().add(file);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button refresh = new Button(message("Git.refresh"));
        refresh.setOnAction(event -> reload());
        commit.setOnAction(event -> commitDialog());
        commit.setDisable(true);
        setTop(new ToolBar(branch, refresh, commit));
        setCenter(table);

        // Follow the global action context: reload when the selected file/project or the active
        // editor document changes, so the view always shows the current selection's repository.
        ActionContext context = ActionContext.getDefault();
        if (context != null) {
            // The context may change on any thread; the view is read and changed on that of JavaFX.
            LookupListener listener = event -> Platform.runLater(this::reload);
            for (Class<?> type : List.of(FileObject.class, EditorDocument.class, OpenProject.class)) {
                context.lookupResult(type).addLookupListener(listener);
            }
        }
        reload();
    }

    /**
     * Reads the git status of the selected project off the FX thread and fills the table: the changes
     * in the project folder. Must run on the JavaFX Application Thread.
     */
    void reload() {
        Path dir = selectedProject();
        long read = reads.incrementAndGet();
        // Nothing to commit until it is read again: the rows may be out of date.
        commit.setDisable(true);
        if (!Objects.equals(dir, shown)) {
            // Another project: the rows of the last one are not its changes.
            show(null, "", List.of());
        }
        if (dir == null) {
            return;
        }
        RP.post(() -> {
            if (read != reads.get()) {
                // Asked again while waiting: that read follows.
                return;
            }
            try {
                if (!GitClient.isRepository(dir)) {
                    Platform.runLater(() -> {
                        if (read != reads.get()) {
                            return;
                        }
                        show(null, message("Git.notRepository"), List.of());
                    });
                    return;
                }
                Path root = GitClient.rootOf(dir);
                String branchName = GitClient.branch(root);
                VcsStatus status = GitClient.status(dir);
                List<VcsFileStatus> files = status.files(MODE);
                Platform.runLater(() -> {
                    if (read != reads.get()) {
                        // The selection changed while this was read: the read of that one follows.
                        return;
                    }
                    show(dir, message("Git.branch") + ": " + branchName, files);
                });
            } catch (RuntimeException | java.io.IOException ex) {
                Platform.runLater(() -> {
                    if (read == reads.get()) {
                        // What was shown is not known to hold any more.
                        show(null, "", List.of());
                        ErrorReporter.report(message("Git.title"), message("Git.error"), ex.getMessage(), ex);
                    }
                });
            }
        });
    }

    /** Shows {@code files} as the changes of {@code dir}; Commit is for a folder with changes only. */
    private void show(Path dir, String title, List<VcsFileStatus> files) {
        shown = dir;
        branch.setText(title);
        changes.setAll(files);
        commit.setDisable(dir == null || files.isEmpty());
    }

    private void commitDialog() {
        // What is committed is what the rows show, whatever is selected by now.
        Path dir = shown;
        if (dir == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(message("Git.commit.title"));
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextArea messageArea = new TextArea();
        messageArea.setPromptText(message("Git.commit.message"));
        messageArea.setPrefRowCount(4);
        VBox.setVgrow(messageArea, Priority.ALWAYS);
        pane.setContent(new VBox(messageArea));
        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK && !messageArea.getText().isBlank()) {
                commit(dir, messageArea.getText().trim());
            }
        });
    }

    private void commit(Path dir, String message) {
        RP.post(() -> {
            // The changes of the project: the table shows no others.
            GitClient.Result add = GitClient.addAll(dir);
            if (add.exitCode() != 0) {
                Platform.runLater(() -> ErrorReporter.report(message("Git.title"),
                        message("Git.error"), add.output(), null));
                return;
            }
            GitClient.Result commit = GitClient.commit(dir, message);
            if (commit.exitCode() != 0) {
                Platform.runLater(() -> ErrorReporter.report(message("Git.title"),
                        message("Git.error"), commit.output(), null));
            }
            Platform.runLater(this::reload);
        });
    }

    private static Path selectedProject() {
        OpenProject project = ProjectContext.selectedProject();
        return project == null ? null : Paths.get(project.getPath());
    }

    private static String label(VcsChangeKind kind) {
        return message("Git.kind." + kind.name());
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitStatusView.class, key);
    }
}
