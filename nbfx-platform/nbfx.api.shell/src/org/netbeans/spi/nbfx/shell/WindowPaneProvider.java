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

import org.netbeans.api.nbfx.shell.WindowPaneHost;

/**
 * Creates the pane for a registered {@link WindowPane}. Products register a provider in the shell
 * lookup when the pane needs product services. The editor pane is always resolved through the
 * content manager and never through a provider.
 * <p>
 * A provider obtains the shell-owned host from {@link WindowPaneContext#createHost(WindowPane)} and
 * fills it; it never constructs the shell's concrete tab pane, which is internal.
 *
 * @since 1.0
 */
public interface WindowPaneProvider {

    /** Priority among providers; the highest priority that returns a pane wins. */
    default int priority() {
        return 0;
    }

    /**
     * Creates the pane for {@code pane}, or returns {@code null} to let the next provider (or the
     * shell's default) handle it. The returned host must carry {@link WindowPane#role() the declared
     * role}; the window composition and persisted layouts are keyed by it.
     *
     * @param pane    the declaration to instantiate
     * @param context the narrow shell context, which creates the host
     * @return the pane host, or {@code null} to decline
     */
    WindowPaneHost createPane(WindowPane pane, WindowPaneContext context);

    /**
     * Called once the pane created by this provider is no longer needed. The default does nothing;
     * a provider that owns resources should release them here.
     *
     * @param pane the pane this provider created
     */
    default void disposePane(WindowPaneHost pane) {
    }
}
