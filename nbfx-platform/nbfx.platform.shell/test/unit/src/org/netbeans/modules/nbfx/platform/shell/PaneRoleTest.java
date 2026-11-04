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

import org.junit.Test;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane.PaneRole;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pane roles are stable names; product roles resolve to equal values without replacing the
 * built-in singletons.
 */
public class PaneRoleTest {

    @Test
    public void builtInRolesAreCanonical() {
        assertEquals(PaneRole.MAIN, PaneRole.of("MAIN"));
        assertEquals(PaneRole.NAVIGATOR, PaneRole.of("NAVIGATOR"));
        assertTrue(PaneRole.MAIN.isMain());
        assertTrue(PaneRole.NAVIGATOR.isPermanent());
        assertFalse(PaneRole.DOCKED.isPermanent());
    }

    @Test
    public void mainIsTheOnlyEditorKind() {
        assertEquals(org.netbeans.api.nbfx.views.PaneKind.EDITOR, PaneRole.MAIN.defaultKind());
        assertEquals(org.netbeans.api.nbfx.views.PaneKind.VIEW, PaneRole.NAVIGATOR.defaultKind());
        assertEquals(org.netbeans.api.nbfx.views.PaneKind.VIEW, PaneRole.of("RIGHT_SIDEBAR").defaultKind());
    }

    @Test
    public void productRolesCompareByStableName() {
        PaneRole first = PaneRole.of("RIGHT_SIDEBAR");
        PaneRole second = PaneRole.of("RIGHT_SIDEBAR");
        assertEquals(first, second);
        assertEquals("RIGHT_SIDEBAR", first.name());
        assertTrue(first.isPermanent());
    }
}
