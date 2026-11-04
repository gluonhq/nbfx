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
import org.netbeans.modules.nbfx.platform.shell.api.WindowEnvironmentContribution;

import java.util.List;
import java.util.Objects;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.modules.nbfx.platform.workspace.WindowEnvironment;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * The generic window environment: it delegates the host-specific operations to
 * {@link WindowEnvironmentContribution}s and provides a neutral fallback for detached-window
 * chrome.
 *
 * @since 1.0
 */
@ServiceProvider(service = WindowEnvironment.class)
public final class DefaultWindowEnvironment implements WindowEnvironment {

    private final Lookup services;
    private final List<WindowEnvironmentContribution> contributions;

    public DefaultWindowEnvironment() {
        this(Lookup.getDefault());
    }

    DefaultWindowEnvironment(Lookup services) {
        this.services = Objects.requireNonNull(services, "services");
        this.contributions = List.copyOf(services.lookupAll(WindowEnvironmentContribution.class));
    }

    @Override
    public Region createDetachedBars(ObservableValue<org.netbeans.api.nbfx.editor.EditorDocument> activeDocument,
            List<Command> scopedCommands) {
        DetachedBarsContext context = new DetachedBarsContext(services, activeDocument, scopedCommands);
        for (WindowEnvironmentContribution contribution : contributions()) {
            Region bars = contribution.createDetachedBars(context);
            if (bars != null) {
                return bars;
            }
        }
        return null;
    }

    @Override
    public boolean revealInNavigator(FileObject file, int index) {
        for (WindowEnvironmentContribution contribution : contributions()) {
            if (contribution.revealInNavigator(file, index)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean selectNextProject() {
        for (WindowEnvironmentContribution contribution : contributions()) {
            if (contribution.selectNextProject()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean selectPreviousProject() {
        for (WindowEnvironmentContribution contribution : contributions()) {
            if (contribution.selectPreviousProject()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TabPane homeOf(Tab tab) {
        for (WindowEnvironmentContribution contribution : contributions()) {
            TabPane home = contribution.homeOf(tab);
            if (home != null) {
                return home;
            }
        }
        return null;
    }

    @Override
    public boolean isNavigatorRevealInProgress() {
        for (WindowEnvironmentContribution contribution : contributions()) {
            if (contribution.isNavigatorRevealInProgress()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setNavigatorRevealInProgress(boolean inProgress) {
        for (WindowEnvironmentContribution contribution : contributions()) {
            contribution.setNavigatorRevealInProgress(inProgress);
        }
    }

    private List<WindowEnvironmentContribution> contributions() {
        return contributions;
    }
}
