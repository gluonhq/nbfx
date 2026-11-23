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
package org.netbeans.spi.nbfx.shell;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.netbeans.api.nbfx.shell.ShellWindow;
import org.netbeans.api.nbfx.shell.WindowPaneHost;
import org.openide.util.Lookup;

/**
 * The narrow context a {@link WindowPaneProvider} receives while the shell is being composed. It
 * carries the lookup the shell uses for contributions and the registered panes of the resolved
 * composition, so a provider does not have to reach for the global lookup.
 * <p>
 * The shell window is available once the window structure exists; during pane creation the
 * {@link #window() window} is the shell facade but its root and main pane are not assigned yet.
 *
 * @param lookup      the shell lookup
 * @param panes       the registered panes of the resolved composition
 * @param window      the shell window facade, or {@code null}
 * @param paneFactory creates the shell-owned host for a declared pane
 * @since 1.0
 */
public record WindowPaneContext(Lookup lookup, List<WindowPane> panes, ShellWindow window,
        WindowPaneFactory paneFactory) {

    public WindowPaneContext {
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(paneFactory, "paneFactory");
        panes = List.copyOf(panes);
    }

    /**
     * Creates a context without a host factory. Intended for tests and for code that never asks the
     * context for a host; {@link #createHost(WindowPane)} fails when called.
     */
    public WindowPaneContext(Lookup lookup, List<WindowPane> panes, ShellWindow window) {
        this(lookup, panes, window, (pane, context) -> {
            throw new IllegalStateException("No window pane host factory in this context");
        });
    }

    /** The registered pane with {@code id}, if declared. */
    public Optional<WindowPane> pane(String id) {
        return panes.stream().filter(pane -> pane.id().equalsIgnoreCase(id)).findFirst();
    }

    /** The shell window once it is available. */
    public Optional<ShellWindow> shellWindow() {
        return Optional.ofNullable(window);
    }

    /**
     * Creates the shell-owned host for {@code pane}. A provider returns this host instead of
     * constructing the concrete tab pane, which is internal to the shell.
     *
     * @param pane the declaration to instantiate
     * @return the host, never {@code null}
     */
    public WindowPaneHost createHost(WindowPane pane) {
        return Objects.requireNonNull(paneFactory.create(pane, this), "host");
    }
}
