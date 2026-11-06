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
package org.netbeans.modules.nbfx.platform.shell;

import javafx.collections.ObservableList;
import javafx.scene.control.MenuItem;
import org.openide.util.Lookup;

/**
 * A dynamic section of a menu. The menu identity, ordering and separator come from the layer
 * registration; a factory only creates the per-shell content.
 *
 * @since 1.0
 */
public interface DynamicMenu {

    /**
     * Creates a dynamic menu section for one shell/menu instance.
     *
     * @since 1.0
     */
    interface Factory {

        /**
         * Creates the content for {@code context}. The factory must be stateless; the returned
         * instance owns its listeners and is disposed with the menu.
         *
         * @param context the scoped shell lookup
         * @return the dynamic menu; never {@code null}
         */
        DynamicMenu create(Lookup context);
    }

    /**
     * The items of this section. The list may be updated by the implementation; the menu follows
     * it until {@link #dispose()} is called.
     *
     * @return the observable items
     */
    ObservableList<MenuItem> items();

    /** Releases listeners and other resources owned by this section. */
    default void dispose() {
    }
}
