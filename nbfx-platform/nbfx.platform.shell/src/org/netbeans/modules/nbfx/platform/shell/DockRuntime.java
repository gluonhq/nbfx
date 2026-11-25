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
import org.netbeans.api.nbfx.views.PaneKind;

import org.netbeans.api.nbfx.views.DockLocation;
import org.netbeans.api.nbfx.views.ViewArea;
import org.netbeans.spi.nbfx.shell.WindowPane;
import org.netbeans.modules.nbfx.platform.workspace.DockArea;
import org.netbeans.modules.nbfx.platform.workspace.DockHost;
import org.netbeans.modules.nbfx.platform.workspace.DockTree;
import org.netbeans.modules.nbfx.platform.workspace.DockTrees;
import org.netbeans.api.nbfx.views.ViewAreaRegistry;
import org.netbeans.modules.nbfx.platform.workspace.WindowLayoutRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
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
 * The application-scoped docking runtime: the main window's {@link DockArea} and how it is bound to
 * the application, its {@link SlidingArea}, the composition it was built from and the pane/area
 * lookup tables. One runtime belongs to one shell instance, so a test or a second application
 * instance can build an isolated runtime instead of mutating a process-wide singleton.
 * <p>
 * The runtime is the {@link DockHost} of its area: it creates, disposes and moves panes and tells
 * when one is empty. The legacy static {@link Docking} facade delegates to the most recently
 * created runtime, so existing callers keep working while products move to instance ownership.
 *
 * @since 1.5
 */
public final class DockRuntime implements DockHost<TabPane> {

    private static final Logger LOG = Logger.getLogger(DockRuntime.class.getName());

    private DockArea<TabPane> area;
    private WindowComposition composition;
    /** The pane hosting each registered view area, resolved from the composition once. */
    private Map<String, NbfxTabPane> areaPanes = Map.of();
    /** The default area of each docking location, snapshotted from the registry once. */
    private Map<DockLocation, ViewArea> locationAreas = Map.of();
    private SlidingArea slides;
    /**
     * The minimized pane whose tab is on loan to the sliding panel, if any. Such a pane is not
     * empty though its tab list may be, so the area must not dispose of it meanwhile.
     */
    private final ObjectProperty<TabPane> lending = new SimpleObjectProperty<>();

    /**
     * Creates the runtime and its area from a resolved composition and the concrete permanent
     * panes.
     *
     * @param composition the registered panes and first-run tree
     * @param primaries   the concrete panes keyed by role name
     */
    public DockRuntime(WindowComposition composition, Map<String, NbfxTabPane> primaries) {
        this.composition = composition;
        SequencedMap<String, TabPane> panes = new LinkedHashMap<>();
        for (Map.Entry<String, NbfxTabPane> entry : primaries.entrySet()) {
            PaneRole role = PaneRole.of(entry.getKey());
            if (!role.isPermanent()) {
                throw new IllegalArgumentException("Not a permanent pane: " + role);
            }
            panes.put(entry.getKey(), entry.getValue());
        }
        SequencedMap<String, TabPane> ordered = orderByComposition(composition, panes);
        double explorerWeight = ViewAreaRegistry.find("explorer")
                .map(ViewArea::weight).orElse(ViewArea.EXPLORER.weight());
        List<Double> dividers = ordered.size() == 2 ? List.of(explorerWeight) : List.of();
        String mainRole = ordered.containsKey(PaneRole.MAIN.name())
                ? PaneRole.MAIN.name() : ordered.lastEntry().getKey();
        area = new DockArea<>(this, ordered, dividers, mainRole);
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
        if (composition != null) {
            for (WindowPane registered : composition.panes()) {
                if (registered.keepWhenEmpty()) {
                    NbfxTabPane pane = primary(registered.role());
                    if (pane != null && area.isPrimaryHidden(pane)) {
                        area.showPrimary(pane);
                    }
                }
            }
        }
        Map<String, NbfxTabPane> byArea = new HashMap<>();
        Map<DockLocation, ViewArea> byLocation = new EnumMap<>(DockLocation.class);
        if (composition != null) {
            for (WindowPane registered : composition.panes()) {
                if (!registered.area().isBlank()) {
                    TabPane pane = ordered.get(registered.role().name());
                    if (pane instanceof NbfxTabPane nbfx) {
                        byArea.putIfAbsent(registered.area(), nbfx);
                    }
                }
            }
        }
        for (ViewArea viewArea : ViewAreaRegistry.discover()) {
            byLocation.putIfAbsent(viewArea.location(), viewArea);
        }
        areaPanes = Map.copyOf(byArea);
        locationAreas = Map.copyOf(byLocation);
        slides = new SlidingArea(area);
    }

    /**
     * Orders the concrete panes by the declared composition tree's permanent leaves, then appends
     * any registered pane the tree does not mention. The main role is passed explicitly to the
     * area, so this order does not decide which pane is main.
     */
    private static SequencedMap<String, TabPane> orderByComposition(
            WindowComposition composition, SequencedMap<String, TabPane> panes) {
        if (composition == null || composition.tree() == null) {
            return panes;
        }
        SequencedMap<String, TabPane> ordered = new LinkedHashMap<>();
        for (DockTree.Leaf leaf : composition.tree().leaves()) {
            if (leaf.isPrimary() && panes.containsKey(leaf.primary())) {
                ordered.put(leaf.primary(), panes.get(leaf.primary()));
            }
        }
        for (Map.Entry<String, TabPane> entry : panes.entrySet()) {
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    /** The runtime's area, or {@code null} after {@link #dispose()}. */
    public DockArea<TabPane> area() {
        return area;
    }

    /** The composition the runtime was created from. */
    public WindowComposition composition() {
        return composition;
    }

    /** The runtime's area with its sliding sides - what the window shows. */
    public SlidingArea slides() {
        return slides;
    }

    /** The declared first-run tree, or the area's default shape when no tree is declared. */
    public DockTree defaultTree() {
        WindowComposition current = composition;
        if (current != null && current.tree() != null) {
            return current.tree();
        }
        return WindowLayoutRegistry.defaultTree()
                .orElseGet(() -> area == null ? null : area.defaultTree());
    }

    /** Releases empty docked panes retained while the initial views were being placed. */
    public void completeStartupLayout() {
        if (area != null) {
            area.retainEmptyDockedPanes(false);
        }
    }

    /** The permanent pane playing {@code role}, or {@code null} if there is none. */
    public NbfxTabPane primary(PaneRole role) {
        return area == null ? null : (NbfxTabPane) area.primary(role.name());
    }

    /**
     * The pane a view docked at {@code location} lives in, creating a docked pane where needed.
     * Returns {@code null} without an area, or when the location's permanent pane is not in it.
     */
    public NbfxTabPane paneAt(DockLocation location) {
        if (area == null) {
            return null;
        }
        // A registered area at this location wins over the fixed legacy mapping.
        ViewArea defaultArea = locationAreas.get(location);
        NbfxTabPane declared = defaultArea == null ? null : areaPanes.get(defaultArea.id());
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
    public NbfxTabPane paneOf(ViewArea viewArea) {
        NbfxTabPane declared = areaPanes.get(viewArea.id());
        return declared != null ? declared : paneAt(viewArea.location());
    }

    /** Whether the registered pane {@code pane} stays visible when empty. */
    public boolean keepsWhenEmpty(TabPane pane) {
        WindowComposition current = composition;
        String role = NbfxTabPane.roleOf(pane).name();
        Optional<WindowPane> registered = current == null
                ? WindowPaneRegistry.find(role)
                : current.pane(role);
        return registered.map(WindowPane::keepWhenEmpty).orElse(false);
    }

    /** Notes that {@code pane}'s tab is on loan to the sliding panel ({@code null}: none is). */
    void setLending(TabPane pane) {
        lending.set(pane);
    }

    /**
     * Logs the layout after {@code what} happened, at INFO while {@link Docking#LOG_LAYOUT} is on:
     * the area's tree with each pane's role, kind and tab titles, the maximized pane, and the
     * minimized panes with their sides.
     */
    void logLayout(String what) {
        if (!Docking.LOG_LAYOUT || area == null) {
            return;
        }
        StringBuilder text = new StringBuilder("Layout after ").append(what).append(":\n");
        text.append(area.printTree(this::describe));
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
    private String describe(TabPane pane) {
        List<String> titles = new ArrayList<>();
        List<Tab> tabs = slides != null && slides.isMinimized(pane) ? slides.tabsOf(pane) : pane.getTabs();
        for (Tab tab : tabs) {
            titles.add(NbfxTabPane.titleOf(tab));
        }
        return NbfxTabPane.roleOf(pane) + "/" + NbfxTabPane.kindOf(pane) + " " + titles;
    }

    /**
     * Persists the runtime's window state. The actual {@code WindowStateStore} integration belongs
     * to C3-3; the method exists now so the lifecycle is owned by the runtime.
     */
    public void saveLayout() {
        // Persistence is completed with the multi-window store in C3-3.
    }

    /**
     * Resets the runtime's area to the declared default shape, or the area's own default when no
     * tree is declared. The persisted-state reset is completed with C3-3.
     */
    public void resetLayout() {
        DockTree tree = defaultTree();
        if (area != null && tree != null && area.accepts(tree)) {
            area.applyTree(tree);
        }
    }

    /** Releases the runtime's references. Called when the owning shell stops. */
    public void dispose() {
        area = null;
        slides = null;
        composition = null;
    }

    // --- DockHost: the pane lifecycle the area drives --------------------------------------------

    @Override
    public TabPane createDockedPane() {
        return new NbfxTabPane(PaneRole.DOCKED);
    }

    /**
     * A pane split off an editor pane (a drop on its edge, New Document Tab Group) is an editor
     * pane; one split off a view pane, docked along the area, or created for a dock location
     * (Output below the editors, through {@link #paneAt}) is a view pane.
     */
    @Override
    public TabPane createDockedPane(TabPane beside) {
        PaneKind kind = beside != null && NbfxTabPane.kindOf(beside) == PaneKind.EDITOR
                ? PaneKind.EDITOR : PaneKind.VIEW;
        return new NbfxTabPane(PaneRole.DOCKED, kind);
    }

    @Override
    public void disposeDockedPane(TabPane pane) {
        NbfxTabPane.unregister(pane);
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

    /** The editor pane's area is made of editor panes. */
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