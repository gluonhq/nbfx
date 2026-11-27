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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.api.nbfx.diff.Diff;
import org.netbeans.api.nbfx.diff.Difference;
import org.netbeans.api.nbfx.diff.Difference.Kind;
import org.netbeans.modules.nbfx.ide.diff.DiffPair;
import org.netbeans.modules.nbfx.ide.diff.DiffSource;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.openide.filesystems.LocalFileSystem;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Reads the two sides of the diff of a file in a real repository - what the last commit has of it
 * and what the working tree has - and compares them as the diff view does. Skipped where git is
 * not installed.
 */
public class GitDiffSidesTest {

    private static final String COMMITTED = "first line\nsecond line\nthird line\n";

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private Path root;

    @Before
    public void createRepository() throws Exception {
        Assume.assumeTrue("git is not installed", gitAvailable());
        root = temp.newFolder("repo").toPath();
        git("init", "--quiet", "--initial-branch=main");
        // The test must not depend on the machine's configuration.
        git("config", "user.name", "nbfx test");
        git("config", "user.email", "test@example.invalid");
        git("config", "commit.gpgsign", "false");
        git("config", "core.autocrlf", "false");
        write("src/modified.txt", COMMITTED);
        write("src/deleted.txt", "to delete\n");
        write("src/old name.txt", "renamed\nas it is\n");
        git("add", "-A");
        git("commit", "--quiet", "-m", "initial");
    }

    @Test
    public void aModifiedFileIsComparedWithWhatTheLastCommitHas() throws Exception {
        write("src/modified.txt", "first line\nsecond line, changed\nthird line\nfourth line\n");

        GitDiff.Sides sides = sides("src/modified.txt");

        assertEquals(DiffSource.of("HEAD", "modified.txt", COMMITTED), sides.head());
        assertEquals(DiffSource.of("Working Tree", "modified.txt",
                "first line\nsecond line, changed\nthird line\nfourth line\n"), sides.workingTree());
        assertEquals(List.of(new Difference(Kind.CHANGE, 1, 1, 1, 1), new Difference(Kind.ADD, 3, 0, 3, 1)),
                differences(sides));

        // Staged or not, the working tree is what is on disk.
        git("add", "-A");
        assertEquals(sides, sides("src/modified.txt"));
        // Committed, both sides are the same, and equal to what they are read as again.
        git("commit", "--quiet", "-m", "changed");
        GitDiff.Sides committed = sides("src/modified.txt");
        assertTrue(differences(committed).isEmpty());
        assertEquals(committed, sides("src/modified.txt"));
    }

    @Test
    public void aNewFileIsAddedWhetherItHasContentOrNone() throws Exception {
        write("src/new.txt", "one\ntwo\n");
        write("src/empty.txt", "");

        GitDiff.Sides added = sides("src/new.txt");
        assertTrue(added.head().isMissing());
        // Every line of it is added: not a blank line changed into them.
        assertEquals(List.of(new Difference(Kind.ADD, 0, 0, 0, 3)), differences(added));

        GitDiff.Sides empty = sides("src/empty.txt");
        assertTrue(empty.head().isMissing());
        assertEquals(DiffSource.of("Working Tree", "empty.txt", ""), empty.workingTree());
        // An empty file is not the same as no file.
        assertFalse(differences(empty).isEmpty());
    }

    @Test
    public void aDeletedFileIsMissingInTheWorkingTree() throws Exception {
        Files.delete(root.resolve("src/deleted.txt"));

        GitDiff.Sides sides = sides("src/deleted.txt");

        assertEquals(DiffSource.of("HEAD", "deleted.txt", "to delete\n"), sides.head());
        assertTrue(sides.workingTree().isMissing());
        assertEquals(List.of(new Difference(Kind.DELETE, 0, 2, 0, 0)), differences(sides));
    }

    @Test
    public void aRenamedFileIsComparedWithWhatItWasCalled() throws Exception {
        git("mv", "src/old name.txt", "src/new name.txt");
        write("src/new name.txt", "renamed\nand changed\n");

        // Looked for under its new name, the commit has no such file: all of it would be added.
        assertTrue(sides("src/new name.txt").head().isMissing());

        GitDiff.Sides sides = GitDiff.sides(root, root.resolve("src/new name.txt"), "src/new name.txt",
                "src/old name.txt", UTF_8, () -> false);
        assertEquals(DiffSource.of("HEAD", "new name.txt", "renamed\nas it is\n"), sides.head());
        assertEquals(List.of(new Difference(Kind.CHANGE, 1, 1, 1, 1)), differences(sides));
    }

    @Test
    public void whatIsNoTextIsSaidOnTheSideItIsOf() throws Exception {
        // Never committed: git has not looked at them, and they are checked all the same.
        Files.write(root.resolve("src/new.bin"), new byte[] {1, 2, 0, 3});
        GitDiff.Sides binary = sides("src/new.bin");
        assertTrue(binary.head().isMissing());
        assertEquals(DiffSource.unreadable("Working Tree", "new.bin", "A binary file."), binary.workingTree());

        Files.write(root.resolve("src/huge.txt"), new byte[(int) GitDiff.MOST_BYTES + 1]);
        DiffSource huge = sides("src/huge.txt").workingTree();
        assertFalse(huge.isComparable());
        assertTrue(huge.problem(), huge.problem().startsWith("Too large to compare"));

        // Committed as text, binary now: said of the working tree, the commit still has its text.
        Files.write(root.resolve("src/modified.txt"), new byte[] {0, 1, 2});
        GitDiff.Sides nowBinary = sides("src/modified.txt");
        assertEquals(DiffSource.of("HEAD", "modified.txt", COMMITTED), nowBinary.head());
        assertFalse(nowBinary.workingTree().isComparable());

        // A folder is no file to compare.
        assertFalse(GitDiff.sides(root, root.resolve("src"), "src", "src", UTF_8, () -> false).workingTree()
                .isComparable());
    }

    @Test
    public void aFileIsReadInItsCharsetOnBothSidesOrSaidNotToBeText() throws Exception {
        Files.write(root.resolve("src/latin.txt"), "café\n".getBytes(ISO_8859_1));
        git("add", "-A");
        git("commit", "--quiet", "-m", "latin");
        Files.write(root.resolve("src/latin.txt"), "café\nthé\n".getBytes(ISO_8859_1));

        GitDiff.Sides latin = sides("src/latin.txt", ISO_8859_1);
        assertEquals(DiffSource.of("HEAD", "latin.txt", "café\n"), latin.head());
        assertEquals(DiffSource.of("Working Tree", "latin.txt", "café\nthé\n"), latin.workingTree());
        assertEquals(List.of(new Difference(Kind.ADD, 1, 0, 1, 1)), differences(latin));

        // Read as what it is not, neither side is text: said, with no letter in the place of another.
        GitDiff.Sides asUtf8 = sides("src/latin.txt", UTF_8);
        assertEquals(DiffSource.unreadable("HEAD", "latin.txt", "Not text in UTF-8."), asUtf8.head());
        assertEquals(DiffSource.unreadable("Working Tree", "latin.txt", "Not text in UTF-8."), asUtf8.workingTree());
    }

    @Test
    public void whatGitConvertsOnCheckoutIsComparedAsText() throws Exception {
        write(".gitattributes", "*.latin working-tree-encoding=ISO-8859-1\n");
        Files.write(root.resolve("src/text.latin"), "café\n".getBytes(ISO_8859_1));
        git("add", "-A");
        git("commit", "--quiet", "-m", "converted");
        Files.write(root.resolve("src/text.latin"), "café\nthé\n".getBytes(ISO_8859_1));

        // Stored as UTF-8 by git, ISO-8859-1 on disk: each side read as it is.
        GitDiff.Sides sides = sides("src/text.latin", ISO_8859_1);

        assertEquals(DiffSource.of("HEAD", "text.latin", "café\n"), sides.head());
        assertEquals(DiffSource.of("Working Tree", "text.latin", "café\nthé\n"), sides.workingTree());
    }

    @Test
    public void whatCannotBeReadIsSaidAndADiffNobodyWantsIsNotRead() throws Exception {
        Path plain = Files.createTempDirectory("nbfx-no-repository");
        try {
            Assume.assumeFalse(GitClient.isRepository(plain));
            Files.writeString(plain.resolve("a.txt"), "a\n");

            GitDiff.Sides sides = GitDiff.sides(plain, plain.resolve("a.txt"), "a.txt", "a.txt", UTF_8, () -> false);

            // Not a new file: git could not tell.
            assertFalse(sides.head().isComparable());
            assertTrue(sides.head().problem(), sides.head().problem().contains("not a git repository"));
            assertEquals(DiffSource.of("Working Tree", "a.txt", "a\n"), sides.workingTree());
        } finally {
            Files.deleteIfExists(plain.resolve("a.txt"));
            Files.delete(plain);
        }
        assertThrows(CancellationException.class, () -> GitDiff.sides(root, root.resolve("src/modified.txt"),
                "src/modified.txt", "src/modified.txt", UTF_8, () -> true));
    }

    @Test
    public void eachWayToCompareHasItsTwoSides() throws Exception {
        // Three versions of one file: committed, staged, and on disk.
        write("src/modified.txt", COMMITTED + "staged\n");
        git("add", "src/modified.txt");
        write("src/modified.txt", COMMITTED + "staged\non disk\n");
        DiffSource head = DiffSource.of("HEAD", "modified.txt", COMMITTED);
        DiffSource index = DiffSource.of("Index", "modified.txt", COMMITTED + "staged\n");
        DiffSource working = DiffSource.of("Working Tree", "modified.txt", COMMITTED + "staged\non disk\n");

        assertEquals(new DiffPair(head, working), pair("src/modified.txt", VcsCompareMode.HEAD_VS_WORKING_TREE));
        assertEquals(new DiffPair(head, index), pair("src/modified.txt", VcsCompareMode.HEAD_VS_INDEX));
        assertEquals(new DiffPair(index, working), pair("src/modified.txt", VcsCompareMode.INDEX_VS_WORKING_TREE));

        // Staged and not committed: added as the index has it, there as the working tree has it.
        write("src/new.txt", "new\n");
        git("add", "src/new.txt");
        assertTrue(pair("src/new.txt", VcsCompareMode.HEAD_VS_INDEX).left().isMissing());
        assertEquals(DiffSource.of("Index", "new.txt", "new\n"), pair("src/new.txt", VcsCompareMode.HEAD_VS_INDEX).right());
        assertEquals(DiffSource.of("Index", "new.txt", "new\n"),
                pair("src/new.txt", VcsCompareMode.INDEX_VS_WORKING_TREE).left());
        // Deleted on disk and not staged: still in the index, missing in the working tree.
        Files.delete(root.resolve("src/deleted.txt"));
        DiffPair deleted = pair("src/deleted.txt", VcsCompareMode.INDEX_VS_WORKING_TREE);
        assertEquals(DiffSource.of("Index", "deleted.txt", "to delete\n"), deleted.left());
        assertTrue(deleted.right().isMissing());
    }

    @Test
    public void aDiffIsOfferedForAFileOrAFolderInAWorkingTree() throws Exception {
        LocalFileSystem files = new LocalFileSystem();
        files.setRootDirectory(root.toFile());

        assertTrue(GitDiff.canDiff(files.findResource("src/modified.txt")));
        // A folder too: the diff of all that changed in it.
        assertTrue(GitDiff.canDiff(files.findResource("src")));
        assertFalse(GitDiff.canDiff(null));
    }

    private DiffPair pair(String path, VcsCompareMode mode) {
        return GitDiff.pair(root, path, path, mode, UTF_8, () -> false);
    }

    @Test
    public void everyContentIsASideOfItsOwnKind() {
        assertEquals(DiffSource.of("HEAD", "a.txt", "text\n"),
                GitDiff.source("HEAD", "a.txt", new GitContent.Text("text\n", "aa", 5)));
        assertEquals(DiffSource.missing("HEAD", "a.txt"), GitDiff.source("HEAD", "a.txt", new GitContent.Missing()));
        // No text to compare: never an empty side, which would say the same as no file or an empty one.
        for (GitContent content : List.of(new GitContent.Binary("aa", 5), new GitContent.TooLarge(9, 5),
                new GitContent.Unmerged(), new GitContent.NotAFile("tree"), new GitContent.Malformed("UTF-8"),
                new GitContent.Failed("fatal: what git said"), new GitContent.Failed(" "))) {
            DiffSource side = GitDiff.source("HEAD", "a.txt", content);
            assertFalse(String.valueOf(content), side.isComparable());
            assertFalse(String.valueOf(content), side.problem().isBlank());
        }
        assertEquals("fatal: what git said", GitDiff.problem(new GitContent.Failed("fatal: what git said")));
        assertTrue(GitDiff.problem(new GitContent.TooLarge(9, 5)).contains("9"));
    }

    private GitDiff.Sides sides(String path) {
        return sides(path, UTF_8);
    }

    private GitDiff.Sides sides(String path, Charset working) {
        return GitDiff.sides(root, root.resolve(path), path, path, working, () -> false);
    }

    /** The blocks the diff view marks for {@code sides}. */
    private static List<Difference> differences(GitDiff.Sides sides) {
        return Diff.compute(sides.head().lines(), sides.workingTree().lines()).differences();
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
