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

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.CommandsProvider;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.api.nbfx.project.ProjectLifecycle;
import org.netbeans.api.nbfx.project.ProjectOpener;
import org.netbeans.api.nbfx.project.ProjectRegistry;
import org.netbeans.modules.nbfx.ide.actions.IdeActionIds;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * Contributes the project lifecycle commands: Open / Close / Close All and Next / Previous Project.
 * They live here with the other project commands; the shell keeps the application-level operations
 * behind {@link ProjectOpener} and {@link ProjectLifecycle}.
 */
@ServiceProvider(service = CommandsProvider.class)
public final class ProjectLifecycleCommandsProvider implements CommandsProvider {

    private static final Logger LOG = Logger.getLogger(ProjectLifecycleCommandsProvider.class.getName());

    @Override
    public Collection<Command> createCommands() {
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        if (registry == null) {
            LOG.warning("No ProjectRegistry found; project actions will not be registered");
            return List.of();
        }
        ProjectLifecycle lifecycle = Lookup.getDefault().lookup(ProjectLifecycle.class);
        ProjectOpener opener = Lookup.getDefault().lookup(ProjectOpener.class);
        if (lifecycle == null) {
            LOG.warning("No ProjectLifecycle found; project close/switch commands are disabled");
        }
        if (opener == null) {
            LOG.warning("No ProjectOpener found; Open Project is disabled");
        }
        SimpleBooleanProperty missingLifecycle = new SimpleBooleanProperty(lifecycle == null);
        SimpleBooleanProperty missingOpener = new SimpleBooleanProperty(opener == null);
        ObservableValue<Boolean> closeDisabled = Bindings.or(Bindings.isEmpty(registry.getOpenProjects()),
                missingLifecycle);
        ObservableValue<Boolean> switchDisabled = Bindings.or(
                Bindings.size(registry.getOpenProjects()).lessThan(2), missingLifecycle);
        return List.of(
                new RunnableCommand(IdeActionIds.OPEN_PROJECT, message("CTL_OpenProjectCommand"),
                        new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                        () -> {
                            if (opener != null) {
                                opener.open();
                            }
                        }, missingOpener),
                new RunnableCommand(IdeActionIds.CLOSE_PROJECT, message("CTL_CloseProjectCommand"),
                        null, () -> {
                            if (lifecycle != null) {
                                lifecycle.close(registry.getSelected());
                            }
                        }, closeDisabled),
                new RunnableCommand(IdeActionIds.CLOSE_ALL_PROJECTS, message("CTL_CloseAllProjectsCommand"),
                        null, () -> {
                            if (lifecycle != null) {
                                lifecycle.closeAll();
                            }
                        }, closeDisabled),
                new RunnableCommand(IdeActionIds.NEXT_PROJECT, message("CTL_NextProjectCommand"),
                        new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
                        () -> {
                            if (lifecycle != null) {
                                lifecycle.selectNext();
                            }
                        }, switchDisabled),
                new RunnableCommand(IdeActionIds.PREVIOUS_PROJECT, message("CTL_PreviousProjectCommand"),
                        new KeyCodeCombination(KeyCode.LEFT, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
                        () -> {
                            if (lifecycle != null) {
                                lifecycle.selectPrevious();
                            }
                        }, switchDisabled));
    }

    private static String message(String key) {
        return NbBundle.getMessage(ProjectLifecycleCommandsProvider.class, key);
    }
}
