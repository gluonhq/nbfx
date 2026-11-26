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
package org.netbeans.modules.nbfx.ide.workbench;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;
import org.junit.Test;
import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.api.nbfx.shell.WindowPaneHost;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.modules.nbfx.platform.shell.AppState;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane;
import org.netbeans.modules.nbfx.platform.shell.SessionRestorer;
import org.netbeans.spi.nbfx.shell.WindowPane;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

import static org.junit.Assert.*;

public class NavigatorPaneProviderTest {

    private static final AtomicBoolean FX_STARTED = new AtomicBoolean();

    @Test
    public void firstRunAttachesNavigatorProviderTab() throws Exception {
        ensureFxStarted();
        ScenarioSupport.onFx(() -> {
            Preferences prefs = Preferences.userRoot().node("nbfx-navigator-test-" + System.nanoTime());
            AppState appState = new AppState(prefs);
            SessionRestorer restorer = new SessionRestorer(appState, id -> null, () -> null);
            FakeNavigator projects = new FakeNavigator("projects", "Projects");
            NavigatorPaneProvider provider = new NavigatorPaneProvider(restorer, List.of(projects), pane -> { });
            WindowPane declaration = new WindowPane(PaneRole.NAVIGATOR.name(), "Navigator",
                    PaneKind.VIEW, "", false, 0);
            NbfxTabPane navigator = null;
            try {
                WindowPaneHost host = provider.createPane(declaration, null);
                assertTrue(host instanceof NbfxTabPane);
                navigator = (NbfxTabPane) host;
                assertEquals(1, navigator.getTabs().size());
                Tab tab = navigator.getTabs().getFirst();
                assertEquals("projects", NbfxTabPane.viewId(tab));
                assertSame(projects.getView(), tab.getContent());
            } finally {
                if (navigator != null) {
                    navigator.getTabs().clear();
                }
                prefs.removeNode();
            }
            return null;
        });
    }

    private static void ensureFxStarted() throws InterruptedException {
        if (FX_STARTED.compareAndSet(false, true)) {
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(ready::countDown);
            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new AssertionError("JavaFX startup timed out");
            }
        }
    }

    private static final class FakeNavigator implements NavigatorView {

        private final String id;
        private final String title;
        private final StackPane view = new StackPane();

        FakeNavigator(String id, String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public StackPane getView() {
            return view;
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }

        @Override
        public void revealFile(FileObject file) {
        }

        @Override
        public void restoreExpandedPaths(FileObject root, List<String> paths) {
        }

        @Override
        public void restoreSelectedPath(FileObject root, String path) {
        }

        @Override
        public List<String> getExpandedPaths(FileObject root) {
            return List.of();
        }

        @Override
        public String getSelectedPath(FileObject root) {
            return null;
        }

        @Override
        public void setOnProjectLoaded(Consumer<FileObject> callback) {
        }

        @Override
        public void setOnProjectLoadFailed(Consumer<FileObject> callback) {
        }
    }
}
