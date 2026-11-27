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
import org.openide.util.NbBundle;

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

    /** What happened to the lines of the left text, block by block, in words: "Lines 3 to 4 changed". */
    static List<String> describeLeft(List<Difference> differences) {
        List<String> told = new ArrayList<>();
        for (Difference difference : differences) {
            told.add(switch (difference.kind()) {
                case ADD -> NbBundle.getMessage(DiffLines.class, "Diff.told.addedAfter", difference.leftStart());
                case DELETE -> lines("Diff.told.deleted", difference.leftStart(), difference.leftCount());
                case CHANGE -> lines("Diff.told.changed", difference.leftStart(), difference.leftCount());
            });
        }
        return told;
    }

    /** What happened to the lines of the right text, block by block, in words. */
    static List<String> describeRight(List<Difference> differences) {
        List<String> told = new ArrayList<>();
        for (Difference difference : differences) {
            told.add(switch (difference.kind()) {
                case ADD -> lines("Diff.told.added", difference.rightStart(), difference.rightCount());
                case DELETE -> NbBundle.getMessage(DiffLines.class, "Diff.told.deletedAfter", difference.rightStart());
                case CHANGE -> lines("Diff.told.changed", difference.rightStart(), difference.rightCount());
            });
        }
        return told;
    }

    /** The message of {@code key} for {@code count} lines from {@code start}, counted from one for the reader. */
    private static String lines(String key, int start, int count) {
        return NbBundle.getMessage(DiffLines.class, key, start + 1, start + count, count);
    }
}
