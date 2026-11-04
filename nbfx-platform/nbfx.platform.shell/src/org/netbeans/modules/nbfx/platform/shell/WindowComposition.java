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

import java.util.List;
import java.util.Optional;
import org.netbeans.modules.nbfx.platform.shell.spi.WindowPane;
import org.netbeans.modules.nbfx.platform.workspace.DockTree;

/**
 * The resolved first-run composition: registered panes and the declared dock tree.
 *
 * @param panes the registered panes
 * @param tree  the first-run tree, or {@code null} to use the area's default shape
 * @since 1.0
 */
public record WindowComposition(List<WindowPane> panes, DockTree tree) {

    public WindowComposition {
        panes = List.copyOf(panes);
    }

    /** The pane with {@code id}, if registered. */
    public Optional<WindowPane> pane(String id) {
        return panes.stream().filter(pane -> pane.id().equalsIgnoreCase(id)).findFirst();
    }

    /** The pane hosting {@code areaId}, if a registered pane declares it. */
    public Optional<WindowPane> paneForArea(String areaId) {
        if (areaId == null || areaId.isBlank()) {
            return Optional.empty();
        }
        return panes.stream().filter(pane -> areaId.equalsIgnoreCase(pane.area())).findFirst();
    }

    /**
     * The default role for a view when no area resolves; never the editor pane, and the built-in
     * navigator role when the composition declares no view pane.
     */
    public NbfxTabPane.PaneRole defaultViewRole() {
        return WindowPaneRegistry.defaultViewRole(panes);
    }
}
