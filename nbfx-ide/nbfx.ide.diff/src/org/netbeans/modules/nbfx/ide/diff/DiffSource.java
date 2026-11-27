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

import java.util.List;
import java.util.Objects;
import org.netbeans.api.nbfx.diff.Diff;

/**
 * One of the two sides a diff view compares, as it was when the diff was asked for: a snapshot.
 * The view shows this text and compares this text - not a file that may be edited, saved or read
 * in another encoding meanwhile - so that the blocks it marks are those of the text beside them.
 * <p>
 * A side is one of three things, and says which:
 * <ul>
 * <li>a <em>text</em>, already read and decoded by whoever made the source, who knows where it
 * comes from and in what charset; an empty text is a file with nothing in it, of one empty
 * line;</li>
 * <li><em>missing</em>: there is no file on this side - the file was added since, or deleted -
 * which is compared as no lines at all;</li>
 * <li><em>unreadable</em>: there is something on this side that is no text to compare - a binary
 * file, one too large, one that could not be read - with what to tell the user of it. Nothing is
 * compared then.</li>
 * </ul>
 * Two sources are equal when they would show the same: a view asked again for a diff of equal
 * sources keeps what it shows.
 *
 * @param title the heading of this side: "HEAD", "Working Tree"
 * @param fileName the name of the file, by which an editor tells how to colour the text
 * @param text the text; {@code null} for a side that is missing or unreadable
 * @param problem why there is no text to compare, for the user to read; {@code null} for a side
 *        that has a text or is missing
 */
public record DiffSource(String title, String fileName, String text, String problem) {

    /** Checks that the title and the name are there, and that the side is one of the three. */
    public DiffSource {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(fileName, "fileName");
        if (text != null && problem != null) {
            throw new IllegalArgumentException("a side with a text has no problem to tell");
        }
    }

    /** A side with {@code text}; an empty text is an empty file. */
    public static DiffSource of(String title, String fileName, String text) {
        return new DiffSource(title, fileName, Objects.requireNonNull(text, "text"), null);
    }

    /** A side with no file on it. */
    public static DiffSource missing(String title, String fileName) {
        return new DiffSource(title, fileName, null, null);
    }

    /** A side with no text to compare, for the reason {@code problem} tells the user. */
    public static DiffSource unreadable(String title, String fileName, String problem) {
        return new DiffSource(title, fileName, null, Objects.requireNonNull(problem, "problem"));
    }

    /** Whether there is no file on this side. */
    public boolean isMissing() {
        return text == null && problem == null;
    }

    /** Whether this side can be compared: it has a text, or is missing. */
    public boolean isComparable() {
        return problem == null;
    }

    /**
     * The lines compared: those of the text, as an editor shows them - one empty line for an
     * empty text - and none for a side that is missing.
     */
    public List<String> lines() {
        return text == null ? List.of() : Diff.lines(text);
    }
}
