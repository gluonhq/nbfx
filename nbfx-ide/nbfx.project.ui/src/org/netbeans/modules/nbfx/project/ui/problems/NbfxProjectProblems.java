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

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.ProjectProblemsImplementation;
import org.netbeans.spi.project.ui.ProjectProblemsProvider;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.ProjectProblem;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.Severity;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 * The JavaFX {@link ProjectProblemsImplementation}. It replaces the Swing
 * {@code BrokenReferencesImpl} in {@code projectui}; the upstream {@code ProjectProblems} API and
 * {@code ProjectProblemsProvider} SPI stay untouched.
 * <p>
 * The alert is coalesced: opening several broken projects in a row shows one dialog, and projects
 * added while it is open are picked up by the next round. The customizer resolves one problem at a
 * time through the provider and closes when nothing is left.
 */
@ServiceProvider(
        service = ProjectProblemsImplementation.class,
        position = 100,
        supersedes = "org.netbeans.modules.project.ui.problems.BrokenReferencesImpl")
public final class NbfxProjectProblems implements ProjectProblemsImplementation {

    private static final Logger LOG = Logger.getLogger(NbfxProjectProblems.class.getName());
    private static final RequestProcessor RP =
            new RequestProcessor(NbfxProjectProblems.class.getName(), 1);
    private static final int ALERT_DELAY = 700;

    private final Object lock = new Object();
    // @GuardedBy("lock")
    private AlertSession session;

    @Override
    public CompletableFuture<Void> showAlert(Project project) {
        Objects.requireNonNull(project, "project");
        if (ProjectProblemsSettings.isSuppressedByProperty()
                || !ProjectProblemsSettings.isShowAgain()
                || !hasError(project)) {
            return CompletableFuture.completedFuture(null);
        }

        AlertSession createdSession = null;
        CompletableFuture<Void> result;
        synchronized (lock) {
            if (session == null) {
                session = new AlertSession();
                createdSession = session;
            }
            session.projects.add(project);
            result = session.future;
        }
        if (createdSession != null) {
            try {
                RP.post(this::startAlert, ALERT_DELAY);
            } catch (RuntimeException ex) {
                LOG.log(Level.FINE, "Could not schedule the project problems alert", ex);
                endSession(createdSession);
            }
        }
        return result;
    }

    @Override
    public CompletableFuture<Void> showCustomizer(Project project) {
        Objects.requireNonNull(project, "project");
        if (Platform.isFxApplicationThread()) {
            try {
                showCustomizer(Set.of(project));
                return CompletableFuture.completedFuture(null);
            } catch (RuntimeException ex) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(ex);
                return failed;
            }
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            Platform.runLater(() -> {
                try {
                    showCustomizer(Set.of(project));
                    result.complete(null);
                } catch (Throwable ex) {
                    result.completeExceptionally(ex);
                }
            });
        } catch (IllegalStateException ex) {
            // No JavaFX toolkit (headless unit tests): there is no UI to show.
            LOG.log(Level.FINE, "No JavaFX toolkit; project problems customizer not shown", ex);
            result.complete(null);
        }
        return result;
    }

    private void startAlert() {
        AlertSession current;
        synchronized (lock) {
            current = session;
        }
        if (current == null) {
            return;
        }
        try {
            Platform.runLater(() -> runAlertSession(current));
        } catch (IllegalStateException ex) {
            LOG.log(Level.FINE, "No JavaFX toolkit; project problems alert not shown", ex);
            endSession(current);
        }
    }

    private void runAlertSession(AlertSession current) {
        try {
            while (true) {
                Set<Project> batch;
                synchronized (lock) {
                    if (session != current) {
                        return;
                    }
                    if (current.projects.isEmpty()) {
                        // Claim the end of the session while holding the lock: a concurrent
                        // showAlert() then starts a new session instead of adding to this one
                        // after its future has been completed.
                        session = null;
                        batch = Set.of();
                    } else {
                        batch = new LinkedHashSet<>(current.projects);
                        current.projects.clear();
                    }
                }
                if (batch.isEmpty()) {
                    current.future.complete(null);
                    return;
                }
                if (ProjectProblemsSettings.isSuppressedByProperty()
                        || !ProjectProblemsSettings.isShowAgain()) {
                    // The user unchecked "show this warning again" while an earlier alert was
                    // open: drop the queued projects as well.
                    continue;
                }
                if (showAlert(batch)) {
                    showCustomizer(batch);
                }
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Could not show project problems", ex);
            endSession(current);
        }
    }

    /** @return {@code true} when the user chose to open the resolver */
    private boolean showAlert(Set<Project> projects) {
        ProjectProblemsModel model = new ProjectProblemsModel(projects, false);
        try {
            if (model.getProblems().isEmpty()) {
                return false;
            }
            ProjectProblemsDialog dialog =
                    new ProjectProblemsDialog(ProjectProblemsUi.owner(), model);
            ButtonType result = dialog.showAndWait().orElse(null);
            return result == dialog.resolveButtonType();
        } finally {
            model.dispose();
        }
    }

    private void showCustomizer(Set<Project> projects) {
        ProjectProblemsModel model = new ProjectProblemsModel(projects, true);
        try {
            if (model.getProblems().isEmpty()) {
                return;
            }
            new ProjectProblemsCustomizer(ProjectProblemsUi.owner(), model).showAndWait();
        } finally {
            model.dispose();
        }
    }

    private void endSession(AlertSession current) {
        synchronized (lock) {
            if (session == current) {
                session = null;
            }
        }
        current.future.complete(null);
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
            for (ProjectProblem problem : problems) {
                if (problem.getSeverity() == Severity.ERROR) {
                    return true;
                }
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "Could not read the problems of " + project, ex);
        }
        return false;
    }

    private static final class AlertSession {
        private final Set<Project> projects = new LinkedHashSet<>();
        private final CompletableFuture<Void> future = new CompletableFuture<>();
    }
}
