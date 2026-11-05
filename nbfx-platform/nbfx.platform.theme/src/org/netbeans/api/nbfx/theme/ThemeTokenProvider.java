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
package org.netbeans.api.nbfx.theme;

import java.util.List;

/**
 * Contributes CSS token defaults. The stylesheets define the actual values on {@code .root};
 * descriptors are optional metadata for validation and tooling. A provider is usually just a tiny
 * service class returning the CSS resources of its module.
 *
 * @since 1.0
 */
public interface ThemeTokenProvider {

    /** CSS resources, relative to this provider class, that define token defaults. */
    List<String> stylesheets();

    /** Optional token descriptors. */
    default List<ThemeToken> descriptors() {
        return List.of();
    }

    /** Ordering among providers; lower values load first. */
    default int order() {
        return 0;
    }
}
