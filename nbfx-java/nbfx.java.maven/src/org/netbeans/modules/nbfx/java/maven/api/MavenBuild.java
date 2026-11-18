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
package org.netbeans.modules.nbfx.java.maven.api;

import org.netbeans.modules.nbfx.project.ui.api.BuildExecution;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput.Kind;
import org.netbeans.modules.nbfx.project.ui.api.BuildProgress;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.netbeans.modules.maven.embedder.EmbedderFactory;
import org.netbeans.modules.maven.embedder.MavenEmbedder;

/**
 * One in-process Maven build via {@link MavenEmbedder#execute}: sets the goals, base directory and
 * properties on a {@link MavenExecutionRequest} and runs it on a background thread. Maven's Plexus
 * log output is routed through java.util.logging under the embedder's logger, so a {@link Handler}
 * on that logger forwards it to the {@link BuildOutput}, each message as the kind
 * {@link MavenOutput} gives it.
 *
 * @since 1.0
 */
final class MavenBuild implements BuildExecution {

    /** The embedder's JUL logger; Maven's Plexus logs are logged under it. */
    private static final String EMBEDDER_LOGGER = "org.netbeans.modules.maven.embedder.EmbedderFactory";

    private final Path dir;
    private final String[] goals;
    private final Map<String, String> properties;
    private final List<String> profiles;
    private final BuildOutput output;
    private final BuildProgress progress;

    MavenBuild(Path dir, String[] goals, Map<String, String> properties, List<String> profiles,
            BuildOutput output, BuildProgress progress) {
        this.dir = dir;
        this.goals = goals;
        this.properties = properties;
        this.profiles = profiles;
        this.output = output;
        this.progress = progress;
    }

    void start() {
        progress.start("maven", this::cancel);
        Thread thread = new Thread(this::run, "nbfx-maven-build");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        Logger logger = Logger.getLogger(EMBEDDER_LOGGER);
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getMessage() != null) {
                    output.append(record.getMessage() + "\n", MavenOutput.recordKind(record));
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Level previous = logger.getLevel();
        try {
            logger.addHandler(handler);
            logger.setLevel(Level.FINER);
            MavenEmbedder embedder = EmbedderFactory.getProjectEmbedder();
            MavenExecutionRequest request = embedder.createMavenExecutionRequest();
            configure(request, dir);
            request.setExecutionListener(new Progress());
            request.setGoals(List.of(goals));
            if (!profiles.isEmpty()) {
                request.setActiveProfiles(new ArrayList<>(profiles));
            }
            if (!properties.isEmpty()) {
                Properties userProperties = new Properties();
                userProperties.putAll(properties);
                request.setUserProperties(userProperties);
            }
            MavenExecutionResult result = embedder.execute(request);
            if (result.hasExceptions()) {
                output.append("\nBUILD FAILED\n", Kind.ERROR);
                for (Throwable failure : result.getExceptions()) {
                    output.append(String.valueOf(failure.getMessage()) + "\n", Kind.ERROR);
                }
            } else {
                output.append("\nBUILD SUCCESSFUL\n", Kind.SUCCESS);
            }
        } catch (Exception ex) {
            output.append("\nBUILD FAILED: " + ex + "\n", Kind.ERROR);
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
            progress.finish();
        }
    }

    /**
     * Writes what {@code mvn} prints as a build goes along and the embedder does not log: the
     * project being built and each plugin goal as it starts, in Maven's own words, so the output
     * reads as it does in a terminal and {@link MavenOutput} recognises the goal lines.
     */
    private final class Progress extends AbstractExecutionListener {

        @Override
        public void projectStarted(ExecutionEvent event) {
            MavenProject project = event.getProject();
            if (project != null) {
                output.append("\nBuilding " + project.getName() + " " + project.getVersion() + "\n");
            }
        }

        @Override
        public void mojoStarted(ExecutionEvent event) {
            MojoExecution mojo = event.getMojoExecution();
            if (mojo == null) {
                return;
            }
            // The plugin by its artifact id: its goal prefix is in a descriptor nbfx cannot see.
            StringBuilder line = new StringBuilder("--- ")
                    .append(mojo.getArtifactId())
                    .append(':').append(mojo.getVersion())
                    .append(':').append(mojo.getGoal());
            if (mojo.getExecutionId() != null) {
                line.append(" (").append(mojo.getExecutionId()).append(')');
            }
            MavenProject project = event.getProject();
            if (project != null) {
                line.append(" @ ").append(project.getArtifactId());
            }
            line.append(" ---");
            output.append("\n" + line + "\n", MavenOutput.lineKind(line.toString()));
        }
    }

    /**
     * Points {@code request} at the project in {@code dir}, as {@code mvn} run in that folder does:
     * its base directory, its {@code pom.xml} - without which Maven builds no project and every
     * goal needing one fails with "there is no POM in this directory" - and the root of its
     * {@code .mvn} configuration. A folder without a POM gets none, for the goals that need no
     * project.
     */
    static void configure(MavenExecutionRequest request, Path dir) {
        File folder = dir.toAbsolutePath().normalize().toFile();
        request.setBaseDirectory(folder);
        request.setMultiModuleProjectDirectory(folder);
        File pom = new File(folder, "pom.xml");
        if (pom.isFile()) {
            request.setPom(pom);
        }
    }

    @Override
    public void cancel() {
        // MavenEmbedder.execute has no cancellation hook; the build runs to completion.
    }
}
