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
import org.openide.filesystems.FileObject;

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
 * A side with a text may also be <em>edited</em>: it is of a {@link #file() file} on disk, and
 * the view shows that file in an editor of it - the one text all its editors share - in place of
 * a copy of the snapshot. What is compared is then what that editor holds, as it is edited; the
 * snapshot is the text the file had when the diff was asked for, which tells how its lines end.
 * Only the side compared to, on the right, is edited.
 * <p>
 * Two sources are equal when they would show the same: a view asked again for a diff of equal
 * sources keeps what it shows.
 *
 * @param title the heading of this side: "HEAD", "Working Tree"
 * @param fileName the name of the file, by which an editor tells how to colour the text
 * @param text the text; {@code null} for a side that is missing or unreadable
 * @param problem why there is no text to compare, for the user to read; {@code null} for a side
 *        that has a text or is missing
 * @param path where the file is in what is compared, with {@code /} between its folders - in a
 *        repository, from its root - for the unified diff to name it by, as {@code a/path} and
 *        {@code b/path}; {@code null} to have it named by the title
 * @param file the file this side is edited in; {@code null} for a side that is only shown
 */
public record DiffSource(String title, String fileName, String text, String problem, String path, FileObject file) {

    /** Checks that the title and the name are there, and that the side is one of the three. */
    public DiffSource {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(fileName, "fileName");
        if (text != null && problem != null) {
            throw new IllegalArgumentException("a side with a text has no problem to tell");
        }
        if (file != null && text == null) {
            throw new IllegalArgumentException("a side that is edited has a text");
        }
    }

    /** A side with {@code text}; an empty text is an empty file. */
    public static DiffSource of(String title, String fileName, String text) {
        return new DiffSource(title, fileName, Objects.requireNonNull(text, "text"), null, null, null);
    }

    /** A side with no file on it. */
    public static DiffSource missing(String title, String fileName) {
        return new DiffSource(title, fileName, null, null, null, null);
    }

    /** A side with no text to compare, for the reason {@code problem} tells the user. */
    public static DiffSource unreadable(String title, String fileName, String problem) {
        return new DiffSource(title, fileName, null, Objects.requireNonNull(problem, "problem"), null, null);
    }

    /** This side, at {@code path} in what is compared: what the unified diff names it by. */
    public DiffSource withPath(String path) {
        return new DiffSource(title, fileName, text, problem, path, file);
    }

    /**
     * A side that is edited in the diff: {@code file}, a file on disk, whose text was
     * {@code text} when the diff was asked for. It is named as the file is.
     */
    public static DiffSource editable(String title, FileObject file, String text) {
        Objects.requireNonNull(file, "file");
        return new DiffSource(title, file.getNameExt(), Objects.requireNonNull(text, "text"), null, null, file);
    }

    /** Whether this side is edited in the diff, not only shown. */
    public boolean isEditable() {
        return file != null;
    }

    /** This side, holding {@code text}: what its editor holds by now. */
    DiffSource withText(String text) {
        return new DiffSource(title, fileName, Objects.requireNonNull(text, "text"), null, path, file);
    }

    /**
     * What the unified diff calls this side: {@code /dev/null} when there is no file on it, as
     * {@code git diff} does; else its path after {@code prefix}, or its title when it has none.
     */
    String unifiedName(String prefix) {
        return isMissing() ? "/dev/null" : path == null ? title : prefix + path;
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
