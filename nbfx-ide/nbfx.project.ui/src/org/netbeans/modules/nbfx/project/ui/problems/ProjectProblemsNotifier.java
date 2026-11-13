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
package org.netbeans.modules.nbfx.project.ui.problems;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.api.project.ui.ProjectProblems;
import org.netbeans.spi.project.ui.ProjectProblemsProvider;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.Severity;
import org.openide.modules.OnStart;
import org.openide.modules.OnStop;
import org.openide.util.RequestProcessor;

/**
 * Raises the project problems alert when a broken project is opened. While {@code projectui} is
 * still present its {@code BrokenProjectNotifier} does the same; the nbfx alert session deduplicates
 * the two notifications. This notifier is the replacement needed when {@code projectui} is retired.
 */
@OnStart
public final class ProjectProblemsNotifier implements Runnable, PropertyChangeListener {

    private static final Logger LOG = Logger.getLogger(ProjectProblemsNotifier.class.getName());
    private static final RequestProcessor RP =
            new RequestProcessor(ProjectProblemsNotifier.class.getName(), 1);
    private static final ProjectProblemsNotifier INSTANCE = new ProjectProblemsNotifier();

    @Override
    public void run() {
        OpenProjects.getDefault().addPropertyChangeListener(INSTANCE);
    }

    @OnStop
    public static final class Stop implements Runnable {
        @Override
        public void run() {
            OpenProjects.getDefault().removePropertyChangeListener(INSTANCE);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (!OpenProjects.PROPERTY_OPEN_PROJECTS.equals(event.getPropertyName())) {
            return;
        }
        Object oldValue = event.getOldValue();
        Object newValue = event.getNewValue();
        if (!(oldValue instanceof Project[] oldProjects)
                || !(newValue instanceof Project[] newProjects)) {
            return;
        }
        Set<Project> added = new LinkedHashSet<>(Arrays.asList(newProjects));
        added.removeAll(Arrays.asList(oldProjects));
        if (added.isEmpty()) {
            return;
        }
        RP.post(() -> {
            for (Project project : added) {
                if (project != null && hasError(project)) {
                    ProjectProblems.showAlert(project);
                }
            }
        });
    }

    private static boolean hasError(Project project) {
        ProjectProblemsProvider provider =
                project.getLookup().lookup(ProjectProblemsProvider.class);
        if (provider == null) {
            return false;
        }
        try {
            var problems = provider.getProblems();
            if (problems == null) {
                return false;
            }
            for (ProjectProblemsProvider.ProjectProblem problem : problems) {
                if (problem.getSeverity() == Severity.ERROR) {
                    return true;
                }
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "Could not read the problems of " + project, ex);
        }
        return false;
    }
}
