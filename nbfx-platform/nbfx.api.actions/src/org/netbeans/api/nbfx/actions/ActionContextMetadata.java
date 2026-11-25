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

/**
 * Neutral context metadata carried in a surface {@code Lookup}. Values are exact, case-sensitive
 * tokens; null/blank means the dimension is unavailable.
 *
 * @param contextType surface-defined context token
 * @param mimeType content MIME type
 * @param languageId source language id
 * @param profileId shell/profile id
 * @since 2.0
 */
public record ActionContextMetadata(
        String contextType,
        String mimeType,
        String languageId,
        String profileId) {

    public ActionContextMetadata {
        contextType = optionalToken(contextType, "contextType");
        mimeType = optionalToken(mimeType, "mimeType");
        languageId = optionalToken(languageId, "languageId");
        profileId = optionalToken(profileId, "profileId");
    }

    private static String optionalToken(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!ActionTokens.isToken(value)) {
            throw new IllegalArgumentException(field + " must be an atomic token");
        }
        return value;
    }
}
