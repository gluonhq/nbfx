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
import javafx.stage.Window;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.ProjectProblemsImplementation;
import org.netbeans.spi.project.ui.ProjectProblemsProvider;
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
    private final ProjectProblemsPresenter presenter;
    private final int alertDelay;
    // @GuardedBy("lock")
    private AlertSession session;

    public NbfxProjectProblems() {
        this(new DefaultPresenter(), ALERT_DELAY);
    }

    NbfxProjectProblems(ProjectProblemsPresenter presenter, int alertDelayMillis) {
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.alertDelay = Math.max(0, alertDelayMillis);
    }

    @Override
    public CompletableFuture<Void> showAlert(Project project) {
        Objects.requireNonNull(project, "project");
        if (ProjectProblemsSettings.isSuppressedByProperty()
                || !ProjectProblemsSettings.isShowAgain()
                || project.getLookup().lookup(ProjectProblemsProvider.class) == null) {
            // No provider is a cheap lookup, not a provider read: complete without a session.
            return CompletableFuture.completedFuture(null);
        }

        AlertSession createdSession = null;
        CompletableFuture<Void> result;
        synchronized (lock) {
            if (session == null) {
                session = new AlertSession();
                createdSession = session;
            }
            // The upstream BrokenProjectNotifier and the nbfx notifier may both report the same
            // project; alert only once per session.
            if (session.seen.add(project)) {
                session.projects.add(project);
            }
            result = session.future;
        }
        if (createdSession != null) {
            try {
                RP.post(this::startAlert, alertDelay);
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
        return showCustomizer(Set.of(project));
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
        showNextAlert(current);
    }

    private void showNextAlert(AlertSession current) {
        try {
            nextAlert(current);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Could not show project problems", ex);
            endSession(current);
        }
    }

    private void nextAlert(AlertSession current) {
        Set<Project> batch;
        synchronized (lock) {
            if (session != current) {
                return;
            }
            if (current.projects.isEmpty()) {
                // Claim the end of the session while holding the lock: a concurrent
                // showAlert() then starts a new session instead of adding to this one after its
                // future has been completed.
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
            // The user unchecked "show this warning again" while an earlier alert was open:
            // drop the queued projects as well.
            showNextAlert(current);
            return;
        }
        showAlert(batch).whenComplete((resolve, failure) -> {
            if (failure != null) {
                LOG.log(Level.WARNING, "Could not show the project problems alert", failure);
                showNextAlert(current);
                return;
            }
            if (Boolean.TRUE.equals(resolve)) {
                showCustomizer(batch).whenComplete((ignored, customizerFailure) -> {
                    if (customizerFailure != null) {
                        LOG.log(Level.WARNING,
                                "Could not show the project problems resolver", customizerFailure);
                    }
                    showNextAlert(current);
                });
            } else {
                showNextAlert(current);
            }
        });
    }

    /** @return a future completing with the user's choice to open the resolver */
    private CompletableFuture<Boolean> showAlert(Set<Project> projects) {
        ProjectProblemsModel model = new ProjectProblemsModel(projects, false);
        return model.loaded().thenCompose(ignored -> {
            if (model.getProblems().isEmpty()) {
                model.dispose();
                return CompletableFuture.completedFuture(false);
            }
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            Runnable show = () -> {
                try {
                    result.complete(presenter.showAlert(ProjectProblemsUi.owner(), model));
                } catch (RuntimeException ex) {
                    result.completeExceptionally(ex);
                } finally {
                    model.dispose();
                }
            };
            try {
                if (Platform.isFxApplicationThread()) {
                    show.run();
                } else {
                    Platform.runLater(show);
                }
            } catch (IllegalStateException ex) {
                LOG.log(Level.FINE, "No JavaFX toolkit; project problems alert not shown", ex);
                model.dispose();
                result.complete(false);
            }
            return result;
        });
    }

    private CompletableFuture<Void> showCustomizer(Set<Project> projects) {
        ProjectProblemsModel model = new ProjectProblemsModel(projects, true);
        CompletableFuture<Void> result = new CompletableFuture<>();
        Runnable show = () -> {
            try {
                presenter.showCustomizer(ProjectProblemsUi.owner(), model);
                result.complete(null);
            } catch (RuntimeException ex) {
                result.completeExceptionally(ex);
            } finally {
                model.dispose();
            }
        };
        try {
            if (Platform.isFxApplicationThread()) {
                show.run();
            } else {
                Platform.runLater(show);
            }
        } catch (IllegalStateException ex) {
            LOG.log(Level.FINE, "No JavaFX toolkit; project problems customizer not shown", ex);
            model.dispose();
            result.complete(null);
        }
        return result;
    }

    private void endSession(AlertSession current) {
        synchronized (lock) {
            if (session == current) {
                session = null;
            }
        }
        current.future.complete(null);
    }

    private static final class DefaultPresenter implements ProjectProblemsPresenter {

        @Override
        public boolean showAlert(Window owner, ProjectProblemsModel model) {
            ProjectProblemsDialog dialog = new ProjectProblemsDialog(owner, model);
            ButtonType result = dialog.showAndWait().orElse(null);
            return result == dialog.resolveButtonType();
        }

        @Override
        public void showCustomizer(Window owner, ProjectProblemsModel model) {
            new ProjectProblemsCustomizer(owner, model).showAndWait();
        }
    }

    private static final class AlertSession {
        private final Set<Project> projects = new LinkedHashSet<>();
        private final Set<Project> seen = new LinkedHashSet<>();
        private final CompletableFuture<Void> future = new CompletableFuture<>();
    }
}
