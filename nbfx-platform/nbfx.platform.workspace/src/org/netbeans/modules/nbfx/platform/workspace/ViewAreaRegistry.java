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
package org.netbeans.modules.nbfx.platform.workspace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.view.DockLocation;
import org.netbeans.api.nbfx.view.ViewArea;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Discovers view areas from {@code NbFx/ViewAreas}, merging product declarations with the platform
 * defaults in {@link ViewArea#defaults()}. A registered area with a default id overrides the default
 * metadata; new ids add new docking destinations.
 *
 * @since 1.0
 */
public final class ViewAreaRegistry {

    private static final Logger LOG = Logger.getLogger(ViewAreaRegistry.class.getName());

    private ViewAreaRegistry() {
    }

    /** All areas, ordered by docking side, declared position and id. */
    public static List<ViewArea> discover() {
        FileObject folder = FileUtil.getConfigFile(LayerPaths.VIEW_AREAS);
        return read(folder);
    }

    /** The area with {@code id}, or a default area when no declaration overrides it. */
    public static Optional<ViewArea> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return discover().stream().filter(area -> area.id().equalsIgnoreCase(id)).findFirst();
    }

    /** The default area for a docking location, if one exists. */
    public static Optional<ViewArea> defaultFor(DockLocation location) {
        return discover().stream()
                .filter(area -> area.location() == location)
                .min(Comparator.comparingInt(area -> position(area.id())));
    }

    /** Reads the area folders below {@code folder}; package-visible for tests. */
    static List<ViewArea> read(FileObject folder) {
        Map<String, ViewArea> areas = new LinkedHashMap<>();
        for (ViewArea area : ViewArea.defaults()) {
            areas.put(area.id(), area);
        }
        Map<String, Integer> positions = new LinkedHashMap<>();
        if (folder != null) {
            for (FileObject child : folder.getChildren()) {
                if (!child.isFolder()) {
                    continue;
                }
                ViewArea area = readArea(child);
                if (area != null) {
                    areas.put(area.id(), area);
                    positions.put(area.id(), intAttribute(child, "position", Integer.MAX_VALUE));
                }
            }
        }
        List<ViewArea> result = new ArrayList<>(areas.values());
        result.sort(Comparator
                .comparingInt((ViewArea area) -> area.location().ordinal())
                .thenComparingInt(area -> positions.getOrDefault(area.id(), Integer.MAX_VALUE))
                .thenComparing(ViewArea::id));
        return List.copyOf(result);
    }

    private static ViewArea readArea(FileObject folder) {
        Object locationValue = folder.getAttribute("location");
        if (!(locationValue instanceof String name)) {
            LOG.warning("View area " + folder.getName() + " has no location");
            return null;
        }
        DockLocation location;
        try {
            location = DockLocation.valueOf(name);
        } catch (IllegalArgumentException ex) {
            LOG.warning("Unknown view area location: " + name);
            return null;
        }
        String displayName = folder.getAttribute("displayName") instanceof String value
                && !value.isBlank() ? value : folder.getName();
        double weight = folder.getAttribute("weight") instanceof Number number
                ? number.doubleValue() : 0.3d;
        if (Double.isNaN(weight) || weight < 0.0d || weight > 1.0d) {
            LOG.warning("Ignoring view area " + folder.getName()
                    + " with weight outside [0, 1]: " + weight);
            return null;
        }
        return new ViewArea(folder.getName(), displayName, location, weight);
    }

    private static int position(String id) {
        FileObject folder = FileUtil.getConfigFile(LayerPaths.VIEW_AREAS);
        FileObject area = folder == null ? null : folder.getFileObject(id);
        return intAttribute(area, "position", Integer.MAX_VALUE);
    }

    private static int intAttribute(FileObject file, String name, int fallback) {
        return file != null && file.getAttribute(name) instanceof Integer value ? value : fallback;
    }
}
