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
package org.netbeans.modules.nbfx.platform.menus;

import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * The top-level menu registrations carry their bundle-backed display names, so the menu builder
 * never falls back to the raw folder ids.
 */
public class MenusTest {

    @Test
    public void topLevelMenusCarryBundleTitles() {
        assertTitle("File", "File");
        assertTitle("Edit", "Edit");
        assertTitle("View", "View");
        assertTitle("Navigate", "Navigate");
        assertTitle("Tools", "Tools");
        assertTitle("Window", "Window");
        assertTitle("Help", "Help");
    }

    private static void assertTitle(String id, String title) {
        FileObject file = FileUtil.getConfigFile("NbFx/Menus/" + id);
        assertNotNull("the " + id + " menu registration was not generated", file);
        assertEquals(title, file.getAttribute("displayName"));
    }
}
