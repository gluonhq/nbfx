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
import org.openide.util.Lookup;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The chrome contract: an empty chrome is safe, the default provider creates a default chrome, and
 * the context is the narrow shell contract.
 */
public class ShellChromeTest {

    @Test
    public void emptyChromeIsSafe() {
        assertNull(ShellChrome.NONE.top());
        assertNull(ShellChrome.NONE.bottom());
        ShellChrome.NONE.dispose();
    }

    @Test
    public void defaultProviderCreatesDefaultChrome() {
        ShellChrome chrome = new DefaultShellChromeProvider().create(new ShellContext(Lookup.getDefault()));
        assertNotNull(chrome);
        assertTrue(chrome instanceof DefaultShellChrome);
        assertNull(chrome.top());
        assertNull(chrome.bottom());
    }

    @Test
    public void contextKeepsTheProvidedLookup() {
        ShellContext context = new ShellContext(Lookup.getDefault());
        assertSame(Lookup.getDefault(), context.lookup());
    }
}
