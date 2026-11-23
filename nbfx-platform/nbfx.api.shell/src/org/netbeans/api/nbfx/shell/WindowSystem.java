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
package org.netbeans.api.nbfx.shell;

/**
 * The nbfx window-system lifecycle. This is deliberately not NetBeans'
 * {@code org.netbeans.core.WindowSystem}: that legacy hook is only driven by the stock Swing
 * {@code GuiRunLevel}, while nbfx owns its lifecycle contract and drives it from the JavaFX
 * Application Thread.
 * <p>
 * The implementation lives in {@code nbfx.platform.workspace} and is looked up as this service.
 * The system merges the layer defaults derived from the view registrations with the persisted state
 * maintained by the shell, and talks to the live window only through {@link WindowSink}. All methods
 * run on the JavaFX Application Thread.
 * <p>
 * Lifecycle: {@link #init()} → {@link #load()} → {@link #show()}; {@link #save()} and
 * {@link #resetLayout()} are valid once loaded and remain valid while shown. Calling them in an
 * earlier phase throws {@link IllegalStateException}.
 *
 * @since 1.0
 */
public interface WindowSystem {

    /**
     * Registers the live shell adapter; called from the shell's {@code start()} before the first
     * {@link #load()}. Until then a no-op sink is used.
     *
     * @param sink the JavaFX shell adapter
     */
    void setSink(WindowSink sink);

    /** Initializes the lifecycle; no window state is touched yet. */
    void init();

    /** Resolves and applies the layer/default and persisted state to the shell. */
    void load();

    /** Makes the main window visible. */
    void show();

    /** Hides the main window, before exit or while the state is being saved. */
    void hide();

    /** Captures the live shell state and persists it. */
    void save();

    /**
     * Drops the persisted state and reloads the layer defaults. Valid after {@link #load()},
     * including while the window is shown.
     *
     * @throws IllegalStateException when called before {@link #load()}
     */
    void resetLayout();

    /**
     * Adds a lifecycle listener.
     *
     * @param listener the listener to add
     */
    void addWindowSystemListener(WindowSystemListener listener);

    /**
     * Removes a lifecycle listener.
     *
     * @param listener the listener to remove
     */
    void removeWindowSystemListener(WindowSystemListener listener);
}
