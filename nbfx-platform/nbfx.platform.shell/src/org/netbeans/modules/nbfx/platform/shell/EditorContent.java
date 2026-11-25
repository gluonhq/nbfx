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
package org.netbeans.modules.nbfx.platform.shell;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Node;
import org.netbeans.api.nbfx.content.Content;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.ContentKind;
import org.netbeans.api.nbfx.content.ContentRequirements;
import org.netbeans.api.nbfx.content.ContentState;
import org.netbeans.api.nbfx.editor.EditorContext;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Adapts an {@link EditorDocument} to the generic {@link Content} lifecycle so an editor tab can be
 * tracked by the same content manager as views. The document keeps owning its node, title, lookup
 * and save behaviour; the adapter gives it a stable identity and routes close through the editor
 * context.
 * <p>
 * Identity is stable per document instance. Clones of the same file are different documents and get
 * different identities, so they can be open at the same time.
 */
final class EditorContent implements Content {

    private static final Map<EditorDocument, ContentId> IDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicInteger SEQ = new AtomicInteger();

    private final EditorDocument document;
    private final ContentId id;
    private final String instanceKey;

    EditorContent(EditorDocument document) {
        this.document = Objects.requireNonNull(document, "document");
        this.id = idFor(document);
        this.instanceKey = instanceKey(document, id);
    }

    EditorDocument document() {
        return document;
    }

    @Override
    public ContentId id() {
        return id;
    }

    @Override
    public String instanceKey() {
        return instanceKey;
    }

    @Override
    public ContentKind kind() {
        return ContentKind.EDITOR;
    }

    @Override
    public String title() {
        return document.getTitle();
    }

    @Override
    public Node node() {
        return document.getNode();
    }

    @Override
    public Lookup getLookup() {
        return document.getLookup();
    }

    @Override
    public ContentRequirements requirements() {
        return ContentRequirements.DEFAULT;
    }

    @Override
    public boolean canClose() {
        // The tab OnCloseRequest already ran the save/discard confirmation before removal.
        return true;
    }

    @Override
    public void close() {
        EditorContext context = Lookup.getDefault().lookup(EditorContext.class);
        if (context != null && context.getDocuments().contains(document)) {
            context.unregister(document);
        }
    }

    @Override
    public ContentState saveState() {
        return ContentState.empty(id, instanceKey);
    }

    @Override
    public void dispose() {
        // The editor context unregisters and disposes the document on close.
    }

    @Override
    public String toString() {
        return "EditorContent[" + instanceKey + "]";
    }

    private static ContentId idFor(EditorDocument document) {
        synchronized (IDS) {
            return IDS.computeIfAbsent(document, d -> {
                String base = sanitize(filePath(d));
                return ContentId.of("nbfx:editor." + base + "-" + SEQ.incrementAndGet());
            });
        }
    }

    private static String instanceKey(EditorDocument document, ContentId id) {
        String path = filePath(document);
        // The id already carries a unique sequence; keep the path for readability.
        return path + "#" + id.value().substring(id.value().lastIndexOf('-') + 1);
    }

    private static String filePath(EditorDocument document) {
        FileObject file = document.getFileObject();
        return file == null ? "untitled" : file.getPath();
    }

    private static String sanitize(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "-");
        return sanitized.isEmpty() ? "document" : sanitized;
    }
}