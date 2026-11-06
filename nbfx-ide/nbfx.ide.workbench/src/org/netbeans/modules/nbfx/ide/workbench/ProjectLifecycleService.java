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

import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.api.nbfx.project.ProjectLifecycle;
import org.openide.util.lookup.ServiceProvider;

/**
 * Implements {@link ProjectLifecycle} by delegating to the running IDE shell, which owns the
 * navigators, loading, session state and unsaved-document confirmation.
 *
 * @since 1.0
 */
@ServiceProvider(service = ProjectLifecycle.class)
public final class ProjectLifecycleService implements ProjectLifecycle {

    @Override
    public void close(OpenProject project) {
        JavaFXLaunchApp app = JavaFXLaunchApp.instance();
        if (app != null) {
            app.closeProject(project);
        }
    }

    @Override
    public void closeAll() {
        JavaFXLaunchApp app = JavaFXLaunchApp.instance();
        if (app != null) {
            app.closeAllProjects();
        }
    }

    @Override
    public void selectNext() {
        JavaFXLaunchApp app = JavaFXLaunchApp.instance();
        if (app != null) {
            app.selectNextProject();
        }
    }

    @Override
    public void selectPrevious() {
        JavaFXLaunchApp app = JavaFXLaunchApp.instance();
        if (app != null) {
            app.selectPreviousProject();
        }
    }
}
