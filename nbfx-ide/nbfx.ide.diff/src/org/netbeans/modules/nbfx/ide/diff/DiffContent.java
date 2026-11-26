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

    /** What opens the diff again in a later session, or {@code null} when it is not restored. */
    default SavedDocument savedState() {
        return null;
    }

    /**
     * The editor of the side that is edited in the diff shown, which the tab hands what is asked
     * of it - undo, paste, find; holds {@code null} when neither side is.
     */
    default ObservableValue<EditorDocument> editorProperty() {
        return new SimpleObjectProperty<>(null);
    }

    /** Whether there are edits made in this diff that are not saved. */
    default ObservableValue<Boolean> modifiedProperty() {
        return new SimpleBooleanProperty(false);
    }

    /** Whether this diff keeps edits of the text {@code other} shows that are not saved, in an editor not shown. */
    default boolean keepsEditsOf(EditorDocument other) {
        return false;
    }

    /** Writes the edits made in this diff to their files. */
    default void save() throws IOException {
    }
}
