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
import static org.junit.Assert.assertNull;
import java.util.List;
import org.netbeans.api.nbfx.output.OutputKind;
import org.junit.Test;

/**
 * Verifies how appended text is cut into console lines and what each append reports as changed.
 */
public class ConsoleLinesTest {

    @Test
    public void newConsoleHasOneEmptyLine() {
        ConsoleLines lines = new ConsoleLines();

        assertEquals(1, lines.size());
        assertEquals("", lines.line(0));
    }

    @Test
    public void textWithoutBreakContinuesTheLastLine() {
        ConsoleLines lines = new ConsoleLines();

        assertEquals(new ConsoleLines.Change(0, 0, 3, 0, 0), lines.append("run"));
        assertEquals(new ConsoleLines.Change(0, 3, 1, 0, 0), lines.append(":"));
        assertEquals(1, lines.size());
        assertEquals("run:", lines.line(0));
    }

    @Test
    public void lineBreaksStartLines() {
        ConsoleLines lines = new ConsoleLines();
        lines.append("init:");

        assertEquals(new ConsoleLines.Change(0, 5, 0, 2, 4), lines.append("\n\ndeps"));
        assertEquals(new ConsoleLines.Change(2, 4, 5, 1, 0), lines.append("-jar:\n"));
        assertEquals(4, lines.size());
        assertEquals("init:", lines.line(0));
        assertEquals("", lines.line(1));
        assertEquals("deps-jar:", lines.line(2));
        assertEquals("", lines.line(3));
        assertEquals(0, lines.lastLineLength());
    }

    @Test
    public void carriageReturnsAreDropped() {
        ConsoleLines lines = new ConsoleLines();

        assertEquals(new ConsoleLines.Change(0, 0, 1, 1, 1), lines.append("a\r\nb"));
        assertEquals("a", lines.line(0));
        assertEquals("b", lines.line(1));
        assertNull(lines.append("\r"));
    }

    @Test
    public void linesKeepTheKindsOfTheirText() {
        ConsoleLines lines = new ConsoleLines();

        lines.append("run", OutputKind.MUTED);
        lines.append(":", OutputKind.MUTED);
        lines.append("\n[javac] ");
        lines.append("warning\nsecond line", OutputKind.WARNING);
        lines.append("\n\n");

        assertEquals(List.of(new ConsoleLines.Segment("run:", OutputKind.MUTED)), lines.segments(0));
        assertEquals(List.of(new ConsoleLines.Segment("[javac] ", OutputKind.NORMAL),
                new ConsoleLines.Segment("warning", OutputKind.WARNING)), lines.segments(1));
        assertEquals(List.of(new ConsoleLines.Segment("second line", OutputKind.WARNING)), lines.segments(2));
        assertEquals(List.of(), lines.segments(3));
        assertEquals("[javac] warning", lines.line(1));
    }

    @Test
    public void nothingAppendedIsNoChange() {
        ConsoleLines lines = new ConsoleLines();

        assertNull(lines.append(""));
        assertNull(lines.append(null));
    }

    @Test
    public void clearLeavesOneEmptyLine() {
        ConsoleLines lines = new ConsoleLines();
        lines.append("a\nb\n");

        lines.clear();

        assertEquals(1, lines.size());
        assertEquals("", lines.line(0));
    }
}
