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
        return beside(differences, left, true);
    }

    /** The place in the left text beside place {@code right} of the right one; see {@link #toRight}. */
    static double toLeft(List<Difference> differences, double right) {
        return beside(differences, right, false);
    }

    /**
     * The place on the other side beside {@code place} of this one - the left one with
     * {@code fromLeft}, else the right one.
     * <p>
     * A block this side has no lines of - lines added on the other, seen from here - cannot be
     * passed line by line: there is no line here to scroll through while the other side goes
     * through them. Passed in no time, the other side would jump by the whole block. So the line
     * next to where they would go - the one above, or the one below at the top of the text or
     * right after another block - takes them along: while it is scrolled through, the other side
     * goes through its own line and the lines of the block, at the pace that takes.
     */
    private static double beside(List<Difference> differences, double place, boolean fromLeft) {
        int shift = 0;
        for (int i = 0; i < differences.size(); i++) {
            Difference difference = differences.get(i);
            int start = fromLeft ? difference.leftStart() : difference.rightStart();
            int count = fromLeft ? difference.leftCount() : difference.rightCount();
            int otherStart = fromLeft ? difference.rightStart() : difference.leftStart();
            int otherCount = fromLeft ? difference.rightCount() : difference.leftCount();
            if (count == 0) {
                Difference above = i == 0 ? null : differences.get(i - 1);
                int aboveEnd = above == null ? 0 : fromLeft ? above.leftEnd() : above.rightEnd();
                // A line of its own above it, in no block: that one takes the lines along.
                boolean lineAbove = start > 0 && aboveEnd < start;
                if (lineAbove && place >= start - 1 && place < start) {
                    return start - 1 + shift + (place - (start - 1)) * (otherCount + 1);
                }
                if (place < start) {
                    break;
                }
                if (!lineAbove && place < start + 1) {
                    // Else the line below, which the block is passed with.
                    return otherStart + (place - start) * (otherCount + 1);
                }
                shift = otherStart + otherCount - start;
                continue;
            }
            if (place < start) {
                break;
            }
            if (place < start + count) {
                return otherStart + (place - start) * otherCount / count;
            }
            shift = otherStart + otherCount - (start + count);
        }
        return Math.max(0, place + shift);
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
     * The block that is at, or nearest below, line {@code rightLine} of the right text; the last
     * one when they are all above it. For a comparison made again: the block the view was at is
     * found by where it was, not by its number, which another block coming or going changes.
     */
    static int blockAt(List<Difference> differences, int rightLine) {
        for (int i = 0; i < differences.size(); i++) {
            Difference difference = differences.get(i);
            if (difference.rightStart() >= rightLine || difference.rightEnd() > rightLine) {
                return i;
            }
        }
        return differences.size() - 1;
    }

    /**
     * The block to go to from block {@code current} of {@code count}: the next one, or with
     * {@code forward} false the one before; the same one at either end - there is no going round -
     * and from no block ({@code current} below zero) the first one going forward, the last one
     * going back; -1 when there is none.
     */
    static int step(int current, int count, boolean forward) {
        if (count <= 0) {
            return -1;
        }
        if (current < 0) {
            // From no block: the first one going down, the last one going up.
            return forward ? 0 : count - 1;
        }
        return Math.clamp(current + (forward ? 1 : -1), 0, count - 1);
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
            String kind = i < 2 || i == lines.size() ? null
                    // What is said of the line before - that it has no line ending - is part of it.
                    : lines.get(i).startsWith("\\") ? running : unifiedKind(lines.get(i));
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
     * How many lines of a unified diff the block that starts at {@code start} takes: its lines
     * deleted and added, with what is said of their end.
     */
    static int unifiedBlockLength(List<String> lines, int start) {
        int end = start;
        while (end < lines.size() && (lines.get(end).startsWith("-") || lines.get(end).startsWith("+")
                || lines.get(end).startsWith("\\"))) {
            end++;
        }
        return end - start;
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

    /**
     * How the lines of {@code text} end, as it is named: {@code LF}, {@code CRLF}, {@code CR},
     * {@code mixed} for more than one of them, or {@code none} for a text of one line.
     */
    static String lineEnding(String text) {
        boolean lf = false;
        boolean crlf = false;
        boolean cr = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lf = true;
            } else if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    crlf = true;
                    i++;
                } else {
                    cr = true;
                }
            }
        }
        int kinds = (lf ? 1 : 0) + (crlf ? 1 : 0) + (cr ? 1 : 0);
        return kinds > 1 ? "mixed" : lf ? "LF" : crlf ? "CRLF" : cr ? "CR" : "none";
    }
}
