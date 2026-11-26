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
import java.util.Objects;
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


    /**
     * The bands of a unified diff, by the first character of its {@code lines}: each run of lines
     * added, of lines deleted, and each line that starts a hunk. The two lines that name the texts
     * - the first two - have none, though they start as lines deleted and added do.
     */
    static List<LineBand> unifiedBands(List<String> lines) {
        List<LineBand> bands = new ArrayList<>();
        int start = 0;
        String running = null;
        for (int i = 0; i <= lines.size(); i++) {
            String kind = i < 2 || i == lines.size() ? null : unifiedKind(lines.get(i));
            if (!Objects.equals(kind, running)) {
                if (running != null) {
                    bands.add(new LineBand(start, i - start, running));
                }
                running = kind;
                start = i;
            }
        }
        return bands;
    }

    /**
     * One change to a text: the characters from {@code start} to {@code end} - offsets that count
     * one character for each line ending - become {@code text}.
     */
    record Edit(int start, int end, String text) {

        /** Whether this changes nothing. */
        boolean isEmpty() {
            return start == end && text.isEmpty();
        }
    }

    /**
     * What makes the block of {@code difference} on the right as it is on the left: its lines of
     * {@code right} taken out, those of {@code left} put in their place.
     */
    static Edit revert(List<String> left, List<String> right, Difference difference) {
        List<String> reverted = new ArrayList<>(right.subList(0, difference.rightStart()));
        reverted.addAll(left.subList(difference.leftStart(), difference.leftEnd()));
        reverted.addAll(right.subList(difference.rightEnd(), right.size()));
        return edit(String.join("\n", right), String.join("\n", reverted));
    }

    /** What makes the text of the lines {@code right} that of the lines {@code left}. */
    static Edit revertAll(List<String> left, List<String> right) {
        return edit(String.join("\n", right), String.join("\n", left));
    }

    /** The least change that makes {@code from} into {@code to}: what they differ in between what they share. */
    private static Edit edit(String from, String to) {
        int shortest = Math.min(from.length(), to.length());
        int start = 0;
        while (start < shortest && from.charAt(start) == to.charAt(start)) {
            start++;
        }
        int tail = 0;
        while (tail < shortest - start
                && from.charAt(from.length() - 1 - tail) == to.charAt(to.length() - 1 - tail)) {
            tail++;
        }
        return new Edit(start, from.length() - tail, to.substring(start, to.length() - tail));
    }

    private static String unifiedKind(String line) {
        return line.startsWith("+") ? ADDED : line.startsWith("-") ? DELETED : line.startsWith("@@") ? CHANGED : null;
    }
}
