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
package org.netbeans.api.nbfx.nodes;

import java.util.List;
import java.util.Objects;

/**
 * A simple {@link PropertySetModel} holding an immutable list of properties.
 *
 * @since 1.0
 */
public final class SimplePropertySetModel implements PropertySetModel {

    private final String name;
    private final String displayName;
    private final List<PropertyModel> properties;

    public SimplePropertySetModel(String name, String displayName, List<PropertyModel> properties) {
        this.name = Objects.requireNonNull(name, "name");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.properties = List.copyOf(properties);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public List<PropertyModel> getProperties() {
        return properties;
    }
}
