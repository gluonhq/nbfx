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
package org.netbeans.api.nbfx.shell;

import java.util.Objects;

/**
 * Stable identity of a shell window.
 *
 * @param namespace the id namespace
 * @param name the id name within the namespace
 * @since 1.12
 */
public record WindowId(String namespace, String name) {

    public WindowId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        ShellIds.Parts parts = ShellIds.parse(namespace + ":" + name);
        namespace = parts.namespace();
        name = parts.name();
    }

    /** Resolves a namespaced {@code namespace:name} id. */
    public static WindowId of(String value) {
        ShellIds.Parts parts = ShellIds.parse(Objects.requireNonNull(value, "value"));
        return new WindowId(parts.namespace(), parts.name());
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
