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
package org.netbeans.api.nbfx.wizard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.netbeans.api.nbfx.layer.LayerInstances;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.layer.LayerRegistration;

/**
 * Discovers the {@link Wizard}s registered under {@code NbFx/Wizards} in the module layer.
 * <p>
 * Discovery reads only the layer metadata; it does not create the wizards.
 * {@link #find(String)} creates a fresh instance for every open, so a wizard does not keep input
 * from a previous open.
 *
 * @since 1.1
 */
public final class WizardRegistry {

    private WizardRegistry() {
    }

    /**
     * Discovers and orders all registered wizards without creating them. Reads the configuration
     * filesystem, so it must be called once the platform is initialized.
     *
     * @return the wizard registrations, ordered by category, position and id
     */
    public static List<WizardEntry> discover() {
        return read(LayerInstances.registrations(LayerPaths.WIZARDS, Wizard.class));
    }

    /**
     * Creates the registered wizard with {@code id}, or returns {@code null} if none.
     * <p>
     * Every call constructs a new wizard instance, so a wizard does not keep state from a previous
     * open. Call this on the JavaFX Application Thread when the wizard builds controls in its
     * constructor or in {@link Wizard#getPages()}.
     *
     * @param id the registration id
     * @return a fresh wizard, or {@code null}
     */
    public static Wizard find(String id) {
        Objects.requireNonNull(id, "id");
        return find(LayerInstances.registrations(LayerPaths.WIZARDS, Wizard.class), id);
    }

    /** Reads the registrations of {@code entries}, taking metadata from each layer entry. */
    static List<WizardEntry> read(Iterable<LayerRegistration<Wizard>> registrations) {
        List<WizardEntry> entries = new ArrayList<>();
        for (LayerRegistration<Wizard> registration : registrations) {
            entries.add(new WizardEntry(
                    registration.id(),
                    stringAttribute(registration.attribute("displayName"), registration.id()),
                    stringAttribute(registration.attribute("iconName"), ""),
                    stringAttribute(registration.attribute("category"), ""),
                    intAttribute(registration.attribute("position"), Integer.MAX_VALUE)));
        }
        return sort(entries);
    }

    /** Creates the first registration with {@code id}. */
    static Wizard find(Iterable<LayerRegistration<Wizard>> registrations, String id) {
        for (LayerRegistration<Wizard> registration : registrations) {
            if (registration.id().equals(id)) {
                return LayerInstances.instantiate(registration);
            }
        }
        return null;
    }

    /** Orders wizards by category, then position, then id. */
    static List<WizardEntry> sort(List<WizardEntry> registrations) {
        List<WizardEntry> sorted = new ArrayList<>(registrations);
        sorted.sort(Comparator
                .comparing(WizardEntry::category)
                .thenComparingInt(WizardEntry::position)
                .thenComparing(WizardEntry::id));
        return List.copyOf(sorted);
    }

    private static String stringAttribute(Object value, String fallback) {
        return value instanceof String text ? text : fallback;
    }

    private static int intAttribute(Object value, int fallback) {
        return value instanceof Integer number ? number : fallback;
    }
}
