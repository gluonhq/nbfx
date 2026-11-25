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
package org.netbeans.api.nbfx.content;

import java.util.Objects;

/**
 * A drop target: the kind of surface and the stable id of that surface within its kind. The global
 * fallback uses an empty id.
 *
 * @param kind the target kind
 * @param id   the stable id within the kind, or an empty string for the global fallback
 * @since 1.4
 */
public record DropTarget(DropTargetKind kind, String id) {

    public DropTarget {
        Objects.requireNonNull(kind, "kind");
        id = Objects.requireNonNull(id, "id");
    }

    /** The global fallback target - the last handler consulted for a drop. */
    public static DropTarget global() {
        return new DropTarget(DropTargetKind.GLOBAL, "");
    }
}