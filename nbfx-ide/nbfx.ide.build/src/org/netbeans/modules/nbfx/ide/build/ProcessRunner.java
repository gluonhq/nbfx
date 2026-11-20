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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput.Kind;
import org.openide.util.NbBundle;

/**
 * Runs the processes of a build in the background, one after the other, and streams their output
 * into the build's output: the error stream as errors, each line of the output stream as the kind the
 * caller gives it. The two streams are read apart, so a line of one may come slightly before or
 * after its neighbour of the other.
 *
 * @since 1.0
 */
final class ProcessRunner {

    /** A run that can be stopped: the process running now, and whether to go on to the next. */
    static final class Control {

        private final AtomicReference<Process> current = new AtomicReference<>();
        private volatile boolean stopped;

        /**
         * Stops the run: the running process and the processes it started - a build tool starts
         * the compiler, the tests, the application - and no further step.
         */
        void stop() {
            stopped = true;
            Process running = current.get();
            if (running != null) {
                running.descendants().forEach(ProcessHandle::destroy);
                running.destroy();
            }
        }

        boolean isStopped() {
            return stopped;
        }
    }

    private ProcessRunner() {
    }

    /**
     * Resolves the processes of a build and runs them, in order, on a daemon thread, streaming
     * into {@code console}, the output of the build. A step that fails ends the run, and so does {@link Control#stop()}.
     *
     * @param steps    gives the processes; called on the daemon thread, as resolving them may read
     *                 the project. {@code null} from it is "no build tool", an empty list "the
     *                 project has this command disabled": either is said in the console
     * @param kinds    the kind of a line of the output stream
     * @param control  stops the run
     * @param onFinish invoked once the run is over; may be {@code null}
     */
    static void run(Supplier<List<BuildProcess>> steps, BuildOutput console, Function<String, Kind> kinds,
            Control control, Runnable onFinish) {
        Thread thread = new Thread(() -> {
            try {
                execute(steps.get(), console, kinds, control);
            } catch (RuntimeException ex) {
                console.append("\n[failed: " + ex + "]\n", Kind.ERROR);
            } finally {
                if (onFinish != null) {
                    onFinish.run();
                }
            }
        }, "nbfx-run");
        thread.setDaemon(true);
        thread.start();
    }

    private static void execute(List<BuildProcess> steps, BuildOutput console,
            Function<String, Kind> kinds, Control control) {
        if (steps == null) {
            console.append(NbBundle.getMessage(ProcessRunner.class, "BuildCommand.noTool") + "\n");
            return;
        }
        if (steps.isEmpty()) {
            console.append(NbBundle.getMessage(ProcessRunner.class, "BuildCommand.disabled") + "\n");
            return;
        }
        for (BuildProcess step : steps) {
            if (control.isStopped() || execute(step, console, kinds, control) != 0) {
                return;
            }
        }
    }

    /** Runs {@code step} to its end; the exit value, or -1 when it did not start or was interrupted. */
    private static int execute(BuildProcess step, BuildOutput console, Function<String, Kind> kinds,
            Control control) {
        console.append("$ " + String.join(" ", step.commandLine()) + "\n", Kind.MUTED);
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(step.commandLine()).directory(step.directory().toFile());
            builder.environment().putAll(step.environment());
            process = builder.start();
            control.current.set(process);
            if (control.isStopped()) {
                // Stopped while starting: the stop found no process to end.
                control.stop();
            }
            InputStream errors = process.getErrorStream();
            Thread errorPump = new Thread(() -> pump(errors, console, line -> Kind.ERROR), "nbfx-run-err");
            errorPump.setDaemon(true);
            errorPump.start();
            pump(process.getInputStream(), console, kinds);
            errorPump.join();
            int exit = process.waitFor();
            console.append("\n[exit " + exit + "]\n", exit == 0 ? Kind.MUTED : Kind.ERROR);
            return exit;
        } catch (IOException ex) {
            console.append("\n[failed to start: " + ex.getMessage() + "]\n", Kind.ERROR);
            return -1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroy();
            }
            console.append("\n[interrupted]\n", Kind.ERROR);
            return -1;
        } finally {
            control.current.set(null);
        }
    }

    /** Appends the lines of {@code stream} to {@code console} until it ends; a read error ends it too. */
    private static void pump(InputStream stream, BuildOutput console, Function<String, Kind> kinds) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                console.append(line + "\n", kinds.apply(line));
            }
        } catch (IOException ex) {
            // The process went away: what was read is shown.
        }
    }
}
