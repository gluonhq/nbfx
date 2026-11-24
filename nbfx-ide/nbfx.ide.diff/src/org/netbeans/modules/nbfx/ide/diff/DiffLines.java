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
     * The line of the right text beside line {@code left} of the left one: the same line where the
     * texts agree; within a block, the line as far into the block of the other side, or its last.
     */
    static int toRight(List<Difference> differences, int left) {
        int shift = 0;
        for (Difference difference : differences) {
            if (left < difference.leftStart()) {
                break;
            }
            if (left < difference.leftEnd()) {
                return difference.rightStart() + Math.min(left - difference.leftStart(),
                        Math.max(0, difference.rightCount() - 1));
            }
            shift = difference.rightEnd() - difference.leftEnd();
        }
        return Math.max(0, left + shift);
    }

    /** The line of the left text beside line {@code right} of the right one; see {@link #toRight}. */
    static int toLeft(List<Difference> differences, int right) {
        int shift = 0;
        for (Difference difference : differences) {
            if (right < difference.rightStart()) {
                break;
            }
            if (right < difference.rightEnd()) {
                return difference.leftStart() + Math.min(right - difference.rightStart(),
                        Math.max(0, difference.leftCount() - 1));
            }
            shift = difference.leftEnd() - difference.rightEnd();
        }
        return Math.max(0, right + shift);
    }
}
