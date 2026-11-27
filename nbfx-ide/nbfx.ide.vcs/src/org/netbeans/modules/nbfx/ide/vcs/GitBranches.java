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
import java.util.Map;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Branch;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Revision;
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Tag;

/**
 * What the Repository Browser does with a branch or a tag, through the {@code git} command line:
 * check it out, start a branch or a tag from it, merge it, rename a branch and delete either. Each
 * is named to git by its full ref wherever that is taken for a revision, so that a tag is not meant
 * for the branch of its name, nor the branch for the tag.
 *
 * @since 1.5
 */
final class GitBranches {

    private GitBranches() {
    }

    /**
     * Checks {@code revision} out. A remote branch or a tag is none to be on: its commit is checked
     * out, on no branch.
     */
    static GitClient.Result checkout(Path root, Revision revision) {
        // By its full ref a local branch would be taken for a revision too, and not be the one checked out.
        return revision instanceof Branch branch && !branch.remote() ? git(root, "checkout", branch.name(), "--")
                : git(root, "checkout", "--detach", revision.ref());
    }

    /**
     * Creates the branch {@code name} where {@code from} is. Started from a remote branch it tracks
     * that one, as far as the configuration of git has it so, which it has unless told otherwise.
     *
     * @param checkout whether the new branch is checked out as well
     */
    static GitClient.Result create(Path root, String name, Revision from, boolean checkout) {
        return checkout ? git(root, "checkout", "-b", name, from.ref()) : git(root, "branch", "--", name, from.ref());
    }

    /**
     * Tags the commit of {@code branch} as {@code name}.
     *
     * @param message what the tag says; with none the tag is a plain name of the commit
     */
    static GitClient.Result tag(Path root, String name, Branch branch, String message) {
        if (message == null || message.isBlank()) {
            return git(root, "tag", "--", name, branch.ref());
        }
        return git(root, "tag", "-a", "-m", message.strip(), "--", name, branch.ref());
    }

    /**
     * Merges {@code revision} into what is checked out. A merge that needs a commit gets the message
     * git proposes; one that conflicts fails, and leaves the conflicts in the working tree.
     */
    static GitClient.Result merge(Path root, Revision revision) {
        return git(root, "merge", "--no-edit", revision.ref());
    }

    /** Renames the local branch {@code branch} to {@code name}. */
    static GitClient.Result rename(Path root, Branch branch, String name) {
        return git(root, "branch", "-m", "--", branch.name(), name);
    }

    /**
     * Deletes {@code branch}; of a remote branch what the repository knows of it, not the branch in
     * the remote.
     *
     * @param force whether a branch is deleted whose commits are in no other: without, git refuses,
     *        and {@link #notMerged} tells that refusal
     */
    static GitClient.Result delete(Path root, Branch branch, boolean force) {
        List<String> args = new ArrayList<>(List.of("branch", force ? "-D" : "-d"));
        if (branch.remote()) {
            args.add("-r");
        }
        args.add("--");
        args.add(branch.name());
        return git(root, args.toArray(String[]::new));
    }

    /** Deletes {@code tag} in the repository; in no remote it was pushed to. */
    static GitClient.Result deleteTag(Path root, Tag tag) {
        return git(root, "tag", "-d", "--", tag.name());
    }

    /** Whether {@code result} is git refusing to delete a branch that is not merged. */
    static boolean notMerged(GitClient.Result result) {
        return result.exitCode() != 0 && result.output().contains("not fully merged");
    }

    private static GitClient.Result git(Path root, String... args) {
        // What git says is matched and shown: in its own words, whatever the language of the machine.
        return GitClient.result(root, Map.of("LC_ALL", "C"), args);
    }
}
