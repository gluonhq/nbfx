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
import java.util.Set;
import org.junit.Test;
import org.netbeans.api.nbfx.shell.ShellProfileRegistry;
import org.netbeans.api.nbfx.shell.ShellRegionId;
import org.netbeans.api.nbfx.shell.ShellRegionKind;
import org.netbeans.api.nbfx.shell.ShellRegionRegistry;
import org.netbeans.api.nbfx.shell.ShellRegionSpec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies the neutral default shell-region layer declaration. */
public class PlatformShellRegionsTest {

    @Test
    public void declaresRequiredDefaultRegionsAndOneDock() {
        List<ShellRegionSpec> regions = ShellRegionRegistry.discover();

        assertEquals(Set.of(ShellRegionId.BRAND, ShellRegionId.MENUBAR, ShellRegionId.TOOLBAR,
                ShellRegionId.DOCK, ShellRegionId.STATUS, ShellRegionId.OVERLAY),
                regions.stream().map(ShellRegionSpec::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, regions.stream().filter(region -> region.id().equals(ShellRegionId.DOCK)).count());
        ShellRegionSpec dock = regions.stream()
                .filter(region -> region.id().equals(ShellRegionId.DOCK))
                .findFirst().orElseThrow();
        assertEquals(ShellRegionKind.DOCK, dock.kind());
        assertTrue(regions.stream().allMatch(ShellRegionSpec::visibleByDefault));
        Set<String> profiles = Set.of("main", "floating-editor", "floating-tool", "presentation");
        assertTrue(regions.stream().allMatch(region -> region.profiles().isEmpty()));
        assertEquals(profiles, ShellProfileRegistry.discover().stream()
                .map(profile -> profile.id()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(profiles.stream().allMatch(profile -> ShellRegionRegistry.forProfile(profile).stream()
                .filter(region -> region.kind().equals(ShellRegionKind.DOCK)).count() == 1));
        assertEquals(1, ShellRegionRegistry.forProfile("product:custom").stream()
                .filter(region -> region.kind().equals(ShellRegionKind.DOCK)).count());
    }
}
