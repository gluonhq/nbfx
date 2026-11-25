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
package org.netbeans.api.nbfx.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * Reads action references contributed to a UI surface under the {@code NbFx} layer. References retain
 * placement, presentation and context metadata; discovery never resolves or creates commands.
 *
 * @since 1.0
 */
public final class ActionLayerReader {

    private static final Logger LOG = Logger.getLogger(ActionLayerReader.class.getName());
    private static final String REF_EXT = LayerPaths.REF_SUFFIX.startsWith(".")
            ? LayerPaths.REF_SUFFIX.substring(1) : LayerPaths.REF_SUFFIX;
    private static final String HINT_PREFIX = "hint.";

    private ActionLayerReader() {
    }

    /** Reads direct references ordered by position then action id. */
    public static List<ActionRef> read(FileObject folder) {
        return read(folder, ref -> true);
    }

    /** Reads direct references that match a MIME-carrying context. */
    public static List<ActionRef> readForMime(FileObject folder, String mimeType) {
        ActionContextMetadata metadata = new ActionContextMetadata(null, mimeType, null, null);
        return readForContext(folder, Lookups.singleton(metadata));
    }

    /** Reads MIME-filtered references from a layer path. */
    public static List<ActionRef> readForMime(String layerPath, String mimeType) {
        return readForMime(layerPath == null ? null : FileUtil.getConfigFile(layerPath), mimeType);
    }

    /**
     * Reads direct references matching {@link ActionContextMetadata} in {@code context}. Different
     * filter dimensions are ANDed; profile ids are ORed. A null/blank profile means all profiles.
     */
    public static List<ActionRef> readForContext(FileObject folder, Lookup context) {
        Objects.requireNonNull(context, "context");
        ActionContextMetadata metadata = context.lookup(ActionContextMetadata.class);
        if (metadata == null) {
            metadata = new ActionContextMetadata(null, null, null, null);
        }
        ActionContextMetadata query = metadata;
        return read(folder, ref -> matches(ref.contextType(), query.contextType())
                && matches(ref.mimeType(), query.mimeType())
                && matches(ref.languageId(), query.languageId())
                && matchesProfiles(ref.profiles(), query.profileId()));
    }

    /** Reads context-filtered references from a layer path. */
    public static List<ActionRef> readForContext(String layerPath, Lookup context) {
        Objects.requireNonNull(context, "context");
        return readForContext(layerPath == null ? null : FileUtil.getConfigFile(layerPath), context);
    }

    private static List<ActionRef> read(FileObject folder, Predicate<ActionRef> filter) {
        if (folder == null) {
            return List.of();
        }
        List<ActionRef> refs = new ArrayList<>();
        for (FileObject child : folder.getChildren()) {
            if (!child.isData() || !child.hasExt(REF_EXT)) {
                continue;
            }
            ActionRef ref = readRef(child);
            if (ref != null && filter.test(ref)) {
                refs.add(ref);
            }
        }
        refs.sort(Comparator.comparingInt(ActionRef::position).thenComparing(ActionRef::actionId));
        return List.copyOf(refs);
    }

    private static ActionRef readRef(FileObject file) {
        try {
            String actionId = stringAttribute(file, "actionId", "");
            if (actionId.isBlank()) {
                throw new IllegalArgumentException("missing actionId");
            }
            int position = intAttribute(file, "position", Integer.MAX_VALUE);
            boolean separatorBefore = booleanAttribute(file, "separatorBefore", false);
            String displayName = stringAttribute(file, "displayName", "");
            String iconName = stringAttribute(file, "iconName", "");
            String contextType = stringAttribute(file, "contextType", "");
            String languageId = stringAttribute(file, "languageId", "");
            String mimeType = stringAttribute(file, "mimeType", "");
            validateFilterToken(contextType, "contextType");
            validateFilterToken(languageId, "languageId");
            validateFilterToken(mimeType, "mimeType");
            Set<String> profiles = parseProfiles(stringAttribute(file, "profiles", ""));
            Map<String, String> hints = new LinkedHashMap<>();
            for (String name : Collections.list(file.getAttributes())) {
                if (name.startsWith(HINT_PREFIX)) {
                    String key = name.substring(HINT_PREFIX.length());
                    if (key.isBlank()) {
                        throw new IllegalArgumentException("empty presentation hint key");
                    }
                    hints.put(key, stringAttribute(file, name, ""));
                }
            }
            return new ActionRef(actionId, position, separatorBefore, displayName, iconName,
                    contextType, languageId, mimeType, profiles, hints);
        } catch (IllegalArgumentException ex) {
            LOG.warning("Cannot read action reference " + file.getPath() + ": " + ex.getMessage());
            return null;
        }
    }

    private static void validateFilterToken(String value, String field) {
        if (!value.isBlank() && !ActionTokens.isToken(value)) {
            throw new IllegalArgumentException(field + " must be an atomic token");
        }
    }

    private static Set<String> parseProfiles(String value) {
        Set<String> profiles = new LinkedHashSet<>();
        String text = value.trim();
        if (!text.isEmpty()) {
            for (String item : text.split("[,\\s]+", -1)) {
                if (!ActionTokens.isToken(item)) {
                    throw new IllegalArgumentException("profiles must be atomic tokens");
                }
                profiles.add(item);
            }
        }
        return profiles;
    }

    private static boolean matches(String declared, String query) {
        return declared.isBlank() || (query != null && declared.equals(query));
    }

    private static boolean matchesProfiles(Set<String> declared, String query) {
        return declared.isEmpty() || (query != null && !query.isBlank() && declared.contains(query));
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

    private static boolean booleanAttribute(FileObject file, String name, boolean fallback) {
        Object value = file.getAttribute(name);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(name + " must be boolean");
        }
        return flag;
    }

    /** A nested menu folder and its display metadata. */
    public record Submenu(int position, boolean separatorBefore, String displayName, FileObject folder) {

        static Submenu of(FileObject folder) {
            String displayName = folder.getAttribute("displayName") instanceof String name && !name.isBlank()
                    ? name : folder.getName();
            int position = folder.getAttribute("position") instanceof Integer p ? p : Integer.MAX_VALUE;
            boolean separatorBefore = Boolean.TRUE.equals(folder.getAttribute("separatorBefore"));
            return new Submenu(position, separatorBefore, displayName, folder);
        }
    }

    /** Reads nested folders that contain action references or dynamic sections. */
    public static List<Submenu> readSubmenus(FileObject folder) {
        if (folder == null) {
            return List.of();
        }
        List<Submenu> submenus = new ArrayList<>();
        for (FileObject child : folder.getChildren()) {
            if (child.isFolder() && hasContent(child)) {
                submenus.add(Submenu.of(child));
            }
        }
        submenus.sort(Comparator.comparingInt(Submenu::position).thenComparing(Submenu::displayName));
        return List.copyOf(submenus);
    }

    private static boolean hasContent(FileObject folder) {
        String dynamicExt = LayerPaths.DYNAMIC_SUFFIX.startsWith(".")
                ? LayerPaths.DYNAMIC_SUFFIX.substring(1) : LayerPaths.DYNAMIC_SUFFIX;
        for (FileObject child : folder.getChildren()) {
            if (child.isData() && (child.hasExt(REF_EXT) || child.hasExt(dynamicExt))) {
                return true;
            }
            if (child.isFolder() && hasContent(child)) {
                return true;
            }
        }
        return false;
    }

    /** Reads references from a layer path. */
    public static List<ActionRef> read(String layerPath) {
        return read(layerPath == null ? null : FileUtil.getConfigFile(layerPath));
    }
}
