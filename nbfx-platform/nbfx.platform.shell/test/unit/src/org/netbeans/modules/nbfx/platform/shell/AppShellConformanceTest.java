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
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.stage.Stage;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.modules.nbfx.platform.actions.ActionRegistryImpl;
import org.netbeans.modules.nbfx.platform.launcher.AppProvider;
import org.netbeans.modules.nbfx.platform.nodes.PropertySheet;
import org.netbeans.modules.nbfx.platform.nodes.SimpleFxProperty;
import org.netbeans.modules.nbfx.platform.nodes.SimpleFxPropertySet;
import org.netbeans.modules.nbfx.platform.output.ConsoleOutput;
import org.netbeans.modules.nbfx.platform.wizard.WizardRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Platform-only AppShell conformance: the shell and the landed platform services must work without
 * any nbfx IDE or Java module on the classpath. Unlike the per-module tests, this fixture also proves
 * the classpath boundary itself.
 */
public class AppShellConformanceTest {

    @Test
    public void noIdeModulesOnThePlatformClasspath() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("org.netbeans.modules.nbfx.ide.workbench.JavaFXLaunchApp"));
        assertNotNull(ShellApplication.class);
        assertSame(ShellApplication.class, new ShellAppProvider().applicationClass());
    }

    @Test
    public void defaultShellApplicationStarts() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            ShellApplication application = new ShellApplication();
            application.start(new Stage());
            assertNotNull(application.getStage());
            assertNotNull(application.getMainPane());
            assertNotNull(application.getDockArea());
            assertNotNull(application.getSlidingArea());
            assertNotNull(application.getChrome());
            assertNotNull(application.getChrome().bottom());
            return null;
        });
    }

    @Test
    public void actionRegistryWorksWithoutIdeModules() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            ActionRegistry registry = new ActionRegistryImpl();
            AtomicBoolean ran = new AtomicBoolean();
            registry.register(new RunnableCommand("conformance", "Conformance", null, () -> ran.set(true)));
            assertFalse(ran.get());
            assertTrue(registry.find("conformance").isPresent());
            registry.find("conformance").orElseThrow().run();
            assertTrue(ran.get());
            return null;
        });
    }

    @Test
    public void propertySheetOutputProgressAndWizardsWork() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            PropertySheet sheet = new PropertySheet();
            assertNotNull(sheet);
            SimpleFxPropertySet set = new SimpleFxPropertySet("conformance", "Conformance", List.of(
                    new SimpleFxProperty("name", "Name", String.class, "value")));
            assertEquals("Conformance", set.getDisplayName());
            assertFalse(set.getProperties().isEmpty());

            assertNotNull(new ConsoleOutput().console("conformance"));
            assertNotNull(WizardRegistry.discover());
            return null;
        });
    }
}
