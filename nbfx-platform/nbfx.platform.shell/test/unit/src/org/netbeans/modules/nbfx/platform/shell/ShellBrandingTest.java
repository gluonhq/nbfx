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
package org.netbeans.modules.nbfx.platform.shell;

import org.netbeans.modules.nbfx.platform.shell.api.ShellBranding;
import org.junit.Test;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;

/**
 * Branding selection is explicit and deterministic when the neutral default and a product branding
 * are both registered.
 */
public class ShellBrandingTest {

    @Test
    public void highestPriorityWins() {
        Lookup lookup = Lookups.fixed(new LowBranding(), new HighBrandingA());
        assertEquals("high-a", ShellBranding.select(lookup).orElseThrow().applicationName());
    }

    @Test
    public void equalPrioritiesAreDeterministic() {
        String first = ShellBranding.select(Lookups.fixed(new HighBrandingB(), new HighBrandingA()))
                .orElseThrow().applicationName();
        String second = ShellBranding.select(Lookups.fixed(new HighBrandingA(), new HighBrandingB()))
                .orElseThrow().applicationName();
        assertEquals(first, second);
    }

    private static class LowBranding implements ShellBranding {
        @Override
        public String applicationName() {
            return "low";
        }
    }

    private static class HighBrandingA implements ShellBranding {
        @Override
        public int priority() {
            return 10;
        }

        @Override
        public String applicationName() {
            return "high-a";
        }
    }

    private static class HighBrandingB implements ShellBranding {
        @Override
        public int priority() {
            return 10;
        }

        @Override
        public String applicationName() {
            return "high-b";
        }
    }
}
