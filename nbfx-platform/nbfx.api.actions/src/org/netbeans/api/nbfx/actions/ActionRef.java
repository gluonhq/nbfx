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
package org.netbeans.api.nbfx.actions;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable reference to an action in a UI surface, as read from the layer.
 *
 * @param actionId the referenced action id
 * @param position order among siblings
 * @param separatorBefore whether a separator precedes the entry
 * @param displayName optional localized display override
 * @param iconName optional icon resource override
 * @param contextType optional exact context token
 * @param languageId optional exact language id
 * @param mimeType optional exact MIME type
 * @param profiles exact profile ids; empty means all profiles
 * @param presentationHints immutable presentation hints
 * @since 1.0
 */
public record ActionRef(
        String actionId,
        int position,
        boolean separatorBefore,
        String displayName,
        String iconName,
        String contextType,
        String languageId,
        String mimeType,
        Set<String> profiles,
        Map<String, String> presentationHints) {

    public ActionRef(String actionId, int position, boolean separatorBefore) {
        this(actionId, position, separatorBefore, "", "", "", "", "", Set.of(), Map.of());
    }

    public ActionRef {
        actionId = Objects.requireNonNull(actionId, "actionId");
        if (actionId.isBlank()) {
            throw new IllegalArgumentException("actionId must not be blank");
        }
        profiles = Set.copyOf(Objects.requireNonNull(profiles, "profiles"));
        for (String profile : profiles) {
            if (!ActionTokens.isToken(profile)) {
                throw new IllegalArgumentException("profile ids must be atomic tokens");
            }
        }
        presentationHints = Map.copyOf(Objects.requireNonNull(presentationHints, "presentationHints"));
    }
}
