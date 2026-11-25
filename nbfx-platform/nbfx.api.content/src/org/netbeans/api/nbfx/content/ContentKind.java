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
 * Extensible classification of runtime content. Built-in kinds are neutral; products define their
 * own namespaced kinds. A kind is not a source: files and URIs are sources that resolve to content,
 * they are never content kinds themselves.
 *
 * @since 1.0
 */
public final class ContentKind {

    public static final ContentKind EDITOR = new ContentKind("nbfx:editor");
    public static final ContentKind VIEW = new ContentKind("nbfx:view");
    public static final ContentKind OUTPUT = new ContentKind("nbfx:output");
    public static final ContentKind TERMINAL = new ContentKind("nbfx:terminal");
    public static final ContentKind CANVAS = new ContentKind("nbfx:canvas");

    private final String value;

    private ContentKind(String value) {
        this.value = value;
    }

    /** Resolves a built-in short/canonical form or a namespaced product kind. */
    public static ContentKind of(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "editor", "nbfx:editor" -> EDITOR;
            case "view", "nbfx:view" -> VIEW;
            case "output", "nbfx:output" -> OUTPUT;
            case "terminal", "nbfx:terminal" -> TERMINAL;
            case "canvas", "nbfx:canvas" -> CANVAS;
            default -> new ContentKind(ContentIds.parse(value).value());
        };
    }

    /** The canonical {@code namespace:name} identity. */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ContentKind kind && value.equals(kind.value);
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