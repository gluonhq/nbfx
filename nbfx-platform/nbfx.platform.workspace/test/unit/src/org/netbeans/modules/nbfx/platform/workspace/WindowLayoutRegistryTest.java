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
import javafx.geometry.Orientation;
import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The first-run window tree is read from the declared layer entry.
 */
public class WindowLayoutRegistryTest {

    @Test
    public void readsTheDeclaredTree() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        FileObject folder = FileUtil.createFolder(fs.getRoot(), "NbFx/WindowLayouts");
        FileObject file = folder.createData("default");
        file.setAttribute("tree", "H[0.3000](P:NAVIGATOR,P:MAIN)");

        DockTree tree = WindowLayoutRegistry.read(file).orElseThrow();

        assertTrue(tree instanceof DockTree.Split);
        DockTree.Split split = (DockTree.Split) tree;
        assertEquals(Orientation.HORIZONTAL, split.orientation());
        assertEquals("NAVIGATOR", ((DockTree.Leaf) split.children().get(0)).primary());
        assertEquals(2, split.children().size());
    }
}
