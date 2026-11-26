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
package org.netbeans.api.nbfx.shell;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.netbeans.api.nbfx.layout.DockGroupId;
import org.netbeans.api.nbfx.layout.LayoutNode;

import static org.junit.Assert.*;

public class DockWindowContractTest {

    @Test
    public void windowIdUsesCanonicalNamespacedIdentity() {
        WindowId id = WindowId.of("nbfx:window.detached.7");
        assertEquals("nbfx", id.namespace());
        assertEquals("window.detached.7", id.name());
        assertEquals("nbfx:window.detached.7", id.value());
        assertEquals(id, WindowId.of(id.value()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void windowIdRejectsMissingNamespace() {
        WindowId.of("window");
    }

    @Test
    public void windowOptionsDefaultToCloseContent() {
        WindowOptions options = new WindowOptions(WindowKind.FLOATING, null, null, null, null,
                false, 400, 300, true, false, null);
        assertEquals(WindowClosePolicy.CLOSE_CONTENT, options.closePolicy());
        assertThrows(NullPointerException.class, () -> new WindowOptions(WindowKind.FLOATING,
                null, null, null, null, false, 400, 300, true, false, null, null));
    }

    @Test
    public void windowContentStateCopiesOpaquePayload() {
        WindowId id = WindowId.of("nbfx:window.test");
        LayoutNode layout = LayoutNode.group(DockGroupId.of("nbfx:docked.1"));
        Map<String, String> payload = new HashMap<>();
        payload.put("selected", "2");
        WindowContentState state = new WindowContentState(id, layout, payload);
        payload.put("selected", "9");

        assertEquals("2", state.payload().get("selected"));
        assertEquals(WindowContentState.empty(id, layout).payload(), Map.of());
        assertNull(WindowContentState.empty(id, null).layout());
    }
}
