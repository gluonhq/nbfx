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
package org.netbeans.modules.nbfx.ide.vcs;

import java.util.List;
import org.junit.Test;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Reads {@code git status --porcelain=v2 -z} records, written here as git prints them. */
public class GitStatusParserTest {

    private static final String HASHES = "N... 100644 100644 100644 1111111 2222222 ";
    private static final String NUL = "\0";

    @Test
    public void emptyOutputIsClean() {
        assertTrue(GitStatusParser.parse("").isClean());
    }

    @Test
    public void readsTheIndexAndTheWorkingTreeApart() {
        VcsStatus status = GitStatusParser.parse(
                "1 M. " + HASHES + "staged.txt" + NUL
                + "1 .M " + HASHES + "unstaged.txt" + NUL
                + "1 MM " + HASHES + "both.txt" + NUL
                + "1 A. " + HASHES + "added.txt" + NUL
                + "1 .D " + HASHES + "deleted.txt" + NUL
                + "1 T. " + HASHES + "link" + NUL);

        assertEquals(List.of(
                new VcsFileStatus("added.txt", null, VcsChangeKind.ADDED, null, false),
                new VcsFileStatus("both.txt", null, VcsChangeKind.MODIFIED, VcsChangeKind.MODIFIED, false),
                new VcsFileStatus("deleted.txt", null, null, VcsChangeKind.DELETED, false),
                new VcsFileStatus("link", null, VcsChangeKind.TYPE_CHANGED, null, false),
                new VcsFileStatus("staged.txt", null, VcsChangeKind.MODIFIED, null, false),
                new VcsFileStatus("unstaged.txt", null, null, VcsChangeKind.MODIFIED, false)),
                status.files());
    }

    @Test
    public void readsARenameWithItsOriginalPath() {
        VcsStatus status = GitStatusParser.parse(
                "2 R. " + HASHES + "R100 new name.txt" + NUL + "old name.txt" + NUL
                + "2 C. " + HASHES + "C75 copy.txt" + NUL + "source.txt" + NUL);

        assertEquals(List.of(
                new VcsFileStatus("copy.txt", "source.txt", VcsChangeKind.COPIED, null, false),
                new VcsFileStatus("new name.txt", "old name.txt", VcsChangeKind.RENAMED, null, false)),
                status.files());
    }

    @Test
    public void keepsSpacesAndLineBreaksInPaths() {
        VcsStatus status = GitStatusParser.parse(
                "1 .M " + HASHES + "dir with space/a b.txt" + NUL
                + "? new\nline.txt" + NUL);

        assertEquals(List.of("dir with space/a b.txt", "new\nline.txt"),
                status.files().stream().map(VcsFileStatus::path).toList());
    }

    @Test
    public void readsUntrackedAndConflictedAndSkipsIgnoredAndHeaders() {
        VcsStatus status = GitStatusParser.parse(
                "# branch.oid 1111111" + NUL
                + "# branch.head main" + NUL
                + "u UU N... 100644 100644 100644 100644 1111111 2222222 3333333 conflict.txt" + NUL
                + "? untracked.txt" + NUL
                + "! build/out.class" + NUL);

        assertEquals(List.of(
                new VcsFileStatus("conflict.txt", null, null, null, true),
                new VcsFileStatus("untracked.txt", null, null, VcsChangeKind.UNTRACKED, false)),
                status.files());
    }

    @Test
    public void joinsADeletionFromTheIndexWithTheUntrackedFileInItsPlace() {
        VcsStatus status = GitStatusParser.parse(
                "1 D. " + HASHES + "kept.txt" + NUL
                + "? kept.txt" + NUL);

        assertEquals(List.of(
                new VcsFileStatus("kept.txt", null, VcsChangeKind.DELETED, VcsChangeKind.UNTRACKED, false)),
                status.files());
    }

    @Test
    public void rejectsWhatIsNotAStatusRecord() {
        assertThrows(IllegalArgumentException.class, () -> GitStatusParser.parse("fatal: not a git repository"));
        assertThrows(IllegalArgumentException.class, () -> GitStatusParser.parse("1 M. short" + NUL));
        assertThrows(IllegalArgumentException.class,
                () -> GitStatusParser.parse("1 X. " + HASHES + "a.txt" + NUL));
        assertThrows(IllegalArgumentException.class,
                () -> GitStatusParser.parse("2 R. " + HASHES + "R100 new.txt" + NUL));
    }
}
