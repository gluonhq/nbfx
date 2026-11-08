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

import javafx.scene.Node;
import javafx.scene.layout.Region;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.modules.nbfx.platform.shell.api.ShellContext;
import org.netbeans.modules.nbfx.platform.shell.api.ShellIconProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * A command's own icon wins over the provider fallback.
 */
public class ShellIconProviderTest {

    private static Command command(String id, Node icon) {
        return new RunnableCommand(id, id, null, () -> { }) {
            @Override
            public Node getIcon() {
                return icon;
            }
        };
    }

    @Test
    public void commandIconWinsOverTheProvider() {
        Node own = new Region();
        ShellIconProvider provider = (id, size) -> new Region();
        Lookup lookup = Lookups.fixed(provider);
        ShellContext context = new ShellContext(lookup);

        assertSame(own, ShellIconProvider.icon(context, command("own", own), 16));
    }

    @Test
    public void providerSuppliesTheFallbackIcon() {
        Node fallback = new Region();
        ShellIconProvider provider = (id, size) -> fallback;
        ShellContext context = new ShellContext(Lookups.fixed(provider));

        assertSame(fallback, ShellIconProvider.icon(context, command("fallback", null), 16));
    }

    @Test
    public void noIconAnywhereIsNull() {
        ShellContext context = new ShellContext(Lookups.fixed());

        assertNull(ShellIconProvider.icon(context, command("none", null), 16));
    }
}
