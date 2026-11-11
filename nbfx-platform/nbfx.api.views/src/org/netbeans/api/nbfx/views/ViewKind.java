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
package org.netbeans.api.nbfx.views;

import java.util.Objects;

/**
 * The kind of a registered view. A view kind groups views for the Window menu and lets products
 * filter contributions without depending on the concrete view class.
 *
 * <p>The built-in kinds cover the standard tool views. A product may define its own kind with
 * {@link #of(String)}; the value is stable and part of the view registration.</p>
 *
 * @since 1.6
 */
public final class ViewKind {

    /** A navigator docked next to the editor, for example a project or VM tree. */
    public static final String NAVIGATOR_ID = "navigator";
    /** A properties-like view docked on the right. */
    public static final String PROPERTIES_ID = "properties";
    /** An output-like view docked below the editor. */
    public static final String OUTPUT_ID = "output";
    /** A general tool view. */
    public static final String TOOL_ID = "tool";
    /** A view that does not fit the built-in kinds; also used for dynamic, unregistered views. */
    public static final String CUSTOM_ID = "custom";

    /** The navigator kind. */
    public static final ViewKind NAVIGATOR = new ViewKind(NAVIGATOR_ID);
    /** The properties kind. */
    public static final ViewKind PROPERTIES = new ViewKind(PROPERTIES_ID);
    /** The output kind. */
    public static final ViewKind OUTPUT = new ViewKind(OUTPUT_ID);
    /** The general tool kind. */
    public static final ViewKind TOOL = new ViewKind(TOOL_ID);
    /** The custom kind. */
    public static final ViewKind CUSTOM = new ViewKind(CUSTOM_ID);

    private final String id;

    private ViewKind(String id) {
        this.id = id;
    }

    /** The stable identifier written by {@link ViewRegistration#kind()}. */
    public String id() {
        return id;
    }

    /**
     * The kind for {@code id}. Unknown non-blank ids are returned as product-defined kinds; a
     * {@code null} or blank id falls back to {@link #TOOL}.
     *
     * @param id the kind id
     * @return the kind, never {@code null}
     */
    public static ViewKind of(String id) {
        if (id == null || id.isBlank()) {
            return TOOL;
        }
        return switch (id) {
            case NAVIGATOR_ID -> NAVIGATOR;
            case PROPERTIES_ID -> PROPERTIES;
            case OUTPUT_ID -> OUTPUT;
            case TOOL_ID -> TOOL;
            case CUSTOM_ID -> CUSTOM;
            default -> new ViewKind(id);
        };
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ViewKind other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
