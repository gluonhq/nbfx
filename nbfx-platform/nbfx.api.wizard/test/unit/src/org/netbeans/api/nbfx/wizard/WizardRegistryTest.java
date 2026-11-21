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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.netbeans.api.nbfx.layer.LayerRegistration;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

/**
 * Verifies that wizards are read from layer registrations with their metadata and ordered by
 * category, position and id, and that only the requested wizard is created.
 *
 * @since 1.1
 */
public class WizardRegistryTest {

    @Test
    public void readsAttributesAndOrders() throws IOException {
        FileObject folder = wizardFolder();
        LayerRegistration<Wizard> bravo = registration(folder, "bravo", DemoWizard.class);
        bravo.file().setAttribute("displayName", "Bravo");
        bravo.file().setAttribute("category", "Project");
        bravo.file().setAttribute("position", 20);
        LayerRegistration<Wizard> alpha = registration(folder, "alpha", DemoWizard.class);
        alpha.file().setAttribute("displayName", "Alpha");
        alpha.file().setAttribute("category", "Project");
        alpha.file().setAttribute("position", 10);

        List<WizardEntry> entries = WizardRegistry.read(List.of(bravo, alpha));

        assertEquals(List.of("alpha", "bravo"),
                entries.stream().map(WizardEntry::id).toList());
        assertEquals("Alpha", entries.get(0).displayName());
    }

    @Test
    public void entryMetadataFallsBackToId() throws IOException {
        FileObject folder = wizardFolder();
        LayerRegistration<Wizard> entry = registration(folder, "x", DemoWizard.class);

        List<WizardEntry> entries = WizardRegistry.read(List.of(entry));

        assertEquals("x", entries.get(0).id());
        assertEquals("x", entries.get(0).displayName());
        assertEquals("", entries.get(0).iconName());
        assertEquals("", entries.get(0).category());
        assertEquals(Integer.MAX_VALUE, entries.get(0).position());
    }

    @Test
    public void findInstantiatesOnlyTheMatch() throws IOException {
        FileObject folder = wizardFolder();
        CountingWizard.CREATED.set(0);
        LayerRegistration<Wizard> alpha = registration(folder, "alpha", CountingWizard.class);
        LayerRegistration<Wizard> bravo = registration(folder, "bravo", CountingWizard.class);

        Wizard found = WizardRegistry.find(List.of(alpha, bravo), "bravo");

        assertNotNull(found);
        assertEquals(1, CountingWizard.CREATED.get());
    }

    @Test
    public void findCreatesAFreshInstanceEachTime() throws IOException {
        FileObject folder = wizardFolder();
        LayerRegistration<Wizard> entry = registration(folder, "alpha", DemoWizard.class);

        Wizard first = WizardRegistry.find(List.of(entry), "alpha");
        Wizard second = WizardRegistry.find(List.of(entry), "alpha");

        assertNotSame(first, second);
    }

    @Test
    public void findReturnsNullForUnknownId() throws IOException {
        FileObject folder = wizardFolder();
        LayerRegistration<Wizard> entry = registration(folder, "alpha", DemoWizard.class);

        assertNull(WizardRegistry.find(List.of(entry), "missing"));
    }

    private static FileObject wizardFolder() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        return FileUtil.createFolder(fs.getRoot(), "NbFx/Wizards");
    }

    private static LayerRegistration<Wizard> registration(FileObject folder, String id,
            Class<? extends Wizard> type) throws IOException {
        return new LayerRegistration<>(folder.createData(id, "instance"), type);
    }

    /** A wizard implementation with a public no-argument constructor. */
    public static final class DemoWizard implements Wizard {

        @Override
        public String getId() {
            return "demo";
        }

        @Override
        public String getDisplayName() {
            return "Demo";
        }

        @Override
        public List<WizardPanel> getPages() {
            return List.of();
        }

        @Override
        public void finish() {
        }
    }

    /** Counts how often the registry creates it. */
    public static final class CountingWizard implements Wizard {

        static final AtomicInteger CREATED = new AtomicInteger();

        public CountingWizard() {
            CREATED.incrementAndGet();
        }

        @Override
        public String getId() {
            return "counting";
        }

        @Override
        public String getDisplayName() {
            return "Counting";
        }

        @Override
        public List<WizardPanel> getPages() {
            return List.of();
        }

        @Override
        public void finish() {
        }
    }
}
