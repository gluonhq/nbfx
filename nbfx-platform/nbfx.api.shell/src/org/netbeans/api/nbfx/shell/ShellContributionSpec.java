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
 * Immutable metadata for one shell contribution. Discovery never constructs the contribution or its
 * Node.
 *
 * @param id stable layer registration id
 * @param region owning shell region
 * @param position order within the region
 * @param profiles exact atomic profile ids; empty means all profiles
 * @param type contribution implementation class
 * @since 1.2
 */
public record ShellContributionSpec(
        String id,
        ShellRegionId region,
        int position,
        Set<String> profiles,
        Class<? extends ShellContribution> type) {

    public ShellContributionSpec {
        id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        region = Objects.requireNonNull(region, "region");
        profiles = Set.copyOf(Objects.requireNonNull(profiles, "profiles"));
        for (String profile : profiles) {
            if (!ShellTokens.isToken(profile)) {
                throw new IllegalArgumentException("profile ids must be nonblank atomic tokens");
            }
        }
        type = Objects.requireNonNull(type, "type");
    }
}
