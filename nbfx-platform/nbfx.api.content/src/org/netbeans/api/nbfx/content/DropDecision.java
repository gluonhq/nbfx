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
 * The outcome of evaluating a {@link DropRequest}. The accepted decision is authoritative: the
 * handler performing the drop cannot recompute a different effect or placement.
 *
 * @param allowed    whether the drop is accepted
 * @param effect     the effective transfer mode of an accepted drop; {@code null} when rejected
 * @param reasonKey  the bundle key explaining a rejection; empty when accepted
 * @param placement  where the drop goes, or {@code null} when no placement is known
 * @since 1.4
 */
public record DropDecision(boolean allowed, TransferMode effect, String reasonKey,
        DockPlacement placement) {

    public DropDecision {
        if (allowed) {
            Objects.requireNonNull(effect, "effect");
            reasonKey = reasonKey == null ? "" : reasonKey;
        } else if (reasonKey == null || reasonKey.isBlank()) {
            throw new IllegalArgumentException("a rejected drop needs a reason key");
        }
    }

    /** An accepted drop with {@code effect} and an optional {@code placement}. */
    public static DropDecision allow(TransferMode effect, DockPlacement placement) {
        return new DropDecision(true, effect, "", placement);
    }

    /** A rejection with {@code reasonKey} but no placement. */
    public static DropDecision reject(String reasonKey) {
        return new DropDecision(false, null, reasonKey, null);
    }

    /** A rejection with {@code reasonKey} that names where the drop would have gone. */
    public static DropDecision reject(String reasonKey, DockPlacement placement) {
        return new DropDecision(false, null, reasonKey, placement);
    }
}