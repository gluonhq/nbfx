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
 * Extensible classification of a shell region. Built-ins accept case-insensitive short and canonical
 * forms; product kinds require {@code namespace:name}.
 *
 * @since 1.1
 */
public record ShellRegionKind(String id) {

    public static final ShellRegionKind BRAND = new ShellRegionKind("nbfx:brand");
    public static final ShellRegionKind MENUBAR = new ShellRegionKind("nbfx:menubar");
    public static final ShellRegionKind TOOLBAR = new ShellRegionKind("nbfx:toolbar");
    public static final ShellRegionKind RIBBON = new ShellRegionKind("nbfx:ribbon");
    public static final ShellRegionKind DOCK = new ShellRegionKind("nbfx:dock");
    public static final ShellRegionKind STATUS = new ShellRegionKind("nbfx:status");
    public static final ShellRegionKind OVERLAY = new ShellRegionKind("nbfx:overlay");

    public ShellRegionKind {
        Objects.requireNonNull(id, "id");
        id = canonical(id);
    }

    /** Resolves a built-in short/canonical form or a namespaced product kind. */
    public static ShellRegionKind of(String value) {
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
            default -> new ShellRegionKind(value);
        };
    }

    /** The canonical id. */
    public String value() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }

    private static String canonical(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "brand", "nbfx:brand" -> "nbfx:brand";
            case "menubar", "nbfx:menubar" -> "nbfx:menubar";
            case "toolbar", "nbfx:toolbar" -> "nbfx:toolbar";
            case "ribbon", "nbfx:ribbon" -> "nbfx:ribbon";
            case "dock", "nbfx:dock" -> "nbfx:dock";
            case "status", "nbfx:status" -> "nbfx:status";
            case "overlay", "nbfx:overlay" -> "nbfx:overlay";
            default -> ShellIds.parse(value).value();
        };
    }
}
