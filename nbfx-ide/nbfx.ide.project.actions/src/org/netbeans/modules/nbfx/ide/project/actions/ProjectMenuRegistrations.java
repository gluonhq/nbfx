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
package org.netbeans.modules.nbfx.ide.project.actions;

import static org.netbeans.modules.nbfx.ide.actions.IdeActionIds.CLOSE_ALL_PROJECTS;
import static org.netbeans.modules.nbfx.ide.actions.IdeActionIds.CLOSE_PROJECT;
import static org.netbeans.modules.nbfx.ide.actions.IdeActionIds.NEW_FILE;
import static org.netbeans.modules.nbfx.ide.actions.IdeActionIds.NEW_PROJECT;
import static org.netbeans.modules.nbfx.ide.actions.IdeActionIds.NEXT_PROJECT;
import static org.netbeans.modules.nbfx.ide.actions.IdeActionIds.OPEN_PROJECT;
import static org.netbeans.modules.nbfx.ide.actions.IdeActionIds.PREVIOUS_PROJECT;

import org.netbeans.api.nbfx.actions.ActionReference;

/**
 * Declares the project entries in the File and Window menus. The commands are registered by
 * {@link ProjectLifecycleCommandsProvider} and {@link WizardCommandsProvider}; this class carries
 * only layer metadata.
 */
@ActionReference(id = NEW_PROJECT, path = "Menus/File", position = 10)
@ActionReference(id = NEW_FILE, path = "Menus/File", position = 15)
@ActionReference(id = OPEN_PROJECT, path = "Menus/File", position = 20)
@ActionReference(id = CLOSE_PROJECT, path = "Menus/File", position = 40)
@ActionReference(id = CLOSE_ALL_PROJECTS, path = "Menus/File", position = 50)
@ActionReference(id = NEXT_PROJECT, path = "Menus/Window", position = 110, separatorBefore = true)
@ActionReference(id = PREVIOUS_PROJECT, path = "Menus/Window", position = 120)
final class ProjectMenuRegistrations {

    private ProjectMenuRegistrations() {
    }
}
