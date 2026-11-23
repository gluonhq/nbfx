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
package org.netbeans.api.nbfx.status;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerInstances;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.layer.LayerRegistration;
import org.openide.util.Lookup;

/**
 * Discovers the {@link StatusElement}s registered under {@code NbFx/Status} in the module layer.
 * <p>
 * The layer entry is authoritative for the id, alignment and position; the element supplies the
 * node and lifecycle. Discovery ({@link #discover()}) reads metadata only and never creates an
 * element. {@link #load()} instantiates and initializes the elements for one status bar; a new call
 * creates fresh elements, so each window can own and dispose its own set.
 *
 * @since 1.2
 */
public final class StatusElementRegistry {

    private static final Logger LOG = Logger.getLogger(StatusElementRegistry.class.getName());

    private StatusElementRegistry() {
    }

    /**
     * Discovers and orders all registered status elements without creating them. Reads the
     * configuration filesystem, so it must be called once the platform is initialized; it does not
     * touch the JavaFX toolkit.
     *
     * @return the registrations, ordered by slot, position and id
     */
    public static List<StatusElementSpec> discover() {
        return read(LayerInstances.registrations(LayerPaths.STATUS, StatusElement.class));
    }

    /**
     * Instantiates and orders all registered status elements for one shell, initializing each with
     * the global lookup.
     *
     * @return the elements, ordered by slot, position and id
     */
    public static List<StatusElementEntry> load() {
        return load(List.of(), Lookup.getDefault());
    }

    /**
     * Instantiates and orders all registered status elements for one shell, initializing each with
     * {@code services}.
     *
     * @param services the shell lookup passed to each element
     * @return the elements, ordered by slot, position and id
     */
    public static List<StatusElementEntry> load(Lookup services) {
        return load(List.of(), services);
    }

    /**
     * Instantiates the registered status elements and merges {@code additional} (elements a window
     * creates programmatically and keeps a reference to), initializing every element with
     * {@code services}.
     *
     * @param additional extra elements to include
     * @param services   the shell lookup passed to each element
     * @return all elements, ordered by slot, position and id
     */
    public static List<StatusElementEntry> load(List<StatusElement> additional, Lookup services) {
        List<StatusElementEntry> elements = new ArrayList<>();
        for (StatusElement element : additional) {
            element.init(services);
            elements.add(new StatusElementEntry(element.getId(), element.alignment(),
                    element.position(), element));
        }
        for (StatusElementSpec spec : discover()) {
            StatusElement element = instantiate(spec);
            if (element == null) {
                continue;
            }
            element.init(services);
            elements.add(new StatusElementEntry(spec.id(), spec.alignment(), spec.position(),
                    element));
        }
        return sortEntries(elements);
    }

    /** Reads the registrations of {@code registrations} into metadata specs. */
    static List<StatusElementSpec> read(
            Iterable<LayerRegistration<StatusElement>> registrations) {
        List<StatusElementSpec> elements = new ArrayList<>();
        for (LayerRegistration<StatusElement> registration : registrations) {
            StatusAlignment alignment = parseAlignment(
                    registration.attribute("alignment"), StatusAlignment.LEFT);
            int position = parsePosition(registration.attribute("position"), 0);
            elements.add(new StatusElementSpec(registration.id(), alignment, position,
                    registration.type()));
        }
        return sort(elements);
    }

    /** Orders elements by slot, then position, then id, so the layout is stable. */
    static List<StatusElementSpec> sort(List<StatusElementSpec> elements) {
        List<StatusElementSpec> sorted = new ArrayList<>(elements);
        sorted.sort(Comparator
                .comparingInt((StatusElementSpec element) -> element.alignment().ordinal())
                .thenComparingInt(StatusElementSpec::position)
                .thenComparing(StatusElementSpec::id));
        return List.copyOf(sorted);
    }

    private static List<StatusElementEntry> sortEntries(List<StatusElementEntry> elements) {
        List<StatusElementEntry> sorted = new ArrayList<>(elements);
        sorted.sort(Comparator
                .comparingInt((StatusElementEntry element) -> element.alignment().ordinal())
                .thenComparingInt(StatusElementEntry::position)
                .thenComparing(StatusElementEntry::id));
        return List.copyOf(sorted);
    }

    private static StatusElement instantiate(StatusElementSpec spec) {
        try {
            return spec.type().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError | SecurityException ex) {
            LOG.log(Level.WARNING, "Cannot create status element " + spec.type().getName()
                    + " for " + spec.id(), ex);
            return null;
        }
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
