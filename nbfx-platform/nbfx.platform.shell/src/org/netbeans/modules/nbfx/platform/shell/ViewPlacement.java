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

import javafx.scene.control.TabPane;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.api.nbfx.views.DockLocation;
import org.netbeans.api.nbfx.views.ViewArea;
import org.netbeans.modules.nbfx.platform.workspace.ViewSpec;

/**
 * Resolves where a view belongs. The registered {@link ViewSpec#area() area} wins; without one,
 * the area's location, the legacy {@link ViewProvider#getDefaultLocation() location} and finally
 * the composition's default view role are tried in that order.
 *
 * @since 1.0
 */
public final class ViewPlacement {

    private ViewPlacement() {
    }

    /** The pane a registered view belongs in, without an explicit area request. */
    public static TabPane homeFor(ViewSpec spec, ViewProvider view) {
        return homeFor(spec, view, null);
    }

    /**
     * The pane a view belongs in.
     *
     * @param spec      the registration, or {@code null} for an unregistered view
     * @param view      the view, or {@code null} when only the registration is known
     * @param requested an explicitly requested area, or {@code null}
     * @return the pane, or {@code null} when the dock area is not built
     */
    public static TabPane homeFor(ViewSpec spec, ViewProvider view,
            ViewArea requested) {
        ViewArea area = requested != null
                ? requested : spec == null ? null : spec.area();
        TabPane target = area == null ? null : Docking.paneOf(area);
        if (target == null && view != null) {
            target = Docking.paneAt(view.getDefaultLocation());
        }
        if (target == null) {
            target = NbfxTabPane.paneWithRole(defaultViewRole());
        }
        return target;
    }

    /** The default view role from the composition, or the registry fallback without one. */
    public static NbfxTabPane.PaneRole defaultViewRole() {
        WindowComposition composition = Docking.composition();
        return composition == null
                ? WindowPaneRegistry.defaultViewRole()
                : composition.defaultViewRole();
    }
}
