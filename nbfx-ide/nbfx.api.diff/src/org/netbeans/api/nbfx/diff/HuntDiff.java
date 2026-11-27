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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * The line comparison of {@link Diff}: the Hunt-McIlroy longest common subsequence, ported from the
 * {@code HuntDiff} of NetBeans' diff module (by Maros Sandor and Martin Entlicher), which is bound
 * to Swing through its module but not in this algorithm. The search for the common lines is theirs;
 * the blocks it is turned into count lines from zero, as {@link Difference} does.
 */
final class HuntDiff {

    private static final int FINAL_SIGMA = 0x3C2;
    private static final int SIGMA = 0x3C3;

    private HuntDiff() {
    }

    static List<Difference> diff(String[] left, String[] right, DiffOptions options, BooleanSupplier cancelled) {
        Poll poll = new Poll(cancelled);
        poll.now();
        int[] common = commonLines(left, right, options, poll);
        return merged(differences(common, left.length, right.length, poll));
    }

    /**
     * Asks whether the comparison is still wanted, and gives it up when it is not. It is asked in
     * every loop over the lines - a text of repeated lines makes some of them long - but passes
     * the question on only now and then, as the answer may cost more than a step of the loop.
     */
    private static final class Poll {

        private static final int EVERY = 0xFF;
        private final BooleanSupplier cancelled;
        private int steps;

        Poll(BooleanSupplier cancelled) {
            this.cancelled = cancelled;
        }

        /** Asks now. */
        void now() {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
        }

        /** Counts a step, and asks after enough of them. */
        void step() {
            if ((++steps & EVERY) == 0) {
                now();
            }
        }
    }

    /**
     * For each line of {@code lines1}, counted from one, the line of {@code lines2} it is the same
     * as in the longest common subsequence, or zero.
     */
    private static int[] commonLines(String[] lines1, String[] lines2, DiffOptions options, Poll poll) {
        int m = lines1.length;
        int n = lines2.length;
        if (options.ignoreCase() || options.ignoreInnerWhitespace()
                || options.ignoreLeadingAndTrailingWhitespace()) {
            lines1 = normalized(lines1, options, poll);
            lines2 = normalized(lines2, options, poll);
        }

        // The lines of the second text <1, n>, sorted, so that equal lines are neighbours.
        Line[] sorted = new Line[n + 1];
        for (int i = 1; i <= n; i++) {
            sorted[i] = new Line(i, lines2[i - 1]);
        }
        // The sort is given up from its comparator: it is most of the time of a text of long lines.
        Arrays.sort(sorted, 1, n + 1, (one, other) -> {
            poll.step();
            return one.text().compareTo(other.text());
        });
        poll.now();

        int[] equivalenceLines = new int[n + 1];
        boolean[] equivalence = new boolean[n + 1];
        // Where the lines equal to a text start among the sorted ones: found once for each text,
        // not looked for again for every line of the first text that has it.
        Map<String, Integer> firstOfEqual = new HashMap<>();
        equivalenceLines[0] = 0;
        equivalence[0] = true;
        for (int i = 1; i <= n; i++) {
            poll.step();
            Line line = sorted[i];
            equivalenceLines[i] = line.number();
            equivalence[i] = i == n || !line.text().equals(sorted[i + 1].text());
            if (equivalence[i - 1]) {
                firstOfEqual.put(line.text(), i);
            }
        }
        int[] equivalenceAssoc = new int[m + 1];
        for (int i = 1; i <= m; i++) {
            poll.step();
            equivalenceAssoc[i] = firstOfEqual.getOrDefault(lines1[i - 1], 0);
        }

        Candidate[] candidates = new Candidate[Math.min(m, n) + 2];
        candidates[0] = new Candidate(0, 0, null);
        candidates[1] = new Candidate(m + 1, n + 1, null);
        int k = 0;
        for (int i = 1; i <= m; i++) {
            poll.step();
            if (equivalenceAssoc[i] != 0) {
                k = merge(candidates, k, i, equivalenceLines, equivalence, equivalenceAssoc[i], poll);
            }
        }
        int[] common = new int[m + 2];
        for (Candidate c = candidates[k]; c != null; c = c.previous()) {
            poll.step();
            common[c.a()] = c.b();
        }
        return common;
    }

    private static String[] normalized(String[] lines, DiffOptions options, Poll poll) {
        String[] normalized = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            poll.step();
            String line = normalized(lines[i], options.ignoreLeadingAndTrailingWhitespace(),
                    options.ignoreInnerWhitespace());
            normalized[i] = options.ignoreCase() ? caseFolded(line) : line;
        }
        return normalized;
    }

    /**
     * {@code line} with each letter in the form that its cases share: its lower case, letter by
     * letter, so that no letter becomes two - a sharp s is not "ss", though its capital is a sharp
     * s - and a dotless i stays what it is. The sigma that ends a word is a sigma.
     */
    static String caseFolded(String line) {
        StringBuilder folded = null;
        for (int i = 0; i < line.length();) {
            int letter = line.codePointAt(i);
            int lower = letter == FINAL_SIGMA ? SIGMA : Character.toLowerCase(letter);
            if (lower != letter && folded == null) {
                folded = new StringBuilder(line.length()).append(line, 0, i);
            }
            if (folded != null) {
                folded.appendCodePoint(lower);
            }
            i += Character.charCount(letter);
        }
        return folded == null ? line : folded.toString();
    }

    /**
     * {@code line} without the white space the options leave out: each option takes its own part
     * of the line, so that both together take what either does. White space is what
     * {@link Character#isWhitespace(int)} says, at the edges and within alike; a line of nothing
     * else is all edge.
     */
    private static String normalized(String line, boolean ignoreEdges, boolean ignoreInner) {
        int length = line.length();
        int first = 0;
        while (first < length && Character.isWhitespace(line.codePointAt(first))) {
            first += Character.charCount(line.codePointAt(first));
        }
        int last = length;
        while (last > first && Character.isWhitespace(line.codePointBefore(last))) {
            last -= Character.charCount(line.codePointBefore(last));
        }
        StringBuilder result = new StringBuilder(length);
        if (!ignoreEdges) {
            result.append(line, 0, first);
        }
        if (ignoreInner) {
            line.substring(first, last).codePoints().filter(c -> !Character.isWhitespace(c))
                    .forEach(result::appendCodePoint);
        } else {
            result.append(line, first, last);
        }
        if (!ignoreEdges) {
            result.append(line, last, length);
        }
        return result.toString();
    }

    private static int binarySearch(Candidate[] candidates, int key, int low, int high) {
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int value = candidates[mid].b();
            if (value < key) {
                low = mid + 1;
            } else if (value > key) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    private static int merge(Candidate[] candidates, int k, int i, int[] equivalenceLines,
            boolean[] equivalence, int p, Poll poll) {
        int r = 0;
        Candidate c = candidates[0];
        while (true) {
            // As long as the lines of the second text equal to this one are many.
            poll.step();
            int j = equivalenceLines[p];
            int s = binarySearch(candidates, j, r, k);
            if (s >= 0) {
                // j was found among the candidates
                s = k + 1;
            } else {
                s = -s - 2;
                if (s < r || s > k) {
                    s = k + 1;
                }
            }
            if (s <= k) {
                if (candidates[s + 1].b() > j) {
                    Candidate found = new Candidate(i, j, candidates[s]);
                    candidates[r] = c;
                    r = s + 1;
                    c = found;
                }
                if (s == k) {
                    candidates[k + 2] = candidates[k + 1];
                    k++;
                    break;
                }
            }
            if (equivalence[p]) {
                break;
            }
            p++;
        }
        candidates[r] = c;
        return k;
    }

    /**
     * The additions and deletions between the common lines; {@code common} counts lines from one,
     * the blocks from zero.
     */
    private static List<Difference> differences(int[] common, int n, int m, Poll poll) {
        List<Difference> differences = new ArrayList<>();
        int start1 = 1;
        int start2 = 1;
        while (start1 <= n) {
            poll.step();
            while (start1 <= n && common[start1] == start2) {
                start1++;
                start2++;
            }
            if (start1 > n) {
                break;
            }
            if (common[start1] < start2) {
                // Lines the second text does not have.
                int end1 = start1 + 1;
                while (end1 <= n && common[end1] < start2) {
                    end1++;
                }
                differences.add(new Difference(Difference.Kind.DELETE, start1 - 1, end1 - start1, start2 - 1, 0));
                start1 = end1;
            } else {
                // Lines the first text does not have.
                int end2 = common[start1];
                differences.add(new Difference(Difference.Kind.ADD, start1 - 1, 0, start2 - 1, end2 - start2));
                start2 = end2;
            }
        }
        if (start2 <= m) {
            // Lines the first text does not have, at the end of the second.
            differences.add(new Difference(Difference.Kind.ADD, n, 0, start2 - 1, m - start2 + 1));
        }
        return differences;
    }

    /** Makes one change of a deletion and the addition that takes its place. */
    private static List<Difference> merged(List<Difference> differences) {
        List<Difference> merged = new ArrayList<>(differences.size());
        for (Difference difference : differences) {
            Difference last = merged.isEmpty() ? null : merged.getLast();
            if (last != null && last.kind() == Difference.Kind.DELETE
                    && difference.kind() == Difference.Kind.ADD
                    && difference.leftStart() == last.leftEnd()) {
                merged.set(merged.size() - 1, new Difference(Difference.Kind.CHANGE, last.leftStart(),
                        last.leftCount(), difference.rightStart(), difference.rightCount()));
            } else {
                merged.add(difference);
            }
        }
        return merged;
    }

    /** A line of the second text and its number, counted from one. */
    private record Line(int number, String text) {
    }

    /** A pair of common lines and the pair before it in the common subsequence found so far. */
    private record Candidate(int a, int b, Candidate previous) {
    }
}
