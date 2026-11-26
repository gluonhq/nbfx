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

import java.util.Map;
import java.util.Objects;
import org.netbeans.api.nbfx.layout.LayoutNode;

/**
 * Opaque, immutable state saved by a {@link WindowContent}. The shell stores the payload without
 * interpreting it; the content implementation owns its format.
 *
 * @param windowId the window identity
 * @param layout the content layout snapshot, or {@code null} for custom content without a layout
 * @param payload opaque content-specific state
 * @since 1.12
 */
public record WindowContentState(WindowId windowId, LayoutNode layout, Map<String, String> payload) {

    public WindowContentState {
        windowId = Objects.requireNonNull(windowId, "windowId");
        payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
    }

    /** An empty payload for {@code windowId} and {@code layout}. */
    public static WindowContentState empty(WindowId windowId, LayoutNode layout) {
        return new WindowContentState(windowId, layout, Map.of());
    }
}
