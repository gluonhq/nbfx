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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.ProjectProblemResolver;
import org.netbeans.spi.project.ui.ProjectProblemsProvider;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.ProjectProblem;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.Result;
import org.netbeans.spi.project.ui.ProjectProblemsProvider.Status;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * A small project fixture for the project problems tests: a folder plus a lookup holding one
 * {@link ProjectProblemsProvider}.
 */
final class TestProject implements Project {

    private final FileObject directory;
    private final Lookup lookup;

    TestProject(FileObject directory, ProjectProblemsProvider provider) {
        this.directory = directory;
        this.lookup = provider == null ? Lookup.EMPTY : Lookups.fixed(provider);
    }

    @Override
    public FileObject getProjectDirectory() {
        return directory;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    /** A provider whose problems can be replaced, firing {@code PROP_PROBLEMS}. */
    static final class Provider implements ProjectProblemsProvider {

        private final PropertyChangeSupport support = new PropertyChangeSupport(this);
        private volatile List<ProjectProblem> problems = List.of();

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            support.addPropertyChangeListener(listener);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            support.removePropertyChangeListener(listener);
        }

        @Override
        public Collection<? extends ProjectProblem> getProblems() {
            return problems;
        }

        void setProblems(List<ProjectProblem> newProblems) {
            problems = List.copyOf(newProblems);
            support.firePropertyChange(PROP_PROBLEMS, null, null);
        }
    }

    /** A resolver that completes immediately with a fixed result. */
    static final class ImmediateResolver implements ProjectProblemResolver {

        private final Status status;
        private final String message;

        ImmediateResolver(Status status, String message) {
            this.status = status;
            this.message = message;
        }

        @Override
        public Future<Result> resolve() {
            return CompletableFuture.completedFuture(
                    message == null ? Result.create(status) : Result.create(status, message));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ImmediateResolver resolver && resolver.status == status;
        }

        @Override
        public int hashCode() {
            return status.hashCode();
        }
    }
}
