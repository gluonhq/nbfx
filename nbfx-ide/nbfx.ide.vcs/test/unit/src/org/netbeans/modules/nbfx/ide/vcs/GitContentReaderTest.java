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
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
        Files.write(root.resolve("text.latin"), "caf\u00e9\n".getBytes(ISO_8859_1));
        Files.write(root.resolve("plain.txt"), "caf\u00e9\n".getBytes(ISO_8859_1));
        git("add", "-A");
        git("commit", "--quiet", "-m", "converted");

        // The file in the working tree is ISO-8859-1 either way; only one is stored as it is.
        assertEquals(UTF_8, GitClient.storedCharset(root, "text.latin", ISO_8859_1, true));
        assertEquals(UTF_8, GitClient.storedCharset(root, "text.latin", ISO_8859_1, false));
        assertEquals(ISO_8859_1, GitClient.storedCharset(root, "plain.txt", ISO_8859_1, true));
        assertEquals(ISO_8859_1, GitClient.storedCharset(root, "no such file.txt", ISO_8859_1, true));
        assertEquals("caf\u00e9\n", ((GitContent.Text) GitClient.headContent(root, "text.latin",
                GitClient.storedCharset(root, "text.latin", ISO_8859_1, true), LIMIT, () -> false)).text());
        assertEquals("caf\u00e9\n", ((GitContent.Text) GitClient.headContent(root, "plain.txt",
                GitClient.storedCharset(root, "plain.txt", ISO_8859_1, true), LIMIT, () -> false)).text());
    }

    @Test
    public void theCharsetIsThatOfWhenTheContentWasStored() throws Exception {
        // Committed as it is, in ISO-8859-1, before any attribute said otherwise.
        Files.write(root.resolve("text.latin"), "caf\u00e9\n".getBytes(ISO_8859_1));
        git("add", "-A");
        git("commit", "--quiet", "-m", "as it is");
        write(".gitattributes", "*.latin working-tree-encoding=ISO-8859-1\n");

        // An attribute only in the working tree changes nothing of what git has.
        assertEquals(ISO_8859_1, GitClient.storedCharset(root, "text.latin", ISO_8859_1, true));
        assertEquals(ISO_8859_1, GitClient.storedCharset(root, "text.latin", ISO_8859_1, false));

        // Staged with the attribute, the index has it converted; the commit still has not.
        git("add", ".gitattributes");
        git("add", "--renormalize", ".");
        assertEquals(UTF_8, GitClient.storedCharset(root, "text.latin", ISO_8859_1, false));
        assertEquals(ISO_8859_1, GitClient.storedCharset(root, "text.latin", ISO_8859_1, true));
        assertEquals("caf\u00e9\n", ((GitContent.Text) GitClient.indexContent(root, "text.latin",
                GitClient.storedCharset(root, "text.latin", ISO_8859_1, false), LIMIT, () -> false)).text());
        assertEquals("caf\u00e9\n", ((GitContent.Text) GitClient.headContent(root, "text.latin",
                GitClient.storedCharset(root, "text.latin", ISO_8859_1, true), LIMIT, () -> false)).text());
        // Read with the charset of the other, the commit is no text - which is why it matters.
        assertEquals(new GitContent.Malformed("UTF-8"), head("text.latin"));
    }

    @Test
    public void aPathIsMatchedByItsBytes() {
        byte[] invalid = {'1', '0', '0', '6', '4', '4', ' ', 'b', 'l', 'o', 'b', ' ', 'a', 'a', '\t',
            'n', (byte) 0xFF, '.', 't', 'x', 't', 0};
        byte[] valid = "100644 blob bb\tn\ufffd.txt\u0000100644 blob cc\tdocs/n\ufffd.txt\u0000"
                .getBytes(UTF_8);
        byte[] listing = new byte[invalid.length + valid.length];
        System.arraycopy(invalid, 0, listing, 0, invalid.length);
        System.arraycopy(valid, 0, listing, invalid.length, valid.length);

        // A name that is no UTF-8 would be read as the one with the replacement character.
        List<String[]> entries = GitContentReader.entries(listing, "n\ufffd.txt");

        assertEquals(1, entries.size());
        assertEquals(List.of("100644", "blob", "bb"), List.of(entries.get(0)));
        assertTrue(GitContentReader.entries(listing, "n.txt").isEmpty());
        assertTrue(GitContentReader.entries(new byte[0], "n.txt").isEmpty());
    }

    @Test
    public void askedForWithoutALimitAFileIsReadUpToWhatCanBeHeld() throws Exception {
        // Asked for without a limit, the limit is what can be read at all.
        GitContent.Text text = (GitContent.Text) GitClient.headContent(root, "modified.txt", UTF_8, Long.MAX_VALUE,
                () -> false);
        assertEquals(COMMITTED, text.text());
        assertTrue(GitContentReader.MOST_BYTES < Integer.MAX_VALUE);
    }

    @Test(timeout = 20_000)
    public void aReadThatIsWaitedForEndsItsGitWhenGivenUp() throws Exception {
        // A git that waits to be asked, and so writes nothing: a read of it never ends by itself.
        Process waiting = GitClient.start(root, Map.of(), "cat-file", "--batch");
        Future<byte[]> reading = GitClient.READERS.submit(() -> waiting.getInputStream().readAllBytes());
        int[] asked = new int[1];

        assertThrows(CancellationException.class, () -> GitClient.await(reading, waiting, () -> ++asked[0] == 3));

        assertEquals(3, asked[0]);
        assertTrue(waiting.waitFor(10, TimeUnit.SECONDS));
        assertFalse(waiting.isAlive());

        // And when the thread that waits is interrupted, which it stays.
        Process other = GitClient.start(root, Map.of(), "cat-file", "--batch");
        Future<byte[]> otherReading = GitClient.READERS.submit(() -> other.getInputStream().readAllBytes());
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> GitClient.await(otherReading, other, () -> false));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertTrue(other.waitFor(10, TimeUnit.SECONDS));
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

    @Test
    public void aFileOnDiskIsReadWithTheSameCareAsWhatGitHas() throws Exception {
        // As it is on disk, not as git has it: no object id.
        write("modified.txt", COMMITTED + "on disk\n");
        assertEquals(new GitContent.Text(COMMITTED + "on disk\n", null, COMMITTED.length() + 8),
                ((GitContent.Text) GitContentReader.workingTree(root.resolve("modified.txt"), UTF_8, LIMIT))
                        .withMode(null));
        assertEquals(new GitContent.Missing(), GitContentReader.workingTree(root.resolve("gone.txt"), UTF_8, LIMIT));
        assertEquals(new GitContent.NotAFile("tree"), GitContentReader.workingTree(root, UTF_8, LIMIT));

        // Never committed, so never looked at by git: binary, too large, or no text in its charset.
        Files.write(root.resolve("new.bin"), new byte[] {1, 2, 0, 3});
        assertEquals(new GitContent.Binary(null, 4), GitContentReader.workingTree(root.resolve("new.bin"), UTF_8, LIMIT));
        write("new-large.txt", "0123456789\n".repeat(1000));
        assertEquals(new GitContent.TooLarge(11_000, 10_999),
                GitContentReader.workingTree(root.resolve("new-large.txt"), UTF_8, 10_999));
        Files.write(root.resolve("latin.txt"), "caf\u00e9\n".getBytes(ISO_8859_1));
        assertEquals(new GitContent.Malformed("UTF-8"),
                GitContentReader.workingTree(root.resolve("latin.txt"), UTF_8, LIMIT));
        assertEquals("caf\u00e9\n", ((GitContent.Text) GitContentReader.workingTree(root.resolve("latin.txt"),
                ISO_8859_1, LIMIT)).text());
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

    @Test
    public void aLinkIsWhereItPointsToAsGitStoresItNotWhatIsThere() throws Exception {
        write("target.txt", "the content of the target\n");
        Path link = root.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, Path.of("target.txt"));
        } catch (UnsupportedOperationException | IOException ex) {
            Assume.assumeNoException("no links on this disk", ex);
        }
        git("add", "-A");
        git("commit", "--quiet", "-m", "a link");

        // In the commit and on disk the same: the path it points to, of the mode of a link.
        GitContent.Text committed = (GitContent.Text) GitContentReader.head(root, "link.txt", UTF_8, LIMIT, () -> false);
        GitContent.Text onDisk = (GitContent.Text) GitContentReader.workingTree(link, UTF_8, LIMIT);
        assertEquals("target.txt", committed.text());
        assertEquals("target.txt", onDisk.text());
        assertEquals(GitContentReader.LINK_MODE, committed.mode());
        assertEquals(GitContentReader.LINK_MODE, onDisk.mode());

        // One that points nowhere is still a link, not a file that is gone.
        Files.delete(link);
        Files.createSymbolicLink(link, Path.of("nowhere.txt"));
        assertEquals("nowhere.txt", ((GitContent.Text) GitContentReader.workingTree(link, UTF_8, LIMIT)).text());
        // And one to a folder is not the folder.
        Files.delete(link);
        Files.createSymbolicLink(link, root);
        assertTrue(GitContentReader.workingTree(link, UTF_8, LIMIT) instanceof GitContent.Text);
    }

    @Test
    public void aFileTellsItsModeWhereTheDiskKeepsOneAndAReadNobodyWantsIsGivenUp() throws Exception {
        Path script = root.resolve("modified.txt");
        Assume.assumeTrue("no permissions on this disk",
                Files.getFileAttributeView(script, java.nio.file.attribute.PosixFileAttributeView.class) != null);
        assertEquals(GitContentReader.FILE_MODE,
                ((GitContent.Text) GitContentReader.head(root, "modified.txt", UTF_8, LIMIT, () -> false)).mode());
        assertEquals(GitContentReader.FILE_MODE, ((GitContent.Text) GitContentReader.workingTree(script, UTF_8, LIMIT)).mode());
        assertTrue(script.toFile().setExecutable(true));
        assertEquals(GitContentReader.EXECUTABLE_MODE,
                ((GitContent.Text) GitContentReader.workingTree(script, UTF_8, LIMIT)).mode());

        assertThrows(CancellationException.class, () -> GitContentReader.workingTree(script, UTF_8, LIMIT, () -> true));
        // Asked between the pieces it is read in: given up part way through a large file.
        write("large.txt", "0123456789abcdef".repeat(1 << 15));
        int[] asked = new int[1];
        assertThrows(CancellationException.class,
                () -> GitContentReader.workingTree(root.resolve("large.txt"), UTF_8, LIMIT, () -> ++asked[0] > 2));
        assertEquals(3, asked[0]);
    }
}
