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
package org.netbeans.modules.nbfx.platform.options;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.netbeans.api.nbfx.layer.LayerEntry;
import org.netbeans.api.nbfx.layer.LayerInstances;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.options.OptionsPanelController;

/**
 * Discovers the {@link OptionsPanelController}s registered under {@code NbFx/Options} in the module
 * layer. The layer entry is authoritative for the id, display name and position; the controller
 * supplies the component and lifecycle.
 *
 * @since 1.0
 */
final class OptionsRegistry {

    private OptionsRegistry() {
    }

    /**
     * Discovers and orders all registered options panels. Reads the configuration filesystem, so it
     * must be called once the platform is initialized.
     *
     * @return the panel registrations, ordered by position then id
     */
    static List<OptionsPanelEntry> discover() {
        return read(LayerInstances.load(LayerPaths.OPTIONS, OptionsPanelController.class));
    }

    /** Reads the registrations of {@code entries}, taking metadata from each layer entry. */
    static List<OptionsPanelEntry> read(Iterable<LayerEntry<OptionsPanelController>> entries) {
        List<OptionsPanelEntry> registrations = new ArrayList<>();
        for (LayerEntry<OptionsPanelController> entry : entries) {
            registrations.add(new OptionsPanelEntry(
                    entry.id(),
                    stringAttribute(entry.attribute("displayName"), entry.id()),
                    intAttribute(entry.attribute("position"), Integer.MAX_VALUE),
                    entry.instance()));
        }
        return sort(registrations);
    }

    /** Orders panels by position, then id. */
    static List<OptionsPanelEntry> sort(List<OptionsPanelEntry> registrations) {
        List<OptionsPanelEntry> sorted = new ArrayList<>(registrations);
        sorted.sort(Comparator
                .comparingInt(OptionsPanelEntry::position)
                .thenComparing(OptionsPanelEntry::id));
        return List.copyOf(sorted);
    }

    private static String stringAttribute(Object value, String fallback) {
        return value instanceof String text ? text : fallback;
    }

    private static int intAttribute(Object value, int fallback) {
        return value instanceof Integer number ? number : fallback;
    }
}
