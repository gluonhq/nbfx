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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.scene.layout.Region;
import org.junit.Test;
import org.netbeans.api.nbfx.editor.SavedDocument;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

        @Override
        public List<DiffMode> modes() {
            return List.of(ALL, STAGED);
        }

        @Override
        public boolean listed() {
            return listed;
        }

        @Override
        public SavedDocument savedState(DiffMode mode) {
            return new SavedDocument("test-diff", mode.id());
        }

        @Override
        public List<DiffEntry> entries(DiffMode mode) {
            asked.add(mode.id());
            List<DiffEntry> entries = new ArrayList<>();
            if (empty) {
                return entries;
            }
            if (mode.equals(ALL)) {
                entries.add(entry("A.txt", "src/A.txt", "Modified", DiffEntry.Kind.MODIFIED, "a\nb\n", "a\nB\n"));
            }
            entries.add(entry("B.txt", "src/B.txt", "Added", DiffEntry.Kind.ADDED, "a\nb\nc\nd\n", secondText));
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
            assertFalse(browser.previousButton().isVisible() && !browser.previousButton().isDisable());
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
                () -> new DiffPair(new DiffSource("HEAD", inMemory(name, left)),
                        new DiffSource("Working Tree", inMemory(name, right))));
    }

    private static FileObject inMemory(String name, String text) throws IOException {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData(name);
        try (OutputStream out = file.getOutputStream()) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
