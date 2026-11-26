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

import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.openide.util.NbBundle;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;

/**
 * A thin client over the {@code git} command line: repository detection, branch, status and commit.
 * Runs the command in {@code dir} and captures its output apart from what it says beside it - the
 * warnings of a hook, a helper or the configuration - which is no part of what is read.
 *
 * @since 1.0
 */
final class GitClient {

    /**
     * The exit code of a git command and what it wrote: its standard output when it succeeded, and
     * what it said of its failure - its standard error, or its output when that is empty - when not.
     */
    record Result(int exitCode, String output) {
    }

    private GitClient() {
    }

    static boolean isRepository(Path dir) {
        return result(dir, "rev-parse", "--is-inside-work-tree").exitCode() == 0;
    }

    static Path rootOf(Path dir) {
        Result result = result(dir, "rev-parse", "--show-toplevel");
        return result.exitCode() == 0 ? Path.of(result.output().trim()) : dir;
    }

    static String branch(Path root) {
        Result result = result(root, "rev-parse", "--abbrev-ref", "HEAD");
        return result.exitCode() == 0 ? result.output().trim() : "";
    }

    /**
     * The changed files in {@code dir} and under it, with the index and the working tree told apart, so
     * that one read serves every {@link org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode}. Their
     * paths are relative to the repository root, whatever {@code dir} is. Untracked files are listed
     * one by one, not by folder.
     */
    static VcsStatus status(Path dir) throws IOException {
        Result result = result(dir, "status", "--porcelain=v2", "-z", "--untracked-files=all", "--", ".");
        if (result.exitCode() != 0) {
            throw new IOException(result.output());
        }
        try {
            return GitStatusParser.parse(result.output());
        } catch (IllegalArgumentException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    /** Creates a repository in {@code dir}, as {@code git init}. */
    static Result init(Path dir) {
        return result(dir, "init");
    }

    /**
     * Commits every change in {@code dir} and under it as the working tree has it, the untracked files
     * as well: what the index held of them before does not matter. A change staged and undone on disk
     * is therefore no change, and nothing is committed where no other is left.
     *
     * <p>Nothing is staged or committed while a file in {@code dir} is conflicted: staging it would
     * tell git the conflict is resolved, with the conflict markers as its content.
     *
     * @return the result of the command that failed, or of the commit
     */
    static Result commitAll(Path dir, String message) {
        try {
            if (status(dir).hasConflicts()) {
                return new Result(1, NbBundle.getMessage(GitClient.class, "Git.commit.conflicts"));
            }
        } catch (IOException ex) {
            return new Result(1, ex.getMessage());
        }
        Result add = addAll(dir);
        if (add.exitCode() != 0) {
            return add;
        }
        // A merge is concluded by its commit, even one that changes nothing here.
        if (!isMerging(dir) && result(dir, "diff", "--cached", "--quiet", "--", ".").exitCode() == 0) {
            return new Result(0, "");
        }
        return commit(dir, message);
    }

    /** Stages every change in {@code dir} and under it. */
    static Result addAll(Path dir) {
        return result(dir, "add", "-A", "--", ".");
    }

    /**
     * Commits what {@link #addAll(Path)} staged. In a folder below the repository root only the changes in that folder
     * are committed, as NetBeans commits a project that shares its repository with others. At the
     * root it is a plain commit, and so it is while merging, wherever {@code dir} is: git concludes
     * a merge as a whole or not at all, and refuses the commit of a part of it.
     */
    static Result commit(Path dir, String message) {
        return isRoot(dir) || isMerging(dir) ? result(dir, "commit", "-m", message)
                : result(dir, "commit", "-m", message, "--", ".");
    }

    /** Whether a merge was started in the repository of {@code dir} and is not yet committed. */
    static boolean isMerging(Path dir) {
        return result(dir, "rev-parse", "--quiet", "--verify", "MERGE_HEAD").exitCode() == 0;
    }

    private static boolean isRoot(Path dir) {
        try {
            return Files.isSameFile(dir, rootOf(dir));
        } catch (IOException ex) {
            return false;
        }
    }

    private static Result result(Path dir, String... args) {
        return result(dir, Map.of(), args);
    }

    /** As {@link #result(Path, String...)}, with {@code environment} added to that of the command. */
    static Result result(Path dir, Map<String, String> environment, String... args) {
        List<String> command = new ArrayList<>(args.length + 3);
        command.add("git");
        command.add("-C");
        command.add(dir.toString());
        command.addAll(List.of(args));
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(environment);
            Process process = builder.start();
            // Read while the output is: a command that fills one stream waits for it to be read.
            CompletableFuture<String> error = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            // Read as is: the status records end with a NUL and their paths may hold any other character.
            String output = read(process.getInputStream());
            int exitCode = process.waitFor();
            String said = error.join();
            return new Result(exitCode, exitCode == 0 || said.isBlank() ? output : said);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(1, "Interrupted");
        } catch (IOException ex) {
            return new Result(1, ex.getMessage());
        }
    }

    private static String read(InputStream stream) {
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
