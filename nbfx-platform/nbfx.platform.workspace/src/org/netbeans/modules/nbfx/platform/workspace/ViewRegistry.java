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

import org.netbeans.api.nbfx.layer.LayerInstances;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.layer.LayerRegistration;
import org.netbeans.api.nbfx.views.DockLocation;
import org.netbeans.api.nbfx.views.ViewRegistration.PersistenceType;
import org.netbeans.api.nbfx.views.ViewArea;
import org.netbeans.api.nbfx.views.ViewKind;
import org.netbeans.api.nbfx.view.ViewProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import org.openide.util.Lookup;

/**
 * Discovers the {@link ViewProvider}s registered under {@code NbFx/Views} in the module layer.
 * <p>
 * Views are declared with {@code @ViewRegistration}, which writes an
 * {@code NbFx/Views/<id>.instance} entry carrying the display name, icon, default location,
 * position and navigator flag. The registered attributes take precedence over the values reported
 * by the view itself, so placement is declared rather than coded. The layer entry is also
 * authoritative for the id.
 * <p>
 * Discovery reads metadata only. The view itself is a {@link ViewProvider} service in the global
 * lookup, resolved by {@link #provider(String)} when its tab is created.
 *
 * @since 1.0
 */
public final class ViewRegistry {

    private static final Logger LOG = Logger.getLogger(ViewRegistry.class.getName());
    /** The fallback area for the legacy whole-window bottom location, which has no mode. */
    private static final ViewArea BOTTOM_AREA =
            new ViewArea("bottom", "Bottom", DockLocation.BOTTOM, 0.3d);

    private ViewRegistry() {
    }

    /**
     * Discovers and orders all registered views. Reads the configuration filesystem, so it must be
     * called once the platform is initialized. Does not create the views.
     *
     * @return the view registrations, ordered by location, position and id
     */
    public static List<ViewSpec> discover() {
        return read(LayerInstances.registrations(LayerPaths.VIEWS, ViewProvider.class));
    }

    /**
     * The registration of the view with {@code id}, or {@code null} when no view declares it. Reads
     * the configuration filesystem like {@link #discover()}; does not create the view.
     *
     * @param id the view id
     * @return the registration, or {@code null}
     */
    public static ViewSpec find(String id) {
        return find(LayerInstances.registrations(LayerPaths.VIEWS, ViewProvider.class), id);
    }

    /**
     * The view service with {@code id}, or {@code null} when no service is registered. Views are
     * published in the global lookup by their {@link ViewProvider#getId() id}; the layer entry only
     * supplies the metadata.
     *
     * @param id the view id
     * @return the view, or {@code null}
     */
    public static ViewProvider provider(String id) {
        for (ViewProvider view : Lookup.getDefault().lookupAll(ViewProvider.class)) {
            if (view.getId().equals(id)) {
                return view;
            }
        }
        return null;
    }

    /** Reads the registrations of {@code entries}, taking placement from each layer entry. */
    static List<ViewSpec> read(Iterable<LayerRegistration<ViewProvider>> registrations) {
        List<ViewSpec> specs = new ArrayList<>();
        for (LayerRegistration<ViewProvider> registration : registrations) {
            specs.add(read(registration));
        }
        return sort(specs);
    }

    /** Creates the first registration with {@code id}. */
    static ViewSpec find(Iterable<LayerRegistration<ViewProvider>> registrations, String id) {
        for (LayerRegistration<ViewProvider> registration : registrations) {
            if (registration.id().equals(id)) {
                return read(registration);
            }
        }
        return null;
    }

    private static ViewSpec read(LayerRegistration<ViewProvider> registration) {
        String displayName = stringAttribute(
                registration.attribute("displayName"), registration.id());
        String iconName = stringAttribute(registration.attribute("iconName"), "");
        ViewKind kind = ViewKind.of(
                stringAttribute(registration.attribute("kind"), ViewKind.TOOL_ID));
        DockLocation location = locationAttribute(
                registration.attribute("location"), DockLocation.LEFT);
        int position = intAttribute(registration.attribute("position"), Integer.MAX_VALUE);
        boolean navigator = booleanAttribute(registration.attribute("navigator"));
        ViewArea area = areaAttribute(registration.attribute("area"), location);
        boolean openAtStartup = booleanAttribute(registration.attribute("openAtStartup"));
        PersistenceType persistenceType =
                persistenceAttribute(registration.attribute("persistenceType"));
        return new ViewSpec(registration.id(), displayName, iconName, kind, position,
                navigator, area, openAtStartup, persistenceType);
    }

    /** Orders views by location, then position, then id, so the layout is stable. */
    static List<ViewSpec> sort(List<ViewSpec> registrations) {
        List<ViewSpec> sorted = new ArrayList<>(registrations);
        sorted.sort(Comparator
                .comparingInt((ViewSpec registration) -> registration.location().ordinal())
                .thenComparingInt(ViewSpec::position)
                .thenComparing(ViewSpec::id));
        return List.copyOf(sorted);
    }

    private static String stringAttribute(Object value, String fallback) {
        return value instanceof String text ? text : fallback;
    }

    private static int intAttribute(Object value, int fallback) {
        return value instanceof Integer number ? number : fallback;
    }

    private static boolean booleanAttribute(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private static DockLocation locationAttribute(Object value, DockLocation fallback) {
        if (value instanceof String name) {
            try {
                return DockLocation.valueOf(name);
            } catch (IllegalArgumentException ex) {
                LOG.warning("Unknown view location: " + name);
            }
        }
        return DockLocation.valueOf(fallback.name());
    }

    /**
     * The window area of the view: its declared {@code area} attribute resolved as an
     * {@link ViewArea}, or the area the {@code location} docks into by default. Empty areas (never
     * declared) and unknown area ids fall back to the location's default area.
     */
    static ViewArea areaAttribute(Object value, DockLocation location) {
        if (value instanceof String id && !id.isBlank()) {
            return ViewAreaRegistry.find(id).orElseGet(() -> defaultArea(location));
        }
        return defaultArea(location);
    }

    private static PersistenceType persistenceAttribute(Object value) {
        if (value instanceof String name) {
            try {
                return PersistenceType.valueOf(name);
            } catch (IllegalArgumentException ex) {
                LOG.warning("Unknown persistence type: " + name);
            }
        }
        return PersistenceType.ALWAYS;
    }

    /** The default window area a location is docked into, mirroring the Swing modes. */
    static ViewArea defaultArea(DockLocation location) {
        return switch (location) {
            case LEFT -> ViewArea.EXPLORER;
            case LEFT_BOTTOM -> ViewArea.NAVIGATOR;
            case CENTER -> ViewArea.EDITOR;
            case CENTER_BOTTOM -> ViewArea.OUTPUT;
            case BOTTOM -> BOTTOM_AREA;
            case RIGHT -> ViewArea.PROPERTIES;
        };
    }
}
