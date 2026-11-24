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
    public void sameLinesStandBesideEachOther() {
        // left:  a0 b1 c2 d3 e4 f5 g6 h7      right: a0 b1 x2 e3 y4 z5 f6 h7
        assertEquals(List.of(0, 1, 2, 2, 3, 6, 7, 7), lines(true));
        assertEquals(List.of(0, 1, 2, 4, 5, 5, 5, 7), lines(false));
    }

    @Test
    public void withoutDifferencesALineIsBesideItself() {
        assertEquals(5, DiffLines.toRight(List.of(), 5));
        assertEquals(5, DiffLines.toLeft(List.of(), 5));
    }

    private static List<Integer> lines(boolean toRight) {
        return java.util.stream.IntStream.range(0, 8)
                .mapToObj(line -> toRight ? DiffLines.toRight(DIFFERENCES, line) : DiffLines.toLeft(DIFFERENCES, line))
                .toList();
    }
}
