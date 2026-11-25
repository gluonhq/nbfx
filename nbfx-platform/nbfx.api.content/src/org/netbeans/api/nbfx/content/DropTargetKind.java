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
 * Extensible classification of a drop target. The built-in kinds follow the neutral routing order -
 * content, overlay, group, area, window, global fallback - and products define their own namespaced
 * kinds.
 *
 * @since 1.4
 */
public final class DropTargetKind {

    public static final DropTargetKind CONTENT = new DropTargetKind("nbfx:content");
    public static final DropTargetKind OVERLAY = new DropTargetKind("nbfx:overlay");
    public static final DropTargetKind GROUP = new DropTargetKind("nbfx:group");
    public static final DropTargetKind AREA = new DropTargetKind("nbfx:area");
    public static final DropTargetKind WINDOW = new DropTargetKind("nbfx:window");
    public static final DropTargetKind GLOBAL = new DropTargetKind("nbfx:global");

    private final String value;

    private DropTargetKind(String value) {
        this.value = value;
    }

    /** Resolves a built-in short/canonical form or a namespaced product kind. */
    public static DropTargetKind of(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "content", "nbfx:content" -> CONTENT;
            case "overlay", "nbfx:overlay" -> OVERLAY;
            case "group", "nbfx:group" -> GROUP;
            case "area", "nbfx:area" -> AREA;
            case "window", "nbfx:window" -> WINDOW;
            case "global", "nbfx:global" -> GLOBAL;
            default -> new DropTargetKind(ContentIds.parse(value).value());
        };
    }

    /** The canonical {@code namespace:name} identity. */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DropTargetKind kind && value.equals(kind.value);
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