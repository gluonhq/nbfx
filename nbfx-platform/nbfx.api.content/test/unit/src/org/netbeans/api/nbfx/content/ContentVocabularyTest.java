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
package org.netbeans.api.nbfx.content;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/** Identity, kind and state vocabulary behaves as the neutral contract documents. */
public class ContentVocabularyTest {

    @Test
    public void contentIdRoundTripsAndComparesByValue() {
        ContentId id = ContentId.of(" nbfx:map ");
        assertEquals("nbfx", id.namespace());
        assertEquals("map", id.name());
        assertEquals("nbfx:map", id.value());
        assertEquals(new ContentId("nbfx", "map"), id);
        assertNotEquals(ContentId.of("nbfx:map"), ContentId.of("nbfx:chart"));
    }

    @Test
    public void contentIdRequiresANamespace() {
        assertThrows(IllegalArgumentException.class, () -> ContentId.of("map"));
        assertThrows(IllegalArgumentException.class, () -> ContentId.of("nbfx:"));
        assertThrows(IllegalArgumentException.class, () -> ContentId.of(":map"));
    }

    @Test
    public void contentKindResolvesBuiltInsAndProducts() {
        assertSame(ContentKind.EDITOR, ContentKind.of("editor"));
        assertSame(ContentKind.EDITOR, ContentKind.of("NBFX:EDITOR"));
        assertSame(ContentKind.VIEW, ContentKind.of("view"));
        assertSame(ContentKind.OUTPUT, ContentKind.of("output"));
        assertSame(ContentKind.TERMINAL, ContentKind.of("terminal"));
        assertSame(ContentKind.CANVAS, ContentKind.of("canvas"));

        ContentKind custom = ContentKind.of("product:map");
        assertEquals("product:map", custom.value());
        assertEquals(ContentKind.of("product:map"), custom);
        assertNotEquals(ContentKind.VIEW, custom);
    }

    @Test
    public void contentStateDefensivelyCopiesItsPayload() {
        Map<String, String> payload = new HashMap<>();
        payload.put("selection", "1");
        ContentState state = new ContentState(ContentId.of("nbfx:editor"), "buffer-1", payload);

        payload.put("selection", "2");

        assertEquals("1", state.payload().get("selection"));
        assertThrows(UnsupportedOperationException.class,
                () -> state.payload().put("other", "value"));
    }

    @Test
    public void contentStateRequiresAnInstanceKey() {
        ContentId id = ContentId.of("nbfx:editor");
        assertThrows(IllegalArgumentException.class, () -> new ContentState(id, " ", Map.of()));
        assertThrows(NullPointerException.class, () -> new ContentState(id, null, Map.of()));
        assertEquals(Map.of(), ContentState.empty(id, "buffer-1").payload());
    }
}