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
import java.util.Set;

/**
 * A logical layout area containing zero or more physical dock groups. The area defines policy and
 * defaults; the layout tree defines geometry.
 *
 * @param id                   stable area identity
 * @param kind                 neutral or product-defined area kind
 * @param displayName          localized display name or bundle key
 * @param defaultLocation      routing default used when no explicit placement is supplied
 * @param weight               routing weight used when no explicit divider is supplied
 * @param defaultPresentation  presentation inherited by groups unless overridden
 * @param capabilities         operations supported by the area
 * @param policyId             stable docking-policy registration id
 * @param style                scoped visual treatment
 * @param visibility           initial visibility policy
 * @param emptyPolicy          behavior when the area has no content
 * @param order                order among sibling areas
 * @since 1.0
 */
public record ViewArea(
        PaneRole id,
        ViewAreaKind kind,
        String displayName,
        DockLocation defaultLocation,
        double weight,
        PresentationId defaultPresentation,
        Set<ViewAreaCapability> capabilities,
        String policyId,
        AreaStyle style,
        ViewAreaVisibility visibility,
        EmptyAreaPolicy emptyPolicy,
        int order) {

    public ViewArea {
        id = Objects.requireNonNull(id, "id");
        kind = Objects.requireNonNull(kind, "kind");
        displayName = Objects.requireNonNull(displayName, "displayName");
        defaultLocation = Objects.requireNonNull(defaultLocation, "defaultLocation");
        defaultPresentation = Objects.requireNonNull(defaultPresentation, "defaultPresentation");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        policyId = Objects.requireNonNull(policyId, "policyId");
        style = Objects.requireNonNull(style, "style");
        visibility = Objects.requireNonNull(visibility, "visibility");
        emptyPolicy = Objects.requireNonNull(emptyPolicy, "emptyPolicy");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (Double.isNaN(weight) || weight < 0.0d || weight > 1.0d) {
            throw new IllegalArgumentException("weight must be between 0 and 1");
        }
        if (policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
    }
}
