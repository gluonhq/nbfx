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

import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import javafx.scene.control.Tab;
import org.junit.Test;
import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.api.nbfx.shell.WindowManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The public {@link WindowManager} exposes the pane/tab operations a product uses, without the
 * concrete tab pane type.
 */
public class WindowManagerTest {

    @Test
    public void exposesPaneAndTabOperations() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
            NbfxTabPane other = new NbfxTabPane(PaneRole.NAVIGATOR);
            try {
                WindowManager manager = new ShellApplication().windowManager();
                Tab view = new Tab("view");
                NbfxTabPane.setViewId(view, "demo.view");

                manager.attach(main, view);
                assertTrue(manager.panes().contains(main));
                assertSame(main, manager.paneOf(view).orElseThrow());
                assertEquals("demo.view", manager.viewId(view));
                assertSame(view, manager.findViewTab("demo.view").orElseThrow());

                manager.move(view, other, 0);
                assertSame(other, manager.paneOf(view).orElseThrow());
                assertEquals(0, other.getTabs().indexOf(view));
                assertSame(view, manager.findViewTab("demo.view").orElseThrow());

                manager.select(view);
                assertSame(view, other.getSelectionModel().getSelectedItem());
            } finally {
                NbfxTabPane.unregister(main);
                NbfxTabPane.unregister(other);
            }
            return null;
        });
    }
}
