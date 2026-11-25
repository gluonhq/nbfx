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

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.netbeans.api.nbfx.layout.AreaStyle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

/** Tests shell-region identities, kinds and immutable specs. */
public class ShellRegionTest {

    @Test
    public void builtInsCanonicalizeAndProductIdsRemainNamespaced() {
        assertSame(ShellRegionId.BRAND, ShellRegionId.of("BRAND"));
        assertSame(ShellRegionId.DOCK, ShellRegionId.of("NBFX:DOCK"));
        assertSame(ShellRegionKind.MENUBAR, ShellRegionKind.of("MENUBAR"));
        assertEquals(ShellRegionId.BRAND, new ShellRegionId("NBFX", "BRAND"));
        assertEquals(ShellRegionKind.TOOLBAR, new ShellRegionKind("NBFX:TOOLBAR"));

        ShellRegionId id = ShellRegionId.of("acme:utility");
        ShellRegionKind kind = ShellRegionKind.of("acme:tool");
        assertEquals("acme:utility", id.value());
        assertEquals("acme:tool", kind.value());
        assertEquals(id, ShellRegionId.of(id.value()));
        assertEquals(kind, ShellRegionKind.of(kind.value()));
    }

    @Test
    public void rejectsUnqualifiedOrMalformedProductIds() {
        assertThrows(IllegalArgumentException.class, () -> ShellRegionId.of("utility"));
        assertThrows(IllegalArgumentException.class, () -> ShellRegionKind.of("tool"));
        assertThrows(IllegalArgumentException.class, () -> ShellRegionId.of("acme:"));
        assertThrows(NullPointerException.class, () -> new ShellRegionId(null, "brand"));
    }

    @Test
    public void specDefensivelyCopiesProfiles() {
        Set<String> profiles = new HashSet<>();
        profiles.add("main");
        ShellRegionSpec spec = new ShellRegionSpec(ShellRegionId.BRAND, ShellRegionKind.BRAND,
                10, profiles, AreaStyle.EMPTY, true);
        profiles.clear();
        assertEquals(Set.of("main"), spec.profiles());
        assertThrows(UnsupportedOperationException.class, () -> spec.profiles().add("other"));
        assertThrows(IllegalArgumentException.class,
                () -> new ShellRegionSpec(ShellRegionId.BRAND, ShellRegionKind.BRAND,
                        10, Set.of("main,compact"), AreaStyle.EMPTY, true));
    }
}
