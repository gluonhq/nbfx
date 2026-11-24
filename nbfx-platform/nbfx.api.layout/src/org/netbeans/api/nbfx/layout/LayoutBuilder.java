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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable builder for an immutable {@link LayoutSpec}. The builder is local to one composition and
 * is not thread-safe.
 *
 * @since 1.0
 */
public final class LayoutBuilder {

    private final String id;
    private final int formatVersion;
    private final Map<PaneRole, ViewArea> areas = new LinkedHashMap<>();
    private final Map<DockGroupId, DockGroupSpec> groups = new LinkedHashMap<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();
    private LayoutNode root;

    private LayoutBuilder(String id, int formatVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.formatVersion = formatVersion;
    }

    /** Creates a builder for a versioned product layout. */
    public static LayoutBuilder builder(String id, int formatVersion) {
        return new LayoutBuilder(id, formatVersion);
    }

    /** Adds a logical area; duplicate area ids are rejected. */
    public LayoutBuilder area(ViewArea area) {
        Objects.requireNonNull(area, "area");
        if (areas.putIfAbsent(area.id(), area) != null) {
            throw new IllegalArgumentException("duplicate area " + area.id());
        }
        return this;
    }

    /** Adds a physical dock group; duplicate group ids are rejected. */
    public LayoutBuilder group(DockGroupSpec group) {
        Objects.requireNonNull(group, "group");
        if (groups.putIfAbsent(group.id(), group) != null) {
            throw new IllegalArgumentException("duplicate group " + group.id());
        }
        return this;
    }

    /** Adds a metadata value preserved with the layout. */
    public LayoutBuilder metadata(String key, String value) {
        metadata.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /** Sets a split tree with explicit divider positions. */
    public LayoutBuilder split(SplitOrientation orientation, List<Double> dividers,
            LayoutNode... children) {
        root = new LayoutNode.Split(orientation, List.of(children), dividers);
        return this;
    }

    /** Sets a split or group root. */
    public LayoutBuilder root(LayoutNode root) {
        this.root = Objects.requireNonNull(root, "root");
        return this;
    }

    /** Builds and validates the immutable layout. */
    public LayoutSpec build() {
        if (root == null) {
            throw new IllegalStateException("layout root is required");
        }
        return new LayoutSpec(id, formatVersion, areas, groups, root, metadata);
    }
}
