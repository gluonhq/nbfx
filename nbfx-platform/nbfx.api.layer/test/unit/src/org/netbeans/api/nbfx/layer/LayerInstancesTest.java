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
package org.netbeans.api.nbfx.layer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Verifies the node-free layer instance reader. */
public class LayerInstancesTest {

    private ClassLoader previous;

    @Before
    public void useTestClassLoader() {
        previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    }

    @After
    public void restoreClassLoader() {
        Thread.currentThread().setContextClassLoader(previous);
    }

    @Test
    public void loadsClassEntriesInPositionOrder() throws Exception {
        FileObject folder = folder();
        createEntry(folder, "second", DemoEditor.class, 20, true);
        createEntry(folder, "first", DemoEditor.class, 10, false);

        List<LayerEntry<DemoEditor>> entries = LayerInstances.load(folder, DemoEditor.class);

        assertEquals(2, entries.size());
        assertEquals("first", entries.get(0).id());
        assertEquals("second", entries.get(1).id());
        assertEquals(Boolean.FALSE, entries.get(0).attribute("inline"));
        assertEquals(Boolean.TRUE, entries.get(1).attribute("inline"));
        assertTrue(entries.get(0).instance() instanceof DemoEditor);
    }

    @Test
    public void skipsWrongTypesMissingClassesAndFactoryEntries() throws Exception {
        FileObject folder = folder();
        createEntry(folder, "string", String.class, 10, true);
        FileObject missing = folder.createData("missing", "instance");
        missing.setAttribute("position", 20);
        FileObject unknown = folder.createData("unknown", "instance");
        unknown.setAttribute("instanceClass", "no.such.Editor");
        unknown.setAttribute("position", 30);
        FileObject factory = folder.createData("factory", "instance");
        factory.setAttribute("instanceCreate", new String[]{"methodvalue", "no.such.Factory.create"});
        factory.setAttribute("position", 40);

        List<LayerEntry<DemoEditor>> entries = LayerInstances.load(folder, DemoEditor.class);

        assertTrue(entries.toString(), entries.isEmpty());
    }

    @Test
    public void keepsTheEntryThatDeclaresTheInstance() throws Exception {
        FileObject folder = folder();
        FileObject file = createEntry(folder, "demo", DemoEditor.class, 1, true);

        LayerEntry<DemoEditor> entry = LayerInstances.load(folder, DemoEditor.class).get(0);

        assertSame(file, entry.file());
        assertEquals("demo", entry.id());
        assertEquals(Boolean.TRUE, entry.attribute("inline"));
        assertNull(entry.attribute("missing"));
    }

    @Test
    public void registrationsDoNotCreateInstances() throws Exception {
        FileObject folder = folder();
        createEntry(folder, "demo", CountingEditor.class, 10, true);
        CountingEditor.CREATED.set(0);

        List<LayerRegistration<CountingEditor>> registrations =
                LayerInstances.registrations(folder, CountingEditor.class);

        assertEquals(1, registrations.size());
        assertEquals(0, CountingEditor.CREATED.get());
        assertNotNull(LayerInstances.instantiate(registrations.get(0)));
        assertEquals(1, CountingEditor.CREATED.get());
    }

    @Test
    public void warnsOnceForBrokenRegistration() throws Exception {
        FileObject folder = FileUtil.createMemoryFileSystem().getRoot()
                .createFolder("broken-" + System.nanoTime());
        FileObject missing = folder.createData("missing", "instance");
        missing.setAttribute("instanceClass", "no.such.Editor");

        Logger logger = Logger.getLogger(LayerInstances.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            LayerInstances.registrations(folder, DemoEditor.class);
            LayerInstances.registrations(folder, DemoEditor.class);
        } finally {
            logger.removeHandler(handler);
        }

        assertEquals(1, records.stream()
                .filter(record -> record.getLevel().intValue() == Level.WARNING.intValue())
                .count());
    }

    private static FileObject folder() throws Exception {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        return fs.getRoot().createFolder("editors");
    }

    private static FileObject createEntry(FileObject folder, String id, Class<?> type,
            int position, boolean inline) throws Exception {
        FileObject file = folder.createData(id, "instance");
        file.setAttribute("instanceClass", type.getName());
        file.setAttribute("position", position);
        file.setAttribute("inline", inline);
        return file;
    }

    /** A class entry target with the public no-argument constructor the reader requires. */
    public static final class DemoEditor {

        public DemoEditor() {
        }
    }

    /** Counts how often the reader creates it. */
    public static final class CountingEditor {

        static final AtomicInteger CREATED = new AtomicInteger();

        public CountingEditor() {
            CREATED.incrementAndGet();
        }
    }
}
