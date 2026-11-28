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

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** How the differences between two texts are written as a unified diff. */
public class UnifiedDiffTest {

    @Test
    public void aChangeIsWrittenWithTheLinesAroundIt() {
        UnifiedDiff diff = unified("a\nb\nc\nd\ne\nf\ng\nh\ni\n", "a\nb\nc\nd\nE\nf\ng\nh\ni\n", 3);

        assertEquals("""
                --- old
                +++ new
                @@ -2,7 +2,7 @@
                 b
                 c
                 d
                -e
                +E
                 f
                 g
                 h
                """, diff.text());
        assertEquals(List.of(6), diff.differenceLines());
    }

    @Test
    public void blocksCloseTogetherShareAHunkAndThoseApartHaveTheirOwn() {
        String left = "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n";
        UnifiedDiff near = unified(left, "1\nB\n3\n4\nE\n6\n7\n8\n9\n10\n11\n12\n", 1);
        assertEquals("""
                --- old
                +++ new
                @@ -1,6 +1,6 @@
                 1
                -2
                +B
                 3
                 4
                -5
                +E
                 6
                """, near.text());
        assertEquals(List.of(4, 8), near.differenceLines());

        UnifiedDiff apart = unified(left, "1\nB\n3\n4\n5\n6\n7\n8\n9\n10\nK\n12\n", 1);
        assertEquals("""
                --- old
                +++ new
                @@ -1,3 +1,3 @@
                 1
                -2
                +B
                 3
                @@ -10,3 +10,3 @@
                 10
                -11
                +K
                 12
                """, apart.text());
        assertEquals(List.of(4, 9), apart.differenceLines());
    }

    @Test
    public void linesAddedAndDeletedCountOnOneSideOnly() {
        assertEquals("""
                --- old
                +++ new
                @@ -1,2 +1,3 @@
                 a
                +x
                 b
                """, unified("a\nb\n", "a\nx\nb\n", 3).text());
        assertEquals("""
                --- old
                +++ new
                @@ -1,3 +1,2 @@
                 a
                -x
                 b
                """, unified("a\nx\nb\n", "a\nb\n", 3).text());
        // Of nothing: the lines start after line zero.
        assertEquals("""
                --- old
                +++ new
                @@ -0,0 +1,2 @@
                +a
                +b
                """, unified("", "a\nb\n", 3).text());
    }

    @Test
    public void aTextThatDoesNotEndWithALineEndingSaysSo() {
        assertEquals("""
                --- old
                +++ new
                @@ -1,2 +1,2 @@
                 a
                -b
                +c
                \\ No newline at end of file
                """, unified("a\nb\n", "a\nc", 3).text());
    }

    @Test
    public void sameTextsHaveNoDiff() {
        UnifiedDiff diff = unified("a\nb\n", "a\nb\n", 3);

        assertEquals("", diff.text());
        assertTrue(diff.differenceLines().isEmpty());
    }

    private static UnifiedDiff unified(String left, String right, int context) {
        List<String> from = Diff.lines(left);
        List<String> to = Diff.lines(right);
        return UnifiedDiff.of("old", "new", from, to, Diff.compute(from, to).differences(), context);
    }

    @Test
    public void aLastLineThatLosesOrGainsItsEndIsALineThatDiffers() {
        // Only the right text lacks it: the line is the same, and yet not.
        UnifiedDiff lost = unified("a\n", "a", 3);
        assertEquals("""
                --- old
                +++ new
                @@ -1,1 +1,1 @@
                -a
                +a
                \\ No newline at end of file
                """, lost.text());
        // The block found for the missing end starts at that line.
        assertEquals(List.of(3), lost.differenceLines());

        assertEquals("""
                --- old
                +++ new
                @@ -1,1 +1,1 @@
                -a
                \\ No newline at end of file
                +a
                """, unified("a", "a\n", 3).text());

        // With a line added after it, as git writes it: one block.
        assertEquals("""
                --- old
                +++ new
                @@ -1,1 +1,2 @@
                -a
                \\ No newline at end of file
                +a
                +b
                """, unified("a", "a\nb\n", 3).text());

        // Neither ends with one: nothing differs there, and it is said of the shared line.
        assertEquals("""
                --- old
                +++ new
                @@ -1,2 +1,2 @@
                -a
                +x
                 b
                \\ No newline at end of file
                """, unified("a\nb", "x\nb", 3).text());
    }

    @Test
    public void anEndLostFarFromAnotherChangeHasAHunkOfItsOwn() {
        UnifiedDiff diff = unified("a\nb\nc\nd\ne\nf\ng\nh\ni\nj\n", "A\nb\nc\nd\ne\nf\ng\nh\ni\nj", 1);

        assertEquals("""
                --- old
                +++ new
                @@ -1,2 +1,2 @@
                -a
                +A
                 b
                @@ -9,2 +9,2 @@
                 i
                -j
                +j
                \\ No newline at end of file
                """, diff.text());
        assertEquals(List.of(3, 8), diff.differenceLines());
    }

    @Test
    public void allOfATextGoneAndNoLinesAroundAChange() {
        assertEquals("""
                --- old
                +++ new
                @@ -1,2 +0,0 @@
                -a
                -b
                """, unified("a\nb\n", "", 3).text());
        assertEquals("""
                --- old
                +++ new
                @@ -2,1 +2,1 @@
                -b
                +x
                """, unified("a\nb\nc\n", "a\nx\nc\n", 0).text());
        // A text that is not there has no lines at all.
        assertEquals("""
                --- old
                +++ new
                @@ -0,0 +1,1 @@
                +a
                """, UnifiedDiff.of("old", "new", List.of(), List.of("a", ""),
                        List.of(new Difference(Difference.Kind.ADD, 0, 0, 0, 2)), 3).text());
        // How the lines end is not part of them: texts that differ in that alone have no diff.
        assertEquals("", unified("a\nb\n", "a\r\nb\r\n", 3).text());
    }

    @Test
    public void aNameIsOneLineWhateverItHolds() {
        assertEquals("a/src/App.java", UnifiedDiff.quoted("a/src/App.java"));
        assertEquals("a/src/café 日本.txt", UnifiedDiff.quoted("a/src/café 日本.txt"));
        assertEquals("\"a/two\\nlines.txt\"", UnifiedDiff.quoted("a/two\nlines.txt"));
        assertEquals("\"a/tab\\there \\\"quoted\\\" back\\\\slash\\r\\001\\177\"",
                UnifiedDiff.quoted("a/tab\there \"quoted\" back\\slash\r\u0001\u007f"));

        List<String> from = Diff.lines("a\n");
        List<String> to = Diff.lines("b\n");
        UnifiedDiff diff = UnifiedDiff.of("a/x\n+++ b/injected", "b/x", from, to, Diff.compute(from, to).differences(), 3);
        assertEquals("""
                --- "a/x\\n+++ b/injected"
                +++ b/x
                @@ -1,1 +1,1 @@
                -a
                +b
                """, diff.text());
    }

    @Test
    public void whatIsNotAComparisonIsRefused() {
        List<String> one = List.of("a", "");
        List<Difference> none = List.of();
        assertThrows(NullPointerException.class, () -> UnifiedDiff.of(null, "new", one, one, none, 3));
        assertThrows(NullPointerException.class, () -> UnifiedDiff.of("old", null, one, one, none, 3));
        assertThrows(NullPointerException.class, () -> UnifiedDiff.of("old", "new", null, one, none, 3));
        assertThrows(NullPointerException.class, () -> UnifiedDiff.of("old", "new", one, null, none, 3));
        assertThrows(NullPointerException.class, () -> UnifiedDiff.of("old", "new", one, one, null, 3));
        assertThrows(IllegalArgumentException.class, () -> UnifiedDiff.of("old", "new", one, one, none, -1));
        // Beyond the text, and out of order.
        assertThrows(IllegalArgumentException.class, () -> UnifiedDiff.of("old", "new", one, one,
                List.of(new Difference(Difference.Kind.CHANGE, 5, 1, 0, 1)), 3));
        List<String> three = List.of("a", "b", "c", "");
        assertThrows(IllegalArgumentException.class, () -> UnifiedDiff.of("old", "new", three, three,
                List.of(new Difference(Difference.Kind.CHANGE, 2, 1, 2, 1),
                        new Difference(Difference.Kind.CHANGE, 0, 1, 0, 1)), 3));

        assertThrows(NullPointerException.class, () -> new UnifiedDiff(null, List.of()));
        assertThrows(NullPointerException.class, () -> new UnifiedDiff("", null));
        assertThrows(IllegalArgumentException.class, () -> new UnifiedDiff("a\n", List.of(1)));
        assertThrows(IllegalArgumentException.class, () -> new UnifiedDiff("a\nb\nc\n", List.of(2, 1)));
        assertThrows(IllegalArgumentException.class, () -> new UnifiedDiff("a\n", List.of(-1)));
        // The lines are those given, not a list that can be changed afterwards.
        List<Integer> lines = new java.util.ArrayList<>(List.of(0));
        UnifiedDiff diff = new UnifiedDiff("a\nb\n", lines);
        lines.set(0, 1);
        assertEquals(List.of(0), diff.differenceLines());
        assertThrows(UnsupportedOperationException.class, () -> diff.differenceLines().add(1));
    }
}
