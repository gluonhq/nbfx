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

import org.junit.Test;
import org.netbeans.api.nbfx.content.ContentManager;
import org.openide.util.Lookup;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The shell owns one content manager and publishes that instance in its lookup. */
public class ShellContentManagerTest {

    @Test
    public void shellLookupPublishesThePerShellContentManager() {
        ShellApplication shell = new ShellApplication();

        Lookup lookup = shell.shellLookup();
        ContentManager manager = lookup.lookup(ContentManager.class);

        assertNotNull("content manager in the shell lookup", manager);
        assertSame("the lookup exposes the owned instance", shell.getContentManager(), manager);
        assertTrue(manager.openContent().isEmpty());
    }
}