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

import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.api.nbfx.views.ViewSpec;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.After;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.views.DockLocation;
import org.netbeans.api.nbfx.views.ViewArea;
import org.netbeans.api.nbfx.view.ViewManager;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.modules.nbfx.platform.actions.ActionRegistryImpl;
import org.netbeans.modules.nbfx.platform.launcher.AppProvider;
import org.netbeans.api.nbfx.nodes.PropertiesProvider;
import org.netbeans.api.nbfx.nodes.PropertySetModel;
import org.netbeans.api.nbfx.nodes.SimplePropertyModel;
import org.netbeans.api.nbfx.nodes.SimplePropertySetModel;
import org.netbeans.api.nbfx.output.OutputConsole;
import org.netbeans.api.nbfx.views.ViewAreaRegistry;
import org.netbeans.api.nbfx.wizard.WizardDisplayer;
import org.netbeans.api.nbfx.wizard.WizardRegistry;
import org.openide.util.Lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Platform-only AppShell conformance: the shell and the landed platform services must work without
 * any nbfx IDE or Java module on the classpath. Unlike the per-module tests, this fixture also proves
 * the classpath boundary itself.
 */
public class AppShellConformanceTest {

    /** The started shell leaves its window open; see {@link JavaFxTestSupport#hideWindows()}. */
    @After
    public void hideWindows() throws InterruptedException {
        JavaFxTestSupport.hideWindows();
    }

    @Test
    public void noIdeModulesOnThePlatformClasspath() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("org.netbeans.modules.nbfx.ide.workbench.JavaFXLaunchApp"));
        assertNotNull(ShellApplication.class);
        assertSame(ShellApplication.class, new ShellAppProvider().applicationClass());
    }

    @Test
    public void defaultShellApplicationStarts() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            ShellApplication application = new ShellApplication();
            application.start(new Stage());
            assertNotNull(application.getStage());
            assertNotNull(application.getMainPane());
            assertNotNull(application.getDockArea());
            assertNotNull(application.getSlidingArea());
            assertNotNull(application.getChrome());
            assertNotNull(application.getChrome().bottom());
            assertSame(application.getMainPane(), application.getWindow().mainPane());
            assertSame(application.getRoot(), application.getWindow().root());

            NbfxTabPane sidebar = (NbfxTabPane) Docking.primary(
                    PaneRole.of("RIGHT_SIDEBAR"));
            assertNotNull("the registered custom pane is instantiated", sidebar);
            assertEquals(PaneKind.VIEW, NbfxTabPane.kindOf(sidebar));
            assertSame("the editor pane remains the dock area's main pane",
                    application.getMainPane(), application.getDockArea().mainPane());
            assertTrue("a keep-when-empty pane stays visible on the first run",
                    application.getDockArea().contains(sidebar));
            assertFalse(application.getDockArea().isPrimaryHidden(sidebar));
            assertSame("the registered area owns the legacy RIGHT location", sidebar,
                    Docking.paneAt(DockLocation.RIGHT));

            ViewArea area = ViewAreaRegistry.find("right-sidebar").orElseThrow();
            ViewProvider view = new ViewProvider() {
                @Override
                public String getId() {
                    return "conformance.custom";
                }

                @Override
                public String getTitle() {
                    return "Custom";
                }

                @Override
                public Node getView() {
                    return new Region();
                }
            };
            ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
            assertNotNull(manager);
            manager.show(view, area, 0);
            assertEquals(1, sidebar.getTabs().size());
            assertSame(sidebar, sidebar.getTabs().get(0).getTabPane());

            manager.show(new RegisteredViewFixture());
            Tab registeredTab = NbfxTabPane.findViewTab("conformance.registered").orElseThrow();
            assertSame("ViewSpec places the view in its declared area", sidebar,
                    registeredTab.getTabPane());

            ViewProvider unregistered = new ViewProvider() {
                @Override
                public String getId() {
                    return "conformance.unregistered";
                }

                @Override
                public String getTitle() {
                    return "Unregistered";
                }

                @Override
                public Node getView() {
                    return new Region();
                }

                @Override
                public DockLocation getDefaultLocation() {
                    return DockLocation.BOTTOM;
                }
            };
            manager.show(unregistered);
            Tab unregisteredTab = NbfxTabPane.findViewTab("conformance.unregistered").orElseThrow();
            assertNotNull("an unregistered view uses its legacy location", unregisteredTab.getTabPane());

            ViewProvider unknownView = new ViewProvider() {
                @Override
                public String getId() {
                    return "conformance.unknown-area";
                }

                @Override
                public String getTitle() {
                    return "Unknown Area";
                }

                @Override
                public Node getView() {
                    return new Region();
                }
            };
            ViewArea unknownArea = new ViewArea("missing-area", "Missing", DockLocation.BOTTOM, 0.2);
            manager.show(unknownView, unknownArea, 0);
            Tab unknownTab = NbfxTabPane.findViewTab("conformance.unknown-area").orElseThrow();
            assertNotNull("an unknown area still finds a fallback pane", unknownTab.getTabPane());
            assertNotSame(sidebar, unknownTab.getTabPane());
            return null;
        });
    }

    @Test
    public void actionRegistryWorksWithoutIdeModules() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
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
        JavaFxTestSupport.onFx(() -> {
            assertTrue("the Properties view is registered", Lookup.getDefault()
                    .lookupAll(ViewProvider.class).stream()
                    .anyMatch(provider -> "properties".equals(provider.getId())));
            // The property contract a downstream product implements, using API imports only.
            PropertiesProvider properties = new ConformancePropertiesProvider();
            List<PropertySetModel> sets = properties.getPropertySets("context");
            assertEquals(1, sets.size());
            assertEquals("Conformance", sets.get(0).getDisplayName());
            assertFalse(sets.get(0).getProperties().isEmpty());

            OutputConsole output = Lookup.getDefault().lookup(OutputConsole.class);
            assertNotNull("the platform provides the output consoles", output);
            assertNotNull(output.console("conformance"));
            assertNotNull(WizardRegistry.discover());
            assertNotNull("the platform provides the wizard displayer", WizardDisplayer.getDefault());
            return null;
        });
    }

    /** The API-only property fixture a downstream product can write. */
    private static final class ConformancePropertiesProvider implements PropertiesProvider {

        @Override
        public List<PropertySetModel> getPropertySets(Object context) {
            if (!(context instanceof String)) {
                return List.of();
            }
            return List.of(new SimplePropertySetModel("conformance", "Conformance",
                    List.of(new SimplePropertyModel("name", "Name", String.class, "value"))));
        }
    }
}
