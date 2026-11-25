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
package org.netbeans.api.nbfx.product;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** Shared validation for immutable product metadata. */
final class ProductValues {

    private ProductValues() {
    }

    static String requiredText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    static String requiredId(String value, String field) {
        String text = requiredText(value, field);
        if (!text.equals(text.trim())) {
            throw new IllegalArgumentException(field + " must not have surrounding whitespace");
        }
        return text;
    }

    static String optionalText(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    static URI absoluteUri(URI value, String field) {
        if (value != null && !value.isAbsolute()) {
            throw new IllegalArgumentException(field + " must be an absolute URI");
        }
        return value;
    }

    static <T> List<T> copyList(List<T> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field));
    }
}
