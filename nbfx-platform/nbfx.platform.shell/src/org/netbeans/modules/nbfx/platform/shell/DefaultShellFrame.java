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
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.netbeans.api.nbfx.shell.ShellChromeProfile;
import org.netbeans.api.nbfx.shell.ShellCommandResolver;
import org.netbeans.api.nbfx.shell.ShellContext;
import org.netbeans.api.nbfx.shell.ShellFrame;
import org.netbeans.api.nbfx.shell.ShellRegionContext;
import org.netbeans.api.nbfx.shell.ShellRegionHost;
import org.netbeans.api.nbfx.shell.ShellRegionKind;
import org.netbeans.api.nbfx.shell.ShellRegionRenderer;
import org.netbeans.api.nbfx.shell.ShellRegionSpec;
import org.netbeans.api.nbfx.status.StatusElementRegistry;
import org.netbeans.modules.nbfx.platform.status.StatusBar;

/** Neutral default frame arranged from declarative shell regions. */
final class DefaultShellFrame implements ShellFrame {

    private static final Logger LOG = Logger.getLogger(DefaultShellFrame.class.getName());

    private final ShellContext context;
    private final ShellRegionHost host;
    private final StackPane root = new StackPane();
    private final BorderPane content = new BorderPane();
    private final StackPane overlays = new StackPane();
    private final VBox top = new VBox();
    private final List<MountedRegion> mounted = new ArrayList<>();
    private Node dockRoot;
    private boolean closed;
    private boolean resolverClosed;

    DefaultShellFrame(ShellContext context, ShellRegionHost host) {
        this.context = Objects.requireNonNull(context, "context");
        this.host = Objects.requireNonNull(host, "host");
        root.getChildren().addAll(content, overlays);
        root.setAccessibleText("Shell");
        content.setAccessibleText("Shell content");
        overlays.setAccessibleText("Shell overlays");
        overlays.setPickOnBounds(false);
        top.getStyleClass().add("shell-top");
        top.setAccessibleText("Shell top");
        content.setTop(top);
        ShellRegionContext regionContext = new ShellRegionContext(context);
        List<ShellRegionSpec> regions = host.regions(context.profile());
        long docks = regions.stream().filter(region -> region.kind().equals(ShellRegionKind.DOCK)).count();
        if (docks != 1) {
            throw new IllegalStateException("Default shell requires exactly one DOCK region; found " + docks);
        }
        try {
            for (ShellRegionSpec region : regions) {
                if (region.kind().equals(ShellRegionKind.DOCK)) {
                    continue;
                }
                Node node = host.mount(region, regionContext);
                mounted.add(new MountedRegion(region, node));
                if (region.kind().equals(ShellRegionKind.STATUS)) {
                    content.setBottom(node);
                } else if (region.kind().equals(ShellRegionKind.OVERLAY)) {
                    overlays.getChildren().add(node);
                } else {
                    top.getChildren().add(node);
                }
            }
        } catch (RuntimeException | Error ex) {
            close();
            throw ex;
        }
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void setDockRoot(Node dockRoot) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Dock mounting requires the JavaFX Application Thread");
        }
        if (closed) {
            throw new IllegalStateException("Shell frame is closed");
        }
        Objects.requireNonNull(dockRoot, "dockRoot");
        if (this.dockRoot == dockRoot) {
            return;
        }
        if (host instanceof ShellRegionHostImpl runtimeHost && runtimeHost.dockRoot() != dockRoot) {
            throw new IllegalStateException("Dock root does not match the runtime host");
        }
        if (this.dockRoot != null && this.dockRoot != dockRoot) {
            throw new IllegalStateException("Dock root is already mounted");
        }
        Node previousCenter = content.getCenter();
        ShellRegionSpec dock = null;
        Node node = null;
        try {
            dock = host.regions(context.profile()).stream()
                    .filter(region -> region.kind().equals(ShellRegionKind.DOCK))
                    .findFirst().orElseThrow();
            node = host.mount(dock, new ShellRegionContext(context));
            content.setCenter(node);
            mounted.add(new MountedRegion(dock, node));
            this.dockRoot = dockRoot;
        } catch (RuntimeException | Error ex) {
            if (node != null && dock != null) {
                try {
                    host.dispose(dock, node);
                } catch (RuntimeException disposeFailure) {
                    LOG.log(Level.WARNING, "Cannot roll back dock mount", disposeFailure);
                }
            }
            content.setCenter(previousCenter);
            throw ex;
        }
    }

    @Override
    public ShellRegionHost regions() {
        return host;
    }

    @Override
    public void close() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Shell frame close requires the JavaFX Application Thread");
        }
        if (closed) {
            return;
        }
        closed = true;
        try {
            for (MountedRegion region : mounted) {
                try {
                    host.dispose(region.spec, region.node);
                } catch (RuntimeException ex) {
                    LOG.log(Level.WARNING, "Cannot dispose shell region " + region.spec.id(), ex);
                }
            }
        } finally {
            mounted.clear();
            top.getChildren().clear();
            overlays.getChildren().clear();
            content.setCenter(null);
            content.setBottom(null);
            closeResolver();
        }
    }

    private void closeResolver() {
        if (resolverClosed) {
            return;
        }
        resolverClosed = true;
        ShellCommandResolver resolver = context.lookup().lookup(ShellCommandResolver.class);
        if (resolver != null) {
            try {
                resolver.close();
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Cannot close shell command resolver", ex);
            }
        }
    }

    /** Built-in renderers used by the neutral default frame. */
    static List<ShellRegionRenderer> renderers(ShellContext context) {
        return List.of(new ShellRegionRenderer() {
            @Override
            public ShellRegionKind kind() {
                return ShellRegionKind.STATUS;
            }

            @Override
            public Node render(ShellRegionSpec region, ShellRegionContext regionContext) {
                return new StatusBar(StatusElementRegistry.load(regionContext.shell().lookup()));
            }

            @Override
            public void dispose(ShellRegionSpec region, Node mounted) {
                ((StatusBar) mounted).dispose();
            }
        });
    }

    private record MountedRegion(ShellRegionSpec spec, Node node) {
    }
}
