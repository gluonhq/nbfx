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
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;

/**
 * A thin client over the {@code git} command line: repository detection, branch, status and commit.
 * Runs the command in {@code dir} and captures its merged output.
 *
 * @since 1.0
 */
final class GitClient {

    /** The exit code and merged output of a git command. */
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

    /** Stages every change in {@code dir} and under it. */
    static Result addAll(Path dir) {
        return result(dir, "add", "-A", "--", ".");
    }

    /**
     * Commits what is staged. In a folder below the repository root only the changes in that folder
     * are committed, as NetBeans commits a project that shares its repository with others; at the
     * root it is a plain commit, which is also the only one git accepts while merging.
     */
    static Result commit(Path dir, String message) {
        return isRoot(dir) ? result(dir, "commit", "-m", message)
                : result(dir, "commit", "-m", message, "--", ".");
    }

    private static boolean isRoot(Path dir) {
        try {
            return Files.isSameFile(dir, rootOf(dir));
        } catch (IOException ex) {
            return false;
        }
    }

    private static Result result(Path dir, String... args) {
        List<String> command = new ArrayList<>(args.length + 3);
        command.add("git");
        command.add("-C");
        command.add(dir.toString());
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            // Read as is: the status records end with a NUL and their paths may hold any other character.
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return new Result(process.waitFor(), output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(1, "Interrupted");
        } catch (IOException ex) {
            return new Result(1, ex.getMessage());
        }
    }
}
