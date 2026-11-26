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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The line comparison of {@link Diff}: the Hunt-McIlroy longest common subsequence, ported from the
 * {@code HuntDiff} of NetBeans' diff module (by Maros Sandor and Martin Entlicher), which is bound
 * to Swing through its module but not in this algorithm. The search for the common lines is theirs;
 * the blocks it is turned into count lines from zero, as {@link Difference} does.
 */
final class HuntDiff {

    private static final Pattern SPACES = Pattern.compile("(\\s+)");

    private HuntDiff() {
    }

    static List<Difference> diff(String[] left, String[] right, DiffOptions options) {
        int[] common = commonLines(left, right, options);
        return merged(differences(common, left.length, right.length));
    }

    /**
     * For each line of {@code lines1}, counted from one, the line of {@code lines2} it is the same
     * as in the longest common subsequence, or zero.
     */
    private static int[] commonLines(String[] lines1, String[] lines2, DiffOptions options) {
        int m = lines1.length;
        int n = lines2.length;
        if (options.ignoreCase() || options.ignoreInnerWhitespace()
                || options.ignoreLeadingAndTrailingWhitespace()) {
            lines1 = normalized(lines1, options);
            lines2 = normalized(lines2, options);
        }

        // The lines of the second text <1, n>, sorted, so that equal lines are neighbours.
        Line[] sorted = new Line[n + 1];
        for (int i = 1; i <= n; i++) {
            sorted[i] = new Line(i, lines2[i - 1]);
        }
        Arrays.sort(sorted, 1, n + 1, Comparator.comparing(Line::text));

        int[] equivalenceLines = new int[n + 1];
        boolean[] equivalence = new boolean[n + 1];
        for (int i = 1; i <= n; i++) {
            Line line = sorted[i];
            equivalenceLines[i] = line.number();
            equivalence[i] = i == n || !line.text().equals(sorted[i + 1].text());
        }
        equivalenceLines[0] = 0;
        equivalence[0] = true;
        int[] equivalenceAssoc = new int[m + 1];
        for (int i = 1; i <= m; i++) {
            equivalenceAssoc[i] = findAssoc(lines1[i - 1], sorted, equivalence);
        }

        Candidate[] candidates = new Candidate[Math.min(m, n) + 2];
        candidates[0] = new Candidate(0, 0, null);
        candidates[1] = new Candidate(m + 1, n + 1, null);
        int k = 0;
        for (int i = 1; i <= m; i++) {
            if (equivalenceAssoc[i] != 0) {
                k = merge(candidates, k, i, equivalenceLines, equivalence, equivalenceAssoc[i]);
            }
        }
        int[] common = new int[m + 2];
        for (Candidate c = candidates[k]; c != null; c = c.previous()) {
            common[c.a()] = c.b();
        }
        return common;
    }

    private static String[] normalized(String[] lines, DiffOptions options) {
        String[] normalized = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (options.ignoreLeadingAndTrailingWhitespace() && options.ignoreInnerWhitespace()) {
                line = SPACES.matcher(line).replaceAll("");
            } else if (options.ignoreLeadingAndTrailingWhitespace()) {
                line = line.strip();
            } else if (options.ignoreInnerWhitespace()) {
                line = withoutInnerSpaces(line);
            }
            if (options.ignoreCase()) {
                line = line.toUpperCase(Locale.ROOT);
            }
            normalized[i] = line;
        }
        return normalized;
    }

    private static String withoutInnerSpaces(String line) {
        Matcher matcher = SPACES.matcher(line);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            if (matcher.start() == 0 || matcher.end() == line.length()) {
                matcher.appendReplacement(result, "$1");
            } else {
                matcher.appendReplacement(result, "");
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static int findAssoc(String line1, Line[] sorted, boolean[] equivalence) {
        int index = binarySearch(sorted, line1, 1, sorted.length - 1);
        if (index < 1) {
            return 0;
        }
        int lastGood = 0;
        for (; index >= 1 && sorted[index].text().equals(line1); index--) {
            if (equivalence[index - 1]) {
                lastGood = index;
            }
        }
        return lastGood;
    }

    private static int binarySearch(Line[] lines, String key, int low, int high) {
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int comparison = lines[mid].text().compareTo(key);
            if (comparison < 0) {
                low = mid + 1;
            } else if (comparison > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
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
            boolean[] equivalence, int p) {
        int r = 0;
        Candidate c = candidates[0];
        while (true) {
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
    private static List<Difference> differences(int[] common, int n, int m) {
        List<Difference> differences = new ArrayList<>();
        int start1 = 1;
        int start2 = 1;
        while (start1 <= n) {
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
