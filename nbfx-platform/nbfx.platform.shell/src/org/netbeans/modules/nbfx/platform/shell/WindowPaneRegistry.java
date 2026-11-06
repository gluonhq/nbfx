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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.modules.nbfx.platform.shell.spi.WindowPane;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Discovers permanent window panes from {@code NbFx/WindowPanes}. The platform declares MAIN and
 * NAVIGATOR there like any other pane, so registrations are the single source of truth.
 *
 * @since 1.0
 */
public final class WindowPaneRegistry {

    private static final Logger LOG = Logger.getLogger(WindowPaneRegistry.class.getName());

    private WindowPaneRegistry() {
    }

    /** All declared panes, by position and id. */
    public static List<WindowPane> discover() {
        return read(FileUtil.getConfigFile(LayerPaths.WINDOW_PANES));
    }

    /** The pane with {@code id}, if declared. */
    public static Optional<WindowPane> find(String id) {
        return discover().stream().filter(pane -> pane.id().equalsIgnoreCase(id)).findFirst();
    }

    /** The pane hosting {@code areaId}, if any. */
    public static Optional<WindowPane> areaPane(String areaId) {
        if (areaId == null || areaId.isBlank()) {
            return Optional.empty();
        }
        return discover().stream().filter(pane -> areaId.equalsIgnoreCase(pane.area())).findFirst();
    }

    /** The default role for showing a view when no area resolves; never the editor pane. */
    public static NbfxTabPane.PaneRole defaultViewRole() {
        return defaultViewRole(discover());
    }

    /**
     * The default view role among {@code panes}; never the editor pane. Falls back to the built-in
     * navigator role when no permanent view pane is declared, which keeps a view placeable even in
     * a partially configured shell.
     */
    public static NbfxTabPane.PaneRole defaultViewRole(List<WindowPane> panes) {
        return panes.stream()
                .filter(pane -> pane.kind() == PaneKind.VIEW)
                .min(Comparator.comparingInt(WindowPane::position))
                .map(WindowPane::role)
                .orElse(NbfxTabPane.PaneRole.NAVIGATOR);
    }

    /** Reads panes from {@code folder}; package-visible for tests. */
    static List<WindowPane> read(FileObject folder) {
        Map<String, WindowPane> panes = new LinkedHashMap<>();
        if (folder != null) {
            for (FileObject child : folder.getChildren()) {
                WindowPane pane = readPane(child);
                if (pane != null) {
                    panes.put(pane.id(), pane);
                }
            }
        }
        List<WindowPane> result = new ArrayList<>(panes.values());
        result.sort(Comparator.comparingInt(WindowPane::position).thenComparing(WindowPane::id));
        return List.copyOf(result);
    }

    private static WindowPane readPane(FileObject file) {
        if (file == null || !file.isData()) {
            return null;
        }
        String id = file.getName();
        if (!id.matches("\\w+")) {
            LOG.warning("Ignoring pane with invalid role id: " + id);
            return null;
        }
        PaneKind kind = parseKind(file.getAttribute("kind"));
        if (kind == null) {
            LOG.warning("Pane " + id + " has an unknown kind");
            return null;
        }
        if ("MAIN".equals(id)) {
            kind = PaneKind.EDITOR;
        } else if (kind == PaneKind.EDITOR) {
            LOG.warning("Pane " + id + " is an editor pane; only MAIN may be one");
            return null;
        }
        String displayName = file.getAttribute("displayName") instanceof String value
                && !value.isBlank() ? value : id;
        String area = file.getAttribute("area") instanceof String value ? value : "";
        boolean keepWhenEmpty = booleanAttribute(file, "keepWhenEmpty", false);
        int position = file.getAttribute("position") instanceof Integer value
                ? value : Integer.MAX_VALUE;
        return new WindowPane(id, displayName, kind, area, keepWhenEmpty, position);
    }

    private static PaneKind parseKind(Object value) {
        if (value instanceof String name) {
            try {
                return PaneKind.valueOf(name);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return PaneKind.VIEW;
    }

    private static boolean booleanAttribute(FileObject file, String name, boolean fallback) {
        Object value = file.getAttribute(name);
        return value instanceof Boolean flag ? flag : fallback;
    }
}
