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
package org.netbeans.modules.nbfx.ide.workbench;

import java.util.List;
import java.util.prefs.Preferences;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Project state is persisted outside the generic shell, under the same preference keys.
 */
public class ProjectStateTest {

    @Test
    public void recentsOpenProjectsAndTreeStateRoundTrip() {
        Preferences prefs = Preferences.userRoot().node("nbfx-project-state-" + System.nanoTime());
        ProjectState state = new ProjectState(prefs);

        state.addRecentProject("/one", "icon");
        state.addRecentProject("/two", null);
        state.addRecentProject("/one", "icon");
        assertEquals(List.of(
                new ProjectState.RecentProject("/one", "icon"),
                new ProjectState.RecentProject("/two", null)),
                state.getRecentProjects());

        state.setOpenProjects(List.of("/one", "/two"), "/two");
        assertEquals(List.of("/one", "/two"), state.getOpenProjects());
        assertEquals("/two", state.getSelectedProject());

        state.setExpandedNodes("/one", List.of("root", "src"));
        state.setSelectedNode("/one", "src/Main.java");
        assertEquals(List.of("root", "src"), state.getExpandedNodes("/one"));
        assertEquals("src/Main.java", state.getSelectedNode("/one"));

        state.clearProjectState(new java.io.File("/one"));
        assertEquals(List.of(), state.getExpandedNodes("/one"));
        assertNull(state.getSelectedNode("/one"));

        state.clearRecentProjects();
        assertEquals(List.of(), state.getRecentProjects());
    }
}
