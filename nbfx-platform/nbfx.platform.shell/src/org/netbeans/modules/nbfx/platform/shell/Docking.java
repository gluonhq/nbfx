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
package org.netbeans.modules.nbfx.platform.shell;

import org.netbeans.api.nbfx.shell.PaneRole;

import org.netbeans.api.nbfx.views.DockLocation;
import org.netbeans.api.nbfx.views.ViewArea;
import org.netbeans.modules.nbfx.platform.workspace.DockArea;
import org.netbeans.modules.nbfx.platform.workspace.DockTree;
import org.netbeans.modules.nbfx.platform.workspace.WindowLayoutRegistry;
import java.util.Map;
import javafx.scene.control.TabPane;

/**
 * The legacy static docking facade. The docking state itself lives in the application-scoped
 * {@link DockRuntime}; these methods delegate to the most recently created runtime so existing
 * callers keep working while products move to instance ownership.
 * <p>
 * Every method is a no-op or returns {@code null} before {@link #create} has run, which is the same
 * contract the former static singleton had. New code should obtain the {@link DockRuntime} from its
 * shell instead of calling this facade.
 *
 * @since 1.0
 */
public final class Docking {

    /**
     * Default share of the window the navigator pane takes, next to the editor pane - the
     * {@link ViewArea#EXPLORER explorer} area's weight, which mirrors the Swing {@code explorer} mode
     * weight (0.3).
     *
     * @deprecated derive the share from {@link ViewArea#EXPLORER} instead.
     */
    @Deprecated
    public static final double DEFAULT_NAVIGATOR_SHARE = ViewArea.EXPLORER.weight();

    /**
     * Debug: whether {@link #logLayout} prints the layout at INFO after every change, while the
     * window system is being worked on. Set to {@code true} for debugging purposes.
     */
    static final boolean LOG_LAYOUT = false;

    /** The most recently created application runtime, or {@code null} before the first create. */
    private static DockRuntime active;

    private Docking() {
    }

    /**
     * The active application runtime, or {@code null} before {@link #create}. Lets the shell and
     * tests reach the instance-scoped runtime without the static getters below.
     */
    static DockRuntime runtime() {
        return active;
    }

    /**
     * Creates the application's area from a resolved composition and the concrete permanent panes,
     * replacing any previous runtime.
     *
     * @param composition the registered panes and first-run tree
     * @param primaries   the concrete panes keyed by role name
     */
    public static DockArea<TabPane> create(WindowComposition composition,
            Map<String, NbfxTabPane> primaries) {
        DockRuntime runtime = new DockRuntime(composition, primaries);
        active = runtime;
        return runtime.area();
    }

    /** The application's area, or {@code null} before it is created (or headless). */
    public static DockArea<TabPane> area() {
        return active == null ? null : active.area();
    }

    /** The declared first-run tree, or the area's default shape when no tree is declared. */
    public static DockTree defaultTree() {
        if (active != null) {
            return active.defaultTree();
        }
        return WindowLayoutRegistry.defaultTree().orElse(null);
    }

    /**
     * Releases empty docked panes retained while the initial views were being placed. Call once
     * the first-run views and any session layout have arrived.
     */
    public static void completeStartupLayout() {
        if (active != null) {
            active.completeStartupLayout();
        }
    }

    /**
     * Logs the layout after {@code what} happened, at INFO while {@link #LOG_LAYOUT} is on.
     */
    public static void logLayout(String what) {
        if (active != null) {
            active.logLayout(what);
        }
    }

    /**
     * The application's area with its sliding sides - the node the main window shows - or
     * {@code null} before the area is created.
     */
    public static SlidingArea slides() {
        return active == null ? null : active.slides();
    }

    /** The composition the shell started with, or {@code null} before the area is created. */
    public static WindowComposition composition() {
        return active == null ? null : active.composition();
    }

    /** The permanent pane playing {@code role}, or {@code null} if there is none. */
    public static NbfxTabPane primary(PaneRole role) {
        return active == null ? null : active.primary(role);
    }

    /**
     * The pane a view docked at {@code location} lives in; see {@link DockRuntime#paneAt}.
     */
    public static NbfxTabPane paneAt(DockLocation location) {
        return active == null ? null : active.paneAt(location);
    }

    /** The pane an {@link ViewArea area} lives in; see {@link DockRuntime#paneOf}. */
    public static NbfxTabPane paneOf(ViewArea area) {
        return active == null ? null : active.paneOf(area);
    }

    /** Notes that {@code pane}'s tab is on loan to the sliding panel ({@code null}: none is). */
    static void setLending(TabPane pane) {
        if (active != null) {
            active.setLending(pane);
        }
    }

    /** Whether the registered pane {@code pane} stays visible when empty. */
    public static boolean keepsWhenEmpty(TabPane pane) {
        return active != null && active.keepsWhenEmpty(pane);
    }
}