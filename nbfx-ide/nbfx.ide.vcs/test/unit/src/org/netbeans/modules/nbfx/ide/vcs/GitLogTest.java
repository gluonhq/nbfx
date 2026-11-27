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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Change;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Commit;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Criteria;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Reads the history of a real repository through the {@code git} command line: what a commit is
 * told with, and which commits each limit of a search leaves. Skipped where git is not installed.
 * <p>
 * The repository has five commits on its branch, a day apart, the newest first: {@code merge}
 * (of the branch {@code side}, which added {@code docs/side.txt}), {@code Add the guide} by Grace,
 * {@code side}, {@code Move and change}, and {@code first}.
 */
public class GitLogTest {

    private static final long DAY = 24 * 60 * 60;
    /** 2026-03-01 12:00 UTC: the day of the first commit. */
    private static final long START = Instant.parse("2026-03-01T12:00:00Z").getEpochSecond();

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private Path root;

    @Before
    public void createRepository() throws Exception {
        Assume.assumeTrue("git is not installed", gitAvailable());
        root = temp.newFolder("repo").toPath();
        git(0, "init", "--quiet", "--initial-branch=main");
        // The test must not depend on the machine's configuration.
        git(0, "config", "user.name", "Ada Lovelace");
        git(0, "config", "user.email", "ada@example.invalid");
        git(0, "config", "commit.gpgsign", "false");
        git(0, "config", "core.autocrlf", "false");
        write("src/a.txt", "one\ntwo\nthree\nfour\nfive\n");
        write("src/b.txt", "b\n");
        git(0, "add", "-A");
        git(0, "commit", "--quiet", "-m", "first\n\nThe body of the first commit,\nin two lines.");
        git(1, "mv", "src/a.txt", "src/moved.txt");
        write("src/b.txt", "b\nchanged\n");
        git(1, "add", "-A");
        git(1, "commit", "--quiet", "-m", "Move and change");
        git(2, "checkout", "--quiet", "-b", "side");
        write("docs/side.txt", "side\n");
        git(2, "add", "-A");
        git(2, "commit", "--quiet", "-m", "side");
        git(3, "checkout", "--quiet", "main");
        write("docs/guide.txt", "guide\n");
        git(3, "add", "-A");
        git(3, "commit", "--quiet", "--author=Grace Hopper <grace@example.invalid>", "-m", "Add the guide");
        git(4, "merge", "--quiet", "--no-ff", "side", "-m", "merge");
        git(4, "tag", "v1", "HEAD~1");
    }

    @Test
    public void aCommitIsToldWithItsAuthorDateMessageAndFiles() throws Exception {
        GitHistory history = GitLog.read(root, all());

        assertFalse(history.more());
        assertEquals(List.of("merge", "Add the guide", "side", "Move and change", "first"), summaries(history));
        Commit first = history.commits().get(4);
        assertEquals(40, first.id().length());
        assertEquals(first.id().substring(0, 7), first.shortId());
        assertEquals(List.of(), first.parents());
        assertEquals("Ada Lovelace", first.author());
        assertEquals("ada@example.invalid", first.email());
        assertEquals(Instant.ofEpochSecond(START), first.date());
        assertEquals("first\n\nThe body of the first commit,\nin two lines.", first.message());
        assertEquals("first", first.summary());
        assertEquals(List.of(new Change(VcsChangeKind.ADDED, "src/a.txt", null),
                new Change(VcsChangeKind.ADDED, "src/b.txt", null)), first.changes());

        Commit moved = history.commits().get(3);
        assertEquals(List.of(first.id()), moved.parents());
        assertFalse(moved.isMerge());
        assertEquals(List.of(new Change(VcsChangeKind.MODIFIED, "src/b.txt", null),
                new Change(VcsChangeKind.RENAMED, "src/moved.txt", "src/a.txt")), moved.changes());

        Commit byGrace = history.commits().get(1);
        assertEquals("Grace Hopper", byGrace.author());
        assertEquals("grace@example.invalid", byGrace.email());
    }

    @Test
    public void aMergeListsWhatItBroughtToItsBranch() throws Exception {
        Commit merge = GitLog.read(root, all()).commits().get(0);

        assertTrue(merge.isMerge());
        assertEquals(2, merge.parents().size());
        assertEquals(List.of(new Change(VcsChangeKind.ADDED, "docs/side.txt", null)), merge.changes());
        assertEquals(merge.changes(), GitLog.changes(root, merge.id()));

        Criteria noMerges = new Criteria(null, null, null, null, null, 50, false, List.of());
        assertEquals(List.of("Add the guide", "side", "Move and change", "first"),
                summaries(GitLog.read(root, noMerges)));
    }

    @Test
    public void aSearchForPathsFindsTheCommitsThatChangedThemAndListsOnlyThose() throws Exception {
        GitHistory ofFolder = GitLog.read(root, Criteria.of(List.of("src")));

        assertEquals(List.of("Move and change", "first"), summaries(ofFolder));

        GitHistory ofFile = GitLog.read(root, Criteria.of(List.of("src/b.txt")));
        Commit moved = ofFile.commits().get(0);
        assertEquals(List.of(new Change(VcsChangeKind.MODIFIED, "src/b.txt", null)), moved.changes());
        // All its files are read apart, for a search that shows them.
        assertEquals(List.of(new Change(VcsChangeKind.MODIFIED, "src/b.txt", null),
                new Change(VcsChangeKind.RENAMED, "src/moved.txt", "src/a.txt")), GitLog.changes(root, moved.id()));
        // The first commit of a repository has all it added.
        assertEquals(2, GitLog.changes(root, ofFile.commits().get(1).id()).size());
    }

    @Test
    public void messageAndAuthorAreSearchedAsTypedWhateverTheCase() throws Exception {
        assertEquals(List.of("Add the guide"), summaries(GitLog.read(root, search("GUIDE", null))));
        assertEquals(List.of("first"), summaries(GitLog.read(root, search("body of", null))));
        // Not a pattern: a dot is a dot.
        assertEquals(List.of(), summaries(GitLog.read(root, search("f.rst", null))));
        assertEquals(List.of("Add the guide"), summaries(GitLog.read(root, search(null, "grace"))));
        assertEquals(List.of("Add the guide"), summaries(GitLog.read(root, search(" ", "GRACE@example"))));
        assertEquals(List.of(), summaries(GitLog.read(root, search("first", "grace"))));
    }

    @Test
    public void theLimitTellsWhetherThereIsMoreAndTheRestIsReadPastIt() throws Exception {
        Criteria two = new Criteria(null, null, null, null, null, 2, true, List.of());

        GitHistory newest = GitLog.read(root, two);
        assertEquals(List.of("merge", "Add the guide"), summaries(newest));
        assertTrue(newest.more());

        GitHistory next = GitLog.read(root, two, 2);
        assertEquals(List.of("side", "Move and change"), summaries(next));
        assertTrue(next.more());

        GitHistory last = GitLog.read(root, two, 4);
        assertEquals(List.of("first"), summaries(last));
        assertFalse(last.more());

        Criteria none = new Criteria(null, null, null, null, null, 0, true, List.of());
        assertEquals(new GitHistory(List.of(), false), GitLog.read(root, none));
    }

    @Test
    public void fromAndToAreRevisionsOrDaysBothIncluded() throws Exception {
        // By day: the second to the fourth of March.
        assertEquals(List.of("Add the guide", "side", "Move and change"),
                summaries(GitLog.read(root, between("2026-03-02", "2026-03-04"))));
        assertEquals(List.of("Move and change", "first"), summaries(GitLog.read(root, between(null, "2026-03-02"))));
        assertEquals(List.of("merge", "Add the guide"), summaries(GitLog.read(root, between("2026-03-04", " "))));

        // By revision: the tag is the commit before the merge, on the branch.
        assertEquals(List.of("Add the guide", "Move and change", "first"),
                summaries(GitLog.read(root, between(null, "v1"))));
        assertEquals(List.of("Add the guide", "Move and change"), summaries(GitLog.read(root, between("v1~1", "v1"))));
        assertEquals(List.of("merge", "Add the guide", "side"), summaries(GitLog.read(root, between("v1", null))));
        // And one of each.
        assertEquals(List.of("Add the guide", "Move and change"),
                summaries(GitLog.read(root, between("2026-03-02", "v1"))));

        assertTrue(GitLog.isDate("2026-03-02"));
        assertFalse(GitLog.isDate("2026-13-02"));
        assertFalse(GitLog.isDate("v1"));
        assertFalse(GitLog.isDate(null));
    }

    @Test
    public void theHistoryOfABranchIsSearchedWithoutCheckingItOut() throws Exception {
        Criteria side = new Criteria("side", null, null, null, null, 50, true, List.of());

        assertEquals(List.of("side", "Move and change", "first"), summaries(GitLog.read(root, side)));
    }

    @Test
    public void whatGitCannotSearchIsSaidAndARepositoryWithoutCommitsHasNoHistory() throws Exception {
        Criteria unknown = new Criteria("no-such-branch", null, null, null, null, 50, true, List.of());
        IOException said = assertThrows(IOException.class, () -> GitLog.read(root, unknown));
        assertTrue(said.getMessage(), said.getMessage().contains("no-such-branch"));

        // What would be taken for an option is no revision.
        Criteria option = new Criteria("--all", null, null, null, null, 50, true, List.of());
        assertThrows(IOException.class, () -> GitLog.read(root, option));
        assertThrows(IOException.class, () -> GitLog.changes(root, "--stat"));

        Path fresh = temp.newFolder("fresh").toPath();
        root = fresh;
        git(0, "init", "--quiet");
        assertEquals(new GitHistory(List.of(), false), GitLog.read(fresh, all()));

        Path plain = Files.createTempDirectory("nbfx-no-repository");
        try {
            Assume.assumeFalse(GitClient.isRepository(plain));
            assertThrows(IOException.class, () -> GitLog.read(plain, all()));
        } finally {
            Files.delete(plain);
        }
    }

    @Test
    public void namesWithSpacesAndOtherAlphabetsAreReadAsTheyAre() throws Exception {
        write("docs/my notes éè.txt", "notes\n");
        git(5, "add", "-A");
        git(5, "commit", "--quiet", "-m", "Résumé – notes");

        Commit newest = GitLog.read(root, all()).commits().get(0);

        assertEquals("Résumé – notes", newest.message());
        assertEquals(List.of(new Change(VcsChangeKind.ADDED, "docs/my notes éè.txt", null)), newest.changes());
    }

    @Test
    public void whatGitWritesIsParsedAndWhatItWouldNotWriteIsRefused() throws Exception {
        String log = "\u0001abc\u0000p1 p2\u0000Ada\u0000ada@x\u000010\u0000subject\n\nbody\n\u0002\u0000\n"
                + "M\u0000a.txt\u0000R100\u0000old.txt\u0000new.txt\u0000"
                + "\u0001def\u0000\u0000Ada\u0000ada@x\u000020\u0000root\n\u0002";

        List<Commit> commits = GitLog.parse(log);

        assertEquals(2, commits.size());
        assertEquals(new Commit("abc", List.of("p1", "p2"), "Ada", "ada@x", Instant.ofEpochSecond(10),
                "subject\n\nbody", List.of(new Change(VcsChangeKind.MODIFIED, "a.txt", null),
                        new Change(VcsChangeKind.RENAMED, "new.txt", "old.txt"))), commits.get(0));
        assertEquals(new Commit("def", List.of(), "Ada", "ada@x", Instant.ofEpochSecond(20), "root", List.of()),
                commits.get(1));
        assertEquals(List.of(), GitLog.parse(""));
        assertThrows(IOException.class, () -> GitLog.parse("\u0001abc\u0000only\u0002"));
        assertThrows(IOException.class, () -> GitLog.parseChanges("X\u0000a.txt\u0000"));
        assertThrows(IOException.class, () -> GitLog.parseChanges("R100\u0000old.txt\u0000"));
    }

    private static Criteria all() {
        return Criteria.of(List.of());
    }

    private static Criteria search(String message, String author) {
        return new Criteria(null, message, author, null, null, 50, true, List.of());
    }

    private static Criteria between(String from, String to) {
        return new Criteria(null, null, null, from, to, 50, true, List.of());
    }

    private static List<String> summaries(GitHistory history) {
        return history.commits().stream().map(Commit::summary).toList();
    }

    private void write(String name, String content) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /** Runs git on the {@code day}th day of the repository: commits made then have that date. */
    private void git(int day, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        String date = (START + day * DAY) + " +0000";
        builder.environment().putAll(Map.of("GIT_AUTHOR_DATE", date, "GIT_COMMITTER_DATE", date));
        Process process = builder.start();
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
