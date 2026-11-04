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

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import org.netbeans.api.nbfx.project.ProjectLifecycle;
import org.netbeans.api.nbfx.view.NavigatorRevealer;
import org.netbeans.modules.nbfx.platform.shell.api.DetachedBarsContext;
import org.netbeans.modules.nbfx.platform.shell.api.DetachedBarsProvider;
import org.netbeans.modules.nbfx.platform.shell.api.NavigatorRevealState;
import org.netbeans.modules.nbfx.platform.shell.api.ProjectSwitcher;
import org.netbeans.modules.nbfx.platform.shell.api.TabHomeResolver;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

/**
 * The IDE's window-environment contribution. It is stateless: every operation delegates to the
 * running shell, which owns the navigators, detached bars, tab homes and reveal state.
 */
@ServiceProviders({
    @ServiceProvider(service = DetachedBarsProvider.class),
    @ServiceProvider(service = TabHomeResolver.class),
    @ServiceProvider(service = ProjectSwitcher.class),
    @ServiceProvider(service = NavigatorRevealState.class),
    @ServiceProvider(service = NavigatorRevealer.class)
})
public final class WindowEnvironmentService implements DetachedBarsProvider, TabHomeResolver,
        ProjectSwitcher, NavigatorRevealState, NavigatorRevealer {

    @Override
    public Region createDetachedBars(DetachedBarsContext context) {
        JavaFXLaunchApp app = JavaFXLaunchApp.instance();
        return app == null ? null : app.createDetachedBars(context);
    }

    @Override
    public boolean reveal(FileObject file) {
        JavaFXLaunchApp app = JavaFXLaunchApp.instance();
        return app != null && file != null && app.revealFileInOwningNavigator(file);
    }

    @Override
    public boolean reveal(FileObject file, int index) {
        JavaFXLaunchApp app = JavaFXLaunchApp.instance();
        if (app == null || file == null) {
            return false;
        }
        app.revealFileInNavigator(file, index);
        return true;
    }

    @Override
    public boolean selectNext() {
        ProjectLifecycle lifecycle = lookupLifecycle();
        if (lifecycle == null) {
            return false;
        }
        lifecycle.selectNext();
        return true;
    }

    @Override
    public boolean selectPrevious() {
        ProjectLifecycle lifecycle = lookupLifecycle();
        if (lifecycle == null) {
            return false;
        }
        lifecycle.selectPrevious();
        return true;
    }

    @Override
    public TabPane homeOf(Tab tab) {
        JavaFXLaunchApp app = JavaFXLaunchApp.instance();
        return app == null ? null : app.homeOf(tab);
    }

    @Override
    public boolean isRevealing() {
        JavaFXLaunchApp app = JavaFXLaunchApp.instance();
        return app != null && app.isNavigatorRevealInProgress();
    }

    @Override
    public void setRevealing(boolean revealing) {
        JavaFXLaunchApp app = JavaFXLaunchApp.instance();
        if (app != null) {
            app.setNavigatorRevealInProgress(revealing);
        }
    }

    private static ProjectLifecycle lookupLifecycle() {
        return Lookup.getDefault().lookup(ProjectLifecycle.class);
    }
}
