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
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.layout.DockGroupId;

/**
 * Public docking and window operations over stable content and group identities. Products use this
 * instead of touching tab panes directly; {@link org.netbeans.api.nbfx.view.ViewManager} shows and
 * hides views and {@link ShellWindow} reaches the window itself.
 * <p>
 * Methods that change the scene graph must be called on the JavaFX Application Thread.
 *
 * @since 1.0
 */
public interface WindowManager {

    /**
     * Every live dock group, in layout order, then detached windows.
     *
     * @return the group ids, never {@code null}
     */
    List<DockGroupId> groups();

    /**
     * The first group playing {@code role}, or empty when it does not exist (yet).
     *
     * @param role the pane role
     * @return the group id, or empty
     */
    Optional<DockGroupId> primary(PaneRole role);

    /**
     * The group that currently holds {@code content}, or empty when the content is not docked.
     *
     * @param content the content id
     * @return the group id, or empty
     */
    Optional<DockGroupId> groupOf(ContentId content);

    /**
     * Selects {@code content} and brings its window to the front. A content id that is not docked
     * is ignored.
     *
     * @param content the content to select
     */
    void select(ContentId content);

    /**
     * Moves {@code content} to position {@code index} of {@code target}, removing it from whichever
     * group currently holds it. This neither selects the content nor fronts its window.
     *
     * @param content the content to move
     * @param target  the destination group
     * @param index   the position among the target's content
     */
    void move(ContentId content, DockGroupId target, int index);

    /**
     * Attaches {@code content} to {@code group} at {@code index}, selects it and fronts its window.
     * A content id that is not open or a group that does not exist is ignored.
     *
     * @param content the content to attach
     * @param group   the destination group
     * @param index   the position among the target's content, or {@code -1} to append
     */
    void attach(ContentId content, DockGroupId group, int index);

    /**
     * Evaluates whether {@code request} can be performed now.
     *
     * @param request the dock request
     * @return the decision, never {@code null}
     */
    DockDecision canDock(DockRequest request);

    /**
     * Performs {@code request} if it is allowed.
     *
     * @param request the dock request
     * @return the result, never {@code null}
     */
    DockResult dock(DockRequest request);

    /**
     * Floats {@code content} into a new window, preserving its content instance.
     *
     * @param content the content to float
     * @param options initial window options
     * @return the floating window
     * @throws IllegalArgumentException when the content is not docked or the options are invalid
     * @since 1.12
     */
    DockWindow floatContent(ContentId content, WindowOptions options);

    /**
     * Floats a non-main dock group into a window, preserving its pane and content instances.
     *
     * @param group the group to float
     * @param options initial window options
     * @return the floating window
     * @throws IllegalArgumentException when the group cannot be floated or the options are invalid
     * @since 1.12
     */
    DockWindow floatGroup(DockGroupId group, WindowOptions options);

    /**
     * Redocks the content of {@code window} according to {@code request}. Group placements move all
     * tabs into the target group; split/edge placements reattach the whole floating pane.
     *
     * @param window the floating window
     * @param request the requested dock placement
     * @return the result, never {@code null}
     * @since 1.12
     */
    DockResult redock(DockWindow window, DockRequest request);

    /**
     * Every live window managed by this manager, main window first followed by floating windows.
     * Custom product windows are enumerated when the multi-window runtime is installed.
     *
     * @return the windows, never {@code null}
     * @since 1.12
     */
    List<DockWindow> windows();
}
