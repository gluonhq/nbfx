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
package org.netbeans.modules.nbfx.ide.workbench;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import org.netbeans.api.nbfx.editor.ContentManager;
import org.netbeans.api.nbfx.editor.EditorContext;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.api.nbfx.project.ProjectRegistry;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Project-aware editor scope: the IDE contribution that maps open editor documents to the project
 * they belong to. It keeps project concepts out of the platform editor API.
 */
public final class ProjectEditorScope {

    /** Project paths resolved per document; tab-title bindings call this repeatedly. */
    private static final Map<EditorDocument, String> PROJECT_PATHS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ProjectEditorScope() {
    }

    /**
     * The path of the open project owning {@code document}, or {@code null} when it belongs to none.
     *
     * @param document the editor document
     * @return the owning project's path, or {@code null}
     */
    public static String projectPathOf(EditorDocument document) {
        if (document == null) {
            return null;
        }
        synchronized (PROJECT_PATHS) {
            if (PROJECT_PATHS.containsKey(document)) {
                return PROJECT_PATHS.get(document);
            }
        }
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        OpenProject project = registry == null ? null : registry.ownerOf(document.getFileObject());
        String path = project == null ? null : project.getPath();
        PROJECT_PATHS.put(document, path);
        return path;
    }

    /**
     * The open documents of the project rooted at {@code projectPath}, in editor-context order.
     *
     * @param projectPath the project path, or {@code null} for documents that belong to no project
     * @return an immutable snapshot of the matching documents
     */
    public static List<EditorDocument> documentsOf(String projectPath) {
        EditorContext context = Lookup.getDefault().lookup(EditorContext.class);
        if (context == null) {
            return List.of();
        }
        return context.getDocuments().stream()
                .filter(document -> Objects.equals(projectPath, projectPathOf(document)))
                .toList();
    }

    /**
     * Closes every editor tab of the files belonging to the project rooted at {@code projectPath}.
     * The file set is resolved before closing, so a project that is already being closed still
     * closes its documents.
     *
     * @param projectPath the project path, or {@code null} for documents that belong to no project
     */
    public static void closeFiles(String projectPath) {
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        if (contentManager == null) {
            return;
        }
        Set<FileObject> files = new LinkedHashSet<>();
        for (EditorDocument document : documentsOf(projectPath)) {
            files.add(document.getFileObject());
        }
        for (FileObject file : files) {
            contentManager.closeFile(file);
        }
    }
}
