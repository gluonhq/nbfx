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

import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.CLOSE_ALL_PROJECTS;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.CLOSE_PROJECT;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.FIND_IN_PROJECTS;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.FIND_USAGES;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.NEW_FILE;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.NEW_PROJECT;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.NEXT_PROJECT;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.OPEN_PROJECT;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.PREVIOUS_PROJECT;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.REPLACE_IN_PROJECTS;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.SELECT_FILES;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.SELECT_IN_FILES;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.SELECT_IN_PROJECTS;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.SELECT_PROJECTS;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.SELECT_SEARCH_RESULTS;
import static org.netbeans.modules.nbfx.ide.workbench.IdeActionIds.SELECT_USAGES;

import org.netbeans.api.nbfx.actions.ActionReference;
import org.netbeans.api.nbfx.views.MenuRegistration;
import org.netbeans.modules.nbfx.platform.menus.Menus;

/**
 * IDE-specific entries in the shell menus. The generic File/Edit/View/Window layout stays in
 * {@link Menus}; this module adds the project, usages and search entries that depend on IDE services.
 */
@MenuRegistration(id = "Window/IDE Tools", displayName = "IDE Tools", position = 100)
@ActionReference(id = NEW_PROJECT, path = "Menus/File", position = 10)
@ActionReference(id = NEW_FILE, path = "Menus/File", position = 15)
@ActionReference(id = OPEN_PROJECT, path = "Menus/File", position = 20)
@ActionReference(id = Menus.OPEN_RECENT, path = "Menus/File", position = 30)
@ActionReference(id = CLOSE_PROJECT, path = "Menus/File", position = 40)
@ActionReference(id = CLOSE_ALL_PROJECTS, path = "Menus/File", position = 50)
@ActionReference(id = SELECT_PROJECTS, path = "Menus/Window", position = 10)
@ActionReference(id = SELECT_FILES, path = "Menus/Window", position = 20)
@ActionReference(id = SELECT_USAGES, path = "Menus/Window", position = 30)
@ActionReference(id = SELECT_SEARCH_RESULTS, path = "Menus/Window", position = 40)
@ActionReference(id = NEXT_PROJECT, path = "Menus/Window", position = 110, separatorBefore = true)
@ActionReference(id = PREVIOUS_PROJECT, path = "Menus/Window", position = 120)
@ActionReference(id = SELECT_IN_PROJECTS, path = "ContextMenus/Editor", position = 120, separatorBefore = true)
@ActionReference(id = SELECT_IN_PROJECTS, path = "Menus/Navigate", position = 70, separatorBefore = true)
@ActionReference(id = SELECT_IN_FILES, path = "Menus/Navigate", position = 80)
@ActionReference(id = FIND_IN_PROJECTS, path = "Menus/Edit", position = 110)
@ActionReference(id = REPLACE_IN_PROJECTS, path = "Menus/Edit", position = 120)
@ActionReference(id = FIND_USAGES, path = "Menus/Edit", position = 130, separatorBefore = true)
public final class IdeMenus {

    private IdeMenus() {
    }
}
