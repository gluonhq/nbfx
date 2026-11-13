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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.spi.project.ui.ProjectProblemsProvider;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.ProjectProblem;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.Severity;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

/**
 * Swing-free model of the problems reported by the {@link ProjectProblemsProvider}s of one or more
 * projects. It replaces the Swing {@code BrokenReferencesModel}: the dialog classes observe its
 * JavaFX list and never touch the providers directly.
 * <p>
 * Provider reads run on a background thread; only the resulting list and the {@code loaded} flag are
 * applied on the JavaFX thread. Provider property changes schedule another background read, so the
 * JavaFX thread is never blocked on project metadata. The model must be {@link #dispose() disposed}
 * when the dialog closes so its listeners stop refreshing a gone UI.
 * <p>
 * It calls {@link ProjectProblemsProvider#getProblems()} directly instead of taking
 * {@code ProjectManager.mutex().readAccess()} as the Swing model did: on macOS the JavaFX thread
 * is the AppKit main thread, and blocking it on a project lock that may need the AWT event thread
 * is exactly the deadlock this port avoids. Providers already own their synchronization and are
 * called from the upstream notifier's background thread.
 */
final class ProjectProblemsModel implements PropertyChangeListener {

    private static final Logger LOG = Logger.getLogger(ProjectProblemsModel.class.getName());
    private static final RequestProcessor RP =
            new RequestProcessor(ProjectProblemsModel.class.getName(), 1);

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
    private final List<Project> projects = new CopyOnWriteArrayList<>();
    private final Map<ProjectProblemsProvider, PropertyChangeListener> listeners = new LinkedHashMap<>();
    private final boolean includeWarnings;
    private final CompletableFuture<Void> loaded = new CompletableFuture<>();
    private final ReadOnlyBooleanWrapper loadedProperty =
            new ReadOnlyBooleanWrapper(this, "loaded", false);
    private final AtomicInteger generation = new AtomicInteger();
    private volatile boolean disposed;

    /**
     * @param projects the projects whose problems to expose
     * @param includeWarnings {@code false} for the alert (errors only), {@code true} for the
     *     resolver, which also shows warnings that can be fixed
     */
    ProjectProblemsModel(Collection<? extends Project> projects, boolean includeWarnings) {
        this.projects.addAll(projects);
        this.includeWarnings = includeWarnings;
        listenToProviders();
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

    /** Whether the first background read has been applied. */
    boolean isLoaded() {
        return loadedProperty.get();
    }

    /** The {@code loaded} flag, set on the JavaFX thread when the first read is applied. */
    ReadOnlyBooleanProperty loadedProperty() {
        return loadedProperty.getReadOnlyProperty();
    }

    /** Completes on the JavaFX thread after the first read is applied. */
    CompletableFuture<Void> loaded() {
        return loaded;
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
     * Schedules a background reload of the problems. Safe to call from any thread; the list is
     * only mutated on the JavaFX thread. The returned future completes on the JavaFX thread when
     * this generation has been applied (or discarded because a newer read superseded it).
     */
    CompletableFuture<Void> refresh() {
        if (disposed) {
            return CompletableFuture.completedFuture(null);
        }
        int current = generation.incrementAndGet();
        CompletableFuture<Void> done = new CompletableFuture<>();
        RP.post(() -> {
            List<ProblemItem> next;
            try {
                next = readProblems();
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Could not read the project problems", ex);
                next = List.of();
            }
            List<ProblemItem> items = next;
            try {
                Platform.runLater(() -> apply(current, items, done));
            } catch (IllegalStateException ex) {
                // No JavaFX toolkit (headless tests, command line helpers): nothing to update.
                LOG.log(Level.FINE, "No JavaFX toolkit; project problems model not updated", ex);
                loaded.complete(null);
                done.complete(null);
            }
        });
        return done;
    }

    private void apply(int generationId, List<ProblemItem> next, CompletableFuture<Void> done) {
        if (disposed || generationId != generation.get()) {
            done.complete(null);
            return;
        }
        if (!problems.equals(next)) {
            problems.setAll(next);
        }
        loadedProperty.set(true);
        loaded.complete(null);
        done.complete(null);
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

    private void listenToProviders() {
        for (Project project : projects) {
            ProjectProblemsProvider provider =
                    project.getLookup().lookup(ProjectProblemsProvider.class);
            if (provider != null) {
                listenTo(provider);
            }
        }
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
        generation.incrementAndGet();
        loaded.complete(null);
        listeners.forEach(ProjectProblemsProvider::removePropertyChangeListener);
        listeners.clear();
        projects.clear();
        problems.clear();
    }
}
