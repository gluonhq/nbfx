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
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Branch;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Remote;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Stash;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Tag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What {@link GitRefs} makes of what git prints, and what it reads of real repositories; those
 * tests are skipped where git is not installed.
 */
public class GitRefsTest {

    private static final String ID = "0123456789abcdef0123456789abcdef01234567";
    private static final String OTHER = "89abcdef0123456789abcdef0123456789abcdef";

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void branchesAreToldApartWithWhatTheyTrack() {
        List<Branch> local = new ArrayList<>();
        List<Branch> remote = new ArrayList<>();

        GitRefs.parseBranches(String.join("\n",
                "refs/heads/feature/x\t" + OTHER + "\t \torigin/feature/x\t[ahead 2, behind 1]\t",
                "refs/heads/main\t" + ID + "\t*\torigin/main\t\t",
                "refs/heads/old\t" + ID + "\t \torigin/old\t[gone]\t",
                "refs/heads/own\t" + ID + "\t \t\t\t",
                "refs/remotes/origin/HEAD\t" + ID + "\t \t\t\trefs/remotes/origin/main",
                "refs/remotes/origin/main\t" + ID + "\t \t\t\t",
                "refs/stash\t" + ID + "\t \t\t\t",
                ""), local, remote);

        assertEquals(List.of(new Branch("feature/x", OTHER, false, false, "origin/feature/x", 2, 1),
                new Branch("main", ID, false, true, "origin/main", 0, 0),
                new Branch("old", ID, false, false, null, 0, 0),
                new Branch("own", ID, false, false, null, 0, 0)), local);
        // The head of the remote names one of its branches, and is none itself.
        assertEquals(List.of(new Branch("origin/main", ID, true, false, null, 0, 0)), remote);
    }

    @Test
    public void anAnnotatedTagNamesTheCommitItPointsTo() {
        List<Tag> tags = GitRefs.parseTags("refs/tags/light\t" + ID + "\t\tThe commit\tand a tab\n"
                + "refs/tags/v1.0\t" + OTHER + "\t" + ID + "\tRelease 1.0\n");

        assertEquals(List.of(new Tag("light", ID, "The commit\tand a tab"), new Tag("v1.0", ID, "Release 1.0")), tags);
    }

    @Test
    public void stashesKeepTheirOrderAndTheirMessage() {
        assertEquals(List.of(new Stash("stash@{0}", ID, "On main: second"), new Stash("stash@{1}", OTHER, "WIP on main: 0123456\tx")),
                GitRefs.parseStashes("stash@{0}\t" + ID + "\tOn main: second\nstash@{1}\t" + OTHER + "\tWIP on main: 0123456\tx\n"));
        assertTrue(GitRefs.parseStashes("").isEmpty());
    }

    @Test
    public void remotesHaveTheAddressesTheyAreFetchedFromAndPushedTo() {
        List<Remote> remotes = GitRefs.parseRemotes(String.join("\n",
                "origin\thttps://example.invalid/a.git (fetch)",
                "origin\thttps://example.invalid/a.git (push)",
                "origin\tssh://example.invalid/mirror (push).git (push)",
                "partial\t/some/where (fetch) [blob:none]",
                "partial\t/some/where (push)",
                ""));

        assertEquals(List.of(
                new Remote("origin", List.of("https://example.invalid/a.git"),
                        List.of("https://example.invalid/a.git", "ssh://example.invalid/mirror (push).git")),
                new Remote("partial", List.of("/some/where"), List.of("/some/where"))), remotes);
    }

    @Test
    public void aHeadOnNoBranchIsCalledByItsTagsOrItsId() {
        List<Tag> tags = List.of(new Tag("v1", ID, ""), new Tag("other", OTHER, ""), new Tag("v1-final", ID, ""));

        assertEquals("v1,v1-final", GitRefs.detachedHead(ID, tags));
        assertEquals("0123456...", GitRefs.detachedHead(ID, List.of()));
    }

    @Test
    public void readsARepository() throws Exception {
        Assume.assumeTrue("git is not installed", gitAvailable());
        Path origin = repository("origin");
        Path root = temp.getRoot().toPath().resolve("clone");
        git(origin, "clone", "--quiet", origin.toString(), root.toString());
        configure(root);
        String branch = git(root, "symbolic-ref", "--short", "HEAD").trim();
        git(root, "tag", "light");
        git(root, "tag", "-a", "v1.0", "-m", "Release 1.0");
        Files.writeString(root.resolve("a.txt"), "more\n", StandardCharsets.UTF_8);
        git(root, "commit", "--quiet", "-am", "second");
        git(root, "branch", "feature");
        Files.writeString(root.resolve("a.txt"), "not yet\n", StandardCharsets.UTF_8);
        git(root, "stash", "push", "--quiet", "-m", "kept for later");
        String first = git(origin, "rev-parse", "HEAD").trim();
        String second = git(root, "rev-parse", "HEAD").trim();

        GitRepositoryInfo info = GitRefs.read(root);

        assertEquals(branch, info.head());
        assertEquals("clone", info.name());
        assertEquals(List.of(new Branch("feature", second, false, false, null, 0, 0),
                new Branch(branch, second, false, true, "origin/" + branch, 1, 0)), sorted(info.localBranches()));
        assertEquals(List.of(new Branch("origin/" + branch, first, true, false, null, 0, 0)), info.remoteBranches());
        assertEquals(List.of(new Tag("light", first, "initial"), new Tag("v1.0", first, "Release 1.0")), info.tags());
        assertEquals(List.of(new Stash("stash@{0}", git(root, "rev-parse", "stash@{0}").trim(),
                "On " + branch + ": kept for later")), info.stashes());
        assertEquals(1, info.remotes().size());
        assertEquals("origin", info.remotes().get(0).name());
        assertEquals(info.remotes().get(0).fetchUrls(), info.remotes().get(0).pushUrls());
        assertEquals(1, info.remotes().get(0).fetchUrls().size());

        // On no branch: the tags of the commit say where.
        git(root, "checkout", "--quiet", "--detach", "v1.0");
        GitRepositoryInfo detached = GitRefs.read(root);
        assertEquals("light,v1.0", detached.head());
        assertFalse(detached.localBranches().stream().anyMatch(Branch::active));
    }

    @Test
    public void aCommitIsReadWithItsAuthorDateAndWholeMessage() throws Exception {
        GitRepositoryInfo.Commit commit = GitRefs.parseCommit(ID + "\u0000A U Thor <author@example.invalid>\u00001783618564"
                + "\u0000Subject\n\nBody, with a \u0000 in it\n\n");

        assertEquals(new GitRepositoryInfo.Commit(ID, "A U Thor <author@example.invalid>",
                java.time.Instant.parse("2026-07-09T17:36:04Z"), "Subject\n\nBody, with a \u0000 in it"), commit);
        try {
            GitRefs.parseCommit("fatal: bad revision");
            assertNull("made a commit of what is none", commit);
        } catch (IOException expected) {
            assertEquals("fatal: bad revision", expected.getMessage());
        }

        Assume.assumeTrue("git is not installed", gitAvailable());
        Path root = repository("commits");
        git(root, "tag", "-a", "v1.0", "-m", "Release 1.0");
        String head = git(root, "rev-parse", "HEAD").trim();
        // Of an annotated tag it is the commit that is told, not the tag.
        for (String revision : List.of("HEAD", "refs/tags/v1.0", head)) {
            GitRepositoryInfo.Commit read = GitRefs.commit(root, revision);
            assertEquals(head, read.id());
            assertEquals("nbfx test <test@example.invalid>", read.author());
            assertEquals("initial", read.message());
            assertTrue(read.date().toString(), read.date().isAfter(java.time.Instant.now().minusSeconds(3600)));
        }
        try {
            GitRefs.commit(root, "refs/heads/none");
            assertNull("read a commit there is none of", root);
        } catch (IOException expected) {
            assertFalse(expected.getMessage().isBlank());
        }
    }

    @Test
    public void readsARepositoryWithoutACommitAndNoneWithoutARepository() throws Exception {
        Assume.assumeTrue("git is not installed", gitAvailable());
        Path root = temp.newFolder("fresh").toPath();
        git(root, "init", "--quiet");
        String branch = git(root, "symbolic-ref", "--short", "HEAD").trim();

        GitRepositoryInfo info = GitRefs.read(root);

        assertEquals(branch, info.head());
        assertTrue(info.localBranches().isEmpty());
        assertTrue(info.tags().isEmpty());
        assertTrue(info.stashes().isEmpty());
        assertTrue(info.remotes().isEmpty());

        Path plain = temp.newFolder("plain").toPath();
        Assume.assumeTrue("the temporary folder is in a repository", GitMenus.workingTreeOf(plain.toFile()) == null);
        try {
            GitRefs.read(plain);
            assertNull("read a folder that is no repository", plain);
        } catch (IOException expected) {
            assertFalse(expected.getMessage().isBlank());
        }
    }

    private static List<Branch> sorted(List<Branch> branches) {
        // "feature" first, whatever the machine calls the first branch of a repository.
        return branches.stream().sorted((a, b) -> Boolean.compare(a.active(), b.active())).toList();
    }

    private Path repository(String name) throws Exception {
        Path root = temp.newFolder(name).toPath();
        git(root, "init", "--quiet");
        configure(root);
        Files.writeString(root.resolve("a.txt"), "first\n", StandardCharsets.UTF_8);
        git(root, "add", "-A");
        git(root, "commit", "--quiet", "-m", "initial");
        return root;
    }

    /** The test must not depend on the machine's configuration. */
    private static void configure(Path root) throws Exception {
        git(root, "config", "user.name", "nbfx test");
        git(root, "config", "user.email", "test@example.invalid");
        git(root, "config", "commit.gpgsign", "false");
        git(root, "config", "tag.gpgsign", "false");
        git(root, "config", "core.autocrlf", "false");
    }

    static String git(Path root, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(String.join(" ", command) + "\n" + output, 0, process.waitFor());
        return output;
    }

    static boolean gitAvailable() {
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
