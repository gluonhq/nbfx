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

import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.Test;
import org.netbeans.modules.nbfx.platform.shell.api.ShellContext;
import org.netbeans.modules.nbfx.platform.shell.api.ShellWindow;
import org.netbeans.modules.nbfx.platform.shell.api.ShellWindowProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The shell window is published globally for {@code @OnUiReady} tasks and through the shell
 * context for context-aware contributions.
 */
public class ShellWindowProviderTest {

    private static final class Window implements ShellWindow {
        @Override
        public Stage stage() {
            return null;
        }

        @Override
        public BorderPane root() {
            return null;
        }

        @Override
        public TabPane mainPane() {
            return null;
        }

        @Override
        public Node slidingArea() {
            return null;
        }
    }

    @Test
    public void providerAndContextExposeTheSameWindow() {
        ShellWindow window = new Window();
        ShellWindowProviderService.set(window);
        try {
            ShellWindowProvider provider = new ShellWindowProviderService();
            assertSame(window, provider.window());
            assertSame(window, new ShellContext(Lookups.fixed(window)).window());
        } finally {
            ShellWindowProviderService.set(null);
        }
        assertNull(new ShellWindowProviderService().window());
    }
}
