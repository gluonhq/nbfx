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
import org.netbeans.api.nbfx.views.ViewArea;
import org.netbeans.modules.nbfx.platform.shell.spi.WindowPane;
import org.netbeans.modules.nbfx.platform.workspace.DockTree;
import org.netbeans.modules.nbfx.platform.workspace.DockTrees;
import org.netbeans.modules.nbfx.platform.workspace.ViewAreaRegistry;
import org.netbeans.modules.nbfx.platform.workspace.WindowLayoutRegistry;

/**
 * Resolves the first-run composition from the registered panes and the declared layout. The shell
 * is one per JVM today, so the resolver and the resulting docking area are process-wide.
 *
 * @since 1.0
 */
public final class WindowCompositionResolver {

    private WindowCompositionResolver() {
    }

    /** The current first-run composition. */
    public static WindowComposition resolve() {
        List<WindowPane> panes = WindowPaneRegistry.discover();
        DockTree tree = WindowLayoutRegistry.defaultTree()
                .map(layout -> DockTrees.resolveDividers(layout,
                        role -> areaWeight(role, panes)))
                .orElse(null);
        return new WindowComposition(panes, tree);
    }

    /** The declared weight of the area a registered pane hosts, or {@code null}. */
    private static Double areaWeight(String role, List<WindowPane> panes) {
        return panes.stream()
                .filter(pane -> pane.role().name().equals(role))
                .map(WindowPane::area)
                .filter(area -> !area.isBlank())
                .flatMap(area -> ViewAreaRegistry.find(area).stream())
                .map(ViewArea::weight)
                .findFirst()
                .orElse(null);
    }
}
