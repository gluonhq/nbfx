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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.ActionContextMetadata;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.layout.AreaStyle;
import org.netbeans.api.nbfx.shell.ShellCommandResolver;
import org.netbeans.api.nbfx.shell.ShellContext;
import org.netbeans.api.nbfx.shell.ShellContribution;
import org.netbeans.api.nbfx.shell.ShellContributionEntry;
import org.netbeans.api.nbfx.shell.ShellContributionSpec;
import org.netbeans.api.nbfx.shell.ShellRegionContext;
import org.netbeans.api.nbfx.shell.ShellRegionId;
import org.netbeans.api.nbfx.shell.ShellRegionKind;
import org.netbeans.api.nbfx.shell.ShellRegionSpec;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Tests runtime region mounting, update, isolation and disposal. */
public class ShellRegionHostImplTest {

    private static final AtomicBoolean FX_STARTED = new AtomicBoolean();
    private static final ShellContext CONTEXT = new ShellContext(Lookup.EMPTY);

    @Test
    public void mountsUpdatesAndDisposesWithStableWrapper() throws Exception {
        onFxThread(() -> {
            int[] created = {0};
            int[] disposed = {0};
            ShellRegionSpec first = spec(ShellRegionId.BRAND, ShellRegionKind.BRAND,
                    Set.of("old"), false);
            ShellRegionHostImpl host = new ShellRegionHostImpl(CONTEXT, null, List.of(),
                    profile -> List.of(first),
                    (region, profile) -> List.of(entry("brand", () -> {
                        created[0]++;
                        return new TestContribution(() -> disposed[0]++);
                    })),
                    region -> null);
            Node wrapper = host.mount(first, new ShellRegionContext(CONTEXT));
            VBox parent = new VBox(wrapper);
            assertEquals(1, created[0]);
            assertTrue(wrapper.getStyleClass().contains("old"));
            assertFalse(wrapper.isVisible());

            ShellRegionSpec updated = spec(ShellRegionId.BRAND, ShellRegionKind.BRAND,
                    Set.of("new"), true);
            host.update(updated, wrapper);
            assertSame(wrapper, parent.getChildren().get(0));
            assertEquals(2, created[0]);
            assertEquals(1, disposed[0]);
            assertFalse(wrapper.getStyleClass().contains("old"));
            assertTrue(wrapper.getStyleClass().contains("new"));
            assertTrue(wrapper.isVisible());

            host.dispose(updated, wrapper);
            assertTrue(parent.getChildren().isEmpty());
            assertEquals(2, disposed[0]);
            host.dispose(updated, wrapper);
            assertEquals(2, disposed[0]);
            return null;
        });
    }

    @Test
    public void isolatesNullParentedAndFailingContributionNodes() throws Exception {
        onFxThread(() -> {
            int[] disposed = {0};
            StackPane parented = new StackPane();
            new VBox(parented);
            ShellRegionSpec region = spec(ShellRegionId.BRAND, ShellRegionKind.BRAND,
                    Set.of(), true);
            ShellRegionHostImpl host = new ShellRegionHostImpl(CONTEXT, null, List.of(),
                    profile -> List.of(region),
                    (id, profile) -> List.of(
                            entry("good", () -> new TestContribution(() -> disposed[0]++)),
                            entry("null", () -> new TestContribution(() -> disposed[0]++) {
                                @Override
                                public Node create(ShellContext context) {
                                    return null;
                                }
                            }),
                            entry("parented", () -> new TestContribution(() -> disposed[0]++) {
                                @Override
                                public Node create(ShellContext context) {
                                    return parented;
                                }
                            }),
                            entry("throws", () -> new TestContribution(() -> disposed[0]++) {
                                @Override
                                public Node create(ShellContext context) {
                                    throw new IllegalStateException("cannot create");
                                }
                            })),
                    id -> null);
            Node wrapper = host.mount(region, new ShellRegionContext(CONTEXT));
            assertEquals(1, ((VBox) wrapper).getChildren().size());
            assertEquals(3, disposed[0]);
            host.close();
            assertEquals(4, disposed[0]);
            return null;
        });
    }

    @Test
    public void dockByIdAndKindUsesRuntimeRootWithoutContributions() throws Exception {
        onFxThread(() -> {
            int[] loads = {0};
            StackPane dockRoot = new StackPane();
            ShellRegionSpec region = spec(ShellRegionId.of("product:dock"), ShellRegionKind.DOCK,
                    Set.of(), true);
            ShellRegionHostImpl host = new ShellRegionHostImpl(CONTEXT, dockRoot, List.of(),
                    profile -> List.of(region),
                    (id, profile) -> {
                        loads[0]++;
                        return List.of();
                    },
                    id -> null);
            assertSame(dockRoot, host.mount(region, new ShellRegionContext(CONTEXT)));
            assertEquals(0, loads[0]);
            VBox parent = new VBox(dockRoot);
            host.dispose(region, dockRoot);
            assertTrue(parent.getChildren().isEmpty());
            return null;
        });
    }

    @Test
    public void lifecycleRejectsNonFxThread() {
        ShellRegionSpec region = spec(ShellRegionId.BRAND, ShellRegionKind.BRAND,
                Set.of(), true);
        ShellRegionHostImpl host = new ShellRegionHostImpl(CONTEXT, null, List.of(),
                profile -> List.of(region), (id, profile) -> List.of(), id -> null);
        assertThrows(IllegalStateException.class,
                () -> host.mount(region, new ShellRegionContext(CONTEXT)));
    }

    @Test
    public void rejectsDuplicateDockRootsAndKindChanges() throws Exception {
        onFxThread(() -> {
            StackPane dockRoot = new StackPane();
            StackPane dockContent = new StackPane();
            dockRoot.getChildren().add(dockContent);
            ShellRegionSpec firstDock = spec(ShellRegionId.DOCK, ShellRegionKind.DOCK,
                    Set.of("dock"), false);
            ShellRegionSpec secondDock = spec(ShellRegionId.of("product:dock"), ShellRegionKind.DOCK,
                    Set.of(), true);
            ShellRegionHostImpl host = new ShellRegionHostImpl(CONTEXT, dockRoot, List.of(),
                    profile -> List.of(firstDock), (id, profile) -> List.of(), id -> null);
            assertSame(dockRoot, host.mount(firstDock, new ShellRegionContext(CONTEXT)));
            assertFalse(dockRoot.isVisible());
            assertTrue(dockRoot.getStyleClass().contains("dock"));
            assertEquals("Docking area", dockRoot.getAccessibleText());
            assertThrows(IllegalStateException.class,
                    () -> host.mount(secondDock, new ShellRegionContext(CONTEXT)));
            host.update(spec(ShellRegionId.DOCK, ShellRegionKind.DOCK, Set.of("updated"), true),
                    dockRoot);
            assertTrue(dockRoot.isVisible());
            host.dispose(spec(ShellRegionId.DOCK, ShellRegionKind.BRAND, Set.of(), true), dockRoot);
            assertTrue(dockRoot.getChildren().contains(dockContent));
            assertTrue(dockRoot.isVisible());
            assertFalse(dockRoot.getStyleClass().contains("updated"));

            int[] disposed = {0};
            ShellRegionSpec brand = spec(ShellRegionId.BRAND, ShellRegionKind.BRAND,
                    Set.of(), true);
            ShellRegionHostImpl ordinary = new ShellRegionHostImpl(CONTEXT, null, List.of(),
                    profile -> List.of(brand),
                    (id, profile) -> List.of(entry("brand", () -> new TestContribution(() -> disposed[0]++))),
                    id -> null);
            Node wrapper = ordinary.mount(brand, new ShellRegionContext(CONTEXT));
            ShellRegionSpec changedKind = spec(ShellRegionId.BRAND, ShellRegionKind.DOCK,
                    Set.of(), true);
            assertThrows(IllegalArgumentException.class, () -> ordinary.update(changedKind, wrapper));
            assertEquals(0, disposed[0]);
            ordinary.dispose(brand, wrapper);
            assertEquals(1, disposed[0]);
            return null;
        });
    }

    @Test
    public void overlayWrapperDoesNotPickEmptyBoundsAndRegionsHaveSemanticNames() throws Exception {
        onFxThread(() -> {
            ShellRegionSpec overlay = spec(ShellRegionId.OVERLAY, ShellRegionKind.OVERLAY,
                    Set.of(), true);
            ShellRegionSpec menu = spec(ShellRegionId.MENUBAR, ShellRegionKind.MENUBAR,
                    Set.of(), true);
            ShellRegionHostImpl host = new ShellRegionHostImpl(CONTEXT, null, List.of(),
                    profile -> List.of(overlay, menu), (id, profile) -> List.of(), id -> null);
            Node overlayNode = host.mount(overlay, new ShellRegionContext(CONTEXT));
            Node menuNode = host.mount(menu, new ShellRegionContext(CONTEXT));
            assertFalse(((StackPane) overlayNode).isPickOnBounds());
            assertEquals("Overlay", overlayNode.getAccessibleText());
            assertEquals("Menu bar", menuNode.getAccessibleText());
            host.close();
            return null;
        });
    }

    @Test
    public void actionsUseResolverMergedContextAndPerReferenceFailureIsolation() throws Exception {
        onFxThread(() -> {
            FileSystem fileSystem = FileUtil.createMemoryFileSystem();
            FileObject folder = folder(fileSystem, "ShellRegions/nbfx:brand");
            FileObject save = data(folder, "save.ref");
            attribute(save, "actionId", "save");
            attribute(save, "contextType", "editor");
            attribute(save, "mimeType", "text/x-java");
            attribute(save, "languageId", "java");
            attribute(save, "profiles", "main");
            FileObject bad = data(folder, "bad.ref");
            attribute(bad, "actionId", "bad");

            MapRegistry registry = new MapRegistry();
            registry.register(new RunnableCommand("save", "Registry", null, () -> {}));
            int[] scopedDisposed = {0};
            RunnableCommand resolverCommand = new RunnableCommand("save", "Resolver", null, () -> {}) {
                @Override
                public org.netbeans.api.nbfx.actions.Command forContext(Lookup context) {
                    return new RunnableCommand("save", "Resolver", null, () -> {}) {
                        @Override
                        public void dispose() {
                            scopedDisposed[0]++;
                        }
                    };
                }
            };
            ShellCommandResolver resolver = new ShellCommandResolver() {
                @Override
                public org.netbeans.api.nbfx.actions.Command resolve(String id) {
                    if ("bad".equals(id)) {
                        throw new IllegalStateException("cannot resolve");
                    }
                    return resolverCommand;
                }
            };
            ShellContext context = new ShellContext(Lookups.fixed(registry, resolver,
                    new ActionContextMetadata("editor", "text/x-java", "java", "compact")));
            ShellRegionSpec region = spec(ShellRegionId.BRAND, ShellRegionKind.BRAND,
                    Set.of(), true);
            ShellRegionHostImpl host = new ShellRegionHostImpl(context, null, List.of(),
                    profile -> List.of(region), (id, profile) -> List.of(), id -> folder);
            Node wrapper = host.mount(region, new ShellRegionContext(context));
            assertEquals(1, ((VBox) wrapper).getChildren().size());
            assertEquals("Resolver", ((Button) ((VBox) wrapper).getChildren().get(0)).getText());
            host.dispose(region, wrapper);
            assertEquals(1, scopedDisposed[0]);
            return null;
        });
    }

    @Test
    public void defaultFrameArrangesRegionsAndClosesIdempotently() throws Exception {
        onFxThread(() -> {
            List<ShellRegionSpec> regions = List.of(
                    spec(ShellRegionId.BRAND, ShellRegionKind.BRAND, Set.of(), true),
                    spec(ShellRegionId.MENUBAR, ShellRegionKind.MENUBAR, Set.of(), true),
                    spec(ShellRegionId.TOOLBAR, ShellRegionKind.TOOLBAR, Set.of(), true),
                    spec(ShellRegionId.DOCK, ShellRegionKind.DOCK, Set.of(), true),
                    spec(ShellRegionId.STATUS, ShellRegionKind.STATUS, Set.of(), true),
                    spec(ShellRegionId.OVERLAY, ShellRegionKind.OVERLAY, Set.of(), true));
            StackPane dockRoot = new StackPane();
            AtomicBoolean failLookup = new AtomicBoolean();
            ShellRegionHostImpl host = new ShellRegionHostImpl(CONTEXT, dockRoot, List.of(),
                    profile -> {
                        if (failLookup.get()) {
                            throw new IllegalStateException("lookup failed");
                        }
                        return regions;
                    },
                    (id, profile) -> List.of(), id -> null);
            DefaultShellFrame frame = new DefaultShellFrame(CONTEXT, host);
            assertTrue(frame.root() instanceof StackPane);
            failLookup.set(true);
            assertThrows(IllegalStateException.class, () -> frame.setDockRoot(dockRoot));
            failLookup.set(false);
            frame.setDockRoot(dockRoot);
            frame.setDockRoot(dockRoot);
            assertTrue(dockRoot.getParent() != null);
            assertThrows(IllegalStateException.class, () -> frame.setDockRoot(new StackPane()));
            frame.close();
            assertNull(dockRoot.getParent());
            frame.close();

            ShellRegionHostImpl noDock = new ShellRegionHostImpl(CONTEXT, dockRoot, List.of(),
                    profile -> List.of(regions.get(0)), (id, profile) -> List.of(), id -> null);
            assertThrows(IllegalStateException.class, () -> new DefaultShellFrame(CONTEXT, noDock));
            return null;
        });
    }

    @Test
    public void defaultFrameClosesResolverOwnedCommandsOnce() throws Exception {
        onFxThread(() -> {
            int[] closed = {0};
            ShellCommandResolver resolver = new ShellCommandResolver() {
                @Override
                public org.netbeans.api.nbfx.actions.Command resolve(String id) {
                    return null;
                }

                @Override
                public void close() {
                    closed[0]++;
                }
            };
            ShellContext context = new ShellContext(Lookups.singleton(resolver));
            StackPane dockRoot = new StackPane();
            ShellRegionSpec dock = spec(ShellRegionId.DOCK, ShellRegionKind.DOCK,
                    Set.of(), true);
            ShellRegionHostImpl host = new ShellRegionHostImpl(context, dockRoot, List.of(),
                    profile -> List.of(dock), (id, profile) -> List.of(), id -> null);
            DefaultShellFrame frame = new DefaultShellFrame(context, host);
            frame.setDockRoot(dockRoot);
            frame.close();
            frame.close();
            assertEquals(1, closed[0]);
            return null;
        });
    }

    @Test
    public void offFxFrameCloseDoesNotPoisonLaterDisposal() throws Exception {
        AtomicReference<DefaultShellFrame> frameRef = new AtomicReference<>();
        AtomicReference<StackPane> dockRef = new AtomicReference<>();
        onFxThread(() -> {
            StackPane dockRoot = new StackPane();
            ShellRegionSpec dock = spec(ShellRegionId.DOCK, ShellRegionKind.DOCK,
                    Set.of(), true);
            ShellRegionHostImpl host = new ShellRegionHostImpl(CONTEXT, dockRoot, List.of(),
                    profile -> List.of(dock), (id, profile) -> List.of(), id -> null);
            DefaultShellFrame frame = new DefaultShellFrame(CONTEXT, host);
            frame.setDockRoot(dockRoot);
            frameRef.set(frame);
            dockRef.set(dockRoot);
            return null;
        });
        assertThrows(IllegalStateException.class, () -> frameRef.get().close());
        assertThrows(IllegalStateException.class, () -> frameRef.get().setDockRoot(dockRef.get()));
        assertTrue(dockRef.get().getParent() != null);
        onFxThread(() -> {
            assertTrue(dockRef.get().getParent() != null);
            frameRef.get().close();
            assertNull(dockRef.get().getParent());
            return null;
        });
    }

    private static FileObject folder(FileSystem fileSystem, String path) {
        try {
            return FileUtil.createFolder(fileSystem.getRoot(), path);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private static FileObject data(FileObject folder, String name) {
        try {
            return folder.createData(name);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private static void attribute(FileObject file, String name, Object value) {
        try {
            file.setAttribute(name, value);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private static ShellRegionSpec spec(ShellRegionId id, ShellRegionKind kind,
            Set<String> classes, boolean visible) {
        return new ShellRegionSpec(id, kind, 10, Set.of("main"),
                new AreaStyle(classes, List.of(), List.of()), visible);
    }

    private static ShellContributionEntry entry(String id,
            java.util.function.Supplier<ShellContribution> factory) {
        return new ShellContributionEntry(new ShellContributionSpec(id, ShellRegionId.BRAND,
                10, Set.of("main"), ShellContribution.class), factory.get());
    }

    private static final class MapRegistry implements ActionRegistry {
        private final Map<String, org.netbeans.api.nbfx.actions.Command> commands = new LinkedHashMap<>();

        @Override
        public void register(org.netbeans.api.nbfx.actions.Command command) {
            commands.put(command.getId(), command);
        }

        @Override
        public Optional<org.netbeans.api.nbfx.actions.Command> find(String id) {
            return Optional.ofNullable(commands.get(id));
        }

        @Override
        public Collection<org.netbeans.api.nbfx.actions.Command> getCommands() {
            return List.copyOf(commands.values());
        }

        @Override
        public Optional<org.netbeans.api.nbfx.actions.Command> createScoped(String id,
                ObservableValue<EditorDocument> activeDocument) {
            return Optional.empty();
        }
    }

    private static class TestContribution implements ShellContribution {

        private final Runnable onDispose;

        private TestContribution(Runnable onDispose) {
            this.onDispose = onDispose;
        }

        @Override
        public Node create(ShellContext context) {
            return new StackPane();
        }

        @Override
        public void dispose() {
            onDispose.run();
        }
    }

    private static <T> T onFxThread(java.util.function.Supplier<T> task) throws InterruptedException {
        ensureFxStarted();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(task.get());
            } catch (Throwable failure) {
                error.set(failure);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(30, TimeUnit.SECONDS)) {
            fail("timed out waiting for the JavaFX thread");
        }
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return result.get();
    }

    private static void ensureFxStarted() throws InterruptedException {
        if (FX_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX startup timed out");
            }
        }
    }
}
