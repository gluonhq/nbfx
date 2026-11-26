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

import java.util.List;
import java.util.Objects;

/**
 * Evaluates a docking request. Policies are consulted by {@link WindowManager#canDock(DockRequest)};
 * the first rejection wins.
 *
 * @since 1.9
 */
@FunctionalInterface
public interface DockPolicy {

    /**
     * Evaluates {@code request}.
     *
     * @param request the dock request
     * @return the decision, never {@code null}
     */
    DockDecision evaluate(DockRequest request);

    /**
     * Combines policies so every policy must allow the request. The first rejection wins; allowing
     * decisions may refine the placement, and the last allowing decision is returned.
     *
     * @param policies the policies in evaluation order
     * @return the composed policy
     * @since 1.10
     */
    static DockPolicy allOf(DockPolicy... policies) {
        List<DockPolicy> copy = List.of(Objects.requireNonNull(policies, "policies"));
        copy.forEach(Objects::requireNonNull);
        return request -> {
            DockDecision decision = DockDecision.allow(request.placement());
            for (DockPolicy policy : copy) {
                DockDecision next = Objects.requireNonNull(policy.evaluate(request), "policy decision");
                if (!next.allowed()) {
                    return next;
                }
                decision = next;
            }
            return decision;
        };
    }

    /**
     * Combines policies so the first policy that allows the request wins. If all reject, the first
     * rejection is returned.
     *
     * @param policies the policies in evaluation order
     * @return the composed policy
     * @since 1.10
     */
    static DockPolicy anyOf(DockPolicy... policies) {
        List<DockPolicy> copy = List.of(Objects.requireNonNull(policies, "policies"));
        copy.forEach(Objects::requireNonNull);
        return request -> {
            DockDecision firstRejection = null;
            for (DockPolicy policy : copy) {
                DockDecision decision = Objects.requireNonNull(policy.evaluate(request), "policy decision");
                if (decision.allowed()) {
                    return decision;
                }
                if (firstRejection == null) {
                    firstRejection = decision;
                }
            }
            return firstRejection == null
                    ? DockDecision.reject("dock.policy.none")
                    : firstRejection;
        };
    }
}
