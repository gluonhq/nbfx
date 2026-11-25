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

    /** Both are required. */
    public UnifiedDiff {
        Objects.requireNonNull(text, "text");
        differenceLines = List.copyOf(differenceLines);
    }

    /**
     * Writes {@code differences} as a unified diff.
     * <p>
     * The lines are those of {@link Diff#lines}: a text that ends with a line ending has an empty
     * last line, which is not written - it is the end of the line before it - and after the last
     * line of a text that does not end with one comes the line {@code \ No newline at end of
     * file}, as {@code diff} writes it.
     *
     * @param leftName what the left text is called in the first line
     * @param rightName what the right text is called in the second line
     * @param left the lines of the text compared from
     * @param right the lines of the text compared to
     * @param differences the blocks in which they differ, as {@link Diff#compute} found them
     * @param context the number of shared lines written before and after each block; blocks whose
     *        shared lines would meet or overlap are written in one hunk
     * @return the unified diff
     */
    public static UnifiedDiff of(String leftName, String rightName, List<String> left, List<String> right,
            List<Difference> differences, int context) {
        Objects.requireNonNull(leftName, "leftName");
        Objects.requireNonNull(rightName, "rightName");
        if (context < 0) {
            throw new IllegalArgumentException("context " + context);
        }
        if (differences.isEmpty()) {
            return new UnifiedDiff("", List.of());
        }
        Writer writer = new Writer(left, right);
        writer.lines.add("--- " + leftName);
        writer.lines.add("+++ " + rightName);
        int from = 0;
        while (from < differences.size()) {
            int to = from + 1;
            // As long as the shared lines after one block reach those before the next.
            while (to < differences.size()
                    && differences.get(to).leftStart() - differences.get(to - 1).leftEnd() <= 2 * context) {
                to++;
            }
            writer.hunk(differences.subList(from, to), context);
            from = to;
        }
        StringBuilder text = new StringBuilder();
        for (String line : writer.lines) {
            text.append(line).append('\n');
        }
        return new UnifiedDiff(text.toString(), writer.differenceLines);
    }

    /** Writes the hunks, counting the lines of each side as they are written. */
    private static final class Writer {

        private final List<String> left;
        private final List<String> right;
        final List<String> lines = new ArrayList<>();
        final List<Integer> differenceLines = new ArrayList<>();
        private int leftWritten;
        private int rightWritten;

        Writer(List<String> left, List<String> right) {
            this.left = left;
            this.right = right;
        }

        void hunk(List<Difference> blocks, int context) {
            Difference first = blocks.getFirst();
            Difference last = blocks.getLast();
            int leftFrom = Math.max(0, first.leftStart() - context);
            int rightFrom = first.rightStart() - (first.leftStart() - leftFrom);
            int leftTo = Math.min(left.size(), last.leftEnd() + context);
            int header = lines.size();
            lines.add("");
            leftWritten = 0;
            rightWritten = 0;
            int at = leftFrom;
            for (Difference block : blocks) {
                for (; at < block.leftStart(); at++) {
                    shared(at);
                }
                differenceLines.add(lines.size());
                for (int line = block.leftStart(); line < block.leftEnd(); line++) {
                    only('-', left, line);
                }
                for (int line = block.rightStart(); line < block.rightEnd(); line++) {
                    only('+', right, line);
                }
                at = block.leftEnd();
            }
            for (; at < leftTo; at++) {
                shared(at);
            }
            lines.set(header, "@@ -" + range(leftFrom, leftWritten) + " +" + range(rightFrom, rightWritten) + " @@");
        }

        /** A line the texts share, by its place in the left one. */
        private void shared(int line) {
            if (isEnd(left, line)) {
                return;
            }
            lines.add(" " + left.get(line));
            leftWritten++;
            rightWritten++;
            if (line == left.size() - 1) {
                lines.add(NO_NEWLINE);
            }
        }

        private void only(char mark, List<String> side, int line) {
            if (isEnd(side, line)) {
                return;
            }
            lines.add(mark + side.get(line));
            if (mark == '-') {
                leftWritten++;
            } else {
                rightWritten++;
            }
            if (line == side.size() - 1) {
                lines.add(NO_NEWLINE);
            }
        }

        /**
         * Whether {@code line} is the empty one after the last line ending of {@code side} - or
         * the only one of an empty text, which has no lines to write.
         */
        private static boolean isEnd(List<String> side, int line) {
            return line == side.size() - 1 && side.get(line).isEmpty();
        }

        /** Where the lines of a side start, counted from one - from the line before when there are none - and how many. */
        private static String range(int from, int count) {
            return (count == 0 ? from : from + 1) + "," + count;
        }
    }
}
