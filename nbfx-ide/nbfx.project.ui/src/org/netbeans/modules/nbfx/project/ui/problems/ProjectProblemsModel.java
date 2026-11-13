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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.spi.project.ui.ProjectProblemsProvider;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.ProjectProblem;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.Severity;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;

/**
 * Swing-free model of the problems reported by the {@link ProjectProblemsProvider}s of one or more
 * projects. It replaces the Swing {@code BrokenReferencesModel}: the dialog classes observe its
 * JavaFX list and never touch the providers directly.
 * <p>
 * The model is confined to the JavaFX thread. Provider property changes are marshalled there with
 * {@link Platform#runLater(Runnable)}; the model must be {@link #dispose() disposed} when the
 * dialog closes so its listeners stop refreshing a gone UI.
 * <p>
 * It calls {@link ProjectProblemsProvider#getProblems()} directly instead of taking
 * {@code ProjectManager.mutex().readAccess()} as the Swing model did: on macOS the JavaFX thread
 * is the AppKit main thread, and blocking it on a project lock that may need the AWT event thread
 * is exactly the deadlock this port avoids. Providers already own their synchronization and are
 * called from the upstream notifier's background thread.
 */
final class ProjectProblemsModel implements PropertyChangeListener {

    private static final Logger LOG = Logger.getLogger(ProjectProblemsModel.class.getName());

    /** One problem and the project that reports it. */
    record ProblemItem(Project project, ProjectProblem problem) {

        boolean isError() {
            return problem.getSeverity() == Severity.ERROR;
        }

        boolean isResolvable() {
            return problem.isResolvable();
        }

        String displayName(boolean includeProject) {
            String name = problem.getDisplayName();
            if (!includeProject) {
                return name;
            }
            return NbBundle.getMessage(ProjectProblemsModel.class, "FMT_ProblemInProject",
                    projectName(project), name);
        }

        String description() {
            return problem.getDescription();
        }

        static String projectName(Project project) {
            try {
                String name = ProjectUtils.getInformation(project).getDisplayName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            } catch (RuntimeException ex) {
                // A unit test or a plain folder project has no ProjectInformationProvider.
                LOG.log(Level.FINE, "Could not resolve the project display name", ex);
            }
            return project.getProjectDirectory().getNameExt();
        }
    }

    private final ObservableList<ProblemItem> problems = FXCollections.observableArrayList();
    private final ObservableList<ProblemItem> readOnlyProblems =
            FXCollections.unmodifiableObservableList(problems);
    private final List<Project> projects = new ArrayList<>();
    private final Map<ProjectProblemsProvider, PropertyChangeListener> listeners = new LinkedHashMap<>();
    private final boolean includeWarnings;
    private boolean disposed;
    private boolean refreshing;

    /**
     * @param projects the projects whose problems to expose
     * @param includeWarnings {@code false} for the alert (errors only), {@code true} for the
     *     resolver, which also shows warnings that can be fixed
     */
    ProjectProblemsModel(Collection<? extends Project> projects, boolean includeWarnings) {
        this.projects.addAll(projects);
        this.includeWarnings = includeWarnings;
        refresh();
    }

    /** The problems, in provider and project order; an unmodifiable JavaFX observable list. */
    ObservableList<ProblemItem> getProblems() {
        return readOnlyProblems;
    }

    /** The projects this model was created for. */
    List<Project> getProjects() {
        return List.copyOf(projects);
    }

    /** Whether at least one problem is an error. */
    boolean hasErrors() {
        return problems.stream().anyMatch(ProblemItem::isError);
    }

    /** Whether more than one project contributes problems; the UI then names the project per row. */
    boolean hasMultipleProjects() {
        long count = problems.stream().map(item -> item.project()).distinct().count();
        return count > 1;
    }

    /**
     * Reloads the problems from the project providers. Safe to call from any thread; the list is
     * only mutated on the JavaFX thread.
     */
    void refresh() {
        if (disposed) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            try {
                Platform.runLater(this::refresh);
            } catch (IllegalStateException ex) {
                // No JavaFX toolkit (headless tests, command line helpers): nothing to refresh.
                LOG.log(Level.FINE, "No JavaFX toolkit; project problems model not refreshed", ex);
            }
            return;
        }
        if (refreshing) {
            return;
        }
        refreshing = true;
        try {
            List<ProblemItem> next = readProblems();
            if (!disposed && !problems.equals(next)) {
                problems.setAll(next);
            }
        } finally {
            refreshing = false;
        }
    }

    private List<ProblemItem> readProblems() {
        List<ProblemItem> result = new ArrayList<>();
        Set<ProblemItem> seen = new HashSet<>();
        for (Project project : projects) {
            ProjectProblemsProvider provider =
                    project.getLookup().lookup(ProjectProblemsProvider.class);
            if (provider == null) {
                continue;
            }
            listenTo(provider);
            Collection<? extends ProjectProblem> reported;
            try {
                reported = provider.getProblems();
            } catch (RuntimeException ex) {
                // A broken provider must not hide the problems of the other projects.
                LOG.log(Level.WARNING, "Could not read the problems of " + project, ex);
                continue;
            }
            if (reported == null) {
                continue;
            }
            for (ProjectProblem problem : reported) {
                if (!includeWarnings && problem.getSeverity() != Severity.ERROR) {
                    continue;
                }
                ProblemItem item = new ProblemItem(project, problem);
                if (seen.add(item)) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private void listenTo(ProjectProblemsProvider provider) {
        if (listeners.containsKey(provider)) {
            return;
        }
        PropertyChangeListener listener = WeakListeners.propertyChange(this, provider);
        provider.addPropertyChangeListener(listener);
        listeners.put(provider, listener);
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (ProjectProblemsProvider.PROP_PROBLEMS.equals(event.getPropertyName())) {
            refresh();
        }
    }

    /** Stops listening to the providers and releases the list. Call on the JavaFX thread. */
    void dispose() {
        disposed = true;
        listeners.forEach(ProjectProblemsProvider::removePropertyChangeListener);
        listeners.clear();
        projects.clear();
        problems.clear();
    }
}
