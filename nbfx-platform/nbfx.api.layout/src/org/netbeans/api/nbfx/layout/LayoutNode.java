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

/**
 * Content-free layout tree. A split contains groups or runtime slots; a group references a declared
 * {@link DockGroupSpec}; a slot materializes a group in an area at runtime.
 *
 * @since 1.0
 */
public sealed interface LayoutNode {

    /** A split with N children and N-1 strictly increasing divider positions. */
    record Split(SplitOrientation orientation, List<LayoutNode> children, List<Double> dividers)
            implements LayoutNode {

        public Split {
            orientation = Objects.requireNonNull(orientation, "orientation");
            children = List.copyOf(Objects.requireNonNull(children, "children"));
            dividers = List.copyOf(Objects.requireNonNull(dividers, "dividers"));
            if (children.size() < 2) {
                throw new IllegalArgumentException("split requires at least two children");
            }
            if (dividers.size() != children.size() - 1) {
                throw new IllegalArgumentException("split requires N-1 dividers");
            }
            double previous = 0.0d;
            for (double divider : dividers) {
                if (Double.isNaN(divider) || divider < 0.0d || divider > 1.0d
                        || divider <= previous) {
                    throw new IllegalArgumentException("dividers must increase within [0,1]");
                }
                previous = divider;
            }
        }
    }

    /** A declared physical group. */
    record Group(DockGroupId groupId) implements LayoutNode {
        public Group {
            groupId = Objects.requireNonNull(groupId, "groupId");
        }
    }

    /** A stable runtime slot in an area. */
    record Slot(SlotId id, PaneRole area, DockSide side) implements LayoutNode {
        public Slot {
            id = Objects.requireNonNull(id, "id");
            area = Objects.requireNonNull(area, "area");
            side = Objects.requireNonNull(side, "side");
        }
    }
}
