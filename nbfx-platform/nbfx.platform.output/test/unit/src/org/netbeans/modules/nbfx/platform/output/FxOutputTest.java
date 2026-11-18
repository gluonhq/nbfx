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
package org.netbeans.modules.nbfx.platform.output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.netbeans.api.nbfx.output.ConsolePanel;
import org.netbeans.api.nbfx.output.ConsoleRun;
import org.netbeans.api.nbfx.output.OutputKind;

/**
 * Verifies the console registry, the accumulation of console text and the runs of a console. The
 * consoles marshal to the JavaFX Application Thread, so the tests run on it.
 */
public class FxOutputTest {

    @Test
    public void sameNameYieldsSameConsole() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleRegistry registry = new ConsoleRegistry();

            assertSame(registry.console("Build"), registry.console("Build"));
            assertEquals(1, registry.consoles().size());
            assertSame("the service hands out the registry's consoles",
                    registry.console("Build"), new ConsoleOutput(registry).console("Build"));
            return null;
        });
    }

    /** The id makes the console, the name only titles it: two consoles may be called the same. */
    @Test
    public void consolesWithTheSameNameAreToldApartByTheirId() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleRegistry registry = new ConsoleRegistry();

            ConsoleModel maven = registry.console("/maven/hellofx", "Run HelloFX");
            ConsoleModel gradle = registry.console("/gradle/hellofx", "Run HelloFX");

            assertNotSame(maven, gradle);
            assertEquals("Run HelloFX", maven.getName());
            assertEquals("Run HelloFX", gradle.getName());
            assertSame(maven, registry.console("/maven/hellofx", "Run HelloFX"));
            assertEquals(2, registry.consoles().size());

            registry.close(maven);
            assertEquals(java.util.List.of(gradle), registry.consoles());
            assertNotSame(maven, registry.console("/maven/hellofx", "Run HelloFX"));
            return null;
        });
    }

    @Test
    public void consoleAccumulatesAndClears() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleModel console = new ConsoleRegistry().console("Run");
            StringBuilder sink = new StringBuilder();

            console.attach((text, kind) -> sink.append(text), () -> sink.setLength(0), () -> {
            });
            console.append("hello");
            console.append(" world");
            assertEquals("hello world", sink.toString());

            console.clear();
            assertEquals("", sink.toString());
            return null;
        });
    }

    /** Asking for a console is not asking to see it: only {@code show()} brings the Output window up. */
    @Test
    public void onlyShowRevealsTheOutputWindow() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleRegistry registry = new ConsoleRegistry();
            AtomicInteger reveals = new AtomicInteger();
            AtomicInteger selections = new AtomicInteger();
            registry.setRevealer(console -> reveals.incrementAndGet());

            ConsoleModel console = registry.console("Build");
            console.append("output");
            assertEquals(0, reveals.get());

            console.attach((text, kind) -> {
            }, () -> {
            }, selections::incrementAndGet);
            console.show();
            assertEquals(1, reveals.get());
            assertEquals("the console's tab comes to the front", 1, selections.get());
            return null;
        });
    }

    @Test
    public void closedConsoleIsForgotten() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleRegistry registry = new ConsoleRegistry();
            ConsoleModel console = registry.console("Run");
            console.append("first run");
            console.setRerun(() -> {
            });
            console.startRun(() -> {
            });

            registry.close(console);

            assertTrue(registry.consoles().isEmpty());
            assertNull("a closed console lets go of its re-run action", console.rerunProperty().get());
            assertNull("and of its run", console.runProperty().get());
            ConsolePanel again = registry.console("Run");
            assertNotSame(console, again);
            assertEquals("", again.text());
            return null;
        });
    }

    /** The end of a run reported after the next run started leaves the next run going. */
    @Test
    public void aStaleRunDoesNotEndTheNextOne() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleModel console = new ConsoleRegistry().console("Run");
            AtomicInteger stopsOfA = new AtomicInteger();
            AtomicInteger stopsOfB = new AtomicInteger();

            ConsoleRun a = console.startRun(stopsOfA::incrementAndGet);
            ConsoleRun b = console.startRun(stopsOfB::incrementAndGet);
            a.finish();

            assertSame("B is still the console's run", b, console.runProperty().get());
            console.runProperty().get().stop();
            assertEquals(0, stopsOfA.get());
            assertEquals(1, stopsOfB.get());

            b.finish();
            assertNull(console.runProperty().get());
            b.finish();
            a.finish();
            assertNull(console.runProperty().get());
            return null;
        });
    }

    /** The same, with the end of the first run reported from another thread and still on its way. */
    @Test
    public void aStaleRunFinishingLateDoesNotEndTheNextOne() throws Exception {
        ConsoleModel console = new ConsoleModel("Run");
        ConsoleRun[] runs = new ConsoleRun[2];
        FxTestSupport.onFx(() -> {
            runs[0] = console.startRun(() -> {
            });
            // Run A ends on its own thread: its finish is queued behind this FX task...
            Thread worker = new Thread(runs[0]::finish);
            worker.start();
            try {
                worker.join();
            } catch (InterruptedException ex) {
                throw new AssertionError(ex);
            }
            // ...and the user starts run B before it gets there.
            runs[1] = console.startRun(() -> {
            });
            return null;
        });

        assertSame(runs[1], FxTestSupport.onFx(() -> console.runProperty().get()));
    }

    @Test
    public void aRunWithoutAStopActionCannotBeStopped() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleModel console = new ConsoleRegistry().console("Run");

            console.startRun(null);

            assertFalse(console.runProperty().get().canStop());
            console.runProperty().get().stop();
            return null;
        });
    }

    /** A view attaching late gets the text with the kinds it was appended as. */
    @Test
    public void attachReplaysTheKinds() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleModel console = new ConsoleModel("Build");
            console.append("run:\n", OutputKind.MUTED);
            console.append("hello\n");
            console.append("BUILD SUCCESSFUL\n", OutputKind.SUCCESS);
            console.append("no kind\n", null);
            StringBuilder sink = new StringBuilder();

            console.attach((text, kind) -> sink.append(kind).append('[').append(text.strip()).append(']'), () -> {
            }, () -> {
            });

            assertEquals("MUTED[run:]NORMAL[hello]SUCCESS[BUILD SUCCESSFUL]NORMAL[no kind]", sink.toString());
            assertEquals("run:\nhello\nBUILD SUCCESSFUL\nno kind\n", console.text());
            return null;
        });
    }

    /** Text appended off the FX thread and still on its way when the view attaches is shown once. */
    @Test
    public void attachDoesNotRepeatPendingText() throws Exception {
        ConsoleModel console = new ConsoleModel("Run");
        StringBuilder sink = new StringBuilder();
        FxTestSupport.onFx(() -> {
            // Appended from another thread while the FX thread is busy here: the delivery is queued.
            Thread writer = new Thread(() -> {
                console.append("cleared");
                console.clear();
                console.append("pending");
            });
            writer.start();
            try {
                writer.join();
            } catch (InterruptedException ex) {
                throw new AssertionError(ex);
            }
            console.attach((text, kind) -> sink.append(text), () -> sink.setLength(0), () -> {
            });
            assertEquals("pending", sink.toString());
            return null;
        });
        // The queued deliveries have run by now.
        assertEquals("pending", FxTestSupport.onFx(sink::toString));

        console.append(" more");
        assertEquals("pending more", FxTestSupport.onFx(sink::toString));
    }
}
