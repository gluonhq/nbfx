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

import javafx.geometry.Point2D;
import org.junit.Test;
import org.openide.util.Lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Drop targets, requests and decisions carry the neutral transfer contract. */
public class TransferContractTest {

    @Test
    public void dropTargetValidatesAndHasAGlobalFallback() {
        DropTarget target = new DropTarget(DropTargetKind.GROUP, "main");
        assertEquals(DropTargetKind.GROUP, target.kind());
        assertEquals("main", target.id());

        DropTarget global = DropTarget.global();
        assertEquals(DropTargetKind.GLOBAL, global.kind());
        assertEquals("", global.id());

        assertThrows(NullPointerException.class, () -> new DropTarget(null, "x"));
        assertThrows(NullPointerException.class, () -> new DropTarget(DropTargetKind.AREA, null));
    }

    @Test
    public void dropTargetKindResolvesBuiltInsAndProducts() {
        assertSame(DropTargetKind.CONTENT, DropTargetKind.of("content"));
        assertSame(DropTargetKind.OVERLAY, DropTargetKind.of("overlay"));
        assertSame(DropTargetKind.GROUP, DropTargetKind.of("group"));
        assertSame(DropTargetKind.AREA, DropTargetKind.of("area"));
        assertSame(DropTargetKind.WINDOW, DropTargetKind.of("window"));
        assertSame(DropTargetKind.GLOBAL, DropTargetKind.of("NBFX:GLOBAL"));

        DropTargetKind custom = DropTargetKind.of("product:map");
        assertEquals("product:map", custom.value());
    }

    @Test
    public void allowedDecisionNeedsAnEffectAndRejectionAReason() {
        DropDecision allowed = DropDecision.allow(TransferMode.COPY, DockPlacement.INTO_GROUP);
        assertTrue(allowed.allowed());
        assertEquals(TransferMode.COPY, allowed.effect());
        assertEquals(DockPlacement.INTO_GROUP, allowed.placement());
        assertEquals("", allowed.reasonKey());

        DropDecision rejected = DropDecision.reject("drop.not.allowed", DockPlacement.SPLIT_RIGHT);
        assertFalse(rejected.allowed());
        assertNull(rejected.effect());
        assertEquals("drop.not.allowed", rejected.reasonKey());
        assertEquals(DockPlacement.SPLIT_RIGHT, rejected.placement());

        assertThrows(NullPointerException.class, () -> new DropDecision(true, null, "", null));
        assertThrows(IllegalArgumentException.class, () -> new DropDecision(false, null, "", null));
        assertThrows(IllegalArgumentException.class, () -> new DropDecision(false, null, " ", null));
    }

    @Test
    public void dropRequestCarriesSourceTargetAndContext() {
        TransferSource source = new TransferSource() {
            @Override
            public TransferKind kind() {
                return TransferKind.FILES;
            }

            @Override
            public Lookup data() {
                return Lookup.EMPTY;
            }
        };
        DropTarget target = new DropTarget(DropTargetKind.CONTENT, "editor");

        DropRequest request = new DropRequest(source, target, new Point2D(1, 2),
                TransferMode.MOVE, Lookup.EMPTY);
        assertSame(source, request.source());
        assertSame(target, request.target());
        assertEquals(new Point2D(1, 2), request.location());
        assertEquals(TransferMode.MOVE, request.requestedMode());
        assertSame(Lookup.EMPTY, request.context());

        DropRequest keyboard = new DropRequest(source, target, null, TransferMode.MOVE, Lookup.EMPTY);
        assertNull("a keyboard transfer names no point", keyboard.location());
    }
}