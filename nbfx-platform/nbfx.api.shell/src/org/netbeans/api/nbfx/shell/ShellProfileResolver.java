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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure chrome-profile selection with deterministic precedence and neutral fallback. Discovery is
 * supplied by the caller; this utility performs no global lookup or JavaFX window operations.
 *
 * @since 1.5
 */
public final class ShellProfileResolver {

    private static final Map<WindowKind, String> NEUTRAL_FALLBACK = Map.of(
            WindowKind.MAIN, "main",
            WindowKind.FLOATING, "floating-editor",
            WindowKind.CUSTOM, "presentation");

    private ShellProfileResolver() {
    }

    /**
     * Selects a profile for {@code options}. Precedence is explicit profile, content/group hint,
     * product policy default for the window kind, policy fallback and platform neutral fallback.
     * Explicit profile objects are authoritative and need not be layer-declared. Unknown hint or
     * policy ids fall through; a missing neutral fallback is an error.
     *
     * @param options the window options
     * @param hint optional content/group profile hint
     * @param policy optional product default policy
     * @param profiles the declared profile snapshot
     * @return the selected profile and effective decoration
     */
    public static ShellProfileSelection resolve(WindowOptions options, ShellProfileHint hint,
            ChromeProfilePolicy policy, List<ShellProfileSpec> profiles) {
        Objects.requireNonNull(options, "options");
        return resolve(options.kind(), options.profile(), options.decoration(), hint, policy, profiles);
    }

    private static ShellProfileSelection resolve(WindowKind kind, ShellChromeProfile explicitProfile,
            WindowDecoration decorationOverride, ShellProfileHint hint, ChromeProfilePolicy policy,
            List<ShellProfileSpec> profiles) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(profiles, "profiles");
        Map<String, ShellProfileSpec> byId = new HashMap<>();
        for (ShellProfileSpec profile : profiles) {
            Objects.requireNonNull(profile, "profile");
            if (byId.putIfAbsent(profile.id(), profile) != null) {
                throw new IllegalArgumentException("Duplicate shell profile id: " + profile.id());
            }
        }
        ShellChromeProfile selected = explicitProfile;
        if (selected == null && hint != null) {
            selected = byId.get(hint.profileId());
        }
        if (selected == null && policy != null) {
            selected = byId.get(policy.defaultProfiles().get(kind));
            if (selected == null) {
                selected = byId.get(policy.fallbackProfile());
            }
        }
        if (selected == null) {
            selected = Optional.ofNullable(byId.get(NEUTRAL_FALLBACK.get(kind)))
                    .orElseThrow(() -> new IllegalStateException(
                            "No neutral shell profile for " + kind));
        }
        WindowDecoration decoration = decorationOverride;
        if (decoration == null && selected instanceof ShellProfileSpec spec) {
            decoration = spec.decoration();
        }
        return new ShellProfileSelection(selected,
                decoration == null ? WindowDecoration.NATIVE : decoration);
    }

    /**
     * Selects a profile for a window kind without explicit options.
     *
     * @param kind the window kind
     * @param hint optional content/group profile hint
     * @param policy optional product default policy
     * @param profiles the declared profile snapshot
     * @return the selected profile and effective decoration
     */
    public static ShellProfileSelection resolve(WindowKind kind, ShellProfileHint hint,
            ChromeProfilePolicy policy, List<ShellProfileSpec> profiles) {
        return resolve(kind, null, hint, policy, profiles);
    }

    /**
     * Selects a profile for a window kind with an optional explicit profile object.
     *
     * @param kind the window kind
     * @param explicitProfile optional authoritative profile object
     * @param hint optional content/group profile hint
     * @param policy optional product default policy
     * @param profiles the declared profile snapshot
     * @return the selected profile and effective decoration
     */
    public static ShellProfileSelection resolve(WindowKind kind, ShellChromeProfile explicitProfile,
            ShellProfileHint hint, ChromeProfilePolicy policy, List<ShellProfileSpec> profiles) {
        return resolve(kind, explicitProfile, null, hint, policy, profiles);
    }
}
