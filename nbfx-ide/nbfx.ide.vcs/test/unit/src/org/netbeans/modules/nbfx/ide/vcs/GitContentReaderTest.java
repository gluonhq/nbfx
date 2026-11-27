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
import java.util.concurrent.CancellationException;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.api.nbfx.diff.Diff;
import org.netbeans.api.nbfx.diff.Difference;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Reads what a real repository has of a file, in its last commit and in its index, through the
 * {@code git} command line: its text, and each reason there is none. Skipped where git is not
 * installed.
 */
public class GitContentReaderTest {

    private static final long LIMIT = 1 << 20;
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
        write("modified.txt", COMMITTED);
        git("add", "-A");
        git("commit", "--quiet", "-m", "initial");
    }

    @Test
    public void contentOfTheLastCommitAndOfTheIndex() throws Exception {
        write("modified.txt", COMMITTED + "staged\n");
        git("add", "modified.txt");
        write("modified.txt", COMMITTED + "staged\nnot staged\n");
        write("new.txt", "new\n");

        GitContent.Text head = (GitContent.Text) head("modified.txt");
        GitContent.Text index = (GitContent.Text) index("modified.txt");

        assertEquals(COMMITTED, head.text());
        assertEquals(COMMITTED + "staged\n", index.text());
        // Told with the object it is of: the same id is the same content, another id another.
        assertEquals(gitOutput("rev-parse", "HEAD:modified.txt"), head.objectId());
        assertEquals(COMMITTED.length(), head.size());
        assertNotEquals(head.objectId(), index.objectId());
        // A file git does not have yet has no content there, and that is no failure.
        assertEquals(new GitContent.Missing(), head("new.txt"));
        assertEquals(new GitContent.Missing(), index("new.txt"));
        // An empty file is text with no content, not a missing one.
        write("empty.txt", "");
        git("add", "empty.txt");
        assertEquals("", ((GitContent.Text) index("empty.txt")).text());
        assertEquals(new GitContent.Missing(), head("empty.txt"));
    }

    @Test
    public void aNameIsMeantAsWrittenWhateverItLooksLike() throws Exception {
        // ":1:notes.txt" is, to git, the first side of a conflict in notes.txt.
        write("notes.txt", "plain\n");
        write("1:notes.txt", "with a colon\n");
        write("0:notes.txt", "with a zero\n");
        write("a*b.txt", "with a star\n");
        write("aXb.txt", "what the star would match\n");
        write("[ab].txt", "with brackets\n");
        git("add", "-A");

        for (String[] file : new String[][] {{"notes.txt", "plain\n"}, {"1:notes.txt", "with a colon\n"},
                {"0:notes.txt", "with a zero\n"}, {"a*b.txt", "with a star\n"}, {"[ab].txt", "with brackets\n"}}) {
            assertEquals(file[0], file[1], ((GitContent.Text) index(file[0])).text());
            assertEquals(file[0], new GitContent.Missing(), head(file[0]));
        }
        git("commit", "--quiet", "-m", "names");
        assertEquals("with a colon\n", ((GitContent.Text) head("1:notes.txt")).text());
        assertEquals("with a star\n", ((GitContent.Text) head("a*b.txt")).text());
        // Not there, though git would find something for it as a pattern or a stage.
        assertEquals(new GitContent.Missing(), index("2:notes.txt"));
        assertEquals(new GitContent.Missing(), index("a?b.txt"));
        assertEquals(new GitContent.Missing(), head("a?b.txt"));
    }

    @Test
    public void aFileInConflictHasNoContentInTheIndex() throws Exception {
        git("checkout", "--quiet", "-b", "side");
        write("modified.txt", "theirs\n");
        git("commit", "--quiet", "-am", "side");
        git("checkout", "--quiet", "main");
        write("modified.txt", "ours\n");
        git("commit", "--quiet", "-am", "main");
        assertTrue(gitFails("merge", "--quiet", "side"));

        assertEquals(new GitContent.Unmerged(), index("modified.txt"));
        // The commit still has its own.
        assertEquals("ours\n", ((GitContent.Text) head("modified.txt")).text());
    }

    @Test
    public void noCommitYetIsNoContentAndABranchThatCannotBeReadIsAFailure() throws Exception {
        Path fresh = temp.newFolder("fresh").toPath();
        root = fresh;
        git("init", "--quiet", "--initial-branch=main");
        write("new.txt", "new\n");
        git("add", "new.txt");

        // Everything is new before the first commit.
        assertEquals(new GitContent.Missing(), head("new.txt"));
        assertEquals("new\n", ((GitContent.Text) index("new.txt")).text());

        // A branch that names no commit git can read: not the same as none yet.
        Files.createDirectories(fresh.resolve(".git/refs/heads"));
        Files.writeString(fresh.resolve(".git/refs/heads/main"), "garbage\n");
        GitContent broken = head("new.txt");
        assertTrue(String.valueOf(broken), broken instanceof GitContent.Failed failed && !failed.message().isBlank());
    }

    @Test
    public void contentThatCannotBeReadIsAFailureNotANewFile() throws Exception {
        Path plain = Files.createTempDirectory("nbfx-no-repository");
        try {
            Assume.assumeFalse(GitClient.isRepository(plain));
            root = plain;

            GitContent head = head("modified.txt");
            GitContent index = index("modified.txt");

            assertTrue(String.valueOf(head), head instanceof GitContent.Failed failed
                    && failed.message().contains("not a git repository"));
            assertTrue(String.valueOf(index), index instanceof GitContent.Failed failed
                    && failed.message().contains("not a git repository"));
        } finally {
            Files.delete(plain);
        }
    }

    @Test
    public void aFileLargerThanWantedIsNotRead() throws Exception {
        write("large.txt", "0123456789\n".repeat(1000));
        git("add", "-A");
        git("commit", "--quiet", "-m", "large");

        assertEquals(new GitContent.TooLarge(11_000, 10_999),
                GitClient.headContent(root, "large.txt", UTF_8, 10_999, () -> false));
        assertEquals(new GitContent.TooLarge(11_000, 0),
                GitClient.indexContent(root, "large.txt", UTF_8, 0, () -> false));
        // As large as wanted is read.
        assertEquals(11_000, ((GitContent.Text) GitClient.headContent(root, "large.txt", UTF_8, 11_000,
                () -> false)).text().length());
    }

    @Test
    public void binaryContentIsToldAsSuchAndTextReadInItsEncodingOrNotAtAll() throws Exception {
        Files.write(root.resolve("image.bin"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2});
        // "café" as ISO-8859-1 is no valid UTF-8.
        Files.write(root.resolve("latin.txt"), "café\n".getBytes(ISO_8859_1));
        git("add", "-A");
        git("commit", "--quiet", "-m", "binary and latin");

        assertTrue(head("image.bin") instanceof GitContent.Binary binary && binary.size() == 7);
        assertTrue(index("image.bin") instanceof GitContent.Binary);
        assertEquals("café\n",
                ((GitContent.Text) GitClient.headContent(root, "latin.txt", ISO_8859_1, LIMIT, () -> false)).text());
        // Not text in the charset asked for: said so, with no letter put in the place of another.
        assertEquals(new GitContent.Malformed("UTF-8"), head("latin.txt"));
    }

    @Test
    public void whatGitConvertsOnCheckoutIsStoredAsUtf8() throws Exception {
        write(".gitattributes", "*.latin working-tree-encoding=ISO-8859-1\n");
        Files.write(root.resolve("text.latin"), "café\n".getBytes(ISO_8859_1));
        Files.write(root.resolve("plain.txt"), "café\n".getBytes(ISO_8859_1));
        git("add", "-A");
        git("commit", "--quiet", "-m", "converted");

        // The file in the working tree is ISO-8859-1 either way; only one is stored as it is.
        assertEquals(UTF_8, GitClient.storedCharset(root, "text.latin", ISO_8859_1));
        assertEquals(ISO_8859_1, GitClient.storedCharset(root, "plain.txt", ISO_8859_1));
        assertEquals(ISO_8859_1, GitClient.storedCharset(root, "no such file.txt", ISO_8859_1));
        assertEquals("café\n", ((GitContent.Text) GitClient.headContent(root, "text.latin",
                GitClient.storedCharset(root, "text.latin", ISO_8859_1), LIMIT, () -> false)).text());
        assertEquals("café\n", ((GitContent.Text) GitClient.headContent(root, "plain.txt",
                GitClient.storedCharset(root, "plain.txt", ISO_8859_1), LIMIT, () -> false)).text());
    }

    @Test
    public void aFolderIsNoFile() throws Exception {
        write("docs/guide.txt", "guide\n");
        git("add", "-A");
        git("commit", "--quiet", "-m", "docs");

        // Not the listing git would show of it.
        assertEquals(new GitContent.NotAFile("tree"), head("docs"));
        // The index has files only.
        assertEquals(new GitContent.Missing(), index("docs"));
        assertEquals("guide\n", ((GitContent.Text) head("docs/guide.txt")).text());
    }

    @Test
    public void contentNobodyWantsIsNotRead() throws Exception {
        assertThrows(CancellationException.class,
                () -> GitClient.headContent(root, "modified.txt", UTF_8, LIMIT, () -> true));
        assertThrows(CancellationException.class,
                () -> GitClient.indexContent(root, "modified.txt", UTF_8, LIMIT, () -> true));
        // Given up between the two questions it takes: what is there, and its content.
        int[] asked = new int[1];
        assertThrows(CancellationException.class,
                () -> GitClient.headContent(root, "modified.txt", UTF_8, LIMIT, () -> ++asked[0] > 1));

        // An interrupted thread reads nothing, and stays interrupted.
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> head("modified.txt"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(COMMITTED, ((GitContent.Text) head("modified.txt")).text());
    }

    @Test
    public void theTwoContentsOfAFileAreComparedLineByLine() throws Exception {
        write("modified.txt", "first line\nsecond line, changed\nthird line\nfourth line\n");
        git("add", "modified.txt");

        GitContent.Text head = (GitContent.Text) head("modified.txt");
        GitContent.Text index = (GitContent.Text) index("modified.txt");
        List<Difference> differences = Diff.compute(Diff.lines(head.text()), Diff.lines(index.text())).differences();

        assertEquals(List.of(new Difference(Difference.Kind.CHANGE, 1, 1, 1, 1),
                new Difference(Difference.Kind.ADD, 3, 0, 3, 1)), differences);
        // The same object on both sides needs no comparison to be known the same.
        git("commit", "--quiet", "-m", "changed");
        assertEquals(((GitContent.Text) head("modified.txt")).objectId(),
                ((GitContent.Text) index("modified.txt")).objectId());
        assertFalse(differences.isEmpty());
    }

    private GitContent head(String path) {
        return GitClient.headContent(root, path, UTF_8, LIMIT, () -> false);
    }

    private GitContent index(String path) {
        return GitClient.indexContent(root, path, UTF_8, LIMIT, () -> false);
    }

    private void write(String name, String content) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void git(String... args) throws Exception {
        assertEquals(String.join(" ", args), 0, run(args).exitCode());
    }

    private boolean gitFails(String... args) throws Exception {
        return run(args).exitCode() != 0;
    }

    private String gitOutput(String... args) throws Exception {
        return run(args).output().strip();
    }

    private GitClient.Result run(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new GitClient.Result(process.waitFor(), output);
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
