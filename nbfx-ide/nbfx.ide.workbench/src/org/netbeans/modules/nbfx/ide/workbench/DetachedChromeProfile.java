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
package org.netbeans.modules.nbfx.ide.workbench;

import java.util.Set;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.modules.nbfx.ide.actions.IdeActionIds;
import org.netbeans.modules.nbfx.platform.shell.api.ShellChromeProfile;

/**
 * The reduced chrome of a detached editor window: save actions, editor commands, view toggles and
 * the window/close/project-switch actions, with the file/clipboard/edit tool bars.
 */
final class DetachedChromeProfile implements ShellChromeProfile {

    private static final Set<String> MENUS = Set.of("File", "Edit", "View", "Window", "Help");
    private static final Set<String> FILE_ITEMS = Set.of(ActionIds.SAVE, ActionIds.SAVE_ALL);
    private static final Set<String> WINDOW_ITEMS = Set.of(
            "configure-window",
            ActionIds.CLOSE_DOCUMENT,
            ActionIds.CLOSE_ALL_DOCUMENTS,
            ActionIds.CLOSE_OTHER_DOCUMENTS,
            IdeActionIds.NEXT_PROJECT,
            IdeActionIds.PREVIOUS_PROJECT);
    private static final Set<String> TOOLBARS = Set.of("file", "clipboard", "edit");

    @Override
    public boolean includeMenu(String menuId) {
        return MENUS.contains(menuId);
    }

    @Override
    public boolean includeMenuItem(String menuId, String itemId) {
        return switch (menuId) {
            case "File" -> FILE_ITEMS.contains(itemId);
            case "Window" -> WINDOW_ITEMS.contains(itemId);
            default -> true;
        };
    }

    @Override
    public boolean includeToolBar(String toolBarId) {
        return TOOLBARS.contains(toolBarId);
    }

    @Override
    public boolean movableToolBars() {
        return false;
    }
}
