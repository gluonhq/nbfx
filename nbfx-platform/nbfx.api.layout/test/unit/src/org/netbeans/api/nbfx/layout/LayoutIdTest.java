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
package org.netbeans.api.nbfx.layout;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/** Tests stable id parsing, canonical forms and built-in constants. */
public class LayoutIdTest {

    @Test
    public void builtInShortFormsResolveToConstants() {
        assertSame(PaneRole.MAIN, PaneRole.of("main"));
        assertSame(ViewAreaKind.SIDE, ViewAreaKind.of("side"));
        assertSame(PresentationId.TABBED, PresentationId.of("tabbed"));
        assertSame(ViewAreaKind.SIDE, ViewAreaKind.of("SIDE"));
    }

    @Test
    public void productIdsRoundTripWithNamespace() {
        PaneRole role = PaneRole.of("acme:inspector");
        ViewAreaKind kind = ViewAreaKind.of("acme:timeline");
        DockGroupId group = DockGroupId.of("acme:editor.main");
        SlotId slot = SlotId.of("acme:drop.1");
        PresentationId presentation = PresentationId.of("acme:ribbon");

        assertEquals("acme:inspector", role.value());
        assertEquals("acme:timeline", kind.value());
        assertEquals("acme:editor.main", group.value());
        assertEquals("acme:drop.1", slot.value());
        assertEquals("acme:ribbon", presentation.value());
        assertEquals(role, PaneRole.of(role.value()));
        assertEquals(group, DockGroupId.of(group.value()));
    }

    @Test
    public void malformedIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PaneRole.of(" "));
        assertThrows(IllegalArgumentException.class, () -> PaneRole.of("acme:"));
        assertThrows(IllegalArgumentException.class, () -> DockGroupId.of("bad id"));
        assertThrows(IllegalArgumentException.class, () -> PresentationId.of(":bad"));
        assertThrows(IllegalArgumentException.class, () -> PaneRole.of("inspector"));
        assertThrows(IllegalArgumentException.class, () -> PresentationId.of("ribbon"));
        assertThrows(NullPointerException.class, () -> new DockGroupId(null, "main"));
        assertThrows(NullPointerException.class, () -> new SlotId("acme", null));
        assertThrows(NullPointerException.class, () -> new PresentationId(null, "tabbed"));
    }
}
