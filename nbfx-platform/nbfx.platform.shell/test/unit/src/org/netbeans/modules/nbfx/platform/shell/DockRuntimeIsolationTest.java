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
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * An application-scoped {@link DockRuntime} owns its own area, slides and panes. Building a runtime
 * must not disturb another runtime or silently replace the active static facade, so tests and a
 * second shell can work in isolation.
 */
public class DockRuntimeIsolationTest {

    @Test
    public void twoRuntimesKeepSeparateAreasAndPanes() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            DockRuntime first = runtime();
            DockRuntime second = runtime();

            assertNotSame("separate dock areas", first.area(), second.area());
            assertNotSame("separate sliding areas", first.slides(), second.slides());
            assertNotSame("separate main panes", first.primary(PaneRole.MAIN),
                    second.primary(PaneRole.MAIN));
            assertSame(first.primary(PaneRole.NAVIGATOR), first.area().primary(
                    PaneRole.NAVIGATOR.name()));
            assertSame(second.primary(PaneRole.MAIN), second.area().mainPane());

            // A zero-content layout still has a shape and a main pane.
            assertNotNull("first layout", first.area().tree());
            assertNotNull("second layout", second.area().tree());
            assertSame(first.primary(PaneRole.MAIN), first.area().mainPane());
            assertSame(second.primary(PaneRole.MAIN), second.area().mainPane());
            return null;
        });
    }

    @Test
    public void buildingARuntimeDoesNotReplaceTheActiveFacade() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            DockRuntime before = Docking.runtime();
            runtime();
            assertSame("an unregistered runtime leaves the facade alone", before,
                    Docking.runtime());
            return null;
        });
    }

    @Test
    public void disposeReleasesOnlyItsOwnState() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            DockRuntime kept = runtime();
            DockRuntime disposed = runtime();

            disposed.dispose();
            assertNull("disposed area", disposed.area());
            assertNull("disposed slides", disposed.slides());
            assertNotNull("kept area", kept.area());
            assertNotNull("kept slides", kept.slides());
            return null;
        });
    }

    @Test
    public void resetLayoutKeepsTheZeroContentShape() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            DockRuntime runtime = runtime();
            runtime.completeStartupLayout();
            runtime.resetLayout();
            assertNotNull("layout after reset", runtime.area().tree());
            assertSame(runtime.primary(PaneRole.MAIN), runtime.area().mainPane());
            return null;
        });
    }

    private static DockRuntime runtime() {
        NbfxTabPane navigator = new NbfxTabPane(PaneRole.NAVIGATOR);
        NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
        Map<String, NbfxTabPane> panes = new LinkedHashMap<>();
        panes.put(PaneRole.NAVIGATOR.name(), navigator);
        panes.put(PaneRole.MAIN.name(), main);
        return new DockRuntime(new WindowComposition(List.of(), null), panes);
    }
}