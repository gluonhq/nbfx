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

import java.util.Locale;
import java.util.Objects;

/**
 * Extensible classification of a drag-and-drop source. Built-in kinds cover the neutral sources the
 * shell itself produces; products define their own namespaced kinds.
 *
 * @since 1.3
 */
public final class TransferKind {

    public static final TransferKind CONTENT = new TransferKind("nbfx:content");
    public static final TransferKind FILES = new TransferKind("nbfx:files");
    public static final TransferKind URIS = new TransferKind("nbfx:uris");
    public static final TransferKind TEXT = new TransferKind("nbfx:text");
    public static final TransferKind NODES = new TransferKind("nbfx:nodes");

    private final String value;

    private TransferKind(String value) {
        this.value = value;
    }

    /** Resolves a built-in short/canonical form or a namespaced product kind. */
    public static TransferKind of(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "content", "nbfx:content" -> CONTENT;
            case "files", "nbfx:files" -> FILES;
            case "uris", "nbfx:uris" -> URIS;
            case "text", "nbfx:text" -> TEXT;
            case "nodes", "nbfx:nodes" -> NODES;
            default -> new TransferKind(ContentIds.parse(value).value());
        };
    }

    /** The canonical {@code namespace:name} identity. */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TransferKind kind && value.equals(kind.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}