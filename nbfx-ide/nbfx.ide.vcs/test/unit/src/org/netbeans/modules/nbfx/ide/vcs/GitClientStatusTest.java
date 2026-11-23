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
import java.util.Map;
import java.util.TreeMap;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Reads the status of a real repository through the {@code git} command line, in the three compare
 * modes. Skipped where git is not installed.
 */
public class GitClientStatusTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private Path root;

    @Before
    public void createRepository() throws Exception {
        Assume.assumeTrue("git is not installed", gitAvailable());
        root = temp.newFolder("repo").toPath();
        git("init", "--quiet");
        // The test must not depend on the machine's configuration.
        git("config", "user.name", "nbfx test");
        git("config", "user.email", "test@example.invalid");
        git("config", "commit.gpgsign", "false");
        git("config", "status.renames", "true");
        git("config", "core.autocrlf", "false");
        for (String name : List.of("modified.txt", "staged.txt", "both.txt", "renamed.txt", "deleted.txt",
                "removed.txt")) {
            write(name, "first line of " + name + "\nsecond line\nthird line\n");
        }
        git("add", "-A");
        git("commit", "--quiet", "-m", "initial");
    }

    @Test
    public void freshCommitIsClean() throws IOException {
        assertTrue(GitClient.status(root).isClean());
    }

    @Test
    public void tellsTheThreeCompareModesApart() throws Exception {
        write("modified.txt", "changed on disk\n");
        write("staged.txt", "changed and staged\n");
        git("add", "staged.txt");
        write("both.txt", "staged content\n");
        git("add", "both.txt");
        write("both.txt", "staged content\nand more on disk\n");
        git("mv", "renamed.txt", "moved.txt");
        Files.delete(root.resolve("deleted.txt"));
        git("rm", "--quiet", "removed.txt");
        write("new folder/untracked file.txt", "new\n");
        write("added.txt", "new and staged\n");
        git("add", "added.txt");

        VcsStatus status = GitClient.status(root);

        assertEquals(Map.of(
                "added.txt", VcsChangeKind.ADDED,
                "both.txt", VcsChangeKind.MODIFIED,
                "deleted.txt", VcsChangeKind.DELETED,
                "modified.txt", VcsChangeKind.MODIFIED,
                "moved.txt", VcsChangeKind.RENAMED,
                "new folder/untracked file.txt", VcsChangeKind.UNTRACKED,
                "removed.txt", VcsChangeKind.DELETED,
                "staged.txt", VcsChangeKind.MODIFIED),
                changes(status, VcsCompareMode.HEAD_VS_WORKING_TREE));
        assertEquals(Map.of(
                "added.txt", VcsChangeKind.ADDED,
                "both.txt", VcsChangeKind.MODIFIED,
                "moved.txt", VcsChangeKind.RENAMED,
                "removed.txt", VcsChangeKind.DELETED,
                "staged.txt", VcsChangeKind.MODIFIED),
                changes(status, VcsCompareMode.HEAD_VS_INDEX));
        assertEquals(Map.of(
                "both.txt", VcsChangeKind.MODIFIED,
                "deleted.txt", VcsChangeKind.DELETED,
                "modified.txt", VcsChangeKind.MODIFIED,
                "new folder/untracked file.txt", VcsChangeKind.UNTRACKED),
                changes(status, VcsCompareMode.INDEX_VS_WORKING_TREE));

        VcsFileStatus moved = status.files().stream()
                .filter(file -> file.path().equals("moved.txt")).findFirst().orElseThrow();
        assertEquals("renamed.txt", moved.originalPath());
    }

    @Test
    public void commitOfEveryChangeLeavesTheRepositoryClean() throws Exception {
        write("modified.txt", "changed on disk\n");
        write("untracked.txt", "new\n");

        assertEquals(0, GitClient.addAll(root).exitCode());
        assertEquals(Map.of("modified.txt", VcsChangeKind.MODIFIED, "untracked.txt", VcsChangeKind.ADDED),
                changes(GitClient.status(root), VcsCompareMode.HEAD_VS_INDEX));
        assertEquals(0, GitClient.commit(root, "second").exitCode());
        assertTrue(GitClient.status(root).isClean());
    }

    private static Map<String, VcsChangeKind> changes(VcsStatus status, VcsCompareMode mode) {
        Map<String, VcsChangeKind> changes = new TreeMap<>();
        for (VcsFileStatus file : status.files(mode)) {
            changes.put(file.path(), file.change(mode).orElseThrow());
        }
        return changes;
    }

    private void write(String name, String content) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void git(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(String.join(" ", command) + "\n" + output, 0, process.waitFor());
    }

    private static boolean gitAvailable() {
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
