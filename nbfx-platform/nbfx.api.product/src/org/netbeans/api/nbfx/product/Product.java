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

import java.util.List;
import java.util.Objects;

/**
 * Immutable product metadata and default composition ids. Policy ids are resolved by their owning
 * APIs; this record contains no live shell, layout, theme or content object.
 *
 * @param identity product identity
 * @param icons product icon references
 * @param style product visual resources
 * @param themePolicyId stable default theme-policy id
 * @param layoutId stable default layout id
 * @param shellId stable default shell id
 * @param chromeProfilePolicyId stable default chrome-profile-policy id
 * @param associations ordered desktop associations
 * @param about About metadata
 * @since 1.0
 */
public record Product(
        ProductIdentity identity,
        ProductIcons icons,
        ProductStyle style,
        String themePolicyId,
        String layoutId,
        String shellId,
        String chromeProfilePolicyId,
        List<DesktopAssociation> associations,
        AboutInfo about) {

    public Product {
        identity = Objects.requireNonNull(identity, "identity");
        icons = Objects.requireNonNull(icons, "icons");
        style = Objects.requireNonNull(style, "style");
        themePolicyId = ProductValues.requiredId(themePolicyId, "themePolicyId");
        layoutId = ProductValues.requiredId(layoutId, "layoutId");
        shellId = ProductValues.requiredId(shellId, "shellId");
        chromeProfilePolicyId = ProductValues.requiredId(
                chromeProfilePolicyId, "chromeProfilePolicyId");
        associations = ProductValues.copyList(associations, "associations");
        about = Objects.requireNonNull(about, "about");
    }
}
