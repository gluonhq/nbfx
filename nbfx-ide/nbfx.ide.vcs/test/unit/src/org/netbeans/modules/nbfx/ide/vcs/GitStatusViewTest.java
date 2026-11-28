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
import java.util.function.BooleanSupplier;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Git view over real repositories: what it shows of the folder it is opened for, that it moves to
 * another one without the rows of the last, and that it offers no commit over a conflict. Skipped
 * where git is not installed.
 */
public class GitStatusViewTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private Path first;
    private Path second;

    @Before
    public void createRepositories() throws Exception {
        Assume.assumeTrue("git is not installed", gitAvailable());
        first = repository("first");
        second = repository("second");
    }

    @After
    public void hideWindows() throws InterruptedException {
        JavaFxTestSupport.hideWindows();
    }

    @Test
    public void showsTheFolderItIsOpenedForAndThenAnother() throws Exception {
        write(first, "a.txt", "changed\n");
        write(second, "new.txt", "new\n");
        write(second, "b.txt", "changed\n");
        GitStatusView view = JavaFxTestSupport.onFx(GitStatusView::new);

        JavaFxTestSupport.onFx(() -> view.show(first, "First"));
        await(() -> rows(view).equals(List.of("a.txt")));
        assertTrue(title(view), title(view).startsWith("Git - First [first] - "));
        assertFalse(commit(view).isDisabled());

        // Asked for several times in a row, and for another folder: what is shown is the last one.
        JavaFxTestSupport.onFx(() -> {
            view.reload();
            view.show(second, null);
            // The rows of the first folder are no changes of this one, and nothing of them is committed.
            assertTrue(rows(view).isEmpty());
            assertTrue(commit(view).isDisabled());
            view.reload();
        });
        await(() -> rows(view).equals(List.of("b.txt", "new.txt")));
        assertTrue(title(view), title(view).startsWith("Git - second [second] - "));
        assertFalse(commit(view).isDisabled());
    }

    @Test
    public void offersNoCommitWhileAFileIsConflicted() throws Exception {
        git(first, "checkout", "--quiet", "-b", "feature");
        write(first, "a.txt", "theirs\n");
        git(first, "commit", "--quiet", "-am", "theirs");
        git(first, "checkout", "--quiet", "-");
        write(first, "a.txt", "ours\n");
        git(first, "commit", "--quiet", "-am", "ours");
        assertTrue(GitClient.result(first, java.util.Map.of(), "merge", "--quiet", "feature").exitCode() != 0);
        GitStatusView view = JavaFxTestSupport.onFx(GitStatusView::new);

        JavaFxTestSupport.onFx(() -> view.show(first, "First"));
        await(() -> rows(view).equals(List.of("a.txt")));

        assertTrue("staging the file would call its conflict resolved", commit(view).isDisabled());
        assertEquals("Resolve the conflicted files before committing", commit(view).getAccessibleText());

        // Resolved and told so to git: there is a merge to commit.
        write(first, "a.txt", "ours and theirs\n");
        git(first, "add", "a.txt");
        JavaFxTestSupport.onFx(view::reload);
        await(() -> !commit(view).isDisabled());
        assertEquals("Commit All", commit(view).getAccessibleText());
    }

    private static String title(GitStatusView view) throws InterruptedException {
        return JavaFxTestSupport.onFx(() -> view.titleProperty().get());
    }

    private static ButtonBase commit(GitStatusView view) {
        GitStatusPane pane = (GitStatusPane) view.getCenter();
        return ((ToolBar) pane.getTop()).getItems().stream()
                .filter(ButtonBase.class::isInstance).map(ButtonBase.class::cast).toList().get(6);
    }

    /** The names of the file rows, in the order of the table. */
    private static List<String> rows(GitStatusView view) {
        List<String> names = new ArrayList<>();
        collect(((GitStatusPane) view.getCenter()).table().getRoot(), names);
        return names;
    }

    private static void collect(TreeItem<StatusRow> parent, List<String> names) {
        for (TreeItem<StatusRow> item : parent.getChildren()) {
            if (item.getValue() instanceof StatusRow.File file) {
                names.add(file.name());
            }
            collect(item, names);
        }
    }

    /** Waits for what the view reads off the FX thread to arrive on it. */
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!JavaFxTestSupport.onFx(condition::getAsBoolean)) {
            assertTrue("the view did not get there in time", System.nanoTime() < deadline);
            Thread.sleep(50);
        }
    }

    private Path repository(String name) throws Exception {
        Path root = temp.newFolder(name).toPath();
        git(root, "init", "--quiet");
        // The test must not depend on the machine's configuration.
        git(root, "config", "user.name", "nbfx test");
        git(root, "config", "user.email", "test@example.invalid");
        git(root, "config", "commit.gpgsign", "false");
        git(root, "config", "core.autocrlf", "false");
        write(root, "a.txt", "first\n");
        write(root, "b.txt", "first\n");
        git(root, "add", "-A");
        git(root, "commit", "--quiet", "-m", "initial");
        return root;
    }

    private static void write(Path root, String name, String content) throws IOException {
        Files.writeString(root.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static void git(Path root, String... args) throws Exception {
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
