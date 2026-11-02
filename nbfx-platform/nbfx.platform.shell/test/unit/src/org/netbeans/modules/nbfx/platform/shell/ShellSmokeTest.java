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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane.PaneRole;
import org.netbeans.modules.nbfx.platform.workspace.DockArea;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * Smoke test of the platform-only shell: the dock area, the tab panes and a view tab are built
 * without any IDE module. Runs on the headless JavaFX toolkit.
 */
public class ShellSmokeTest {

    private static final AtomicBoolean FX_STARTED = new AtomicBoolean();

    @Test
    public void shellBuildsWithoutIdeModules() throws InterruptedException {
        onFx(() -> {
            NbfxTabPane navigator = new NbfxTabPane(PaneRole.NAVIGATOR);
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);

            DockArea<TabPane> area = Docking.create(navigator, main);
            assertNotNull("dock area", area);
            assertNotNull("sliding area", Docking.slides());
            assertSame(navigator, Docking.primary(PaneRole.NAVIGATOR));
            assertSame(main, Docking.primary(PaneRole.MAIN));

            ViewProvider view = new ViewProvider() {
                @Override
                public String getTitle() {
                    return "Smoke";
                }

                @Override
                public Node getView() {
                    return new Label("smoke");
                }
            };
            Tab tab = ViewTabs.create(view, "smoke tooltip");
            assertEquals("Smoke", NbfxTabPane.titleOf(tab));
            assertNotNull("view id", NbfxTabPane.viewId(tab));

            NbfxTabPane.attachTab(main, tab);
            assertEquals(1, main.getTabs().size());
            assertSame(tab, main.getTabs().get(0));
            return null;
        });
    }

    private static <T> T onFx(Supplier<T> task) throws InterruptedException {
        ensureFxStarted();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(task.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(30, TimeUnit.SECONDS)) {
            fail("timed out waiting for the JavaFX thread");
        }
        Throwable failure = error.get();
        if (failure instanceof AssertionError assertion) {
            throw assertion;
        }
        if (failure != null) {
            throw new AssertionError(failure);
        }
        return result.get();
    }

    private static void ensureFxStarted() throws InterruptedException {
        if (FX_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await();
        }
    }
}
