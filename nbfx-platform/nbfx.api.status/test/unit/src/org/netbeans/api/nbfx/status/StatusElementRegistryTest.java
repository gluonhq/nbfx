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
package org.netbeans.api.nbfx.status;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import org.junit.Test;
import org.netbeans.api.nbfx.layer.LayerRegistration;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Verifies the ordering and layer metadata applied to registered status elements: by slot, then
 * position, then id. Discovery is metadata-only; loading initializes additional elements. The
 * elements never build a node, so no JavaFX toolkit is required.
 *
 * @since 1.2
 */
public class StatusElementRegistryTest {

    @Test
    public void sortsBySlotThenPositionThenId() {
        StatusElementSpec right = spec("a-right", StatusAlignment.RIGHT, 10);
        StatusElementSpec leftLate = spec("b-left", StatusAlignment.LEFT, 20);
        StatusElementSpec leftEarly = spec("c-left", StatusAlignment.LEFT, 10);
        StatusElementSpec center = spec("d-center", StatusAlignment.CENTER, 5);
        StatusElementSpec fill = spec("e-fill", StatusAlignment.FILL, 0);

        List<StatusElementSpec> sorted = StatusElementRegistry.sort(
                List.of(right, leftLate, leftEarly, center, fill));

        assertEquals(List.of("c-left", "b-left", "d-center", "e-fill", "a-right"),
                sorted.stream().map(StatusElementSpec::id).toList());
    }

    @Test
    public void breaksPositionTiesById() {
        StatusElementSpec second = spec("z", StatusAlignment.LEFT, 1);
        StatusElementSpec first = spec("a", StatusAlignment.LEFT, 1);

        List<StatusElementSpec> sorted = StatusElementRegistry.sort(List.of(second, first));

        assertEquals(List.of("a", "z"), sorted.stream().map(StatusElementSpec::id).toList());
    }

    @Test
    public void initializesAdditionalElementsWithTheShellLookup() {
        AtomicReference<Lookup> received = new AtomicReference<>();
        StatusElement element = new Element("contextual", StatusAlignment.LEFT, 0) {
            @Override
            public void init(Lookup services) {
                received.set(services);
            }
        };
        Lookup services = Lookups.fixed();
        StatusElementRegistry.load(List.of(element), services);
        assertSame(services, received.get());
    }

    @Test
    public void readsAttributesFromLayerEntries() throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        FileObject folder = FileUtil.createFolder(fs.getRoot(), "NbFx/Status");
        FileObject file = folder.createData("registered", "instance");
        file.setAttribute("alignment", "RIGHT");
        file.setAttribute("position", 7);

        List<StatusElementSpec> elements = StatusElementRegistry.read(
                List.of(new LayerRegistration<>(file, Element.class)));

        assertEquals("registered", elements.get(0).id());
        assertEquals(StatusAlignment.RIGHT, elements.get(0).alignment());
        assertEquals(7, elements.get(0).position());
        assertEquals(Element.class, elements.get(0).type());
    }

    private static StatusElementSpec spec(String id, StatusAlignment alignment, int position) {
        return new StatusElementSpec(id, alignment, position, Element.class);
    }

    /** A public element class for the layer registration metadata tests. */
    public static class Element implements StatusElement {

        private final String id;
        private final StatusAlignment alignment;
        private final int position;

        public Element() {
            this("element", StatusAlignment.LEFT, 0);
        }

        Element(String id, StatusAlignment alignment, int position) {
            this.id = id;
            this.alignment = alignment;
            this.position = position;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public StatusAlignment alignment() {
            return alignment;
        }

        @Override
        public int position() {
            return position;
        }

        @Override
        public Node getNode() {
            return null;
        }
    }
}
