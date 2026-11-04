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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.netbeans.modules.nbfx.platform.shell.AppState;
import org.netbeans.modules.nbfx.platform.shell.ViewStateStore;
import org.openide.util.NbPreferences;

/**
 * IDE project state: recent projects, open/selected project and the project tree expansion state.
 * It uses the same preferences node as the old shell state, so existing userdirs migrate without a
 * copy step.
 */
final class ProjectState implements ViewStateStore {

    private static final Logger LOG = Logger.getLogger(ProjectState.class.getName());

    private static final String PREFS_PATH = "org/netbeans/modules/nbfx/session";
    private static final String RECENT_PROJECTS = "project.recent";
    private static final String PROJECT_OPEN = "project.open";
    private static final String PROJECT_SELECTED = "project.selected";
    private static final String TREE_EXPANDED_NODE = "tree.expanded";
    private static final String TREE_SELECTED_NODE = "tree.selected";
    private static final int MAX_RECENT_PROJECTS = 10;

    private final Preferences prefs;

    ProjectState() {
        this(NbPreferences.root().node(PREFS_PATH), null);
    }

    ProjectState(AppState appState) {
        this(NbPreferences.root().node(PREFS_PATH), appState);
    }

    ProjectState(Preferences prefs) {
        this(prefs, null);
    }

    ProjectState(Preferences prefs, AppState appState) {
        this.prefs = prefs;
        if (appState != null) {
            migrateLegacySession(appState);
        }
    }

    /** A recent project: its directory path and the resolved menu icon name (may be {@code null}). */
    public record RecentProject(String path, String iconName) {
    }

    /** Converts the single-project session written by older versions, once. */
    private void migrateLegacySession(AppState appState) {
        try {
            String legacyOpen = prefs.get(PROJECT_OPEN, null);
            boolean legacyFlag = "true".equals(legacyOpen) || "false".equals(legacyOpen);
            if (appState.hasSessionLayout() || (!legacyFlag && !appState.hasLegacyLayouts())) {
                return;
            }
            String last = legacyLastProject();
            AppState.Layout layout = appState.readLegacyLayout(last);
            appState.clearLegacyLayouts();
            appState.migrateSessionLayout(layout);
            boolean wasOpen = !"false".equals(legacyOpen);
            setOpenProjects(wasOpen && last != null ? List.of(last) : List.of(), last);
            LOG.info(() -> "Migrated the legacy project session" + (last == null ? "" : " of " + last));
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Could not migrate the legacy project session", ex);
        }
    }

    private String legacyLastProject() {
        String raw = get(RECENT_PROJECTS, "");
        for (String line : raw.split("\n")) {
            if (!line.isBlank()) {
                int tab = line.indexOf('\t');
                return tab < 0 ? line : line.substring(0, tab);
            }
        }
        return null;
    }

    /** The recent projects, most-recent first. */
    List<RecentProject> getRecentProjects() {
        String raw = get(RECENT_PROJECTS, "");
        if (raw.isBlank()) {
            return List.of();
        }
        List<RecentProject> list = new ArrayList<>();
        for (String line : raw.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int tab = line.indexOf('\t');
            if (tab < 0) {
                list.add(new RecentProject(line, null));
            } else {
                String iconName = line.substring(tab + 1);
                list.add(new RecentProject(line.substring(0, tab), iconName.isBlank() ? null : iconName));
            }
        }
        return list;
    }

    /** Records a project as the most recent, de-duplicating and bounding the list. */
    void addRecentProject(String path, String iconName) {
        if (path == null || path.isBlank()) {
            return;
        }
        List<RecentProject> list = new ArrayList<>(getRecentProjects());
        list.removeIf(rp -> rp.path().equals(path));
        list.addFirst(new RecentProject(path, iconName));
        while (list.size() > MAX_RECENT_PROJECTS) {
            list.removeLast();
        }
        prefs.put(RECENT_PROJECTS, serialize(list));
        flush();
    }

    /** Removes a project from the recent list (e.g. when its directory no longer exists). */
    void removeRecentProject(String path) {
        List<RecentProject> list = new ArrayList<>(getRecentProjects());
        if (list.removeIf(rp -> rp.path().equals(path))) {
            prefs.put(RECENT_PROJECTS, serialize(list));
            flush();
        }
    }

    void clearRecentProjects() {
        prefs.remove(RECENT_PROJECTS);
        flush();
    }

    /** The projects that were open when the application last exited, in the order they were opened. */
    List<String> getOpenProjects() {
        String raw = get(PROJECT_OPEN, "");
        if (raw.isBlank()) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (String line : raw.split("\n")) {
            if (!line.isBlank()) {
                paths.add(line);
            }
        }
        return List.copyOf(paths);
    }

    /** The path of the project that was selected when the application last exited, or {@code null}. */
    String getSelectedProject() {
        String selected = get(PROJECT_SELECTED, "");
        return selected.isBlank() ? null : selected;
    }

    /**
     * Records the open projects and selected project, so the next launch reopens exactly this
     * session. Persisted immediately, so a project closed before a crash is not reopened.
     */
    void setOpenProjects(List<String> paths, String selected) {
        List<String> kept = paths == null ? List.of()
                : paths.stream().filter(path -> path != null && !path.isBlank()).toList();
        if (kept.isEmpty()) {
            prefs.remove(PROJECT_OPEN);
        } else {
            prefs.put(PROJECT_OPEN, String.join("\n", kept));
        }
        if (selected == null || selected.isBlank() || !kept.contains(selected)) {
            prefs.remove(PROJECT_SELECTED);
        } else {
            prefs.put(PROJECT_SELECTED, selected);
        }
        flush();
    }

    /** Removes the persisted tree state of {@code project}. */
    void clearProjectState(File project) {
        if (project == null) {
            return;
        }
        String key = expansionKey(project.getPath());
        prefs.node(TREE_EXPANDED_NODE).remove(key);
        prefs.node(TREE_SELECTED_NODE).remove(key);
        flush();
    }

    List<String> getExpandedNodes(String projectPath) {
        String[] lines = projectLines(TREE_EXPANDED_NODE, projectPath, 1);
        List<String> list = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                list.add(lines[i]);
            }
        }
        return list;
    }

    @Override
    public void setExpandedNodes(String projectPath, List<String> nodes) {
        if (projectPath == null) {
            return;
        }
        Preferences node = prefs.node(TREE_EXPANDED_NODE);
        String key = expansionKey(projectPath);
        if (nodes == null || nodes.isEmpty()) {
            node.remove(key);
        } else {
            node.put(key, projectPath + "\n" + String.join("\n", nodes));
        }
        flush();
    }

    String getSelectedNode(String projectPath) {
        String[] lines = projectLines(TREE_SELECTED_NODE, projectPath, 2);
        if (lines.length < 2 || lines[1].isBlank()) {
            return null;
        }
        return lines[1];
    }

    @Override
    public void setSelectedNode(String projectPath, String nodeId) {
        if (projectPath == null) {
            return;
        }
        Preferences node = prefs.node(TREE_SELECTED_NODE);
        String key = expansionKey(projectPath);
        if (nodeId == null || nodeId.isBlank()) {
            node.remove(key);
        } else {
            node.put(key, projectPath + "\n" + nodeId);
        }
        flush();
    }

    private String[] projectLines(String childNode, String projectPath, int minLines) {
        if (projectPath == null) {
            return new String[0];
        }
        String raw = prefs.node(childNode).get(expansionKey(projectPath), "");
        if (raw.isBlank()) {
            return new String[0];
        }
        String[] lines = raw.split("\n", -1);
        if (lines.length < minLines || !projectPath.equals(lines[0])) {
            return new String[0];
        }
        return lines;
    }

    private String get(String key, String def) {
        try {
            return prefs.get(key, def);
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private void flush() {
        try {
            prefs.flush();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Could not flush project state", ex);
        }
    }

    private static String serialize(List<RecentProject> list) {
        StringBuilder sb = new StringBuilder();
        for (RecentProject rp : list) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(rp.path());
            if (rp.iconName() != null && !rp.iconName().isBlank()) {
                sb.append('\t').append(rp.iconName());
            }
        }
        return sb.toString();
    }

    private static String expansionKey(String projectPath) {
        return Integer.toHexString(projectPath.hashCode());
    }
}
