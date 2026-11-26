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
 * Compares two texts line by line. It knows nothing of files, version control or views: the lines
 * come from wherever the caller has them, and what is done with the result is the caller's.
 *
 * @since 1.0
 */
public final class Diff {

    private Diff() {
    }

    /**
     * The blocks in which {@code left} and {@code right} differ, lines being the same when equal.
     *
     * @param left the lines of the text compared from
     * @param right the lines of the text compared to
     * @return the differences, from the top down
     */
    public static DiffResult compute(List<String> left, List<String> right) {
        return compute(left, right, DiffOptions.EXACT);
    }

    /**
     * The blocks in which {@code left} and {@code right} differ, with {@code options} telling which
     * lines are the same.
     *
     * @param left the lines of the text compared from
     * @param right the lines of the text compared to
     * @param options what lines may differ in and still be the same
     * @return the differences, from the top down
     */
    public static DiffResult compute(List<String> left, List<String> right, DiffOptions options) {
        Objects.requireNonNull(options, "options");
        return new DiffResult(HuntDiff.diff(left.toArray(String[]::new), right.toArray(String[]::new), options));
    }

    /**
     * The lines of {@code text}, as an editor shows them: split at each line ending, whichever of
     * the three kinds it is, so that a text that ends with one has an empty last line and an empty
     * text has one empty line. Two texts split this way differ in the last line when only one of
     * them ends with a line ending.
     *
     * @param text the text to split
     * @return its lines, without their line endings; at least one
     */
    public static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                lines.add(text.substring(start, i));
                if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        lines.add(text.substring(start));
        return lines;
    }
}
