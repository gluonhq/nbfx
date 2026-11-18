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
package org.netbeans.modules.nbfx.ide.build;

import org.netbeans.api.nbfx.progress.Progress;
import org.netbeans.api.nbfx.output.ConsolePanel;
import org.netbeans.api.nbfx.output.ConsoleRun;
import org.netbeans.api.nbfx.output.OutputConsole;
import org.netbeans.api.nbfx.output.OutputKind;
import org.netbeans.modules.nbfx.project.ui.api.BuildActionProvider;
import org.netbeans.modules.nbfx.project.ui.api.BuildExecution;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput;
import org.netbeans.modules.nbfx.project.ui.api.BuildProgress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Runs a build action for a project directory. It first asks the project-type
 * {@link BuildActionProvider} to run the command in-process (streaming to the Fx console); when the
 * provider does not support in-process execution, it falls back to the CLI runner. Shared by the
 * Build menu command and the navigator context menu.
 *
 * @since 1.0
 */
public final class BuildRunner {

    private static final Logger LOG = Logger.getLogger(BuildRunner.class.getName());

    private BuildRunner() {
    }

    /**
     * How an in-process build is started once its console and progress exist; see
     * {@link #run(Path, String, Starter)}.
     *
     * @since 1.0
     */
    @FunctionalInterface
    public interface Starter {

        /** Starts the build, streaming to {@code output} and reporting to {@code progress}; {@code null} when it cannot. */
        BuildExecution start(BuildOutput output, BuildProgress progress);
    }

    /**
     * Runs an in-process build that is not one of the {@code BuildCommands} - a Maven goal picked
     * in the Navigator - in the console {@code consoleName}, cleared and shown first, with
     * cancellable progress: what {@link #run(Path, String, String)} does for a command, without the
     * CLI fallback.
     * <p>
     * The console keeps {@code starter} for its <em>Re-run</em> action until the console or the
     * project of {@code dir} is closed, so the starter should hold what names the build - the
     * folder, the goals - and not the project's own objects.
     *
     * @since 1.0
     */
    public static void run(Path dir, String consoleName, Starter starter) {
        if (dir == null) {
            return;
        }
        OutputConsole output = Lookup.getDefault().lookup(OutputConsole.class);
        if (output == null) {
            LOG.warning("No OutputConsole service; build output cannot be shown");
            return;
        }
        ConsolePanel console = output.console(consoleId(dir, consoleName), consoleName);
        console.clear();
        console.show();
        Reruns.set(console, dir, () -> run(dir, consoleName, starter));
        Progress progress = Lookup.getDefault().lookup(Progress.class);
        try {
            if (starter.start(output(console), progress(progress, consoleName, console)) == null) {
                console.append(NbBundle.getMessage(BuildRunner.class, "BuildCommand.noTool") + "\n");
            }
        } catch (RuntimeException | LinkageError t) {
            LOG.log(Level.WARNING, "In-process build failed", t);
            console.append("\n[in-process build failed: " + t + "]\n");
        }
    }

    public static void run(Path dir, String command, String consoleName) {
        if (dir == null) {
            return;
        }
        OutputConsole output = Lookup.getDefault().lookup(OutputConsole.class);
        if (output == null) {
            LOG.warning("No OutputConsole service; build output cannot be shown");
            return;
        }
        ConsolePanel console = output.console(consoleId(dir, consoleName), consoleName);
        console.clear();
        console.show();
        // Re-run asks for the project's build action again: it holds the folder and the command only.
        Reruns.set(console, dir, () -> run(dir, command, consoleName));

        Progress progress = Lookup.getDefault().lookup(Progress.class);
        BuildActionProvider provider = BuildActions.providerFor(dir);
        LOG.info(() -> "Build " + command + " in " + dir + " via "
                + (provider == null ? "CLI" : provider.getClass().getName()));
        if (provider != null) {
            try {
                BuildExecution execution = provider.start(dir, command,
                        output(console), progress(progress, consoleName, console));
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

    /**
     * What identifies the console of a build: its folder with its name, so that the same command
     * on two projects called alike fills two consoles, and on one project the same console again.
     */
    static String consoleId(Path dir, String consoleName) {
        return dir.toAbsolutePath().normalize() + "\n" + consoleName;
    }

    /**
     * The console the builds named {@code consoleName} in {@code dir} write to, or {@code null}
     * without the output service: for whoever follows the output of a build it starts here.
     */
    public static ConsolePanel console(Path dir, String consoleName) {
        OutputConsole output = Lookup.getDefault().lookup(OutputConsole.class);
        return output == null ? null : output.console(consoleId(dir, consoleName), consoleName);
    }

    /** The CLI fallback: detect the tool from marker files and run it as a process. */
    private static void runCli(Path dir, String command, String consoleName, ConsolePanel console, Progress progress) {
        List<String> commandLine = BuildTool.detect(dir).commandLine(dir, command);
        BuildActionProvider provider = BuildActions.providerFor(dir);
        if (commandLine == null && provider != null) {
            commandLine = provider.commandLine(dir, command);
        }
        if (commandLine == null) {
            console.append(NbBundle.getMessage(BuildRunner.class, "BuildCommand.noTool") + "\n");
            return;
        }
        AtomicReference<Process> process = new AtomicReference<>();
        Runnable cancel = () -> {
            Process running = process.get();
            if (running != null) {
                running.destroy();
            }
        };
        Progress.Handle handle = progress == null ? null : progress.start(consoleName, cancel);
        ConsoleRun run = console.startRun(cancel);
        List<String> line = commandLine;
        // The project type knows how its tool names its steps and reports its result.
        Function<String, OutputKind> kinds = provider == null
                ? text -> OutputKind.NORMAL
                : text -> outputKind(provider.lineKind(text));
        ProcessRunner.run(line, dir, console, kinds, process::set, () -> {
            run.finish();
            if (handle != null) {
                handle.finish();
            }
        });
    }

    private static OutputKind outputKind(BuildOutput.Kind kind) {
        return switch (kind) {
            case ERROR -> OutputKind.ERROR;
            case WARNING -> OutputKind.WARNING;
            case SUCCESS -> OutputKind.SUCCESS;
            case MUTED -> OutputKind.MUTED;
            case NORMAL -> OutputKind.NORMAL;
        };
    }

    private static BuildOutput output(ConsolePanel console) {
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

            @Override
            public void append(String text, Kind kind) {
                console.append(text, outputKind(kind));
            }
        };
    }

    /**
     * The progress of an in-process build, which is also the build's run in {@code console}: the
     * build can be stopped from the Output window with the cancel action it first reports, the one
     * its progress handle has, from that first report to its finish. A build reports its progress
     * more than once - a new message - without becoming another run, and finishing ends this
     * build's run only: the console may be running the next build by then.
     */
    static BuildProgress progress(Progress progress, String name, ConsolePanel console) {
        return new BuildProgress() {
            private ConsoleRun run;
            private Progress.Handle handle;

            @Override
            public synchronized void start(String displayName, Runnable cancel) {
                if (run != null) {
                    if (handle != null) {
                        handle.setMessage(displayName);
                    }
                    return;
                }
                run = console.startRun(cancel);
                if (progress != null) {
                    handle = progress.start(displayName, cancel);
                }
            }

            @Override
            public synchronized void finish() {
                if (run != null) {
                    run.finish();
                    run = null;
                }
                if (handle != null) {
                    handle.finish();
                    handle = null;
                }
            }
        };
    }
}
