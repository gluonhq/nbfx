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
package org.netbeans.api.nbfx.layout;

import java.util.Locale;
import java.util.Objects;

/**
 * Extensible identity of a dock-group presentation. Built-in {@code nbfx} names are canonicalized
 * case-insensitively; product namespaces remain case-sensitive.
 */
public record PresentationId(String namespace, String name) {

    public static final PresentationId TABBED = new PresentationId("nbfx", "tabbed");
    public static final PresentationId SINGLE = new PresentationId("nbfx", "single");
    public static final PresentationId CANVAS = new PresentationId("nbfx", "canvas");
    public static final PresentationId OVERLAY = new PresentationId("nbfx", "overlay");
    public static final PresentationId HEADERLESS = new PresentationId("nbfx", "headerless");

    public PresentationId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        Ids.Parts parts = Ids.parse(namespace + ":" + name);
        String builtInName = builtInName(parts.namespace(), parts.name());
        namespace = builtInName == null ? parts.namespace() : "nbfx";
        name = builtInName == null ? parts.name() : builtInName;
    }

    private static String builtInName(String namespace, String name) {
        if (!"nbfx".equalsIgnoreCase(namespace)) {
            return null;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "tabbed" -> "tabbed";
            case "single" -> "single";
            case "canvas" -> "canvas";
            case "overlay" -> "overlay";
            case "headerless" -> "headerless";
            default -> null;
        };
    }

    /** Resolves a built-in short/canonical form or a namespaced product presentation. */
    public static PresentationId of(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "tabbed", "nbfx:tabbed" -> TABBED;
            case "single", "nbfx:single" -> SINGLE;
            case "canvas", "nbfx:canvas" -> CANVAS;
            case "overlay", "nbfx:overlay" -> OVERLAY;
            case "headerless", "nbfx:headerless" -> HEADERLESS;
            default -> {
                Ids.Parts parts = Ids.parse(value);
                yield new PresentationId(parts.namespace(), parts.name());
            }
        };
    }

    /** The canonical {@code namespace:name} identity. */
    public String value() {
        return namespace + ":" + name;
    }

    @Override
    public String toString() {
        return value();
    }
}
