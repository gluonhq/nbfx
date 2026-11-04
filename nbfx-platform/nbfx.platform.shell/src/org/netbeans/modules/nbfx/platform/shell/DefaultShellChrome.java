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

import org.netbeans.modules.nbfx.platform.shell.api.ShellChrome;
import org.netbeans.modules.nbfx.platform.shell.api.ShellContext;
import org.netbeans.modules.nbfx.platform.shell.api.ShellCommandResolver;

import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.netbeans.modules.nbfx.platform.status.StatusBar;
import org.netbeans.modules.nbfx.platform.status.StatusElementRegistry;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 * The platform's default chrome: menu bar and tool bars above the docking area and the composed
 * status bar below it. Concrete controls stay here; the product-facing {@link ShellChrome} contract
 * only exposes nodes.
 *
 * @since 1.0
 */
public final class DefaultShellChrome implements ShellChrome, ToolBarHost {

    private final ShellContext context;
    private final ShellMenuBar menuBuilder;
    private final ShellToolBars toolBarBuilder;
    private final VBox top;
    private final StatusBar statusBar;

    /** Creates the default chrome for {@code context}. */
    public DefaultShellChrome(ShellContext context) {
        this.context = context;
        // The tool bars are built first so the View section can observe the container they produce.
        this.toolBarBuilder = new ShellToolBars(context);
        ShellContext menuContext = new ShellContext(new ProxyLookup(
                Lookups.fixed(toolBarBuilder.container()), context.lookup()));
        this.menuBuilder = new ShellMenuBar(menuContext);
        boolean hasMenus = !menuBuilder.menuBar().getMenus().isEmpty();
        boolean hasToolBars = !toolBarBuilder.container().getToolBars().isEmpty();
        this.top = hasMenus || hasToolBars
                ? new VBox(menuBuilder.menuBar(), toolBarBuilder.container())
                : null;
        this.statusBar = new StatusBar(StatusElementRegistry.discover());
    }

    /** The context this chrome was created for. */
    ShellContext context() {
        return context;
    }

    @Override
    public ToolBarContainer toolBars() {
        return toolBarBuilder.container();
    }

    @Override
    public Node top() {
        return top;
    }

    @Override
    public Node bottom() {
        return statusBar;
    }

    @Override
    public void dispose() {
        menuBuilder.dispose();
        statusBar.dispose();
        ShellCommandResolver resolver = context.lookup().lookup(ShellCommandResolver.class);
        if (resolver != null) {
            resolver.close();
        }
    }
}
