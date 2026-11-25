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
package org.netbeans.api.nbfx.layout;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Discovers declarative product layouts from {@code NbFx/Layouts}, logical areas from
 * {@code NbFx/LayoutAreas} and physical groups from {@code NbFx/DockGroups}. Malformed entries are
 * skipped and reported to the log; they do not prevent other declarations from loading.
 *
 * @since 1.0
 */
public final class LayoutRegistry {

    private static final Logger LOG = Logger.getLogger(LayoutRegistry.class.getName());

    private LayoutRegistry() {
    }

    /** All registered layouts, ordered by stable id. */
    public static List<LayoutSpec> discover() {
        return read(FileUtil.getConfigFile(LayerPaths.LAYOUTS));
    }

    /** The layout with {@code id}, if it is registered and valid. */
    public static Optional<LayoutSpec> find(String id) {
        if (id == null || id.isBlank() || id.indexOf('/') >= 0 || id.indexOf('\\') >= 0) {
            return Optional.empty();
        }
        FileObject folder = FileUtil.getConfigFile(LayerPaths.LAYOUTS);
        return readFile(folder == null ? null : folder.getFileObject(id, "xml"));
    }

    /** All registered logical areas, ordered by area order and canonical role id. */
    public static List<ViewArea> areas() {
        return readAreas(FileUtil.getConfigFile(LayerPaths.LAYOUT_AREAS));
    }

    /** The logical area with {@code id}, if it is registered and valid. */
    public static Optional<ViewArea> findArea(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            PaneRole role = PaneRole.of(id);
            return areas().stream().filter(area -> area.id().equals(role)).findFirst();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** All registered physical dock groups, ordered by canonical group id. */
    public static List<DockGroupSpec> groups() {
        Map<PaneRole, ViewArea> areas = new HashMap<>();
        for (ViewArea area : areas()) {
            areas.put(area.id(), area);
        }
        return readGroups(FileUtil.getConfigFile(LayerPaths.DOCK_GROUPS), areas);
    }

    /** The physical dock group with {@code id}, if it is registered and valid. */
    public static Optional<DockGroupSpec> findGroup(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            DockGroupId groupId = DockGroupId.of(id);
            return groups().stream().filter(group -> group.id().equals(groupId)).findFirst();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** Reads layout XML files below {@code folder}; package-visible for tests. */
    static List<LayoutSpec> read(FileObject folder) {
        if (folder == null) {
            return List.of();
        }
        List<LayoutSpec> result = new ArrayList<>();
        for (FileObject file : folder.getChildren()) {
            if (file.isData() && "xml".equals(file.getExt())) {
                readFile(file).ifPresent(result::add);
            }
        }
        result.sort(Comparator.comparing(LayoutSpec::id));
        return List.copyOf(result);
    }

    /** Reads area folders below {@code folder}; package-visible for tests. */
    static List<ViewArea> readAreas(FileObject folder) {
        if (folder == null) {
            return List.of();
        }
        List<ViewArea> result = new ArrayList<>();
        for (FileObject file : folder.getChildren()) {
            if (file.isFolder()) {
                ViewArea area = readArea(file);
                if (area != null) {
                    result.add(area);
                }
            }
        }
        result.sort(Comparator.comparingInt(ViewArea::order)
                .thenComparing(area -> area.id().value()));
        return List.copyOf(result);
    }

    private static ViewArea readArea(FileObject folder) {
        try {
            PaneRole id = PaneRole.of(folder.getName());
            Object displayName = folder.getAttribute("displayName");
            if (!(displayName instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("missing displayName");
            }
            ViewAreaKind kind = ViewAreaKind.of(stringAttribute(folder, "kind", "MAIN"));
            DockLocation location = DockLocation.valueOf(
                    stringAttribute(folder, "location", "CENTER").trim().toUpperCase(Locale.ROOT));
            double weight = doubleAttribute(folder, "weight", 1.0d);
            PresentationId presentation = PresentationId.of(
                    stringAttribute(folder, "presentation", "tabbed"));
            Set<ViewAreaCapability> capabilities = new LinkedHashSet<>();
            String capabilityText = stringAttribute(folder, "capabilities", "").trim();
            if (!capabilityText.isEmpty()) {
                for (String item : capabilityText.split("[,\\s]+")) {
                    capabilities.add(ViewAreaCapability.valueOf(item.trim().toUpperCase(Locale.ROOT)));
                }
            }
            String policyId = stringAttribute(folder, "policyId", "default");
            ViewAreaVisibility visibility = ViewAreaVisibility.valueOf(
                    stringAttribute(folder, "visibility", "STARTUP").trim().toUpperCase(Locale.ROOT));
            EmptyAreaPolicy emptyPolicy = EmptyAreaPolicy.valueOf(
                    stringAttribute(folder, "emptyPolicy", "KEEP").trim().toUpperCase(Locale.ROOT));
            int order = intAttribute(folder, "order", 0);
            return new ViewArea(id, kind, name, location, weight, presentation, capabilities,
                    policyId, AreaStyle.EMPTY, visibility, emptyPolicy, order);
        } catch (IllegalArgumentException ex) {
            LOG.warning("Cannot read layout area " + folder.getPath() + ": " + ex.getMessage());
            return null;
        }
    }

    /** Reads group files below {@code folder}; package-visible for tests. */
    static List<DockGroupSpec> readGroups(FileObject folder, Map<PaneRole, ViewArea> areas) {
        if (folder == null) {
            return List.of();
        }
        List<DockGroupSpec> result = new ArrayList<>();
        for (FileObject file : folder.getChildren()) {
            if (file.isData()) {
                DockGroupSpec group = readGroup(file, areas);
                if (group != null) {
                    result.add(group);
                }
            }
        }
        result.sort(Comparator.comparing(group -> group.id().value()));
        return List.copyOf(result);
    }

    private static DockGroupSpec readGroup(FileObject file, Map<PaneRole, ViewArea> areas) {
        try {
            DockGroupId id = DockGroupId.of(file.getNameExt());
            String areaText = stringAttribute(file, "area", "").trim();
            if (areaText.isEmpty()) {
                throw new IllegalArgumentException("missing area");
            }
            PaneRole area = PaneRole.of(areaText);
            ViewArea areaSpec = areas.get(area);
            if (areaSpec == null) {
                throw new IllegalArgumentException("unknown area " + area);
            }
            PaneKind kind = PaneKind.valueOf(
                    stringAttribute(file, "kind", "VIEW").trim().toUpperCase(Locale.ROOT));
            String presentationText = stringAttribute(file, "presentation", "").trim();
            PresentationId presentation = presentationText.isEmpty()
                    ? areaSpec.defaultPresentation() : PresentationId.of(presentationText);
            ContentCapacity capacity = ContentCapacity.valueOf(
                    stringAttribute(file, "capacity", "MANY").trim().toUpperCase(Locale.ROOT));
            String policyText = stringAttribute(file, "policyId", "").trim();
            String policyId = policyText.isEmpty() ? areaSpec.policyId() : policyText;
            GroupLifetime lifetime = GroupLifetime.valueOf(
                    stringAttribute(file, "lifetime", "PERSISTENT").trim().toUpperCase(Locale.ROOT));
            return new DockGroupSpec(id, area, kind, presentation, capacity, policyId, lifetime,
                    AreaStyle.EMPTY);
        } catch (IllegalArgumentException ex) {
            LOG.warning("Cannot read dock group " + file.getPath() + ": " + ex.getMessage());
            return null;
        }
    }

    /** Reads one layout XML file; package-visible for tests. */
    static Optional<LayoutSpec> readFile(FileObject file) {
        if (file == null || !file.isData() || !"xml".equals(file.getExt())) {
            return Optional.empty();
        }
        try (InputStream input = file.getInputStream()) {
            LayoutSpec layout = LayoutXml.read(input);
            if (!file.getName().equals(layout.id())) {
                LOG.warning("Layout file " + file.getPath() + " declares id " + layout.id());
                return Optional.empty();
            }
            return Optional.of(layout);
        } catch (IOException | IllegalArgumentException ex) {
            LOG.warning("Cannot read layout " + file.getPath() + ": " + ex.getMessage());
            return Optional.empty();
        }
    }

    private static String stringAttribute(FileObject file, String name, String fallback) {
        Object value = file.getAttribute(name);
        return value instanceof String text ? text : fallback;
    }

    private static int intAttribute(FileObject file, String name, int fallback) {
        Object value = file.getAttribute(name);
        return value instanceof Integer number ? number : fallback;
    }

    private static double doubleAttribute(FileObject file, String name, double fallback) {
        Object value = file.getAttribute(name);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
