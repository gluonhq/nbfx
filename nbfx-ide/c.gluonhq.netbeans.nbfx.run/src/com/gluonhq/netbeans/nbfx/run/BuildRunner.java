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
package com.gluonhq.netbeans.nbfx.run;

import com.gluonhq.netbeans.nbfx.output.FxConsole;
import com.gluonhq.netbeans.nbfx.output.FxOutput;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildActionProvider;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildExecution;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildOutput;
import com.gluonhq.netbeans.nbfx.project.ui.api.BuildProgress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Runs a build action for a project directory. It first asks the project-type
 * {@link BuildActionProvider} to run the command in-process (streaming to the Fx console); when the
 * provider does not support in-process execution, it falls back to the CLI runner. Shared by the
 * Build menu command and the navigator context menu.
 * <p>
 * The running build is reported as an ordinary cancellable system task through the progress API, so
 * the status line and the running-tasks popup show it without any bespoke UI.
 *
 * @since 1.0
 */
public final class BuildRunner {

    private static final Logger LOG = Logger.getLogger(BuildRunner.class.getName());

    private BuildRunner() {
    }

    public static void run(Path dir, String command, String consoleName) {
        if (dir == null) {
            return;
        }
        FxOutput output = Lookup.getDefault().lookup(FxOutput.class);
        if (output == null) {
            LOG.warning("No FxOutput service; build output cannot be shown");
            return;
        }
        FxConsole console = output.console(consoleName);
        console.clear();
        console.show();

        AtomicReference<Runnable> cancelAction = new AtomicReference<>();
        ProgressHandle handle = ProgressHandle.createSystemHandle(consoleName, () -> {
            Runnable cancel = cancelAction.get();
            if (cancel == null) {
                return false;
            }
            cancel.run();
            return true;
        });
        BuildProgress progress = new BuildProgress() {
            @Override
            public void start(String displayName, Runnable cancel) {
                if (cancel != null) {
                    cancelAction.set(cancel);
                }
                handle.setDisplayName(displayName);
            }

            @Override
            public void finish() {
                handle.finish();
            }
        };
        handle.start();

        BuildActionProvider provider = BuildActions.providerFor(dir);
        LOG.info(() -> "Build " + command + " in " + dir + " via "
                + (provider == null ? "CLI" : provider.getClass().getName()));
        if (provider != null) {
            try {
                BuildExecution execution = provider.start(dir, command, output(console), progress);
                if (execution != null) {
                    return;
                }
                LOG.info(() -> provider.getClass().getName() + " has no in-process executor; using CLI");
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "In-process build failed; falling back to CLI", t);
                console.append("\n[in-process build failed: " + t + "]\n");
            }
        }
        runCli(dir, command, consoleName, console, progress);
    }

    /** The CLI fallback: detect the tool from marker files and run it as a process. */
    private static void runCli(Path dir, String command, String consoleName, FxConsole console, BuildProgress progress) {
        List<String> commandLine = BuildTool.detect(dir).commandLine(dir, command);
        if (commandLine == null) {
            BuildActionProvider provider = BuildActions.providerFor(dir);
            if (provider != null) {
                commandLine = provider.commandLine(dir, command);
            }
        }
        if (commandLine == null) {
            console.append(NbBundle.getMessage(BuildRunner.class, "BuildCommand.noTool") + "\n");
            progress.finish();
            return;
        }
        AtomicReference<Process> process = new AtomicReference<>();
        progress.start(consoleName, () -> {
            Process running = process.get();
            if (running != null) {
                running.destroy();
            }
        });
        List<String> line = commandLine;
        ProcessRunner.run(line, dir, console, process::set, progress::finish);
    }

    private static BuildOutput output(FxConsole console) {
        return new BuildOutput() {
            @Override
            public void clear() {
                console.clear();
            }

            @Override
            public void show() {
                console.show();
            }

            @Override
            public void append(String text) {
                console.append(text);
            }
        };
    }
}
