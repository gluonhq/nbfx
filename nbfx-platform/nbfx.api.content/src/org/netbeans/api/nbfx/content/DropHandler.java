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

import java.util.Set;

/**
 * Evaluates and performs a drop on one target. The shell routes an external or domain drag to the
 * topmost matching handler; internal content/group drags over dock affordances use the docking
 * policy instead.
 * <p>
 * {@link #evaluate(DropRequest)} is pure and may be called repeatedly while the drag hovers.
 * {@link #perform(DropRequest, DropDecision)} runs once for an accepted decision and must use that
 * decision's effect and placement unchanged.
 *
 * @since 1.4
 */
public interface DropHandler {

    /** The target this handler serves. */
    DropTarget target();

    /** The source kinds this handler accepts; empty means none. */
    Set<TransferKind> sourceKinds();

    /**
     * Evaluates the drop without mutating anything.
     *
     * @param request the drop request
     * @return the decision, never {@code null}
     */
    DropDecision evaluate(DropRequest request);

    /**
     * Performs the accepted drop.
     *
     * @param request  the request that was evaluated
     * @param accepted the accepted decision
     */
    void perform(DropRequest request, DropDecision accepted);
}