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
package org.netbeans.modules.nbfx.platform.shell.api.shutdown;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The choices made in the shutdown confirmation. The maps keep their insertion order. */
public record ShutdownSelection(Map<String, String> choices, Map<String, String> options) {

    public ShutdownSelection {
        choices = choices == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(choices));
        options = options == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    public String choice(String itemId, String fallback) {
        return choices.getOrDefault(itemId, fallback);
    }

    public String option(String optionId, String fallback) {
        return options.getOrDefault(optionId, fallback);
    }

    /** The safe default plan for a shutdown that bypasses the confirmation dialog. */
    public static ShutdownSelection defaults(ShutdownImpact impact) {
        Map<String, String> choices = new LinkedHashMap<>();
        for (ShutdownImpact.Item item : impact.items()) {
            if (!item.choices().isEmpty()) {
                choices.put(item.id(), item.defaultChoice());
            }
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (ShutdownImpact.Option option : impact.options()) {
            options.put(option.id(), option.defaultValue());
        }
        return new ShutdownSelection(choices, options);
    }
}
