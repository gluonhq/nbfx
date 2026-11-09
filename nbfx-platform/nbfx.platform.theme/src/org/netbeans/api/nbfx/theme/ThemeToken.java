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
package org.netbeans.api.nbfx.theme;

import java.util.Objects;

/**
 * A token declared by a component. The token's default value lives in the component's CSS, not in
 * this descriptor; the descriptor is for validation and tooling.
 *
 * @param id          the CSS property name, for example {@code -nbfx-timeline-playhead}
 * @param type        the value type
 * @param description a short description, or an empty string
 * @param owner       the declaring module/component, or an empty string
 */
public record ThemeToken(String id, ThemeTokenType type, String description, String owner) {

    public ThemeToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        description = description == null ? "" : description;
        owner = owner == null ? "" : owner;
    }
}
