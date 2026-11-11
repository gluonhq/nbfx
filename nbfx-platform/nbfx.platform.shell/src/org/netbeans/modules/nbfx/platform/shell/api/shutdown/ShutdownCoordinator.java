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
package org.netbeans.modules.nbfx.platform.shell.api.shutdown;

/**
 * Collects impact, confirms the shutdown, and runs the selected cleanup.
 * <p>
 * {@link #impact()} and {@link #confirm(ShutdownImpact)} are called on the JavaFX Application
 * Thread, so they may show UI. {@link #shutdown(ShutdownSelection)} is called off that thread, so
 * participants may perform blocking cleanup without freezing the shell. Implementations must be
 * idempotent.
 */
public interface ShutdownCoordinator {

    ShutdownImpact impact();

    /** Returns the selected plan, or {@code null} when the user cancels. */
    ShutdownSelection confirm(ShutdownImpact impact);

    /** Runs every participant in order. Implementations must be idempotent. */
    void shutdown(ShutdownSelection selection);
}
