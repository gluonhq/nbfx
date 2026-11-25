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

/**
 * Immutable product identity used by runtime UI, About, diagnostics and packaging.
 *
 * @param id stable product namespace/id
 * @param name localized user-visible product name supplied by the provider
 * @param version product version
 * @param vendor optional vendor name
 * @param license optional license name
 * @param website optional absolute website URI
 * @param copyright optional copyright notice
 * @since 1.0
 */
public record ProductIdentity(
        String id,
        String name,
        String version,
        String vendor,
        String license,
        URI website,
        String copyright) {

    public ProductIdentity {
        id = ProductValues.requiredId(id, "id");
        name = ProductValues.requiredText(name, "name");
        version = ProductValues.requiredId(version, "version");
        vendor = ProductValues.optionalText(vendor, "vendor");
        license = ProductValues.optionalText(license, "license");
        website = ProductValues.absoluteUri(website, "website");
        copyright = ProductValues.optionalText(copyright, "copyright");
    }
}
