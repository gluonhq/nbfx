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

import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane.PaneRole;

import org.netbeans.api.nbfx.views.DockLocation;
import org.netbeans.api.nbfx.views.ViewArea;
import org.netbeans.modules.nbfx.platform.shell.spi.WindowPane;
import org.netbeans.modules.nbfx.platform.workspace.DockArea;
import org.netbeans.modules.nbfx.platform.workspace.DockHost;
import org.netbeans.modules.nbfx.platform.workspace.DockTree;
import org.netbeans.modules.nbfx.platform.workspace.DockTrees;
import org.netbeans.modules.nbfx.platform.workspace.ViewAreaRegistry;
import org.netbeans.modules.nbfx.platform.workspace.WindowLayoutRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.logging.Logger;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Side;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * The main window's {@link DockArea} and how it is bound to the application: its permanent panes are
 * the {@link PaneRole#NAVIGATOR} and {@link PaneRole#MAIN} {@link NbfxTabPane}s, keyed by their role
 * names in the persisted {@code DockTree}s, and the panes it docks next to them are
 * {@link PaneRole#DOCKED} ones. There is one area per JVM, so the shell is one per JVM; creating a
 * second area replaces the first.
 */
public final class Docking implements DockHost<TabPane> {

    /**
     * Default share of the window the navigator pane takes, next to the editor pane - the
     * {@link ViewArea#EXPLORER explorer} area's weight, which mirrors the Swing {@code explorer} mode
     * weight (0.3).
     *
     * @deprecated derive the share from {@link ViewArea#EXPLORER} instead.
     */
    @Deprecated
    public static final double DEFAULT_NAVIGATOR_SHARE = ViewArea.EXPLORER.weight();

    private static final Logger LOG = Logger.getLogger(Docking.class.getName());

    /**
     * Debug: whether {@link #logLayout} prints the layout at INFO after every change, while the
     * window system is being worked on. Set to {@code true} for debugging purposes.
     */
    static final boolean LOG_LAYOUT = false;

    private static DockArea<TabPane> area;
    /** The composition the shell started with; runtime placement resolves against it. */
    private static WindowComposition composition;
    /** The area with the sliding sides around it: what the window shows. */
    private static SlidingArea slides;
    /**
     * The minimized pane whose tab is on loan to the sliding panel, if any. Such a pane is not
     * empty though its tab list may be, so the area must not dispose of it meanwhile.
     */
    private static final ObjectProperty<TabPane> lending = new SimpleObjectProperty<>();

    private Docking() {}

    /**
     * Creates the application's area from a resolved composition and the concrete permanent panes,
     * replacing any previous one.
     *
     * @param composition the registered panes and first-run tree
     * @param primaries   the concrete panes keyed by role name
     */
    public static DockArea<TabPane> create(WindowComposition composition,
            Map<String, NbfxTabPane> primaries) {
        Docking.composition = composition;
        SequencedMap<String, TabPane> panes = new LinkedHashMap<>();
        for (Map.Entry<String, NbfxTabPane> entry : primaries.entrySet()) {
            PaneRole role = NbfxTabPane.roleOf(entry.getValue());
            if (!role.isPermanent()) {
                throw new IllegalArgumentException("Not a permanent pane: " + role);
            }
            panes.put(role.name(), entry.getValue());
        }
        double explorerWeight = ViewAreaRegistry.find("explorer")
                .map(ViewArea::weight).orElse(ViewArea.EXPLORER.weight());
        List<Double> dividers = panes.size() == 2 ? List.of(explorerWeight) : List.of();
        area = new DockArea<>(new Docking(), panes, dividers);
        // Keep the declared tree's empty docked leaves alive until the initial views arrive.
        area.retainEmptyDockedPanes(true);
        if (composition != null && composition.tree() != null) {
            DockTree tree = composition.tree();
            if (area.accepts(tree)) {
                area.applyTree(tree);
            } else {
                LOG.warning("Ignoring window layout with unknown permanent pane roles: "
                        + DockTrees.format(tree));
            }
        }
        slides = new SlidingArea(area);
        return area;
    }

    /** The application's area, or {@code null} before it is created (or headless). */
    public static DockArea<TabPane> area() {
        return area;
    }

    /** The declared first-run tree, or the area's default shape when no tree is declared. */
    public static DockTree defaultTree() {
        WindowComposition current = composition;
        if (current != null && current.tree() != null) {
            return current.tree();
        }
        return WindowLayoutRegistry.defaultTree()
                .orElseGet(() -> area == null ? null : area.defaultTree());
    }

    /**
     * Releases empty docked panes retained while the initial views were being placed. Call once
     * the first-run views and any session layout have arrived.
     */
    public static void completeStartupLayout() {
        if (area != null) {
            area.retainEmptyDockedPanes(false);
        }
    }

    /**
     * Logs the layout after {@code what} happened, at INFO while {@link #LOG_LAYOUT} is on: the
     * area's tree with each pane's role, kind and tab titles, the maximized pane, and the minimized
     * panes with their sides.
     */
    public static void logLayout(String what) {
        if (!LOG_LAYOUT || area == null) {
            return;
        }
        StringBuilder text = new StringBuilder("Layout after ").append(what).append(":\n");
        text.append(area.printTree(Docking::describe));
        if (area.isMaximized()) {
            text.append("maximized: ").append(describe(area.maximizedPane())).append('\n');
        }
        for (TabPane minimized : area.minimizedPanes()) {
            text.append("minimized ").append(slides == null ? "" : slides.sideOf(minimized)).append(": ")
                    .append(describe(minimized)).append('\n');
        }
        LOG.info(text.toString());
    }

    /** {@code pane} as {@code ROLE/KIND [tab titles]}, a slid-in tab counted with its group. */
    private static String describe(TabPane pane) {
        List<String> titles = new ArrayList<>();
        List<Tab> tabs = slides != null && slides.isMinimized(pane) ? slides.tabsOf(pane) : pane.getTabs();
        for (Tab tab : tabs) {
            titles.add(NbfxTabPane.titleOf(tab));
        }
        return NbfxTabPane.roleOf(pane) + "/" + NbfxTabPane.kindOf(pane) + " " + titles;
    }

    /**
     * The application's area with its sliding sides - the node the main window shows, and what
     * minimizes panes to those sides - or {@code null} before the area is created.
     */
    public static SlidingArea slides() {
        return slides;
    }

    /** The composition the shell started with, or {@code null} before the area is created. */
    public static WindowComposition composition() {
        return composition;
    }

    /** The permanent pane playing {@code role} in the application's area, or {@code null} if there is none. */
    public static NbfxTabPane primary(PaneRole role) {
        return area == null ? null : (NbfxTabPane) area.primary(role.name());
    }

    /**
     * The pane a view docked at {@code location} lives in: a permanent pane, the docked pane right
     * below it, or the one along the bottom or the right of the whole area - the docked panes being
     * created if there is none yet (a pane created here must receive a tab before the next pulse, or
     * it removes itself again). Returns {@code null} without an area, or when the location's permanent
     * pane is not in it. Map an area to its pane with {@link #paneOf(ViewArea) paneOf}. While the
     * navigator pane is minimized there is nothing below it: a view bound for there joins the
     * navigator pane's minimized group instead.
     */
    public static NbfxTabPane paneAt(DockLocation location) {
        if (area == null) {
            return null;
        }
        // A registered area at this location wins over the fixed legacy mapping; a product can
        // therefore move the meaning of LEFT/RIGHT without changing Docking.
        NbfxTabPane declared = ViewAreaRegistry.defaultFor(location)
                .map(Docking::registeredPaneFor)
                .orElse(null);
        if (declared != null) {
            return declared;
        }
        TabPane pane = switch (location) {
            case LEFT -> primary(PaneRole.NAVIGATOR);
            case CENTER -> primary(PaneRole.MAIN);
            case LEFT_BOTTOM -> {
                NbfxTabPane navigator = primary(PaneRole.NAVIGATOR);
                yield navigator != null && area.isMinimized(navigator) ? navigator : area.paneBelow(navigator);
            }
            case CENTER_BOTTOM -> area.paneBelow(primary(PaneRole.MAIN));
            case BOTTOM -> area.edgePane(Side.BOTTOM);
            case RIGHT -> area.edgePane(Side.RIGHT);
        };
        return (NbfxTabPane) pane;
    }

    /** The pane an {@link ViewArea area} lives in, using its declared pane role when available. */
    public static NbfxTabPane paneOf(ViewArea area) {
        NbfxTabPane declared = registeredPaneFor(area);
        return declared != null ? declared : paneAt(area.location());
    }

    /** The registered pane hosting {@code area}, or {@code null} when no pane declares it. */
    private static NbfxTabPane registeredPaneFor(ViewArea area) {
        WindowComposition current = composition;
        Optional<WindowPane> registered = current == null
                ? WindowPaneRegistry.areaPane(area.id())
                : current.paneForArea(area.id());
        return registered.map(pane -> primary(pane.role())).orElse(null);
    }

    @Override
    public TabPane createDockedPane() {
        return new NbfxTabPane(PaneRole.DOCKED);
    }

    /**
     * A pane split off an editor pane (a drop on its edge, New Document Tab Group) is an editor pane;
     * one split off a view pane, docked along the area, or created for a dock location (Output below
     * the editors, through {@link #paneAt}) is a view pane.
     */
    @Override
    public TabPane createDockedPane(TabPane beside) {
        PaneKind kind = beside != null && NbfxTabPane.kindOf(beside) == PaneKind.EDITOR ? PaneKind.EDITOR : PaneKind.VIEW;
        return new NbfxTabPane(PaneRole.DOCKED, kind);
    }

    @Override
    public void disposeDockedPane(TabPane pane) {
        NbfxTabPane.unregister(pane);
    }

    /** Notes that {@code pane}'s tab is on loan to the sliding panel ({@code null}: none is). */
    static void setLending(TabPane pane) {
        lending.set(pane);
    }

    /** Whether the registered pane {@code pane} stays visible when empty. */
    public static boolean keepsWhenEmpty(TabPane pane) {
        WindowComposition current = composition;
        String role = NbfxTabPane.roleOf(pane).name();
        Optional<WindowPane> registered = current == null
                ? WindowPaneRegistry.find(role)
                : current.pane(role);
        return registered.map(WindowPane::keepWhenEmpty).orElse(false);
    }

    @Override
    public boolean keepWhenEmpty(TabPane pane) {
        return keepsWhenEmpty(pane);
    }

    /** A pane is empty when it holds no tab and none of its tabs is slid in from a sliding side. */
    @Override
    public ObservableValue<Boolean> isEmpty(TabPane pane) {
        return Bindings.createBooleanBinding(() -> pane.getTabs().isEmpty() && lending.get() != pane,
                pane.getTabs(), lending);
    }

    /** The editor pane's area is made of editor panes: those an emptied editor pane takes over, and what Output goes below. */
    @Override
    public boolean belongsToMainArea(TabPane pane) {
        return NbfxTabPane.kindOf(pane) == PaneKind.EDITOR;
    }

    @Override
    public void moveContent(TabPane from, TabPane into) {
        for (Tab tab : List.copyOf(from.getTabs())) {
            NbfxTabPane.moveTab(into, tab, into.getTabs().size());
        }
    }
}
