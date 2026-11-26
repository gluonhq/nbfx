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
import org.openide.util.Lookup;

/**
 * Product or shell content hosted by a {@link DockWindow}. Closing the content releases host
 * resources only; content close/dispose remains owned by the content manager.
 *
 * @since 1.12
 */
public interface WindowContent extends AutoCloseable {

    /** The root node shown in the window. */
    Node root();

    /** The current content/action lookup. */
    Lookup lookup();

    /** Saves opaque content state for later restoration. */
    WindowContentState saveState();

    /**
     * Releases host resources without closing the content. Implementations that have no separate
     * host resources may leave this as a no-op.
     */
    @Override
    default void close() {
    }
}
