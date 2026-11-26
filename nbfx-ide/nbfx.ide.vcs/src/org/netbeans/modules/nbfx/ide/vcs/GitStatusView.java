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

import org.openide.util.Lookup;
import javafx.stage.Modality;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.ArrayList;
import org.netbeans.api.nbfx.dialogs.ErrorReporter;
import org.netbeans.api.nbfx.actions.ActionContext;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;
import org.netbeans.modules.nbfx.project.context.ProjectContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.openide.filesystems.FileObject;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;

/**
 * The Git view: the branch of the project it was opened for and the files changed in its folder -
 * not those of the other projects of its repository - in a {@link GitStatusPane}, with Refresh and
 * Commit, which takes those changes. It runs the {@code git} command line off the FX thread and hands
 * the result to the pane; the compare mode and the tree or list view are remembered between sessions.
 *
 * @since 1.0
 */
final class GitStatusView extends BorderPane {

    private static final RequestProcessor RP = new RequestProcessor("nbfx-git", 1);

    private static final String KEY_MODE = "status.mode";
    private static final String KEY_FLAVOUR = "status.flavour";
    private static final String KEY_COLUMNS = "status.columns";

    private final Preferences preferences = NbPreferences.forModule(GitStatusView.class);
    /** Kept, as a lookup result is only weakly held by the context and would lose its listener. */
    private final List<Lookup.Result<?>> contextResults = new ArrayList<>();
    /** The project whose changes are shown, on the FX thread; {@code null} until one is known. */
    private OpenProject project;
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(message("Git.title"));
    private final GitStatusPane pane = new GitStatusPane(
            stored(KEY_MODE, VcsCompareMode.HEAD_VS_WORKING_TREE), stored(KEY_FLAVOUR, GitStatusPane.Flavour.TREE));
    /** Counts the reads asked for: what an earlier one found is not shown over a later one. */
    private final AtomicLong reads = new AtomicLong();
    /** The project folder the rows are of, which is what Commit commits; {@code null} without rows read. */
    private Path shown;

    GitStatusView() {
        pane.modeProperty().subscribe(mode -> preferences.put(KEY_MODE, mode.name()));
        pane.flavourProperty().subscribe(flavour -> preferences.put(KEY_FLAVOUR, flavour.name()));
        pane.restoreColumns(storedColumns());
        pane.setOnColumnsChanged(this::storeColumns);
        pane.setOnRefresh(this::reload);
        pane.setOnCommit(this::commitDialog);
        setCenter(pane);

        // The view shows the project it was opened for, as NetBeans' Git window, not the selection:
        // moving its tab or clicking elsewhere changes the selection, and must not change the view.
        // Only a view that has no project yet - restored with the layout at start-up - takes the
        // first one selected.
        ActionContext context = ActionContext.getDefault();
        if (context != null) {
            LookupListener listener = event -> {
                if (project == null) {
                    Platform.runLater(this::reload);
                }
            };
            for (Class<?> type : List.of(FileObject.class, EditorDocument.class, OpenProject.class)) {
                Lookup.Result<?> result = context.lookupResult(type);
                result.addLookupListener(listener);
                contextResults.add(result);
            }
        }
        // Coming back to the view shows what changed on disk meanwhile.
        focusWithinProperty().subscribe(focused -> {
            if (focused) {
                reload();
            }
        });
        reload();
    }

    /** What the tab of the view reads: "Git", or with the project, the repository and the branch shown. */
    ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }

    /** Shows the changes of {@code shown} from now on; {@code null} keeps the project and refreshes. */
    void show(OpenProject shown) {
        if (shown != null) {
            project = shown;
        }
        reload();
    }

    /**
     * Reads the git status of the project off the FX thread and fills the table: the changes in the
     * project folder, not those of the other projects of its repository. Must run on the JavaFX
     * Application Thread.
     */
    void reload() {
        if (project == null) {
            project = ProjectContext.selectedProject();
        }
        OpenProject project = this.project;
        Path dir = project == null ? null : Paths.get(project.getPath());
        long read = reads.incrementAndGet();
        // Nothing to commit until it is read again: the rows may be out of date.
        pane.setCommitDisabled(true);
        if (dir == null) {
            show("", message("Git.noProject"));
            return;
        }
        if (shown != null && !dir.equals(shown)) {
            // Another project: the rows of the last one are not its changes.
            show("", "");
        }
        RP.post(() -> {
            if (read != reads.get()) {
                // Asked again while waiting: that read follows.
                return;
            }
            try {
                if (!GitClient.isRepository(dir)) {
                    Platform.runLater(() -> {
                        if (read == reads.get()) {
                            show("", message("Git.notRepository"));
                        }
                    });
                    return;
                }
                Path root = GitClient.rootOf(dir);
                String branchName = GitClient.branch(root);
                VcsStatus status = GitClient.status(dir);
                // Commit takes every change, staged or not, whatever the table compares.
                boolean nothingToCommit = status.files(VcsCompareMode.HEAD_VS_WORKING_TREE).isEmpty();
                String repositoryName = root.getFileName() == null ? root.toString() : root.getFileName().toString();
                Platform.runLater(() -> {
                    if (read != reads.get()) {
                        // Asked again while this was read: that read follows.
                        return;
                    }
                    shown = dir;
                    title.set(NbBundle.getMessage(GitStatusView.class, "Git.title.repository",
                            project.getDisplayName(), repositoryName, branchName));
                    pane.setBranch(message("Git.branch") + ": " + branchName);
                    pane.setStatus(root, status);
                    pane.setCommitDisabled(nothingToCommit);
                });
            } catch (RuntimeException | java.io.IOException ex) {
                Platform.runLater(() -> {
                    if (read == reads.get()) {
                        // What was shown is not known to hold any more.
                        show("", message("Git.error"));
                        ErrorReporter.report(message("Git.title"), message("Git.error"), ex.getMessage(), ex);
                    }
                });
            }
        });
    }

    /** No changes to show, and so none to commit: the reason in place of the table. */
    private void show(String branch, String text) {
        shown = null;
        title.set(message("Git.title"));
        pane.setBranch(branch);
        pane.setMessage(text);
        pane.setCommitDisabled(true);
    }

    private <E extends Enum<E>> E stored(String key, E fallback) {
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), preferences.get(key, fallback.name()));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    /** The columns as "id=width:visible", separated by commas; anything else is ignored. */
    private List<GitStatusPane.ColumnState> storedColumns() {
        List<GitStatusPane.ColumnState> columns = new ArrayList<>();
        for (String entry : preferences.get(KEY_COLUMNS, "").split(",")) {
            int equals = entry.indexOf('=');
            int colon = entry.indexOf(':', equals + 1);
            if (equals <= 0 || colon < 0) {
                continue;
            }
            try {
                columns.add(new GitStatusPane.ColumnState(entry.substring(0, equals),
                        Double.parseDouble(entry.substring(equals + 1, colon)),
                        Boolean.parseBoolean(entry.substring(colon + 1))));
            } catch (NumberFormatException corrupt) {
                return List.of();
            }
        }
        return columns;
    }

    private void storeColumns() {
        preferences.put(KEY_COLUMNS, pane.columns().stream()
                .map(column -> column.id() + "=" + column.width() + ":" + column.visible())
                .collect(Collectors.joining(",")));
    }

    private void commitDialog() {
        // What is committed is what the rows show, whatever is selected by now.
        Path dir = shown;
        if (dir == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(message("Git.commit.title"));
        dialog.setResizable(true);
        if (getScene() != null && getScene().getWindow() != null) {
            dialog.initOwner(getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
        }
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(
                Objects.requireNonNull(GitStatusView.class.getResource("vcs.css")).toExternalForm());
        dialogPane.getStyleClass().add("git-commit");
        ButtonType commitType = new ButtonType(message("Git.commit"), ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(commitType, ButtonType.CANCEL);
        TextArea messageArea = new TextArea();
        messageArea.setPromptText(message("Git.commit.message"));
        messageArea.setPrefRowCount(5);
        messageArea.setWrapText(true);
        VBox.setVgrow(messageArea, Priority.ALWAYS);
        dialogPane.setContent(new VBox(messageArea));
        // Nothing to commit without a message.
        dialogPane.lookupButton(commitType).disableProperty().bind(
                messageArea.textProperty().map(String::isBlank));
        dialog.setOnShown(event -> messageArea.requestFocus());
        dialog.showAndWait().ifPresent(result -> {
            if (result == commitType && !messageArea.getText().isBlank()) {
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

    private static String message(String key) {
        return NbBundle.getMessage(GitStatusView.class, key);
    }
}
