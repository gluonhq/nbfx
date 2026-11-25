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
package org.netbeans.api.nbfx.shell;

import java.util.Locale;
import java.util.Objects;

/**
 * Stable identity of a declarative shell chrome region. Built-ins accept case-insensitive short and
 * canonical forms; product ids require {@code namespace:name}.
 *
 * @since 1.1
 */
public record ShellRegionId(String namespace, String name) {

    public static final ShellRegionId BRAND = new ShellRegionId("nbfx", "brand");
    public static final ShellRegionId MENUBAR = new ShellRegionId("nbfx", "menubar");
    public static final ShellRegionId TOOLBAR = new ShellRegionId("nbfx", "toolbar");
    public static final ShellRegionId RIBBON = new ShellRegionId("nbfx", "ribbon");
    public static final ShellRegionId DOCK = new ShellRegionId("nbfx", "dock");
    public static final ShellRegionId STATUS = new ShellRegionId("nbfx", "status");
    public static final ShellRegionId OVERLAY = new ShellRegionId("nbfx", "overlay");

    public ShellRegionId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        ShellIds.Parts parts = ShellIds.parse(namespace + ":" + name);
        String builtIn = builtInName(parts.namespace(), parts.name());
        namespace = builtIn == null ? parts.namespace() : "nbfx";
        name = builtIn == null ? parts.name() : builtIn;
    }

    /** Resolves a built-in short/canonical form or a namespaced product region id. */
    public static ShellRegionId of(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "brand", "nbfx:brand" -> BRAND;
            case "menubar", "nbfx:menubar" -> MENUBAR;
            case "toolbar", "nbfx:toolbar" -> TOOLBAR;
            case "ribbon", "nbfx:ribbon" -> RIBBON;
            case "dock", "nbfx:dock" -> DOCK;
            case "status", "nbfx:status" -> STATUS;
            case "overlay", "nbfx:overlay" -> OVERLAY;
            default -> {
                ShellIds.Parts parts = ShellIds.parse(value);
                yield new ShellRegionId(parts.namespace(), parts.name());
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

    private static String builtInName(String namespace, String name) {
        if (!"nbfx".equalsIgnoreCase(namespace)) {
            return null;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "brand" -> "brand";
            case "menubar" -> "menubar";
            case "toolbar" -> "toolbar";
            case "ribbon" -> "ribbon";
            case "dock" -> "dock";
            case "status" -> "status";
            case "overlay" -> "overlay";
            default -> null;
        };
    }
}
