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
package com.gluonhq.netbeans.nbfx.launcher;

import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.editor.EditorService;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectOpener;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import com.gluonhq.netbeans.nbfx.output.FxConsole;
import com.gluonhq.netbeans.nbfx.output.FxOutput;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import javafx.application.Platform;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * The W0 full-application smoke: drives the running shell through the same services a user would
 * exercise - open a project, edit/save a file, build, run, assert the output and commit - then writes
 * a JSON report. It is started by {@link FxGuiRunLevel} when {@code NBFX_SCENARIO} points at the
 * fixture, and it exits the application with the report status.
 */
final class ScenarioRunner {

    private ScenarioRunner() {
    }

    static int run(Path fixture) {
        Path work = null;
        try {
            work = Files.createTempDirectory("nbfx-scenario").resolve("ant-hello");
            copyTree(fixture, work);
            git(work, "init");
            git(work, "config", "user.email", "scenario@nbfx.local");
            git(work, "config", "user.name", "nbfx scenario");
            git(work, "add", "-A");
            git(work, "commit", "-m", "fixture");

            FileObject root = FileUtil.toFileObject(work.toFile());
            if (root == null) {
                throw new IllegalStateException("fixture root is not a FileObject");
            }
            ProjectOpener opener = Lookup.getDefault().lookup(ProjectOpener.class);
            ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
            if (opener == null || registry == null) {
                throw new IllegalStateException("project services are not available");
            }
            opener.open(root);
            await("project open", 60, () -> registry.find(OpenProject.pathOf(root)) != null);
            OpenProject project = registry.find(OpenProject.pathOf(root));

            EditorService editors = Lookup.getDefault().lookup(EditorService.class);
            if (editors == null) {
                throw new IllegalStateException("EditorService is not available");
            }
            FileObject main = root.getFileObject("src/hello/Main.java");
            if (main == null) {
                throw new IllegalStateException("src/hello/Main.java is missing");
            }
            onFx(() -> {
                EditorDocument document = editors.createDocument(main);
                if (!document.replaceRange(0, 0, "// nbfx scenario edit\n")) {
                    throw new IllegalStateException("the document is read-only");
                }
                document.save();
                return null;
            });

            FxOutput output = Lookup.getDefault().lookup(FxOutput.class);
            if (output == null) {
                throw new IllegalStateException("FxOutput is not available");
            }
            FxConsole console = output.console("nbfx-scenario");
            console.clear();
            runProcess(work, List.of("ant", "build"), console);
            await("build", 120, () -> console.text().contains("BUILD SUCCESSFUL"));
            console.clear();
            runProcess(work, List.of("ant", "run"), console);
            await("run marker", 120, () -> console.text().contains("NB_FX_SCENARIO_OK"));

            git(work, "add", "-A");
            git(work, "commit", "-m", "scenario");
            if (!git(work, "status", "--porcelain").trim().isEmpty()) {
                throw new IllegalStateException("git status is not clean after the commit");
            }

            OpenProject opened = project;
            onFx(() -> {
                registry.close(opened);
                return null;
            });
            System.out.println("[scenario] OK " + work);
            writeReport(work, true, null);
            return 0;
        } catch (Throwable failure) {
            System.out.println("[scenario] FAILED: " + failure);
            failure.printStackTrace(System.out);
            writeReport(work, false, failure);
            return 1;
        }
    }

    private static void writeReport(Path work, boolean ok, Throwable failure) {
        try {
            Path report = reportPath();
            String json = "{\n  \"ok\": " + ok + ",\n  \"work\": " + quote(work) + ",\n  \"error\": "
                    + quote(failure == null ? null : failure.toString()) + "\n}\n";
            if (report.getParent() != null) {
                Files.createDirectories(report.getParent());
            }
            Files.writeString(report, json);
            System.out.println("[scenario] report " + report);
        } catch (IOException ex) {
            System.out.println("[scenario] cannot write the report: " + ex);
        }
    }

    private static Path reportPath() {
        String configured = System.getenv("NBFX_SCENARIO_REPORT");
        return configured == null || configured.isBlank()
                ? Path.of("build", "scenario-report.json")
                : Path.of(configured);
    }

    private static String quote(Object value) {
        return value == null ? "null" : '"' + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static void runProcess(Path dir, List<String> command, FxConsole console) throws Exception {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        console.append("$ " + String.join(" ", command) + "\n");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                console.append(line + "\n");
            }
        }
        int exit = process.waitFor();
        console.append("[exit " + exit + "]\n");
        if (exit != 0) {
            throw new IllegalStateException("command failed (" + exit + "): " + command);
        }
    }

    private static String git(Path dir, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + out);
        }
        return out;
    }

    private static void await(String what, int seconds, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("timed out waiting for " + what);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(action.call());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future.get(60, TimeUnit.SECONDS);
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                Path relative = from.relativize(path);
                if (relative.toString().isEmpty()
                        || relative.startsWith("build")
                        || relative.startsWith("dist")
                        || relative.startsWith(".git")) {
                    continue;
                }
                Path target = to.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
