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
import static org.junit.Assert.assertTrue;
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

    @Test
    public void theModeOfTheNextDiffIsTheOneTheUserChoseNotTheOneADiffWasOpenedIn() throws Exception {
        java.util.prefs.Preferences preferences = org.openide.util.NbPreferences.forModule(GitDiff.class);
        String before = preferences.get("diff.mode", null);
        try {
            preferences.remove("diff.mode");
            assertEquals(VcsCompareMode.HEAD_VS_WORKING_TREE, GitDiff.lastMode());
            java.nio.file.Path folder = java.nio.file.Files.createTempDirectory("nbfx-diff-mode");
            try {
                GitDiff.Provider provider = new GitDiff.Provider(folder, "a.txt", true);
                // The three ways to compare, in their order, each told by what it compares.
                assertEquals(java.util.List.of("HEAD_VS_WORKING_TREE", "HEAD_VS_INDEX", "INDEX_VS_WORKING_TREE"),
                        provider.modes().stream().map(org.netbeans.modules.nbfx.ide.diff.DiffMode::id).toList());
                assertEquals("HEAD_VS_INDEX", provider.mode(VcsCompareMode.HEAD_VS_INDEX).id());

                // Listing in a mode - as a diff opened in it, compared again or restored does -
                // leaves the mode of the next diff alone, whether it can be listed or not.
                try {
                    provider.entries(provider.mode(VcsCompareMode.INDEX_VS_WORKING_TREE), () -> false);
                } catch (java.io.IOException noRepository) {
                    // Not what is tested here.
                }
                assertEquals(VcsCompareMode.HEAD_VS_WORKING_TREE, GitDiff.lastMode());

                // The user's choice of one is kept.
                provider.modeChosen(provider.mode(VcsCompareMode.HEAD_VS_INDEX));
                assertEquals(VcsCompareMode.HEAD_VS_INDEX, GitDiff.lastMode());
            } finally {
                java.nio.file.Files.delete(folder);
            }
        } finally {
            if (before == null) {
                preferences.remove("diff.mode");
            } else {
                preferences.put("diff.mode", before);
            }
        }
    }

    @Test
    public void aFolderListsWhatTheModeListsUnderThePathsItListsThem() {
        VcsFileStatus moved = new VcsFileStatus("b/Moved.java", "a/Moved.java", VcsChangeKind.RENAMED, null, false);
        VcsFileStatus undone = new VcsFileStatus("b/Gone.java", "a/Gone.java", VcsChangeKind.RENAMED,
                VcsChangeKind.DELETED, false);
        VcsFileStatus copy = new VcsFileStatus("b/Copy.java", "a/Kept.java", VcsChangeKind.COPIED, null, false);
        VcsFileStatus replaced = new VcsFileStatus("a/Back.java", null, VcsChangeKind.DELETED, null, false);
        VcsFileStatus untracked = new VcsFileStatus("a/Back.java", null, null, VcsChangeKind.UNTRACKED, false);
        VcsStatus status = new VcsStatus(List.of(moved, undone, copy, replaced, untracked));

        for (VcsCompareMode mode : VcsCompareMode.values()) {
            // Whatever the folder, never a file the mode does not list, and each under its path there.
            List<String> all = GitDiff.changedWithin(status, "", mode).stream().map(file -> file.path(mode)).toList();
            assertEquals(mode.name(), status.files(mode).stream().map(file -> file.path(mode)).sorted().toList(), all);
        }
        // Renamed in the index and deleted from disk: what is gone is the file the last commit has.
        assertEquals(List.of("a/Back.java", "a/Gone.java", "b/Copy.java", "b/Moved.java"),
                within(status, "", VcsCompareMode.HEAD_VS_WORKING_TREE));
        // Out of the index with a file in its place: one row, not two.
        assertEquals(1, GitDiff.changedWithin(status, "a", VcsCompareMode.HEAD_VS_WORKING_TREE).stream()
                .filter(file -> file.path().equals("a/Back.java")).count());

        // Moved to another folder: a change of the one it left and of the one it is in.
        assertEquals(List.of("a/Back.java", "b/Gone.java", "b/Moved.java"),
                within(status, "a", VcsCompareMode.HEAD_VS_INDEX));
        assertEquals(List.of("b/Copy.java", "b/Gone.java", "b/Moved.java"),
                within(status, "b", VcsCompareMode.HEAD_VS_INDEX));
        // What it was copied from is no part of a copy: not listed in the folder of its source.
        assertFalse(within(status, "a", VcsCompareMode.HEAD_VS_INDEX).contains("b/Copy.java"));
        // Between the index and the disk nothing was renamed: by the path in the index alone.
        assertEquals(List.of("a/Back.java"), within(status, "a", VcsCompareMode.INDEX_VS_WORKING_TREE));
        assertEquals(List.of("b/Gone.java"), within(status, "b", VcsCompareMode.INDEX_VS_WORKING_TREE));

        // In the last commit a renamed file is under its old name; a copied one is not in it at all.
        assertEquals("a/Moved.java", GitDiff.headPath(moved, VcsCompareMode.HEAD_VS_INDEX));
        assertEquals("a/Gone.java", GitDiff.headPath(undone, VcsCompareMode.HEAD_VS_WORKING_TREE));
        assertEquals("b/Copy.java", GitDiff.headPath(copy, VcsCompareMode.HEAD_VS_INDEX));
        assertEquals("a/Back.java", GitDiff.headPath(replaced, VcsCompareMode.HEAD_VS_INDEX));
    }

    private static List<String> within(VcsStatus status, String scope, VcsCompareMode mode) {
        return GitDiff.changedWithin(status, scope, mode).stream().map(file -> file.path(mode)).toList();
    }

    @Test
    public void theDiffOfOneFileHasNoTableAndTheOneOfAFolderHas() {
        assertFalse(new GitDiff.Provider(Path.of("/repo"), "src/A.java", true).listed());
        assertTrue(new GitDiff.Provider(Path.of("/repo"), "src", false).listed());
    }

    @Test
    public void aSavedDiffIsOpenedAgainWhateverItsNameAndOnlyIfItIsReadWhole() throws Exception {
        Path root = java.nio.file.Files.createTempDirectory("nbfx-diff-restore").toRealPath();
        try {
            java.nio.file.Files.createDirectory(root.resolve(".git"));
            java.nio.file.Files.createDirectory(root.resolve("src"));
            // A deleted file, and a name with a line break in it: the path is the rest of the state.
            Path gone = root.resolve("src").resolve("two\nlines.txt");
            assertTrue(GitDiff.restore(GitDiff.state(VcsCompareMode.HEAD_VS_INDEX, true, gone)));
            assertTrue(GitDiff.restore(GitDiff.state(VcsCompareMode.INDEX_VS_WORKING_TREE, false, root.resolve("src"))));

            // Saved as a file, and a folder by now: not the diff that was saved.
            assertFalse(GitDiff.restore(GitDiff.state(VcsCompareMode.HEAD_VS_INDEX, true, root.resolve("src"))));

            // Neither a file nor a folder; and a path that is not whole.
            assertFalse(GitDiff.restore("HEAD_VS_INDEX\nlink\n" + root.resolve("src")));
            assertFalse(GitDiff.restore("HEAD_VS_INDEX\nfolder\nsrc"));
            assertFalse(GitDiff.restore("HEAD_VS_INDEX\nfolder\n" + root.resolve("src") + "\u0000"));
        } finally {
            java.nio.file.Files.delete(root.resolve("src"));
            java.nio.file.Files.delete(root.resolve(".git"));
            java.nio.file.Files.delete(root);
        }
    }
}
