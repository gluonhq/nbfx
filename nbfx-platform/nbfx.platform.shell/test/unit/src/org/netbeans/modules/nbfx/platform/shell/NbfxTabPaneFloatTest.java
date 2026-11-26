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

import javafx.scene.control.Tab;
import javafx.stage.Stage;
import org.junit.Test;
import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.*;

public class NbfxTabPaneFloatTest {

    @Test
    public void floatsTabWithSourceKindAndPreservesContentOnRedockClose() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane source = new NbfxTabPane(PaneRole.MAIN, PaneKind.EDITOR);
            NbfxTabPane floating = null;
            try {
                Tab tab = new Tab("Editor");
                NbfxTabPane.attachTab(source, tab);

                floating = NbfxTabPane.floatTab(tab, 10, 20, 400, 300);
                Stage stage = NbfxTabPane.stageOf(floating);
                assertNotNull(stage);
                assertTrue(stage.isShowing());
                assertTrue(source.getTabs().isEmpty());
                assertEquals(java.util.List.of(tab), floating.getTabs());
                assertEquals(PaneRole.DETACHED, floating.role());
                assertEquals(PaneKind.EDITOR, NbfxTabPane.kindOf(floating));

                try {
                    NbfxTabPane.showDetachedPane(floating, 0, 0, 400, 300);
                    fail("a pane already in a window was shown again");
                } catch (IllegalArgumentException expected) {
                    // as documented
                }

                stage.hide();
                assertFalse(stage.isShowing());
                assertTrue(NbfxTabPane.tabPanes().contains(floating));
                assertEquals(java.util.List.of(tab), floating.getTabs());
                stage.show();
                assertTrue(stage.isShowing());

                NbfxTabPane.closeFloatingPane(floating, true);
                assertFalse(stage.isShowing());
                assertTrue(NbfxTabPane.tabPanes().contains(floating));
                assertEquals(java.util.List.of(tab), floating.getTabs());
            } finally {
                if (floating != null) {
                    NbfxTabPane.closeFloatingPane(floating, false);
                    NbfxTabPane.unregister(floating);
                }
                NbfxTabPane.unregister(source);
            }
            return null;
        });
    }

    @Test
    public void normalFloatingWindowCloseUnregistersPane() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane floating = new NbfxTabPane(PaneRole.DETACHED, PaneKind.VIEW);
            try {
                NbfxTabPane.attachTab(floating, new Tab("View"));
                NbfxTabPane.showDetachedPane(floating, 0, 0, 300, 200);
                Stage stage = NbfxTabPane.stageOf(floating);
                assertTrue(stage.isShowing());

                NbfxTabPane.closeFloatingPane(floating, false);
                assertFalse(stage.isShowing());
                assertFalse(NbfxTabPane.tabPanes().contains(floating));
            } finally {
                NbfxTabPane.closeFloatingPane(floating, false);
                NbfxTabPane.unregister(floating);
            }
            return null;
        });
    }
}
