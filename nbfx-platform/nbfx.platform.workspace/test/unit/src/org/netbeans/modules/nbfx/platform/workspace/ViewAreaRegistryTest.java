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
package org.netbeans.modules.nbfx.platform.workspace;

import java.io.IOException;
import java.util.List;
import org.netbeans.api.nbfx.view.DockLocation;
import org.netbeans.api.nbfx.view.ViewArea;
import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * View areas are merged from the platform defaults and the layer declarations.
 */
public class ViewAreaRegistryTest {

    @Test
    public void invalidWeightIsSkipped() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        FileObject root = FileUtil.createFolder(fs.getRoot(), "NbFx/ViewAreas");
        FileObject broken = FileUtil.createFolder(root, "broken");
        broken.setAttribute("location", "RIGHT");
        broken.setAttribute("weight", 1.5d);

        List<ViewArea> areas = ViewAreaRegistry.read(root);

        assertTrue(areas.stream().noneMatch(area -> area.id().equals("broken")));
    }

    @Test
    public void readsCustomAndOverriddenAreas() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        FileObject root = FileUtil.createFolder(fs.getRoot(), "NbFx/ViewAreas");
        FileObject custom = FileUtil.createFolder(root, "right-sidebar");
        custom.setAttribute("displayName", "Right Sidebar");
        custom.setAttribute("location", "RIGHT");
        custom.setAttribute("weight", 0.25d);
        custom.setAttribute("position", 10);
        FileObject explorer = FileUtil.createFolder(root, "explorer");
        explorer.setAttribute("displayName", "Projects");
        explorer.setAttribute("location", "LEFT");
        explorer.setAttribute("weight", 0.4d);

        List<ViewArea> areas = ViewAreaRegistry.read(root);

        ViewArea sidebar = areas.stream()
                .filter(area -> area.id().equals("right-sidebar")).findFirst().orElseThrow();
        assertEquals(DockLocation.RIGHT, sidebar.location());
        assertEquals(0.25d, sidebar.weight(), 0.001d);
        ViewArea projects = areas.stream()
                .filter(area -> area.id().equals("explorer")).findFirst().orElseThrow();
        assertEquals("Projects", projects.displayName());
        assertEquals(0.4d, projects.weight(), 0.001d);
        assertTrue(areas.stream().anyMatch(area -> area.id().equals("output")));
    }
}
