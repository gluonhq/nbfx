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
package org.netbeans.api.nbfx.content;

import java.util.Map;
import java.util.Objects;

/**
 * The state needed to recreate one piece of content. The identity and the instance key select the
 * content; the opaque payload carries whatever the owning factory needs to restore it. A store keys
 * payloads by identity and instance key and never interprets the payload.
 *
 * @param id          the content identity
 * @param instanceKey the stable per-identity instance key, so two documents of one kind stay distinct
 * @param payload     the opaque, unmodifiable per-content payload
 * @since 1.0
 */
public record ContentState(ContentId id, String instanceKey, Map<String, String> payload) {

    public ContentState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(instanceKey, "instanceKey");
        if (instanceKey.isBlank()) {
            throw new IllegalArgumentException("instanceKey must not be blank");
        }
        payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
    }

    /** An empty payload for {@code id} and {@code instanceKey}. */
    public static ContentState empty(ContentId id, String instanceKey) {
        return new ContentState(id, instanceKey, Map.of());
    }
}