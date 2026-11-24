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
 * Stable identity of a logical layout area. Product roles are namespaced; built-in roles also
 * accept their short forms.
 *
 * @since 1.0
 */
public final class PaneRole {

    public static final PaneRole MAIN = new PaneRole("nbfx:main");
    public static final PaneRole SIDE = new PaneRole("nbfx:side");
    public static final PaneRole BOTTOM = new PaneRole("nbfx:bottom");
    public static final PaneRole CENTER = new PaneRole("nbfx:center");

    private final String value;

    private PaneRole(String value) {
        this.value = value;
    }

    /** Resolves a built-in short/canonical form or a namespaced product role. */
    public static PaneRole of(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "main", "nbfx:main" -> MAIN;
            case "side", "nbfx:side" -> SIDE;
            case "bottom", "nbfx:bottom" -> BOTTOM;
            case "center", "nbfx:center" -> CENTER;
            default -> new PaneRole(Ids.parse(value).value());
        };
    }

    /** The canonical {@code namespace:name} identity. */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PaneRole role && value.equals(role.value);
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
