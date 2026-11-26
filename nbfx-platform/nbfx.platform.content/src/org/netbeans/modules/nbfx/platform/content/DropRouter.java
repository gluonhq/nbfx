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
import java.util.LinkedHashSet;
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
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

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

    /**
     * Detailed routing outcome. Exactly one of {@code resolved} and {@code rejection} is non-null.
     *
     * @param resolved the accepted drop, or {@code null} when none accepts
     * @param rejection the first rejection reason, or {@code null} when a drop resolves
     */
    public record Resolution(ResolvedDrop resolved, DropDecision rejection) {
        public Resolution {
            if ((resolved == null) == (rejection == null)) {
                throw new IllegalArgumentException("resolution needs exactly one outcome");
            }
            if (rejection != null && rejection.allowed()) {
                throw new IllegalArgumentException("resolution rejection must be rejected");
            }
        }

        public boolean accepted() {
            return resolved != null;
        }
    }

    /** Discovers handlers from {@link Lookup#getDefault()} and the drop-handler layer path. */
    public static List<DropHandler> discover() {
        LinkedHashSet<DropHandler> handlers = new LinkedHashSet<>();
        handlers.addAll(Lookup.getDefault().lookupAll(DropHandler.class));
        handlers.addAll(Lookups.forPath(LayerPaths.DROP_HANDLERS).lookupAll(DropHandler.class));
        return List.copyOf(handlers);
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
        return Optional.ofNullable(resolveDetailed(source, targetsInPriorityOrder, location,
                requestedMode, context, handlers).resolved());
    }

    /**
     * Resolves a drop and retains the first rejection when no handler accepts. Handlers are evaluated
     * once, in target-priority then handler order.
     *
     * @return an accepted resolution or a rejected resolution carrying a reason key
     */
    public static Resolution resolveDetailed(TransferSource source,
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
        DropDecision firstRejection = null;
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
                    return new Resolution(new ResolvedDrop(handler, request, decision), null);
                }
                if (decision != null && firstRejection == null) {
                    firstRejection = decision;
                }
            }
        }
        return new Resolution(null, firstRejection == null
                ? DropDecision.reject("drop.no-handler") : firstRejection);
    }
}
