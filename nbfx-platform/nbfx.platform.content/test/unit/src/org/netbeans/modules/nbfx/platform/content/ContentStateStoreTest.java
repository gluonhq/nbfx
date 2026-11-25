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
package org.netbeans.modules.nbfx.platform.content;

import java.util.Map;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.ContentState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The content store round-trips opaque payloads keyed by identity and instance key. */
public class ContentStateStoreTest {

    private Preferences node;
    private ContentStateStore store;

    @Before
    public void setUp() {
        node = Preferences.userRoot().node("nbfx-content-test").node(UUID.randomUUID().toString());
        store = new ContentStateStore(node);
    }

    @After
    public void tearDown() {
        try {
            node.removeNode();
        } catch (BackingStoreException ex) {
            // Best effort; the node is unique per test anyway.
        }
    }

    @Test
    public void saveLoadAndRemoveRoundTripWithOpaquePayload() {
        ContentId id = ContentId.of("nbfx:editor");
        ContentState state = new ContentState(id, "buffer-1", Map.of(
                "caret", "12",
                "text", "a=b\nc\n\u00e4",
                "empty", ""));

        store.save(state);

        assertEquals(state, store.load(id, "buffer-1").orElseThrow());
        store.remove(id, "buffer-1");
        assertFalse(store.load(id, "buffer-1").isPresent());
    }

    @Test
    public void sameIdentityDifferentInstanceKeysStayDistinct() {
        ContentId id = ContentId.of("nbfx:editor");
        ContentState first = new ContentState(id, "buffer-1", Map.of("v", "1"));
        ContentState second = new ContentState(id, "buffer-2", Map.of("v", "2"));

        store.save(first);
        store.save(second);

        assertEquals(first, store.load(id, "buffer-1").orElseThrow());
        assertEquals(second, store.load(id, "buffer-2").orElseThrow());
        assertEquals(2, store.loadAll().size());
    }

    @Test
    public void saveReplacesThePreviousPayload() {
        ContentId id = ContentId.of("nbfx:editor");
        store.save(new ContentState(id, "buffer-1", Map.of("v", "1")));
        store.save(new ContentState(id, "buffer-1", Map.of("v", "2")));

        assertEquals(Map.of("v", "2"), store.load(id, "buffer-1").orElseThrow().payload());
        assertEquals(1, store.loadAll().size());
    }

    @Test
    public void clearDropsEverything() {
        ContentId id = ContentId.of("nbfx:editor");
        store.save(new ContentState(id, "buffer-1", Map.of("v", "1")));
        store.save(new ContentState(id, "buffer-2", Map.of("v", "2")));

        store.clear();

        assertTrue(store.loadAll().isEmpty());
        assertFalse(store.load(id, "buffer-1").isPresent());
    }
}