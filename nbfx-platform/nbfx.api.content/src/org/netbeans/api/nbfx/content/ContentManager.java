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

import java.util.List;
import java.util.Optional;
import javafx.beans.value.ObservableValue;

/**
 * The public content lifecycle and routing seam. One manager belongs to one shell instance; it owns
 * content close and dispose, and hosts never destroy content themselves.
 * <p>
 * Opening from a file, URI or other source needs the source vocabulary and lands with that slice; the
 * manager already accepts ready-made content, so editors and views share one lifecycle today.
 *
 * @since 1.2
 */
public interface ContentManager {

    /**
     * Registers already-created content and makes it the active content when nothing else is open.
     * Content of an identity that is already open is not added twice.
     *
     * @param content the content to open
     */
    void open(Content content);

    /**
     * Closes {@code content}. A content that answers {@link Content#canClose() canClose()} with
     * {@code false} is left open; otherwise the manager closes and disposes it and removes it from
     * the open set. Unknown content is ignored.
     *
     * @param content the content to close
     */
    void close(Content content);

    /**
     * The open content with {@code id}, if any.
     *
     * @param id the content identity
     * @return the content, or empty
     */
    Optional<Content> find(ContentId id);

    /**
     * The open content in open order as an unmodifiable snapshot.
     *
     * @return the open content, never {@code null}
     */
    List<Content> openContent();

    /**
     * The active content, or {@code null} when nothing is open. The value changes as content is
     * opened, closed or selected.
     *
     * @return the observable active content
     */
    ObservableValue<Content> activeContent();
}