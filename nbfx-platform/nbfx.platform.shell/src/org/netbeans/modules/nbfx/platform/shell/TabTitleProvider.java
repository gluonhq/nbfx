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

import java.util.List;
import org.netbeans.api.nbfx.editor.EditorDocument;

/**
 * Supplies the text shown on an editor tab and in its tooltip. A host registers an implementation
 * with {@code @ServiceProvider} to add host-specific disambiguation, for example the owning project
 * when two open files share a name.
 * <p>
 * The default implementation uses the document's plain title and file path.
 */
public interface TabTitleProvider {

    /**
     * The title for {@code document}'s tab.
     *
     * @param document      the document whose tab is being labelled
     * @param openDocuments every open document, including {@code document}
     * @return the tab title; never {@code null}
     */
    default String titleFor(EditorDocument document, List<? extends EditorDocument> openDocuments) {
        return document.getTitle();
    }

    /**
     * The tooltip for {@code document}'s tab.
     *
     * @param document the document whose tab is being labelled
     * @return the tooltip; never {@code null}
     */
    default String tooltipFor(EditorDocument document) {
        return document.getFileObject() != null
                ? document.getFileObject().getPath() : document.getTitle();
    }
}
