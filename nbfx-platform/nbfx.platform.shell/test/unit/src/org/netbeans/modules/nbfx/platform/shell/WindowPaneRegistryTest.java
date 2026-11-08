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

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.modules.nbfx.platform.shell.spi.WindowPane;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Window panes are discovered from layer declarations, with MAIN as the only editor pane.
 */
public class WindowPaneRegistryTest {

    @Test
    public void readsProductPanesAndAreas() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        FileObject root = FileUtil.createFolder(fs.getRoot(), "NbFx/WindowPanes");
        FileObject pane = root.createData("RIGHT_SIDEBAR");
        pane.setAttribute("kind", "VIEW");
        pane.setAttribute("area", "right-sidebar");
        pane.setAttribute("keepWhenEmpty", true);
        pane.setAttribute("position", 30);

        List<WindowPane> panes = WindowPaneRegistry.read(root);

        assertEquals(1, panes.size());
        WindowPane sidebar = panes.get(0);
        assertEquals("right-sidebar", sidebar.area());
        assertTrue(sidebar.keepWhenEmpty());
        assertTrue(panes.stream().anyMatch(p -> p.area().equals("right-sidebar")));
    }

    @Test
    public void discoversThePlatformPanes() {
        List<WindowPane> panes = WindowPaneRegistry.discover();

        WindowPane main = panes.stream().filter(p -> p.id().equals("MAIN")).findFirst().orElseThrow();
        assertEquals(PaneKind.EDITOR, main.kind());
        assertTrue(main.keepWhenEmpty());
        assertTrue(panes.stream().anyMatch(p -> p.id().equals("NAVIGATOR")));
    }

    @Test
    public void reservedNonPermanentRolesAreRejected() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        FileObject root = FileUtil.createFolder(fs.getRoot(), "NbFx/WindowPanes");
        for (String id : List.of("DOCKED", "DETACHED", "SLIDE")) {
            root.createData(id).setAttribute("kind", "VIEW");
        }

        assertTrue(WindowPaneRegistry.read(root).isEmpty());
    }

    @Test
    public void onlyMainMayBeAnEditorPane() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        FileObject root = FileUtil.createFolder(fs.getRoot(), "NbFx/WindowPanes");
        FileObject pane = root.createData("SECOND_EDITOR");
        pane.setAttribute("kind", "EDITOR");

        assertTrue(WindowPaneRegistry.read(root).isEmpty());
    }
}
