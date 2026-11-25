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
}
