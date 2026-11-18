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

import org.netbeans.api.nbfx.output.ConsolePanel;
import org.netbeans.api.nbfx.output.OutputKind;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs an external process in the background and streams its output into a console: the error
 * stream as errors, each line of the output stream as the kind the caller gives it. The two
 * streams are read apart, so a line of one may come slightly before or after its neighbour of the
 * other.
 *
 * @since 1.0
 */
final class ProcessRunner {

    private ProcessRunner() {
    }

    /**
     * Starts {@code command} in {@code dir} on a daemon thread, streaming into {@code console}.
     *
     * @param kinds    the kind of a line of the output stream
     * @param onStart  invoked with the started process, so the caller can cancel it; may be {@code null}
     * @param onFinish invoked once the process has finished (or failed to start); may be {@code null}
     */
    static void run(List<String> command, Path dir, ConsolePanel console, Function<String, OutputKind> kinds,
            Consumer<Process> onStart, Runnable onFinish) {
        Thread thread = new Thread(() -> execute(command, dir, console, kinds, onStart, onFinish), "nbfx-run");
        thread.setDaemon(true);
        thread.start();
    }

    private static void execute(List<String> command, Path dir, ConsolePanel console,
            Function<String, OutputKind> kinds, Consumer<Process> onStart, Runnable onFinish) {
        console.append("$ " + String.join(" ", command) + "\n", OutputKind.MUTED);
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .directory(dir.toFile())
                    .start();
            if (onStart != null) {
                onStart.accept(process);
            }
            InputStream errors = process.getErrorStream();
            Thread errorPump = new Thread(() -> pump(errors, console, line -> OutputKind.ERROR), "nbfx-run-err");
            errorPump.setDaemon(true);
            errorPump.start();
            pump(process.getInputStream(), console, kinds);
            errorPump.join();
            int exit = process.waitFor();
            console.append("\n[exit " + exit + "]\n", exit == 0 ? OutputKind.MUTED : OutputKind.ERROR);
        } catch (IOException ex) {
            console.append("\n[failed to start: " + ex.getMessage() + "]\n", OutputKind.ERROR);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroy();
            }
            console.append("\n[interrupted]\n", OutputKind.ERROR);
        } finally {
            if (onFinish != null) {
                onFinish.run();
            }
        }
    }

    /** Appends the lines of {@code stream} to {@code console} until it ends; a read error ends it too. */
    private static void pump(InputStream stream, ConsolePanel console, Function<String, OutputKind> kinds) {
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
