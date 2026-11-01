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
package org.netbeans.modules.nbfx.ide.actions;

/**
 * Stable identifiers for the IDE-only built-in commands. Platform/generic command ids live in
 * {@link org.netbeans.api.nbfx.actions.ActionIds}.
 */
public final class IdeActionIds {

    /** Creates a new project. */
    public static final String NEW_PROJECT = "newProject";

    /** Creates a new file from a template. */
    public static final String NEW_FILE = "newFile";

    /** Opens an existing project. */
    public static final String OPEN_PROJECT = "openProject";

    /** Closes the currently selected project without closing the application. */
    public static final String CLOSE_PROJECT = "closeProject";

    /** Closes every open project without closing the application. */
    public static final String CLOSE_ALL_PROJECTS = "closeAllProjects";

    /** Selects the project after the selected one, wrapping around at the end of the list. */
    public static final String NEXT_PROJECT = "nextProject";

    /** Selects the project before the selected one, wrapping around at the start of the list. */
    public static final String PREVIOUS_PROJECT = "previousProject";

    /** Saves the modified documents of the selected project. */
    public static final String SAVE_PROJECT = "saveProject";

    /** Copies the files selected in the navigator to the clipboard. */
    public static final String FILE_COPY = "file.copy";

    /** Cuts the files selected in the navigator to the clipboard. */
    public static final String FILE_CUT = "file.cut";

    /** Pastes the clipboard files into the folder selected in the navigator. */
    public static final String FILE_PASTE = "file.paste";

    /** Undoes the last file operation. */
    public static final String FILE_UNDO = "file.undo";

    /** Redoes the last undone file operation. */
    public static final String FILE_REDO = "file.redo";

    /** Find... on a folder of the Projects view: opens the search dialog scoped to the selected folders. */
    public static final String FILE_FIND = "file.find";

    /** Shows/selects the Projects navigator tab. */
    public static final String SELECT_PROJECTS = "selectProjects";

    /** Shows/selects the Files navigator tab. */
    public static final String SELECT_FILES = "selectFiles";

    /** Navigate &#9656; Select in Projects: selects the file of the active editor in the Projects view. */
    public static final String SELECT_IN_PROJECTS = "selectInProjects";

    /** Navigate &#9656; Select in Files: selects the file of the active editor in the Files view. */
    public static final String SELECT_IN_FILES = "selectInFiles";

    /** Shows and selects the Usages view (Window menu). */
    public static final String SELECT_USAGES = "selectUsages";

    /** Shows and selects the Search Results view (Window menu). */
    public static final String SELECT_SEARCH_RESULTS = "selectSearchResults";

    /** Shows and selects the Git view (Window menu). */
    public static final String SELECT_GIT = "selectGit";

    /** Shows and selects the Plugins view (Window menu). */
    public static final String SELECT_PLUGINS = "selectPlugins";

    /** Shows the Navigator window (the Structure window, Window &gt; Navigator). */
    public static final String SELECT_NAVIGATOR = "selectNavigator";

    /** Finds the usages of the element at the caret of the active editor (Edit menu, editor context menu). */
    public static final String FIND_USAGES = "findUsages";

    /** Edit &#9656; Find in Projects...: opens the project-wide search dialog. */
    public static final String FIND_IN_PROJECTS = "findInProjects";

    /** Edit &#9656; Replace in Projects...: opens the project-wide search dialog in replace mode. */
    public static final String REPLACE_IN_PROJECTS = "replaceInProjects";

    /** Navigate &#9656; Inspect &#9656; Members: shows the members of the type at the caret. */
    public static final String INSPECT_MEMBERS = "inspectMembers";

    /** Navigate &#9656; Inspect &#9656; File Members: shows the members of the active editor's file. */
    public static final String INSPECT_FILE_MEMBERS = "inspectFileMembers";

    /** Navigate &#9656; Go to Source: opens the source of the element at the caret. */
    public static final String GO_TO_SOURCE = "goToSource";

    /** Navigate &#9656; Go to Declaration: opens the declaration of the element at the caret. */
    public static final String GO_TO_DECLARATION = "goToDeclaration";

    /** Navigate &#9656; Go to Super Implementation: opens the overridden method or supertype. */
    public static final String GO_TO_SUPER_IMPLEMENTATION = "goToSuperImplementation";

    /** Navigate &#9656; Go to Implementation: lists the subtypes or overriding methods. */
    public static final String GO_TO_IMPLEMENTATION = "goToImplementation";

    /** Builds the selected project with its build tool. */
    public static final String BUILD = "build";

    /** Cleans and then builds the selected project. */
    public static final String CLEAN_BUILD = "cleanBuild";

    /** Cleans the selected project's build output. */
    public static final String CLEAN = "clean";

    /** Runs the selected project's tests. */
    public static final String TEST = "test";

    /** Runs the selected project. */
    public static final String RUN = "run";

    /** Generates the selected project's javadoc. */
    public static final String JAVADOC = "javadoc";

    /** Debugs the active file. */
    public static final String DEBUG_FILE = "debugFile";

    /** Toggles a breakpoint on the active editor's current line. */
    public static final String TOGGLE_BREAKPOINT = "toggleBreakpoint";

    /** Resumes the running debug session. */
    public static final String DEBUG_CONTINUE = "debugContinue";

    /** Steps over in the running debug session. */
    public static final String DEBUG_STEP_OVER = "debugStepOver";

    /** Steps into in the running debug session. */
    public static final String DEBUG_STEP_INTO = "debugStepInto";

    /** Steps out in the running debug session. */
    public static final String DEBUG_STEP_OUT = "debugStepOut";

    /** Stops the running debug session. */
    public static final String DEBUG_STOP = "debugStop";

    private IdeActionIds() {
    }
}
