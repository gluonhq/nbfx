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

import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.netbeans.api.nbfx.layout.AreaStyle;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Tests shell-region metadata discovery, ordering and profile filtering. */
public class ShellRegionRegistryTest {

    @Test
    public void readsDirectFoldersOnlyAndFiltersProfiles() throws Exception {
        FileSystem fileSystem = FileUtil.createMemoryFileSystem();
        FileObject root = fileSystem.getRoot().createFolder("ShellRegions");
        FileObject brand = root.createFolder("nbfx:brand");
        brand.setAttribute("kind", "nbfx:brand");
        brand.setAttribute("position", 10);
        brand.setAttribute("profiles", "main, compact");
        brand.setAttribute("styleClass", "acme-brand");
        brand.setAttribute("visibleByDefault", false);
        brand.createFolder("File");
        FileObject utility = root.createFolder("product:utility");
        utility.setAttribute("kind", "product:tool");
        utility.setAttribute("position", 5);
        root.createFolder("product:bad");
        FileObject wrongPosition = root.createFolder("product:wrong-position");
        wrongPosition.setAttribute("kind", "nbfx:brand");
        wrongPosition.setAttribute("position", "10");
        FileObject wrongProfiles = root.createFolder("product:wrong-profiles");
        wrongProfiles.setAttribute("kind", "nbfx:brand");
        wrongProfiles.setAttribute("profiles", 123);
        FileObject wrongVisible = root.createFolder("product:wrong-visible");
        wrongVisible.setAttribute("kind", "nbfx:brand");
        wrongVisible.setAttribute("visibleByDefault", "false");

        List<ShellRegionSpec> regions = ShellRegionRegistry.read(root);
        assertEquals(2, regions.size());
        assertEquals(ShellRegionId.of("product:utility"), regions.get(0).id());
        assertEquals(ShellRegionId.BRAND, regions.get(1).id());
        assertEquals(Set.of("main", "compact"), regions.get(1).profiles());
        assertEquals(new AreaStyle(Set.of("acme-brand"), List.of(), List.of()), regions.get(1).style());
        assertFalse(regions.get(1).visibleByDefault());
        assertEquals(2, ShellRegionRegistry.forProfile(root, "main").size());
        assertEquals(2, ShellRegionRegistry.forProfile(root, "compact").size());
        assertEquals(1, ShellRegionRegistry.forProfile(root, "other").size());
        assertEquals(regions, ShellRegionRegistry.forProfile(root, null));
        assertThrows(UnsupportedOperationException.class, () -> regions.add(regions.get(0)));
    }

    @Test
    public void rejectsDuplicateCanonicalIdsDeterministically() throws Exception {
        FileSystem fileSystem = FileUtil.createMemoryFileSystem();
        FileObject root = fileSystem.getRoot().createFolder("ShellRegions");
        FileObject shortId = root.createFolder("brand");
        shortId.setAttribute("kind", "BRAND");
        FileObject canonicalId = root.createFolder("nbfx:brand");
        canonicalId.setAttribute("kind", "BRAND");

        List<ShellRegionSpec> regions = ShellRegionRegistry.read(root);
        assertEquals(1, regions.size());
        assertEquals(ShellRegionId.BRAND, regions.get(0).id());
        assertTrue(ShellRegionRegistry.read(null).isEmpty());
    }
}
