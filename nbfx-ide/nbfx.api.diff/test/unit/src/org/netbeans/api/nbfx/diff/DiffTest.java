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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CancellationException;
import org.junit.Test;
import org.netbeans.api.nbfx.diff.Difference.Kind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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
    public void optionsTogetherLeaveOutWhatEachDoes() {
        // U+2003, an em space: white space, but none of the ASCII ones.
        String em = "\u2003";
        List<String> indented = List.of(em + "a" + em + "b" + em, "   ");
        DiffOptions edges = new DiffOptions(false, false, true);
        DiffOptions inner = new DiffOptions(false, true, false);
        DiffOptions both = new DiffOptions(false, true, true);

        assertTrue(Diff.compute(indented, List.of("a" + em + "b", ""), edges).isIdentical());
        assertTrue(Diff.compute(indented, List.of(em + "ab" + em, "   "), inner).isIdentical());
        assertFalse(Diff.compute(indented, List.of("ab", ""), inner).isIdentical());
        assertFalse(Diff.compute(indented, List.of("ab", ""), edges).isIdentical());
        assertTrue(Diff.compute(indented, List.of("ab", ""), both).isIdentical());
        assertTrue(Diff.compute(indented, List.of(" a b ", "\t"), both).isIdentical());
    }

    @Test
    public void whatIsToCompareMustBeThere() {
        List<String> lines = List.of("a");
        assertEquals("left", assertThrows(NullPointerException.class,
                () -> Diff.compute(null, lines)).getMessage());
        assertEquals("right", assertThrows(NullPointerException.class,
                () -> Diff.compute(lines, null)).getMessage());
        assertEquals("options", assertThrows(NullPointerException.class,
                () -> Diff.compute(lines, lines, null)).getMessage());
        assertEquals("right line 1", assertThrows(NullPointerException.class,
                () -> Diff.compute(lines, java.util.Arrays.asList("a", null))).getMessage());
    }

    @Test
    public void aComparisonNobodyWaitsForIsGivenUpBeforeItStarts() {
        // Whatever the size of the texts, none included.
        for (List<String> lines : List.of(List.<String>of(), List.of("a"), List.of("a", "b", "c"))) {
            int[] asked = new int[1];
            assertThrows(CancellationException.class,
                    () -> Diff.compute(lines, lines, DiffOptions.EXACT, () -> ++asked[0] > 0));
            assertEquals(1, asked[0]);
        }
    }

    @Test
    public void aComparisonIsGivenUpInEveryPartOfIt() {
        List<String> left = new ArrayList<>();
        List<String> right = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            left.add("  Line " + i);
            right.add("line  " + (i + 1));
        }
        DiffOptions loose = new DiffOptions(true, true, true);
        // How often a whole comparison asks: it is given up at each of those times.
        int[] asked = new int[1];
        assertFalse(Diff.compute(left, right, loose, () -> ++asked[0] < 0).isIdentical());
        int times = asked[0];
        // Telling the lines apart, ordering them, the search and the blocks ask each in turn.
        assertTrue("asked " + times + " times", times > 40);

        for (int at = 1; at <= times; at++) {
            int last = at;
            int[] count = new int[1];
            assertThrows("asked " + at, CancellationException.class,
                    () -> Diff.compute(left, right, loose, () -> ++count[0] == last));
            assertEquals(at, count[0]);
        }
    }

    @Test(timeout = 20_000)
    public void aLineRepeatedManyTimesIsComparedInTimeAndGivenUpInTime() {
        // Blank lines, braces: every one of them is the same as every other.
        List<String> few = List.of("a", "", "b");
        List<String> many = Collections.nCopies(400_000, "");

        assertEquals(List.of(new Difference(Kind.DELETE, 0, 1, 0, 0), new Difference(Kind.CHANGE, 2, 1, 1, 399_999)),
                Diff.compute(few, many).differences());
        // The other way round, whichever of the blank lines is the one kept.
        assertKeepsTheMost(many, few, DiffOptions.EXACT);

        // Both sides of it: the search then has every pair of lines to go through.
        List<String> repeated = Collections.nCopies(3_000, "}");
        assertTrue(Diff.compute(repeated, repeated).isIdentical());
        List<String> endless = Collections.nCopies(200_000, "}");
        int[] asked = new int[1];
        assertThrows(CancellationException.class,
                () -> Diff.compute(endless, endless, DiffOptions.EXACT, () -> ++asked[0] > 2_000));
    }

    @Test
    public void lettersAreTheSameWhateverTheirCaseOneForOne() {
        DiffOptions ignoreCase = new DiffOptions(true, false, false);

        assertTrue(same("Done", "DONE", ignoreCase));
        assertTrue(same("\u00c9t\u00e9", "\u00e9T\u00c9", ignoreCase));
        // A sharp s and its capital, but not the two letters it is written with in capitals.
        assertTrue(same("stra\u00dfe", "STRA\u1e9eE", ignoreCase));
        assertFalse(same("stra\u00dfe", "STRASSE", ignoreCase));
        // A dotless i is no i; a sigma is one wherever it stands in its word.
        assertFalse(same("\u0131", "i", ignoreCase));
        assertTrue(same("\u039f\u03a3", "\u03bf\u03c2", ignoreCase));
        // Letters beyond the first plane: Deseret long I, capital and small.
        assertTrue(same("\ud801\udc00", "\ud801\udc28", ignoreCase));
        assertFalse(same("\ud801\udc00", "\ud801\udc28", DiffOptions.EXACT));
    }

    @Test
    public void whatAResultIsMadeOfMustBeInOrder() {
        Difference first = new Difference(Kind.CHANGE, 2, 2, 2, 1);
        new DiffResult(List.of(first, new Difference(Kind.ADD, 4, 0, 3, 1)));

        assertThrows(IllegalArgumentException.class,
                () -> new DiffResult(List.of(first, new Difference(Kind.ADD, 3, 0, 3, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> new DiffResult(List.of(first, new Difference(Kind.DELETE, 4, 1, 2, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> new DiffResult(List.of(new Difference(Kind.ADD, 4, 0, 3, 1), first)));
        // A block cannot end past the last line there can be.
        assertThrows(IllegalArgumentException.class,
                () -> new Difference(Kind.DELETE, Integer.MAX_VALUE, 1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Difference(Kind.ADD, 0, 0, 2, Integer.MAX_VALUE));
        assertEquals("text", assertThrows(NullPointerException.class, () -> Diff.lines(null)).getMessage());
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

    @Test
    public void asManyLinesAreKeptAsTheTextsHaveInCommon() {
        // Leading from one text to the other is not enough: deleting all and adding all does too.
        // Every pair of texts of up to five lines over two letters, and larger ones at random.
        List<List<String>> small = new ArrayList<>();
        for (int length = 0; length <= 5; length++) {
            for (int bits = 0; bits < 1 << length; bits++) {
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < length; i++) {
                    lines.add((bits >> i & 1) == 0 ? "a" : "b");
                }
                small.add(lines);
            }
        }
        for (List<String> left : small) {
            for (List<String> right : small) {
                assertKeepsTheMost(left, right, DiffOptions.EXACT);
            }
        }
        Random random = new Random(20261127);
        for (int round = 0; round < 300; round++) {
            assertKeepsTheMost(randomLines(random, 60, 6), randomLines(random, 60, 6), DiffOptions.EXACT);
            // Many lines of few kinds: most of them repeat.
            assertKeepsTheMost(randomLines(random, 80, 2), randomLines(random, 80, 2), DiffOptions.EXACT);
        }
    }

    @Test
    public void withOptionsTheLinesKeptAreTheSameAsTheOptionsTellIt() {
        DiffOptions loose = new DiffOptions(true, true, true);
        Random random = new Random(20261128);
        for (int round = 0; round < 300; round++) {
            List<String> left = variants(randomLines(random, 40, 4), random);
            List<String> right = variants(randomLines(random, 40, 4), random);

            assertKeepsTheMost(left, right, loose);
        }
    }

    /**
     * Checks the blocks of a comparison against the lines themselves: the blocks are in order and
     * within the texts, the lines between them are the same as {@code options} tell it, pair by
     * pair, and they are as many as the longest sequence of lines the texts have in common.
     */
    private static void assertKeepsTheMost(List<String> left, List<String> right, DiffOptions options) {
        List<Difference> differences = Diff.compute(left, right, options).differences();
        String told = left + " -> " + right + " by " + differences;
        List<String> leftKeys = left.stream().map(line -> key(line, options)).toList();
        List<String> rightKeys = right.stream().map(line -> key(line, options)).toList();
        int kept = 0;
        int leftAt = 0;
        int rightAt = 0;
        for (Difference difference : differences) {
            assertTrue(told, difference.leftStart() >= leftAt && difference.rightStart() >= rightAt);
            assertTrue(told, difference.leftEnd() <= left.size() && difference.rightEnd() <= right.size());
            assertEquals(told, difference.leftStart() - leftAt, difference.rightStart() - rightAt);
            for (; leftAt < difference.leftStart(); leftAt++, rightAt++, kept++) {
                assertEquals(told, leftKeys.get(leftAt), rightKeys.get(rightAt));
            }
            leftAt = difference.leftEnd();
            rightAt = difference.rightEnd();
        }
        assertEquals(told, left.size() - leftAt, right.size() - rightAt);
        for (; leftAt < left.size(); leftAt++, rightAt++, kept++) {
            assertEquals(told, leftKeys.get(leftAt), rightKeys.get(rightAt));
        }
        assertEquals(told, longestCommon(leftKeys, rightKeys), kept);
    }

    /** The length of the longest common subsequence, found the plain way: a table of every pair. */
    private static int longestCommon(List<String> left, List<String> right) {
        int[][] longest = new int[left.size() + 1][right.size() + 1];
        for (int i = 1; i <= left.size(); i++) {
            for (int j = 1; j <= right.size(); j++) {
                longest[i][j] = left.get(i - 1).equals(right.get(j - 1)) ? longest[i - 1][j - 1] + 1
                        : Math.max(longest[i - 1][j], longest[i][j - 1]);
            }
        }
        return longest[left.size()][right.size()];
    }

    /** What two lines share when they are the same under {@code options}, worked out apart from the engine. */
    private static String key(String line, DiffOptions options) {
        String key = line;
        if (options.ignoreLeadingAndTrailingWhitespace()) {
            key = key.strip();
        }
        if (options.ignoreInnerWhitespace()) {
            String inner = key.strip();
            int start = key.indexOf(inner);
            key = key.substring(0, start) + inner.replace(" ", "").replace("\t", "") + key.substring(start + inner.length());
        }
        return options.ignoreCase() ? key.toLowerCase(Locale.ROOT) : key;
    }

    /** {@code lines}, each written one of the ways a loose comparison takes for the same. */
    private static List<String> variants(List<String> lines, Random random) {
        List<String> variants = new ArrayList<>();
        for (String line : lines) {
            String word = line + " " + line;
            variants.add(switch (random.nextInt(4)) {
                case 0 -> word;
                case 1 -> "  " + word.toUpperCase(Locale.ROOT);
                case 2 -> line + "\t " + line + " ";
                default -> line + line;
            });
        }
        return variants;
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
        return randomLines(random, 12, 5);
    }

    /** Fewer than {@code most} lines of {@code kinds} kinds. */
    private static List<String> randomLines(Random random, int most, int kinds) {
        int count = random.nextInt(most);
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(String.valueOf((char) ('a' + random.nextInt(kinds))));
        }
        return lines;
    }

    private static boolean same(String left, String right, DiffOptions options) {
        return Diff.compute(List.of(left), List.of(right), options).isIdentical();
    }

    private static List<Difference> diff(String left, String right) {
        return Diff.compute(List.of(left.split("\\|", -1)), List.of(right.split("\\|", -1))).differences();
    }
}
