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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.netbeans.api.nbfx.editor.ContentManager;
import org.netbeans.api.nbfx.editor.EditorContext;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.modules.nbfx.project.context.ProjectContext;
import org.netbeans.spi.nbfx.editor.DocumentRestorer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * Show History: opens the history of a project, a folder or a file in a tab of the editor area,
 * titled "Show History - name", as in NetBeans. The history of the same is opened once: asked for
 * again, its tab is brought to the front. The tab is opened again with the session.
 *
 * @since 1.4
 */
final class GitHistoryView {

    /** The id the saved History tabs are told by. */
    static final String RESTORER_ID = "git-history";

    /**
     * What a history is of.
     *
     * @param root the root of the working tree
     * @param path the file or folder in it whose changes are searched, relative to the root with
     *        {@code /} between its folders; empty for the whole repository
     * @param name what it is called in the title of the tab
     */
    record Scope(Path root, String path, String name) {

        Scope {
            Objects.requireNonNull(root);
            Objects.requireNonNull(path);
            Objects.requireNonNull(name);
        }

        /** The paths a search is for: the one of this scope, or none for the whole repository. */
        List<String> paths() {
            return path.isEmpty() ? List.of() : List.of(path);
        }

        /** This scope as the text a later session opens it again from: its three parts, a line each. */
        String saved() {
            return root + "\n" + path + "\n" + name;
        }

        /** The scope {@link #saved()} wrote, or {@code null} when {@code state} is not one. */
        static Scope ofSaved(String state) {
            String[] parts = state == null ? new String[0] : state.split("\n", 3);
            if (parts.length != 3 || parts[0].isBlank() || parts[2].isBlank()) {
                return null;
            }
            try {
                return new Scope(Path.of(parts[0]), parts[1], parts[2]);
            } catch (RuntimeException ex) {
                return null;
            }
        }

        /** The same history whatever it is called. */
        boolean sameAs(Scope other) {
            return root.equals(other.root) && path.equals(other.path);
        }
    }

    private GitHistoryView() {
    }

    /**
     * Opens the history of {@code file}: of the file, or of all in the folder. Does nothing for a
     * file in no working tree. Must run on the JavaFX Application Thread.
     */
    static void show(FileObject file) {
        File onDisk = file == null ? null : FileUtil.toFile(file);
        OpenProject project = file == null ? null : ProjectContext.projectOf(file);
        // A project is called as the IDE calls it, not as its folder.
        boolean isProject = project != null && onDisk != null && onDisk.toPath().equals(GitMenus.folderOf(project));
        open(scopeOf(onDisk, isProject ? project.getDisplayName() : null));
    }

    /** Opens the history of {@code project}. Must run on the JavaFX Application Thread. */
    static void show(OpenProject project) {
        Path folder = GitMenus.folderOf(project);
        open(scopeOf(folder == null ? null : folder.toFile(), project == null ? null : project.getDisplayName()));
    }

    /**
     * What the history of {@code file} is of: its working tree and where it is in it, called
     * {@code name}, or as the file when that is {@code null}. {@code null} for a file in no working
     * tree.
     */
    static Scope scopeOf(File file, String name) {
        Path root = file == null ? null : GitMenus.workingTreeOf(file);
        if (root == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        root.relativize(file.toPath()).forEach(part -> names.add(part.toString()));
        String path = String.join("/", names);
        return new Scope(root, path, name != null ? name : file.getName());
    }

    /** Opens the history of {@code scope}, or brings its tab to the front. Does nothing for {@code null}. */
    static void open(Scope scope) {
        ContentManager content = Lookup.getDefault().lookup(ContentManager.class);
        if (scope == null || content == null) {
            return;
        }
        EditorContext context = Lookup.getDefault().lookup(EditorContext.class);
        if (context != null) {
            for (EditorDocument open : context.getDocuments()) {
                if (open instanceof GitHistoryDocument history && history.scope().sameAs(scope)) {
                    content.openDocument(history, null);
                    return;
                }
            }
        }
        content.openDocument(document(scope), null);
    }

    /** A document for the history of {@code scope}, which searches it at once. */
    static GitHistoryDocument document(Scope scope) {
        return new GitHistoryDocument(scope, NbBundle.getMessage(GitHistoryView.class, "Git.history.title", scope.name()),
                new GitHistoryPane(scope.root(), scope.paths()));
    }

    /** Opens the History tabs of the last session again, while what they were of is still a working tree. */
    @ServiceProvider(service = DocumentRestorer.class)
    public static final class Restorer implements DocumentRestorer {

        @Override
        public String id() {
            return RESTORER_ID;
        }

        @Override
        public void restore(String state) {
            Scope scope = Scope.ofSaved(state);
            if (scope != null && scope.root().equals(GitMenus.workingTreeOf(scope.root().toFile()))) {
                open(scope);
            }
        }
    }
}
