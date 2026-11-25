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

import javafx.scene.Node;

/**
 * One product-owned Node contribution mounted in a shell region. Each shell mount receives a fresh
 * instance; instances and Nodes must not be shared between windows or surfaces.
 *
 * @since 1.2
 */
public interface ShellContribution {

    /**
     * Creates a fresh, unparented Node for one shell mount. Called on the JavaFX Application Thread;
     * ownership transfers to the shell when the Node is returned.
     *
     * @param context the per-shell context
     * @return a fresh unparented Node
     */
    Node create(ShellContext context);

    /**
     * Releases resources owned by this per-shell instance. Called on the JavaFX Application Thread
     * when the mount is disposed. Detaching or hiding a mount does not imply disposal.
     */
    default void dispose() {
    }
}
