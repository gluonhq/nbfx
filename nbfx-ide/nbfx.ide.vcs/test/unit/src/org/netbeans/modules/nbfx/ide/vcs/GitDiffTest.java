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

import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.netbeans.modules.nbfx.ide.diff.DiffEntry;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Which files the diff of a folder lists, and how it colours them. */
public class GitDiffTest {

    private static final VcsStatus STATUS = new VcsStatus(List.of(
            new VcsFileStatus("z.txt", null, null, VcsChangeKind.MODIFIED, false),
            new VcsFileStatus("app/src/New.java", null, VcsChangeKind.ADDED, null, false),
            new VcsFileStatus("app/src/App.java", null, VcsChangeKind.MODIFIED, VcsChangeKind.MODIFIED, false),
            new VcsFileStatus("app/Notes.txt", null, null, VcsChangeKind.UNTRACKED, false),
            new VcsFileStatus("application/Other.java", null, null, VcsChangeKind.MODIFIED, false)));

    @Test
    public void theRootListsEveryFileThatDiffersInTheModeByPath() {
        assertEquals(List.of("app/Notes.txt", "app/src/App.java", "app/src/New.java", "application/Other.java", "z.txt"),
                paths("", VcsCompareMode.HEAD_VS_WORKING_TREE));
        assertEquals(List.of("app/src/App.java", "app/src/New.java"), paths("", VcsCompareMode.HEAD_VS_INDEX));
    }

    @Test
    public void aFolderListsOnlyWhatIsInItOrBelow() {
        // Not the folder whose name only starts the same.
        assertEquals(List.of("app/Notes.txt", "app/src/App.java", "app/src/New.java"),
                paths("app", VcsCompareMode.HEAD_VS_WORKING_TREE));
        assertEquals(List.of("app/Notes.txt", "app/src/App.java"), paths("app", VcsCompareMode.INDEX_VS_WORKING_TREE));
        assertEquals(List.of(), paths("docs", VcsCompareMode.HEAD_VS_WORKING_TREE));
    }

    @Test
    public void changesAreColouredAsTheStatusViewColoursThem() {
        assertEquals(DiffEntry.Kind.ADDED, GitDiff.entryKind(VcsChangeKind.UNTRACKED));
        assertEquals(DiffEntry.Kind.ADDED, GitDiff.entryKind(VcsChangeKind.ADDED));
        assertEquals(DiffEntry.Kind.MODIFIED, GitDiff.entryKind(VcsChangeKind.RENAMED));
        assertEquals(DiffEntry.Kind.DELETED, GitDiff.entryKind(VcsChangeKind.DELETED));
        assertEquals(DiffEntry.Kind.CONFLICTED, GitDiff.entryKind(VcsChangeKind.CONFLICTED));
        // A file the same on both sides of the mode.
        assertEquals(DiffEntry.Kind.OTHER, GitDiff.entryKind(null));
    }

    @Test
    public void aSavedDiffTellsItsModeKindAndPathAndOneThatCannotBeReadIsNotOpened() {
        assertEquals("HEAD_VS_INDEX\nfile\n" + Path.of("/repo/src/App.java"),
                GitDiff.state(VcsCompareMode.HEAD_VS_INDEX, true, Path.of("/repo/src/App.java")));
        assertEquals("INDEX_VS_WORKING_TREE\nfolder\n" + Path.of("/repo"),
                GitDiff.state(VcsCompareMode.INDEX_VS_WORKING_TREE, false, Path.of("/repo")));
        assertEquals("git-diff", new GitDiff.Restorer().id());

        assertFalse(GitDiff.restore(""));
        assertFalse(GitDiff.restore("SIDEWAYS\nfile\n/repo/src/App.java"));
        // No such folder, so no working tree.
        assertFalse(GitDiff.restore("HEAD_VS_INDEX\nfile\n/no/such/folder/of/a/diff/App.java"));
    }

    private static List<String> paths(String scope, VcsCompareMode mode) {
        return GitDiff.changedWithin(STATUS, scope, mode).stream().map(VcsFileStatus::path).toList();
    }
}
