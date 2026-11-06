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

import org.netbeans.modules.nbfx.platform.shell.api.DetachedBarsContext;
import org.netbeans.modules.nbfx.platform.shell.api.DetachedBarsProvider;
import org.netbeans.modules.nbfx.platform.shell.api.NavigatorRevealState;
import org.netbeans.modules.nbfx.platform.shell.api.ProjectSwitcher;
import org.netbeans.modules.nbfx.platform.shell.api.TabHomeResolver;
import org.netbeans.modules.nbfx.platform.shell.api.WindowEnvironmentContribution;

import java.util.List;
import java.util.Objects;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.view.NavigatorRevealer;
import org.netbeans.modules.nbfx.platform.workspace.WindowEnvironment;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * The generic window environment: it composes the focused window capabilities and keeps the old
 * {@link WindowEnvironmentContribution} as a compatibility fallback.
 *
 * @since 1.0
 */
@ServiceProvider(service = WindowEnvironment.class)
public final class DefaultWindowEnvironment implements WindowEnvironment {

    private final Lookup services;
    private final Lookup.Result<DetachedBarsProvider> detachedBars;
    private final Lookup.Result<TabHomeResolver> homeResolvers;
    private final Lookup.Result<ProjectSwitcher> projectSwitchers;
    private final Lookup.Result<NavigatorRevealState> revealStates;
    private final Lookup.Result<NavigatorRevealer> revealers;
    private final Lookup.Result<WindowEnvironmentContribution> legacy;

    public DefaultWindowEnvironment() {
        this(Lookup.getDefault());
    }

    DefaultWindowEnvironment(Lookup services) {
        this.services = Objects.requireNonNull(services, "services");
        this.detachedBars = services.lookupResult(DetachedBarsProvider.class);
        this.homeResolvers = services.lookupResult(TabHomeResolver.class);
        this.projectSwitchers = services.lookupResult(ProjectSwitcher.class);
        this.revealStates = services.lookupResult(NavigatorRevealState.class);
        this.revealers = services.lookupResult(NavigatorRevealer.class);
        this.legacy = services.lookupResult(WindowEnvironmentContribution.class);
    }

    @Override
    public Region createDetachedBars(ObservableValue<org.netbeans.api.nbfx.editor.EditorDocument> activeDocument,
            List<Command> scopedCommands) {
        DetachedBarsContext context = new DetachedBarsContext(services, activeDocument, scopedCommands);
        for (DetachedBarsProvider provider : detachedBars.allInstances()) {
            Region bars = provider.createDetachedBars(context);
            if (bars != null) {
                return bars;
            }
        }
        for (WindowEnvironmentContribution contribution : legacy.allInstances()) {
            Region bars = contribution.createDetachedBars(context);
            if (bars != null) {
                return bars;
            }
        }
        return null;
    }

    @Override
    public boolean revealInNavigator(FileObject file, int index) {
        for (NavigatorRevealer revealer : revealers.allInstances()) {
            if (revealer.reveal(file, index)) {
                return true;
            }
        }
        for (WindowEnvironmentContribution contribution : legacy.allInstances()) {
            if (contribution.revealInNavigator(file, index)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean selectNextProject() {
        for (ProjectSwitcher switcher : projectSwitchers.allInstances()) {
            if (switcher.selectNext()) {
                return true;
            }
        }
        for (WindowEnvironmentContribution contribution : legacy.allInstances()) {
            if (contribution.selectNextProject()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean selectPreviousProject() {
        for (ProjectSwitcher switcher : projectSwitchers.allInstances()) {
            if (switcher.selectPrevious()) {
                return true;
            }
        }
        for (WindowEnvironmentContribution contribution : legacy.allInstances()) {
            if (contribution.selectPreviousProject()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TabPane homeOf(Tab tab) {
        for (TabHomeResolver resolver : homeResolvers.allInstances()) {
            TabPane home = resolver.homeOf(tab);
            if (home != null) {
                return home;
            }
        }
        for (WindowEnvironmentContribution contribution : legacy.allInstances()) {
            TabPane home = contribution.homeOf(tab);
            if (home != null) {
                return home;
            }
        }
        return null;
    }

    @Override
    public boolean isNavigatorRevealInProgress() {
        for (NavigatorRevealState state : revealStates.allInstances()) {
            if (state.isRevealing()) {
                return true;
            }
        }
        for (WindowEnvironmentContribution contribution : legacy.allInstances()) {
            if (contribution.isNavigatorRevealInProgress()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setNavigatorRevealInProgress(boolean inProgress) {
        revealStates.allInstances().forEach(state -> state.setRevealing(inProgress));
        legacy.allInstances().forEach(contribution -> contribution.setNavigatorRevealInProgress(inProgress));
    }
}
