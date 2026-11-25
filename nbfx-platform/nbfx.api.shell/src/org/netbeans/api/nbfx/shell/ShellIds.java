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

/** Shared parsing for namespaced shell-region ids. */
final class ShellIds {

    private ShellIds() {
    }

    static Parts parse(String value) {
        Objects.requireNonNull(value, "value");
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        int colon = text.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("product ids require namespace:name: " + text);
        }
        String namespace = text.substring(0, colon);
        String name = text.substring(colon + 1);
        requirePart(namespace, "namespace");
        requirePart(name, "name");
        return new Parts(namespace, name);
    }

    private static void requirePart(String value, String label) {
        if (value.isEmpty() || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("invalid " + label + ": " + value);
        }
    }

    record Parts(String namespace, String name) {
        String value() {
            return namespace + ":" + name;
        }
    }
}
