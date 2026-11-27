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
import org.netbeans.modules.nbfx.ide.vcs.GitRepositoryInfo.Stash;
import org.openide.util.NbBundle;

/**
 * What the Repository Browser does with the stashes, through the {@code git} command line: save the
 * changes of the working tree as one, apply one and drop one. Git names a stash by its place among
 * them, which another takes when one is saved or dropped elsewhere: a stash is applied or dropped
 * only while its place still holds the commit the browser read there.
 *
 * @since 1.5
 */
final class GitStashes {

    private GitStashes() {
    }

    /**
     * Saves the changes of the working tree as a stash, and takes them out of it.
     *
     * @param message what the stash is saved with; with none git says where it was made
     * @param untracked whether the files git does not track yet are saved, and taken away, as well
     */
    static GitClient.Result save(Path root, String message, boolean untracked) {
        List<String> args = new ArrayList<>(List.of("stash", "push"));
        if (untracked) {
            args.add("--include-untracked");
        }
        if (message != null && !message.isBlank()) {
            args.add("-m");
            args.add(message.strip());
        }
        return git(root, args.toArray(String[]::new));
    }

    /** Whether {@code result} is git saying there was nothing to save. */
    static boolean nothingSaved(GitClient.Result result) {
        return result.exitCode() == 0 && result.output().contains("No local changes to save");
    }

    /**
     * Applies {@code stash} to the working tree, and keeps it. Where its changes do not fit what the
     * working tree has by now, git refuses, or leaves the conflicts in it and fails.
     */
    static GitClient.Result apply(Path root, Stash stash) {
        GitClient.Result moved = moved(root, stash);
        return moved != null ? moved : git(root, "stash", "apply", stash.name());
    }

    /** Drops {@code stash}: its changes are in no branch, and are lost with it. */
    static GitClient.Result drop(Path root, Stash stash) {
        GitClient.Result moved = moved(root, stash);
        return moved != null ? moved : git(root, "stash", "drop", stash.name());
    }

    /** The failure of a stash that is no longer where it was read, or {@code null} when it is. */
    private static GitClient.Result moved(Path root, Stash stash) {
        GitClient.Result at = git(root, "rev-parse", "--quiet", "--verify", stash.name() + "^{commit}");
        if (at.exitCode() == 0 && at.output().trim().equals(stash.id())) {
            return null;
        }
        return new GitClient.Result(1, NbBundle.getMessage(GitStashes.class, "Git.stash.outdated", stash.name()));
    }

    private static GitClient.Result git(Path root, String... args) {
        // What git says is matched and shown: in its own words, whatever the language of the machine.
        return GitClient.result(root, Map.of("LC_ALL", "C"), args);
    }
}
