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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The resources and state that a shutdown would affect. The component lists and the summary keep
 * their insertion order, so the confirmation dialog is deterministic.
 */
public record ShutdownImpact(List<Item> items, List<Option> options,
        Map<String, String> summary) {

    public ShutdownImpact {
        items = items == null ? List.of() : List.copyOf(items);
        options = options == null ? List.of() : List.copyOf(options);
        summary = summary == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(summary));
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public enum Severity {
        INFO, IMPACT, WARNING,
        /** The item can discard data. */
        DATA_LOSS
    }

    public enum Type {
        TEXT, DIRECTORY, BOOLEAN
    }

    /**
     * One selectable consequence of an item.
     *
     * @param dataLoss whether choosing this option discards data
     */
    public record Choice(String id, String label, String consequence, boolean dataLoss) {
        public Choice {
            id = id == null ? "" : id;
            label = label == null ? "" : label;
            consequence = consequence == null ? "" : consequence;
        }
    }

    /**
     * One affected resource or state. An item without a category is grouped under the dialog's
     * localized "Other" heading.
     */
    public record Item(String id, String category, String title, String detail,
            Severity severity, List<Choice> choices, String defaultChoice) {

        public Item {
            id = id == null ? "" : id;
            category = category == null ? "" : category;
            title = title == null ? "" : title;
            detail = detail == null ? "" : detail;
            severity = severity == null ? Severity.INFO : severity;
            choices = choices == null ? List.of() : List.copyOf(choices);
            if (defaultChoice == null && !choices.isEmpty()) {
                defaultChoice = choices.get(0).id();
            }
        }
    }

    public record Option(String id, String label, Type type, String defaultValue) {
        public Option {
            id = id == null ? "" : id;
            label = label == null ? "" : label;
            type = type == null ? Type.TEXT : type;
            defaultValue = defaultValue == null ? "" : defaultValue;
        }
    }

    public static final class Builder {

        private final List<Item> items = new ArrayList<>();
        private final Map<String, Option> options = new LinkedHashMap<>();
        private final Map<String, String> summary = new LinkedHashMap<>();

        public Builder add(Item item) {
            if (item != null) {
                items.add(item);
            }
            return this;
        }

        public Builder option(Option option) {
            if (option != null) {
                options.put(option.id(), option);
            }
            return this;
        }

        public Builder summary(String key, String value) {
            if (key != null && value != null) {
                summary.put(key, value);
            }
            return this;
        }

        public ShutdownImpact build() {
            return new ShutdownImpact(items, List.copyOf(options.values()), summary);
        }
    }
}
