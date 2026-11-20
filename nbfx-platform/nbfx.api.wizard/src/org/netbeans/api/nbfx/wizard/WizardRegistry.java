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
import org.netbeans.api.nbfx.layer.LayerEntry;
import org.netbeans.api.nbfx.layer.LayerInstances;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.FileObject;

/**
 * Discovers the {@link Wizard}s registered under {@code NbFx/Wizards} in the module layer.
 *
 * @since 1.1
 */
public final class WizardRegistry {

    private WizardRegistry() {
    }

    /**
     * Discovers and orders all registered wizards. Reads the configuration filesystem, so it must be
     * called once the platform is initialized.
     *
     * @return the wizard registrations, ordered by category, position and id
     */
    public static List<WizardEntry> discover() {
        return read(LayerInstances.load(LayerPaths.WIZARDS, Wizard.class));
    }

    /** The registered wizard with {@code id}, or {@code null} if none. */
    public static Wizard find(String id) {
        return discover().stream()
                .filter(registration -> registration.id().equals(id))
                .map(WizardEntry::wizard)
                .findFirst()
                .orElse(null);
    }

    /** Reads the registrations of {@code entries}, taking metadata from each layer entry. */
    static List<WizardEntry> read(Iterable<LayerEntry<Wizard>> entries) {
        List<WizardEntry> registrations = new ArrayList<>();
        for (LayerEntry<Wizard> entry : entries) {
            Wizard wizard = entry.instance();
            String id = entry.id();
            FileObject file = entry.file();
            String displayName = stringAttribute(file, "displayName", wizard.getDisplayName());
            String iconName = stringAttribute(file, "iconName", "");
            String category = stringAttribute(file, "category", "");
            int position = intAttribute(file, "position", Integer.MAX_VALUE);
            registrations.add(new WizardEntry(id, displayName, iconName, category, position, wizard));
        }
        return sort(registrations);
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

    private static String stringAttribute(FileObject file, String name, String fallback) {
        Object value = file == null ? null : file.getAttribute(name);
        return value instanceof String text ? text : fallback;
    }

    private static int intAttribute(FileObject file, String name, int fallback) {
        Object value = file == null ? null : file.getAttribute(name);
        return value instanceof Integer number ? number : fallback;
    }
}
