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
package org.netbeans.api.nbfx.shell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Stateless discovery of declarative chrome profiles from {@code NbFx/ShellProfiles}. Malformed
 * entries are logged and skipped without preventing other profiles from loading.
 *
 * @since 1.4
 */
public final class ShellProfileRegistry {

    private static final Logger LOG = Logger.getLogger(ShellProfileRegistry.class.getName());

    private ShellProfileRegistry() {
    }

    /** All valid profiles ordered by stable id. */
    public static List<ShellProfileSpec> discover() {
        return read(FileUtil.getConfigFile(LayerPaths.SHELL_PROFILES));
    }

    /** The profile with {@code id}, if it is declared and valid. */
    public static Optional<ShellProfileSpec> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return discover().stream().filter(profile -> profile.id().equals(id)).findFirst();
    }

    /** Reads direct profile folders below {@code folder}; package-visible for tests. */
    static List<ShellProfileSpec> read(FileObject folder) {
        if (folder == null) {
            return List.of();
        }
        List<ShellProfileSpec> result = new ArrayList<>();
        for (FileObject file : folder.getChildren()) {
            if (!file.isFolder()) {
                continue;
            }
            ShellProfileSpec profile = readProfile(file);
            if (profile != null) {
                result.add(profile);
            }
        }
        result.sort(Comparator.comparing(ShellProfileSpec::id));
        return List.copyOf(result);
    }

    private static ShellProfileSpec readProfile(FileObject folder) {
        try {
            String id = ShellTokens.require(folder.getName(), "profile id");
            WindowKind windowKind = enumAttribute(folder, "windowKind", WindowKind.class);
            Set<ShellRegionId> regions = new LinkedHashSet<>();
            String regionText = stringAttribute(folder, "regions", "").trim();
            if (!regionText.isEmpty()) {
                for (String item : regionText.split("[,\\s]+", -1)) {
                    if (!ShellTokens.isToken(item)) {
                        throw new IllegalArgumentException("regions must contain atomic ids");
                    }
                    regions.add(ShellRegionId.of(item));
                }
            }
            WindowDecoration decoration = enumAttribute(folder, "decoration", WindowDecoration.class);
            Object systemMenuBar = folder.getAttribute("systemMenuBar");
            if (systemMenuBar != null && !(systemMenuBar instanceof Boolean)) {
                throw new IllegalArgumentException("systemMenuBar must be boolean");
            }
            boolean useSystemMenuBar = systemMenuBar == null || (Boolean) systemMenuBar;
            return new ShellProfileSpec(id, windowKind, regions, decoration, useSystemMenuBar);
        } catch (IllegalArgumentException ex) {
            LOG.warning("Cannot read shell profile " + folder.getPath() + ": " + ex.getMessage());
            return null;
        }
    }

    private static String stringAttribute(FileObject file, String name, String fallback) {
        Object value = file.getAttribute(name);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return text;
    }

    private static <T extends Enum<T>> T enumAttribute(FileObject file, String name, Class<T> type) {
        String value = stringAttribute(file, name, "").trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown " + name + ": " + value);
        }
    }
}
