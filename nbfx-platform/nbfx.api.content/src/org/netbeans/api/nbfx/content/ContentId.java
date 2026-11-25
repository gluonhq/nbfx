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

import java.util.Objects;

/**
 * Stable identity of a piece of content. Identities use the one namespaced string form
 * ({@code namespace:name}) shared by the other nbfx typed ids; the namespace is usually the
 * product or feature that owns the identity.
 *
 * @param namespace the namespace, for example {@code nbfx}
 * @param name      the name within the namespace
 * @since 1.0
 */
public record ContentId(String namespace, String name) {

    public ContentId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        ContentIds.Parts parts = ContentIds.parse(namespace + ":" + name);
        namespace = parts.namespace();
        name = parts.name();
    }

    /** Resolves a namespaced {@code namespace:name} id. */
    public static ContentId of(String value) {
        Objects.requireNonNull(value, "value");
        ContentIds.Parts parts = ContentIds.parse(value);
        return new ContentId(parts.namespace(), parts.name());
    }

    /** The canonical {@code namespace:name} identity. */
    public String value() {
        return namespace + ":" + name;
    }

    @Override
    public String toString() {
        return value();
    }
}