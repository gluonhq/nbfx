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
package org.netbeans.api.nbfx.actions;

/**
 * Stable identifiers for the built-in platform {@link Command}s, used to look them up from the
 * {@link ActionRegistry} and to wire UI controls to them.
 * <p>
 * IDE-only command ids live in {@code org.netbeans.modules.nbfx.ide.workbench.IdeActionIds}.
 */
public final class ActionIds {

    /** Saves the active editor document. */
    public static final String SAVE = "save";

    /** Saves all modified editor documents. */
    public static final String SAVE_ALL = "saveAll";

    /** Undoes the last edit in the active editor document. */
    public static final String UNDO = "undo";

    /** Redoes the last undone edit in the active editor document. */
    public static final String REDO = "redo";

    /** Copies the current selection of the active editor document to the clipboard. */
    public static final String COPY = "copy";

    /** Cuts the current selection of the active editor document to the clipboard. */
    public static final String CUT = "cut";

    /** Pastes the clipboard content into the active editor document. */
    public static final String PASTE = "paste";

    /** Selects and focuses the main editor pane. */
    public static final String SELECT_EDITOR = "selectEditor";

    /** Restores the default window layout. */
    public static final String RESET_WINDOWS = "resetWindows";

    /** Closes the selected editor document. */
    public static final String CLOSE_DOCUMENT = "closeDocument";

    /** Closes all open editor documents. */
    public static final String CLOSE_ALL_DOCUMENTS = "closeAllDocuments";

    /** Closes all open editor documents except the selected one. */
    public static final String CLOSE_OTHER_DOCUMENTS = "closeOtherDocuments";

    /** Shows the Find bar of the active editor (Edit menu). */
    public static final String FIND = "find";

    /** Moves to the next match of the active editor's search (Edit menu). */
    public static final String FIND_NEXT = "findNext";

    /** Moves to the previous match of the active editor's search (Edit menu). */
    public static final String FIND_PREVIOUS = "findPrevious";

    /** Searches for the active editor's selection, or the identifier at its caret (Edit menu). */
    public static final String FIND_SELECTION = "findSelection";

    /** Edit &#9656; Replace...: shows the editor's Find and Replace bars. */
    public static final String REPLACE = "replace";

    /** Opens the Options dialog (Window menu). */
    public static final String SELECT_OPTIONS = "selectOptions";

    /** Shows the Properties window (Window &gt; IDE Tools). */
    public static final String SELECT_PROPERTIES = "selectProperties";

    /** Shows and selects the Output view (Window menu). */
    public static final String SELECT_OUTPUT = "selectOutput";

    /** View menu: shows the popup listing every running task. */
    public static final String SHOW_PROGRESS = "showProgress";

    private ActionIds() {
    }
}
