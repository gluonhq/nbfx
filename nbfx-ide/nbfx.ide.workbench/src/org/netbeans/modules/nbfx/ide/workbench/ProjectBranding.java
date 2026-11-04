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

import org.netbeans.modules.nbfx.platform.shell.api.ShellContext;
import org.netbeans.modules.nbfx.platform.shell.api.ShellBranding;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.util.Subscription;
import org.netbeans.api.nbfx.project.ProjectRegistry;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * The IDE branding: names the application and keeps the window title in step with the selected
 * project. It takes precedence over the neutral platform default.
 */
@ServiceProvider(service = ShellBranding.class)
public final class ProjectBranding implements ShellBranding {

    private final ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
    private final StringProperty title = new SimpleStringProperty(applicationName());
    private Subscription subscription;

    public ProjectBranding() {
        if (registry != null) {
            title.set(WindowTitles.of(registry.getSelected()));
            subscription = registry.selectedProjectProperty()
                    .subscribe(project -> title.set(WindowTitles.of(project)));
        }
    }

    @Override
    public void dispose() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public String applicationName() {
        return WindowTitles.APP_NAME;
    }

    @Override
    public String title(ShellContext context) {
        return WindowTitles.of(registry == null ? null : registry.getSelected());
    }

    @Override
    public ObservableValue<String> titleProperty(ShellContext context) {
        return title;
    }
}
