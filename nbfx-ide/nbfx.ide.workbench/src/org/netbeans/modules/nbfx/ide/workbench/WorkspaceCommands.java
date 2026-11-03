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
package org.netbeans.modules.nbfx.ide.workbench;

import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.modules.nbfx.ide.actions.IdeActionIds;
import org.netbeans.modules.nbfx.platform.shell.EditorContexts;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/**
 * Registers the workbench-owned commands: navigator/editor selection, window reset and document
 * close, plus Select in Projects/Files. Project lifecycle and build commands live in their own IDE
 * modules; the workbench only contributes what depends on the main window, its navigator panes and
 * the shared editor set.
 */
final class WorkspaceCommands {

    /** The launcher-owned window actions and their enablement. */
    record WindowActions(
            Runnable selectProjects, Runnable selectFiles, Runnable selectEditor, Runnable resetWindows,
            Runnable closeDocument, ObservableValue<Boolean> closeDocumentDisabled,
            Runnable closeAllDocuments, ObservableValue<Boolean> closeAllDisabled,
            Runnable closeOtherDocuments, ObservableValue<Boolean> closeOtherDisabled) {
    }

    private WorkspaceCommands() {
    }

    static void register(ActionRegistry registry, WindowActions actions,
            Consumer<FileObject> selectInProjects, Consumer<FileObject> selectInFiles) {
        if (registry == null) {
            return;
        }
        registry.register(new RunnableCommand(IdeActionIds.SELECT_PROJECTS, message("CTL_SelectProjectsCommand"),
                new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.SHORTCUT_DOWN), actions.selectProjects()));
        registry.register(new RunnableCommand(IdeActionIds.SELECT_FILES, message("CTL_SelectFilesCommand"),
                new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.SHORTCUT_DOWN), actions.selectFiles()));
        registry.register(new RunnableCommand(ActionIds.SELECT_EDITOR, message("CTL_SelectEditorCommand"),
                new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN), actions.selectEditor()));
        registry.register(new RunnableCommand(ActionIds.RESET_WINDOWS, message("CTL_ResetWindowsCommand"),
                null, actions.resetWindows()));
        registry.register(new RunnableCommand(ActionIds.CLOSE_DOCUMENT, message("CTL_CloseDocumentCommand"),
                new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
                actions.closeDocument(), actions.closeDocumentDisabled()));
        registry.register(new RunnableCommand(ActionIds.CLOSE_ALL_DOCUMENTS, message("CTL_CloseAllDocumentsCommand"),
                new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                actions.closeAllDocuments(), actions.closeAllDisabled()));
        registry.register(new RunnableCommand(ActionIds.CLOSE_OTHER_DOCUMENTS, message("CTL_CloseOtherDocumentsCommand"),
                null, actions.closeOtherDocuments(), actions.closeOtherDisabled()));

        ObservableValue<EditorDocument> active = EditorContexts.activeDocument();
        ObservableValue<Boolean> disabled = active == null
                ? new SimpleBooleanProperty(true)
                : Bindings.createBooleanBinding(() -> active.getValue() == null, active);
        registry.register(new RunnableCommand(IdeActionIds.SELECT_IN_PROJECTS, message("CTL_SelectInProjectsCommand"),
                new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> {
                    EditorDocument document = active == null ? null : active.getValue();
                    if (document != null) {
                        selectInProjects.accept(document.getFileObject());
                    }
                }, disabled));
        registry.register(new RunnableCommand(IdeActionIds.SELECT_IN_FILES, message("CTL_SelectInFilesCommand"),
                new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> {
                    EditorDocument document = active == null ? null : active.getValue();
                    if (document != null) {
                        selectInFiles.accept(document.getFileObject());
                    }
                }, disabled));
    }

    private static String message(String key) {
        return NbBundle.getMessage(WorkspaceCommands.class, key);
    }
}
