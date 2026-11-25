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

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

/** The neutral transfer vocabulary resolves built-in and product values. */
public class TransferVocabularyTest {

    @Test
    public void transferKindResolvesBuiltInsAndProducts() {
        assertSame(TransferKind.CONTENT, TransferKind.of("content"));
        assertSame(TransferKind.CONTENT, TransferKind.of("NBFX:CONTENT"));
        assertSame(TransferKind.FILES, TransferKind.of("files"));
        assertSame(TransferKind.URIS, TransferKind.of("uris"));
        assertSame(TransferKind.TEXT, TransferKind.of("text"));
        assertSame(TransferKind.NODES, TransferKind.of("nodes"));

        TransferKind custom = TransferKind.of("product:layer");
        assertEquals("product:layer", custom.value());
        assertEquals(TransferKind.of("product:layer"), custom);
        assertNotEquals(TransferKind.FILES, custom);
    }

    @Test
    public void transferModeHasMoveCopyAndLink() {
        assertEquals(List.of("MOVE", "COPY", "LINK"),
                Arrays.stream(TransferMode.values()).map(Enum::name).toList());
    }

    @Test
    public void dockPlacementCoversTheNeutralPlacements() {
        assertEquals(List.of(
                "INTO_GROUP", "BEFORE", "AFTER", "TAB_STRIP",
                "SPLIT_LEFT", "SPLIT_RIGHT", "SPLIT_TOP", "SPLIT_BOTTOM",
                "EDGE_LEFT", "EDGE_RIGHT", "EDGE_TOP", "EDGE_BOTTOM",
                "FLOAT", "NEW_WINDOW"),
                Arrays.stream(DockPlacement.values()).map(Enum::name).toList());
    }
}