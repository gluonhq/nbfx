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

import javafx.scene.layout.StackPane;
import org.junit.Test;
import org.netbeans.api.nbfx.shell.DockWindow;
import org.netbeans.api.nbfx.shell.ShellChromeProfile;
import org.netbeans.api.nbfx.shell.WindowContent;
import org.netbeans.api.nbfx.shell.WindowContentState;
import org.netbeans.api.nbfx.shell.WindowId;
import org.netbeans.api.nbfx.shell.WindowKind;
import org.netbeans.api.nbfx.shell.WindowOptions;
import org.netbeans.modules.nbfx.platform.content.DefaultContentManager;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.*;

public class CustomWindowTest {

    @Test
    public void createsEnumeratesAndClosesProductWindow() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            DefaultWindowManager manager = new DefaultWindowManager(new DefaultContentManager());
            WindowId id = WindowId.of("product:window.inspector");
            FakeContent content = new FakeContent(id);
            ShellChromeProfile profile = new ShellChromeProfile() {
                @Override public boolean includeMenu(String menuId) { return true; }
                @Override public boolean includeMenuItem(String menuId, String itemId) { return true; }
                @Override public boolean includeToolBar(String toolBarId) { return true; }
                @Override public String id() { return "product.inspector"; }
            };
            WindowOptions options = new WindowOptions(WindowKind.CUSTOM, profile, "Inspector",
                    null, null, false, 500, 350, true, false, null);
            DockWindow window = manager.createCustomWindow(id, options, content);
            try {
                assertEquals(id, window.id());
                assertEquals(WindowKind.CUSTOM, window.kind());
                assertSame(content, window.content());
                assertSame(content.root, window.content().root());
                assertNull(window.layout());
                assertTrue(window.window().isShowing());
                assertEquals("product.inspector", window.state().profileId());
                assertEquals("custom", window.content().lookup().lookup(String.class));
                assertEquals(java.util.List.of(window), manager.windows());

                assertThrows(IllegalArgumentException.class,
                        () -> manager.createCustomWindow(id, options, new FakeContent(id)));
                assertThrows(IllegalArgumentException.class,
                        () -> manager.createCustomWindow(WindowId.of("product:window.other"), options, content));

                window.hide();
                assertFalse(window.window().isShowing());
                assertEquals(java.util.List.of(window), manager.windows());
                assertFalse(content.closed);
                window.show();
                assertTrue(window.window().isShowing());
            } finally {
                window.close();
            }
            assertFalse(window.window().isShowing());
            assertTrue(content.closed);
            assertTrue(manager.windows().isEmpty());
            assertThrows(IllegalStateException.class, window::show);
            return null;
        });
    }

    private static final class FakeContent implements WindowContent {

        private final WindowId id;
        private final StackPane root = new StackPane();
        private boolean closed;

        FakeContent(WindowId id) {
            this.id = id;
        }

        @Override
        public StackPane root() {
            return root;
        }

        @Override
        public Lookup lookup() {
            return Lookups.singleton("custom");
        }

        @Override
        public WindowContentState saveState() {
            return WindowContentState.empty(id, null);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
