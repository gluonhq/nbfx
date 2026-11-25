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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.After;
import org.junit.Test;
import org.netbeans.api.nbfx.product.AboutInfo;
import org.netbeans.api.nbfx.product.Product;
import org.netbeans.api.nbfx.product.ProductIdentity;
import org.netbeans.api.nbfx.product.ProductIcons;
import org.netbeans.api.nbfx.product.ProductProvider;
import org.netbeans.api.nbfx.product.ProductStyle;
import org.netbeans.api.nbfx.shell.ChromeProfilePolicy;
import org.netbeans.api.nbfx.shell.ChromeProfilePolicyProvider;
import org.netbeans.api.nbfx.shell.ShellChromeProfile;
import org.netbeans.api.nbfx.shell.ShellContext;
import org.netbeans.api.nbfx.shell.ShellFrame;
import org.netbeans.api.nbfx.shell.ShellProvider;
import org.netbeans.api.nbfx.shell.ShellRegionContext;
import org.netbeans.api.nbfx.shell.ShellRegionHost;
import org.netbeans.api.nbfx.shell.ShellRegionKind;
import org.netbeans.api.nbfx.shell.ShellRegionSpec;
import org.netbeans.api.nbfx.shell.WindowKind;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** One-start integration fixture for profile publication and window decoration. */
public class ShellProfileStartupTest {

    @After
    public void hideWindows() throws InterruptedException {
        JavaFxTestSupport.hideWindows();
    }

    @Test
    public void productProfileIsPublishedBeforeProviderCreation() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            AtomicReference<ShellChromeProfile> received = new AtomicReference<>();
            ShellProvider provider = (context, profile) -> {
                received.set(profile);
                assertSame(profile, context.profile());
                assertSame(profile, context.lookup().lookup(ShellChromeProfile.class));
                return new Frame(context);
            };
            ChromeProfilePolicy policy = new ChromeProfilePolicy("product:chrome",
                    Map.of(WindowKind.MAIN, "presentation"), "main");
            ChromeProfilePolicyProvider policyProvider = () -> policy;
            Product product = new Product(
                    new ProductIdentity("example", "Example", "1.0", null, null, null, null),
                    new ProductIcons(List.of(), null, List.of()),
                    new ProductStyle(List.of(), List.of(), List.of()),
                    "theme.default", "example.default", "shell.default", "product:chrome",
                    List.of(), new AboutInfo("", List.of(), List.of(), null));
            ProductProvider productProvider = () -> product;
            Application application = new Application(provider,
                    Lookups.fixed(policyProvider, productProvider));
            Stage stage = new Stage();
            application.start(stage);
            assertEquals("presentation", received.get().id());
            assertEquals(StageStyle.UNDECORATED, stage.getStyle());
            return null;
        });
    }

    private static final class Application extends ShellApplication {

        private final ShellProvider provider;
        private final Lookup extra;

        private Application(ShellProvider provider, Lookup extra) {
            this.provider = provider;
            this.extra = extra;
        }

        @Override
        protected Lookup shellLookup() {
            return new ProxyLookup(Lookups.fixed(provider), extra, super.shellLookup());
        }
    }

    private static final class Frame implements ShellFrame {

        private final ShellContext context;
        private final ShellRegionHost host;
        private final StackPane root = new StackPane();
        private ShellRegionSpec dock;
        private Node dockNode;

        private Frame(ShellContext context) {
            this.context = context;
            this.host = context.lookup().lookup(ShellRegionHost.class);
        }

        @Override
        public Node root() {
            return root;
        }

        @Override
        public void setDockRoot(Node dockRoot) {
            dock = host.regions(context.profile()).stream()
                    .filter(region -> region.kind().equals(ShellRegionKind.DOCK))
                    .findFirst().orElseThrow();
            dockNode = host.mount(dock, new ShellRegionContext(context));
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
        }
    }
}
