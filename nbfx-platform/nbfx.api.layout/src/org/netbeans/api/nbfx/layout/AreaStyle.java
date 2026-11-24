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
package org.netbeans.api.nbfx.layout;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.netbeans.api.nbfx.resources.ResourceRef;

/**
 * Scoped visual treatment for a layout area or dock group.
 *
 * @param styleClasses  CSS style classes applied to the host wrapper
 * @param stylesheets   module-owned stylesheets applied to the wrapper subtree
 * @param themeOverlays optional scoped theme resources
 * @since 1.0
 */
public record AreaStyle(Set<String> styleClasses, List<ResourceRef> stylesheets,
        List<ResourceRef> themeOverlays) {

    public static final AreaStyle EMPTY = new AreaStyle(Set.of(), List.of(), List.of());

    public AreaStyle {
        styleClasses = Set.copyOf(Objects.requireNonNull(styleClasses, "styleClasses"));
        stylesheets = List.copyOf(Objects.requireNonNull(stylesheets, "stylesheets"));
        themeOverlays = List.copyOf(Objects.requireNonNull(themeOverlays, "themeOverlays"));
    }
}
