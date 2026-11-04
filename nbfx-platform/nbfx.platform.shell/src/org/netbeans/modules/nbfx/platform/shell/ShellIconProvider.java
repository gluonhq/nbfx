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

import javafx.scene.Node;
import org.netbeans.api.nbfx.actions.Command;

/**
 * Supplies the icon for a command that does not carry one itself. This lets a product keep its
 * icon resources in one place without teaching the generic builders about resource paths. The
 * generic builders fall back to {@link Command#getIcon()} first.
 *
 * @since 1.0
 */
public interface ShellIconProvider {

    /** The size of an icon in a menu, in pixels. */
    double MENU_SIZE = 16;

    /** The size of an icon on a tool bar, in pixels. */
    double TOOLBAR_SIZE = 24;

    /**
     * Builds a new icon node for {@code commandId}, or returns {@code null}.
     *
     * @param commandId the command id
     * @param size      the requested edge size in pixels
     * @return a new icon node, or {@code null}
     */
    Node icon(String commandId, double size);

    /** Returns {@code command}'s own icon, or the provider's icon, or {@code null}. */
    static Node icon(ShellContext context, Command command, double size) {
        Node own = command.getIcon();
        if (own != null || context == null) {
            return own;
        }
        ShellIconProvider provider = context.lookup().lookup(ShellIconProvider.class);
        return provider == null ? null : provider.icon(command.getId(), size);
    }
}
