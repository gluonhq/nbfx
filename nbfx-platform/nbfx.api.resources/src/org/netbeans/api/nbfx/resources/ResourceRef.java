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
package org.netbeans.api.nbfx.resources;

import java.util.Objects;

/**
 * A module-relative resource reference. The path has no leading slash; resolution uses the owning
 * module classloader at the rendering boundary.
 *
 * @param module the module code-name or owner id
 * @param path   the module-relative resource path
 * @since 1.0
 */
public record ResourceRef(String module, String path) {

    public ResourceRef {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(path, "path");
        if (module.isBlank()) {
            throw new IllegalArgumentException("module must not be blank");
        }
        if (path.isBlank() || path.startsWith("/")) {
            throw new IllegalArgumentException("path must be non-blank and module-relative");
        }
    }

    /** Creates a module-relative resource reference. */
    public static ResourceRef of(String module, String path) {
        return new ResourceRef(module, path);
    }
}
