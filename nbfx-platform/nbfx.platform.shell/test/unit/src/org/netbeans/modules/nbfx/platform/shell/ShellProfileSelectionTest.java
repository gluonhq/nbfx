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
import java.util.Map;
import java.util.Set;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.Test;
import org.netbeans.api.nbfx.product.AboutInfo;
import org.netbeans.api.nbfx.product.Product;
import org.netbeans.api.nbfx.product.ProductIdentity;
import org.netbeans.api.nbfx.product.ProductIcons;
import org.netbeans.api.nbfx.product.ProductProvider;
import org.netbeans.api.nbfx.product.ProductStyle;
import org.netbeans.api.nbfx.shell.ChromeProfilePolicy;
import org.netbeans.api.nbfx.shell.ChromeProfilePolicyProvider;
import org.netbeans.api.nbfx.shell.ShellChromeProfile;
import org.netbeans.api.nbfx.shell.ShellProfileHint;
import org.netbeans.api.nbfx.shell.ShellProfileSelection;
import org.netbeans.api.nbfx.shell.ShellProfileSpec;
import org.netbeans.api.nbfx.shell.ShellRegionId;
import org.netbeans.api.nbfx.shell.WindowDecoration;
import org.netbeans.api.nbfx.shell.WindowKind;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Tests runtime main-profile selection without starting JavaFX or a window system. */
public class ShellProfileSelectionTest {

    @Test
    public void neutralHintPolicyAndExplicitPrecedence() {
        ShellProfileSelection neutral = ShellApplication.selectMainProfile(Lookup.EMPTY);
        assertEquals("main", neutral.profile().id());
        assertEquals(WindowDecoration.NATIVE, neutral.decoration());

        ChromeProfilePolicy policy = new ChromeProfilePolicy("product:chrome",
                Map.of(WindowKind.MAIN, "presentation"), "main");
        ChromeProfilePolicyProvider policyProvider = () -> policy;
        ProductProvider productProvider = productProvider();
        ShellProfileSelection product = ShellApplication.selectMainProfile(
                Lookups.fixed(policyProvider, productProvider));
        assertEquals("presentation", product.profile().id());
        assertEquals(WindowDecoration.UNDECORATED, product.decoration());

        ShellProfileSelection hinted = ShellApplication.selectMainProfile(Lookups.fixed(
                new ShellProfileHint("floating-editor"), policyProvider, productProvider));
        assertEquals("floating-editor", hinted.profile().id());

        ShellProfileSpec explicit = new ShellProfileSpec("product:explicit", WindowKind.MAIN,
                Set.of(ShellRegionId.DOCK), null, false);
        ShellProfileSelection explicitSelection = ShellApplication.selectMainProfile(Lookups.fixed(
                explicit, new ShellProfileHint("floating-editor"), policyProvider, productProvider));
        assertSame(explicit, explicitSelection.profile());
    }

    @Test
    public void missingProductPolicyAndDuplicateProvidersFail() {
        IllegalStateException missingPolicy = assertThrows(IllegalStateException.class,
                () -> ShellApplication.selectMainProfile(Lookups.singleton(productProvider())));
        assertTrue(missingPolicy.getMessage().contains("product:chrome"));
        ChromeProfilePolicy policy = new ChromeProfilePolicy("product:chrome",
                Map.of(WindowKind.MAIN, "presentation"), "main");
        ChromeProfilePolicyProvider first = () -> policy;
        ChromeProfilePolicyProvider second = () -> policy;
        assertThrows(IllegalStateException.class, () -> ShellApplication.selectMainProfile(
                Lookups.fixed(first, second, productProvider())));
        assertThrows(IllegalStateException.class, () -> ShellApplication.selectMainProfile(
                Lookups.fixed(productProvider(), productProvider())));
        ShellProfileSpec firstProfile = new ShellProfileSpec("first", WindowKind.MAIN, Set.of(), null, true);
        ShellProfileSpec secondProfile = new ShellProfileSpec("second", WindowKind.MAIN, Set.of(), null, true);
        assertThrows(IllegalStateException.class, () -> ShellApplication.selectMainProfile(
                Lookups.fixed(firstProfile, secondProfile)));
        assertThrows(IllegalStateException.class, () -> ShellApplication.selectMainProfile(
                Lookups.fixed(new ShellProfileHint("main"), new ShellProfileHint("presentation"))));
    }

    @Test
    public void stageDecorationAppliesNativeAndUndecoratedBeforeShowing()
            throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            Stage undecorated = new Stage();
            ShellApplication.applyWindowDecoration(undecorated, WindowDecoration.UNDECORATED);
            assertEquals(StageStyle.UNDECORATED, undecorated.getStyle());
            ShellApplication.applyWindowDecoration(undecorated, WindowDecoration.UNDECORATED);

            Stage initiallyUndecorated = new Stage(StageStyle.UNDECORATED);
            ShellApplication.applyWindowDecoration(initiallyUndecorated, WindowDecoration.NATIVE);
            assertEquals(StageStyle.DECORATED, initiallyUndecorated.getStyle());
            return null;
        });
    }

    private static ProductProvider productProvider() {
        Product product = new Product(
                new ProductIdentity("example", "Example", "1.0", null, null, null, null),
                new ProductIcons(List.of(), null, List.of()),
                new ProductStyle(List.of(), List.of(), List.of()),
                "theme.default", "example.default", "shell.default", "product:chrome",
                List.of(), new AboutInfo("", List.of(), List.of(), null));
        return () -> product;
    }
}
