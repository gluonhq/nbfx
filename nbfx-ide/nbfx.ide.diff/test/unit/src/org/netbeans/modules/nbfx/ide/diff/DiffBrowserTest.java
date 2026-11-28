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
package org.netbeans.modules.nbfx.ide.diff;

import javafx.stage.Stage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.event.Event;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import org.junit.Test;
import org.netbeans.api.nbfx.editor.SavedDocument;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Verifies what the diff browser makes of a provider on the headless JavaFX toolkit: the files it
 * lists, the diff of the one selected, and what choosing a way to compare and Refresh ask for.
 */
public class DiffBrowserTest {

    private static final DiffMode ALL = new DiffMode("all", "Everything", Region::new);
    private static final DiffMode STAGED = new DiffMode("staged", "Staged", Region::new);

    /** Two files in the first mode, the second of them alone in the other; counts what it is asked. */
    private static final class Files implements DiffProvider {

        final List<String> asked = new CopyOnWriteArrayList<>();
        volatile boolean listed = true;
        volatile boolean empty;
        volatile String secondText = "a\nx\nc\ny\n";
        /** The modes the user chose, as the provider was told; and whether it was let go of. */
        final List<String> chosen = new CopyOnWriteArrayList<>();
        volatile int disposed;
        /** Whether the provider fails at what it is asked on the FX thread. */
        volatile boolean failing;
        /** Held up while set: the list of the files, and the reading of the texts of one. */
        volatile CountDownLatch listHeld;
        volatile CountDownLatch loadHeld;
        final AtomicInteger loads = new AtomicInteger();

        private static void hold(CountDownLatch latch) {
            try {
                if (latch != null && !latch.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("never let go");
                }
            } catch (InterruptedException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private DiffEntry held(DiffEntry entry) {
            return new DiffEntry(entry.name(), entry.location(), entry.status(), entry.kind(), entry.icon(), () -> {
                hold(loadHeld);
                try {
                    return entry.loader().load();
                } finally {
                    loads.incrementAndGet();
                }
            });
        }

        @Override
        public void modeChosen(DiffMode mode) {
            chosen.add(mode.id());
        }

        @Override
        public void dispose() {
            disposed++;
        }

        @Override
        public List<DiffMode> modes() {
            if (failing) {
                throw new IllegalStateException("no modes");
            }
            return List.of(ALL, STAGED);
        }

        @Override
        public boolean listed() {
            return listed;
        }

        @Override
        public SavedDocument savedState(DiffMode mode) {
            if (failing) {
                throw new IllegalStateException("no state");
            }
            return new SavedDocument("test-diff", mode.id());
        }

        @Override
        public List<DiffEntry> entries(DiffMode mode) {
            asked.add(mode.id());
            hold(listHeld);
            List<DiffEntry> entries = new ArrayList<>();
            if (empty) {
                return entries;
            }
            if (mode.equals(ALL)) {
                entries.add(held(entry("A.txt", "src/A.txt", "Modified", DiffEntry.Kind.MODIFIED, "a\nb\n", "a\nB\n")));
            }
            entries.add(held(entry("B.txt", "src/B.txt", "Added", DiffEntry.Kind.ADDED, "a\nb\nc\nd\n", secondText)));
            return entries;
        }
    }

    @Test
    public void listsTheFilesAndShowsTheDiffOfTheFirst() throws Exception {
        Files files = new Files();
        DiffBrowser browser = browser(files, null);
        await(browser);

        JavaFxTestSupport.onFx(() -> {
            assertEquals(ALL, browser.mode());
            assertTrue(browser.modeToggles().get(0).isSelected());
            assertEquals(List.of("src/A.txt", "src/B.txt"), browser.entries().stream().map(DiffEntry::location).toList());
            assertEquals("A.txt", browser.selected().name());
            assertEquals("1 difference", browser.summary());
        });
    }

    @Test
    public void filesAreListedOneAfterTheOtherOrUnderTheirFolders() throws Exception {
        Files files = new Files();
        DiffBrowser browser = browser(files, null);
        await(browser);
        JavaFxTestSupport.onFx(() -> browser.select(1));
        await(browser);

        JavaFxTestSupport.onFx(() -> {
            assertTrue(browser.listToggle().isSelected());
            assertEquals(List.of("A.txt", "B.txt"), browser.rows());
            // The "+" button of the header, to choose the columns from.
            assertTrue(browser.table().isTableMenuButtonVisible());

            browser.treeToggle().fire();

            assertEquals(List.of("src", "A.txt", "B.txt"), browser.rows());
            // The file selected stays so, and its diff is not read again.
            assertEquals("B.txt", browser.selected().name());
            assertFalse(browser.isBusy());
            assertEquals("2 differences", browser.summary());
        });
        assertEquals(List.of("all"), files.asked);
    }

    @Test
    public void aRefreshKeepsTheSelectedFileInViewAndTheLayoutIsToldAndRestored() throws Exception {
        Files files = new Files();
        DiffBrowser browser = browser(files, null);
        await(browser);
        int[] changes = new int[1];

        JavaFxTestSupport.onFx(() -> {
            browser.setOnLayoutChanged(() -> changes[0]++);
            browser.setTree(true);
            assertTrue(browser.isTree());
            browser.table().getTreeItem(0).setExpanded(false);
            assertEquals(List.of("src"), browser.rows());
            browser.refreshButton().fire();
        });
        await(browser);

        JavaFxTestSupport.onFx(() -> {
            // The selected file is shown again, so its folder opens; with none it stays collapsed.
            assertEquals(List.of("src", "A.txt", "B.txt"), browser.rows());
            assertTrue(changes[0] > 0);

            List<DiffBrowser.ColumnState> saved = DiffSettings.parse("path=300.0:true,file=200.0:true,status=90.0:false");
            browser.restoreColumns(saved);
            assertEquals(List.of("path", "file", "status"),
                    browser.columns().stream().map(DiffBrowser.ColumnState::id).toList());
            assertFalse(browser.columns().get(2).visible());
            assertEquals(saved.stream().map(DiffBrowser.ColumnState::id).toList(),
                    DiffSettings.parse(DiffSettings.format(browser.columns())).stream()
                            .map(DiffBrowser.ColumnState::id).toList());
            assertTrue(DiffSettings.parse("file=wide:true").isEmpty());

            browser.setTableShare(0.4);
            assertEquals(0.4, browser.tableShare(), 0.001);
            // Not a share of the height: left as it is.
            browser.setTableShare(-1);
            assertEquals(0.4, browser.tableShare(), 0.001);
        });
    }

    @Test
    public void theDocumentIsSavedAsTheProviderSaysInTheModeChosen() throws Exception {
        DiffBrowser browser = browser(new Files(), null);
        await(browser);
        DiffDocument document = new DiffDocument("key", "src [ Diff ]", browser);

        assertEquals(new SavedDocument("test-diff", "all"), JavaFxTestSupport.onFx(document::getSavedState));
        JavaFxTestSupport.onFx(() -> browser.modeToggles().get(1).fire());
        await(browser);
        assertEquals(new SavedDocument("test-diff", "staged"), JavaFxTestSupport.onFx(document::getSavedState));
    }

    @Test
    public void theNextFileIsShownOnTheTabTheLastOneWasOn() throws Exception {
        DiffBrowser browser = browser(new Files(), null);
        await(browser);
        JavaFxTestSupport.onFx(() -> browser.pane().setTextual(true));

        JavaFxTestSupport.onFx(() -> browser.select(1));
        await(browser);

        JavaFxTestSupport.onFx(() -> {
            assertTrue(browser.pane().isTextual());
            assertTrue(browser.pane().unified().startsWith("--- HEAD\n+++ Working Tree\n@@ "));
        });
    }

    @Test
    public void selectingAFileShowsItsDiff() throws Exception {
        DiffBrowser browser = browser(new Files(), null);
        await(browser);

        JavaFxTestSupport.onFx(() -> browser.select(1));
        await(browser);

        JavaFxTestSupport.onFx(() -> {
            assertEquals("2 differences", browser.summary());
            assertEquals(2, browser.pane().differences().size());
        });
    }

    @Test
    public void choosingAModeAsksAgainAndKeepsTheFileSelected() throws Exception {
        Files files = new Files();
        DiffBrowser browser = browser(files, null);
        await(browser);
        JavaFxTestSupport.onFx(() -> browser.select(1));
        await(browser);

        JavaFxTestSupport.onFx(() -> browser.modeToggles().get(1).fire());
        await(browser);

        JavaFxTestSupport.onFx(() -> {
            assertEquals(STAGED, browser.mode());
            assertEquals(List.of("src/B.txt"), browser.entries().stream().map(DiffEntry::location).toList());
            assertEquals("B.txt", browser.selected().name());
            assertEquals("2 differences", browser.summary());
            // A click on the chosen mode leaves it chosen, and asks nothing.
            browser.modeToggles().get(1).fire();
            assertTrue(browser.modeToggles().get(1).isSelected());
        });
        assertEquals(List.of("all", "staged"), files.asked);
    }

    @Test
    public void aDiffAskedForAgainComparesInTheWayAskedForAndOnlyTheUsersChoiceIsKept() throws Exception {
        Files files = new Files();
        DiffBrowser browser = browser(files, ALL);
        await(browser);

        // Asked for again in another way, by a provider made anew: its mode is told by its id.
        JavaFxTestSupport.onFx(() -> browser.show(new DiffMode("staged", "Staged", Region::new)));
        await(browser);
        JavaFxTestSupport.onFx(() -> {
            assertEquals(STAGED, browser.mode());
            assertTrue(browser.modeToggles().get(1).isSelected());
            assertEquals(1, browser.entries().size());
        });
        // Not the user's choice: nothing for the provider to keep.
        assertEquals(List.of(), files.chosen);

        // In the same way, or in none, or one it does not have: compared again as it is.
        JavaFxTestSupport.onFx(() -> browser.show(STAGED));
        await(browser);
        JavaFxTestSupport.onFx(() -> browser.show(null));
        await(browser);
        JavaFxTestSupport.onFx(() -> browser.show(new DiffMode("other", "Other", Region::new)));
        await(browser);
        assertEquals(STAGED, JavaFxTestSupport.onFx(browser::mode));
        assertEquals(List.of("all", "staged", "staged", "staged", "staged"), files.asked);
        assertEquals(List.of(), files.chosen);

        // The toggle, which is the user's: told to the provider.
        JavaFxTestSupport.onFx(() -> browser.modeToggles().get(0).fire());
        await(browser);
        assertEquals(List.of("all"), files.chosen);
    }

    @Test
    public void theSameFileComparedAgainKeepsItsDiffAndAnotherOneGivesWayAtOnce() throws Exception {
        Files files = new Files();
        DiffBrowser browser = browser(files, ALL);
        await(browser);
        JavaFxTestSupport.onFx(() -> browser.select(1));
        await(browser);
        DiffPane shown = JavaFxTestSupport.onFx(browser::pane);
        assertEquals("2 differences", JavaFxTestSupport.onFx(browser::summary));

        // Refresh with the file changed: the diff that is there takes the new texts.
        files.secondText = "a\nb\nc\nD\n";
        JavaFxTestSupport.onFx(() -> {
            browser.refreshButton().fire();
            assertSame(shown, browser.pane());
        });
        await(browser);
        JavaFxTestSupport.onFx(() -> {
            assertSame(shown, browser.pane());
            assertEquals("1 difference", browser.summary());
        });

        // Another file chosen: the diff shown gives way at once, to the word that one is coming.
        JavaFxTestSupport.onFx(() -> {
            browser.select(0);
            assertNull(browser.pane());
            assertEquals("Comparing...", browser.placeholderText());
        });
        await(browser);
        assertEquals("1 difference", JavaFxTestSupport.onFx(browser::summary));
    }

    @Test
    public void aClosedDiffLetsGoOfItsProviderAndAsksItNothingMore() throws Exception {
        Files files = new Files();
        DiffBrowser browser = browser(files, ALL);
        await(browser);
        int asked = files.asked.size();

        JavaFxTestSupport.onFx(() -> {
            // Asked for, and closed before it is answered.
            browser.refresh();
            browser.dispose();
        });
        Thread.sleep(300);

        assertEquals(1, files.disposed);
        assertEquals(asked, files.asked.size());
        assertNull(JavaFxTestSupport.onFx(browser::pane));
    }

    @Test
    public void aProviderThatFailsOnTheFxThreadIsTakenAsOneWithNothingToTell() throws Exception {
        Files files = new Files();
        files.failing = true;

        // No modes, so none to choose; its files are listed all the same.
        DiffBrowser browser = browser(files, null);

        JavaFxTestSupport.onFx(() -> {
            assertTrue(browser.modeToggles().isEmpty());
            assertNull(browser.mode());
            // Asked as the session is kept: not restored, and nothing thrown.
            assertNull(browser.savedState());
        });
    }

    @Test
    public void theBarIsReachedWithTheKeyboard() throws Exception {
        DiffBrowser browser = browser(new Files(), ALL);
        await(browser);

        JavaFxTestSupport.onFx(() -> {
            browser.modeToggles().forEach(toggle -> assertTrue(toggle.isFocusTraversable()));
            assertTrue(browser.refreshButton().isFocusTraversable());
            assertTrue(browser.previousButton().isFocusTraversable());
        });
    }

    @Test
    public void opensInTheModeAskedFor() throws Exception {
        Files files = new Files();
        DiffBrowser browser = browser(files, STAGED);
        await(browser);

        JavaFxTestSupport.onFx(() -> {
            assertEquals(STAGED, browser.mode());
            assertTrue(browser.modeToggles().get(1).isSelected());
            assertEquals(1, browser.entries().size());
        });
    }

    @Test
    public void refreshReadsTheTextsAgain() throws Exception {
        Files files = new Files();
        DiffBrowser browser = browser(files, STAGED);
        await(browser);

        files.secondText = "a\nb\nc\nd\n";
        JavaFxTestSupport.onFx(() -> browser.refreshButton().fire());
        await(browser);

        JavaFxTestSupport.onFx(() -> assertEquals("No differences", browser.summary()));
        assertEquals(List.of("staged", "staged"), files.asked);
    }

    @Test
    public void nothingToCompareSaysSo() throws Exception {
        Files files = new Files();
        files.empty = true;
        DiffBrowser browser = browser(files, null);
        await(browser);

        JavaFxTestSupport.onFx(() -> {
            assertTrue(browser.entries().isEmpty());
            assertNull(browser.pane());
            assertEquals("", browser.summary());
            // No table for no file: one line says there is nothing, in place of both.
            assertTrue(browser.getCenter() instanceof Label);
            assertEquals("No changes", ((Label) browser.getCenter()).getText());
            assertFalse(browser.previousButton().isVisible() && !browser.previousButton().isDisable());
        });

        // A file to list again: the table is back.
        files.empty = false;
        JavaFxTestSupport.onFx(browser::refresh);
        await(browser);
        JavaFxTestSupport.onFx(() -> {
            assertFalse(browser.getCenter() instanceof Label);
            assertEquals(2, browser.entries().size());
        });
    }

    @Test
    public void aProviderOfOneFileHasNoTable() throws Exception {
        Files files = new Files();
        files.listed = false;
        DiffBrowser browser = browser(files, STAGED);
        await(browser);

        JavaFxTestSupport.onFx(() -> {
            assertTrue(browser.lookupAll(".diff-files").isEmpty());
            assertEquals("2 differences", browser.summary());
            browser.dispose();
            assertNull(browser.pane());
        });
    }

    private static DiffBrowser browser(DiffProvider provider, DiffMode mode) throws Exception {
        return JavaFxTestSupport.onFx(() -> new DiffBrowser(provider, mode));
    }

    /** Until the provider has answered, the texts are read and they are compared. */
    private static void await(DiffBrowser browser) throws Exception {
        for (int i = 0; i < 100; i++) {
            boolean settled = JavaFxTestSupport.onFx(() -> !browser.isBusy()
                    && !"Comparing...".equals(browser.summary()));
            if (settled) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("the diff was not shown");
    }

    private static DiffEntry entry(String name, String location, String status, DiffEntry.Kind kind,
            String left, String right) {
        return new DiffEntry(name, location, status, kind, null,
                () -> new DiffPair(DiffSource.of("HEAD", name, left), DiffSource.of("Working Tree", name, right)));
    }

    @Test
    public void textsReadForTheListBeforeAreNotShownOnceTheFilesAreAskedForAgain() throws Exception {
        Files files = new Files();
        DiffBrowser browser = browser(files, ALL);
        await(browser);

        // The texts of the second file are being read, in the first mode...
        files.loadHeld = new CountDownLatch(1);
        int loads = files.loads.get();
        JavaFxTestSupport.onFx(() -> browser.select(1));
        // ...when the other mode is chosen, whose list takes its time.
        files.listHeld = new CountDownLatch(1);
        JavaFxTestSupport.onFx(() -> browser.modeToggles().get(1).fire());

        // The texts arrive first: they are of the mode before, and are not shown under the new one.
        files.loadHeld.countDown();
        for (int i = 0; i < 200 && files.loads.get() == loads; i++) {
            Thread.sleep(10);
        }
        assertEquals(loads + 1, files.loads.get());
        JavaFxTestSupport.onFx(() -> { });
        JavaFxTestSupport.onFx(() -> {
            assertNull(browser.pane());
            assertEquals("Comparing...", browser.placeholderText());
            assertTrue(browser.isBusy());
        });

        files.loadHeld = null;
        files.listHeld.countDown();
        files.listHeld = null;
        await(browser);
        JavaFxTestSupport.onFx(() -> {
            assertEquals(STAGED, browser.mode());
            assertEquals("2 differences", browser.summary());
        });
    }

    @Test
    public void whileTheFilesAreAskedForAgainTheDiffShownSaysSoAndIsNotSteppedThrough() throws Exception {
        Files files = new Files();
        DiffBrowser browser = browser(files, ALL);
        await(browser);
        JavaFxTestSupport.onFx(() -> browser.select(1));
        await(browser);
        JavaFxTestSupport.onFx(() -> {
            assertEquals("2 differences", browser.summary());
            assertFalse(browser.nextButton().isDisable());
        });

        files.listHeld = new CountDownLatch(1);
        JavaFxTestSupport.onFx(() -> {
            browser.refresh();
            // At once, with the diff of before still there.
            assertNotNull(browser.pane());
            assertEquals("Comparing...", browser.summary());
            assertTrue(browser.nextButton().isDisable());
            assertTrue(browser.previousButton().isDisable());
        });

        files.listHeld.countDown();
        files.listHeld = null;
        await(browser);
        JavaFxTestSupport.onFx(() -> {
            assertEquals("2 differences", browser.summary());
            assertFalse(browser.nextButton().isDisable());
        });
    }

    @Test
    public void theKeyboardSentToADiffThatIsNotThereYetGoesToItWhenItComes() throws Exception {
        Files files = new Files();
        files.listHeld = new CountDownLatch(1);
        Stage[] stage = new Stage[1];
        DiffBrowser browser = JavaFxTestSupport.onFx(() -> {
            DiffBrowser made = new DiffBrowser(files, ALL);
            stage[0] = new Stage();
            stage[0].setScene(new Scene(made, 600, 400));
            stage[0].show();
            // As the tab is selected, before the provider has answered.
            made.focusEditor();
            return made;
        });
        try {
            JavaFxTestSupport.onFx(() -> {
                assertNull(browser.pane());
                // Not on what takes no keys: on the table, until there is a text to be in.
                assertSame(browser.table(), browser.getScene().getFocusOwner());
            });

            files.listHeld.countDown();
            files.listHeld = null;
            await(browser);
            JavaFxTestSupport.onFx(() -> assertTrue("the keyboard is in " + browser.getScene().getFocusOwner(),
                    within(browser.getScene().getFocusOwner(), browser.pane())));

            // The file whose diff has the keyboard gives way to another: the keyboard is in that one.
            JavaFxTestSupport.onFx(() -> browser.select(1));
            await(browser);
            JavaFxTestSupport.onFx(() -> assertTrue("the keyboard is in " + browser.getScene().getFocusOwner(),
                    within(browser.getScene().getFocusOwner(), browser.pane())));

            // Put elsewhere by the user meanwhile, it stays there.
            JavaFxTestSupport.onFx(() -> {
                browser.select(0);
                browser.refreshButton().requestFocus();
            });
            await(browser);
            JavaFxTestSupport.onFx(() -> assertSame(browser.refreshButton(), browser.getScene().getFocusOwner()));
        } finally {
            JavaFxTestSupport.onFx(() -> stage[0].hide());
        }
    }

    @Test
    public void theKeysOfTheNextAndPreviousDifferenceWorkFromTheTableAndTheButtons() throws Exception {
        Files files = new Files();
        Stage[] stage = new Stage[1];
        // In a window: the table is part of the tab once the tab is shown.
        DiffBrowser browser = JavaFxTestSupport.onFx(() -> {
            DiffBrowser made = new DiffBrowser(files, ALL);
            stage[0] = new Stage();
            stage[0].setScene(new Scene(made, 600, 400));
            stage[0].show();
            return made;
        });
        await(browser);
        JavaFxTestSupport.onFx(() -> browser.select(1));
        await(browser);

        JavaFxTestSupport.onFx(() -> {
            // At the first of its two differences, as a diff opens.
            assertEquals(0, browser.pane().current());
            Event.fireEvent(browser.table(), key(KeyCode.PERIOD));
            assertEquals(1, browser.pane().current());
            Event.fireEvent(browser.refreshButton(), key(KeyCode.COMMA));
            assertEquals(0, browser.pane().current());
            Event.fireEvent(browser.modeToggles().get(0), key(KeyCode.PERIOD));
            assertEquals(1, browser.pane().current());
            stage[0].hide();
        });
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, true, false, false);
    }

    private static boolean within(Node node, Node part) {
        while (node != null && node != part) {
            node = node.getParent();
        }
        return node != null && part != null;
    }
}
