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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 * @param root          split or group layout tree
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
        areas = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(areas, "areas")));
        groups = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(groups, "groups")));
        root = Objects.requireNonNull(root, "root");
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(metadata, "metadata")));
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (formatVersion < 1) {
            throw new IllegalArgumentException("formatVersion must be positive");
        }
        if (!(root instanceof LayoutNode.Split || root instanceof LayoutNode.Group)) {
            throw new IllegalArgumentException("layout root must be a split or group");
        }
        validateReferences(areas, groups, root);
    }

    /**
     * Returns a canonical copy of this layout. Unreferenced transient groups are removed, areas are
     * ordered by {@code order}/id, groups by id and metadata by key, and signed-zero dividers are
     * normalized to positive zero. Geometry is already validated by construction; invalid geometry
     * is rejected rather than clamped. The operation is idempotent.
     *
     * @return the normalized layout
     */
    public LayoutSpec normalized() {
        Set<DockGroupId> referenced = new LinkedHashSet<>();
        collectGroups(root, referenced);

        Map<PaneRole, ViewArea> orderedAreas = new LinkedHashMap<>();
        areas.values().stream()
                .sorted(Comparator.comparingInt(ViewArea::order)
                        .thenComparing(area -> area.id().value()))
                .forEach(area -> orderedAreas.put(area.id(), area));

        Map<DockGroupId, DockGroupSpec> orderedGroups = new LinkedHashMap<>();
        List<DockGroupSpec> groupList = new ArrayList<>(groups.values());
        groupList.sort(Comparator.comparing(group -> group.id().value()));
        for (DockGroupSpec group : groupList) {
            if (referenced.contains(group.id()) || group.lifetime() != GroupLifetime.TRANSIENT) {
                orderedGroups.put(group.id(), group);
            }
        }

        Map<String, String> orderedMetadata = new LinkedHashMap<>();
        metadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> orderedMetadata.put(entry.getKey(), entry.getValue()));

        return new LayoutSpec(id, formatVersion, orderedAreas, orderedGroups,
                normalizeNode(root), orderedMetadata);
    }

    private static LayoutNode normalizeNode(LayoutNode node) {
        if (node instanceof LayoutNode.Split split) {
            List<LayoutNode> children = split.children().stream()
                    .map(LayoutSpec::normalizeNode)
                    .toList();
            List<Double> dividers = split.dividers().stream()
                    .map(divider -> divider == 0.0d ? 0.0d : divider)
                    .toList();
            return new LayoutNode.Split(split.orientation(), children, dividers);
        }
        return node;
    }

    private static void validateReferences(Map<PaneRole, ViewArea> areas,
            Map<DockGroupId, DockGroupSpec> groups, LayoutNode root) {
        Set<DockGroupId> referenced = new LinkedHashSet<>();
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
        validateSlots(areas, root, new LinkedHashSet<>());
    }

    private static void collectGroups(LayoutNode node, Set<DockGroupId> referenced) {
        if (node instanceof LayoutNode.Group group) {
            if (!referenced.add(group.groupId())) {
                throw new IllegalArgumentException("duplicate group reference " + group.groupId());
            }
        } else if (node instanceof LayoutNode.Split split) {
            split.children().forEach(child -> collectGroups(child, referenced));
        }
    }

    private static void validateSlots(Map<PaneRole, ViewArea> areas, LayoutNode node,
            Set<SlotId> slotIds) {
        if (node instanceof LayoutNode.Slot slot) {
            if (!slotIds.add(slot.id())) {
                throw new IllegalArgumentException("duplicate slot id " + slot.id());
            }
            if (!areas.containsKey(slot.area())) {
                throw new IllegalArgumentException("slot " + slot.id()
                        + " refers to missing area " + slot.area());
            }
        }
        if (node instanceof LayoutNode.Split split) {
            split.children().forEach(child -> validateSlots(areas, child, slotIds));
        }
    }
}
