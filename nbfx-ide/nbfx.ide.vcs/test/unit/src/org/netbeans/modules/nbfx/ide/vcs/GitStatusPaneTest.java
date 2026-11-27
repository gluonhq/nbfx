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

import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.TextField;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ContextMenu;
import javafx.event.Event;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.stage.Stage;
import org.junit.After;
import org.junit.Test;
import org.netbeans.modules.nbfx.ide.vcs.GitStatusPane.Flavour;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsFileStatus;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the Versioning pane on the headless JavaFX toolkit: the table follows the compare mode and
 * the tree or list view, the tool bar drives both, and a refresh keeps what the user arranged.
 */
public class GitStatusPaneTest {

    private static final Path ROOT = Path.of("repo").toAbsolutePath();
    private static final VcsStatus STATUS = new VcsStatus(List.of(
            new VcsFileStatus("README.md", null, VcsChangeKind.MODIFIED, null, false),
            new VcsFileStatus("src/app/App.java", null, null, VcsChangeKind.MODIFIED, false),
            new VcsFileStatus("src/app/New.java", null, null, VcsChangeKind.UNTRACKED, false)));

    @After
    public void hideWindows() throws InterruptedException {
        JavaFxTestSupport.hideWindows();
    }

    @Test
    public void tableFollowsTheCompareModeAndTheFlavour() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            GitStatusPane pane = new GitStatusPane(VcsCompareMode.HEAD_VS_WORKING_TREE, Flavour.TREE);
            pane.setStatus(ROOT, STATUS);
            assertEquals(List.of("src/app/", "  App.java|Modified|src/app/App.java",
                    "  New.java|Untracked|src/app/New.java", "README.md|Modified|README.md"), rows(pane));

            pane.modeProperty().set(VcsCompareMode.HEAD_VS_INDEX);
            assertEquals(List.of("README.md|Modified|README.md"), rows(pane));

            pane.modeProperty().set(VcsCompareMode.INDEX_VS_WORKING_TREE);
            pane.flavourProperty().set(Flavour.LIST);
            assertEquals(List.of("App.java|Modified|src/app/App.java", "New.java|Untracked|src/app/New.java"),
                    rows(pane));
        });
    }

    @Test
    public void toolBarSwitchesModeAndFlavourAndRunsTheActions() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            GitStatusPane pane = new GitStatusPane(VcsCompareMode.HEAD_VS_INDEX, Flavour.LIST);
            AtomicInteger refreshed = new AtomicInteger();
            AtomicInteger committed = new AtomicInteger();
            pane.setOnRefresh(refreshed::incrementAndGet);
            pane.setOnCommit(committed::incrementAndGet);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 600, 300));
            stage.show();

            List<ButtonBase> buttons = buttons(pane);
            assertEquals(7, buttons.size());
            // The stored mode and flavour come back selected.
            assertEquals(List.of(false, true, false, true, false), buttons.subList(0, 5).stream()
                    .map(button -> ((ToggleButton) button).isSelected()).toList());

            buttons.get(2).fire();
            assertSame(VcsCompareMode.INDEX_VS_WORKING_TREE, pane.modeProperty().get());
            // A second click does not leave the group without a mode.
            buttons.get(2).fire();
            assertTrue(((ToggleButton) buttons.get(2)).isSelected());
            assertSame(VcsCompareMode.INDEX_VS_WORKING_TREE, pane.modeProperty().get());

            buttons.get(4).fire();
            assertSame(Flavour.TREE, pane.flavourProperty().get());

            buttons.get(5).fire();
            assertEquals(1, refreshed.get());
            assertTrue("no commit without changes", buttons.get(6).isDisabled());
            pane.setCommitDisabled(false);
            buttons.get(6).fire();
            assertEquals(1, committed.get());
        });
    }

    @Test
    public void refreshKeepsCollapsedFoldersAndTheSameStatusKeepsTheRows() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            GitStatusPane pane = new GitStatusPane(VcsCompareMode.HEAD_VS_WORKING_TREE, Flavour.TREE);
            pane.setStatus(ROOT, STATUS);
            TreeItem<StatusRow> folder = pane.table().getRoot().getChildren().get(0);
            folder.setExpanded(false);

            // The same status again: the very same rows stay.
            pane.setStatus(ROOT, new VcsStatus(STATUS.files()));
            assertSame(folder, pane.table().getRoot().getChildren().get(0));

            // One file less: new rows, the folder still collapsed.
            pane.setStatus(ROOT, new VcsStatus(STATUS.files().subList(0, 2)));
            TreeItem<StatusRow> rebuilt = pane.table().getRoot().getChildren().get(0);
            assertEquals("src/app", rebuilt.getValue().path());
            assertFalse(rebuilt.isExpanded());
            assertEquals(1, rebuilt.getChildren().size());
        });
    }

    @Test
    public void messageReplacesTheTable() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            GitStatusPane pane = new GitStatusPane(VcsCompareMode.HEAD_VS_WORKING_TREE, Flavour.TREE);
            assertEquals("No changes between HEAD and the working tree",
                    ((Label) pane.table().getPlaceholder()).getText());
            pane.setStatus(ROOT, STATUS);

            pane.setMessage("Not a Git repository");
            assertTrue(rows(pane).isEmpty());
            assertEquals("Not a Git repository", ((Label) pane.table().getPlaceholder()).getText());

            // The status that was shown before the message is shown again.
            pane.setStatus(ROOT, STATUS);
            assertEquals(4, rows(pane).size());
            // What a mode without changes reads is of that mode: the others may hold some.
            pane.modeProperty().set(VcsCompareMode.HEAD_VS_INDEX);
            assertEquals("No staged changes", ((Label) pane.table().getPlaceholder()).getText());
            pane.modeProperty().set(VcsCompareMode.INDEX_VS_WORKING_TREE);
            assertEquals("No changes that are not staged", ((Label) pane.table().getPlaceholder()).getText());
        });
    }

    @Test
    public void columnsAreConfigurableAndRestored() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            GitStatusPane pane = new GitStatusPane(VcsCompareMode.HEAD_VS_WORKING_TREE, Flavour.TREE);
            assertTrue("the header has the column menu", pane.table().isTableMenuButtonVisible());
            assertEquals(List.of("file", "status", "path"),
                    pane.columns().stream().map(GitStatusPane.ColumnState::id).toList());
            assertTrue("File is always shown", pane.table().getColumns().get(0).visibleProperty().isBound());

            // A saved layout: Path before Status, Status hidden; an unknown column is skipped.
            pane.restoreColumns(List.of(new GitStatusPane.ColumnState("file", 300, false),
                    new GitStatusPane.ColumnState("gone", 10, true),
                    new GitStatusPane.ColumnState("path", 200, true),
                    new GitStatusPane.ColumnState("status", 90, false)));
            assertEquals(List.of("file", "path", "status"),
                    pane.columns().stream().map(GitStatusPane.ColumnState::id).toList());
            assertEquals(List.of(true, true, false),
                    pane.columns().stream().map(GitStatusPane.ColumnState::visible).toList());
            assertEquals(90, pane.table().getColumns().get(2).getPrefWidth(), 0);
            assertSame("File stays the tree column", pane.table().getColumns().get(0), pane.table().getTreeColumn());
        });
    }

    @Test
    public void contextMenuOffersOpenDiffRefreshAndCommitAll() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            GitStatusPane pane = new GitStatusPane(VcsCompareMode.HEAD_VS_WORKING_TREE, Flavour.TREE);
            AtomicInteger committed = new AtomicInteger();
            AtomicInteger refreshed = new AtomicInteger();
            pane.setOnCommit(committed::incrementAndGet);
            pane.setOnRefresh(refreshed::incrementAndGet);
            pane.setStatus(ROOT, STATUS);
            ContextMenu menu = pane.table().getContextMenu();
            // The commit is of every change, not of the row the menu opened on, and says so.
            assertEquals(List.of("Open", "Diff", "", "Refresh", "Commit All..."), menu.getItems().stream()
                    .map(item -> item instanceof SeparatorMenuItem ? "" : item.getText()).toList());
            MenuItem open = menu.getItems().get(0);
            MenuItem diff = menu.getItems().get(1);
            MenuItem commit = menu.getItems().get(4);
            menu.getItems().get(3).fire();
            assertEquals(1, refreshed.get());

            // A folder has nothing to open or compare; a file that is not on disk has nothing to
            // open, but its diff shows what is gone.
            pane.table().getSelectionModel().select(0);
            menu.getOnShowing().handle(null);
            assertTrue(open.isDisable());
            assertTrue(diff.isDisable());
            pane.table().getSelectionModel().select(1);
            menu.getOnShowing().handle(null);
            assertTrue(open.isDisable());
            assertFalse(diff.isDisable());

            // Commit follows the tool bar button.
            assertTrue(commit.isDisable());
            pane.setCommitDisabled(false);
            assertFalse(commit.isDisable());
            commit.fire();
            assertEquals(1, committed.get());
        });
    }

    @Test
    public void toolBarIsReachedAndUsedWithTheKeyboard() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            GitStatusPane pane = new GitStatusPane(VcsCompareMode.HEAD_VS_WORKING_TREE, Flavour.TREE);
            AtomicInteger refreshed = new AtomicInteger();
            pane.setOnRefresh(refreshed::incrementAndGet);
            TextField elsewhere = new TextField();
            Stage stage = new Stage();
            stage.setScene(new Scene(new VBox(elsewhere, pane), 600, 300));
            stage.show();
            elsewhere.requestFocus();

            // Every button is a stop of the focus traversal and tells assistive technology what it is.
            List<ButtonBase> buttons = buttons(pane);
            for (ButtonBase button : buttons) {
                assertTrue(button.getAccessibleText(), button.isFocusTraversable());
                assertFalse(button.getAccessibleText().isBlank());
            }
            // With the focus, the key that presses a button runs it; the focus stays where it was put.
            ButtonBase refresh = buttons.get(5);
            refresh.requestFocus();
            assertTrue(refresh.isFocused());
            Event.fireEvent(refresh, new KeyEvent(KeyEvent.KEY_PRESSED, " ", " ", KeyCode.SPACE,
                    false, false, false, false));
            Event.fireEvent(refresh, new KeyEvent(KeyEvent.KEY_RELEASED, " ", " ", KeyCode.SPACE,
                    false, false, false, false));
            assertEquals(1, refreshed.get());
            assertTrue(refresh.isFocused());
            assertTrue("the focus is in the view", pane.isFocusWithin());
        });
    }

    @Test
    public void commitTellsWhyThereIsNothingToCommit() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            GitStatusPane pane = new GitStatusPane(VcsCompareMode.HEAD_VS_WORKING_TREE, Flavour.TREE);
            ButtonBase commit = buttons(pane).get(6);
            assertEquals("Commit All", commit.getAccessibleText());

            pane.setCommitDisabled(true, "Resolve the conflicted files before committing");
            assertTrue(commit.isDisabled());
            assertEquals("Resolve the conflicted files before committing", commit.getAccessibleText());
            assertEquals("Resolve the conflicted files before committing", commit.getTooltip().getText());
            Label notice = (Label) ((ToolBar) pane.getTop()).getItems().stream()
                    .filter(item -> item.getStyleClass().contains("git-notice")).findFirst().orElseThrow();
            assertTrue(notice.isVisible());
            assertEquals("Resolve the conflicted files before committing", notice.getText());

            pane.setCommitDisabled(false, null);
            assertFalse(commit.isDisabled());
            assertEquals("Commit All", commit.getAccessibleText());
            assertFalse(notice.isVisible());
        });
    }

    @Test
    public void aRenamedFileTellsWhereItComesFrom() {
        VcsFileStatus moved = new VcsFileStatus("src/New.java", "src/Old.java", VcsChangeKind.RENAMED, null, false);
        assertEquals("src/Old.java \u2192 src/New.java", GitStatusPane.movedFrom(
                new StatusRow.File("New.java", "src/New.java", moved, VcsChangeKind.RENAMED)));
        // Renamed and then deleted, it is a deleted file where it was: there is no path it moved to.
        VcsFileStatus gone = new VcsFileStatus("src/New.java", "src/Old.java", VcsChangeKind.RENAMED,
                VcsChangeKind.DELETED, false);
        assertNull(GitStatusPane.movedFrom(
                new StatusRow.File("Old.java", "src/Old.java", gone, VcsChangeKind.DELETED)));
        assertNull(GitStatusPane.movedFrom(new StatusRow.Folder("src", "src")));
    }

    /** The rows as "name|status|path", indented by their depth; a folder ends with a slash. */
    private static List<String> rows(GitStatusPane pane) {
        List<String> rows = new ArrayList<>();
        collect(pane, pane.table().getRoot(), "", rows);
        return rows;
    }

    private static void collect(GitStatusPane pane, TreeItem<StatusRow> parent, String indent, List<String> rows) {
        for (TreeItem<StatusRow> item : parent.getChildren()) {
            StatusRow row = item.getValue();
            if (row instanceof StatusRow.Folder) {
                rows.add(indent + row.name() + "/");
            } else {
                Object status = pane.table().getColumns().get(1).getCellObservableValue(item).getValue();
                Object path = pane.table().getColumns().get(2).getCellObservableValue(item).getValue();
                rows.add(indent + row.name() + "|" + status + "|" + path);
            }
            collect(pane, item, indent + "  ", rows);
        }
    }

    private static List<ButtonBase> buttons(GitStatusPane pane) {
        List<ButtonBase> buttons = new ArrayList<>();
        for (Node node : ((ToolBar) pane.getTop()).getItems()) {
            if (node instanceof ButtonBase button) {
                buttons.add(button);
            }
        }
        return buttons;
    }
}
