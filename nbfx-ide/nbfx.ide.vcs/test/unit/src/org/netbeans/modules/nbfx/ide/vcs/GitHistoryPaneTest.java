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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.After;
import org.junit.Test;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Change;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Commit;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Criteria;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.CommitRow;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.Field;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.FileRow;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.MoreRow;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.Row;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the History summary on the headless JavaFX toolkit, over a stand-in for git: what it
 * searches with, what it shows of what is found, and what Show All Files, the filter and Show more
 * do.
 */
public class GitHistoryPaneTest {

    private static final Change APP = new Change(VcsChangeKind.MODIFIED, "src/app/App.java", null);
    private static final Change README = new Change(VcsChangeKind.ADDED, "README.md", null);
    private static final Commit NEWEST = commit("3333333ccc", "Grace Hopper", "Tidy up\n\nNothing else.", APP);
    private static final Commit MIDDLE = commit("2222222bbb", "Ada Lovelace", "Add the guide", APP);
    private static final Commit OLDEST = commit("1111111aaa", "Ada Lovelace", "first", APP);

    /** What the stand-in was asked: the criteria of each search, with what was skipped. */
    private final List<String> asked = new CopyOnWriteArrayList<>();
    private volatile String failure;

    @After
    public void hideWindows() throws InterruptedException {
        JavaFxTestSupport.hideWindows();
    }

    @Test
    public void showsWhatTheSearchFindsAndOpensACommitToItsFiles() throws Exception {
        GitHistoryPane pane = show(List.of("src"));
        await(() -> pane.rows().size() == 3);

        assertEquals(List.of(new CommitRow(NEWEST, false), new CommitRow(MIDDLE, false), new MoreRow()),
                JavaFxTestSupport.onFx(() -> List.copyOf(pane.rows())));
        // The first search is for the paths the pane was made for, with the usual limit.
        assertEquals(List.of(new Criteria("", "", "", "", "", 50, true, List.of("src")) + " skip 0"), asked);

        JavaFxTestSupport.onFx(() -> {
            pane.list().getSelectionModel().select(1);
            pane.toggle(MIDDLE);
            assertEquals(List.of(new CommitRow(NEWEST, false), new CommitRow(MIDDLE, true), new FileRow(MIDDLE, APP),
                    new MoreRow()), List.copyOf(pane.rows()));
            // The commit that was selected still is, opened.
            assertEquals(new CommitRow(MIDDLE, true), pane.list().getSelectionModel().getSelectedItem());
            pane.toggle(MIDDLE);
            assertEquals(3, pane.rows().size());
        });
    }

    @Test
    public void showMoreAddsTheNextCommitsOfTheSearch() throws Exception {
        GitHistoryPane pane = show(List.of("src"));
        await(() -> pane.rows().size() == 3);

        JavaFxTestSupport.onFx(pane::showMore);
        await(() -> pane.rows().size() == 3 && !(pane.rows().get(2) instanceof MoreRow));

        assertEquals(List.of(new CommitRow(NEWEST, false), new CommitRow(MIDDLE, false), new CommitRow(OLDEST, false)),
                JavaFxTestSupport.onFx(() -> List.copyOf(pane.rows())));
        assertTrue(asked.toString(), asked.get(1).endsWith(" skip 2"));
        // Nothing more to ask for.
        JavaFxTestSupport.onFx(pane::showMore);
        Thread.sleep(200);
        assertEquals(2, asked.size());
    }

    @Test
    public void searchOptionsAreWhatIsSearchedWith() throws Exception {
        GitHistoryPane pane = show(List.of());
        await(() -> pane.rows().size() == 3);

        JavaFxTestSupport.onFx(() -> {
            pane.option("message").setText("guide");
            pane.option("author").setText("ada");
            pane.option("branch").setText("main");
            pane.option("from").setText("2026-03-01");
            pane.option("to").setText("v1");
            pane.option("limit").setText("many");
            pane.includeMerges().setSelected(false);
            pane.toggle(NEWEST);
            pane.search();
            // What was shown goes while the new search runs.
            assertTrue(pane.rows().isEmpty());
            assertEquals("Searching...", pane.placeholderText());
            // A limit that is no number is the usual one, and says so.
            assertEquals("50", pane.option("limit").getText());
        });
        await(() -> pane.rows().size() == 3);

        assertEquals(new Criteria("main", "guide", "ada", "2026-03-01", "v1", 50, false, List.of()) + " skip 0",
                asked.get(1));
        // A new search starts with every commit closed.
        assertEquals(new CommitRow(NEWEST, false), JavaFxTestSupport.onFx(() -> pane.rows().get(0)));
        // The whole repository has no other files to show.
        assertTrue(JavaFxTestSupport.onFx(() -> pane.showAll().isDisabled()));
    }

    @Test
    public void showAllFilesReadsTheOtherFilesOfAnOpenedCommit() throws Exception {
        GitHistoryPane pane = show(List.of("src"));
        await(() -> pane.rows().size() == 3);
        assertFalse(JavaFxTestSupport.onFx(() -> pane.showAll().isDisabled()));

        JavaFxTestSupport.onFx(() -> {
            pane.toggle(NEWEST);
            pane.showAll().setSelected(true);
        });
        await(() -> pane.rows().size() == 5);

        assertEquals(List.of(new CommitRow(NEWEST, true), new FileRow(NEWEST, README), new FileRow(NEWEST, APP),
                new CommitRow(MIDDLE, false), new MoreRow()), JavaFxTestSupport.onFx(() -> List.copyOf(pane.rows())));
        assertEquals("changes " + NEWEST.id(), asked.get(1));

        // Read once: opened again, and another one opened, each asks once.
        JavaFxTestSupport.onFx(() -> {
            pane.toggle(NEWEST);
            pane.toggle(NEWEST);
            pane.toggle(MIDDLE);
        });
        await(() -> pane.rows().size() == 7);
        assertEquals(3, asked.size());

        JavaFxTestSupport.onFx(() -> pane.showAll().setSelected(false));
        assertEquals(5, (int) JavaFxTestSupport.onFx(() -> pane.rows().size()));
    }

    @Test
    public void theFilterLeavesTheCommitsThatHaveItsText() throws Exception {
        GitHistoryPane pane = show(List.of("src"));
        await(() -> pane.rows().size() == 3);

        JavaFxTestSupport.onFx(() -> {
            pane.filterText().setText("grace");
            assertEquals(List.of(new CommitRow(NEWEST, false), new MoreRow()), List.copyOf(pane.rows()));
            pane.filterField().setValue(Field.MESSAGE);
            // None left: said so, and not with a lone row to ask for more.
            assertTrue(pane.rows().isEmpty());
            assertEquals("No commit among those found has that text", pane.placeholderText());
            pane.filterText().setText("");
            assertEquals(3, pane.rows().size());
        });
    }

    @Test
    public void aSearchThatFailsSaysWhatGitSaid() throws Exception {
        failure = "fatal: bad revision 'nowhere'";
        GitHistoryPane pane = show(List.of("src"));
        await(() -> "fatal: bad revision 'nowhere'".equals(pane.placeholderText()));
        assertTrue(JavaFxTestSupport.onFx(() -> pane.rows().isEmpty()));

        failure = null;
        JavaFxTestSupport.onFx(pane::search);
        await(() -> pane.rows().size() == 3);
    }

    @Test
    public void aCommitIsToldByWhoMadeItAndAChangeByItsLetter() {
        assertTrue(GitHistoryPane.who(NEWEST), GitHistoryPane.who(NEWEST)
                .startsWith("Grace Hopper <grace.hopper@example.invalid>  "));
        assertEquals("A", GitHistoryPane.letter(VcsChangeKind.ADDED));
        assertEquals("M", GitHistoryPane.letter(VcsChangeKind.MODIFIED));
        assertEquals("D", GitHistoryPane.letter(VcsChangeKind.DELETED));
        assertEquals("R", GitHistoryPane.letter(VcsChangeKind.RENAMED));
        assertEquals("C", GitHistoryPane.letter(VcsChangeKind.COPIED));
        assertEquals("T", GitHistoryPane.letter(VcsChangeKind.TYPE_CHANGED));
    }

    /** A pane over a history of three commits, found two at a time. */
    private GitHistoryPane show(List<String> paths) throws InterruptedException {
        GitHistoryPane.Reader reader = new GitHistoryPane.Reader() {
            @Override
            public GitHistory read(Criteria criteria, int skip) throws IOException {
                asked.add(criteria + " skip " + skip);
                if (failure != null) {
                    throw new IOException(failure);
                }
                return skip == 0 ? new GitHistory(List.of(NEWEST, MIDDLE), true)
                        : new GitHistory(List.of(OLDEST), false);
            }

            @Override
            public List<Change> changes(String id) {
                asked.add("changes " + id);
                return List.of(README, APP);
            }
        };
        return JavaFxTestSupport.onFx(() -> {
            GitHistoryPane pane = new GitHistoryPane(reader, paths);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 900, 600));
            stage.show();
            return pane;
        });
    }

    private static void await(BooleanSupplier reached) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            if (JavaFxTestSupport.onFx(reached::getAsBoolean)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("not reached");
    }

    private static Commit commit(String id, String author, String message, Change... changes) {
        String email = author.toLowerCase().replace(' ', '.') + "@example.invalid";
        return new Commit(id, new ArrayList<>(), author, email, Instant.ofEpochSecond(1_770_000_000L), message,
                List.of(changes));
    }
}
