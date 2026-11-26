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

import javafx.scene.AccessibleAttribute;
import javafx.scene.control.TabPane;
import org.openide.util.NbBundle;

/** Accessible status announcements for external drag-and-drop results. */
final class DropAccessibility {

    private DropAccessibility() {
    }

    static void accepted(TabPane pane) {
        announce(pane, NbBundle.getMessage(DropAccessibility.class, "DnD.drop.accepted"));
    }

    static void rejected(TabPane pane, String reasonKey) {
        String reason = switch (reasonKey == null ? "" : reasonKey) {
            case "drop.no-handler" -> NbBundle.getMessage(DropAccessibility.class, "DnD.drop.rejected.noHandler");
            case "drop.open.failed" -> NbBundle.getMessage(DropAccessibility.class, "DnD.drop.rejected.noHost");
            default -> reasonKey == null || reasonKey.isBlank() ? "drop.no-handler" : reasonKey;
        };
        announce(pane, NbBundle.getMessage(DropAccessibility.class, "DnD.drop.rejected", reason));
    }

    private static void announce(TabPane pane, String message) {
        if (pane.getAccessibleText() == null || pane.getAccessibleText().isBlank()) {
            pane.setAccessibleText(message);
            pane.notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
        } else {
            pane.setAccessibleHelp(message);
            pane.notifyAccessibleAttributeChanged(AccessibleAttribute.HELP);
        }
    }
}
