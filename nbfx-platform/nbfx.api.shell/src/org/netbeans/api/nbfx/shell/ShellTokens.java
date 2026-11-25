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

/** Shared validation for atomic shell metadata tokens. */
final class ShellTokens {

    private ShellTokens() {
    }

    static boolean isToken(String value) {
        return value != null && !value.isBlank()
                && value.chars().noneMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character) || character == ',');
    }

    static String require(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!isToken(value)) {
            throw new IllegalArgumentException(field + " must be an atomic token");
        }
        return value;
    }
}
