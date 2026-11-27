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
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Branch;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Remote;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Stash;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Tag;
import org.netbeans.modules.nbfx.ide.vcs.RepositoryTree.Kind;
import org.netbeans.modules.nbfx.ide.vcs.RepositoryTree.Row;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The rows the Repository Browser makes of a repository. */
public class RepositoryTreeTest {

    private static final String ID = "0123456789abcdef0123456789abcdef01234567";
    private static final Path ROOT = Path.of("work", "NetBeansFX").toAbsolutePath();

    @Test
    public void aRepositoryHoldsItsBranchesTagsStashesAndRemotes() {
        Row repository = RepositoryTree.of(new GitRepositoryInfo(ROOT, "main",
                List.of(new Branch("feature", ID, false, false, null, 0, 0), new Branch("main", ID, false, true, "origin/main", 0, 0)),
                List.of(new Branch("origin/main", ID, true, false, null, 0, 0)),
                List.of(new Tag("v1.0", ID, "Release 1.0"), new Tag("bare", ID, "")),
                List.of(new Stash("stash@{0}", ID, "On main: a message that goes on for long enough not to fit in the row of a stash")),
                List.of(new Remote("origin", List.of("https://example.invalid/a.git"), List.of("ssh://example.invalid/a.git")))));

        assertEquals(Kind.REPOSITORY, repository.kind());
        assertEquals("NetBeansFX [main]", repository.text());
        assertEquals(ROOT.toString(), repository.tooltip());
        assertEquals(List.of("Branches", "Tags", "Stashes", "Remotes"), texts(repository));
        assertEquals(List.of(Kind.BRANCHES, Kind.TAGS, Kind.STASHES, Kind.REMOTES), kinds(repository));

        Row branches = repository.children().get(0);
        assertEquals(List.of("Local", "Remote"), texts(branches));
        Row local = branches.children().get(0);
        assertEquals(List.of("feature - 0123456789", "main - 0123456789"), texts(local));
        assertEquals(List.of(Kind.BRANCH, Kind.ACTIVE_BRANCH), kinds(local));
        assertNull(local.children().get(0).tooltip());
        assertEquals("In sync with origin/main", local.children().get(1).tooltip());
        assertEquals(List.of("origin/main - 0123456789"), texts(branches.children().get(1)));

        Row tags = repository.children().get(1);
        assertEquals(List.of("v1.0 - 0123456789", "bare - 0123456789"), texts(tags));
        assertEquals("Release 1.0", tags.children().get(0).tooltip());
        assertNull(tags.children().get(1).tooltip());

        Row stash = repository.children().get(2).children().get(0);
        assertEquals(75, stash.text().length());
        assertTrue(stash.text(), stash.text().startsWith("stash@{0}: On main: a message") && stash.text().endsWith("..."));
        assertTrue(stash.tooltip(), stash.tooltip().endsWith("in the row of a stash"));

        Row origin = repository.children().get(3).children().get(0);
        assertEquals("origin", origin.text());
        assertEquals(List.of("https://example.invalid/a.git", "ssh://example.invalid/a.git"), texts(origin));
        assertEquals(List.of(Kind.FETCH_URL, Kind.PUSH_URL), kinds(origin));
    }

    @Test
    public void checkingOutAnotherBranchChangesNoKey() {
        Row before = RepositoryTree.of(repository("main", true));
        Row after = RepositoryTree.of(repository("feature", false));

        assertEquals(before.key(), after.key());
        assertEquals(keys(before.children().get(0).children().get(0)), keys(after.children().get(0).children().get(0)));
        assertEquals(RepositoryTree.key(ROOT), RepositoryTree.unread(ROOT, null).key());
    }

    @Test
    public void aBranchTellsHowItStandsToTheOneItTracks() {
        assertNull(RepositoryTree.tracking(new Branch("main", ID, false, true, null, 0, 0)));
        assertEquals("In sync with origin/main", RepositoryTree.tracking(new Branch("main", ID, false, true, "origin/main", 0, 0)));
        assertEquals("Ahead of origin/main by 1 commit.",
                RepositoryTree.tracking(new Branch("main", ID, false, true, "origin/main", 1, 0)));
        assertEquals("Behind origin/main by 1,234 commits.",
                RepositoryTree.tracking(new Branch("main", ID, false, true, "origin/main", 0, 1234)));
        assertEquals("Merge with origin/main needed.",
                RepositoryTree.tracking(new Branch("main", ID, false, true, "origin/main", 2, 3)));
    }

    @Test
    public void aRepositoryThatCouldNotBeReadSaysWhy() {
        Row unread = RepositoryTree.unread(ROOT, null);
        assertEquals("NetBeansFX", unread.text());
        assertEquals(ROOT.toString(), unread.tooltip());
        assertTrue(unread.children().isEmpty());
        assertEquals("fatal: not a git repository", RepositoryTree.unread(ROOT, "fatal: not a git repository").tooltip());
    }

    private static GitRepositoryInfo repository(String head, boolean mainActive) {
        return new GitRepositoryInfo(ROOT, head,
                List.of(new Branch("feature", ID, false, !mainActive, null, 0, 0), new Branch("main", ID, false, mainActive, null, 0, 0)),
                List.of(), List.of(), List.of(), List.of());
    }

    private static List<String> texts(Row row) {
        return row.children().stream().map(Row::text).toList();
    }

    private static List<Kind> kinds(Row row) {
        return row.children().stream().map(Row::kind).toList();
    }

    private static List<String> keys(Row row) {
        return row.children().stream().map(Row::key).toList();
    }
}
