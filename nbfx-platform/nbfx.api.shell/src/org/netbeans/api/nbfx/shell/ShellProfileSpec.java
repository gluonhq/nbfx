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

import java.util.Objects;
import java.util.Set;

/**
 * Immutable declarative chrome profile. Region and entry applicability are intersected; an empty
 * region set adds no profile-level restriction.
 *
 * @param id stable profile id
 * @param windowKind optional kind for product-default selection
 * @param regions allowed region ids, or an empty set for all profile-applicable regions
 * @param decoration optional decoration override
 * @param systemMenuBar whether the menu bar uses the platform menu bar
 * @since 1.4
 */
public record ShellProfileSpec(
        String id,
        WindowKind windowKind,
        Set<ShellRegionId> regions,
        WindowDecoration decoration,
        boolean systemMenuBar) implements ShellChromeProfile {

    public ShellProfileSpec {
        id = ShellTokens.require(id, "id");
        regions = Set.copyOf(Objects.requireNonNull(regions, "regions"));
    }

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
    public boolean includeRegion(ShellRegionId regionId) {
        return regions.isEmpty() || regions.contains(regionId);
    }

    @Override
    public boolean useSystemMenuBar() {
        return systemMenuBar;
    }
}
