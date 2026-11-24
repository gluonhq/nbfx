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

import java.util.ArrayList;
import java.util.List;
import org.netbeans.api.nbfx.diff.Difference;
import org.netbeans.api.nbfx.editor.LineBand;

/**
 * What the two editors of a diff view need of the differences, without a toolkit: the bands that
 * mark the blocks on each side, and the line of one side that stands beside a line of the other, by
 * which the two are scrolled in step.
 */
final class DiffLines {

    /** Lines only this side has. */
    static final String ADDED = "diff-added";
    /** Lines the other side no longer has. */
    static final String DELETED = "diff-deleted";
    /** Lines that are other lines on the other side. */
    static final String CHANGED = "diff-changed";

    private DiffLines() {
    }

    /**
     * The bands of the left editor: its deleted and changed lines, and a rule where the right side
     * has lines it does not.
     */
    static List<LineBand> leftBands(List<Difference> differences) {
        List<LineBand> bands = new ArrayList<>(differences.size());
        for (Difference difference : differences) {
            bands.add(new LineBand(difference.leftStart(), difference.leftCount(), switch (difference.kind()) {
                case ADD -> ADDED;
                case DELETE -> DELETED;
                case CHANGE -> CHANGED;
            }));
        }
        return bands;
    }

    /**
     * The bands of the right editor: its added and changed lines, and a rule where the left side
     * has lines it does not.
     */
    static List<LineBand> rightBands(List<Difference> differences) {
        List<LineBand> bands = new ArrayList<>(differences.size());
        for (Difference difference : differences) {
            bands.add(new LineBand(difference.rightStart(), difference.rightCount(), switch (difference.kind()) {
                case ADD -> ADDED;
                case DELETE -> DELETED;
                case CHANGE -> CHANGED;
            }));
        }
        return bands;
    }

    /**
     * The place in the right text beside place {@code left} of the left one, a place being a line
     * and the fraction of it passed: the same place where the texts agree; within a block, as far
     * into the block of the other side, in proportion - a block of one line beside one of three is
     * passed three times as fast - so that both sides leave the block together, and scrolling one
     * moves the other without a jump.
     */
    static double toRight(List<Difference> differences, double left) {
        int shift = 0;
        for (Difference difference : differences) {
            if (left < difference.leftStart()) {
                break;
            }
            if (left < difference.leftEnd()) {
                return difference.rightStart()
                        + (left - difference.leftStart()) * difference.rightCount() / difference.leftCount();
            }
            shift = difference.rightEnd() - difference.leftEnd();
        }
        return Math.max(0, left + shift);
    }

    /** The place in the left text beside place {@code right} of the right one; see {@link #toRight}. */
    static double toLeft(List<Difference> differences, double right) {
        int shift = 0;
        for (Difference difference : differences) {
            if (right < difference.rightStart()) {
                break;
            }
            if (right < difference.rightEnd()) {
                return difference.leftStart()
                        + (right - difference.rightStart()) * difference.leftCount() / difference.rightCount();
            }
            shift = difference.leftEnd() - difference.rightEnd();
        }
        return Math.max(0, right + shift);
    }

    /** Where in a right text of {@code lineCount} lines the block starts, from 0 at its top to 1. */
    static double overviewStart(Difference difference, int lineCount) {
        return Math.clamp((double) difference.rightStart() / Math.max(1, lineCount), 0, 1);
    }

    /** How much of a right text of {@code lineCount} lines the block takes, from 0 to 1. */
    static double overviewExtent(Difference difference, int lineCount) {
        return Math.clamp((double) difference.rightCount() / Math.max(1, lineCount), 0, 1);
    }

    /**
     * The block to go to from block {@code current} of {@code count}: the next one, or with
     * {@code forward} false the one before; the same one at either end, and -1 when there is none.
     */
    static int step(int current, int count, boolean forward) {
        if (count <= 0) {
            return -1;
        }
        return Math.clamp(current + (forward ? 1 : -1), 0, count - 1);
    }

}
