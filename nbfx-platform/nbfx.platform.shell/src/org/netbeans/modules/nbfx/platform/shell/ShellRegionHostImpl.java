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
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.netbeans.api.nbfx.actions.ActionContextMetadata;
import org.netbeans.api.nbfx.actions.ActionLayerReader;
import org.netbeans.api.nbfx.actions.ActionRef;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.shell.ShellChromeProfile;
import org.netbeans.api.nbfx.shell.ShellContext;
import org.netbeans.api.nbfx.shell.ShellContributionEntry;
import org.netbeans.api.nbfx.shell.ShellContributionRegistry;
import org.netbeans.api.nbfx.shell.ShellRegionContext;
import org.netbeans.api.nbfx.shell.ShellRegionHost;
import org.netbeans.api.nbfx.shell.ShellRegionId;
import org.netbeans.api.nbfx.shell.ShellRegionKind;
import org.netbeans.api.nbfx.shell.ShellRegionRegistry;
import org.netbeans.api.nbfx.shell.ShellRegionRenderer;
import org.netbeans.api.nbfx.shell.ShellRegionSpec;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/** Runtime-owned declarative region mount lifecycle. */
public final class ShellRegionHostImpl implements ShellRegionHost, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ShellRegionHostImpl.class.getName());

    private final ShellContext context;
    private final Node dockRoot;
    private final Map<ShellRegionKind, ShellRegionRenderer> renderers;
    private final Function<String, List<ShellRegionSpec>> regionSupplier;
    private final BiFunction<ShellRegionId, String, List<ShellContributionEntry>> contributionSupplier;
    private final Function<ShellRegionId, FileObject> folderSupplier;
    private final Map<Node, Mount> mounts = new IdentityHashMap<>();
    private final Map<ShellRegionId, Node> mountsById = new LinkedHashMap<>();

    public ShellRegionHostImpl(ShellContext context, Node dockRoot, List<ShellRegionRenderer> renderers) {
        this(context, dockRoot, renderers,
                ShellRegionRegistry::forProfile,
                ShellContributionRegistry::load,
                id -> FileUtil.getConfigFile(LayerPaths.SHELL_REGIONS + "/" + id.value()));
    }

    ShellRegionHostImpl(ShellContext context, Node dockRoot, List<ShellRegionRenderer> renderers,
            Function<String, List<ShellRegionSpec>> regionSupplier,
            BiFunction<ShellRegionId, String, List<ShellContributionEntry>> contributionSupplier,
            Function<ShellRegionId, FileObject> folderSupplier) {
        this.context = Objects.requireNonNull(context, "context");
        this.dockRoot = dockRoot;
        this.renderers = new LinkedHashMap<>();
        for (ShellRegionRenderer renderer : renderers) {
            if (this.renderers.putIfAbsent(renderer.kind(), renderer) != null) {
                throw new IllegalArgumentException("Duplicate renderer for " + renderer.kind());
            }
        }
        this.regionSupplier = Objects.requireNonNull(regionSupplier, "regionSupplier");
        this.contributionSupplier = Objects.requireNonNull(
                contributionSupplier, "contributionSupplier");
        this.folderSupplier = Objects.requireNonNull(folderSupplier, "folderSupplier");
    }

    Node dockRoot() {
        return dockRoot;
    }

    boolean isDockMounted(Node node) {
        Mount mount = mounts.get(node);
        return mount != null && mount.spec.kind().equals(ShellRegionKind.DOCK);
    }

    @Override
    public List<ShellRegionSpec> regions(ShellChromeProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return regionSupplier.apply(profile.id()).stream()
                .filter(region -> profile.includeRegion(region.id()))
                .toList();
    }

    @Override
    public Node mount(ShellRegionSpec region, ShellRegionContext regionContext) {
        fxThread();
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(regionContext, "regionContext");
        if (mountsById.containsKey(region.id())) {
            throw new IllegalStateException("Shell region already mounted: " + region.id());
        }
        if (region.kind().equals(ShellRegionKind.DOCK)) {
            if (dockRoot == null) {
                throw new IllegalStateException("DOCK region requires a runtime dock root");
            }
            if (mounts.containsKey(dockRoot)) {
                throw new IllegalStateException("Runtime dock root is already mounted");
            }
            Mount mount = new Mount(region, dockRoot, null);
            captureDockState(mount);
            applyStyle(dockRoot, region);
            applyAccessibility(dockRoot, region);
            mounts.put(dockRoot, mount);
            mountsById.put(region.id(), dockRoot);
            return dockRoot;
        }

        Node wrapper = region.kind().equals(ShellRegionKind.OVERLAY)
                ? new StackPane() : new VBox();
        if (wrapper instanceof StackPane stack) {
            stack.setPickOnBounds(false);
        }
        wrapper.setId(region.id().value());
        applyStyle(wrapper, region);
        applyAccessibility(wrapper, region);
        Mount mount = new Mount(region, wrapper, renderers.get(region.kind()));
        try {
            fill(mount, regionContext);
        } catch (RuntimeException | Error ex) {
            disposeContents(mount);
            throw ex;
        }
        mounts.put(wrapper, mount);
        mountsById.put(region.id(), wrapper);
        return wrapper;
    }

    @Override
    public void update(ShellRegionSpec region, Node mounted) {
        fxThread();
        Mount mount = mount(mounted);
        if (mount == null || !mount.spec.id().equals(region.id())) {
            throw new IllegalArgumentException("Node is not mounted for region " + region.id());
        }
        if (!mount.spec.kind().equals(region.kind())) {
            throw new IllegalArgumentException("shell region kind cannot change during update");
        }
        if (region.kind().equals(ShellRegionKind.DOCK)) {
            restoreDockState(mount);
            captureDockState(mount);
            mount.spec = region;
            applyStyle(dockRoot, region);
            applyAccessibility(dockRoot, region);
            return;
        }
        disposeContents(mount);
        mounted.getStyleClass().removeAll(mount.spec.style().styleClasses());
        mount.spec = region;
        mount.renderer = renderers.get(region.kind());
        applyStyle(mounted, region);
        ShellRegionContext regionContext = new ShellRegionContext(context);
        fill(mount, regionContext);
    }

    @Override
    public void dispose(ShellRegionSpec region, Node mounted) {
        fxThread();
        Mount mount = mount(mounted);
        if (mount == null || !mount.spec.id().equals(region.id())) {
            return;
        }
        mounts.remove(mounted);
        mountsById.remove(region.id(), mounted);
        if (!mount.spec.kind().equals(ShellRegionKind.DOCK)) {
            disposeContents(mount);
        } else {
            restoreDockState(mount);
        }
        if (mounted.getParent() instanceof javafx.scene.layout.Pane parent) {
            parent.getChildren().remove(mounted);
        }
    }

    @Override
    public void close() {
        fxThread();
        for (Node mounted : List.copyOf(mounts.keySet())) {
            Mount mount = mounts.get(mounted);
            if (mount != null) {
                dispose(mount.spec, mounted);
            }
        }
    }

    private void fill(Mount mount, ShellRegionContext regionContext) {
        List<OrderedNode> ordered = new ArrayList<>();
        ShellRegionRenderer renderer = mount.renderer;
        if (renderer != null) {
            try {
                Node rendered = renderer.render(mount.spec, regionContext);
                if (rendered != null) {
                    requireUnparented(rendered);
                    ordered.add(new OrderedNode(Integer.MIN_VALUE, 0, rendered));
                    mount.rendererNode = rendered;
                }
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Cannot render shell region " + mount.spec.id(), ex);
            }
        }

        List<ShellContributionEntry> entries;
        try {
            entries = contributionSupplier.apply(mount.spec.id(), regionContext.profile().id());
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Cannot load shell contributions for " + mount.spec.id(), ex);
            entries = List.of();
        }
        for (ShellContributionEntry entry : entries) {
            try {
                Node node = entry.contribution().create(regionContext.shell());
                if (node == null) {
                    throw new IllegalStateException("contribution returned null");
                }
                requireUnparented(node);
                mount.contributions.add(entry);
                ordered.add(new OrderedNode(entry.spec().position(), ordered.size(), node));
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Cannot create shell contribution " + entry.spec().id(), ex);
                disposeQuietly(entry.contribution());
            }
        }
        ordered.addAll(actionNodes(mount, regionContext));
        ordered.sort(Comparator.comparingInt(OrderedNode::position)
                .thenComparingInt(OrderedNode::sequence));
        if (mount.wrapper instanceof VBox box) {
            ordered.forEach(node -> box.getChildren().add(node.node));
        } else if (mount.wrapper instanceof StackPane stack) {
            ordered.forEach(node -> stack.getChildren().add(node.node));
        }
    }

    private List<OrderedNode> actionNodes(Mount mount, ShellRegionContext regionContext) {
        FileObject folder = folderSupplier.apply(mount.spec.id());
        if (folder == null) {
            return List.of();
        }
        Lookup shellLookup = regionContext.shell().lookup();
        ActionContextMetadata existing = shellLookup.lookup(ActionContextMetadata.class);
        ActionContextMetadata merged = new ActionContextMetadata(
                existing == null ? null : existing.contextType(),
                existing == null ? null : existing.mimeType(),
                existing == null ? null : existing.languageId(),
                regionContext.profile().id());
        ProxyLookup actionLookup = new ProxyLookup(Lookups.singleton(merged), shellLookup);
        List<OrderedNode> result = new ArrayList<>();
        int sequence = 0;
        for (ActionRef ref : ActionLayerReader.readForContext(folder, actionLookup)) {
            Command owned = null;
            try {
                Command canonical = regionContext.shell().command(ref.actionId());
                if (canonical == null) {
                    continue;
                }
                Command command = canonical.forContext(actionLookup);
                if (command == null) {
                    throw new IllegalStateException("command returned null context view");
                }
                if (command != canonical) {
                    owned = command;
                    mount.scopedCommands.add(command);
                }
                Button button = new Button();
                button.setText(ref.displayName().isBlank() ? command.getText() : ref.displayName());
                button.setAccessibleText(button.getText());
                Node icon = regionContext.shell().icon(command, 16);
                if (icon != null) {
                    button.setGraphic(icon);
                }
                button.disableProperty().bind(command.disabledProperty());
                button.setOnAction(_ -> command.run());
                result.add(new OrderedNode(ref.position(), sequence++, button));
                owned = null;
            } catch (RuntimeException ex) {
                if (owned != null) {
                    mount.scopedCommands.remove(owned);
                    try {
                        owned.dispose();
                    } catch (RuntimeException disposeFailure) {
                        LOG.log(Level.WARNING, "Cannot dispose failed scoped action", disposeFailure);
                    }
                }
                LOG.log(Level.WARNING, "Cannot create shell action " + ref.actionId(), ex);
            }
        }
        return result;
    }

    private void disposeContents(Mount mount) {
        if (mount.rendererNode != null && mount.renderer != null) {
            try {
                mount.renderer.dispose(mount.spec, mount.rendererNode);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Cannot dispose renderer for " + mount.spec.id(), ex);
            }
        }
        mount.rendererNode = null;
        if (mount.wrapper instanceof VBox box) {
            box.getChildren().clear();
        } else if (mount.wrapper instanceof StackPane stack) {
            stack.getChildren().clear();
        }
        for (ShellContributionEntry entry : mount.contributions) {
            disposeQuietly(entry.contribution());
        }
        mount.contributions.clear();
        for (Command command : mount.scopedCommands) {
            try {
                command.dispose();
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Cannot dispose scoped action for " + mount.spec.id(), ex);
            }
        }
        mount.scopedCommands.clear();
    }

    private Mount mount(Node node) {
        return mounts.get(Objects.requireNonNull(node, "mounted"));
    }

    private static void applyStyle(Node node, ShellRegionSpec region) {
        node.getStyleClass().addAll(region.style().styleClasses());
        node.setVisible(region.visibleByDefault());
        node.setManaged(region.visibleByDefault());
    }

    private static void applyAccessibility(Node node, ShellRegionSpec region) {
        String label = "Shell region";
        AccessibleRole role = null;
        if (region.kind().equals(ShellRegionKind.BRAND)) {
            label = "Brand";
        } else if (region.kind().equals(ShellRegionKind.MENUBAR)) {
            label = "Menu bar";
            role = AccessibleRole.MENU_BAR;
        } else if (region.kind().equals(ShellRegionKind.TOOLBAR)) {
            label = "Tool bar";
            role = AccessibleRole.TOOL_BAR;
        } else if (region.kind().equals(ShellRegionKind.RIBBON)) {
            label = "Ribbon";
        } else if (region.kind().equals(ShellRegionKind.DOCK)) {
            label = "Docking area";
        } else if (region.kind().equals(ShellRegionKind.STATUS)) {
            label = "Status bar";
        } else if (region.kind().equals(ShellRegionKind.OVERLAY)) {
            label = "Overlay";
        }
        node.setAccessibleText(label);
        if (role != null) {
            node.setAccessibleRole(role);
        }
    }

    private static void captureDockState(Mount mount) {
        mount.dockStyles = new java.util.HashSet<>(mount.wrapper.getStyleClass());
        mount.dockVisible = mount.wrapper.isVisible();
        mount.dockManaged = mount.wrapper.isManaged();
        mount.dockAccessibleText = mount.wrapper.getAccessibleText();
        mount.dockAccessibleRole = mount.wrapper.getAccessibleRole();
    }

    private static void restoreDockState(Mount mount) {
        mount.wrapper.getStyleClass().setAll(mount.dockStyles);
        mount.wrapper.setVisible(mount.dockVisible);
        mount.wrapper.setManaged(mount.dockManaged);
        mount.wrapper.setAccessibleText(mount.dockAccessibleText);
        mount.wrapper.setAccessibleRole(mount.dockAccessibleRole);
    }

    private static void requireUnparented(Node node) {
        if (node.getParent() != null) {
            throw new IllegalStateException("region content must be unparented");
        }
    }

    private static void disposeQuietly(org.netbeans.api.nbfx.shell.ShellContribution contribution) {
        try {
            contribution.dispose();
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Cannot dispose shell contribution", ex);
        }
    }

    private static void fxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Shell region lifecycle requires the JavaFX Application Thread");
        }
    }

    private static final class Mount {
        private ShellRegionSpec spec;
        private final Node wrapper;
        private ShellRegionRenderer renderer;
        private Node rendererNode;
        private final List<ShellContributionEntry> contributions = new ArrayList<>();
        private final Set<Command> scopedCommands =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private Set<String> dockStyles;
        private boolean dockVisible;
        private boolean dockManaged;
        private String dockAccessibleText;
        private AccessibleRole dockAccessibleRole;

        private Mount(ShellRegionSpec spec, Node wrapper, ShellRegionRenderer renderer) {
            this.spec = spec;
            this.wrapper = wrapper;
            this.renderer = renderer;
        }
    }

    private record OrderedNode(int position, int sequence, Node node) {
    }
}
