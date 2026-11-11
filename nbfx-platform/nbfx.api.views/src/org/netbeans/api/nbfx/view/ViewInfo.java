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

import java.util.Objects;
import org.netbeans.api.nbfx.views.ViewArea;
import org.netbeans.api.nbfx.views.ViewKind;

/**
 * A currently open view tab, as reported by {@link ViewManager#openViews()}.
 *
 * <p>This is a metadata read model, not the view itself: it carries the stable id, the registered
 * display name, the kind and the current state. Use {@link ViewManager#activate(String)} to bring
 * the view on screen; the {@link ViewProvider} instance stays owned by the shell.</p>
 *
 * @param id          the stable view id
 * @param displayName the registered display name
 * @param iconName    the registered icon resource name, or an empty string
 * @param kind        the registered view kind
 * @param area        the area the view was registered for
 * @param position    the registered position within the area
 * @param active      whether this view is the active component
 * @param detached    whether the tab currently lives in a detached window
 * @since 1.6
 */
public record ViewInfo(String id, String displayName, String iconName, ViewKind kind,
        ViewArea area, int position, boolean active, boolean detached) {

    public ViewInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(iconName, "iconName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(area, "area");
    }
}
