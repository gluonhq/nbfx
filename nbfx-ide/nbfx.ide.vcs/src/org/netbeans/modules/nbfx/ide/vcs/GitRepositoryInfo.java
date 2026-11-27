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
import java.time.Instant;
import java.util.List;

/**
 * What the Repository Browser shows of a repository: what is checked out, and its branches, tags,
 * stashes and remotes, as {@link GitRefs} read them.
 *
 * @param root the folder of the working tree
 * @param head what is checked out: the branch, or without one the tags of the commit, or the start of its id
 * @param localBranches the branches of the repository, by name
 * @param remoteBranches the branches it knows of its remotes, by name
 * @param tags the tags, by name
 * @param stashes the stashes, the last one made first
 * @param remotes the remotes, by name
 * @since 1.4
 */
record GitRepositoryInfo(Path root, String head, List<Branch> localBranches, List<Branch> remoteBranches,
        List<Tag> tags, List<Stash> stashes, List<Remote> remotes) {

    GitRepositoryInfo {
        localBranches = List.copyOf(localBranches);
        remoteBranches = List.copyOf(remoteBranches);
        tags = List.copyOf(tags);
        stashes = List.copyOf(stashes);
        remotes = List.copyOf(remotes);
    }

    /** The name of the repository: that of its folder. */
    String name() {
        return nameOf(root);
    }

    /** The name the browser gives the repository in {@code root}. */
    static String nameOf(Path root) {
        return root.getFileName() == null ? root.toString() : root.getFileName().toString();
    }

    /** What names a commit in the browser, and is checked out, branched from and merged: a branch or a tag. */
    sealed interface Revision permits Branch, Tag {

        /** The name it is shown with. */
        String name();

        /** The full name of its ref, which is no other branch or tag of the same name. */
        String ref();
    }

    /**
     * A branch.
     *
     * @param name the name, with that of the remote for a remote branch: {@code origin/main}
     * @param id the id of its commit
     * @param remote whether it is a branch of a remote, as the repository last saw it
     * @param active whether it is the one checked out
     * @param upstream the branch it tracks, or {@code null} without one, or when that one is gone
     * @param ahead how many commits it has that {@code upstream} has not
     * @param behind how many commits {@code upstream} has that it has not
     */
    record Branch(String name, String id, boolean remote, boolean active, String upstream, int ahead, int behind)
            implements Revision {

        @Override
        public String ref() {
            return (remote ? "refs/remotes/" : "refs/heads/") + name;
        }

        /** The name without that of the remote: {@code main} for {@code origin/main}. */
        String localName() {
            return remote ? name.substring(name.indexOf('/') + 1) : name;
        }
    }

    /**
     * A commit, as the dialogs of the browser tell what a revision is.
     *
     * @param id its id
     * @param author who wrote it, with the address: {@code A U Thor <author@example.org>}
     * @param date when it was committed
     * @param message its message, whole
     */
    record Commit(String id, String author, Instant date, String message) {
    }

    /**
     * A tag.
     *
     * @param name the name
     * @param id the id of the commit it names, which for an annotated tag is not its own
     * @param message the first line of its message, or of the message of the commit for a tag without one
     */
    record Tag(String name, String id, String message) implements Revision {

        @Override
        public String ref() {
            return "refs/tags/" + name;
        }
    }

    /**
     * What a tag that is an object of its own says: one made with a message. A tag made without is
     * a plain name of its commit, and has none of this.
     *
     * @param id the id of the tag, which is not that of its commit
     * @param tagger who made it, with the address
     * @param date when it was made
     * @param message its message, whole
     */
    record TagDetails(String id, String tagger, Instant date, String message) {
    }

    /**
     * A stash.
     *
     * @param name what git calls it, by its place among the stashes: <code>stash@{0}</code>
     * @param id the id of its commit, which is the same wherever it is among them
     * @param message what it was saved with
     */
    record Stash(String name, String id, String message) {
    }

    /**
     * A remote.
     *
     * @param name the name
     * @param fetchUrls where it is fetched from
     * @param pushUrls where it is pushed to
     */
    record Remote(String name, List<String> fetchUrls, List<String> pushUrls) {

        Remote {
            fetchUrls = List.copyOf(fetchUrls);
            pushUrls = List.copyOf(pushUrls);
        }
    }
}
