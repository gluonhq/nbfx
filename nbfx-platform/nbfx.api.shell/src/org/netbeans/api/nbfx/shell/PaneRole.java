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

import org.netbeans.api.nbfx.views.PaneKind;

/**
 * The role a pane plays in the window layout. Tabs of either kind (editor or view) may be dragged
 * into any pane, so the role describes the <em>pane</em>, never the tabs it holds. It is the only
 * stable identity a pane has and is what layout persistence is keyed on.
 * <p>
 * The built-in roles are canonical singletons. Product roles are obtained with {@link #of(String)}
 * and compared by their stable name, so a persisted layout survives a module reload.
 *
 * @since 1.0
 */
public final class PaneRole {

    /** The docked navigator pane in the main window; exactly one exists. */
    public static final PaneRole NAVIGATOR = new PaneRole("NAVIGATOR", true);
    /** The docked editor pane in the main window; exactly one exists. */
    public static final PaneRole MAIN = new PaneRole("MAIN", true);
    /** A pane docked in the main window next to the permanent panes; exists only while it holds a tab. */
    public static final PaneRole DOCKED = new PaneRole("DOCKED", false);
    /** A pane living in its own detached window; any number may exist. */
    public static final PaneRole DETACHED = new PaneRole("DETACHED", false);
    /**
     * The pane a tab of a minimized pane slides in to; exactly one exists, holding the slid-in tab
     * on loan from its group, and it is never persisted.
     */
    public static final PaneRole SLIDE = new PaneRole("SLIDE", false);

    private final String name;
    private final boolean permanent;

    private PaneRole(String name, boolean permanent) {
        this.name = name;
        this.permanent = permanent;
    }

    /** The stable name used in persisted layouts and layer roles. */
    public String name() {
        return name;
    }

    /** Whether a pane with this role exists for the whole session, even empty. */
    public boolean isPermanent() {
        return permanent;
    }

    /** Whether this is the main editor pane. */
    public boolean isMain() {
        return this == MAIN;
    }

    /** The kind a new pane with this role has: the main pane edits, every other hosts views. */
    public PaneKind defaultKind() {
        return isMain() ? PaneKind.EDITOR : PaneKind.VIEW;
    }

    /**
     * Resolves a stable role name; unknown names become product roles.
     *
     * @param name the stable role name
     * @return the canonical role
     * @throws IllegalArgumentException if {@code name} is null or not a word
     */
    public static PaneRole of(String name) {
        if (name == null || !name.matches("\\w+")) {
            throw new IllegalArgumentException("Invalid pane role name: " + name);
        }
        return switch (name) {
            case "MAIN" -> MAIN;
            case "NAVIGATOR" -> NAVIGATOR;
            case "DOCKED" -> DOCKED;
            case "DETACHED" -> DETACHED;
            case "SLIDE" -> SLIDE;
            default -> new PaneRole(name, true);
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PaneRole role && name.equals(role.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
