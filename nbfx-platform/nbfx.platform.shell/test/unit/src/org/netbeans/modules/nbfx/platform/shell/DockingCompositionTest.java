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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.netbeans.api.nbfx.view.DockLocation;
import org.netbeans.api.nbfx.view.ViewArea;
import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane.PaneRole;
import org.netbeans.modules.nbfx.platform.shell.spi.WindowPane;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Runtime placement resolves against the composition the shell started with, not a later registry
 * read.
 */
public class DockingCompositionTest {

    @Test
    public void areaAndLifecycleLookupsUseTheResolvedComposition() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
            NbfxTabPane custom = new NbfxTabPane(PaneRole.of("CUSTOM"));
            WindowPane declaration = new WindowPane("CUSTOM", "Custom", PaneKind.VIEW,
                    "custom-area", true, 30);
            WindowComposition composition = new WindowComposition(List.of(declaration), null);
            Map<String, NbfxTabPane> panes = new LinkedHashMap<>();
            panes.put(PaneRole.MAIN.name(), main);
            panes.put("CUSTOM", custom);

            Docking.create(composition, panes);

            assertSame(composition, Docking.composition());
            assertSame(custom, Docking.paneOf(
                    new ViewArea("custom-area", "Custom", DockLocation.RIGHT, 0.2)));
            assertTrue(Docking.keepsWhenEmpty(custom));
            return null;
        });
    }
}
