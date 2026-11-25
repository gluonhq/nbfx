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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Tests immutable chrome-profile contracts. */
public class ShellProfileApiTest {

    @Test
    public void profileSpecCopiesRegionsAndFiltersByAllowList() {
        Set<ShellRegionId> regions = new HashSet<>();
        regions.add(ShellRegionId.DOCK);
        ShellProfileSpec profile = new ShellProfileSpec("floating-editor", WindowKind.FLOATING,
                regions, WindowDecoration.NATIVE, false);
        regions.clear();
        assertEquals(Set.of(ShellRegionId.DOCK), profile.regions());
        assertTrue(profile.includeRegion(ShellRegionId.DOCK));
        assertFalse(profile.includeRegion(ShellRegionId.BRAND));
        assertFalse(profile.useSystemMenuBar());
        assertTrue(profile.includeMenu("File"));
    }

    @Test
    public void emptyAllowListAndLegacyProfilesRemainInclusive() {
        ShellProfileSpec unrestricted = new ShellProfileSpec("product:custom", null,
                Set.of(), null, true);
        assertTrue(unrestricted.includeRegion(ShellRegionId.BRAND));
        assertTrue(ShellChromeProfile.all().includeRegion(ShellRegionId.DOCK));
        assertEquals("main", ShellChromeProfile.all().id());
    }

    @Test
    public void policyCopiesDefaultsAndOptionsValidateDimensions() {
        Map<WindowKind, String> defaults = new java.util.HashMap<>();
        defaults.put(WindowKind.MAIN, "main");
        ChromeProfilePolicy policy = new ChromeProfilePolicy("product", defaults, "main");
        defaults.clear();
        assertEquals(Map.of(WindowKind.MAIN, "main"), policy.defaultProfiles());
        assertThrows(IllegalArgumentException.class,
                () -> new WindowOptions(WindowKind.MAIN, null, "Title", null, null,
                        false, 0.0d, 100.0d, true, true, null));
        assertThrows(IllegalArgumentException.class, () -> new ShellProfileHint("bad hint"));
    }
}
