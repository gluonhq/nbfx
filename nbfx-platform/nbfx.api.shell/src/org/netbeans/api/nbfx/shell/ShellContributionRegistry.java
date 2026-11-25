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
import java.util.Set;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerInstances;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.layer.LayerRegistration;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Stateless discovery and per-shell instantiation of shell contributions. Metadata discovery reads
 * direct {@code .instance} children of region folders without constructing classes or Nodes.
 * {@code DOCK} is runtime-owned and never accepts ordinary contributions.
 *
 * @since 1.2
 */
public final class ShellContributionRegistry {

    private static final Logger LOG = Logger.getLogger(ShellContributionRegistry.class.getName());

    private ShellContributionRegistry() {
    }

    /** All contribution metadata, ordered by region, position and registration id. */
    public static List<ShellContributionSpec> discover() {
        return read(FileUtil.getConfigFile(LayerPaths.SHELL_REGIONS));
    }

    /** Contribution metadata for one region and exact profile id. */
    public static List<ShellContributionSpec> discover(ShellRegionId region, String profileId) {
        Objects.requireNonNull(region, "region");
        return discover().stream()
                .filter(spec -> spec.region().equals(region) && matches(spec, profileId))
                .toList();
    }

    /**
     * Creates fresh contribution instances for one shell mount. No instances or Nodes are cached or
     * shared. The caller owns the returned instances and must dispose them when the mount is released.
     * Filtering happens before construction.
     */
    public static List<ShellContributionEntry> load(ShellRegionId region, String profileId) {
        Objects.requireNonNull(region, "region");
        return load(FileUtil.getConfigFile(LayerPaths.SHELL_REGIONS), region, profileId);
    }

    /** Reads contribution metadata below {@code folder}; package-visible for tests. */
    static List<ShellContributionSpec> read(FileObject folder) {
        return declarations(folder).stream().map(Declaration::spec).toList();
    }

    /** Loads fresh instances below {@code folder}; package-visible for tests. */
    static List<ShellContributionEntry> load(FileObject folder, ShellRegionId region, String profileId) {
        List<ShellContributionEntry> result = new ArrayList<>();
        for (Declaration declaration : declarations(folder)) {
            ShellContributionSpec spec = declaration.spec();
            if (!spec.region().equals(region) || !matches(spec, profileId)) {
                continue;
            }
            ShellContribution contribution = LayerInstances.instantiate(declaration.registration());
            if (contribution != null) {
                result.add(new ShellContributionEntry(spec, contribution));
            }
        }
        return List.copyOf(result);
    }

    private static List<Declaration> declarations(FileObject folder) {
        if (folder == null) {
            return List.of();
        }
        List<Declaration> result = new ArrayList<>();
        Set<ShellRegionId> seenRegions = new HashSet<>();
        FileObject[] children = folder.getChildren();
        Arrays.sort(children, Comparator.comparing(FileObject::getNameExt));
        for (FileObject child : children) {
            if (!child.isFolder()) {
                continue;
            }
            ShellRegionId region;
            try {
                region = ShellRegionId.of(child.getName());
            } catch (IllegalArgumentException ex) {
                LOG.warning("Cannot read shell region contributions " + child.getPath()
                        + ": " + ex.getMessage());
                continue;
            }
            if (!seenRegions.add(region)) {
                LOG.warning("Duplicate shell region id " + region + " at " + child.getPath());
                continue;
            }
            if (region.equals(ShellRegionId.DOCK)) {
                continue;
            }
            for (LayerRegistration<ShellContribution> registration
                    : LayerInstances.registrations(child, ShellContribution.class)) {
                ShellContributionSpec spec = readSpec(region, registration);
                if (spec != null) {
                    result.add(new Declaration(spec, registration));
                }
            }
        }
        result.sort(Comparator.comparing((Declaration declaration) -> declaration.spec().region().value())
                .thenComparingInt(declaration -> declaration.spec().position())
                .thenComparing(declaration -> declaration.spec().id()));
        return List.copyOf(result);
    }

    private static ShellContributionSpec readSpec(ShellRegionId region,
            LayerRegistration<ShellContribution> registration) {
        try {
            Object positionValue = registration.attribute("position");
            if (positionValue != null && !(positionValue instanceof Integer)) {
                throw new IllegalArgumentException("position must be an integer");
            }
            int position = positionValue == null ? Integer.MAX_VALUE : (Integer) positionValue;
            Object profilesValue = registration.attribute("profiles");
            if (profilesValue != null && !(profilesValue instanceof String)) {
                throw new IllegalArgumentException("profiles must be a string");
            }
            Set<String> profiles = new LinkedHashSet<>();
            String profileText = profilesValue == null ? "" : ((String) profilesValue).trim();
            if (!profileText.isEmpty()) {
                for (String item : profileText.split("[,\\s]+", -1)) {
                    if (!ShellTokens.isToken(item)) {
                        throw new IllegalArgumentException("profiles must be atomic tokens");
                    }
                    profiles.add(item);
                }
            }
            return new ShellContributionSpec(registration.id(), region, position, profiles,
                    registration.type());
        } catch (IllegalArgumentException ex) {
            LOG.warning("Cannot read shell contribution " + registration.file().getPath()
                    + ": " + ex.getMessage());
            return null;
        }
    }

    private static boolean matches(ShellContributionSpec spec, String profileId) {
        return profileId == null || profileId.isBlank()
                || spec.profiles().isEmpty() || spec.profiles().contains(profileId);
    }

    private record Declaration(ShellContributionSpec spec,
            LayerRegistration<ShellContribution> registration) {
    }
}
