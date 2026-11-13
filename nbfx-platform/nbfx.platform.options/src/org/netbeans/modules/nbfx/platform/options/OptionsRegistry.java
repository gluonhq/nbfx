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
package org.netbeans.modules.nbfx.platform.options;

import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.options.OptionsPanelController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.Lookups;

/**
 * Discovers the {@link OptionsPanelController}s registered under {@code NbFx/Options} in the module
 * layer. The layer entry is authoritative for the id, display name and position; the controller
 * supplies the component and lifecycle.
 *
 * @since 1.0
 */
final class OptionsRegistry {

    private OptionsRegistry() {
    }

    /**
     * Discovers and orders all registered options panels. Reads the configuration filesystem, so it
     * must be called once the platform is initialized.
     *
     * @return the panel registrations, ordered by position then id
     */
    static List<OptionsPanelEntry> discover() {
        FileObject folder = FileUtil.getConfigFile(LayerPaths.OPTIONS);
        if (folder == null) {
            return List.of();
        }
        Map<String, OptionsPanelController> panelsByClass = new LinkedHashMap<>();
        for (OptionsPanelController panel : Lookups.forPath(LayerPaths.OPTIONS)
                .lookupAll(OptionsPanelController.class)) {
            panelsByClass.putIfAbsent(panel.getClass().getName(), panel);
        }
        List<OptionsPanelEntry> registrations = new ArrayList<>();
        for (FileObject file : folder.getChildren()) {
            if (!file.isData() || !file.hasExt("instance")) {
                continue;
            }
            Object className = file.getAttribute("instanceClass");
            if (!(className instanceof String name)) {
                continue;
            }
            OptionsPanelController panel = panelsByClass.get(name);
            if (panel != null) {
                registrations.add(read(file, panel));
            }
        }
        return sort(registrations);
    }

    /** Reads the registrations of {@code panels}, keyed by the layer file's base name. */
    static List<OptionsPanelEntry> read(FileObject folder, Map<String, OptionsPanelController> panels) {
        List<OptionsPanelEntry> registrations = new ArrayList<>();
        for (Map.Entry<String, OptionsPanelController> entry : panels.entrySet()) {
            FileObject file = folder == null ? null : folder.getFileObject(entry.getKey(), "instance");
            registrations.add(read(entry.getKey(), file, entry.getValue()));
        }
        return sort(registrations);
    }

    private static OptionsPanelEntry read(FileObject file, OptionsPanelController panel) {
        return read(file.getName(), file, panel);
    }

    private static OptionsPanelEntry read(String id, FileObject file, OptionsPanelController panel) {
        String displayName = stringAttribute(file, "displayName", id);
        int position = intAttribute(file, "position", Integer.MAX_VALUE);
        return new OptionsPanelEntry(id, displayName, position, panel);
    }

    /** Orders panels by position, then id. */
    static List<OptionsPanelEntry> sort(List<OptionsPanelEntry> registrations) {
        List<OptionsPanelEntry> sorted = new ArrayList<>(registrations);
        sorted.sort(Comparator
                .comparingInt(OptionsPanelEntry::position)
                .thenComparing(OptionsPanelEntry::id));
        return List.copyOf(sorted);
    }

    private static String stringAttribute(FileObject file, String name, String fallback) {
        Object value = file == null ? null : file.getAttribute(name);
        return value instanceof String text ? text : fallback;
    }

    private static int intAttribute(FileObject file, String name, int fallback) {
        Object value = file == null ? null : file.getAttribute(name);
        return value instanceof Integer number ? number : fallback;
    }
}
