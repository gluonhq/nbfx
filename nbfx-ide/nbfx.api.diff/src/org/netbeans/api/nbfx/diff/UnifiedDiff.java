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
import java.util.List;
import java.util.Objects;

/**
 * The differences between two texts written as a unified diff, the form of {@code diff -u} and
 * {@code git diff}: after two lines that name the texts, a hunk for each group of blocks that lie
 * close together - a line that tells where it is in both texts, then its lines, those only in the
 * left text after a {@code -}, those only in the right one after a {@code +}, and a few lines the
 * texts share around them after a space.
 *
 * @param text the unified diff, its lines ending with a line feed; empty when the texts are the same
 * @param differenceLines for each difference, in their order, the line of {@code text} - counted
 *        from zero - where its lines start
 * @since 1.1
 */
public record UnifiedDiff(String text, List<Integer> differenceLines) {

    /** The lines the texts share that are written around each block, as {@code diff -u} does. */
    public static final int DEFAULT_CONTEXT = 3;

    private static final String NO_NEWLINE = "\\ No newline at end of file";

    /**
     * Checks that the text is there and that each block starts at a line of it, none before the
     * one before it.
     *
     * @throws NullPointerException when the text or the lines are {@code null}
     * @throws IllegalArgumentException when a line is not one of the text, or comes before the
     *         line of the block before it
     */
    public UnifiedDiff {
        Objects.requireNonNull(text, "text");
        differenceLines = List.copyOf(Objects.requireNonNull(differenceLines, "differenceLines"));
        long lines = text.chars().filter(c -> c == '\n').count();
        int before = 0;
        for (int line : differenceLines) {
            if (line < before || line >= lines) {
                throw new IllegalArgumentException("line " + line + " of a block, after " + before + ", in a text of "
                        + lines + " lines");
            }
            before = line;
        }
    }

    /**
     * Writes {@code differences} as a unified diff.
     * <p>
     * The lines are those of {@link Diff#lines}: a text that ends with a line ending has an empty
     * last line, which is not written - it is the end of the line before it - and after the last
     * line of a text that does not end with one comes the line {@code \ No newline at end of
     * file}, as {@code diff} writes it. Two texts that differ in whether their last line ends
     * with a line ending differ in that line: it is written as deleted and as added, though its
     * characters are the same, and the block {@link Diff#compute} found for the missing end is
     * told to start there.
     * <p>
     * The two names are written as {@code git diff} writes paths: one with a quote, a backslash
     * or a character that is not printed is put in quotes, with those written as {@code \"},
     * {@code \\}, {@code \t}, {@code \n}, {@code \r} or by their number, so that a name is one
     * line whatever it holds. Characters beyond ASCII are written as they are.
     *
     * @param leftName what the left text is called in the first line
     * @param rightName what the right text is called in the second line
     * @param left the lines of the text compared from; none for a text that is not there
     * @param right the lines of the text compared to; none for a text that is not there
     * @param differences the blocks in which they differ, as {@link Diff#compute} found them
     * @param context the number of shared lines written before and after each block; blocks whose
     *        shared lines would meet or overlap are written in one hunk
     * @return the unified diff, with the line each of {@code differences} starts at; an empty
     *         one when there are none, or none with a line to write - an empty text against one
     *         that is not there
     * @throws NullPointerException when an argument is {@code null}
     * @throws IllegalArgumentException when {@code context} is negative, or the blocks are not in
     *         order, overlap, or reach beyond the lines of a text
     */
    public static UnifiedDiff of(String leftName, String rightName, List<String> left, List<String> right,
            List<Difference> differences, int context) {
        Objects.requireNonNull(leftName, "leftName");
        Objects.requireNonNull(rightName, "rightName");
        left = List.copyOf(Objects.requireNonNull(left, "left"));
        right = List.copyOf(Objects.requireNonNull(right, "right"));
        differences = List.copyOf(Objects.requireNonNull(differences, "differences"));
        if (context < 0) {
            throw new IllegalArgumentException("context " + context);
        }
        int leftAt = 0;
        int rightAt = 0;
        for (Difference difference : differences) {
            if (difference.leftStart() < leftAt || difference.rightStart() < rightAt
                    || difference.leftEnd() > left.size() || difference.rightEnd() > right.size()) {
                throw new IllegalArgumentException("a block out of order or beyond the text: " + difference);
            }
            leftAt = difference.leftEnd();
            rightAt = difference.rightEnd();
        }
        if (differences.isEmpty()) {
            return new UnifiedDiff("", List.of());
        }
        Writer writer = new Writer(left, right);
        List<Block> asFound = new ArrayList<>();
        for (Difference difference : differences) {
            asFound.add(writer.written(difference));
        }
        List<Block> blocks = writer.blocks(asFound);
        if (blocks.isEmpty()) {
            // No line to write on either side: a file with nothing in it against no file at all.
            return new UnifiedDiff("", List.of());
        }
        writer.lines.add("--- " + quoted(leftName));
        writer.lines.add("+++ " + quoted(rightName));
        int from = 0;
        while (from < blocks.size()) {
            int to = from + 1;
            // As long as the shared lines after one block reach those before the next.
            while (to < blocks.size() && (long) blocks.get(to).leftStart - blocks.get(to - 1).leftEnd <= 2L * context) {
                to++;
            }
            writer.hunk(blocks.subList(from, to), context);
            from = to;
        }
        StringBuilder text = new StringBuilder();
        for (String line : writer.lines) {
            text.append(line).append('\n');
        }
        List<Integer> differenceLines = new ArrayList<>();
        // Both in the order of the texts: the block a difference was written in is the one the
        // difference before it was written in, or a later one - found in one pass over them.
        int within = 0;
        for (Block found : asFound) {
            // Its own block, one it was joined to, or the last line of the texts, written as
            // changed for an end that only one of them has - which is the last block.
            while (within < blocks.size() - 1 && !blocks.get(within).holds(found)) {
                within++;
            }
            differenceLines.add(blocks.get(within).line);
        }
        return new UnifiedDiff(text.toString(), differenceLines);
    }

    /** {@code name} as {@code git diff} writes a path: as it is, or in quotes when it could not be read back. */
    static String quoted(String name) {
        StringBuilder quoted = null;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            String escape = switch (c) {
                case '"' -> "\\\"";
                case '\\' -> "\\\\";
                case '\t' -> "\\t";
                case '\n' -> "\\n";
                case '\r' -> "\\r";
                default -> c < 0x20 || c == 0x7f ? "\\" + String.format("%03o", (int) c) : null;
            };
            if (escape != null && quoted == null) {
                quoted = new StringBuilder("\"").append(name, 0, i);
            }
            if (quoted != null) {
                quoted.append(escape != null ? escape : String.valueOf(c));
            }
        }
        return quoted == null ? name : quoted.append('"').toString();
    }

    /** Lines that differ, of each side those from a start up to an end; and where they are written. */
    private static final class Block {
        int leftStart;
        int leftEnd;
        int rightStart;
        int rightEnd;
        int line;

        Block(int leftStart, int leftEnd, int rightStart, int rightEnd) {
            this.leftStart = leftStart;
            this.leftEnd = leftEnd;
            this.rightStart = rightStart;
            this.rightEnd = rightEnd;
        }

        boolean isEmpty() {
            return leftStart == leftEnd && rightStart == rightEnd;
        }

        /** Whether {@code other} starts within this block, or right at its end. */
        boolean holds(Block other) {
            return leftStart <= other.leftStart && other.leftStart <= leftEnd
                    && rightStart <= other.rightStart && other.rightStart <= rightEnd;
        }
    }

    /** Writes the hunks, counting the lines of each side as they are written. */
    private static final class Writer {

        private final List<String> left;
        private final List<String> right;
        /** The lines there are to write of each side: all but the empty one after its last line ending. */
        private final int leftCount;
        private final int rightCount;
        /** Whether the last line of each side ends with a line ending; so does a side with no line. */
        private final boolean leftEnded;
        private final boolean rightEnded;
        final List<String> lines = new ArrayList<>();
        private int leftWritten;
        private int rightWritten;

        Writer(List<String> left, List<String> right) {
            this.left = left;
            this.right = right;
            leftEnded = left.isEmpty() || left.getLast().isEmpty();
            rightEnded = right.isEmpty() || right.getLast().isEmpty();
            leftCount = left.isEmpty() ? 0 : leftEnded ? left.size() - 1 : left.size();
            rightCount = right.isEmpty() ? 0 : rightEnded ? right.size() - 1 : right.size();
        }

        /** {@code difference} without the empty line after a last line ending, which is not written. */
        Block written(Difference difference) {
            return new Block(Math.min(difference.leftStart(), leftCount), Math.min(difference.leftEnd(), leftCount),
                    Math.min(difference.rightStart(), rightCount), Math.min(difference.rightEnd(), rightCount));
        }

        /**
         * The blocks to write: those found that have a line to write, and the last line of a
         * text where the two share its characters and not its end - one ends with a line ending
         * there and the other does not - which is a line that differs.
         */
        List<Block> blocks(List<Block> asFound) {
            List<Block> blocks = new ArrayList<>();
            for (Block found : asFound) {
                if (!found.isEmpty()) {
                    blocks.add(new Block(found.leftStart, found.leftEnd, found.rightStart, found.rightEnd));
                }
            }
            if (!leftEnded && leftCount > 0) {
                int partner = partner(blocks, leftCount - 1, true);
                if (partner >= 0 && !(partner == rightCount - 1 && !rightEnded)) {
                    blocks.add(new Block(leftCount - 1, leftCount, partner, partner + 1));
                }
            }
            blocks.sort((one, other) -> Integer.compare(one.leftStart, other.leftStart));
            if (!rightEnded && rightCount > 0) {
                int partner = partner(blocks, rightCount - 1, false);
                if (partner >= 0 && !(partner == leftCount - 1 && !leftEnded)) {
                    blocks.add(new Block(partner, partner + 1, rightCount - 1, rightCount));
                }
            }
            blocks.sort((one, other) -> one.leftStart != other.leftStart
                    ? Integer.compare(one.leftStart, other.leftStart) : Integer.compare(one.rightStart, other.rightStart));
            // Blocks with no shared line between them are one.
            List<Block> joined = new ArrayList<>();
            for (Block block : blocks) {
                Block before = joined.isEmpty() ? null : joined.getLast();
                if (before != null && before.leftEnd == block.leftStart && before.rightEnd == block.rightStart) {
                    before.leftEnd = block.leftEnd;
                    before.rightEnd = block.rightEnd;
                } else {
                    joined.add(block);
                }
            }
            return joined;
        }

        /**
         * The line of the other side that the shared {@code line} of one side - the left one, or
         * the right one - stands beside; -1 when it is in a block, and shared with none.
         */
        private static int partner(List<Block> blocks, int line, boolean ofLeft) {
            int shift = 0;
            for (Block block : blocks) {
                int start = ofLeft ? block.leftStart : block.rightStart;
                int end = ofLeft ? block.leftEnd : block.rightEnd;
                if (line >= start && line < end) {
                    return -1;
                }
                if (end <= line) {
                    int grown = (block.rightEnd - block.rightStart) - (block.leftEnd - block.leftStart);
                    shift += ofLeft ? grown : -grown;
                }
            }
            return line + shift;
        }

        void hunk(List<Block> blocks, int context) {
            Block first = blocks.getFirst();
            Block last = blocks.getLast();
            int leftFrom = Math.max(0, first.leftStart - context);
            int rightFrom = first.rightStart - (first.leftStart - leftFrom);
            int leftTo = (int) Math.min(leftCount, (long) last.leftEnd + context);
            int header = lines.size();
            lines.add("");
            leftWritten = 0;
            rightWritten = 0;
            int at = leftFrom;
            for (Block block : blocks) {
                for (; at < block.leftStart; at++) {
                    shared(at);
                }
                block.line = lines.size();
                for (int line = block.leftStart; line < block.leftEnd; line++) {
                    lines.add("-" + left.get(line));
                    leftWritten++;
                    if (line == leftCount - 1 && !leftEnded) {
                        lines.add(NO_NEWLINE);
                    }
                }
                for (int line = block.rightStart; line < block.rightEnd; line++) {
                    lines.add("+" + right.get(line));
                    rightWritten++;
                    if (line == rightCount - 1 && !rightEnded) {
                        lines.add(NO_NEWLINE);
                    }
                }
                at = block.leftEnd;
            }
            for (; at < leftTo; at++) {
                shared(at);
            }
            lines.set(header, "@@ -" + range(leftFrom, leftWritten) + " +" + range(rightFrom, rightWritten) + " @@");
        }

        /** A line the texts share, by its place in the left one: with its end, or without it on both sides. */
        private void shared(int line) {
            lines.add(" " + left.get(line));
            leftWritten++;
            rightWritten++;
            if (line == leftCount - 1 && !leftEnded) {
                lines.add(NO_NEWLINE);
            }
        }

        /** Where the lines of a side start, counted from one - from the line before when there are none - and how many. */
        private static String range(int from, int count) {
            return (count == 0 ? from : from + 1) + "," + count;
        }
    }
}
