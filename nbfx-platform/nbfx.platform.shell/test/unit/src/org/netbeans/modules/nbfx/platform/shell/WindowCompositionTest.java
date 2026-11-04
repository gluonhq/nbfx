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
import org.junit.Test;
import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane.PaneRole;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The resolved composition exposes its panes and derives the default view role without asking the
 * registries again.
 */
public class WindowCompositionTest {

    private static WindowPane pane(String id, PaneKind kind, int position) {
        return new WindowPane(id, id, kind, "", true, false, position);
    }

    @Test
    public void resolvesPaneByRoleName() {
        WindowComposition composition = new WindowComposition(List.of(
                pane("MAIN", PaneKind.EDITOR, 10),
                pane("RIGHT_SIDEBAR", PaneKind.VIEW, 30)), null);

        assertTrue(composition.pane("RIGHT_SIDEBAR").isPresent());
        assertEquals(PaneRole.MAIN, composition.pane("main").orElseThrow().role());
    }

    @Test
    public void defaultViewRoleIgnoresTheEditorAndHonorsPosition() {
        WindowComposition composition = new WindowComposition(List.of(
                pane("RIGHT_SIDEBAR", PaneKind.VIEW, 30),
                pane("MAIN", PaneKind.EDITOR, 10),
                pane("LEFT_BAR", PaneKind.VIEW, 20)), null);

        assertEquals(PaneRole.of("LEFT_BAR"), composition.defaultViewRole());
        assertSame(PaneRole.MAIN, composition.pane("MAIN").orElseThrow().role());
    }
}
