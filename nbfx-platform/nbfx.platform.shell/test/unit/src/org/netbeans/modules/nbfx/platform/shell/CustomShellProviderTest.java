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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.After;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.shell.ShellChromeProfile;
import org.netbeans.api.nbfx.shell.ShellContext;
import org.netbeans.api.nbfx.shell.ShellFrame;
import org.netbeans.api.nbfx.shell.ShellProvider;
import org.netbeans.api.nbfx.shell.ShellCommandResolver;
import org.netbeans.api.nbfx.shell.ShellRegionContext;
import org.netbeans.api.nbfx.shell.ShellRegionHost;
import org.netbeans.api.nbfx.shell.ShellRegionKind;
import org.netbeans.api.nbfx.shell.ShellRegionSpec;
import org.netbeans.api.nbfx.shell.WindowManager;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Acceptance fixture for an API-only custom shell provider. */
public class CustomShellProviderTest {

    @After
    public void hideWindows() throws InterruptedException {
        JavaFxTestSupport.hideWindows();
    }

    @Test
    public void customProviderUsesRuntimeHostsAndDefaultServices() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            AtomicInteger created = new AtomicInteger();
            AtomicInteger closed = new AtomicInteger();
            AtomicReference<ShellContext> receivedContext = new AtomicReference<>();
            ShellProvider provider = (context, profile) -> {
                created.incrementAndGet();
                receivedContext.set(context);
                return new ApiOnlyFrame(context, profile, closed, true);
            };
            CustomApplication application = new CustomApplication(provider);
            application.start(new Stage());

            assertEquals(1, created.get());
            ShellFrame frame = application.getShellFrame();
            assertNotNull(frame);
            assertTrue(application.getRoot().getChildren().contains(frame.root()));
            assertNotNull(application.getMainPane());
            assertSame(application.windowManager(), receivedContext.get().lookup().lookup(WindowManager.class));
            assertSame(frame.regions(), receivedContext.get().lookup().lookup(ShellRegionHost.class));
            assertNotNull(receivedContext.get().lookup().lookup(ActionRegistry.class));
            assertNotNull(receivedContext.get().lookup().lookup(ToolBarContainer.class));
            assertTrue(frame.root() instanceof StackPane);
            frame.close();
            assertEquals(1, closed.get());
            return null;
        });
    }

    @Test
    public void duplicateProvidersFailBeforeFrameCreation() {
        ShellProvider first = (context, profile) -> new ApiOnlyFrame(context, profile,
                new AtomicInteger(), true);
        ShellProvider second = (context, profile) -> new ApiOnlyFrame(context, profile,
                new AtomicInteger(), true);
        AtomicInteger resolverClosed = new AtomicInteger();
        ShellCommandResolver resolver = new ShellCommandResolver() {
            @Override
            public org.netbeans.api.nbfx.actions.Command resolve(String id) {
                return null;
            }

            @Override
            public void close() {
                resolverClosed.incrementAndGet();
            }
        };
        CustomApplication application = new CustomApplication(first, Lookups.fixed(second, resolver));
        AssertionError failure = assertThrows(AssertionError.class,
                () -> JavaFxTestSupport.onFx(() -> {
                    application.start(new Stage());
                    return null;
                }));
        assertTrue(failure.getCause().getMessage().contains("Exactly one ShellProvider"));
        assertEquals(1, resolverClosed.get());
    }

    @Test
    public void omittedDockMountRollsBackFrameAndResolver() {
        AtomicInteger frameClosed = new AtomicInteger();
        AtomicInteger resolverClosed = new AtomicInteger();
        ShellCommandResolver resolver = new ShellCommandResolver() {
            @Override
            public org.netbeans.api.nbfx.actions.Command resolve(String id) {
                return null;
            }

            @Override
            public void close() {
                resolverClosed.incrementAndGet();
            }
        };
        ShellProvider provider = (context, profile) -> new ApiOnlyFrame(context, profile,
                frameClosed, false);
        CustomApplication application = new CustomApplication(provider, Lookups.singleton(resolver));
        AssertionError failure = assertThrows(AssertionError.class,
                () -> JavaFxTestSupport.onFx(() -> {
                    application.start(new Stage());
                    return null;
                }));
        assertTrue(failure.getCause().getMessage().contains("did not mount"));
        assertEquals(1, frameClosed.get());
        assertEquals(1, resolverClosed.get());
    }

    @Test
    public void rawDockAttachmentWithoutHostMountIsRejected() {
        AtomicInteger frameClosed = new AtomicInteger();
        ShellProvider provider = (context, profile) -> new ApiOnlyFrame(context, profile,
                frameClosed, true, false);
        CustomApplication application = new CustomApplication(provider);
        AssertionError failure = assertThrows(AssertionError.class,
                () -> JavaFxTestSupport.onFx(() -> {
                    application.start(new Stage());
                    return null;
                }));
        assertTrue(failure.getCause().getMessage().contains("did not mount"));
        assertEquals(1, frameClosed.get());
    }

    private static final class CustomApplication extends ShellApplication {

        private final ShellProvider provider;
        private final Lookup extra;

        private CustomApplication(ShellProvider provider) {
            this(provider, Lookup.EMPTY);
        }

        private CustomApplication(ShellProvider provider, Lookup extra) {
            this.provider = provider;
            this.extra = extra;
        }

        @Override
        protected Lookup shellLookup() {
            return new ProxyLookup(Lookups.fixed(provider), extra, super.shellLookup());
        }
    }

    /** Implements only public shell API and JavaFX types. */
    private static final class ApiOnlyFrame implements ShellFrame {

        private final ShellContext context;
        private final ShellRegionHost host;
        private final StackPane root = new StackPane();
        private final AtomicInteger closed;
        private final boolean mountDock;
        private final boolean useHost;
        private ShellRegionSpec dock;
        private Node dockNode;
        private Node rawDock;

        private ApiOnlyFrame(ShellContext context, ShellChromeProfile profile,
                AtomicInteger closed, boolean mountDock) {
            this(context, profile, closed, mountDock, true);
        }

        private ApiOnlyFrame(ShellContext context, ShellChromeProfile profile,
                AtomicInteger closed, boolean mountDock, boolean useHost) {
            this.context = context;
            this.host = context.lookup().lookup(ShellRegionHost.class);
            this.closed = closed;
            this.mountDock = mountDock;
            this.useHost = useHost;
        }

        @Override
        public Node root() {
            return root;
        }

        @Override
        public void setDockRoot(Node dockRoot) {
            if (!mountDock) {
                return;
            }
            if (!useHost) {
                rawDock = dockRoot;
                root.getChildren().add(rawDock);
                return;
            }
            ShellRegionSpec region = host.regions(context.profile()).stream()
                    .filter(spec -> spec.kind().equals(ShellRegionKind.DOCK))
                    .findFirst().orElseThrow();
            dock = region;
            dockNode = host.mount(region, new ShellRegionContext(context));
            if (dockNode != dockRoot) {
                throw new IllegalStateException("Runtime dock root mismatch");
            }
            root.getChildren().add(dockNode);
        }

        @Override
        public ShellRegionHost regions() {
            return host;
        }

        @Override
        public void close() {
            if (dock != null && dockNode != null) {
                host.dispose(dock, dockNode);
                root.getChildren().remove(dockNode);
                dock = null;
                dockNode = null;
            }
            if (rawDock != null) {
                root.getChildren().remove(rawDock);
                rawDock = null;
            }
            closed.incrementAndGet();
        }
    }
}
