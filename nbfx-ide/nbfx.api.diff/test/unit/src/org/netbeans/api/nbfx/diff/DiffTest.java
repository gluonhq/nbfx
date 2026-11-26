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
package org.netbeans.api.nbfx.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.netbeans.api.nbfx.diff.Difference.Kind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies the blocks the line comparison finds, and that they always lead from one text to the other. */
public class DiffTest {

    @Test
    public void sameLinesHaveNoDifferences() {
        assertTrue(Diff.compute(List.of("a", "b"), List.of("a", "b")).isIdentical());
        assertTrue(Diff.compute(List.of(), List.of()).isIdentical());
    }

    @Test
    public void addedLinesAreToldWithWhereTheyGo() {
        assertEquals(List.of(new Difference(Kind.ADD, 1, 0, 1, 2)),
                diff("a|d", "a|b|c|d"));
        assertEquals(List.of(new Difference(Kind.ADD, 0, 0, 0, 1)),
                diff("a", "x|a"));
        assertEquals(List.of(new Difference(Kind.ADD, 1, 0, 1, 1)),
                diff("a", "a|x"));
        assertEquals(List.of(new Difference(Kind.ADD, 0, 0, 0, 2)),
                Diff.compute(List.of(), List.of("a", "b")).differences());
    }

    @Test
    public void deletedLinesAreToldWithWhereTheyWere() {
        assertEquals(List.of(new Difference(Kind.DELETE, 1, 2, 1, 0)),
                diff("a|b|c|d", "a|d"));
        assertEquals(List.of(new Difference(Kind.DELETE, 0, 1, 0, 0)),
                diff("x|a", "a"));
        assertEquals(List.of(new Difference(Kind.DELETE, 1, 1, 1, 0)),
                diff("a|x", "a"));
        assertEquals(List.of(new Difference(Kind.DELETE, 0, 2, 0, 0)),
                Diff.compute(List.of("a", "b"), List.of()).differences());
    }

    @Test
    public void replacedLinesAreOneChange() {
        assertEquals(List.of(new Difference(Kind.CHANGE, 1, 1, 1, 1)),
                diff("a|b|c", "a|x|c"));
        assertEquals(List.of(new Difference(Kind.CHANGE, 1, 2, 1, 1)),
                diff("a|b|c|d", "a|x|d"));
        assertEquals(List.of(new Difference(Kind.CHANGE, 0, 1, 0, 3)),
                diff("a", "x|y|z"));
    }

    @Test
    public void severalBlocksComeFromTheTopDown() {
        List<Difference> differences = diff("a|b|c|d", "x|b|d|e");

        assertEquals(List.of(new Difference(Kind.CHANGE, 0, 1, 0, 1), new Difference(Kind.DELETE, 2, 1, 2, 0),
                new Difference(Kind.ADD, 4, 0, 3, 1)), differences);
    }

    @Test
    public void optionsTellWhichLinesAreTheSame() {
        List<String> left = List.of("int a = 1;", "  return a;", "Done");
        List<String> right = List.of("int  a=1;", "return a;  ", "DONE");

        assertFalse(Diff.compute(left, right).isIdentical());
        assertEquals(List.of(new Difference(Kind.CHANGE, 0, 1, 0, 1), new Difference(Kind.CHANGE, 2, 1, 2, 1)),
                Diff.compute(left, right, new DiffOptions(false, false, true)).differences());
        assertEquals(List.of(new Difference(Kind.CHANGE, 1, 2, 1, 2)),
                Diff.compute(left, right, new DiffOptions(false, true, false)).differences());
        assertTrue(Diff.compute(left, right, new DiffOptions(true, true, true)).isIdentical());
    }

    @Test
    public void linesAreSplitAsAnEditorShowsThem() {
        assertEquals(List.of("a", "b"), Diff.lines("a\nb"));
        assertEquals(List.of("a", "b", ""), Diff.lines("a\nb\n"));
        assertEquals(List.of("a", "b", "", "c"), Diff.lines("a\r\nb\r\rc"));
        assertEquals(List.of(""), Diff.lines(""));
        // Only one of the texts ends with a line ending: the last line differs.
        assertEquals(List.of(new Difference(Kind.ADD, 1, 0, 1, 1)),
                Diff.compute(Diff.lines("a"), Diff.lines("a\n")).differences());
    }

    @Test
    public void differencesLeadFromOneTextToTheOther() {
        Random random = new Random(20261124);
        for (int round = 0; round < 500; round++) {
            List<String> left = randomLines(random);
            List<String> right = randomLines(random);
            List<Difference> differences = Diff.compute(left, right).differences();

            assertEquals("round " + round + ": " + left + " -> " + right + " by " + differences,
                    right, applied(left, right, differences));
        }
    }

    /** {@code left} with the blocks replaced by the lines of {@code right} they name; checks their order. */
    private static List<String> applied(List<String> left, List<String> right, List<Difference> differences) {
        List<String> result = new ArrayList<>();
        int next = 0;
        for (Difference difference : differences) {
            assertTrue("blocks overlap or are out of order: " + differences, difference.leftStart() >= next);
            result.addAll(left.subList(next, difference.leftStart()));
            assertEquals("right start of " + difference, result.size(), difference.rightStart());
            result.addAll(right.subList(difference.rightStart(), difference.rightEnd()));
            next = difference.leftEnd();
        }
        result.addAll(left.subList(next, left.size()));
        return result;
    }

    private static List<String> randomLines(Random random) {
        // Few distinct lines, so that the texts share many and repeat some.
        int count = random.nextInt(12);
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(String.valueOf((char) ('a' + random.nextInt(5))));
        }
        return lines;
    }

    private static List<Difference> diff(String left, String right) {
        return Diff.compute(List.of(left.split("\\|", -1)), List.of(right.split("\\|", -1))).differences();
    }
}
