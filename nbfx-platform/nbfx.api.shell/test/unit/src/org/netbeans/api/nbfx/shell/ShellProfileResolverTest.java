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

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/** Tests deterministic chrome-profile precedence and decoration resolution. */
public class ShellProfileResolverTest {

    private static final ShellProfileSpec MAIN = profile("main", WindowKind.MAIN,
            WindowDecoration.NATIVE);
    private static final ShellProfileSpec FLOATING = profile("floating-editor", WindowKind.FLOATING,
            WindowDecoration.NATIVE);
    private static final ShellProfileSpec PRESENTATION = profile("presentation", WindowKind.CUSTOM,
            WindowDecoration.UNDECORATED);
    private static final ShellProfileSpec PRODUCT = profile("product:main", WindowKind.MAIN,
            WindowDecoration.UNDECORATED);
    private static final List<ShellProfileSpec> PROFILES = List.of(MAIN, FLOATING, PRESENTATION, PRODUCT);

    @Test
    public void explicitProfileWinsAndOptionsDecorationOverridesIt() {
        ShellChromeProfile explicit = ShellChromeProfile.all();
        WindowOptions options = new WindowOptions(WindowKind.MAIN, explicit, null, null, null,
                false, 800.0d, 600.0d, true, true, WindowDecoration.UNDECORATED);
        ShellProfileSelection selection = ShellProfileResolver.resolve(options,
                new ShellProfileHint("presentation"), policy(Map.of(WindowKind.MAIN, "product:main"),
                        "main"), PROFILES);
        assertSame(explicit, selection.profile());
        assertEquals(WindowDecoration.UNDECORATED, selection.decoration());
    }

    @Test
    public void hintWinsOverProductPolicyAndUnknownHintFallsThrough() {
        ChromeProfilePolicy policy = policy(Map.of(WindowKind.MAIN, "product:main"), "main");
        assertEquals("presentation", ShellProfileResolver.resolve(WindowKind.MAIN,
                new ShellProfileHint("presentation"), policy, PROFILES).profile().id());
        assertEquals("product:main", ShellProfileResolver.resolve(WindowKind.MAIN,
                new ShellProfileHint("missing"), policy, PROFILES).profile().id());
    }

    @Test
    public void productDefaultPolicyFallbackAndNeutralFallbackAreDeterministic() {
        ChromeProfilePolicy policy = policy(Map.of(WindowKind.MAIN, "product:main"), "main");
        ShellProfileSelection selected = ShellProfileResolver.resolve(WindowKind.MAIN, null,
                policy, PROFILES);
        assertEquals("product:main", selected.profile().id());
        assertEquals(WindowDecoration.UNDECORATED, selected.decoration());

        ChromeProfilePolicy missingDefault = policy(Map.of(WindowKind.MAIN, "missing"), "presentation");
        assertEquals("presentation", ShellProfileResolver.resolve(WindowKind.MAIN, null,
                missingDefault, PROFILES).profile().id());

        ChromeProfilePolicy missingBoth = policy(Map.of(WindowKind.MAIN, "missing"), "missing");
        assertEquals("main", ShellProfileResolver.resolve(WindowKind.MAIN, null,
                missingBoth, PROFILES).profile().id());
        assertEquals("floating-editor", ShellProfileResolver.resolve(WindowKind.FLOATING, null,
                null, PROFILES).profile().id());
        assertEquals("presentation", ShellProfileResolver.resolve(WindowKind.CUSTOM, null,
                null, PROFILES).profile().id());
    }

    @Test
    public void missingNeutralProfileAndDuplicateIdsFail() {
        assertThrows(IllegalStateException.class, () -> ShellProfileResolver.resolve(
                WindowKind.MAIN, null, null, List.of(FLOATING, PRESENTATION)));
        assertThrows(IllegalArgumentException.class, () -> ShellProfileResolver.resolve(
                WindowKind.MAIN, null, null, List.of(MAIN, MAIN)));
    }

    private static ShellProfileSpec profile(String id, WindowKind kind, WindowDecoration decoration) {
        return new ShellProfileSpec(id, kind, Set.of(ShellRegionId.DOCK), decoration, true);
    }

    private static ChromeProfilePolicy policy(Map<WindowKind, String> defaults, String fallback) {
        return new ChromeProfilePolicy("product:policy", defaults, fallback);
    }
}
