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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.netbeans.api.nbfx.resources.ResourceRef;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** Tests immutable area/group/layout values and reference validation. */
public class LayoutSpecTest {

    @Test
    public void zeroContentLayoutWithOneGroupIsValid() {
        PaneRole area = PaneRole.of("product:editor");
        DockGroupId group = DockGroupId.of("product:editor.main");
        LayoutSpec layout = new LayoutSpec("product.default", 1,
                Map.of(area, area(area)),
                Map.of(group, group(group, area)),
                new LayoutNode.Group(group),
                Map.of());

        assertEquals(1, layout.areas().size());
        assertEquals(1, layout.groups().size());
    }

    @Test
    public void nestedSplitUsesDividerPositions() {
        PaneRole side = PaneRole.of("product:side");
        PaneRole main = PaneRole.of("product:editor");
        DockGroupId left = DockGroupId.of("product:side.main");
        DockGroupId right = DockGroupId.of("product:editor.main");
        LayoutNode.Split root = new LayoutNode.Split(SplitOrientation.HORIZONTAL,
                List.of(new LayoutNode.Group(left), new LayoutNode.Group(right)),
                List.of(0.25d));

        LayoutSpec layout = new LayoutSpec("product.default", 1,
                Map.of(side, area(side), main, area(main)),
                Map.of(left, group(left, side), right, group(right, main)),
                root, Map.of());

        assertEquals(List.of(0.25d), root.dividers());
        assertEquals(2, layout.groups().size());
    }

    @Test
    public void rejectsMissingGroupAreaAndUnreferencedPersistentGroup() {
        PaneRole area = PaneRole.of("product:editor");
        DockGroupId group = DockGroupId.of("product:editor.main");
        assertThrows(IllegalArgumentException.class, () -> new LayoutSpec("bad", 1,
                Map.of(), Map.of(group, group(group, area)), new LayoutNode.Group(group), Map.of()));
        DockGroupId other = DockGroupId.of("product:editor.other");
        assertThrows(IllegalArgumentException.class, () -> new LayoutSpec("bad", 1,
                Map.of(area, area(area)),
                Map.of(group, group(group, area), other, group(other, area)),
                new LayoutNode.Group(group), Map.of()));
    }

    @Test
    public void builderCreatesTheSameImmutableLayout() {
        PaneRole side = PaneRole.of("product:side");
        PaneRole main = PaneRole.of("product:editor");
        DockGroupId left = DockGroupId.of("product:side.main");
        DockGroupId right = DockGroupId.of("product:editor.main");
        LayoutSpec layout = LayoutBuilder.builder("product.default", 1)
                .area(area(side))
                .area(area(main))
                .group(group(left, side))
                .group(group(right, main))
                .metadata("owner", "product")
                .split(SplitOrientation.HORIZONTAL, List.of(0.25d),
                        LayoutNode.group(left), LayoutNode.group(right))
                .build();

        assertEquals("product.default", layout.id());
        assertEquals("product", layout.metadata().get("owner"));
        assertEquals(List.of(0.25d), ((LayoutNode.Split) layout.root()).dividers());
    }

    @Test
    public void builderRejectsDuplicateAreaAndGroupIds() {
        ViewArea area = area(PaneRole.MAIN);
        assertThrows(IllegalArgumentException.class, () -> LayoutBuilder.builder("bad", 1)
                .area(area).area(area));
        DockGroupSpec group = group(DockGroupId.of("product:editor.main"), PaneRole.MAIN);
        assertThrows(IllegalArgumentException.class, () -> LayoutBuilder.builder("bad", 1)
                .group(group).group(group));
    }

    @Test
    public void builderRequiresRootAndValidReferences() {
        assertThrows(IllegalStateException.class, () -> LayoutBuilder.builder("empty", 1).build());
        assertThrows(IllegalArgumentException.class, () -> LayoutBuilder.builder("bad", 1)
                .root(LayoutNode.group("product:missing"))
                .build());
    }

    @Test
    public void rejectsMissingGroupReferenceAndMismatchedMapKeys() {
        PaneRole area = PaneRole.of("product:editor");
        DockGroupId missing = DockGroupId.of("product:missing");
        assertThrows(IllegalArgumentException.class, () -> new LayoutSpec("bad", 1,
                Map.of(area, area(area)), Map.of(), new LayoutNode.Group(missing), Map.of()));

        DockGroupId group = DockGroupId.of("product:editor.main");
        assertThrows(IllegalArgumentException.class, () -> new LayoutSpec("bad", 1,
                Map.of(area, area(area)),
                Map.of(DockGroupId.of("product:other"), group(group, area)),
                new LayoutNode.Group(group), Map.of()));
    }

    @Test
    public void defensivelyCopiesCollections() {
        Set<ViewAreaCapability> capabilities = new HashSet<>();
        capabilities.add(ViewAreaCapability.MOVABLE);
        List<ResourceRef> sheets = new ArrayList<>();
        sheets.add(ResourceRef.of("org.example", "area.css"));
        AreaStyle style = new AreaStyle(Set.of("acme-area"), sheets, List.of());
        ViewArea area = new ViewArea(PaneRole.MAIN, ViewAreaKind.MAIN, "Area", DockLocation.CENTER,
                1.0d, PresentationId.TABBED, capabilities, "default", style,
                ViewAreaVisibility.STARTUP, EmptyAreaPolicy.KEEP, 0);
        capabilities.clear();
        sheets.clear();
        assertEquals(Set.of(ViewAreaCapability.MOVABLE), area.capabilities());
        assertEquals(1, area.style().stylesheets().size());
    }

    @Test
    public void rejectsSlotRootAndDuplicateTreeIdentities() {
        PaneRole area = PaneRole.of("product:editor");
        LayoutNode.Slot slot = new LayoutNode.Slot(SlotId.of("product:slot"), area, DockSide.CENTER);
        assertThrows(IllegalArgumentException.class, () -> new LayoutSpec("bad", 1,
                Map.of(area, area(area)), Map.of(), slot, Map.of()));

        DockGroupId group = DockGroupId.of("product:editor.main");
        LayoutNode.Split duplicateGroups = new LayoutNode.Split(SplitOrientation.HORIZONTAL,
                List.of(new LayoutNode.Group(group), new LayoutNode.Group(group)), List.of(0.5d));
        assertThrows(IllegalArgumentException.class, () -> new LayoutSpec("bad", 1,
                Map.of(area, area(area)), Map.of(group, group(group, area)),
                duplicateGroups, Map.of()));
        LayoutNode.Split duplicateSlots = new LayoutNode.Split(SplitOrientation.HORIZONTAL,
                List.of(slot, new LayoutNode.Slot(slot.id(), area, DockSide.END)), List.of(0.5d));
        assertThrows(IllegalArgumentException.class, () -> new LayoutSpec("bad", 1,
                Map.of(area, area(area)), Map.of(), duplicateSlots, Map.of()));
    }

    @Test
    public void acceptsDividerEndpoints() {
        LayoutNode.Split split = new LayoutNode.Split(SplitOrientation.HORIZONTAL,
                List.of(new LayoutNode.Group(group()), new LayoutNode.Group(group()),
                        new LayoutNode.Group(group())),
                List.of(0.0d, 1.0d));
        assertEquals(List.of(0.0d, 1.0d), split.dividers());
    }

    @Test
    public void validatesDividerArityRangeAndOrder() {
        assertThrows(IllegalArgumentException.class, () -> new LayoutNode.Split(
                SplitOrientation.HORIZONTAL, List.of(new LayoutNode.Group(group())), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new LayoutNode.Split(
                SplitOrientation.HORIZONTAL,
                List.of(new LayoutNode.Group(group()), new LayoutNode.Group(group()), new LayoutNode.Group(group())),
                List.of(0.8d, 0.2d)));
    }

    private static DockGroupId group() {
        return DockGroupId.of("product:editor.main");
    }

    private static ViewArea area(PaneRole role) {
        return new ViewArea(role, ViewAreaKind.MAIN, "Area", DockLocation.CENTER, 1.0d,
                PresentationId.TABBED, Set.of(), "default", AreaStyle.EMPTY,
                ViewAreaVisibility.STARTUP, EmptyAreaPolicy.KEEP, 0);
    }

    private static DockGroupSpec group(DockGroupId id, PaneRole area) {
        return new DockGroupSpec(id, area, PaneKind.VIEW, PresentationId.TABBED,
                ContentCapacity.MANY, "default", GroupLifetime.PERSISTENT, AreaStyle.EMPTY);
    }
}
