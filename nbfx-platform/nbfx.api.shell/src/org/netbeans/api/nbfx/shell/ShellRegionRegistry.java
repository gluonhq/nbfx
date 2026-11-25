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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.layout.AreaStyle;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Stateless discovery of declarative shell-region metadata from {@code NbFx/ShellRegions}. Only
 * direct child folders are regions; nested contribution/menu/action folders are not interpreted as
 * regions. Malformed entries are logged and skipped.
 *
 * @since 1.1
 */
public final class ShellRegionRegistry {

    private static final Logger LOG = Logger.getLogger(ShellRegionRegistry.class.getName());

    private ShellRegionRegistry() {
    }

    /** All regions ordered by position and canonical id. */
    public static List<ShellRegionSpec> discover() {
        return read(FileUtil.getConfigFile(LayerPaths.SHELL_REGIONS));
    }

    /** The region with {@code id}, if it is declared and valid. */
    public static Optional<ShellRegionSpec> find(ShellRegionId id) {
        Objects.requireNonNull(id, "id");
        return discover().stream().filter(region -> region.id().equals(id)).findFirst();
    }

    /**
     * Regions applicable to an exact, case-sensitive profile id. A null/blank id and regions with an
     * empty profile set mean all profiles.
     */
    public static List<ShellRegionSpec> forProfile(String profileId) {
        return forProfile(FileUtil.getConfigFile(LayerPaths.SHELL_REGIONS), profileId);
    }

    /** Reads profile-filtered regions below {@code folder}; package-visible for tests. */
    static List<ShellRegionSpec> forProfile(FileObject folder, String profileId) {
        List<ShellRegionSpec> regions = read(folder);
        if (profileId == null || profileId.isBlank()) {
            return regions;
        }
        return regions.stream()
                .filter(region -> region.profiles().isEmpty() || region.profiles().contains(profileId))
                .toList();
    }

    /** Reads direct region folders below {@code folder}; package-visible for tests. */
    static List<ShellRegionSpec> read(FileObject folder) {
        if (folder == null) {
            return List.of();
        }
        List<ShellRegionSpec> result = new ArrayList<>();
        Set<ShellRegionId> seen = new HashSet<>();
        FileObject[] children = folder.getChildren();
        Arrays.sort(children, Comparator.comparing(FileObject::getNameExt));
        for (FileObject child : children) {
            if (!child.isFolder()) {
                continue;
            }
            ShellRegionSpec region = readRegion(child);
            if (region == null) {
                continue;
            }
            if (!seen.add(region.id())) {
                LOG.warning("Duplicate shell region id " + region.id() + " at " + child.getPath());
                continue;
            }
            result.add(region);
        }
        result.sort(Comparator.comparingInt(ShellRegionSpec::position)
                .thenComparing(region -> region.id().value()));
        return List.copyOf(result);
    }

    private static ShellRegionSpec readRegion(FileObject folder) {
        try {
            ShellRegionId id = ShellRegionId.of(folder.getName());
            ShellRegionKind kind = ShellRegionKind.of(stringAttribute(folder, "kind", ""));
            int position = intAttribute(folder, "position", Integer.MAX_VALUE);
            Set<String> profiles = new LinkedHashSet<>();
            String profileText = stringAttribute(folder, "profiles", "").trim();
            if (!profileText.isEmpty()) {
                for (String item : profileText.split("[,\\s]+", -1)) {
                    if (!isToken(item)) {
                        throw new IllegalArgumentException("profiles must be atomic tokens");
                    }
                    profiles.add(item);
                }
            }
            String styleClass = stringAttribute(folder, "styleClass", "");
            if (!styleClass.isEmpty() && !isToken(styleClass)) {
                throw new IllegalArgumentException("styleClass must be one CSS class token");
            }
            AreaStyle style = styleClass.isEmpty()
                    ? AreaStyle.EMPTY : new AreaStyle(Set.of(styleClass), List.of(), List.of());
            Object visible = folder.getAttribute("visibleByDefault");
            if (visible != null && !(visible instanceof Boolean)) {
                throw new IllegalArgumentException("visibleByDefault must be boolean");
            }
            boolean visibleByDefault = visible == null || (Boolean) visible;
            return new ShellRegionSpec(id, kind, position, profiles, style, visibleByDefault);
        } catch (IllegalArgumentException ex) {
            LOG.warning("Cannot read shell region " + folder.getPath() + ": " + ex.getMessage());
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

    private static int intAttribute(FileObject file, String name, int fallback) {
        Object value = file.getAttribute(name);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Integer number)) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return number;
    }

    private static boolean isToken(String value) {
        return value != null && !value.isBlank()
                && value.chars().noneMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character) || character == ',');
    }
}
