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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Side;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.netbeans.api.nbfx.content.Content;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.ContentManager;
import org.netbeans.api.nbfx.content.DockPlacement;
import org.netbeans.api.nbfx.layout.DockGroupId;
import org.netbeans.api.nbfx.resources.ResourceRef;
import org.netbeans.api.nbfx.shell.DockDecision;
import org.netbeans.api.nbfx.shell.DockPolicy;
import org.netbeans.api.nbfx.shell.DockWindow;
import org.netbeans.api.nbfx.shell.DockRequest;
import org.netbeans.api.nbfx.shell.DockResult;
import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.api.nbfx.shell.WindowClosePolicy;
import org.netbeans.api.nbfx.shell.WindowContent;
import org.netbeans.api.nbfx.shell.WindowId;
import org.netbeans.api.nbfx.shell.WindowKind;
import org.netbeans.api.nbfx.shell.WindowManager;
import org.netbeans.api.nbfx.shell.WindowOptions;
import org.netbeans.api.nbfx.shell.WindowState;
import org.netbeans.api.nbfx.views.ViewContent;
import org.netbeans.modules.nbfx.platform.workspace.DockArea;
import org.netbeans.modules.nbfx.platform.workspace.DropTarget;
import org.openide.util.Lookup;

/**
 * The {@link WindowManager} over the shell's dock groups and the content manager. It translates
 * stable content and group identities into the tab-pane operations the runtime already supports.
 */
final class DefaultWindowManager implements WindowManager {

    private static final Logger LOG = Logger.getLogger(DefaultWindowManager.class.getName());

    private final ContentManager contentManager;
    private final Supplier<Collection<? extends DockPolicy>> policies;
    private final Supplier<WindowState> mainState;
    private final AppState appState;
    private final Map<NbfxTabPane, FloatingWindow> floating = new LinkedHashMap<>();
    private final Map<WindowId, CustomWindow> custom = new LinkedHashMap<>();
    private MainDockWindow mainWindow;

    DefaultWindowManager(ContentManager contentManager) {
        this(contentManager, () -> Lookup.getDefault().lookupAll(DockPolicy.class), () -> null);
    }

    DefaultWindowManager(ContentManager contentManager, Supplier<Collection<? extends DockPolicy>> policies) {
        this(contentManager, policies, () -> null);
    }

    DefaultWindowManager(ContentManager contentManager, Supplier<Collection<? extends DockPolicy>> policies,
            Supplier<WindowState> mainState) {
        this(contentManager, policies, mainState, null);
    }

    DefaultWindowManager(ContentManager contentManager, Supplier<Collection<? extends DockPolicy>> policies,
            Supplier<WindowState> mainState, AppState appState) {
        this.contentManager = Objects.requireNonNull(contentManager, "contentManager");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.mainState = Objects.requireNonNull(mainState, "mainState");
        this.appState = appState;
    }

    @Override
    public List<DockGroupId> groups() {
        return NbfxTabPane.panesByRole().stream()
                .filter(NbfxTabPane.class::isInstance)
                .map(pane -> ((NbfxTabPane) pane).groupId())
                .toList();
    }

    @Override
    public Optional<DockGroupId> primary(PaneRole role) {
        if (role == null) {
            return Optional.empty();
        }
        TabPane pane = NbfxTabPane.paneWithRole(role);
        return pane instanceof NbfxTabPane nbfx ? Optional.of(nbfx.groupId()) : Optional.empty();
    }

    @Override
    public Optional<DockGroupId> groupOf(ContentId content) {
        return findTab(content)
                .map(Tab::getTabPane)
                .filter(NbfxTabPane.class::isInstance)
                .map(pane -> ((NbfxTabPane) pane).groupId());
    }

    @Override
    public void select(ContentId content) {
        findTab(content).ifPresent(NbfxTabPane::selectTabAndMoveToFront);
    }

    @Override
    public void move(ContentId content, DockGroupId target, int index) {
        Tab tab = findTab(content).orElse(null);
        TabPane pane = paneForGroup(target);
        if (tab == null || pane == null) {
            return;
        }
        NbfxTabPane.moveTab(pane, tab, index);
    }

    @Override
    public void attach(ContentId content, DockGroupId group, int index) {
        Tab tab = findTab(content).orElse(null);
        TabPane pane = paneForGroup(group);
        if (tab == null || pane == null) {
            return;
        }
        if (index < 0 || index >= pane.getTabs().size()) {
            NbfxTabPane.attachTab(pane, tab);
        } else {
            NbfxTabPane.moveTab(pane, tab, index);
            NbfxTabPane.selectTabAndMoveToFront(tab);
        }
    }

    @Override
    public void closeGroup(DockGroupId group) {
        TabPane pane = paneForGroup(group);
        if (pane != null) {
            TabContextMenu.closeGroup(pane);
        }
    }

    @Override
    public DockDecision canDock(DockRequest request) {
        if (request == null || request.content() == null) {
            return DockDecision.reject("dock.request.invalid");
        }
        if (contentManager.find(request.content()).isEmpty()) {
            return DockDecision.reject("dock.content.missing");
        }
        DockPlacement placement = request.placement();
        DockDecision structural = validatePlacement(request, placement);
        if (structural != null) {
            return structural;
        }
        DockDecision decision = DockDecision.allow(placement);
        for (DockPolicy policy : policies.get()) {
            if (policy == null) {
                continue;
            }
            DockDecision policyDecision = policy.evaluate(request);
            if (policyDecision == null) {
                continue;
            }
            if (!policyDecision.allowed()) {
                return policyDecision;
            }
            decision = policyDecision;
        }
        DockPlacement resolvedPlacement = decision.placement();
        DockDecision refined = validatePlacement(request, resolvedPlacement);
        return refined == null ? decision : refined;
    }

    @Override
    public DockResult dock(DockRequest request) {
        DockDecision decision = canDock(request);
        if (!decision.allowed()) {
            return DockResult.rejected(decision);
        }
        Tab tab = findTab(request.content()).orElse(null);
        if (tab == null) {
            return DockResult.rejected(DockDecision.reject("dock.content.missing"));
        }
        DockPlacement placement = decision.placement();
        if (placement == DockPlacement.FLOAT) {
            floatContent(request.content(), defaultFloatingOptions());
            DockGroupId group = groupOf(request.content()).orElse(null);
            return group == null
                    ? DockResult.rejected(DockDecision.reject("dock.content.missing"))
                    : DockResult.accepted(decision, group);
        }
        if (isGroupPlacement(placement)) {
            DockGroupId target = resolveTargetGroup(request);
            TabPane pane = paneForGroup(target);
            if (pane == null) {
                return DockResult.rejected(DockDecision.reject("dock.target.missing"));
            }
            int index = -1;
            if (isRelativePlacement(placement)) {
                Tab targetTab = findTab(request.targetContent()).orElse(null);
                if (targetTab == null || targetTab.getTabPane() != pane) {
                    return DockResult.rejected(DockDecision.reject("dock.target.missing"));
                }
                index = pane.getTabs().indexOf(targetTab);
                if (tab.getTabPane() == pane && pane.getTabs().indexOf(tab) < index) {
                    index--;
                }
                if (placement == DockPlacement.AFTER) {
                    index++;
                }
                NbfxTabPane.moveTab(pane, tab, index);
                NbfxTabPane.selectTabAndMoveToFront(tab);
            } else {
                NbfxTabPane.attachTab(pane, tab);
            }
            return DockResult.accepted(decision, ((NbfxTabPane) pane).groupId());
        }
        if (isSplitPlacement(placement) || isEdgePlacement(placement)) {
            DockArea<TabPane> area = Docking.area();
            if (area == null) {
                return DockResult.rejected(DockDecision.reject("dock.target.missing"));
            }
            TabPane targetPane = isSplitPlacement(placement)
                    ? paneForGroup(resolveTargetGroup(request)) : null;
            if (isSplitPlacement(placement) && (targetPane == null || !area.contains(targetPane))) {
                return DockResult.rejected(DockDecision.reject("dock.target.missing"));
            }
            TabPane source = tab.getTabPane();
            if (source != null && area.contains(source) && source.getTabs().size() == 1
                    && (isEdgePlacement(placement) || source != targetPane)) {
                DropTarget<TabPane> target = dropTargetFor(placement, targetPane);
                area.relocate(source, target);
                DockGroupId group = source instanceof NbfxTabPane nbfx ? nbfx.groupId() : null;
                return group == null
                        ? DockResult.rejected(DockDecision.reject("dock.target.missing"))
                        : DockResult.accepted(decision, group);
            }
            if (source != null) {
                source.getTabs().remove(tab);
            }
            TabPane created = area.split(dropTargetFor(placement, targetPane));
            NbfxTabPane.moveTab(created, tab, 0);
            DockGroupId group = created instanceof NbfxTabPane nbfx ? nbfx.groupId() : null;
            return group == null
                    ? DockResult.rejected(DockDecision.reject("dock.target.missing"))
                    : DockResult.accepted(decision, group);
        }
        return DockResult.rejected(DockDecision.reject("dock.placement.unsupported"));
    }

    // --- Floating windows ------------------------------------------------------------------------

    @Override
    public DockWindow floatContent(ContentId content, WindowOptions options) {
        return floatContentWindow(content, options);
    }

    private FloatingWindow floatContentWindow(ContentId content, WindowOptions options) {
        Objects.requireNonNull(content, "content");
        WindowOptions checked = requireFloatingOptions(options);
        return floatContentAt(content, checked, defaultFloatingX(checked), defaultFloatingY(checked));
    }

    private FloatingWindow floatContentAt(ContentId content, WindowOptions options, double x, double y) {
        Objects.requireNonNull(content, "content");
        WindowOptions checked = requireFloatingOptions(options);
        Tab tab = findTab(content).orElseThrow(() -> new IllegalArgumentException("Content is not docked"));
        if (tab.getTabPane() instanceof NbfxTabPane nbfx && NbfxTabPane.isFloatingPane(nbfx)) {
            FloatingWindow existing = floating.get(nbfx);
            if (existing == null) {
                existing = registerFloating(nbfx, null, defaultFloatingOptions());
            }
            existing.show();
            return existing;
        }
        Consumer<Stage> configure = windowStageConfigurator(checked, x, y);
        NbfxTabPane pane = NbfxTabPane.floatTab(tab, x, y, checked.width(), checked.height(), configure);
        return registerFloating(pane, null, checked);
    }

    @Override
    public DockWindow floatGroup(DockGroupId group, WindowOptions options) {
        return floatGroupWindow(group, options);
    }

    private FloatingWindow floatGroupWindow(DockGroupId group, WindowOptions options) {
        Objects.requireNonNull(group, "group");
        WindowOptions checked = requireFloatingOptions(options);
        return floatGroupAt(group, checked, defaultFloatingX(checked), defaultFloatingY(checked));
    }

    private FloatingWindow floatGroupAt(DockGroupId group, WindowOptions options, double x, double y) {
        Objects.requireNonNull(group, "group");
        WindowOptions checked = requireFloatingOptions(options);
        TabPane pane = paneForGroup(group);
        if (!(pane instanceof NbfxTabPane nbfx)) {
            throw new IllegalArgumentException("Unknown dock group: " + group);
        }
        if (nbfx.role() == PaneRole.MAIN) {
            throw new IllegalArgumentException("The main pane cannot be floated");
        }
        if (NbfxTabPane.isFloatingPane(nbfx)) {
            FloatingWindow existing = floating.get(nbfx);
            if (existing == null) {
                existing = registerFloating(nbfx, null, defaultFloatingOptions());
            }
            existing.show();
            return existing;
        }
        DockArea<TabPane> area = Docking.area();
        if (area == null || !area.contains(nbfx)) {
            throw new IllegalArgumentException("Dock group is not in the dock area: " + group);
        }
        DockArea.Anchor<TabPane> anchor = area.detach(nbfx);
        Consumer<Stage> configure = windowStageConfigurator(checked, x, y);
        NbfxTabPane.showDetachedPane(nbfx, x, y, checked.width(), checked.height(), configure);
        return registerFloating(nbfx, anchor, checked);
    }

    @Override
    public DockWindow createCustomWindow(WindowId id, WindowOptions options, WindowContent content) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(content, "content");
        if (options.kind() != WindowKind.CUSTOM) {
            throw new IllegalArgumentException("Custom windows require WindowKind.CUSTOM");
        }
        if (options.closePolicy() == WindowClosePolicy.REDOCK) {
            throw new IllegalArgumentException("Custom windows cannot redock content");
        }
        if (custom.containsKey(id)) {
            throw new IllegalArgumentException("Custom window already exists: " + id);
        }
        double x = defaultFloatingX(options);
        double y = defaultFloatingY(options);
        WindowOptions effective = options;
        if (options.rememberBounds() && appState != null) {
            AppState.WindowBounds bounds = appState.customWindowBounds(id);
            if (bounds != null) {
                double[] clamped = AppState.clampBounds(bounds.x(), bounds.y(),
                        bounds.width(), bounds.height());
                x = clamped[0];
                y = clamped[1];
                effective = new WindowOptions(options.kind(), options.profile(), options.title(),
                        options.icon(), options.owner(), options.modal(), clamped[2], clamped[3],
                        options.resizable(), true, options.decoration(), options.closePolicy());
            }
        }
        Consumer<Stage> configure = windowStageConfigurator(effective, x, y);
        CustomWindow window = new CustomWindow(this, id, effective, content, configure);
        custom.put(id, window);
        window.show();
        return window;
    }

    @Override
    public DockResult redock(DockWindow window, DockRequest request) {
        if (!(window instanceof FloatingWindow floating) || floating.owner() != this) {
            return DockResult.rejected(DockDecision.reject("dock.window.missing"));
        }
        return redockWindow(floating, request);
    }

    private DockResult redockWindow(FloatingWindow window, DockRequest request) {
        if (window.stage() == null || !NbfxTabPane.tabPanes().contains(window.pane())) {
            return DockResult.rejected(DockDecision.reject("dock.window.missing"));
        }
        if (request == null) {
            return DockResult.rejected(DockDecision.reject("dock.request.invalid"));
        }
        Tab representative = findTab(request.content()).orElse(null);
        if (representative == null || representative.getTabPane() != window.pane()) {
            return DockResult.rejected(DockDecision.reject("dock.window.mismatch"));
        }
        if (window.pane().getTabs().isEmpty()) {
            return DockResult.rejected(DockDecision.reject("dock.content.missing"));
        }
        DockDecision decision = canDock(request);
        if (!decision.allowed()) {
            return DockResult.rejected(decision);
        }
        DockPlacement placement = decision.placement();
        Tab selected = window.pane().getSelectionModel().getSelectedItem();
        if (selected == null) {
            selected = window.pane().getTabs().getFirst();
        }
        if (isGroupPlacement(placement)) {
            DockGroupId target = resolveTargetGroup(request);
            TabPane pane = paneForGroup(target);
            if (pane == null) {
                return DockResult.rejected(DockDecision.reject("dock.target.missing"));
            }
            List<Tab> tabs = List.copyOf(window.pane().getTabs());
            int base = pane.getTabs().size();
            if (isRelativePlacement(placement)) {
                Tab targetTab = findTab(request.targetContent()).orElse(null);
                if (targetTab == null || targetTab.getTabPane() != pane) {
                    return DockResult.rejected(DockDecision.reject("dock.target.missing"));
                }
                base = pane.getTabs().indexOf(targetTab) + (placement == DockPlacement.AFTER ? 1 : 0);
            }
            for (int i = 0; i < tabs.size(); i++) {
                int index = isRelativePlacement(placement) ? base + i : pane.getTabs().size();
                NbfxTabPane.moveTab(pane, tabs.get(i), index);
            }
            NbfxTabPane.closeFloatingPane(window.pane(), true);
            window.markClosed();
            if (window.pane().role() == PaneRole.DETACHED || window.pane().role() == PaneRole.DOCKED) {
                NbfxTabPane.unregister(window.pane());
            }
            floating.remove(window.pane());
            NbfxTabPane.selectTabAndMoveToFront(selected);
            return DockResult.accepted(decision, ((NbfxTabPane) pane).groupId());
        }
        if (isSplitPlacement(placement) || isEdgePlacement(placement)) {
            DockArea<TabPane> area = Docking.area();
            if (area == null) {
                return DockResult.rejected(DockDecision.reject("dock.target.missing"));
            }
            TabPane targetPane = isSplitPlacement(placement)
                    ? paneForGroup(resolveTargetGroup(request)) : null;
            if (isSplitPlacement(placement) && (targetPane == null || !area.contains(targetPane))) {
                return DockResult.rejected(DockDecision.reject("dock.target.missing"));
            }
            NbfxTabPane.closeFloatingPane(window.pane(), true);
            window.markClosed();
            area.dockDetached(window.pane(), dropTargetFor(placement, targetPane));
            floating.remove(window.pane());
            NbfxTabPane.selectTabAndMoveToFront(selected);
            return DockResult.accepted(decision, window.pane().groupId());
        }
        return DockResult.rejected(DockDecision.reject("dock.placement.unsupported"));
    }

    @Override
    public List<DockWindow> windows() {
        List<DockWindow> result = new ArrayList<>();
        DockArea<TabPane> area = Docking.area();
        if (area != null && area.getScene() != null && area.getScene().getWindow() instanceof Stage stage) {
            SlidingArea slides = Docking.runtime().slides();
            if (mainWindow == null || mainWindow.root() != slides
                    || mainWindow.area() != area || mainWindow.stage() != stage) {
                mainWindow = new MainDockWindow(slides, area, stage, mainState);
            }
            result.add(mainWindow);
        } else {
            mainWindow = null;
        }
        for (FloatingWindow window : List.copyOf(floating.values())) {
            if (NbfxTabPane.tabPanes().contains(window.pane())) {
                result.add(window);
            } else {
                floating.remove(window.pane(), window);
            }
        }
        result.addAll(custom.values());
        return List.copyOf(result);
    }

    void saveCustomWindowBounds(CustomWindow window) {
        if (appState != null && window.options().rememberBounds()) {
            appState.saveCustomWindowBounds(window.windowId(), window.stage().getX(),
                    window.stage().getY(), window.stage().getWidth(), window.stage().getHeight());
        }
    }

    void removeCustomWindow(CustomWindow window) {
        custom.remove(window.windowId(), window);
    }

    void closeFloatingWindow(FloatingWindow window) {
        if (window.owner() != this) {
            throw new IllegalArgumentException("Window belongs to another manager");
        }
        switch (window.options().closePolicy()) {
            case HIDE -> window.hide();
            case REDOCK -> redockFloatingOnClose(window);
            case CLOSE_CONTENT -> {
                NbfxTabPane.closeFloatingPane(window.pane(), false);
                if (!window.stage().isShowing()) {
                    floating.remove(window.pane(), window);
                }
            }
        }
    }

    private void redockFloatingOnClose(FloatingWindow window) {
        List<Tab> tabs = List.copyOf(window.pane().getTabs());
        if (tabs.isEmpty()) {
            NbfxTabPane.closeFloatingPane(window.pane(), true);
            window.markClosed();
            floating.remove(window.pane());
            return;
        }
        DockArea<TabPane> area = Docking.area();
        TabPane fallback = area == null ? null : area.mainPane();
        if (fallback == null) {
            fallback = NbfxTabPane.paneWithRole(PaneRole.MAIN);
        }
        Map<Tab, TabPane> targets = new LinkedHashMap<>();
        for (Tab tab : tabs) {
            TabPane target = NbfxTabPane.homeOf(tab);
            if (target == null || target == window.pane() || !NbfxTabPane.tabPanes().contains(target)) {
                target = fallback;
            }
            if (target == null || target == window.pane()) {
                return;
            }
            targets.put(tab, target);
        }
        Tab selected = window.pane().getSelectionModel().getSelectedItem();
        targets.forEach((tab, target) -> NbfxTabPane.moveTab(target, tab, target.getTabs().size()));
        NbfxTabPane.closeFloatingPane(window.pane(), true);
        window.markClosed();
        if (window.pane().role() == PaneRole.DETACHED || window.pane().role() == PaneRole.DOCKED) {
            NbfxTabPane.unregister(window.pane());
        }
        floating.remove(window.pane());
        if (selected != null) {
            NbfxTabPane.selectTabAndMoveToFront(selected);
        }
    }

    private FloatingWindow registerFloating(NbfxTabPane pane, DockArea.Anchor<TabPane> anchor,
            WindowOptions options) {
        Stage stage = NbfxTabPane.stageOf(pane);
        if (stage == null) {
            throw new IllegalStateException("Floating pane has no stage");
        }
        FloatingWindow window = new FloatingWindow(this, pane, stage, anchor, options);
        floating.put(pane, window);
        stage.addEventFilter(WindowEvent.WINDOW_HIDDEN, _ -> {
            if (NbfxTabPane.isClosingWindow(stage)) {
                window.markClosed();
                floating.remove(pane, window);
            }
        });
        return window;
    }

    private static WindowOptions defaultFloatingOptions() {
        return new WindowOptions(WindowKind.FLOATING, null, null, null, null,
                false, 600, 400, true, false, null);
    }

    private static WindowOptions requireFloatingOptions(WindowOptions options) {
        Objects.requireNonNull(options, "options");
        if (options.kind() != WindowKind.FLOATING) {
            throw new IllegalArgumentException("Floating windows require WindowKind.FLOATING");
        }
        return options;
    }

    private static Consumer<Stage> windowStageConfigurator(WindowOptions options, double x, double y) {
        return stage -> {
            if (options.owner() != null) {
                stage.initOwner(options.owner());
            }
            if (options.modal()) {
                stage.initModality(options.owner() != null
                        ? javafx.stage.Modality.WINDOW_MODAL : javafx.stage.Modality.APPLICATION_MODAL);
            }
            if (options.title() != null && !options.title().isBlank()) {
                stage.setTitle(options.title());
            }
            if (options.decoration() != null) {
                ShellApplication.applyWindowDecoration(stage, options.decoration());
            }
            applyWindowIcon(stage, options.icon());
            stage.setResizable(options.resizable());
            stage.setX(x);
            stage.setY(y);
        };
    }

    private static void applyWindowIcon(Stage stage, ResourceRef icon) {
        if (icon == null) {
            return;
        }
        ClassLoader loader = Lookup.getDefault().lookup(ClassLoader.class);
        URL resource = loader == null ? null : loader.getResource(icon.path());
        if (resource == null) {
            LOG.warning(() -> "Window icon resource not found: " + icon.path());
            return;
        }
        try {
            stage.getIcons().setAll(new Image(resource.toExternalForm()));
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Cannot load window icon: " + icon.path(), ex);
        }
    }

    private static double defaultFloatingX(WindowOptions options) {
        Window owner = options.owner();
        if (owner == null) {
            DockArea<TabPane> area = Docking.area();
            owner = area != null && area.getScene() != null ? area.getScene().getWindow() : null;
        }
        return owner != null && Double.isFinite(owner.getX()) ? owner.getX() + 40 : 100;
    }

    private static double defaultFloatingY(WindowOptions options) {
        Window owner = options.owner();
        if (owner == null) {
            DockArea<TabPane> area = Docking.area();
            owner = area != null && area.getScene() != null ? area.getScene().getWindow() : null;
        }
        return owner != null && Double.isFinite(owner.getY()) ? owner.getY() + 40 : 100;
    }

    private DockDecision validatePlacement(DockRequest request, DockPlacement placement) {
        if (placement == DockPlacement.FLOAT) {
            return findTab(request.content()).isEmpty()
                    ? DockDecision.reject("dock.content.missing") : null;
        }
        if (isGroupPlacement(placement)) {
            DockGroupId target = resolveTargetGroup(request);
            TabPane pane = paneForGroup(target);
            if (pane == null) {
                return DockDecision.reject("dock.target.missing");
            }
            if (isRelativePlacement(placement)) {
                if (request.targetContent() == null || request.targetContent().equals(request.content())) {
                    return DockDecision.reject("dock.target.missing");
                }
                Tab targetTab = findTab(request.targetContent()).orElse(null);
                if (targetTab == null || targetTab.getTabPane() != pane) {
                    return DockDecision.reject("dock.target.missing");
                }
            }
            return null;
        }
        if (isSplitPlacement(placement)) {
            DockArea<TabPane> area = Docking.area();
            DockGroupId target = resolveTargetGroup(request);
            TabPane pane = paneForGroup(target);
            if (area == null || !(pane instanceof NbfxTabPane) || !area.contains(pane)) {
                return DockDecision.reject("dock.target.missing");
            }
            return null;
        }
        if (isEdgePlacement(placement)) {
            return Docking.area() == null ? DockDecision.reject("dock.target.missing") : null;
        }
        return DockDecision.reject("dock.placement.unsupported");
    }

    private DockGroupId resolveTargetGroup(DockRequest request) {
        if (request.targetGroup() != null) {
            return request.targetGroup();
        }
        if (request.targetArea() != null) {
            TabPane pane = NbfxTabPane.paneWithRole(request.targetArea());
            if (pane instanceof NbfxTabPane nbfx) {
                return nbfx.groupId();
            }
        }
        return null;
    }

    private static boolean isGroupPlacement(DockPlacement placement) {
        return placement == DockPlacement.INTO_GROUP || placement == DockPlacement.TAB_STRIP
                || isRelativePlacement(placement);
    }

    private static boolean isRelativePlacement(DockPlacement placement) {
        return placement == DockPlacement.BEFORE || placement == DockPlacement.AFTER;
    }

    private static boolean isSplitPlacement(DockPlacement placement) {
        return placement == DockPlacement.SPLIT_LEFT || placement == DockPlacement.SPLIT_RIGHT
                || placement == DockPlacement.SPLIT_TOP || placement == DockPlacement.SPLIT_BOTTOM;
    }

    private static boolean isEdgePlacement(DockPlacement placement) {
        return placement == DockPlacement.EDGE_LEFT || placement == DockPlacement.EDGE_RIGHT
                || placement == DockPlacement.EDGE_TOP || placement == DockPlacement.EDGE_BOTTOM;
    }

    private static DropTarget<TabPane> dropTargetFor(DockPlacement placement, TabPane targetPane) {
        Side side = switch (placement) {
            case SPLIT_LEFT, EDGE_LEFT -> Side.LEFT;
            case SPLIT_RIGHT, EDGE_RIGHT -> Side.RIGHT;
            case SPLIT_TOP, EDGE_TOP -> Side.TOP;
            case SPLIT_BOTTOM, EDGE_BOTTOM -> Side.BOTTOM;
            default -> throw new IllegalArgumentException("Not a split/edge placement: " + placement);
        };
        return isEdgePlacement(placement) ? new DropTarget<>(null, side) : new DropTarget<>(targetPane, side);
    }

    private Optional<Tab> findTab(ContentId contentId) {
        if (contentId == null) {
            return Optional.empty();
        }
        Content content = contentManager.find(contentId).orElse(null);
        if (content instanceof ViewContent viewContent) {
            return NbfxTabPane.findViewTab(viewContent.view().getId());
        }
        if (content instanceof EditorContent editorContent) {
            return NbfxTabPane.findTab(editorContent.document());
        }
        // Fallback: scan tabs by view id derived from content id convention.
        String value = contentId.value();
        if (value.startsWith("nbfx:view.")) {
            String viewId = value.substring("nbfx:view.".length());
            return NbfxTabPane.findViewTab(viewId);
        }
        return Optional.empty();
    }

    private static TabPane paneForGroup(DockGroupId groupId) {
        if (groupId == null) {
            return null;
        }
        for (TabPane pane : NbfxTabPane.panesByRole()) {
            if (pane instanceof NbfxTabPane nbfx && groupId.equals(nbfx.groupId())) {
                return pane;
            }
        }
        return null;
    }
}
