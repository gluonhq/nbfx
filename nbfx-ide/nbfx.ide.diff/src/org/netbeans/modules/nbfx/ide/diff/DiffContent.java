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

import java.io.IOException;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.SavedDocument;

/** What a diff tab holds, as far as its {@link DiffDocument} needs to know. */
interface DiffContent {

    /** Reads again what is compared, and compares it. */
    void refresh();

    /** Releases the editors. */
    void dispose();

    /**
     * Gives the keyboard to the text compared to, so that the keys scroll the diff as soon as its
     * tab is chosen.
     */
    void focusEditor();

    /**
     * The editor the keyboard is in, or was in last, of the two texts shown: the one Copy is of.
     * Its value is {@code null} while no text is shown.
     */
    ObservableValue<EditorDocument> activeEditorProperty();

    /**
     * The editor of the side that is edited in the diff shown, which the tab hands what changes a
     * text - undo, redo, and cut and paste while the keyboard is in it; its value is {@code null}
     * while neither side is. It is that editor on the Textual tab too, where it is not seen: what
     * is undone there shows in the unified diff, which is of that text.
     */
    default ObservableValue<EditorDocument> editorProperty() {
        return new SimpleObjectProperty<>(null);
    }

    /**
     * Whether what is edited in this diff takes edits and can be saved by now: not for a text
     * that is only shown, nor for a file that was locked - changed on disk under edits not saved.
     */
    default ObservableValue<Boolean> editableProperty() {
        return new SimpleBooleanProperty(false);
    }

    /** Whether there are edits made in this diff that are not saved. */
    default ObservableValue<Boolean> modifiedProperty() {
        return new SimpleBooleanProperty(false);
    }

    /** Writes the edits made in this diff to their files. */
    default void save() throws IOException {
    }

    /** What opens the diff again in a later session, or {@code null} when it is not restored. */
    default SavedDocument savedState() {
        return null;
    }
}
