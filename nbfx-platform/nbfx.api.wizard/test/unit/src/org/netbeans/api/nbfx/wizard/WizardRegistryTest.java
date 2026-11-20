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
package org.netbeans.api.nbfx.wizard;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.netbeans.api.nbfx.layer.LayerEntry;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;

/**
 * Verifies that wizards are read from layer entries with their metadata and ordered by category,
 * position and id, falling back to the wizard's own defaults.
 *
 * @since 1.1
 */
public class WizardRegistryTest {

    @Test
    public void readsAttributesAndOrders() throws IOException {
        FileObject folder = wizardFolder();
        FileObject bravo = folder.createData("bravo", "instance");
        bravo.setAttribute("displayName", "Bravo");
        bravo.setAttribute("category", "Project");
        bravo.setAttribute("position", 20);
        FileObject alpha = folder.createData("alpha", "instance");
        alpha.setAttribute("displayName", "Alpha");
        alpha.setAttribute("category", "Project");
        alpha.setAttribute("position", 10);

        List<WizardEntry> registrations = WizardRegistry.read(List.of(
                new LayerEntry<>(wizard("bravo"), bravo),
                new LayerEntry<>(wizard("alpha"), alpha)));

        assertEquals(List.of("alpha", "bravo"),
                registrations.stream().map(WizardEntry::id).toList());
        assertEquals("Alpha", registrations.get(0).displayName());
    }

    @Test
    public void fallsBackToWizardDefaults() throws IOException {
        FileObject folder = wizardFolder();
        FileObject file = folder.createData("x", "instance");

        List<WizardEntry> registrations =
                WizardRegistry.read(List.of(new LayerEntry<>(wizard("x"), file)));

        assertEquals("x", registrations.get(0).id());
        assertEquals("x", registrations.get(0).displayName());
        assertEquals("", registrations.get(0).category());
    }

    private static FileObject wizardFolder() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        return FileUtil.createFolder(fs.getRoot(), "NbFx/Wizards");
    }

    private static Wizard wizard(String id) {
        return new Wizard() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getDisplayName() {
                return id;
            }

            @Override
            public List<WizardPanel> getPages() {
                return List.of();
            }

            @Override
            public void finish() {
            }
        };
    }
}
