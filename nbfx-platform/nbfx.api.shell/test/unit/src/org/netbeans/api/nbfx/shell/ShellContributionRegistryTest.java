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
import javafx.scene.Node;
import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Tests metadata-only discovery and fresh per-shell contribution instances. */
public class ShellContributionRegistryTest {

    @Test
    public void discoversWithoutConstructionAndLoadsFreshFilteredInstances() throws Exception {
        TestContribution.constructed = 0;
        FileSystem fileSystem = FileUtil.createMemoryFileSystem();
        FileObject root = fileSystem.getRoot().createFolder("ShellRegions");
        FileObject brand = root.createFolder("brand");
        contribution(brand, "late", TestContribution.class, 20, "main");
        contribution(brand, "early", TestContribution.class, 10, "main");
        FileObject dock = root.createFolder("dock");
        contribution(dock, "runtime", TestContribution.class, 1, "main");

        List<ShellContributionSpec> specs = ShellContributionRegistry.read(root);
        assertEquals(List.of("early", "late"), specs.stream().map(ShellContributionSpec::id).toList());
        assertEquals(0, TestContribution.constructed);
        assertTrue(ShellContributionRegistry.load(root, ShellRegionId.BRAND, "other").isEmpty());
        assertEquals(0, TestContribution.constructed);

        List<ShellContributionEntry> first = ShellContributionRegistry.load(
                root, ShellRegionId.BRAND, "main");
        List<ShellContributionEntry> second = ShellContributionRegistry.load(
                root, ShellRegionId.BRAND, "main");
        assertEquals(2, first.size());
        assertEquals(2, second.size());
        assertEquals(4, TestContribution.constructed);
        assertNotSame(first.get(0).contribution(), second.get(0).contribution());
        assertNotSame(first.get(0).contribution(), first.get(1).contribution());
        assertSame(TestContribution.class, first.get(0).spec().type());
        assertEquals(Set.of("main"), first.get(0).spec().profiles());
        assertThrows(UnsupportedOperationException.class,
                () -> first.get(0).spec().profiles().add("other"));

        int disposed = 0;
        for (ShellContributionEntry entry : first) {
            entry.contribution().dispose();
            disposed++;
        }
        assertEquals(2, disposed);
        assertTrue(ShellContributionRegistry.load(root, ShellRegionId.DOCK, "main").isEmpty());
    }

    @Test
    public void skipsMalformedNestedAndWrongTypeEntries() throws Exception {
        FileSystem fileSystem = FileUtil.createMemoryFileSystem();
        FileObject root = fileSystem.getRoot().createFolder("ShellRegions");
        FileObject brand = root.createFolder("nbfx:brand");
        contribution(brand, "wrong-position", TestContribution.class, "10", null);
        contribution(brand, "wrong-profiles", TestContribution.class, 10, 123);
        contribution(brand, "wrong-type", String.class, 10, null);
        brand.createFolder("nested").createData("hidden.instance")
                .setAttribute("instanceClass", TestContribution.class);
        brand.createData("action.ref").setAttribute("actionId", "save");

        assertTrue(ShellContributionRegistry.read(root).isEmpty());
        assertTrue(ShellContributionRegistry.load(root, ShellRegionId.BRAND, null).isEmpty());
    }

    private static void contribution(FileObject region, String id,
            Class<?> type, Object position, Object profiles) throws Exception {
        FileObject file = region.createData(id + ".instance");
        file.setAttribute("instanceClass", type);
        if (position != null) {
            file.setAttribute("position", position);
        }
        if (profiles != null) {
            file.setAttribute("profiles", profiles);
        }
    }

    /** Fresh test contribution; discovery must never invoke its constructor. */
    public static final class TestContribution implements ShellContribution {

        private static int constructed;

        public TestContribution() {
            constructed++;
        }

        @Override
        public Node create(ShellContext context) {
            return null;
        }
    }
}
