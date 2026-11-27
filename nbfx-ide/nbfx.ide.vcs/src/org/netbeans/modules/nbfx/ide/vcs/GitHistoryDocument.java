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

import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.netbeans.api.nbfx.editor.CaretInfo;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.SavedDocument;
import org.openide.filesystems.FileObject;

/**
 * The document of a History tab in the editor area: a {@link GitHistoryPane} that the shell treats
 * as it does an editor - a tab with a title, closed and moved like the others - though it is the
 * editor of no file. There is nothing in it to save, undo or paste. It is told apart from other
 * histories by its {@link #scope()}, and opened again with the session from it.
 *
 * @since 1.4
 */
final class GitHistoryDocument implements EditorDocument {

    private final GitHistoryView.Scope scope;
    private final String title;
    private final GitHistoryPane pane;
    private final ReadOnlyBooleanWrapper never = new ReadOnlyBooleanWrapper(this, "never", false);
    private final ObservableValue<CaretInfo> noCaret = new SimpleObjectProperty<>();
    private final ObservableValue<String> noLineSeparator = new SimpleObjectProperty<>();

    GitHistoryDocument(GitHistoryView.Scope scope, String title, GitHistoryPane pane) {
        this.scope = Objects.requireNonNull(scope);
        this.title = Objects.requireNonNull(title);
        this.pane = Objects.requireNonNull(pane);
    }

    /** What this is the history of: opening the history of the same shows this tab again. */
    GitHistoryView.Scope scope() {
        return scope;
    }

    GitHistoryPane pane() {
        return pane;
    }

    /** No file: a history is not the editor of what it is the history of. */
    @Override
    public FileObject getFileObject() {
        return null;
    }

    @Override
    public SavedDocument getSavedState() {
        return new SavedDocument(GitHistoryView.RESTORER_ID, scope.saved());
    }

    @Override
    public Node getNode() {
        return pane;
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
        // Nothing to save.
    }

    @Override
    public void undo() {
    }

    @Override
    public void redo() {
    }

    @Override
    public void copy() {
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
        return never.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyBooleanProperty editableProperty() {
        return never.getReadOnlyProperty();
    }

    @Override
    public ObservableValue<CaretInfo> caretInfoProperty() {
        return noCaret;
    }

    @Override
    public ObservableValue<String> lineSeparatorProperty() {
        return noLineSeparator;
    }

    @Override
    public void setLineSeparator(String separator) {
    }

    @Override
    public void requestFocus() {
        pane.list().requestFocus();
    }
}
