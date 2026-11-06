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
package org.netbeans.modules.nbfx.platform.status;

import org.netbeans.api.nbfx.status.StatusAlignment;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Test;

/**
 * Verifies the ordering applied to registered status elements: by slot, then position, then id.
 * The elements never build a node, so no JavaFX toolkit is required.
 *
 * @since 1.0
 */
public class StatusElementRegistryTest {

    @Test
    public void sortsBySlotThenPositionThenId() {
        StatusElement right = element("a-right", StatusAlignment.RIGHT, 10);
        StatusElement leftLate = element("b-left", StatusAlignment.LEFT, 20);
        StatusElement leftEarly = element("c-left", StatusAlignment.LEFT, 10);
        StatusElement center = element("d-center", StatusAlignment.CENTER, 5);

        List<StatusElement> sorted =
                StatusElementRegistry.sort(List.of(right, leftLate, leftEarly, center));

        assertEquals(List.of("c-left", "b-left", "d-center", "a-right"),
                sorted.stream().map(StatusElement::id).toList());
    }

    @Test
    public void breaksPositionTiesById() {
        StatusElement second = element("z", StatusAlignment.LEFT, 1);
        StatusElement first = element("a", StatusAlignment.LEFT, 1);

        List<StatusElement> sorted = StatusElementRegistry.sort(List.of(second, first));

        assertEquals(List.of("a", "z"), sorted.stream().map(StatusElement::id).toList());
    }

    @Test
    public void initializesAdditionalElementsWithTheShellLookup() {
        AtomicReference<Lookup> received = new AtomicReference<>();
        StatusElementHolder holder = new StatusElementHolder() {
            @Override
            public String getId() {
                return "contextual";
            }

            @Override
            public void init(Lookup services) {
                received.set(services);
            }

            @Override
            public Node getNode() {
                return null;
            }
        };
        Lookup services = Lookups.fixed();
        StatusElementRegistry.discover(List.of(
                new StatusElement("contextual", StatusAlignment.LEFT, 1, holder)), services);
        assertSame(services, received.get());
    }

    private static StatusElement element(String id, StatusAlignment alignment, int position) {
        return new StatusElement(id, alignment, position, new StatusElementHolder() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public Node getNode() {
                return null;
            }
        });
    }
}
