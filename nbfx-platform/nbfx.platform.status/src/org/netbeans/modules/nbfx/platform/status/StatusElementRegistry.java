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
package org.netbeans.modules.nbfx.platform.status;

import org.netbeans.api.nbfx.layer.LayerEntry;
import org.netbeans.api.nbfx.layer.LayerInstances;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.status.StatusAlignment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import org.openide.util.Lookup;

/**
 * Discovers the {@link StatusElementHolder}s registered under {@code NbFx/Status} in the module
 * layer.
 * <p>
 * The layer entry is authoritative for the id, alignment and position; the element supplies the
 * node and lifecycle. The registered attributes take precedence over the values reported by the
 * element itself, so placement is declared with {@code @StatusRegistration} rather than in code.
 *
 * @since 1.0
 */
public final class StatusElementRegistry {

    private static final Logger LOG = Logger.getLogger(StatusElementRegistry.class.getName());

    private StatusElementRegistry() {
    }

    /**
     * Discovers and orders all registered status elements. Reads the configuration filesystem, so it
     * must be called once the platform is initialized; it does not touch the JavaFX toolkit.
     *
     * @return the elements, ordered by slot, position and id
     */
    public static List<StatusElement> discover() {
        return discover(List.of(), Lookup.getDefault());
    }

    /**
     * Discovers the registered status elements with {@code services} passed to each element before
     * the status bar creates its node.
     *
     * @param services the shell lookup
     * @return the elements, ordered by slot, position and id
     */
    public static List<StatusElement> discover(Lookup services) {
        return discover(List.of(), services);
    }

    /**
     * Discovers the registered status elements and merges {@code additional} (for example elements a
     * window creates programmatically and keeps a reference to) into the ordered result.
     *
     * @param additional extra elements to include
     * @return all elements, ordered by slot, position and id
     */
    public static List<StatusElement> discover(List<StatusElement> additional) {
        return discover(additional, Lookup.getDefault());
    }

    /**
     * Discovers the registered status elements and merges {@code additional}, initializing every
     * element with {@code services}.
     *
     * @param additional extra elements to include
     * @param services   the shell lookup passed to each element
     * @return all elements, ordered by slot, position and id
     */
    public static List<StatusElement> discover(List<StatusElement> additional, Lookup services) {
        return read(LayerInstances.load(LayerPaths.STATUS, StatusElementHolder.class),
                additional, services);
    }

    /** Reads the registrations of {@code entries} and merges {@code additional}. */
    static List<StatusElement> read(Iterable<LayerEntry<StatusElementHolder>> entries,
            List<StatusElement> additional, Lookup services) {
        List<StatusElement> elements = new ArrayList<>(additional);
        for (StatusElement element : additional) {
            element.element().init(services);
        }
        for (LayerEntry<StatusElementHolder> entry : entries) {
            StatusElementHolder element = entry.instance();
            element.init(services);
            StatusAlignment alignment = parseAlignment(
                    entry.attribute("alignment"), element.alignment());
            int position = parsePosition(entry.attribute("position"), element.position());
            elements.add(new StatusElement(entry.id(), alignment, position, element));
        }
        return sort(elements);
    }

    /** Orders elements by slot, then position, then id, so the layout is stable. */
    static List<StatusElement> sort(List<StatusElement> elements) {
        List<StatusElement> sorted = new ArrayList<>(elements);
        sorted.sort(Comparator
                .comparingInt((StatusElement element) -> element.alignment().ordinal())
                .thenComparingInt(StatusElement::position)
                .thenComparing(StatusElement::id));
        return List.copyOf(sorted);
    }

    private static StatusAlignment parseAlignment(Object value, StatusAlignment fallback) {
        if (value instanceof String name) {
            try {
                return StatusAlignment.valueOf(name);
            } catch (IllegalArgumentException ex) {
                LOG.warning("Unknown status alignment: " + name);
            }
        }
        return fallback;
    }

    private static int parsePosition(Object value, int fallback) {
        return value instanceof Integer position ? position : fallback;
    }
}
