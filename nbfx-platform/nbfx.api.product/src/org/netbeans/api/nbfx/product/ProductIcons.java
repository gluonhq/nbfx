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
import org.netbeans.api.nbfx.resources.ResourceRef;

/**
 * Immutable product icon references. List order defines precedence.
 *
 * @param windowIcons window icon candidates in precedence order
 * @param aboutLogo optional About logo
 * @param installerIcons installer icon candidates in precedence order
 * @since 1.0
 */
public record ProductIcons(
        List<ResourceRef> windowIcons,
        ResourceRef aboutLogo,
        List<ResourceRef> installerIcons) {

    public ProductIcons {
        windowIcons = ProductValues.copyList(windowIcons, "windowIcons");
        installerIcons = ProductValues.copyList(installerIcons, "installerIcons");
    }
}
