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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.netbeans.api.nbfx.dialogs.ErrorReporter;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * The Initialize a Git Repository dialog, as in NetBeans: asks for the folder the repository is
 * created in, proposing the project's, and runs {@code git init} there.
 *
 * @since 1.2
 */
final class GitInitDialog {

    private static final RequestProcessor RP = new RequestProcessor("nbfx-git-init", 1);

    private GitInitDialog() {
    }

    /**
     * Asks for the folder, starting from {@code proposed}, and creates the repository in it. Must run
     * on the JavaFX Application Thread.
     *
     * @param proposed the folder first shown; may be {@code null}
     * @param done run on the FX thread once the repository exists; may be {@code null}
     */
    static void show(Path proposed, Runnable done) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(message("Git.init.title"));
        dialog.setResizable(true);
        Window owner = Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(
                Objects.requireNonNull(GitInitDialog.class.getResource("vcs.css")).toExternalForm());
        dialogPane.getStyleClass().add("git-init");
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField path = new TextField(proposed == null ? "" : proposed.toString());
        path.setPrefColumnCount(32);
        HBox.setHgrow(path, Priority.ALWAYS);
        Label pathLabel = new Label(message("Git.init.path"));
        pathLabel.setLabelFor(path);
        Button browse = new Button(message("Git.init.browse"));
        browse.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(message("Git.init.title"));
            Path current = folder(path.getText());
            if (current != null) {
                chooser.setInitialDirectory(current.toFile());
            }
            File chosen = chooser.showDialog(dialogPane.getScene().getWindow());
            if (chosen != null) {
                path.setText(chosen.getAbsolutePath());
            }
        });
        HBox row = new HBox(8, pathLabel, path, browse);
        row.setAlignment(Pos.CENTER_LEFT);
        dialogPane.setContent(new VBox(10, new Label(message("Git.init.prompt")), row));
        // A repository is created in a folder that exists.
        dialogPane.lookupButton(ButtonType.OK).disableProperty().bind(
                path.textProperty().map(text -> folder(text) == null));
        dialog.setOnShown(event -> {
            path.requestFocus();
            path.selectAll();
        });
        dialog.showAndWait().ifPresent(result -> {
            Path folder = folder(path.getText());
            if (result == ButtonType.OK && folder != null) {
                init(folder, done);
            }
        });
    }

    /** Runs {@code git init} in {@code folder} off the FX thread, then {@code done} on it. */
    static void init(Path folder, Runnable done) {
        RP.post(() -> {
            GitClient.Result result = GitClient.init(folder);
            if (result.exitCode() == 0) {
                // The .git folder was made behind the filesystem's back.
                FileObject created = FileUtil.toFileObject(folder.toFile());
                if (created != null) {
                    created.refresh();
                }
                // A folder that was in no working tree is in one now.
                GitStatusCache.getDefault().repositoriesChanged();
            }
            Platform.runLater(() -> {
                if (result.exitCode() != 0) {
                    ErrorReporter.report(message("Git.init.title"), message("Git.error"), result.output(), null);
                } else if (done != null) {
                    done.run();
                }
            });
        });
    }

    /** The existing folder {@code text} names, or {@code null}. */
    static Path folder(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            Path folder = Path.of(text.trim());
            return folder.isAbsolute() && Files.isDirectory(folder) ? folder : null;
        } catch (InvalidPathException ex) {
            return null;
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitInitDialog.class, key);
    }
}
