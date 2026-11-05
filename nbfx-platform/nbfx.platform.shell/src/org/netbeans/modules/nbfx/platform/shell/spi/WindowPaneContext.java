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
package org.netbeans.modules.nbfx.platform.shell.spi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.netbeans.modules.nbfx.platform.shell.api.ShellWindow;
import org.openide.util.Lookup;

/**
 * The narrow context a {@link WindowPaneProvider} receives while the shell is being composed. It
 * carries the lookup the shell uses for contributions and the registered panes of the resolved
 * composition, so a provider does not have to reach for the global lookup.
 * <p>
 * The shell window is available once the window structure exists; during pane creation the
 * {@link #window() window} is the shell facade but its root and main pane are not assigned yet.
 *
 * @param lookup the shell lookup
 * @param panes  the registered panes of the resolved composition
 * @param window the shell window facade, or {@code null}
 * @since 1.0
 */
public record WindowPaneContext(Lookup lookup, List<WindowPane> panes, ShellWindow window) {

    public WindowPaneContext {
        Objects.requireNonNull(lookup, "lookup");
        panes = List.copyOf(panes);
    }

    /** The registered pane with {@code id}, if declared. */
    public Optional<WindowPane> pane(String id) {
        return panes.stream().filter(pane -> pane.id().equalsIgnoreCase(id)).findFirst();
    }

    /** The shell window once it is available. */
    public Optional<ShellWindow> shellWindow() {
        return Optional.ofNullable(window);
    }
}
