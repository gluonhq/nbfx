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

    /** What opens the diff again in a later session, or {@code null} when it is not restored. */
    default SavedDocument savedState() {
        return null;
    }
}
