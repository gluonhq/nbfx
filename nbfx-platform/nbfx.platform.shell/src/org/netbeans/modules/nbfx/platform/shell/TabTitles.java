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
import org.openide.util.Lookup;

/**
 * Computes the text shown on an editor tab and in its tooltip.
 * <p>
 * Delegates to the host's {@link TabTitleProvider} when one is registered. Without one, the tab
 * shows the document's plain title and the tooltip its file path.
 */
public final class TabTitles {

    /** The provider used when the host registers none: the interface defaults. */
    private static final TabTitleProvider DEFAULT = new TabTitleProvider() { };

    /** The host's provider; the result caches the instances so labelling a tab does no lookup. */
    private static final Lookup.Result<TabTitleProvider> PROVIDERS =
            Lookup.getDefault().lookupResult(TabTitleProvider.class);

    private TabTitles() {
    }

    /**
     * The title for {@code document}'s tab.
     *
     * @param document      the document whose tab is being labelled
     * @param openDocuments every open document, including {@code document}
     */
    public static String titleFor(EditorDocument document, List<? extends EditorDocument> openDocuments) {
        return provider().titleFor(document, openDocuments);
    }

    /**
     * The tooltip for {@code document}'s tab.
     *
     * @param document the document whose tab is being labelled
     */
    public static String tooltipFor(EditorDocument document) {
        return provider().tooltipFor(document);
    }

    /** The host's provider, or the interface defaults when none is registered. */
    private static TabTitleProvider provider() {
        for (TabTitleProvider provider : PROVIDERS.allInstances()) {
            return provider;
        }
        return DEFAULT;
    }
}
