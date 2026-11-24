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

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable, content-free product layout. Explicit tree geometry wins over area routing defaults.
 *
 * @param id            stable layout id
 * @param formatVersion persistence schema version
 * @param areas         logical areas by stable role
 * @param groups        physical groups by stable id
 * @param root          layout tree
 * @param metadata      opaque product metadata
 * @since 1.0
 */
public record LayoutSpec(
        String id,
        int formatVersion,
        Map<PaneRole, ViewArea> areas,
        Map<DockGroupId, DockGroupSpec> groups,
        LayoutNode root,
        Map<String, String> metadata) {

    public LayoutSpec {
        id = Objects.requireNonNull(id, "id");
        areas = Map.copyOf(Objects.requireNonNull(areas, "areas"));
        groups = Map.copyOf(Objects.requireNonNull(groups, "groups"));
        root = Objects.requireNonNull(root, "root");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (formatVersion < 1) {
            throw new IllegalArgumentException("formatVersion must be positive");
        }
        validateReferences(areas, groups, root);
    }

    private static void validateReferences(Map<PaneRole, ViewArea> areas,
            Map<DockGroupId, DockGroupSpec> groups, LayoutNode root) {
        Set<DockGroupId> referenced = new HashSet<>();
        collectGroups(root, referenced);
        for (Map.Entry<PaneRole, ViewArea> areaEntry : areas.entrySet()) {
            if (!areaEntry.getKey().equals(areaEntry.getValue().id())) {
                throw new IllegalArgumentException("area map key does not match area id");
            }
        }
        for (Map.Entry<DockGroupId, DockGroupSpec> groupEntry : groups.entrySet()) {
            if (!groupEntry.getKey().equals(groupEntry.getValue().id())) {
                throw new IllegalArgumentException("group map key does not match group id");
            }
        }
        for (DockGroupId groupId : referenced) {
            if (!groups.containsKey(groupId)) {
                throw new IllegalArgumentException("layout refers to missing group " + groupId);
            }
        }
        for (DockGroupSpec group : groups.values()) {
            if (!areas.containsKey(group.area())) {
                throw new IllegalArgumentException("group " + group.id()
                        + " refers to missing area " + group.area());
            }
            if (!referenced.contains(group.id()) && group.lifetime() != GroupLifetime.TRANSIENT) {
                throw new IllegalArgumentException("unreferenced persistent group " + group.id());
            }
        }
        validateSlots(areas, root);
    }

    private static void collectGroups(LayoutNode node, Set<DockGroupId> referenced) {
        if (node instanceof LayoutNode.Group group) {
            referenced.add(group.groupId());
        } else if (node instanceof LayoutNode.Split split) {
            split.children().forEach(child -> collectGroups(child, referenced));
        }
    }

    private static void validateSlots(Map<PaneRole, ViewArea> areas, LayoutNode node) {
        if (node instanceof LayoutNode.Slot slot && !areas.containsKey(slot.area())) {
            throw new IllegalArgumentException("slot " + slot.id()
                    + " refers to missing area " + slot.area());
        }
        if (node instanceof LayoutNode.Split split) {
            split.children().forEach(child -> validateSlots(areas, child));
        }
    }
}
