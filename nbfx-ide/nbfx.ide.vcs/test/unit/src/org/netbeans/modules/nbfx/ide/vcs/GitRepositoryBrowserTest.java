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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.stage.Stage;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.modules.nbfx.ide.vcs.RepositoryTree.Kind;
import org.netbeans.modules.nbfx.ide.vcs.RepositoryTree.Row;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The Repository Browser over real repositories: what it shows of them, and that reading them again
 * keeps the rows as they were. Skipped where git is not installed.
 */
public class GitRepositoryBrowserTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private Path first;
    private Path second;

    @Before
    public void createRepositories() throws Exception {
        Assume.assumeTrue("git is not installed", GitRefsTest.gitAvailable());
        first = repository("first");
        second = repository("second");
    }

    @After
    public void hideWindows() throws InterruptedException {
        JavaFxTestSupport.hideWindows();
    }

    @Test
    public void showsTheRepositoriesAndWhatChangesInThem() throws Exception {
        String branch = GitRefsTest.git(first, "symbolic-ref", "--short", "HEAD").trim();
        GitRepositoryBrowser browser = JavaFxTestSupport.onFx(GitRepositoryBrowser::new);
        // No project is open here.
        assertTrue(JavaFxTestSupport.onFx(() -> repositories(browser).isEmpty() && !browser.tree().isVisible()));

        JavaFxTestSupport.onFx(() -> browser.setRepositories(List.of(first, second, first)));
        // There at once, by name; what is checked out follows when it is read.
        assertEquals(List.of("first", "second"), JavaFxTestSupport.onFx(() -> texts(browser.tree().getRoot())));
        await(() -> texts(browser.tree().getRoot()).equals(List.of("first [" + branch + "]", "second [" + branch + "]")));
        assertTrue(JavaFxTestSupport.onFx(() -> browser.tree().isVisible()));

        // Opened as the user would, and a branch selected.
        TreeItem<Row> local = JavaFxTestSupport.onFx(() -> {
            TreeItem<Row> repository = repositories(browser).get(0);
            assertFalse(repository.isExpanded());
            browser.select(first.resolve("a.txt"));
            assertTrue(repository.isExpanded());
            assertSame(repository, browser.tree().getSelectionModel().getSelectedItem());
            TreeItem<Row> branches = repository.getChildren().get(0);
            branches.setExpanded(true);
            TreeItem<Row> folder = branches.getChildren().get(0);
            folder.setExpanded(true);
            browser.tree().getSelectionModel().select(folder.getChildren().get(0));
            return folder;
        });
        assertEquals(List.of(Kind.ACTIVE_BRANCH), JavaFxTestSupport.onFx(() -> kinds(local)));
        // A branch has its actions; a row that only holds others has none.
        JavaFxTestSupport.onFx(() -> {
            assertEquals(6, browser.actions(local.getChildren().get(0)).size());
            assertTrue(browser.actions(local).isEmpty());
            assertEquals(12, browser.actions(repositories(browser).get(1)).size());
            assertTrue(browser.actions(null).isEmpty());
        });

        // Changed outside the IDE: read again, the rows that were there are the same, where they were.
        GitRefsTest.git(first, "checkout", "--quiet", "-b", "feature");
        GitRefsTest.git(first, "tag", "v1");
        JavaFxTestSupport.onFx(browser::reload);
        await(() -> texts(browser.tree().getRoot()).get(0).equals("first [feature]"));
        JavaFxTestSupport.onFx(() -> {
            TreeItem<Row> repository = repositories(browser).get(0);
            assertTrue(repository.isExpanded());
            assertSame(local, repository.getChildren().get(0).getChildren().get(0));
            assertTrue(local.isExpanded());
            assertEquals(List.of(Kind.ACTIVE_BRANCH, Kind.BRANCH), kinds(local));
            assertTrue(texts(local).toString(), texts(local).get(0).startsWith("feature - "));
            assertEquals(branch, selected(browser).key().substring("BRANCH:".length()));
            assertEquals(1, repository.getChildren().get(1).getChildren().size());
            assertEquals(12, browser.actions(repository).size());
            // The stashes have theirs, with none saved yet.
            assertEquals(1, browser.actions(repository.getChildren().get(2)).size());
            // And the remotes, with none added yet.
            assertEquals(1, browser.actions(repository.getChildren().get(3)).size());
            // The tag has its actions too.
            assertEquals(5, browser.actions(repository.getChildren().get(1).getChildren().get(0)).size());
        });

        // A repository that is no longer among them goes, the other stays as it is.
        JavaFxTestSupport.onFx(() -> browser.setRepositories(List.of(first)));
        JavaFxTestSupport.onFx(() -> {
            assertEquals(List.of("first [feature]"), texts(browser.tree().getRoot()));
            assertTrue(local.isExpanded());
        });
    }

    @Test
    public void aFolderThatIsNoRepositoryAnyMoreHoldsNothing() throws Exception {
        Path gone = temp.newFolder("gone").toPath();
        Assume.assumeTrue("the temporary folder is in a repository", GitMenus.workingTreeOf(gone.toFile()) == null);
        GitRepositoryBrowser browser = JavaFxTestSupport.onFx(GitRepositoryBrowser::new);

        JavaFxTestSupport.onFx(() -> browser.setRepositories(List.of(gone, second)));
        await(() -> texts(browser.tree().getRoot()).get(1).startsWith("second ["));

        JavaFxTestSupport.onFx(() -> {
            TreeItem<Row> row = repositories(browser).get(0);
            assertEquals("gone", row.getValue().text());
            assertTrue(row.isLeaf());
            assertTrue(row.getValue().tooltip(), row.getValue().tooltip().contains("not a git repository"));
        });
    }

    @Test
    public void aRepositoryClosedIsBackWhenItIsOpenedAgain() throws Exception {
        Path gone = temp.newFolder("unread").toPath();
        GitRepositoryBrowser browser = JavaFxTestSupport.onFx(GitRepositoryBrowser::new);
        JavaFxTestSupport.onFx(() -> browser.setRepositories(List.of(first, second, gone)));
        await(() -> texts(browser.tree().getRoot()).get(1).startsWith("second ["));

        JavaFxTestSupport.onFx(() -> {
            // Closed by its menu, also one that could not be read, which has nothing else in it.
            List<MenuItem> ofUnread = browser.actions(repositories(browser).get(2));
            assertEquals(List.of("Close Repository"), ofUnread.stream().map(MenuItem::getText).toList());
            ofUnread.get(0).fire();
            List<MenuItem> ofFirst = browser.actions(repositories(browser).get(0));
            assertEquals("Close Repository", ofFirst.get(ofFirst.size() - 1).getText());
            ofFirst.get(ofFirst.size() - 1).fire();
            assertEquals(1, repositories(browser).size());
            assertTrue(texts(browser.tree().getRoot()).get(0).startsWith("second ["));

            // Still closed while it stays among the repositories there are: as when another project opens.
            browser.setRepositories(List.of(first, second));
            assertEquals(1, repositories(browser).size());
            // Asked for, it is back, where it was.
            browser.select(first);
            assertEquals(List.of("first", "second"), texts(browser.tree().getRoot()).stream()
                    .map(text -> text.replaceAll(" \\[.*", "")).toList());
            assertSame(repositories(browser).get(0), browser.tree().getSelectionModel().getSelectedItem());

            // Closed again, and gone with its project: opened again, it is shown.
            browser.close(first);
            browser.setRepositories(List.of(second));
            browser.setRepositories(List.of(first, second));
            assertEquals(2, repositories(browser).size());

            browser.close(first);
            browser.close(second);
            assertFalse(browser.tree().isVisible());
        });
    }

    @Test
    public void aRowSaysNothingOfItselfWhileTheMenuIsOpen() throws Exception {
        GitRepositoryBrowser browser = JavaFxTestSupport.onFx(() -> {
            GitRepositoryBrowser created = new GitRepositoryBrowser();
            Stage stage = new Stage();
            stage.setScene(new Scene(created, 400, 300));
            stage.show();
            created.setRepositories(List.of(first));
            return created;
        });
        await(() -> texts(browser.tree().getRoot()).get(0).startsWith("first ["));
        await(() -> tooltip(browser) != null);
        assertEquals(first.toString(), JavaFxTestSupport.onFx(() -> tooltip(browser).getText()));

        JavaFxTestSupport.onFx(() -> browser.tree().getContextMenu().show(browser.tree(), 10, 10));
        await(() -> browser.tree().getContextMenu().isShowing());
        assertNull(JavaFxTestSupport.onFx(() -> tooltip(browser)));

        JavaFxTestSupport.onFx(() -> browser.tree().getContextMenu().hide());
        await(() -> tooltip(browser) != null);
    }

    /** The tooltip of the first row, as its cell has it. */
    private static Tooltip tooltip(GitRepositoryBrowser browser) {
        browser.applyCss();
        browser.layout();
        return browser.tree().lookupAll(".tree-cell").stream().map(TreeCell.class::cast)
                .filter(cell -> !cell.isEmpty() && cell.getIndex() == 0).findFirst().map(TreeCell::getTooltip).orElse(null);
    }

    private static List<TreeItem<Row>> repositories(GitRepositoryBrowser browser) {
        return browser.tree().getRoot().getChildren();
    }

    private static Row selected(GitRepositoryBrowser browser) {
        return browser.tree().getSelectionModel().getSelectedItem().getValue();
    }

    private static List<String> texts(TreeItem<Row> parent) {
        return parent.getChildren().stream().map(item -> item.getValue().text()).toList();
    }

    private static List<Kind> kinds(TreeItem<Row> parent) {
        return parent.getChildren().stream().map(item -> item.getValue().kind()).toList();
    }

    /** Waits for what the browser reads off the FX thread to arrive on it. */
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!JavaFxTestSupport.onFx(condition::getAsBoolean)) {
            assertTrue("the browser did not get there in time", System.nanoTime() < deadline);
            Thread.sleep(50);
        }
    }

    private Path repository(String name) throws Exception {
        Path root = temp.newFolder(name).toPath();
        GitRefsTest.git(root, "init", "--quiet");
        // The test must not depend on the machine's configuration.
        GitRefsTest.git(root, "config", "user.name", "nbfx test");
        GitRefsTest.git(root, "config", "user.email", "test@example.invalid");
        GitRefsTest.git(root, "config", "commit.gpgsign", "false");
        Files.writeString(root.resolve("a.txt"), "first\n", StandardCharsets.UTF_8);
        GitRefsTest.git(root, "add", "-A");
        GitRefsTest.git(root, "commit", "--quiet", "-m", "initial");
        return root;
    }
}
