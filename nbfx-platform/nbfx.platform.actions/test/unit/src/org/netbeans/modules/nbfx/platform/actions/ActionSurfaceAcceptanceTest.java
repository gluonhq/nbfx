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
package org.netbeans.modules.nbfx.platform.actions;

import java.util.List;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.ActionContextMetadata;
import org.netbeans.api.nbfx.actions.ActionLayerReader;
import org.netbeans.api.nbfx.actions.ActionRef;
import org.netbeans.api.nbfx.actions.Command;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** Proves one layer command can be placed and resolved on four shell surfaces. */
public class ActionSurfaceAcceptanceTest {

    @Test
    public void oneCommandResolvesFromMenuToolbarRibbonAndBrand() throws Exception {
        FileSystem fileSystem = FileUtil.createMemoryFileSystem();
        FileObject actions = FileUtil.createFolder(fileSystem.getRoot(), "Actions");
        FileObject commandFile = actions.createData("layer.instance");
        commandFile.setAttribute("instanceClass", ActionRegistryImplTest.LayerCommand.class);
        ActionRegistryImpl registry = new ActionRegistryImpl(Lookup.EMPTY, actions);
        Command canonical = registry.find("layer").orElseThrow();
        Lookup context = Lookups.fixed(new ActionContextMetadata(
                "editor", "text/plain", "text", "main"));

        for (String path : List.of(
                "Menus/File",
                "Toolbars/main",
                "ShellRegions/nbfx:ribbon/Home/Clipboard",
                "ShellRegions/nbfx:brand")) {
            FileObject surface = FileUtil.createFolder(fileSystem.getRoot(), path);
            FileObject ref = surface.createData("layer.ref");
            ref.setAttribute("actionId", "layer");
            ref.setAttribute("contextType", "editor");
            ref.setAttribute("profiles", "main");
            List<ActionRef> refs = ActionLayerReader.readForContext(surface, context);
            assertEquals(1, refs.size());
            assertSame(canonical, registry.resolve(refs.get(0).actionId(), context).orElseThrow());
        }
        assertEquals(1, registry.getCommands().size());
    }
}
