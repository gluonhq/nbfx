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
package org.netbeans.api.nbfx.content;

import javafx.scene.Node;
import org.openide.util.Lookup;

/**
 * One piece of runtime content - an editor buffer, a view, an output console, a canvas - with a
 * stable identity, a stable scene-graph node and a lifecycle the content manager drives.
 * <p>
 * {@link #node()} is stable across moves, theme changes, float/redock and presentation changes: a
 * host reparents the same node rather than recreating it. Content never closes or disposes itself on
 * removal from a host; {@link ContentManager} owns close and dispose. A closed content may still be
 * queried for its {@linkplain #saveState() state} before it is disposed.
 *
 * @since 1.1
 */
public interface Content extends Lookup.Provider, AutoCloseable {

    /** The stable identity of this content. */
    ContentId id();

    /**
     * The stable per-identity instance key, so two documents of the same kind stay distinct in a
     * store keyed by identity and instance key.
     */
    String instanceKey();

    /** The content classification. */
    ContentKind kind();

    /** The user-visible title, already localized by the content. */
    String title();

    /** The stable scene-graph node; hosts reparent it and never recreate it on a move. */
    Node node();

    /** What this content asks of its host and its store. */
    ContentRequirements requirements();

    /**
     * Whether a user close request may proceed now. A dirty editor returns {@code false}; the close
     * request is then rejected rather than forced.
     */
    boolean canClose();

    /**
     * Handles a user close request. The manager calls this only after {@link #canClose()} returned
     * {@code true}; it releases the content's editing resources but not the state needed by
     * {@link #saveState()}.
     */
    @Override
    void close();

    /** Captures the state needed to recreate this content; never {@code null}. */
    ContentState saveState();

    /**
     * Releases the content's resources for good. The manager calls this after the last host detached
     * the content; the node must not be used afterwards.
     */
    void dispose();
}