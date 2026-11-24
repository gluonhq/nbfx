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
package org.netbeans.api.nbfx.layout;

import java.util.Objects;

/**
 * A physical dock group inside a logical area.
 *
 * @param id           stable group identity
 * @param area         owning logical area
 * @param kind         semantic group behaviour
 * @param presentation rendering presentation
 * @param capacity     number of content items accepted
 * @param policyId     stable docking-policy registration id
 * @param lifetime     persistence and empty-group lifecycle
 * @param style        optional group-specific visual treatment
 * @since 1.0
 */
public record DockGroupSpec(
        DockGroupId id,
        PaneRole area,
        PaneKind kind,
        PresentationId presentation,
        ContentCapacity capacity,
        String policyId,
        GroupLifetime lifetime,
        AreaStyle style) {

    public DockGroupSpec {
        id = Objects.requireNonNull(id, "id");
        area = Objects.requireNonNull(area, "area");
        kind = Objects.requireNonNull(kind, "kind");
        presentation = Objects.requireNonNull(presentation, "presentation");
        capacity = Objects.requireNonNull(capacity, "capacity");
        policyId = Objects.requireNonNull(policyId, "policyId");
        lifetime = Objects.requireNonNull(lifetime, "lifetime");
        style = Objects.requireNonNull(style, "style");
        if (policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
    }
}
