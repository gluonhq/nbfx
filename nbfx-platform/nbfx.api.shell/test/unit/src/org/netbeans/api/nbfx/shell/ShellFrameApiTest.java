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

import org.junit.Test;
import org.openide.util.Lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** Tests the additive shell frame/provider contracts. */
public class ShellFrameApiTest {

    @Test
    public void legacyProfilesDefaultToMainAndCanDeclareStableId() {
        assertEquals("main", ShellChromeProfile.ALL.id());
        ShellChromeProfile custom = new ShellChromeProfile() {
            @Override
            public boolean includeMenu(String menuId) {
                return true;
            }

            @Override
            public boolean includeMenuItem(String menuId, String itemId) {
                return true;
            }

            @Override
            public boolean includeToolBar(String toolBarId) {
                return true;
            }

            @Override
            public String id() {
                return "floating-editor";
            }
        };
        assertEquals("floating-editor", custom.id());
    }

    @Test
    public void regionContextRequiresShellAndRendererDisposalIsOptional() {
        assertThrows(NullPointerException.class, () -> new ShellRegionContext(null));
        ShellRegionRenderer renderer = new ShellRegionRenderer() {
            @Override
            public ShellRegionKind kind() {
                return ShellRegionKind.BRAND;
            }

            @Override
            public javafx.scene.Node render(ShellRegionSpec region, ShellRegionContext context) {
                return null;
            }
        };
        renderer.dispose(new ShellRegionSpec(ShellRegionId.BRAND, ShellRegionKind.BRAND,
                0, java.util.Set.of(), org.netbeans.api.nbfx.layout.AreaStyle.EMPTY, true), null);
    }
}
