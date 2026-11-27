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
import java.util.ArrayList;
import java.util.List;
import org.netbeans.api.nbfx.icons.ToolIcons;
import org.openide.util.NbBundle;

/**
 * The rows of the Repository Browser, as NetBeans arranges them: a repository holds its branches -
 * the local and the remote ones apart - its tags, its stashes and its remotes, each remote with the
 * addresses it is fetched from and pushed to.
 *
 * @since 1.4
 */
final class RepositoryTree {

    /** What a row is of, which is what its icon tells. */
    enum Kind {
        REPOSITORY("repository"),
        BRANCHES("branches"),
        FOLDER(ToolIcons.FOLDER),
        BRANCH("branch"),
        ACTIVE_BRANCH("active_branch"),
        TAGS("tags"),
        TAG("tag"),
        STASHES("stashes"),
        STASH("stash"),
        REMOTES("repository"),
        REMOTE("remote"),
        FETCH_URL("remote_fetch"),
        PUSH_URL("remote_push");

        private final String icon;

        Kind(String icon) {
            this.icon = icon;
        }

        /** The name of the icon, for {@link GitIcons#view(String)}. */
        String icon() {
            return icon;
        }
    }

    /**
     * A row.
     *
     * @param kind what it is of
     * @param key what tells it from the rows beside it, and is the same after the repository is read again
     * @param text what it reads
     * @param tooltip what it says of itself when pointed at, or {@code null}
     * @param item what it is the row of, for what can be done with it: the {@link GitRepositoryInfo}
     *        of a repository that was read, its branch, tag, stash or remote - the remote also for
     *        an address of it - or {@code null}
     * @param children the rows below it
     */
    record Row(Kind kind, String key, String text, String tooltip, Object item, List<Row> children) {

        Row {
            children = List.copyOf(children);
        }
    }

    /** How much of a commit id a branch or a tag is shown with, as in NetBeans. */
    private static final int ID_LENGTH = 10;
    /** How long the row of a stash gets, as in NetBeans. */
    private static final int STASH_LENGTH = 75;

    private RepositoryTree() {
    }

    /** The row of a repository that is not read yet, or could not be: {@code problem} says why, if so. */
    static Row unread(Path root, String problem) {
        return new Row(Kind.REPOSITORY, key(root), GitRepositoryInfo.nameOf(root),
                problem == null || problem.isBlank() ? root.toString() : problem, null, List.of());
    }

    /** The row of {@code repository}, with all there is below it. */
    static Row of(GitRepositoryInfo repository) {
        Row branches = group(Kind.BRANCHES, "Git.browser.branches", List.of(
                group(Kind.FOLDER, "Git.browser.local", branches(repository.localBranches())),
                group(Kind.FOLDER, "Git.browser.remote", branches(repository.remoteBranches()))));
        List<Row> tags = new ArrayList<>();
        for (GitRepositoryInfo.Tag tag : repository.tags()) {
            tags.add(leaf(Kind.TAG, tag.name(), message("Git.browser.ref", tag.name(), shortId(tag.id())),
                    tag.message().isBlank() ? null : tag.message(), tag));
        }
        List<Row> stashes = new ArrayList<>();
        for (GitRepositoryInfo.Stash stash : repository.stashes()) {
            String text = message("Git.browser.stash", stash.name(), stash.message());
            stashes.add(leaf(Kind.STASH, stash.name(), text.length() > STASH_LENGTH
                    ? text.substring(0, STASH_LENGTH - 3) + "..." : text, text, stash));
        }
        List<Row> remotes = new ArrayList<>();
        for (GitRepositoryInfo.Remote remote : repository.remotes()) {
            List<Row> urls = new ArrayList<>();
            remote.fetchUrls().forEach(url -> urls.add(leaf(Kind.FETCH_URL, url, url, message("Git.browser.fetchUrl"), remote)));
            remote.pushUrls().forEach(url -> urls.add(leaf(Kind.PUSH_URL, url, url, message("Git.browser.pushUrl"), remote)));
            remotes.add(new Row(Kind.REMOTE, Kind.REMOTE + ":" + remote.name(), remote.name(), null, remote, urls));
        }
        return new Row(Kind.REPOSITORY, key(repository.root()),
                message("Git.browser.repository", repository.name(), repository.head()), repository.root().toString(),
                repository, List.of(branches, group(Kind.TAGS, "Git.browser.tags", tags),
                        group(Kind.STASHES, "Git.browser.stashes", stashes),
                        group(Kind.REMOTES, "Git.browser.remotes", remotes)));
    }

    /** The key of the row of the repository in {@code root}. */
    static String key(Path root) {
        return Kind.REPOSITORY + ":" + root;
    }

    private static List<Row> branches(List<GitRepositoryInfo.Branch> branches) {
        List<Row> rows = new ArrayList<>();
        for (GitRepositoryInfo.Branch branch : branches) {
            // The key does not tell the active branch: checking out another one moves no row.
            rows.add(new Row(branch.active() ? Kind.ACTIVE_BRANCH : Kind.BRANCH, Kind.BRANCH + ":" + branch.name(),
                    message("Git.browser.ref", branch.name(), shortId(branch.id())), tracking(branch), branch, List.of()));
        }
        return rows;
    }

    /** How {@code branch} stands to the one it tracks, or {@code null} when it tracks none. */
    static String tracking(GitRepositoryInfo.Branch branch) {
        String upstream = branch.upstream();
        if (upstream == null) {
            return null;
        }
        if (branch.ahead() > 0 && branch.behind() > 0) {
            return message("Git.browser.tracking.mergeNeeded", upstream);
        }
        if (branch.ahead() > 0) {
            return message("Git.browser.tracking.ahead", upstream, branch.ahead());
        }
        if (branch.behind() > 0) {
            return message("Git.browser.tracking.behind", upstream, branch.behind());
        }
        return message("Git.browser.tracking.inSync", upstream);
    }

    private static Row group(Kind kind, String nameKey, List<Row> children) {
        return new Row(kind, nameKey, message(nameKey), null, null, children);
    }

    private static Row leaf(Kind kind, String name, String text, String tooltip, Object item) {
        return new Row(kind, kind + ":" + name, text, tooltip, item, List.of());
    }

    private static String shortId(String id) {
        return id.length() > ID_LENGTH ? id.substring(0, ID_LENGTH) : id;
    }

    private static String message(String key, Object... arguments) {
        return NbBundle.getMessage(RepositoryTree.class, key, arguments);
    }
}
