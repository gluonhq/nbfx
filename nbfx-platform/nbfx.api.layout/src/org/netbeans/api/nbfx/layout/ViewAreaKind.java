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
 * Extensible classification of a logical layout area. Built-in kinds are neutral; products define
 * their own namespaced kinds.
 *
 * @since 1.0
 */
public final class ViewAreaKind {

    public static final ViewAreaKind MAIN = new ViewAreaKind("nbfx:main");
    public static final ViewAreaKind SIDE = new ViewAreaKind("nbfx:side");
    public static final ViewAreaKind BOTTOM = new ViewAreaKind("nbfx:bottom");
    public static final ViewAreaKind CANVAS = new ViewAreaKind("nbfx:canvas");
    public static final ViewAreaKind TOOL = new ViewAreaKind("nbfx:tool");
    public static final ViewAreaKind CUSTOM = new ViewAreaKind("nbfx:custom");

    private final String value;

    private ViewAreaKind(String value) {
        this.value = value;
    }

    /** Resolves a built-in short form or a namespaced product kind. */
    public static ViewAreaKind of(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "main" -> MAIN;
            case "side" -> SIDE;
            case "bottom" -> BOTTOM;
            case "canvas" -> CANVAS;
            case "tool" -> TOOL;
            case "custom" -> CUSTOM;
            default -> new ViewAreaKind(Ids.parse(value).value());
        };
    }

    /** The canonical {@code namespace:name} identity. */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ViewAreaKind kind && value.equals(kind.value);
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
