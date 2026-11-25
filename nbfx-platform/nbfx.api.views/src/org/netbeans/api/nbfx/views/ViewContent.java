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
package org.netbeans.api.nbfx.views;

import java.util.Objects;
import javafx.scene.Node;
import org.netbeans.api.nbfx.content.Content;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.ContentKind;
import org.netbeans.api.nbfx.content.ContentRequirements;
import org.netbeans.api.nbfx.content.ContentState;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.openide.util.Lookup;

/**
 * Adapts a {@link ViewProvider} to the generic {@link Content} lifecycle, so a view can be opened,
 * closed and observed through the same content manager as editors and other content. The wrapped
 * view stays the owner of the node, title and lookup; the adapter only gives it the neutral identity
 * and lifecycle the manager needs.
 * <p>
 * The adapter is stateless: the same view may be adapted more than once and yields equal identities.
 * A view has no persisted payload of its own, so its {@linkplain #saveState() state} is an empty
 * payload under its identity and instance key.
 *
 * @since 1.10
 */
public final class ViewContent implements Content {

    private final ViewProvider view;

    /**
     * Wraps {@code view} as content.
     *
     * @param view the view provider
     */
    public ViewContent(ViewProvider view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    /** The wrapped view. */
    public ViewProvider view() {
        return view;
    }

    @Override
    public ContentId id() {
        return contentId(instanceKey());
    }

    @Override
    public String instanceKey() {
        String id = view.getId();
        return id == null || id.isBlank() ? "view" : id;
    }

    @Override
    public ContentKind kind() {
        return ContentKind.VIEW;
    }

    @Override
    public String title() {
        return view.getTitle();
    }

    @Override
    public Node node() {
        return view.getView();
    }

    @Override
    public Lookup getLookup() {
        return view.getLookup();
    }

    @Override
    public ContentRequirements requirements() {
        return ContentRequirements.DEFAULT;
    }

    @Override
    public boolean canClose() {
        return true;
    }

    @Override
    public void close() {
        view.viewClosed();
    }

    @Override
    public ContentState saveState() {
        return ContentState.empty(id(), instanceKey());
    }

    @Override
    public void dispose() {
        // The view owns its resources; the adapter adds none.
    }

    @Override
    public String toString() {
        return "ViewContent[" + instanceKey() + "]";
    }

    /**
     * The content identity for a view id. Characters outside the id grammar become {@code -}, so any
     * view id maps deterministically to a valid namespaced content id.
     *
     * @param viewId the view id
     * @return the content identity
     */
    static ContentId contentId(String viewId) {
        String name = "view." + viewId.replaceAll("[^A-Za-z0-9._-]", "-");
        return ContentId.of("nbfx:" + name);
    }
}