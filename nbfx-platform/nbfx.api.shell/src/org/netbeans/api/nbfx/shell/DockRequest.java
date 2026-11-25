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
import java.util.Set;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.DockPlacement;
import org.netbeans.api.nbfx.content.TransferMode;
import org.netbeans.api.nbfx.layout.DockGroupId;

/**
 * A request to dock content into a group or area.
 *
 * @param content     the content to dock
 * @param sourceGroup the group the content currently occupies, or {@code null}
 * @param targetArea  the target pane role, or {@code null} when targeting a specific group
 * @param targetGroup the target group, or {@code null} when targeting an area
 * @param placement   where to place the content
 * @param mode        the transfer mode
 * @param modifiers   docking modifiers; never {@code null}
 * @since 1.7
 */
public record DockRequest(
        ContentId content,
        DockGroupId sourceGroup,
        PaneRole targetArea,
        DockGroupId targetGroup,
        DockPlacement placement,
        TransferMode mode,
        Set<DockModifier> modifiers) {

    public DockRequest {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(mode, "mode");
        modifiers = modifiers == null ? Set.of() : Set.copyOf(modifiers);
    }
}