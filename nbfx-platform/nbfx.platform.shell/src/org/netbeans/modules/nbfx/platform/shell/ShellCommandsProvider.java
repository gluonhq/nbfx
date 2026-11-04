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

import java.util.Collection;
import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.CommandsProvider;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.api.nbfx.editor.ContentManager;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * Generic shell commands for closing editor documents. They live in the platform shell so every
 * product gets them; the workbench keeps only the IDE-specific navigator/editor-selection commands.
 *
 * @since 1.0
 */
@ServiceProvider(service = CommandsProvider.class)
public final class ShellCommandsProvider implements CommandsProvider {

    @Override
    public Collection<Command> createCommands() {
        ObservableList<EditorDocument> documents = EditorContexts.openDocuments();
        ObservableValue<Boolean> empty = Bindings.isEmpty(documents);
        ObservableValue<Boolean> fewerThanTwo = Bindings.size(documents).lessThan(2);
        return List.of(
                new RunnableCommand(ActionIds.SELECT_EDITOR, message("CTL_SelectEditorCommand"),
                        new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN),
                        ShellCommandsProvider::selectEditor),
                new RunnableCommand(ActionIds.CLOSE_DOCUMENT, message("CTL_CloseDocumentCommand"),
                        new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN),
                        () -> DocumentCloser.closeDocument(activeDocument()), empty),
                new RunnableCommand(ActionIds.CLOSE_ALL_DOCUMENTS, message("CTL_CloseAllDocumentsCommand"),
                        new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                        DocumentCloser::closeAllDocuments, empty),
                new RunnableCommand(ActionIds.CLOSE_OTHER_DOCUMENTS, message("CTL_CloseOtherDocumentsCommand"),
                        null, () -> DocumentCloser.closeOtherDocuments(activeDocument()), fewerThanTwo));
    }

    private static void selectEditor() {
        ContentManager content = Lookup.getDefault().lookup(ContentManager.class);
        if (content != null) {
            content.focusActiveEditor();
        }
    }

    private static EditorDocument activeDocument() {
        ObservableValue<EditorDocument> active = EditorContexts.activeDocument();
        return active == null ? null : active.getValue();
    }

    private static String message(String key) {
        return NbBundle.getMessage(ShellCommandsProvider.class, key);
    }
}
