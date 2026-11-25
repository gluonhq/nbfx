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

import org.netbeans.api.nbfx.content.DockPlacement;

/**
 * The outcome of evaluating a {@link DockRequest}. A rejection always carries a reason key.
 *
 * @param allowed   whether the docking operation is accepted
 * @param placement the effective placement, or {@code null} when rejected
 * @param reasonKey the bundle key explaining a rejection; empty when accepted
 * @since 1.7
 */
public record DockDecision(boolean allowed, DockPlacement placement, String reasonKey) {

    public DockDecision {
        if (allowed) {
            reasonKey = reasonKey == null ? "" : reasonKey;
        } else if (reasonKey == null || reasonKey.isBlank()) {
            throw new IllegalArgumentException("a rejected dock needs a reason key");
        }
    }

    /** An accepted dock with {@code placement}. */
    public static DockDecision allow(DockPlacement placement) {
        return new DockDecision(true, placement, "");
    }

    /** A rejection with {@code reasonKey}. */
    public static DockDecision reject(String reasonKey) {
        return new DockDecision(false, null, reasonKey);
    }
}