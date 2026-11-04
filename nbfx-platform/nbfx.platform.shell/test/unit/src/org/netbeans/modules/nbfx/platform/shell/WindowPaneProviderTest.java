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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane.PaneRole;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Pane providers are resolved from the shell context, in priority order, and receive the
 * composition that is being built.
 */
public class WindowPaneProviderTest {

    private static final class Provider implements WindowPaneProvider {

        private final int priority;
        private final AtomicReference<WindowPaneContext> seen;
        private NbfxTabPane created;

        Provider(int priority, AtomicReference<WindowPaneContext> seen) {
            this.priority = priority;
            this.seen = seen;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public NbfxTabPane createPane(WindowPane pane, WindowPaneContext context) {
            seen.set(context);
            created = new NbfxTabPane(pane.role(), pane.kind());
            return created;
        }
    }

    @Test
    public void highestPriorityProviderCreatesThePane() throws InterruptedException {
        AtomicReference<WindowPaneContext> seen = new AtomicReference<>();
        Provider low = new Provider(0, seen);
        Provider high = new Provider(10, seen);
        Lookup lookup = Lookups.fixed(low, high);

        FxTestSupport.onFx(() -> {
            ShellApplication shell = new ShellApplication();
            WindowPane compositionPane = new WindowPane("RIGHT_SIDEBAR", "Right", PaneKind.VIEW,
                    "right-sidebar", false, 30);
            WindowComposition composition = new WindowComposition(List.of(compositionPane), null);
            WindowPaneContext context = new WindowPaneContext(lookup, composition, shell);

            NbfxTabPane pane = shell.createPane(compositionPane, context);

            assertSame(high.created, pane);
            assertNull(low.created);
            assertEquals(PaneRole.of("RIGHT_SIDEBAR"), NbfxTabPane.roleOf(pane));
            assertSame(composition, seen.get().composition());
            assertSame(shell, seen.get().window());
            assertEquals(composition, context.composition());
            return null;
        });
    }
}
