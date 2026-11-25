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
package org.netbeans.modules.nbfx.platform.content;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.geometry.Point2D;
import org.netbeans.api.nbfx.content.DropDecision;
import org.netbeans.api.nbfx.content.DropHandler;
import org.netbeans.api.nbfx.content.DropRequest;
import org.netbeans.api.nbfx.content.DropTarget;
import org.netbeans.api.nbfx.content.TransferMode;
import org.netbeans.api.nbfx.content.TransferSource;
import org.openide.util.Lookup;

/**
 * Routes external/domain drops to registered {@link DropHandler}s. Targets are tried in the
 * priority order supplied by the caller (typically content, overlay, group, area, global fallback).
 * The first handler that matches the target and source kind and returns an accepted decision wins.
 */
public final class DropRouter {

    private DropRouter() {
    }

    /** A handler, request and accepted decision selected by routing. */
    public record ResolvedDrop(DropHandler handler, DropRequest request, DropDecision decision) {
        public ResolvedDrop {
            Objects.requireNonNull(handler, "handler");
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(decision, "decision");
            if (!decision.allowed()) {
                throw new IllegalArgumentException("resolved drop must be accepted");
            }
        }
    }

    /** Discovers handlers from {@link Lookup#getDefault()}. */
    public static List<DropHandler> discover() {
        return List.copyOf(Lookup.getDefault().lookupAll(DropHandler.class));
    }

    /**
     * Resolves the first accepted handler for {@code source} over {@code targetsInPriorityOrder}.
     *
     * @param source                  the transfer source
     * @param targetsInPriorityOrder  targets to try, highest priority first
     * @param location                drop location in target-local coordinates
     * @param requestedMode           requested transfer mode
     * @param context                 routing context
     * @param handlers                candidate handlers
     * @return the resolved drop, or empty when no handler accepts
     */
    public static Optional<ResolvedDrop> resolve(TransferSource source,
                                                 List<DropTarget> targetsInPriorityOrder,
                                                 Point2D location,
                                                 TransferMode requestedMode,
                                                 Lookup context,
                                                 Collection<? extends DropHandler> handlers) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetsInPriorityOrder, "targetsInPriorityOrder");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(requestedMode, "requestedMode");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(handlers, "handlers");
        for (DropTarget target : targetsInPriorityOrder) {
            if (target == null) {
                continue;
            }
            DropRequest request = new DropRequest(source, target, location, requestedMode, context);
            for (DropHandler handler : handlers) {
                if (handler == null || !target.equals(handler.target())) {
                    continue;
                }
                if (!handler.sourceKinds().contains(source.kind())) {
                    continue;
                }
                DropDecision decision = handler.evaluate(request);
                if (decision != null && decision.allowed()) {
                    return Optional.of(new ResolvedDrop(handler, request, decision));
                }
            }
        }
        return Optional.empty();
    }
}
