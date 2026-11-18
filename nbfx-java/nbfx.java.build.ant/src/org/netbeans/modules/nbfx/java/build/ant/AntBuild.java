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
package org.netbeans.modules.nbfx.java.build.ant;

import org.netbeans.modules.nbfx.project.ui.api.BuildExecution;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput.Kind;
import org.netbeans.modules.nbfx.project.ui.api.BuildProgress;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;

/**
 * One in-process Ant build: configures the {@code build.xml} project, streams Ant's messages into a
 * {@link BuildOutput} - target names muted, errors and warnings as errors, the result as a success
 * or an error - and runs the requested targets on a background thread. Cancellation sets a
 * flag that the {@link BuildListener} checks to abort.
 *
 * @since 1.0
 */
final class AntBuild implements BuildExecution {

    private final Path dir;
    private final String[] targets;
    private final BuildOutput output;
    private final BuildProgress progress;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    AntBuild(Path dir, String[] targets, BuildOutput output, BuildProgress progress) {
        this.dir = dir;
        this.targets = targets;
        this.output = output;
        this.progress = progress;
    }

    void start() {
        progress.start("ant", this::cancel);
        Thread thread = new Thread(this::run, "nbfx-ant-build");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        try {
            File buildFile = dir.resolve("build.xml").toFile();
            Project project = new Project();
            project.setBaseDir(dir.toFile());
            project.init();
            // Ant's Main sets these before parsing; ProjectHelper.configureProject does not, and the
            // harness build files (projectized.xml) read ${ant.file} / ${ant.project.name} at load time.
            project.setUserProperty("ant.file", buildFile.getAbsolutePath());
            project.addBuildListener(new Listener(output, cancelled));
            ProjectHelper helper = ProjectHelper.getProjectHelper();
            project.addReference(ProjectHelper.PROJECTHELPER_REFERENCE, helper);
            helper.parse(project, buildFile);
            for (String target : targets) {
                if (cancelled.get()) {
                    break;
                }
                project.executeTarget(target);
            }
            if (cancelled.get()) {
                output.append("\nBUILD CANCELLED\n", Kind.ERROR);
            } else {
                output.append("\nBUILD SUCCESSFUL\n", Kind.SUCCESS);
            }
        } catch (BuildException ex) {
            output.append("\nBUILD FAILED: " + ex.getMessage() + "\n", Kind.ERROR);
        } catch (RuntimeException ex) {
            output.append("\nBUILD ERROR: " + ex + "\n", Kind.ERROR);
        } finally {
            progress.finish();
        }
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    /** Forwards Ant's log messages to the output and aborts when cancelled. */
    private static final class Listener implements BuildListener {

        private final BuildOutput output;
        private final AtomicBoolean cancelled;

        Listener(BuildOutput output, AtomicBoolean cancelled) {
            this.output = output;
            this.cancelled = cancelled;
        }

        @Override
        public void buildStarted(BuildEvent event) {
        }

        @Override
        public void buildFinished(BuildEvent event) {
        }

        @Override
        public void targetStarted(BuildEvent event) {
            check();
            output.append("\n" + event.getTarget().getName() + ":\n", Kind.MUTED);
        }

        @Override
        public void targetFinished(BuildEvent event) {
            check();
        }

        @Override
        public void taskStarted(BuildEvent event) {
            check();
        }

        @Override
        public void taskFinished(BuildEvent event) {
            check();
        }

        @Override
        public void messageLogged(BuildEvent event) {
            check();
            if (event.getPriority() > Project.MSG_INFO) {
                return;
            }
            String message = event.getMessage();
            if (message != null && !message.isBlank()) {
                output.append(message + "\n", kindOf(event));
            }
        }

        /**
         * Errors and warnings in the error colour, as NetBeans, which sends both to the error
         * stream: Ant logs the error stream of what it runs (a {@code java} task) and the
         * compiler's messages as warnings. An {@code echo} is plain text whatever its level,
         * which is the warning level unless the build file says otherwise.
         */
        private static Kind kindOf(BuildEvent event) {
            if (event.getPriority() > Project.MSG_WARN) {
                return Kind.NORMAL;
            }
            boolean echo = event.getTask() != null && "echo".equals(event.getTask().getTaskName());
            return echo && event.getPriority() == Project.MSG_WARN ? Kind.NORMAL : Kind.ERROR;
        }

        private void check() {
            if (cancelled.get()) {
                throw new BuildException("Build cancelled");
            }
        }
    }
}
