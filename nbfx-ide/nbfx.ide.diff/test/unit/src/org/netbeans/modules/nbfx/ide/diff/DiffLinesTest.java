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
package org.netbeans.modules.nbfx.ide.diff;

import java.util.List;
import org.junit.Test;
import org.netbeans.api.nbfx.diff.Diff;
import org.netbeans.api.nbfx.diff.Difference;
import org.netbeans.api.nbfx.editor.LineBand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies the bands of the two sides of a diff and the lines that stand beside each other. */
public class DiffLinesTest {

    /** a b [c d -> x] e [+ y z] f [- g] h */
    private static final List<Difference> DIFFERENCES = Diff.compute(
            List.of("a", "b", "c", "d", "e", "f", "g", "h"),
            List.of("a", "b", "x", "e", "y", "z", "f", "h")).differences();

    @Test
    public void bandsMarkTheBlocksAndRuleTheGaps() {
        assertEquals(List.of(new LineBand(2, 2, DiffLines.CHANGED), new LineBand(5, 0, DiffLines.ADDED),
                new LineBand(6, 1, DiffLines.DELETED)), DiffLines.leftBands(DIFFERENCES));
        assertEquals(List.of(new LineBand(2, 1, DiffLines.CHANGED), new LineBand(4, 2, DiffLines.ADDED),
                new LineBand(7, 0, DiffLines.DELETED)), DiffLines.rightBands(DIFFERENCES));
    }

    @Test
    public void theBlocksOfEachSideAreToldInWords() {
        // left:  a0 b1 c2 d3 e4 f5 g6 h7      right: a0 b1 x2 e3 y4 z5 f6 h7   (told from one)
        assertEquals(List.of("Lines 3 to 4 changed", "Lines added after line 5", "Line 7 deleted"),
                DiffLines.describeLeft(DIFFERENCES));
        assertEquals(List.of("Line 3 changed", "Lines 5 to 6 added", "Lines deleted after line 7"),
                DiffLines.describeRight(DIFFERENCES));
        assertEquals(List.of(), DiffLines.describeLeft(List.of()));
    }

    @Test
    public void sameLinesStandBesideEachOther() {
        // left:  a0 b1 c2 d3 e4 f5 g6 h7      right: a0 b1 x2 e3 y4 z5 f6 h7
        // Two changed lines beside one are passed at half the pace; a deleted one is passed in no time.
        assertEquals(List.of(0.0, 1.0, 2.0, 2.5, 3.0, 6.0, 7.0, 7.0), places(true));
        // Added lines have nothing beside them: the other side waits where they would go.
        assertEquals(List.of(0.0, 1.0, 2.0, 4.0, 5.0, 5.0, 5.0, 7.0), places(false));
    }

    @Test
    public void placesWithinALineKeepTheirFraction() {
        // Where the texts agree, half a line is half a line.
        assertEquals(1.5, DiffLines.toRight(DIFFERENCES, 1.5), 1e-9);
        assertEquals(6.25, DiffLines.toRight(DIFFERENCES, 5.25), 1e-9);
        // In the block of two lines beside one, half way through the two is half way through the one.
        assertEquals(2.5, DiffLines.toRight(DIFFERENCES, 3.0), 1e-9);
        assertEquals(2.75, DiffLines.toRight(DIFFERENCES, 3.5), 1e-9);
        assertEquals(3.0, DiffLines.toLeft(DIFFERENCES, 2.5), 1e-9);
        // No jump on leaving a block: just before its end is just before the end of the other.
        assertEquals(3.0, DiffLines.toRight(DIFFERENCES, 3.999), 1e-2);
        assertEquals(5.0, DiffLines.toLeft(DIFFERENCES, 5.999), 1e-9);
    }

    @Test
    public void aBlockOneSideHasNoLinesOfIsPassedWithTheLineNextToItAndNotInAJump() {
        // left:  a0 b1 c2 d3 e4 f5 g6 h7      right: a0 b1 x2 e3 y4 z5 f6 h7
        // The right text has nothing where g was deleted, before its line 7: the line above, f,
        // takes it along - through f the left goes through f and g, at twice the pace.
        assertEquals(5.0, DiffLines.toLeft(DIFFERENCES, 6.0), 1e-9);
        assertEquals(6.0, DiffLines.toLeft(DIFFERENCES, 6.5), 1e-9);
        assertEquals(7.0, DiffLines.toLeft(DIFFERENCES, 7.0), 1e-9);
        // And the left text has nothing where y and z were added, before its line 5: through e,
        // the line above, the right goes through e, y and z.
        assertEquals(3.0, DiffLines.toRight(DIFFERENCES, 4.0), 1e-9);
        assertEquals(4.5, DiffLines.toRight(DIFFERENCES, 4.5), 1e-9);
        assertEquals(6.0, DiffLines.toRight(DIFFERENCES, 5.0), 1e-9);

        // No jump anywhere: a small step on one side is never a leap on the other.
        for (double place = 0; place < 8; place += 0.01) {
            assertTrue("left jumps at " + place, DiffLines.toLeft(DIFFERENCES, place + 0.01)
                    - DiffLines.toLeft(DIFFERENCES, place) <= 0.01 * 3 + 1e-9);
            assertTrue("right jumps at " + place, DiffLines.toRight(DIFFERENCES, place + 0.01)
                    - DiffLines.toRight(DIFFERENCES, place) <= 0.01 * 3 + 1e-9);
            // And it never goes back.
            assertTrue(DiffLines.toLeft(DIFFERENCES, place + 0.01) >= DiffLines.toLeft(DIFFERENCES, place) - 1e-9);
        }
    }

    @Test
    public void linesMissingAtTheTopOrRightAfterABlockArePassedWithTheLineBelow() {
        // Deleted at the very top: the right text has no line above where they were.
        List<Difference> top = Diff.compute(List.of("x", "y", "a", "b"), List.of("a", "b")).differences();
        assertEquals(0.0, DiffLines.toLeft(top, 0.0), 1e-9);
        assertEquals(1.5, DiffLines.toLeft(top, 0.5), 1e-9);
        assertEquals(3.0, DiffLines.toLeft(top, 1.0), 1e-9);
        assertEquals(3.5, DiffLines.toLeft(top, 1.5), 1e-9);
        for (double place = 0; place < 2; place += 0.01) {
            assertTrue("jumps at " + place,
                    DiffLines.toLeft(top, place + 0.01) - DiffLines.toLeft(top, place) <= 0.01 * 3 + 1e-9);
        }
    }

    @Test
    public void theBlockTheViewWasAtIsFoundAgainByWhereItIs() {
        // Blocks at right lines 2, 4-5 and 7.
        assertEquals(0, DiffLines.blockAt(DIFFERENCES, 0));
        assertEquals(0, DiffLines.blockAt(DIFFERENCES, 2));
        assertEquals(1, DiffLines.blockAt(DIFFERENCES, 3));
        assertEquals(1, DiffLines.blockAt(DIFFERENCES, 5));
        assertEquals(2, DiffLines.blockAt(DIFFERENCES, 6));
        // Below them all: the last one.
        assertEquals(2, DiffLines.blockAt(DIFFERENCES, 100));
        assertEquals(-1, DiffLines.blockAt(List.of(), 3));
        // From no block, the first one going down and the last one going up.
        assertEquals(0, DiffLines.step(-1, 3, true));
        assertEquals(2, DiffLines.step(-1, 3, false));
        assertEquals(-1, DiffLines.step(-1, 0, false));
    }

    @Test
    public void withoutDifferencesALineIsBesideItself() {
        assertEquals(5, DiffLines.toRight(List.of(), 5), 1e-9);
        assertEquals(5.5, DiffLines.toLeft(List.of(), 5.5), 1e-9);
    }

    @Test
    public void stepsStopAtTheFirstAndTheLastBlock() {
        assertEquals(1, DiffLines.step(0, 3, true));
        assertEquals(2, DiffLines.step(2, 3, true));
        assertEquals(0, DiffLines.step(0, 3, false));
        assertEquals(1, DiffLines.step(2, 3, false));
        // From nowhere, forward is the first block.
        assertEquals(0, DiffLines.step(-1, 3, true));
        assertEquals(-1, DiffLines.step(0, 0, true));
    }

    @Test
    public void overviewPlacesABlockByItsLinesInTheRightText() {
        Difference added = DIFFERENCES.get(1);

        assertEquals(0.5, DiffLines.overviewStart(added, 8), 1e-9);
        assertEquals(0.25, DiffLines.overviewExtent(added, 8), 1e-9);
        // A deletion has no lines on the right: a mark of no extent, where they were.
        assertEquals(0, DiffLines.overviewExtent(DIFFERENCES.get(2), 8), 1e-9);
        assertEquals(1, DiffLines.overviewStart(new Difference(Difference.Kind.DELETE, 0, 1, 9, 0), 8), 1e-9);
    }

    private static List<Double> places(boolean toRight) {
        return java.util.stream.IntStream.range(0, 8)
                .mapToObj(line -> toRight ? DiffLines.toRight(DIFFERENCES, line) : DiffLines.toLeft(DIFFERENCES, line))
                .toList();
    }
}
