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

import java.util.Map;
import java.util.Objects;

/**
 * Product defaults for chrome-profile selection.
 *
 * @param id stable policy id
 * @param defaultProfiles profile id by window kind
 * @param fallbackProfile profile used when a kind has no usable default
 * @since 1.4
 */
public record ChromeProfilePolicy(
        String id,
        Map<WindowKind, String> defaultProfiles,
        String fallbackProfile) {

    public ChromeProfilePolicy {
        id = ShellTokens.require(id, "id");
        defaultProfiles = Map.copyOf(Objects.requireNonNull(defaultProfiles, "defaultProfiles"));
        for (Map.Entry<WindowKind, String> entry : defaultProfiles.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "windowKind");
            ShellTokens.require(entry.getValue(), "profileId");
        }
        fallbackProfile = ShellTokens.require(fallbackProfile, "fallbackProfile");
    }
}
