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
import org.netbeans.api.nbfx.layout.DockGroupId;

/**
 * The outcome of a docking operation.
 *
 * @param decision the dock decision
 * @param group    the group the content ended up in, or {@code null} when rejected
 * @since 1.7
 */
public record DockResult(DockDecision decision, DockGroupId group) {

    public DockResult {
        Objects.requireNonNull(decision, "decision");
        if (decision.allowed() && group == null) {
            throw new IllegalArgumentException("an accepted dock needs a target group");
        }
    }

    /** An accepted result for {@code decision} and {@code group}. */
    public static DockResult accepted(DockDecision decision, DockGroupId group) {
        return new DockResult(decision, group);
    }

    /** A rejected result carrying {@code decision}. */
    public static DockResult rejected(DockDecision decision) {
        return new DockResult(decision, null);
    }
}