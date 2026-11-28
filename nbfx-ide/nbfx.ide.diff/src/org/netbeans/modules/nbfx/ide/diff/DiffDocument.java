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

import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
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
 */
final class DiffDocument implements EditorDocument {

    private final Object key;
    private final String title;
    private final Region node;
    private final DiffContent content;
    private final Runnable onDispose;
    private final ReadOnlyBooleanWrapper never = new ReadOnlyBooleanWrapper(this, "never", false);
    private final ReadOnlyBooleanWrapper hasSelection = new ReadOnlyBooleanWrapper(this, "hasSelection", false);

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
        // Of the text the keyboard is in, or was last: what Copy takes.
        hasSelection.bind(content.activeEditorProperty().flatMap(EditorDocument::hasSelectionProperty).orElse(false));
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
        return false;
    }

    @Override
    public ReadOnlyBooleanProperty modifiedProperty() {
        return never.getReadOnlyProperty();
    }

    @Override
    public void save() {
    }

    @Override
    public void undo() {
    }

    @Override
    public void redo() {
    }

    /** Copies what is selected in the text the keyboard is in; nothing is cut from or pasted into a diff. */
    @Override
    public void copy() {
        EditorDocument active = content.activeEditorProperty().getValue();
        if (active != null) {
            active.copy();
        }
    }

    @Override
    public String getSelectedText() {
        EditorDocument active = content.activeEditorProperty().getValue();
        return active == null ? "" : active.getSelectedText();
    }

    @Override
    public void cut() {
    }

    @Override
    public void paste() {
    }

    @Override
    public ReadOnlyBooleanProperty canUndoProperty() {
        return never.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyBooleanProperty canRedoProperty() {
        return never.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyBooleanProperty hasSelectionProperty() {
        return hasSelection.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyBooleanProperty editableProperty() {
        return never.getReadOnlyProperty();
    }

    @Override
    public ObservableValue<CaretInfo> caretInfoProperty() {
        return new SimpleObjectProperty<>(null);
    }

    @Override
    public ObservableValue<String> lineSeparatorProperty() {
        return new SimpleStringProperty(null);
    }

    @Override
    public void setLineSeparator(String separator) {
    }

    @Override
    public void requestFocus() {
        content.focusEditor();
    }

    @Override
    public void dispose() {
        hasSelection.unbind();
        content.dispose();
        onDispose.run();
    }
}
