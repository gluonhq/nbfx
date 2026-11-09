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
package org.netbeans.api.nbfx.view;

import java.util.List;
import java.util.Optional;
import org.netbeans.api.nbfx.views.ViewArea;

/**
 * Shows and hides {@link ViewProvider views}. The implementation lives in the launcher and is
 * registered in the global {@code Lookup}; it owns the tab of each view and knows which pane - or
 * detached window - that tab is currently in.
 */
public interface ViewManager {

    /**
     * Brings {@code view} on screen: selects its tab and fronts its window if it has one, otherwise
     * docks a new tab at {@link ViewProvider#getDefaultLocation()}. Must be called on the FX thread.
     */
    void show(ViewProvider view);

    /**
     * Shows {@code view} at an explicitly declared area and position. The default delegates to
     * {@link #show(ViewProvider)} for implementations that do not support explicit placement.
     *
     * @param view     the view to show
     * @param area     the declared area, or {@code null} to use the view's default
     * @param position the position among the views in the area
     */
    default void show(ViewProvider view, ViewArea area, int position) {
        show(view);
    }

    /** Closes the tab of {@code view}, wherever it is; does nothing if it has none. */
    void hide(ViewProvider view);

    /** Whether {@code view} currently has a tab, docked or detached. */
    boolean isShowing(ViewProvider view);

    /**
     * The views that currently have a tab, in a stable kind/position/id order. The list is a
     * snapshot; use {@link #addOpenViewsListener(Runnable)} to follow changes. The default returns
     * an empty list for implementations that do not track open views.
     *
     * @return the open views, never {@code null}
     * @since 1.6
     */
    default List<ViewInfo> openViews() {
        return List.of();
    }

    /**
     * The view that is currently the active component, if the focused pane holds a view tab. The
     * default returns an empty value.
     *
     * @return the active view, or empty when an editor or nothing is active
     * @since 1.6
     */
    default Optional<ViewInfo> activeView() {
        return Optional.empty();
    }

    /**
     * Brings the open view with {@code viewId} on screen, including a detached window. Does nothing
     * when no tab with that id is open. The default does nothing.
     *
     * @param viewId the stable view id
     * @since 1.6
     */
    default void activate(String viewId) {
    }

    /**
     * Registers a listener invoked when the set of open views or the active view changes. The
     * default ignores the listener.
     *
     * @param listener the listener to add
     * @since 1.6
     */
    default void addOpenViewsListener(Runnable listener) {
    }

    /**
     * Removes a listener added with {@link #addOpenViewsListener(Runnable)}.
     *
     * @param listener the listener to remove
     * @since 1.6
     */
    default void removeOpenViewsListener(Runnable listener) {
    }
}
