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
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Compares two texts line by line. It knows nothing of files, version control or views: the lines
 * come from wherever the caller has them, and what is done with the result is the caller's.
 * <p>
 * Under development until its first consumer, the diff view, has landed: it may change
 * incompatibly until then.
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
     * @throws NullPointerException when an argument, or a line, is {@code null}
     */
    public static DiffResult compute(List<String> left, List<String> right, DiffOptions options) {
        return compute(left, right, options, () -> false);
    }

    /**
     * As {@link #compute(List, List, DiffOptions)}, given up when {@code cancelled} says so. The
     * time a comparison takes grows with the number of lines of both texts and with how often their
     * lines repeat, and nothing here limits either: a caller that compares texts of any size does
     * it off the thread it draws on, decides how large a text it compares at all, and cancels the
     * comparison nobody waits for any more. {@code cancelled} is asked before the lines are looked
     * at and then every few hundred steps of every part of the comparison - telling the lines that
     * are the same apart, ordering them, the search, and the making of the blocks - on the calling
     * thread, and must answer at once.
     *
     * @param left the lines of the text compared from
     * @param right the lines of the text compared to
     * @param options what lines may differ in and still be the same
     * @param cancelled whether the result is no longer wanted
     * @return the differences, from the top down
     * @throws CancellationException when {@code cancelled} answered {@code true}
     * @throws NullPointerException when an argument, or a line, is {@code null}
     */
    public static DiffResult compute(List<String> left, List<String> right, DiffOptions options,
            BooleanSupplier cancelled) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancelled, "cancelled");
        // Before the lines are looked at: copying and checking them takes time too.
        if (cancelled.getAsBoolean()) {
            throw new CancellationException();
        }
        return new DiffResult(HuntDiff.diff(lines(left, "left"), lines(right, "right"), options, cancelled));
    }

    private static String[] lines(List<String> lines, String side) {
        String[] array = lines.toArray(String[]::new);
        for (int i = 0; i < array.length; i++) {
            if (array[i] == null) {
                throw new NullPointerException(side + " line " + i);
            }
        }
        return array;
    }

    /**
     * The lines of {@code text}, as an editor shows them: split at each line ending, whichever of
     * the three kinds it is, so that a text that ends with one has an empty last line and an empty
     * text has one empty line. Two texts split this way differ in the last line when only one of
     * them ends with a line ending.
     *
     * <p>
     * The line endings themselves are not kept: two texts that differ only in them - one with
     * {@code \n}, the other with {@code \r\n} - have the same lines, and a caller that shows such a
     * change, or a missing last line ending as git words it, tells it from the texts.
     *
     * @param text the text to split
     * @return its lines, without their line endings; at least one
     * @throws NullPointerException when {@code text} is {@code null}
     */
    public static List<String> lines(String text) {
        Objects.requireNonNull(text, "text");
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
