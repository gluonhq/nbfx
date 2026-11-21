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

import java.util.List;
import java.util.Optional;
import javafx.scene.control.Tab;
import org.netbeans.api.nbfx.view.ViewManager;

/**
 * The panes and tabs of the running shell window. This replaces the static operations a product
 * used to call on the shell's concrete tab pane; use {@link ViewManager} to show and hide views and
 * {@link ShellWindow} to reach the window itself.
 * <p>
 * The manager is published in the shell lookup and through {@link ShellWindow#windowManager()}.
 * Methods that change the scene graph must be called on the JavaFX Application Thread.
 *
 * @since 1.0
 */
public interface WindowManager {

    /**
     * Every live pane: the docked panes in layout order, then detached windows.
     *
     * @return the panes, never {@code null}
     */
    List<WindowPaneHost> panes();

    /**
     * The single permanent pane playing {@code role}, or empty when it does not exist (yet).
     *
     * @param role the pane role
     * @return the pane, or empty
     */
    Optional<WindowPaneHost> primary(PaneRole role);

    /**
     * The pane that currently holds {@code tab}, or empty when the tab is not docked anywhere.
     *
     * @param tab the tab
     * @return the pane, or empty
     */
    Optional<WindowPaneHost> paneOf(Tab tab);

    /**
     * The open view tab whose provider id is {@code viewId}, searching every pane.
     *
     * @param viewId the stable view id
     * @return the tab, or empty
     */
    Optional<Tab> findViewTab(String viewId);

    /**
     * The stable view id stored on {@code tab}, or {@code null} when the tab is not a view tab.
     *
     * @param tab the tab
     * @return the view id, or {@code null}
     */
    String viewId(Tab tab);

    /**
     * Selects {@code tab} and brings its window to the front.
     *
     * @param tab the tab to select
     */
    void select(Tab tab);

    /**
     * Moves {@code tab} to position {@code index} of {@code target}, removing it from whichever pane
     * currently holds it. Unlike {@link #attach(WindowPaneHost, Tab)} this neither selects the tab
     * nor fronts its window, so a layout can be rebuilt without stealing focus.
     *
     * @param tab    the tab to move
     * @param target the destination pane
     * @param index  the position among the target's tabs
     */
    void move(Tab tab, WindowPaneHost target, int index);

    /**
     * Appends {@code tab} to {@code pane}, selects it and fronts its window.
     *
     * @param pane the destination pane
     * @param tab  the tab to attach
     */
    void attach(WindowPaneHost pane, Tab tab);
}
