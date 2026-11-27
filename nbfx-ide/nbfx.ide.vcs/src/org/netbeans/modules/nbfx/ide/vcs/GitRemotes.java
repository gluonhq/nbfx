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
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Remote;

/**
 * What the Repository Browser does with the remotes, through the {@code git} command line: add and
 * remove one, fetch and pull from one and push to one. Git is not let ask for a name or a password
 * where there is no one to answer: what a credential helper or an agent does not know fails, and
 * says so.
 *
 * @since 1.5
 */
final class GitRemotes {

    private GitRemotes() {
    }

    /** Adds the remote {@code name}, fetched from and pushed to {@code url}; nothing is fetched yet. */
    static GitClient.Result add(Path root, String name, String url) {
        return git(root, "remote", "add", "--", name, url);
    }

    /** Removes {@code remote}, and with it what the repository knows of its branches. */
    static GitClient.Result remove(Path root, Remote remote) {
        return git(root, "remote", "remove", "--", remote.name());
    }

    /** Fetches from {@code remote} what its configuration has fetched. */
    static GitClient.Result fetch(Path root, Remote remote) {
        return git(root, "fetch", "--", remote.name());
    }

    /** Fetches from every remote what its configuration has fetched. */
    static GitClient.Result fetchAll(Path root) {
        return git(root, "fetch", "--all");
    }

    /**
     * Fetches and brings into the branch that is checked out the one it tracks, as {@code git pull}
     * does; a merge that needs a commit gets the message git proposes.
     */
    static GitClient.Result pull(Path root) {
        return git(root, pullArguments(root, null));
    }

    /** Pushes the branch that is checked out to the one it tracks, as {@code git push} does. */
    static GitClient.Result push(Path root) {
        return git(root, "push");
    }

    /**
     * Fetches from {@code remote} and brings into what is checked out the branch that one tracks
     * there, as git has it; a merge that needs a commit gets the message git proposes.
     */
    static GitClient.Result pull(Path root, Remote remote) {
        return git(root, pullArguments(root, remote));
    }

    /**
     * The arguments of a pull, from {@code remote} or as the branch has it. Branches that both have
     * commits the other has not are merged or rebased as the configuration of git says; where it
     * says neither, git refuses to choose, and they are merged, as git did before it asked.
     */
    private static String[] pullArguments(Path root, Remote remote) {
        List<String> args = new ArrayList<>(List.of("pull", "--no-edit"));
        if (git(root, "config", "--get", "pull.rebase").exitCode() != 0
                && git(root, "config", "--get", "pull.ff").exitCode() != 0) {
            args.add("--no-rebase");
        }
        if (remote != null) {
            args.add("--");
            args.add(remote.name());
        }
        return args.toArray(String[]::new);
    }

    /**
     * Pushes {@code branch}, the branch that is checked out, to {@code remote}. A branch that tracks
     * one of this remote is pushed as git has it, to that one. Any other is pushed to the branch of
     * its own name, and one that tracks none tracks that one from then on.
     */
    static GitClient.Result push(Path root, Remote remote, Branch branch) {
        if (tracks(branch, remote)) {
            return git(root, "push", "--", remote.name());
        }
        List<String> args = new ArrayList<>(List.of("push"));
        if (branch.upstream() == null) {
            args.add("--set-upstream");
        }
        args.addAll(List.of("--", remote.name(), branch.ref() + ":" + branch.ref()));
        return git(root, args.toArray(String[]::new));
    }

    /** Whether {@code branch} tracks a branch of {@code remote}. */
    static boolean tracks(Branch branch, Remote remote) {
        return branch.upstream() != null && branch.upstream().startsWith(remote.name() + "/");
    }

    private static GitClient.Result git(Path root, String... args) {
        // In git's own words, whatever the language of the machine, and without asking the terminal there is none of.
        return GitClient.result(root, Map.of("LC_ALL", "C", "GIT_TERMINAL_PROMPT", "0"), args);
    }
}
