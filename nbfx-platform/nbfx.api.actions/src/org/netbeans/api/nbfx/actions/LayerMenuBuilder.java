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
package org.netbeans.api.nbfx.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Builds JavaFX menu items from layer-declared action references. Legacy builds resolve canonical
 * commands; context builds return an owned result that disposes scoped command views on close.
 *
 * @since 1.0
 */
public final class LayerMenuBuilder {

    private static final Logger LOG = Logger.getLogger(LayerMenuBuilder.class.getName());

    private final ActionRegistry registry;

    /** Creates a builder resolving references against {@code registry}. */
    public LayerMenuBuilder(ActionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Builds legacy menu items using canonical/global-context commands. Must be called on the JavaFX
     * Application Thread.
     *
     * @param folder the surface folder; may be {@code null}
     * @return the menu items, in order
     */
    public List<MenuItem> build(FileObject folder) {
        return buildLegacy(folder);
    }

    /**
     * Builds context-filtered menu items and owns all scoped command views created for the build.
     * Closing the result disposes those views; canonical registry commands are never disposed.
     *
     * @param folder the surface folder; may be {@code null}
     * @param context the surface context snapshot
     * @return an owned menu build
     */
    public Build buildOwned(FileObject folder, Lookup context) {
        Objects.requireNonNull(context, "context");
        Build build = new Build();
        try {
            build.nodes.addAll(buildContext(folder, context, build));
            build.finish();
            return build;
        } catch (RuntimeException | Error ex) {
            build.close();
            throw ex;
        }
    }

    /**
     * Builds legacy menu items from a layer path.
     *
     * @param layerPath the layer path
     * @return the menu items, in order
     */
    public List<MenuItem> build(String layerPath) {
        return build(layerPath == null ? null : FileUtil.getConfigFile(layerPath));
    }

    /**
     * Builds an owned context-filtered menu from a layer path.
     *
     * @param layerPath the layer path
     * @param context the surface context snapshot
     * @return an owned menu build
     */
    public Build buildOwned(String layerPath, Lookup context) {
        Objects.requireNonNull(context, "context");
        return buildOwned(layerPath == null ? null : FileUtil.getConfigFile(layerPath), context);
    }

    private List<MenuItem> buildLegacy(FileObject folder) {
        List<Node> nodes = new ArrayList<>();
        for (ActionRef ref : ActionLayerReader.read(folder)) {
            registry.find(ref.actionId()).ifPresentOrElse(
                    command -> nodes.add(new Node(ref.position(),
                            ref.separatorBefore(), item(command, ref))),
                    () -> LOG.warning("No command registered for action reference: " + ref.actionId()));
        }
        for (ActionLayerReader.Submenu submenu : ActionLayerReader.readSubmenus(folder)) {
            List<MenuItem> children = buildLegacy(submenu.folder());
            if (!children.isEmpty()) {
                Menu menu = new Menu(submenu.displayName());
                menu.getItems().addAll(children);
                nodes.add(new Node(submenu.position(), submenu.separatorBefore(), menu));
            }
        }
        return items(nodes);
    }

    private List<Node> buildContext(FileObject folder, Lookup context, Build owner) {
        List<Node> nodes = new ArrayList<>();
        for (ActionRef ref : ActionLayerReader.readForContext(folder, context)) {
            Optional<Command> canonical = registry.find(ref.actionId());
            registry.resolve(ref.actionId(), context).ifPresentOrElse(
                    command -> {
                        if (canonical.isEmpty() || command != canonical.get()) {
                            owner.scopedCommands.add(command);
                        }
                        nodes.add(new Node(ref.position(), ref.separatorBefore(), item(command, ref)));
                    },
                    () -> LOG.warning("No command registered for action reference: " + ref.actionId()));
        }
        for (ActionLayerReader.Submenu submenu : ActionLayerReader.readSubmenus(folder)) {
            List<Node> children = buildContext(submenu.folder(), context, owner);
            if (!children.isEmpty()) {
                Menu menu = new Menu(submenu.displayName());
                menu.getItems().addAll(items(children));
                nodes.add(new Node(submenu.position(), submenu.separatorBefore(), menu));
            }
        }
        return nodes;
    }

    private static List<MenuItem> items(List<Node> nodes) {
        nodes.sort(Comparator.comparingInt(Node::position));
        List<MenuItem> items = new ArrayList<>();
        MenuItem previous = null;
        for (Node node : nodes) {
            if (node.separatorBefore() && previous != null && !(previous instanceof SeparatorMenuItem)) {
                items.add(new SeparatorMenuItem());
            }
            items.add(node.item());
            previous = node.item();
        }
        return List.copyOf(items);
    }

    private MenuItem item(Command command, ActionRef ref) {
        MenuItem item = new MenuItem(ref.displayName().isBlank() ? command.getText() : ref.displayName());
        KeyCombination accelerator = accelerator(command);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.disableProperty().bind(command.disabledProperty());
        item.setOnAction(_ -> command.run());
        return item;
    }

    /** The command's accelerator, overridden by the user {@link KeyMap} when one is registered. */
    private static KeyCombination accelerator(Command command) {
        KeyMap keyMap = Lookup.getDefault().lookup(KeyMap.class);
        return keyMap == null ? command.getAccelerator()
                : keyMap.accelerator(command.getId(), command.getAccelerator());
    }

    /** A built menu item at its folder position; separators are resolved once ordered. */
    private record Node(int position, boolean separatorBefore, MenuItem item) {
        Node {
            Objects.requireNonNull(item, "item");
        }
    }

    /**
     * An owned context-filtered menu build. Close releases scoped command views after the menu items
     * are detached or no longer in use.
     *
     * @since 2.0
     */
    public static final class Build implements AutoCloseable {

        private final List<Node> nodes = new ArrayList<>();
        private final Set<Command> scopedCommands =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private List<MenuItem> items = List.of();
        private boolean closed;

        private Build() {
        }

        /** The immutable menu-item snapshot. */
        public List<MenuItem> items() {
            return items;
        }

        private void finish() {
            items = LayerMenuBuilder.items(nodes);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Command command : scopedCommands) {
                try {
                    command.dispose();
                } catch (RuntimeException ex) {
                    LOG.warning("Cannot dispose scoped menu command " + command.getId() + ": "
                            + ex.getMessage());
                }
            }
            scopedCommands.clear();
        }
    }
}
