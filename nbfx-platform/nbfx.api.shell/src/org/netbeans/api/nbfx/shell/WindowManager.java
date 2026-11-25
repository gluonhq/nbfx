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
}
