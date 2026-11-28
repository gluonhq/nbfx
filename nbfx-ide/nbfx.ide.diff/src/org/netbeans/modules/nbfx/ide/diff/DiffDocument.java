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
package org.netbeans.modules.nbfx.ide.diff;

import javafx.beans.binding.Bindings;
import java.util.function.Consumer;
import java.io.IOException;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import org.netbeans.api.nbfx.editor.CaretInfo;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.SavedDocument;
import org.openide.filesystems.FileObject;

/**
 * The document of a diff tab in the editor area: a {@link DiffPane} that the shell treats as it does
 * an editor - a tab with a title, closed and moved like the others - though it is the editor of no
 * file. It has nothing to save, undo or paste, and is told apart from other diffs by its
 * {@link #key()}.
 * <p>
 * When the diff shown has a side that is edited, what changes a text - undo, cut, paste - is handed
 * to the editor of that side, and saving writes the edits made in the diff; Copy and Find are of
 * the text the keyboard is in, edited or not.
 */
final class DiffDocument implements EditorDocument {

    private final Object key;
    private final String title;
    private final Region node;
    private final DiffContent content;
    private final Runnable onDispose;
    /** The editor of the side that is edited; of none while neither side is. */
    private final ObservableValue<EditorDocument> editor;
    /** The editor the keyboard is in, or was in last. */
    private final ObservableValue<EditorDocument> active;
    private final ReadOnlyBooleanWrapper modified = new ReadOnlyBooleanWrapper(this, "modified", false);
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(this, "canUndo", false);
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(this, "canRedo", false);
    private final ReadOnlyBooleanWrapper hasSelection = new ReadOnlyBooleanWrapper(this, "hasSelection", false);
    private final ReadOnlyBooleanWrapper editable = new ReadOnlyBooleanWrapper(this, "editable", false);

    <T extends Region & DiffContent> DiffDocument(Object key, String title, T content) {
        this(key, title, content, () -> { });
    }

    /** @param onDispose run when the tab of the diff is closed */
    <T extends Region & DiffContent> DiffDocument(Object key, String title, T content, Runnable onDispose) {
        this.key = Objects.requireNonNull(key);
        this.title = Objects.requireNonNull(title);
        this.node = Objects.requireNonNull(content);
        this.content = content;
        this.onDispose = Objects.requireNonNull(onDispose);
        editor = content.editorProperty();
        active = content.activeEditorProperty();
        ObservableValue<Boolean> unsaved = content.modifiedProperty();
        modified.bind(unsaved.orElse(false));
        canUndo.bind(editor.flatMap(EditorDocument::canUndoProperty).orElse(false));
        canRedo.bind(editor.flatMap(EditorDocument::canRedoProperty).orElse(false));
        // Of the text the keyboard is in, or was last: what Copy takes.
        hasSelection.bind(active.flatMap(EditorDocument::hasSelectionProperty).orElse(false));
        // Edits kept of a file no longer shown can be saved as well: the tab is not a locked one.
        ObservableValue<Boolean> accepts = editor.flatMap(EditorDocument::editableProperty).orElse(false);
        editable.bind(Bindings.createBooleanBinding(
                () -> accepts.getValue() || Boolean.TRUE.equals(unsaved.getValue()), accepts, unsaved));
    }

    /** What this diff is of: opening a diff with the same key shows this one again. */
    Object key() {
        return key;
    }

    DiffContent content() {
        return content;
    }

    /** As its content says: a diff of what a provider has can be opened again, one of two texts cannot. */
    @Override
    public SavedDocument getSavedState() {
        return content.savedState();
    }

    /** No file: a diff is not the editor of either of the two it shows. */
    @Override
    public FileObject getFileObject() {
        return null;
    }

    @Override
    public Node getNode() {
        return node;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public boolean isModified() {
        return modified.get();
    }

    @Override
    public ReadOnlyBooleanProperty modifiedProperty() {
        return modified.getReadOnlyProperty();
    }

    /** Writes the edits made in the diff to their files. */
    @Override
    public void save() throws IOException {
        content.save();
    }

    /** The editors of the file edited here show the same text: closing this tab loses nothing while one is open. */
    @Override
    public boolean sharesContentWith(EditorDocument other) {
        EditorDocument edited = editor.getValue();
        return this == other || edited != null && edited.sharesContentWith(other);
    }

    @Override
    public void undo() {
        edited(EditorDocument::undo);
    }

    @Override
    public void redo() {
        edited(EditorDocument::redo);
    }

    /** Copies what is selected in the text the keyboard is in, edited or only shown. */
    @Override
    public void copy() {
        active(EditorDocument::copy);
    }

    @Override
    public String getSelectedText() {
        EditorDocument in = active.getValue();
        return in == null ? "" : in.getSelectedText();
    }

    /** Only of the text that is edited, and only while the keyboard is in it. */
    @Override
    public void cut() {
        if (editor.getValue() != null && editor.getValue() == active.getValue()) {
            editor.getValue().cut();
        }
    }

    @Override
    public void paste() {
        if (editor.getValue() != null && editor.getValue() == active.getValue()) {
            editor.getValue().paste();
        }
    }

    @Override
    public ReadOnlyBooleanProperty canUndoProperty() {
        return canUndo.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyBooleanProperty canRedoProperty() {
        return canRedo.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyBooleanProperty hasSelectionProperty() {
        return hasSelection.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyBooleanProperty editableProperty() {
        return editable.getReadOnlyProperty();
    }

    @Override
    public ObservableValue<CaretInfo> caretInfoProperty() {
        return editor.flatMap(EditorDocument::caretInfoProperty);
    }

    @Override
    public ObservableValue<String> lineSeparatorProperty() {
        return editor.flatMap(EditorDocument::lineSeparatorProperty);
    }

    @Override
    public void setLineSeparator(String separator) {
        edited(edited -> edited.setLineSeparator(separator));
    }

    /** Find is of the text the keyboard is in: either side can be searched, the edited one replaced in. */
    @Override
    public void showFind() {
        active(EditorDocument::showFind);
    }

    @Override
    public void showReplace() {
        edited(EditorDocument::showReplace);
    }

    @Override
    public void findNext() {
        active(EditorDocument::findNext);
    }

    @Override
    public void findPrevious() {
        active(EditorDocument::findPrevious);
    }

    @Override
    public void findSelection() {
        active(EditorDocument::findSelection);
    }

    @Override
    public void requestFocus() {
        content.focusEditor();
    }

    @Override
    public void dispose() {
        modified.unbind();
        canUndo.unbind();
        canRedo.unbind();
        hasSelection.unbind();
        editable.unbind();
        try {
            content.dispose();
        } finally {
            // Forgotten whatever became of its editors: a closed diff is not the one asked for again.
            onDispose.run();
        }
    }

    private void edited(Consumer<EditorDocument> action) {
        EditorDocument edited = editor.getValue();
        if (edited != null) {
            action.accept(edited);
        }
    }

    private void active(Consumer<EditorDocument> action) {
        EditorDocument in = active.getValue();
        if (in != null) {
            action.accept(in);
        }
    }
}
