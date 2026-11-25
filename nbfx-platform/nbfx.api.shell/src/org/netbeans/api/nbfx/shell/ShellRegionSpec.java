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
import org.netbeans.api.nbfx.layout.AreaStyle;

/**
 * Immutable, Node-free metadata for one shell chrome region.
 *
 * @param id stable region identity
 * @param kind renderer classification
 * @param position order among sibling regions
 * @param profiles exact profile ids; empty means all profiles
 * @param style scoped visual treatment
 * @param visibleByDefault initial visibility policy
 * @since 1.1
 */
public record ShellRegionSpec(
        ShellRegionId id,
        ShellRegionKind kind,
        int position,
        Set<String> profiles,
        AreaStyle style,
        boolean visibleByDefault) {

    public ShellRegionSpec {
        id = Objects.requireNonNull(id, "id");
        kind = Objects.requireNonNull(kind, "kind");
        profiles = Set.copyOf(Objects.requireNonNull(profiles, "profiles"));
        for (String profile : profiles) {
            if (!isToken(profile)) {
                throw new IllegalArgumentException("profile ids must be nonblank atomic tokens");
            }
        }
        style = Objects.requireNonNull(style, "style");
    }

    private static boolean isToken(String value) {
        return value != null && !value.isBlank()
                && value.chars().noneMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character) || character == ',');
    }
}
