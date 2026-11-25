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
package org.netbeans.api.nbfx.shell;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tests declarative profile discovery and malformed-entry isolation. */
public class ShellProfileRegistryTest {

    @Test
    public void readsProfilesAndSkipsMalformedFolders() throws IOException {
        FileSystem fileSystem = FileUtil.createMemoryFileSystem();
        FileObject root = FileUtil.createFolder(fileSystem.getRoot(), "ShellProfiles");
        FileObject main = root.createFolder("main");
        main.setAttribute("windowKind", "MAIN");
        main.setAttribute("regions", "BRAND, DOCK");
        main.setAttribute("decoration", "NATIVE");
        FileObject floating = root.createFolder("floating-editor");
        floating.setAttribute("windowKind", "FLOATING");
        floating.setAttribute("systemMenuBar", false);
        FileObject wrongType = root.createFolder("wrong-type");
        wrongType.setAttribute("systemMenuBar", "false");
        FileObject wrongKind = root.createFolder("wrong-kind");
        wrongKind.setAttribute("windowKind", "UNKNOWN");
        FileObject wrongRegion = root.createFolder("wrong-region");
        wrongRegion.setAttribute("regions", "BRAND,not-namespaced");

        List<ShellProfileSpec> profiles = ShellProfileRegistry.read(root);
        assertEquals(List.of("floating-editor", "main"),
                profiles.stream().map(ShellProfileSpec::id).toList());
        assertEquals(Set.of(ShellRegionId.BRAND, ShellRegionId.DOCK), profiles.get(1).regions());
        assertFalse(profiles.get(0).systemMenuBar());
        assertTrue(ShellProfileRegistry.read(null).isEmpty());
    }
}
