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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.control.MenuItem;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Branch;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Remote;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Stash;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Tag;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What the Repository Browser does with a branch, in a real repository cloned from another, and
 * what it offers to do. Skipped where git is not installed.
 */
public class GitBranchesTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private Path root;
    /** What the machine calls the first branch of a repository. */
    private String main;

    @Before
    public void createRepositories() throws Exception {
        Assume.assumeTrue("git is not installed", GitRefsTest.gitAvailable());
        Path origin = temp.newFolder("origin").toPath();
        GitRefsTest.git(origin, "init", "--quiet");
        configure(origin);
        commit(origin, "a.txt", "first\n", "initial");
        GitRefsTest.git(origin, "branch", "topic");
        root = temp.getRoot().toPath().resolve("clone");
        GitRefsTest.git(origin, "clone", "--quiet", origin.toString(), root.toString());
        configure(root);
        main = GitRefsTest.git(root, "symbolic-ref", "--short", "HEAD").trim();
    }

    @Test
    public void aRemoteBranchIsCheckedOutAsALocalOneThatTracksIt() throws Exception {
        Branch remote = remote("origin/topic");

        ok(GitBranches.create(root, "topic", remote, true));

        GitRepositoryInfo info = GitRefs.read(root);
        assertEquals("topic", info.head());
        assertEquals("origin/topic", local(info, "topic").upstream());
        assertTrue(local(info, "topic").active());

        // On its own it is no branch to be on.
        ok(GitBranches.checkout(root, remote));
        assertFalse(GitRefs.read(root).localBranches().stream().anyMatch(Branch::active));

        ok(GitBranches.checkout(root, local(info, main)));
        assertEquals(main, GitRefs.read(root).head());
    }

    @Test
    public void aBranchAndATagAreMeantByTheirOwnRef() throws Exception {
        commit(root, "a.txt", "second\n", "second");
        String second = GitRefsTest.git(root, "rev-parse", "HEAD").trim();
        Branch active = local(GitRefs.read(root), main);
        // A tag named as the branch, of another commit: what is said of the branch is not said of it.
        GitRefsTest.git(root, "tag", main, "HEAD~1");

        ok(GitBranches.create(root, "feature", active, false));
        ok(GitBranches.tag(root, "plain", active, "  "));
        ok(GitBranches.tag(root, "v1.0", active, "Release 1.0\n"));

        GitRepositoryInfo info = GitRefs.read(root);
        assertEquals(main, info.head());
        assertEquals(second, local(info, "feature").id());
        assertTrue(info.tags().toString(), info.tags().containsAll(
                List.of(new Tag("plain", second, "second"), new Tag("v1.0", second, "Release 1.0"))));

        ok(GitBranches.checkout(root, local(info, "feature")));
        assertEquals("feature", GitRefs.read(root).head());
        ok(GitBranches.checkout(root, local(info, main)));
        assertEquals(main, GitRefs.read(root).head());
    }

    @Test
    public void aBranchIsMergedRenamedAndDeleted() throws Exception {
        ok(GitBranches.create(root, "feature", local(GitRefs.read(root), main), true));
        commit(root, "b.txt", "theirs\n", "on feature");
        String feature = GitRefsTest.git(root, "rev-parse", "HEAD").trim();
        ok(GitBranches.checkout(root, local(GitRefs.read(root), main)));
        commit(root, "c.txt", "ours\n", "on main");

        // A branch whose commits are in no other is not deleted unasked.
        GitClient.Result refused = GitBranches.delete(root, local(GitRefs.read(root), "feature"), false);
        assertTrue(refused.output(), GitBranches.notMerged(refused));
        assertFalse(GitBranches.notMerged(new GitClient.Result(0, "")));

        // Both sides have commits: the merge is one more, made without asking for its message.
        ok(GitBranches.merge(root, local(GitRefs.read(root), "feature")));
        assertTrue(Files.exists(root.resolve("b.txt")));
        assertEquals(feature, GitRefsTest.git(root, "rev-parse", "HEAD^2").trim());

        ok(GitBranches.rename(root, local(GitRefs.read(root), "feature"), "done"));
        GitRepositoryInfo renamed = GitRefs.read(root);
        assertEquals(List.of("done", main).stream().sorted().toList(),
                renamed.localBranches().stream().map(Branch::name).toList());

        ok(GitBranches.delete(root, local(renamed, "done"), false));
        ok(GitBranches.delete(root, remote("origin/topic"), false));
        GitRepositoryInfo left = GitRefs.read(root);
        assertEquals(List.of(main), left.localBranches().stream().map(Branch::name).toList());
        assertEquals(List.of("origin/" + main), left.remoteBranches().stream().map(Branch::name).toList());
    }

    @Test
    public void aBranchThatIsNotMergedIsDeletedWhenToldTo() throws Exception {
        ok(GitBranches.create(root, "feature", local(GitRefs.read(root), main), true));
        commit(root, "b.txt", "theirs\n", "on feature");
        ok(GitBranches.checkout(root, local(GitRefs.read(root), main)));

        ok(GitBranches.delete(root, local(GitRefs.read(root), "feature"), true));

        assertEquals(List.of(main), GitRefs.read(root).localBranches().stream().map(Branch::name).toList());
    }

    @Test
    public void aTagIsCheckedOutBranchedFromMergedAndDeleted() throws Exception {
        String first = GitRefsTest.git(root, "rev-parse", "HEAD").trim();
        // A branch named as the tag, of another commit: what is said of the tag is not said of it.
        GitRefsTest.git(root, "tag", "-a", "v1.0", "-m", "Release 1.0\n\nThe first one.");
        GitRefsTest.git(root, "tag", "plain");
        commit(root, "a.txt", "second\n", "second");
        GitRefsTest.git(root, "branch", "v1.0");
        Tag tag = tag("v1.0");
        assertEquals(first, tag.id());

        GitRepositoryInfo.TagDetails details = GitRefs.tagDetails(root, tag);
        assertEquals("nbfx test <test@example.invalid>", details.tagger());
        assertEquals("Release 1.0\n\nThe first one.", details.message());
        assertFalse(first.equals(details.id()));
        assertTrue(details.date().isAfter(java.time.Instant.now().minusSeconds(3600)));
        assertEquals(null, GitRefs.tagDetails(root, tag("plain")));

        // A tag is none to be on: its commit is checked out, on no branch.
        ok(GitBranches.checkout(root, tag));
        GitRepositoryInfo detached = GitRefs.read(root);
        assertEquals(first, GitRefsTest.git(root, "rev-parse", "HEAD").trim());
        assertFalse(detached.localBranches().stream().anyMatch(Branch::active));

        ok(GitBranches.create(root, "from-tag", tag, true));
        assertEquals("from-tag", GitRefs.read(root).head());
        assertEquals(first, local(GitRefs.read(root), "from-tag").id());
        commit(root, "b.txt", "ours\n", "on from-tag");
        ok(GitBranches.checkout(root, local(GitRefs.read(root), main)));
        commit(root, "c.txt", "theirs\n", "third");
        GitRefsTest.git(root, "tag", "later", "from-tag");
        ok(GitBranches.merge(root, tag("later")));
        assertTrue(Files.exists(root.resolve("b.txt")));

        ok(GitBranches.deleteTag(root, tag));
        GitRepositoryInfo left = GitRefs.read(root);
        assertEquals(List.of("later", "plain"), left.tags().stream().map(Tag::name).toList());
        // The branch of its name is still there.
        assertTrue(left.localBranches().stream().anyMatch(branch -> branch.name().equals("v1.0")));
    }

    @Test
    public void changesAreStashedAppliedAndDropped() throws Exception {
        assertTrue(GitStashes.nothingSaved(GitStashes.save(root, "nothing", true)));
        Files.writeString(root.resolve("a.txt"), "changed\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("new.txt"), "new\n", StandardCharsets.UTF_8);

        // Without the files git does not track, those stay.
        GitClient.Result tracked = GitStashes.save(root, "  tracked only ", false);
        ok(tracked);
        assertFalse(GitStashes.nothingSaved(tracked));
        assertEquals("first\n", Files.readString(root.resolve("a.txt")));
        assertTrue(Files.exists(root.resolve("new.txt")));
        ok(GitStashes.save(root, "", true));
        assertFalse(Files.exists(root.resolve("new.txt")));

        List<Stash> stashes = GitRefs.read(root).stashes();
        assertEquals(List.of("stash@{0}", "stash@{1}"), stashes.stream().map(Stash::name).toList());
        assertEquals("On " + main + ": tracked only", stashes.get(1).message());
        assertTrue(stashes.get(0).message(), stashes.get(0).message().startsWith("WIP on " + main + ": "));

        // Applied, a stash is still there.
        ok(GitStashes.apply(root, stashes.get(1)));
        assertEquals("changed\n", Files.readString(root.resolve("a.txt")));
        assertEquals(stashes, GitRefs.read(root).stashes());

        ok(GitStashes.drop(root, stashes.get(0)));
        // The one that was second is the first by now: what was read of it names another, or none.
        GitClient.Result moved = GitStashes.drop(root, stashes.get(1));
        assertTrue(moved.output(), moved.exitCode() != 0 && moved.output().contains("stash@{1}"));
        GitClient.Result other = GitStashes.apply(root, stashes.get(0));
        assertTrue(other.output(), other.exitCode() != 0 && other.output().contains("Refresh the stashes"));
        List<Stash> left = GitRefs.read(root).stashes();
        assertEquals(List.of(new Stash("stash@{0}", stashes.get(1).id(), stashes.get(1).message())), left);

        ok(GitStashes.drop(root, left.get(0)));
        assertTrue(GitRefs.read(root).stashes().isEmpty());
    }

    @Test
    public void aRemoteIsAddedFetchedPulledFromPushedToAndRemoved() throws Exception {
        // Another repository to push to, with nothing in it; and the one cloned gets a commit to pull.
        Path other = temp.newFolder("other.git").toPath();
        GitRefsTest.git(other, "init", "--quiet", "--bare");
        Path origin = temp.getRoot().toPath().resolve("origin");
        commit(origin, "a.txt", "theirs\n", "second");
        String second = GitRefsTest.git(origin, "rev-parse", "HEAD").trim();

        ok(GitRemotes.add(root, "backup", other.toString()));
        GitRepositoryInfo info = GitRefs.read(root);
        assertEquals(List.of("backup", "origin"), info.remotes().stream().map(Remote::name).toList());
        Remote backup = info.remotes().get(0);
        Remote cloned = info.remotes().get(1);
        assertEquals(List.of(other.toString()), backup.pushUrls());
        GitClient.Result twice = GitRemotes.add(root, "backup", other.toString());
        assertTrue(twice.output(), twice.exitCode() != 0 && twice.output().contains("already exists"));

        // Fetched, the commit is known; pulled, it is checked out.
        ok(GitRemotes.fetch(root, cloned));
        Branch fetched = local(GitRefs.read(root), main);
        assertEquals(1, fetched.behind());
        assertEquals("first\n", Files.readString(root.resolve("a.txt")));
        ok(GitRemotes.pull(root, cloned));
        assertEquals(second, GitRefsTest.git(root, "rev-parse", "HEAD").trim());
        assertEquals("theirs\n", Files.readString(root.resolve("a.txt")));

        // To the remote it tracks, a branch is pushed as git has it; to another, to the branch of its name.
        commit(root, "b.txt", "ours\n", "third");
        String third = GitRefsTest.git(root, "rev-parse", "HEAD").trim();
        Branch current = local(GitRefs.read(root), main);
        assertTrue(GitRemotes.tracks(current, cloned));
        assertFalse(GitRemotes.tracks(current, backup));
        ok(GitRemotes.push(root, backup, current));
        assertEquals(third, GitRefsTest.git(other, "rev-parse", "refs/heads/" + main).trim());
        assertEquals("origin/" + main, local(GitRefs.read(root), main).upstream());

        // A branch that tracks none tracks the one it is pushed to.
        ok(GitBranches.create(root, "feature", current, true));
        ok(GitRemotes.push(root, backup, local(GitRefs.read(root), "feature")));
        assertEquals("backup/feature", local(GitRefs.read(root), "feature").upstream());
        assertEquals(third, GitRefsTest.git(other, "rev-parse", "refs/heads/feature").trim());

        ok(GitRemotes.remove(root, backup));
        GitRepositoryInfo left = GitRefs.read(root);
        assertEquals(List.of("origin"), left.remotes().stream().map(Remote::name).toList());
        assertFalse(left.remoteBranches().stream().anyMatch(branch -> branch.name().startsWith("backup/")));

        // As git has it: from every remote, and from and to the branch tracked.
        commit(origin, "c.txt", "theirs\n", "fourth");
        ok(GitBranches.checkout(root, local(left, main)));
        ok(GitRemotes.fetchAll(root));
        assertEquals(1, local(GitRefs.read(root), main).behind());
        // Both have a commit the other has not, and nothing says how they are brought together: merged.
        ok(GitRemotes.pull(root));
        assertTrue(Files.exists(root.resolve("c.txt")));
        assertTrue(Files.exists(root.resolve("b.txt")));
        assertEquals(GitRefsTest.git(origin, "rev-parse", "HEAD").trim(), GitRefsTest.git(root, "rev-parse", "HEAD^2").trim());
        GitRefsTest.git(origin, "checkout", "--quiet", "--detach");
        ok(GitRemotes.push(root));
        assertEquals(GitRefsTest.git(root, "rev-parse", "HEAD").trim(),
                GitRefsTest.git(origin, "rev-parse", "refs/heads/" + main).trim());

        // Where there is nothing to fetch from, git says so, and asks no one.
        GitClient.Result nowhere = GitRemotes.fetch(root, new Remote("gone", List.of(), List.of()));
        assertTrue(nowhere.output(), nowhere.exitCode() != 0 && !nowhere.output().isBlank());
    }

    @Test
    public void whatGitRefusesIsSaidInItsWords() throws Exception {
        Branch active = local(GitRefs.read(root), main);

        GitClient.Result exists = GitBranches.create(root, main, active, false);
        assertTrue(exists.output(), exists.exitCode() != 0 && exists.output().contains("already exists"));
        GitClient.Result invalid = GitBranches.rename(root, active, "no good");
        assertTrue(invalid.output(), invalid.exitCode() != 0 && invalid.output().contains("not a valid branch name"));
        assertEquals(main, GitRefs.read(root).head());
    }

    @Test
    public void theMenuOfABranchOffersWhatCanBeDoneWithIt() throws Exception {
        GitRepositoryInfo info = GitRefs.read(root);
        GitBrowserActions actions = new GitBrowserActions(() -> { });

        List<MenuItem> active = JavaFxTestSupport.onFx(() -> actions.items(info, local(info, main)));
        Branch topic = remote("origin/topic");
        List<MenuItem> remote = JavaFxTestSupport.onFx(() -> actions.items(info, topic));

        assertEquals(List.of("Checkout Revision...", "Create Branch...", "Create Tag...", "Merge Revision...",
                "Rename Branch...", "Delete Branch..."), active.stream().map(MenuItem::getText).toList());
        // The branch that is checked out is neither merged into itself nor deleted.
        assertEquals(List.of(false, false, false, true, false, true), active.stream().map(MenuItem::isDisable).toList());
        // A remote branch has its name from the remote.
        assertEquals(List.of(false, false, false, false, true, false), remote.stream().map(MenuItem::isDisable).toList());

        GitRefsTest.git(root, "tag", "v1.0");
        Tag tag = tag("v1.0");
        List<MenuItem> ofTag = JavaFxTestSupport.onFx(() -> actions.items(info, tag));
        assertEquals(List.of("Show Details", "Checkout Revision...", "Create Branch...", "Merge Revision...", "Delete Tag..."),
                ofTag.stream().map(MenuItem::getText).toList());
        assertFalse(ofTag.stream().anyMatch(MenuItem::isDisable));

        // The repository: what is done with all of it, and with what is checked out.
        List<MenuItem> ofRepository = JavaFxTestSupport.onFx(() -> actions.items(info));
        assertEquals(java.util.Arrays.asList("Show Changes", "Commit All...", null, "Stash Changes", "Create Branch...", "Create Tag...", null,
                "Remote", null, "Open Configuration"), ofRepository.stream().map(MenuItem::getText).toList());
        assertFalse(ofRepository.stream().anyMatch(MenuItem::isDisable));
        List<MenuItem> ofRemotes = ((javafx.scene.control.Menu) ofRepository.get(7)).getItems();
        assertEquals(List.of("Fetch", "Pull...", "Push..."), ofRemotes.stream().map(MenuItem::getText).toList());
        assertFalse(ofRemotes.stream().anyMatch(MenuItem::isDisable));
        assertTrue(Files.isSameFile(root.resolve(".git/config"), GitRefs.configFile(root)));

        Remote origin = info.remotes().get(0);
        assertEquals(List.of("Add Remote..."), JavaFxTestSupport.onFx(() -> actions.remoteItems(info)).stream()
                .map(MenuItem::getText).toList());
        List<MenuItem> ofRemote = JavaFxTestSupport.onFx(() -> actions.items(info, origin, RepositoryTree.Kind.REMOTE));
        assertEquals(java.util.Arrays.asList("Fetch", "Pull...", "Push...", null, "Remove Remote..."),
                ofRemote.stream().map(MenuItem::getText).toList());
        assertFalse(ofRemote.stream().anyMatch(MenuItem::isDisable));
        assertEquals(List.of("Fetch", "Pull..."), JavaFxTestSupport.onFx(
                () -> actions.items(info, origin, RepositoryTree.Kind.FETCH_URL)).stream().map(MenuItem::getText).toList());
        assertEquals(List.of("Push..."), JavaFxTestSupport.onFx(
                () -> actions.items(info, origin, RepositoryTree.Kind.PUSH_URL)).stream().map(MenuItem::getText).toList());
        // On no branch there is nothing to pull into, and nothing to push.
        GitRefsTest.git(root, "checkout", "--quiet", "--detach");
        GitRepositoryInfo detached = GitRefs.read(root);
        assertEquals(java.util.Arrays.asList(false, true, true, false, false), JavaFxTestSupport.onFx(
                () -> actions.items(detached, origin, RepositoryTree.Kind.REMOTE)).stream().map(MenuItem::isDisable).toList());
        List<MenuItem> ofDetached = JavaFxTestSupport.onFx(() -> actions.items(detached));
        assertEquals(List.of(false, false, false, false, true, true, false, false, false, false),
                ofDetached.stream().map(MenuItem::isDisable).toList());
        assertEquals(List.of(false, true, true), ((javafx.scene.control.Menu) ofDetached.get(7)).getItems().stream()
                .map(MenuItem::isDisable).toList());

        Stash stash = new Stash("stash@{0}", tag.id(), "On main: later");
        assertEquals(List.of("Stash Changes"), JavaFxTestSupport.onFx(() -> actions.stashItems(info)).stream()
                .map(MenuItem::getText).toList());
        assertEquals(java.util.Arrays.asList("Apply Stash", null, "Drop Stash..."),
                JavaFxTestSupport.onFx(() -> actions.items(info, stash)).stream().map(MenuItem::getText).toList());
    }

    private Branch remote(String name) throws Exception {
        return GitRefs.read(root).remoteBranches().stream().filter(branch -> branch.name().equals(name))
                .findFirst().orElseThrow();
    }

    private Tag tag(String name) throws Exception {
        return GitRefs.read(root).tags().stream().filter(tag -> tag.name().equals(name)).findFirst().orElseThrow();
    }

    private static Branch local(GitRepositoryInfo info, String name) {
        return info.localBranches().stream().filter(branch -> branch.name().equals(name)).findFirst().orElseThrow();
    }

    private static void ok(GitClient.Result result) {
        assertEquals(result.output(), 0, result.exitCode());
    }

    private static void commit(Path repository, String file, String content, String message) throws Exception {
        Files.writeString(repository.resolve(file), content, StandardCharsets.UTF_8);
        GitRefsTest.git(repository, "add", "-A");
        GitRefsTest.git(repository, "commit", "--quiet", "-m", message);
    }

    /** The test must not depend on the machine's configuration. */
    private static void configure(Path repository) throws Exception {
        GitRefsTest.git(repository, "config", "user.name", "nbfx test");
        GitRefsTest.git(repository, "config", "user.email", "test@example.invalid");
        GitRefsTest.git(repository, "config", "commit.gpgsign", "false");
        GitRefsTest.git(repository, "config", "tag.gpgsign", "false");
        GitRefsTest.git(repository, "config", "core.autocrlf", "false");
    }
}
