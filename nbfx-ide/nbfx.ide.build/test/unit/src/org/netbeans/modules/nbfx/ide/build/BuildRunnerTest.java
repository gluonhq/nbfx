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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.netbeans.api.nbfx.output.ConsolePanel;
import org.netbeans.api.nbfx.output.ConsoleRun;
import org.netbeans.api.nbfx.output.OutputConsole;
import org.netbeans.modules.nbfx.project.ui.api.BuildActionProvider;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput;
import org.netbeans.modules.nbfx.project.ui.api.BuildProgress;
import org.openide.util.test.MockLookup;

/**
 * Verifies what the build runner tells the console of a build: how to run it again, and its run -
 * from the build's first progress report to its finish, and no other build's.
 */
public class BuildRunnerTest {

    /** A console recording what it is told; its runs end by identity, as the contract says. */
    private static final class FakeConsole implements ConsolePanel {

        private final String name;
        final StringBuilder text = new StringBuilder();
        final List<Runnable> stops = new ArrayList<>();
        Runnable rerun;
        Runnable currentStop;
        ConsoleRun current;
        int shown;

        FakeConsole(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void append(String delta) {
            text.append(delta);
        }

        @Override
        public void clear() {
            text.setLength(0);
        }

        @Override
        public void show() {
            shown++;
        }

        @Override
        public String text() {
            return text.toString();
        }

        @Override
        public void setRerun(Runnable rerun) {
            this.rerun = rerun;
        }

        @Override
        public ConsoleRun startRun(Runnable stop) {
            stops.add(stop);
            ConsoleRun[] run = new ConsoleRun[1];
            run[0] = () -> {
                if (current == run[0]) {
                    current = null;
                    currentStop = null;
                }
            };
            current = run[0];
            currentStop = stop;
            return run[0];
        }

        boolean isRunning() {
            return current != null;
        }
    }

    private final List<FakeConsole> consoles = new ArrayList<>();
    private final Map<String, FakeConsole> byId = new HashMap<>();
    private final Path dir = Path.of(System.getProperty("java.io.tmpdir"), "nbfx-build-runner-test");

    @Before
    public void installConsoles() {
        MockLookup.setInstances((OutputConsole) (id, name) -> {
            FakeConsole console = byId.get(id);
            if (console == null) {
                console = new FakeConsole(name);
                byId.put(id, console);
                consoles.add(console);
            }
            return console;
        });
    }

    @After
    public void removeConsoles() {
        MockLookup.setInstances();
    }

    @Test
    public void aBuildIsARunOfItsConsoleFromStartToFinish() {
        List<BuildProgress> progress = new ArrayList<>();
        AtomicInteger cancels = new AtomicInteger();
        BuildRunner.run(dir, "Build app", (output, buildProgress) -> {
            progress.add(buildProgress);
            output.append("building\n");
            return () -> {
            };
        });
        FakeConsole console = consoles.get(0);
        assertEquals("the console is shown, by the runner and not by asking for it", 1, console.shown);
        assertEquals("building\n", console.text());
        assertNotNull(console.rerun);
        assertFalse("nothing runs before the build reports its start", console.isRunning());

        Runnable cancel = cancels::incrementAndGet;
        progress.get(0).start("maven", cancel);
        assertTrue(console.isRunning());
        assertSame("Stop is the build's cancel action", cancel, console.currentStop);

        // A second report is a new message of the same build, not another run with another cancel.
        progress.get(0).start("maven: compile", () -> {
        });
        assertEquals(1, console.stops.size());
        assertSame(cancel, console.currentStop);

        progress.get(0).finish();
        assertFalse(console.isRunning());
        progress.get(0).finish();
        assertFalse(console.isRunning());
    }

    @Test
    public void reRunStartsTheBuildAgainInTheSameConsole() {
        AtomicInteger starts = new AtomicInteger();
        BuildRunner.run(dir, "Build app", (output, buildProgress) -> {
            output.append("run " + starts.incrementAndGet() + "\n");
            return () -> {
            };
        });
        FakeConsole console = consoles.get(0);

        console.rerun.run();

        assertEquals(2, starts.get());
        assertEquals(1, consoles.size());
        assertEquals("the console is cleared for the new run", "run 2\n", console.text());
    }

    /** The first build reports its end after the second one started: the second is still running. */
    @Test
    public void aBuildFinishingLateDoesNotEndTheNextOne() {
        List<BuildProgress> progress = new ArrayList<>();
        BuildRunner.Starter starter = (output, buildProgress) -> {
            progress.add(buildProgress);
            return () -> {
            };
        };
        BuildRunner.run(dir, "Build app", starter);
        FakeConsole console = consoles.get(0);
        Runnable cancelA = () -> {
        };
        Runnable cancelB = () -> {
        };
        progress.get(0).start("first", cancelA);

        console.rerun.run();
        progress.get(1).start("second", cancelB);
        progress.get(0).finish();

        assertTrue(console.isRunning());
        assertSame(cancelB, console.currentStop);
        progress.get(1).finish();
        assertFalse(console.isRunning());
    }

    /** Two projects called alike: the same command name, a console each; the same project again, the same console. */
    @Test
    public void projectsWithTheSameNameGetAConsoleEach() {
        BuildRunner.Starter starter = (output, buildProgress) -> () -> {
        };
        BuildRunner.run(dir.resolve("maven").resolve("hellofx"), "Run HelloFX", starter);
        BuildRunner.run(dir.resolve("gradle").resolve("hellofx"), "Run HelloFX", starter);
        BuildRunner.run(dir.resolve("maven").resolve("hellofx"), "Run HelloFX", starter);
        BuildRunner.run(dir.resolve("maven").resolve("hellofx"), "Build HelloFX", starter);

        assertEquals(3, consoles.size());
        assertEquals("Run HelloFX", consoles.get(0).getName());
        assertEquals("Run HelloFX", consoles.get(1).getName());
        assertEquals("Build HelloFX", consoles.get(2).getName());
    }

    /**
     * The process fallback asks the project type first: it knows what the project maps the command
     * to. The tool the folder's marker files name is the fallback for a command it has no command
     * line for, and for a folder without a project type.
     */
    @Test
    public void theProjectTypesCommandLineWinsOverTheMarkerFiles() throws Exception {
        Path maven = Files.createTempDirectory("nbfx-build-runner");
        Files.writeString(maven.resolve("pom.xml"), "<project/>");
        BuildActionProvider mapped = provider(List.of("mvn", "clean", "javafx:run"));
        BuildActionProvider silent = provider(null);
        List<String> markerFiles = BuildTool.detect(maven).commandLine(maven, "run");
        assertNotNull("the folder is a Maven one by its marker file", markerFiles);

        assertEquals(List.of(new BuildProcess(List.of("mvn", "clean", "javafx:run"), maven)),
                BuildRunner.processes(maven, "run", mapped));
        assertEquals(List.of(new BuildProcess(markerFiles, maven)), BuildRunner.processes(maven, "run", silent));
        assertEquals(List.of(new BuildProcess(markerFiles, maven)), BuildRunner.processes(maven, "run", null));
        assertNull(BuildRunner.processes(Files.createTempDirectory("nbfx-no-tool"), "run", silent));
    }

    private static BuildActionProvider provider(List<String> commandLine) {
        return new BuildActionProvider() {
            @Override
            public String projectTypeId() {
                return "test";
            }

            @Override
            public String[] getSupportedActions() {
                return new String[] {"run"};
            }

            @Override
            public List<String> commandLine(Path dir, String command) {
                return commandLine;
            }
        };
    }

    /** A build run as processes: each in its folder with its environment, the next only after a success, and stoppable. */
    @Test
    public void processesRunInOrderUntilOneFails() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-build-processes");
        StringBuffer text = new StringBuffer();
        CountDownLatch finished = new CountDownLatch(1);
        List<String> started = new ArrayList<>();
        BuildOutput output = new BuildOutput() {
            @Override
            public void clear() {
            }

            @Override
            public void show() {
            }

            @Override
            public void append(String delta) {
                text.append(delta);
            }
        };
        BuildProgress progress = new BuildProgress() {
            @Override
            public void start(String name, Runnable cancel) {
                started.add(name);
            }

            @Override
            public void finish() {
                finished.countDown();
            }
        };
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();

        BuildProcesses.start("steps", () -> List.of(
                new BuildProcess(List.of(java, "-version"), dir),
                new BuildProcess(List.of(java, "--no-such-option-nbfx"), dir, Map.of("NBFX_TEST", "1")),
                new BuildProcess(List.of(java, "-version"), dir)), output, progress, line -> BuildOutput.Kind.NORMAL);

        assertTrue(finished.await(60, TimeUnit.SECONDS));
        assertEquals(List.of("steps"), started);
        assertEquals("the step after the failed one does not run: " + text, 2, count(text.toString(), "$ "));
        assertTrue(text.toString(), text.indexOf("[exit 0]") >= 0);
    }

    @Test
    public void aDisabledCommandSaysSoAndRunsNothing() throws Exception {
        StringBuffer text = new StringBuffer();
        CountDownLatch finished = new CountDownLatch(1);
        BuildOutput output = new BuildOutput() {
            @Override
            public void clear() {
            }

            @Override
            public void show() {
            }

            @Override
            public void append(String delta) {
                text.append(delta);
            }
        };
        BuildProgress progress = new BuildProgress() {
            @Override
            public void start(String name, Runnable cancel) {
            }

            @Override
            public void finish() {
                finished.countDown();
            }
        };

        BuildProcesses.start("disabled", List::of, output, progress, line -> BuildOutput.Kind.NORMAL);

        assertTrue(finished.await(30, TimeUnit.SECONDS));
        assertTrue(text.toString(), text.indexOf("not available") >= 0);
        assertEquals(0, count(text.toString(), "$ "));
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            count++;
        }
        return count;
    }

    /** Closing a project takes the re-run actions of the builds under its folder away. */
    @Test
    public void closingAProjectForgetsItsReRuns() {
        BuildRunner.Starter starter = (output, buildProgress) -> () -> {
        };
        BuildRunner.run(dir.resolve("app"), "Build app", starter);
        BuildRunner.run(dir.resolve("app").resolve("module"), "Build module", starter);
        BuildRunner.run(dir.resolve("other"), "Build other", starter);

        Reruns.forget(dir.resolve("app"));

        assertNull(consoles.get(0).rerun);
        assertNull(consoles.get(1).rerun);
        assertNotNull(consoles.get(2).rerun);
    }
}
