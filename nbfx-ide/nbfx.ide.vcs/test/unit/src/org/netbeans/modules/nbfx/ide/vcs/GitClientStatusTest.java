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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Reads the status of a real repository through the {@code git} command line, in the three compare
 * modes. Skipped where git is not installed.
 */
public class GitClientStatusTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private Path root;

    @Before
    public void createRepository() throws Exception {
        Assume.assumeTrue("git is not installed", gitAvailable());
        root = temp.newFolder("repo").toPath();
        git("init", "--quiet");
        // The test must not depend on the machine's configuration.
        git("config", "user.name", "nbfx test");
        git("config", "user.email", "test@example.invalid");
        git("config", "commit.gpgsign", "false");
        git("config", "status.renames", "true");
        git("config", "core.autocrlf", "false");
        for (String name : List.of("modified.txt", "staged.txt", "both.txt", "renamed.txt", "deleted.txt",
                "removed.txt")) {
            write(name, "first line of " + name + "\nsecond line\nthird line\n");
        }
        git("add", "-A");
        git("commit", "--quiet", "-m", "initial");
    }

    @Test
    public void contentOfTheLastCommitAndOfTheIndex() throws Exception {
        String committed = "first line of modified.txt\nsecond line\nthird line\n";
        write("modified.txt", committed + "staged\n");
        git("add", "modified.txt");
        write("modified.txt", committed + "staged\nnot staged\n");
        write("new.txt", "new\n");

        assertEquals(Optional.of(committed), GitClient.headContent(root, "modified.txt"));
        assertEquals(Optional.of(committed + "staged\n"), GitClient.indexContent(root, "modified.txt"));
        // A file git does not have yet has no content there.
        assertEquals(Optional.empty(), GitClient.headContent(root, "new.txt"));
        assertEquals(Optional.empty(), GitClient.indexContent(root, "new.txt"));
    }

    @Test
    public void freshCommitIsClean() throws IOException {
        assertTrue(GitClient.status(root).isClean());
    }

    @Test
    public void whatGitSaysBesideItsOutputIsNoPartOfWhatIsRead() throws Exception {
        write("modified.txt", "changed on disk\n");
        // Git traces to its standard error, as a hook, a helper or the configuration warn there.
        Map<String, String> traced = Map.of("GIT_TRACE", "1");

        GitClient.Result status = GitClient.result(root, traced, "status", "--porcelain=v2", "-z");
        assertEquals(status.output(), 0, status.exitCode());
        assertFalse(status.output(), status.output().contains("trace:"));
        assertEquals(Map.of("modified.txt", VcsChangeKind.MODIFIED),
                changes(GitStatusParser.parse(status.output()), VcsCompareMode.HEAD_VS_WORKING_TREE));

        // Of a command that failed, what it said is what there is to show.
        GitClient.Result failed = GitClient.result(root, traced, "rev-parse", "--verify", "--quiet", "no-such-branch");
        assertTrue(failed.exitCode() != 0);
        assertTrue(failed.output(), failed.output().contains("trace:"));
    }

    @Test
    public void tellsTheThreeCompareModesApart() throws Exception {
        write("modified.txt", "changed on disk\n");
        write("staged.txt", "changed and staged\n");
        git("add", "staged.txt");
        write("both.txt", "staged content\n");
        git("add", "both.txt");
        write("both.txt", "staged content\nand more on disk\n");
        git("mv", "renamed.txt", "moved.txt");
        Files.delete(root.resolve("deleted.txt"));
        git("rm", "--quiet", "removed.txt");
        write("new folder/untracked file.txt", "new\n");
        write("added.txt", "new and staged\n");
        git("add", "added.txt");

        VcsStatus status = GitClient.status(root);

        assertEquals(Map.of(
                "added.txt", VcsChangeKind.ADDED,
                "both.txt", VcsChangeKind.MODIFIED,
                "deleted.txt", VcsChangeKind.DELETED,
                "modified.txt", VcsChangeKind.MODIFIED,
                "moved.txt", VcsChangeKind.RENAMED,
                "new folder/untracked file.txt", VcsChangeKind.UNTRACKED,
                "removed.txt", VcsChangeKind.DELETED,
                "staged.txt", VcsChangeKind.MODIFIED),
                changes(status, VcsCompareMode.HEAD_VS_WORKING_TREE));
        assertEquals(Map.of(
                "added.txt", VcsChangeKind.ADDED,
                "both.txt", VcsChangeKind.MODIFIED,
                "moved.txt", VcsChangeKind.RENAMED,
                "removed.txt", VcsChangeKind.DELETED,
                "staged.txt", VcsChangeKind.MODIFIED),
                changes(status, VcsCompareMode.HEAD_VS_INDEX));
        assertEquals(Map.of(
                "both.txt", VcsChangeKind.MODIFIED,
                "deleted.txt", VcsChangeKind.DELETED,
                "modified.txt", VcsChangeKind.MODIFIED,
                "new folder/untracked file.txt", VcsChangeKind.UNTRACKED),
                changes(status, VcsCompareMode.INDEX_VS_WORKING_TREE));

        VcsFileStatus moved = status.files().stream()
                .filter(file -> file.path().equals("moved.txt")).findFirst().orElseThrow();
        assertEquals("renamed.txt", moved.originalPath());
    }

    @Test
    public void commitOfEveryChangeLeavesTheRepositoryClean() throws Exception {
        write("modified.txt", "changed on disk\n");
        write("untracked.txt", "new\n");

        assertEquals(0, GitClient.addAll(root).exitCode());
        assertEquals(Map.of("modified.txt", VcsChangeKind.MODIFIED, "untracked.txt", VcsChangeKind.ADDED),
                changes(GitClient.status(root), VcsCompareMode.HEAD_VS_INDEX));
        assertEquals(0, GitClient.commit(root, "second").exitCode());
        assertTrue(GitClient.status(root).isClean());
    }

    @Test
    public void statusAndCommitOfAFolderLeaveTheRestOfTheRepositoryAlone() throws Exception {
        write("project/src/App.java", "class App {}\n");
        write("other/Readme.txt", "first\n");
        git("add", "-A");
        git("commit", "--quiet", "-m", "two projects");
        write("project/src/App.java", "class App { int changed; }\n");
        write("project/new.txt", "new\n");
        write("other/Readme.txt", "changed\n");
        Path project = root.resolve("project");

        // The folder's changes only, with paths from the repository root.
        assertEquals(Map.of("project/new.txt", VcsChangeKind.UNTRACKED, "project/src/App.java", VcsChangeKind.MODIFIED),
                changes(GitClient.status(project), VcsCompareMode.HEAD_VS_WORKING_TREE));

        assertEquals(0, GitClient.addAll(project).exitCode());
        // Staged by hand in the other project: a commit of this one must not take it.
        git("add", "other/Readme.txt");
        GitClient.Result commit = GitClient.commit(project, "project only");
        assertEquals(commit.output(), 0, commit.exitCode());

        assertTrue(GitClient.status(project).isClean());
        assertEquals(Map.of("other/Readme.txt", VcsChangeKind.MODIFIED),
                changes(GitClient.status(root), VcsCompareMode.HEAD_VS_INDEX));
    }

    @Test
    public void aMergeIsConcludedFromAFolderOfTheRepository() throws Exception {
        write("project/App.java", "class App {}\n");
        write("other/Readme.txt", "first\n");
        git("add", "-A");
        git("commit", "--quiet", "-m", "two projects");
        git("checkout", "--quiet", "-b", "feature");
        write("project/App.java", "class App { int feature; }\n");
        write("other/Readme.txt", "feature\n");
        git("commit", "--quiet", "-am", "feature");
        git("checkout", "--quiet", "-");
        git("merge", "--quiet", "--no-ff", "--no-commit", "feature");
        Path project = root.resolve("project");
        assertTrue(GitClient.isMerging(project));

        assertEquals(0, GitClient.addAll(project).exitCode());
        // Git commits no part of a merge: the commit is of all of it, the other project as well.
        GitClient.Result commit = GitClient.commit(project, "merged");
        assertEquals(commit.output(), 0, commit.exitCode());

        assertFalse(GitClient.isMerging(project));
        assertTrue(GitClient.status(root).isClean());
    }

    @Test
    public void aFileRenamedGoneAndWrittenAgainWhereItWasIsOneChangedFile() throws Exception {
        git("mv", "renamed.txt", "moved.txt");
        Files.delete(root.resolve("moved.txt"));
        write("renamed.txt", "written again\n");

        VcsStatus status = GitClient.status(root);

        assertEquals(Map.of("renamed.txt", VcsChangeKind.MODIFIED),
                changes(status, VcsCompareMode.HEAD_VS_WORKING_TREE));
        assertEquals(1, status.files(VcsCompareMode.HEAD_VS_WORKING_TREE).size());
    }

    @Test
    public void nothingIsStagedOrCommittedWhileAFileIsConflicted() throws Exception {
        git("checkout", "--quiet", "-b", "feature");
        write("both.txt", "theirs\n");
        git("commit", "--quiet", "-am", "theirs");
        git("checkout", "--quiet", "-");
        write("both.txt", "ours\n");
        git("commit", "--quiet", "-am", "ours");
        // The merge stops at the conflict, with its markers in the file.
        assertTrue(gitFails("merge", "--quiet", "feature"));
        write("modified.txt", "another change\n");
        VcsStatus before = GitClient.status(root);
        assertTrue(before.hasConflicts());
        String head = head();

        GitClient.Result commit = GitClient.commitAll(root, "would call the conflict resolved");

        assertTrue(commit.exitCode() != 0);
        assertEquals("Resolve the conflicted files before committing", commit.output());
        assertEquals("no commit was made", head, head());
        // The file is still unmerged in the index, and nothing else was staged.
        assertEquals(before, GitClient.status(root));
        assertTrue(Files.readString(root.resolve("both.txt")).contains("<<<<<<<"));

        // Resolved by hand and told so to git, the merge is committed with every change.
        write("both.txt", "ours and theirs\n");
        git("add", "both.txt");
        commit = GitClient.commitAll(root, "merged");
        assertEquals(commit.output(), 0, commit.exitCode());
        assertTrue(GitClient.status(root).isClean());
        assertFalse(GitClient.isMerging(root));
    }

    @Test
    public void whatIsCommittedIsTheWorkingTreeWhateverWasStaged() throws Exception {
        // Staged and then undone on disk: no change. Deleted in the index: deleted.
        write("staged.txt", "changed and staged\n");
        git("add", "staged.txt");
        write("staged.txt", "first line of staged.txt\nsecond line\nthird line\n");
        git("rm", "--quiet", "removed.txt");
        // Deleted in the index and written again: the file stays, with what it holds now.
        git("rm", "--quiet", "deleted.txt");
        write("deleted.txt", "written again\n");
        write("untracked.txt", "new\n");

        GitClient.Result commit = GitClient.commitAll(root, "the working tree");

        assertEquals(commit.output(), 0, commit.exitCode());
        assertTrue(GitClient.status(root).isClean());
        assertEquals(List.of("deleted.txt", "removed.txt", "untracked.txt"), committedPaths());
        assertEquals("written again\n", Files.readString(root.resolve("deleted.txt")));
    }

    @Test
    public void aChangeStagedAndUndoneAloneLeavesNothingToCommit() throws Exception {
        write("staged.txt", "changed and staged\n");
        git("add", "staged.txt");
        write("staged.txt", "first line of staged.txt\nsecond line\nthird line\n");
        assertFalse(GitClient.status(root).isClean());
        String head = head();

        GitClient.Result commit = GitClient.commitAll(root, "nothing");

        // No commit and no failure: the index is back at the last commit, as the disk is.
        assertEquals(commit.output(), 0, commit.exitCode());
        assertEquals(head, head());
        assertTrue(GitClient.status(root).isClean());
    }

    /** The paths the last commit changed, in order. */
    private List<String> committedPaths() {
        GitClient.Result names = GitClient.result(root, Map.of(), "diff", "--name-only", "HEAD~1", "HEAD");
        assertEquals(names.output(), 0, names.exitCode());
        return names.output().lines().sorted().toList();
    }

    private String head() {
        GitClient.Result head = GitClient.result(root, Map.of(), "rev-parse", "HEAD");
        assertEquals(head.output(), 0, head.exitCode());
        return head.output().trim();
    }

    private boolean gitFails(String... args) {
        return GitClient.result(root, Map.of(), args).exitCode() != 0;
    }

    private static Map<String, VcsChangeKind> changes(VcsStatus status, VcsCompareMode mode) {
        Map<String, VcsChangeKind> changes = new TreeMap<>();
        for (VcsFileStatus file : status.files(mode)) {
            changes.put(file.path(mode), file.change(mode).orElseThrow());
        }
        return changes;
    }

    private void write(String name, String content) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void git(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(String.join(" ", command) + "\n" + output, 0, process.waitFor());
    }

    private static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
