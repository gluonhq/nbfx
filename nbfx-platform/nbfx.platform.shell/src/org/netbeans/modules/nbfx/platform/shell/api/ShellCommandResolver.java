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
package org.netbeans.modules.nbfx.platform.shell.api;

import org.netbeans.api.nbfx.actions.Command;

/**
 * Resolves a layer command for one chrome instance. The default resolution is a plain registry
 * lookup; a product can supply a resolver in the {@link ShellContext} lookup to scope commands to
 * its window (for example the main pane's active document) or to dispatch between an editor and a
 * file target.
 * <p>
 * The resolver is deliberately generic: it deals in {@link Command}s only, so the chrome API does
 * not depend on editor or project types.
 *
 * @since 1.0
 */
public interface ShellCommandResolver {

    /**
     * Resolves the command registered under {@code id}, or {@code null}.
     *
     * @param id the command id
     * @return the command, or {@code null}
     */
    Command resolve(String id);

    /** Releases any scoped commands created by this resolver. */
    default void close() {
    }
}
