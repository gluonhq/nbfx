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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Verifies the console registry and the accumulation of console text. The consoles marshal to the
 * JavaFX Application Thread, so the tests run on it.
 *
 * @since 1.0
 */
public class FxOutputTest {

    @Test
    public void sameNameYieldsSameConsole() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleOutput output = new ConsoleOutput();

            assertSame(output.console("Build"), output.console("Build"));
            assertEquals(1, output.consoles().size());
            return null;
        });
    }

    @Test
    public void consoleAccumulatesAndClears() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleOutput output = new ConsoleOutput();
            ConsoleModel console = (ConsoleModel) output.console("Run");
            StringBuilder sink = new StringBuilder();

            console.attach(sink::append, () -> sink.setLength(0), () -> {
            });
            console.append("hello");
            console.append(" world");
            assertEquals("hello world", sink.toString());

            console.clear();
            assertEquals("", sink.toString());
            return null;
        });
    }

    @Test
    public void closedConsoleIsForgotten() throws Exception {
        FxTestSupport.onFx(() -> {
            ConsoleOutput output = new ConsoleOutput();
            ConsoleModel console = (ConsoleModel) output.console("Run");
            console.append("first run");

            output.close(console);

            assertTrue(output.consoles().isEmpty());
            ConsolePanel again = output.console("Run");
            assertNotSame(console, again);
            assertEquals("", again.text());
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
            console.attach(sink::append, () -> sink.setLength(0), () -> {
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
