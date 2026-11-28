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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BooleanSupplier;
import javafx.scene.layout.Region;
import org.netbeans.junit.MockServices;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.LocalFileSystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Verifies, with the editors of the editor module on the headless JavaFX toolkit, that the right
 * side of a diff is edited when it is a file on disk: that the texts are compared again as it is
 * changed, that a block and all of them are reverted as edits that are saved with the tab, and
 * that a diff of several files keeps the edits of a file left without saving.
 */
public class DiffEditingTest {

    private static final DiffMode ALL = new DiffMode("all", "Everything", Region::new);

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    /** With the editors of the editor module, and no stand-in another test left in their place. */
    @Before
    public void realEditors() {
        MockServices.setServices();
        // The user says yes to what is asked, and what was asked is kept.
        ASKED.clear();
        answer = true;
        DiffPane.confirmation = text -> {
            ASKED.add(text);
            return answer;
        };
    }

    @After
    public void askTheUserAgain() {
        DiffPane.confirmation = WAS_ASKED;
    }

    private static final java.util.function.Predicate<String> WAS_ASKED = DiffPane.confirmation;
    private static final List<String> ASKED = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile boolean answer = true;

    @Test
    public void aFileOnDiskIsEditedAndComparedAgainAsItChanges() throws Exception {
        File file = onDisk("App.txt", "a\nb\nc\n");
        DiffPane pane = pane("a\nb\nc\n", file);
        until(() -> "No differences".equals(pane.summary()));

        JavaFxTestSupport.onFx(() -> {
            assertTrue(pane.isEditable());
            assertTrue(pane.editor().editableProperty().get());
            assertTrue(pane.revertAllButton().isDisable());
            assertTrue(pane.editor().replaceRange(2, 3, "x"));
        });
        until(() -> pane.differences().size() == 1);

        JavaFxTestSupport.onFx(() -> {
            assertEquals("1 difference", pane.summary());
            assertTrue(pane.editor().isModified());
            assertFalse(pane.revertAllButton().isDisable());
            assertEquals(1, pane.revertButtons().size());
            // Not saved: the file is as it was.
            assertEquals("a\nb\nc\n", read(file));
            pane.dispose();
        });
    }

    @Test
    public void aTextInMemoryIsNotEdited() throws Exception {
        // A text that is only shown; and a file on disk whose source does not say it is edited.
        File file = onDisk("Shown.txt", "b\n");
        for (DiffSource right : List.of(DiffSource.of("Index", "App.txt", "b\n"),
                DiffSource.of("Working Tree", "Shown.txt", "b\n"))) {
            DiffPane pane = JavaFxTestSupport.onFx(() -> new DiffPane(DiffSource.of("HEAD", "App.txt", "a\n"), right));
            until(() -> pane.differences().size() == 1);
            assertNotEdited(pane);
        }
        assertEquals("b\n", read(file));
    }

    private static void assertNotEdited(DiffPane pane) throws Exception {

        JavaFxTestSupport.onFx(() -> {
            assertFalse(pane.isEditable());
            assertNull(pane.editor());
            assertTrue(pane.revertAllButton().isDisable());
            assertTrue(pane.revertButtons().stream().noneMatch(javafx.scene.Node::isVisible));
            pane.revert(0);
            pane.revertAll();
            assertEquals(1, pane.differences().size());
            pane.dispose();
        });
    }

    @Test
    public void aBlockIsRevertedAsAnEditThatSavingWrites() throws Exception {
        File file = onDisk("App.txt", "a\nx\nc\nd\nnew\n");
        DiffPane pane = pane("a\nb\nc\nd\n", file);
        until(() -> pane.differences().size() == 2);

        JavaFxTestSupport.onFx(() -> pane.revertButtons().get(1).fire());
        until(() -> pane.differences().size() == 1);
        JavaFxTestSupport.onFx(() -> {
            assertTrue(pane.editor().isModified());
            assertEquals("a\nx\nc\nd\n", read(file).replace("new\n", ""));
            DiffDocument document = new DiffDocument("key", "App.txt [ Diff ]", pane);
            assertTrue(document.isModified());
            assertTrue(document.canUndoProperty().get());
            assertTrue(document.editableProperty().get());
            save(document);
            assertFalse(document.isModified());
            assertEquals("a\nx\nc\nd\n", read(file));
        });

        JavaFxTestSupport.onFx(() -> pane.revertAllButton().fire());
        until(() -> pane.differences().isEmpty());
        JavaFxTestSupport.onFx(() -> {
            save(pane);
            assertEquals("a\nb\nc\nd\n", read(file));
            pane.dispose();
        });
    }

    @Test
    public void theEditsOfAFileLeftAreKeptUntilSaved() throws Exception {
        File first = onDisk("First.txt", "a\n");
        File second = onDisk("Second.txt", "b\n");
        DiffProvider provider = new DiffProvider() {
            @Override
            public List<DiffMode> modes() {
                return List.of(ALL);
            }

            @Override
            public List<DiffEntry> entries(DiffMode mode, BooleanSupplier cancelled) {
                return List.of(entry(first, "old a\n"), entry(second, "old b\n"));
            }
        };
        DiffBrowser browser = JavaFxTestSupport.onFx(() -> new DiffBrowser(provider, ALL));
        until(() -> browser.pane() != null && browser.pane().differences().size() == 1);
        DiffDocument document = JavaFxTestSupport.onFx(() -> new DiffDocument("key", "Diff", browser));

        JavaFxTestSupport.onFx(() -> {
            assertFalse(document.isModified());
            assertFalse(browser.revertAllButton().isDisable());
            browser.revertAllButton().fire();
            assertTrue(document.isModified());
            browser.select(1);
        });
        until(() -> browser.pane() != null && !browser.isBusy()
                && "Second.txt".equals(browser.selected().name()) && browser.pane().differences().size() == 1);

        JavaFxTestSupport.onFx(() -> {
            // The first file is not shown, its edits are not lost.
            assertEquals(1, browser.unsaved().size());
            assertTrue(document.isModified());
            assertEquals("a\n", read(first));
            EditorDocument shown = browser.pane().editor();
            assertNotNull(shown);
            assertFalse(shown.isModified());

            save(document);
            assertEquals("old a\n", read(first));
            assertEquals("b\n", read(second));
            assertFalse(document.isModified());
            assertTrue(browser.unsaved().isEmpty());
            browser.dispose();
        });
    }

    private DiffEntry entry(File file, String committed) {
        return new DiffEntry(file.getName(), file.getName(), "Modified", DiffEntry.Kind.MODIFIED, null,
                cancelled -> new DiffPair(DiffSource.of("HEAD", file.getName(), committed),
                        DiffSource.editable("Working Tree", onDisk(file), read(file))));
    }

    private DiffPane pane(String left, File right) throws Exception {
        FileObject to = onDisk(right);
        String onDisk = read(right);
        return JavaFxTestSupport.onFx(() -> new DiffPane(DiffSource.of("HEAD", right.getName(), left),
                DiffSource.editable("Working Tree", to, onDisk)));
    }

    private File onDisk(String name, String text) throws IOException {
        File file = folder.newFile(name);
        Files.writeString(file.toPath(), text);
        return file;
    }

    private static void save(EditorDocument document) {
        try {
            document.save();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void save(DiffContent content) {
        try {
            content.save();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    /** The file as one of a file system of the disk, as those the IDE has. */
    private FileObject onDisk(File file) {
        try {
            LocalFileSystem disk = new LocalFileSystem();
            disk.setRootDirectory(folder.getRoot());
            return disk.findResource(file.getName());
        } catch (IOException | java.beans.PropertyVetoException ex) {
            throw new AssertionError(ex);
        }
    }

    private static String read(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void until(BooleanSupplier settled) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (JavaFxTestSupport.onFx(() -> settled.getAsBoolean())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("the diff did not get there");
    }

    @Test
    public void theFileEditedKeepsItsEditorWhenTheDiffIsAskedForAgainAndIsComparedAsItsEditorHasIt() throws Exception {
        File file = onDisk("App.txt", "a\nb\nc\n");
        FileObject onDisk = onDisk(file);
        DiffPane pane = JavaFxTestSupport.onFx(() -> new DiffPane(DiffSource.of("HEAD", "App.txt", "a\nb\nc\n"),
                DiffSource.editable("Working Tree", onDisk, "a\nb\nc\n")));
        until(() -> "No differences".equals(pane.summary()));
        EditorDocument editor = JavaFxTestSupport.onFx(pane::editor);
        assertNotNull(editor);

        // Typed, and not saved: what is compared is what the editor holds, not what was read.
        JavaFxTestSupport.onFx(() -> assertTrue(editor.replaceRange(2, 3, "x")));
        until(() -> pane.differences().size() == 1);
        JavaFxTestSupport.onFx(() -> assertTrue(pane.unified(), pane.unified().contains("-b\n+x\n")));

        // Asked for again, the file read as it is on disk - without the edit - and the commit
        // another one: the editor stays, with the edit, and that is what is compared.
        JavaFxTestSupport.onFx(() -> pane.setSources(DiffSource.of("HEAD", "App.txt", "a\nx\nc\n"),
                DiffSource.editable("Working Tree", onDisk, read(file))));
        until(() -> "No differences".equals(pane.summary()));
        JavaFxTestSupport.onFx(() -> {
            assertSame(editor, pane.editor());
            assertTrue(editor.isModified());
            assertTrue(pane.isEditable());
        });

        // Another file in its place is another editor, and the side of a text only shown is none.
        File other = onDisk("Other.txt", "o\n");
        FileObject otherOnDisk = onDisk(other);
        JavaFxTestSupport.onFx(() -> pane.setSources(DiffSource.of("HEAD", "Other.txt", "o\n"),
                DiffSource.editable("Working Tree", otherOnDisk, "o\n")));
        until(() -> "No differences".equals(pane.summary()));
        JavaFxTestSupport.onFx(() -> {
            assertNotNull(pane.editor());
            assertTrue(pane.editor() != editor);
            pane.setSources(DiffSource.of("HEAD", "Other.txt", "o\n"), DiffSource.of("Index", "Other.txt", "o\n"));
            assertNull(pane.editor());
            assertFalse(pane.isEditable());
            pane.dispose();
        });
        // Nothing was saved.
        assertEquals("a\nb\nc\n", read(file));
    }

    @Test
    public void howTheLinesOfAFileEditedEndIsToldByTheFileNotByItsEditor() throws Exception {
        // The same lines, ended as Windows ends them, on both sides: nothing differs.
        File same = onDisk("Same.txt", "a\r\nb\r\n");
        FileObject sameOnDisk = onDisk(same);
        DiffPane pane = JavaFxTestSupport.onFx(() -> new DiffPane(DiffSource.of("HEAD", "Same.txt", "a\r\nb\r\n"),
                DiffSource.editable("Working Tree", sameOnDisk, "a\r\nb\r\n")));
        until(() -> !"Comparing...".equals(pane.summary()));
        JavaFxTestSupport.onFx(() -> {
            assertEquals("No differences", pane.summary());
            pane.dispose();
        });

        // Committed with one ending, on disk with the other: said, though no line differs.
        DiffPane ends = JavaFxTestSupport.onFx(() -> new DiffPane(DiffSource.of("HEAD", "Same.txt", "a\nb\n"),
                DiffSource.editable("Working Tree", sameOnDisk, "a\r\nb\r\n")));
        until(() -> !"Comparing...".equals(ends.summary()));
        JavaFxTestSupport.onFx(() -> {
            assertEquals("Only line endings differ: LF in HEAD, CRLF in Working Tree", ends.summary());
            ends.dispose();
        });
    }

    @Test
    public void theTabOfADiffHandsWhatChangesATextToTheSideEditedAndCopyToTheSideTheKeyboardIsIn() throws Exception {
        File file = onDisk("App.txt", "a\nx\n");
        DiffPane pane = pane("a\nb\n", file);
        until(() -> pane.differences().size() == 1);
        DiffDocument document = JavaFxTestSupport.onFx(() -> new DiffDocument("key", "App.txt [ Diff ]", pane));

        JavaFxTestSupport.onFx(() -> {
            assertTrue(document.editableProperty().get());
            assertFalse(document.isModified());
            assertSame(pane.editor(), pane.editorProperty().getValue());
            // The editors of the file show the same text: this tab is one of them.
            assertTrue(document.sharesContentWith(pane.editor()));
            pane.revertAllButton().fire();
            assertTrue(document.isModified());
            save(document);
            assertFalse(document.isModified());
            assertEquals("a\nb\n", read(file));
            document.dispose();
            // Closed: no side is edited, and nothing is left to save.
            assertNull(pane.editorProperty().getValue());
            assertFalse(document.isModified());
        });
    }

    @Test
    public void noBlockIsRevertedWhileTheBlocksAreNotThoseOfTheTextAndEachClickAfterwardsIsTaken() throws Exception {
        File file = onDisk("App.txt", "a\nx\nc\ny\ne\nz\n");
        DiffPane pane = pane("a\nb\nc\nd\ne\nf\n", file);
        until(() -> pane.differences().size() == 3 && !pane.revertAllButton().isDisable());

        JavaFxTestSupport.onFx(() -> {
            assertTrue(pane.revertButtons().stream().noneMatch(javafx.scene.Node::isDisable));
            // Typed: the blocks are those of the text before, and say so at once.
            assertTrue(pane.editor().replaceRange(0, 1, "A"));
            assertEquals("Comparing...", pane.summary());
            assertTrue(pane.revertAllButton().isDisable());
            assertTrue(pane.revertButtons().stream().allMatch(javafx.scene.Node::isDisable));
            // Nothing is done by what cannot be clicked, nor asked.
            pane.revertButtons().get(1).fire();
            pane.revert(1);
            pane.revertAll();
        });
        assertTrue(ASKED.isEmpty());
        // The line typed in is beside one that differed: the same three blocks, the first a line longer.
        until(() -> !pane.revertAllButton().isDisable());
        JavaFxTestSupport.onFx(() -> {
            assertEquals("3 differences", pane.summary());
            assertEquals(2, pane.differences().get(0).rightCount());
            assertTrue(pane.revertButtons().stream().noneMatch(javafx.scene.Node::isDisable));
        });

        // One after the other, each once the blocks are found again: none of the clicks is lost.
        for (int left = 2; left >= 0; left--) {
            int blocks = left;
            JavaFxTestSupport.onFx(() -> pane.revertButtons().get(0).fire());
            until(() -> pane.differences().size() == blocks
                    && (blocks == 0 || !pane.revertButtons().get(0).isDisable()));
        }
        JavaFxTestSupport.onFx(() -> {
            assertEquals("No differences", pane.summary());
            save(pane);
            pane.dispose();
        });
        assertEquals("a\nb\nc\nd\ne\nf\n", read(file));
    }

    @Test
    public void allOfAFileIsRevertedOnlyOnceTheUserSaidSo() throws Exception {
        File file = onDisk("App.txt", "a\nx\nc\ny\n");
        DiffPane pane = pane("a\nb\nc\nd\n", file);
        until(() -> pane.differences().size() == 2 && !pane.revertAllButton().isDisable());

        answer = false;
        JavaFxTestSupport.onFx(() -> pane.revertAllButton().fire());
        assertEquals(1, ASKED.size());
        // Which file, with the text of what, and how much of it.
        assertTrue(ASKED.get(0), ASKED.get(0).startsWith("Replace the text of App.txt with that of HEAD?"));
        assertTrue(ASKED.get(0), ASKED.get(0).contains("2 blocks of this file"));
        JavaFxTestSupport.onFx(() -> {
            assertFalse(pane.editor().isModified());
            assertEquals(2, pane.differences().size());
        });

        answer = true;
        JavaFxTestSupport.onFx(() -> pane.revertAllButton().fire());
        until(() -> pane.differences().isEmpty());
        assertEquals(2, ASKED.size());
        JavaFxTestSupport.onFx(() -> {
            assertTrue(pane.editor().isModified());
            pane.dispose();
        });
    }

    @Test
    public void eachBlockIsRevertedByAButtonThatSaysWhichAndTheCaretStaysOnItsLine() throws Exception {
        File file = onDisk("App.txt", "a\nnew\nb\nc\nx\ne\n");
        DiffPane pane = pane("a\nb\nc\nd\ne\n", file);
        until(() -> pane.differences().size() == 2 && !pane.revertAllButton().isDisable());

        JavaFxTestSupport.onFx(() -> {
            assertEquals("Revert block 1 of 2: Line 2 added", pane.revertButtons().get(0).getAccessibleText());
            assertEquals("Revert block 2 of 2: Line 5 changed", pane.revertButtons().get(1).getAccessibleText());
            assertTrue(pane.revertButtons().get(0).isFocusTraversable());
            assertTrue(pane.revertAllButton().isFocusTraversable());
        });

        // In a window, where an editor has a caret to place.
        javafx.stage.Stage[] stage = new javafx.stage.Stage[1];
        JavaFxTestSupport.onFx(() -> {
            stage[0] = new javafx.stage.Stage();
            stage[0].setScene(new javafx.scene.Scene(pane, 800, 400));
            stage[0].show();
        });
        try {
            // The caret on the last line, "e": the line added above it is taken out.
            JavaFxTestSupport.onFx(() -> pane.editor().restoreView(0, 5, 1));
            until(() -> pane.editor().getCaretParagraph() == 5);
            JavaFxTestSupport.onFx(() -> pane.revertButtons().get(0).fire());
            until(() -> pane.differences().size() == 1 && !pane.revertAllButton().isDisable());
            until(() -> pane.editor().getCaretParagraph() == 4 && pane.editor().getCaretColumn() == 1);

            // The caret in the line that is put back: at its start.
            JavaFxTestSupport.onFx(() -> pane.editor().restoreView(0, 3, 1));
            until(() -> pane.editor().getCaretParagraph() == 3 && pane.editor().getCaretColumn() == 1);
            JavaFxTestSupport.onFx(() -> pane.revertButtons().get(0).fire());
            until(() -> pane.differences().isEmpty());
            until(() -> pane.editor().getCaretParagraph() == 3 && pane.editor().getCaretColumn() == 0);
        } finally {
            JavaFxTestSupport.onFx(() -> {
                stage[0].hide();
                pane.dispose();
            });
        }
    }

    @Test
    public void aRevertMadeOnTheTextualTabIsUndoneThereAndShowsInTheUnifiedDiff() throws Exception {
        File file = onDisk("App.txt", "a\nx\n");
        DiffPane pane = pane("a\nb\n", file);
        until(() -> pane.differences().size() == 1 && !pane.revertAllButton().isDisable());
        DiffDocument document = JavaFxTestSupport.onFx(() -> new DiffDocument("key", "App.txt [ Diff ]", pane));
        javafx.stage.Stage[] stage = new javafx.stage.Stage[1];
        JavaFxTestSupport.onFx(() -> {
            stage[0] = new javafx.stage.Stage();
            stage[0].setScene(new javafx.scene.Scene(pane, 800, 400));
            stage[0].show();
            pane.setTextual(true);
        });
        try {
            until(() -> !pane.isWritingUnified() && pane.textualEditor() != null);
            JavaFxTestSupport.onFx(() -> {
                assertFalse(document.canUndoProperty().get());
                // Revert All is offered on this tab too.
                pane.revertAllButton().fire();
            });
            until(() -> pane.differences().isEmpty());
            // And what it did can be undone from here, as from the two sides.
            until(() -> document.canUndoProperty().get());
            JavaFxTestSupport.onFx(() -> {
                assertSame(pane.editor(), pane.editorProperty().getValue());
                assertTrue(document.isModified());
                assertTrue(document.editableProperty().get());
                assertEquals("No differences", pane.textualNote());
                document.undo();
            });
            until(() -> pane.differences().size() == 1 && !pane.isWritingUnified() && pane.textualEditor() != null);
            JavaFxTestSupport.onFx(() -> {
                assertTrue(pane.unified(), pane.unified().contains("-b\n+x\n"));
                assertTrue(document.canRedoProperty().get());
                // Nothing is cut from or pasted into the text edited while the keyboard is not in it.
                document.cut();
                document.paste();
                document.showReplace();
            });
            until(() -> pane.differences().size() == 1 && !pane.revertAllButton().isDisable());
        } finally {
            JavaFxTestSupport.onFx(() -> {
                stage[0].hide();
                document.dispose();
            });
        }
        assertEquals("a\nx\n", read(file));
    }

    @Test
    public void everyFileThatCouldNotBeSavedIsNamedAndTheOthersAreSaved() throws Exception {
        File first = onDisk("First.txt", "a\n");
        File second = onDisk("Second.txt", "b\n");
        File third = onDisk("Third.txt", "c\n");
        DiffProvider provider = new DiffProvider() {
            @Override
            public List<DiffMode> modes() {
                return List.of(ALL);
            }

            @Override
            public List<DiffEntry> entries(DiffMode mode, BooleanSupplier cancelled) {
                return List.of(entry(first, "old a\n"), entry(second, "old b\n"), entry(third, "old c\n"));
            }
        };
        DiffBrowser browser = JavaFxTestSupport.onFx(() -> new DiffBrowser(provider, ALL));
        for (int i = 0; i < 3; i++) {
            int index = i;
            until(() -> browser.pane() != null && !browser.isBusy() && browser.selected() == browser.entries().get(index)
                    && !browser.revertAllButton().isDisable());
            JavaFxTestSupport.onFx(() -> {
                browser.revertAllButton().fire();
                if (index < 2) {
                    browser.select(index + 1);
                }
            });
        }
        until(() -> browser.unsaved().size() == 2);
        org.junit.Assume.assumeTrue("files cannot be made read-only here",
                first.setWritable(false) && second.setWritable(false) && !first.canWrite());
        try {
            IOException failure = JavaFxTestSupport.onFx(() -> {
                try {
                    browser.save();
                    return null;
                } catch (IOException ex) {
                    return ex;
                }
            });
            org.junit.Assume.assumeNotNull("a read-only file is written all the same here", failure);
            // Both that failed are named, and the one that could be written was.
            assertTrue(failure.getMessage(), failure.getMessage().contains("First.txt"));
            assertTrue(failure.getMessage(), failure.getMessage().contains("Second.txt"));
            assertEquals(1, failure.getSuppressed().length);
            assertEquals("old c\n", read(third));
            assertEquals("a\n", read(first));
            // What could not be written is still there to save.
            JavaFxTestSupport.onFx(() -> {
                assertEquals(2, browser.unsaved().size());
                assertTrue(browser.modifiedProperty().getValue());
            });
        } finally {
            first.setWritable(true);
            second.setWritable(true);
            JavaFxTestSupport.onFx(browser::dispose);
        }
    }

    @Test
    public void aRevertIsAnEditThatUndoTakesBackAndRedoMakesAgain() throws Exception {
        File file = onDisk("App.txt", "a\nx\nc\n");
        DiffPane pane = pane("a\nb\nc\n", file);
        until(() -> pane.differences().size() == 1 && !pane.revertAllButton().isDisable());
        DiffDocument document = JavaFxTestSupport.onFx(() -> new DiffDocument("key", "App.txt [ Diff ]", pane));
        // In a window: an editor undoes there.
        javafx.stage.Stage[] stage = new javafx.stage.Stage[1];
        JavaFxTestSupport.onFx(() -> {
            stage[0] = new javafx.stage.Stage();
            stage[0].setScene(new javafx.scene.Scene(pane, 800, 400));
            stage[0].show();
        });
        try {
            JavaFxTestSupport.onFx(() -> pane.revertButtons().get(0).fire());
            until(() -> pane.differences().isEmpty());
            until(() -> document.canUndoProperty().get());

            JavaFxTestSupport.onFx(document::undo);
            until(() -> pane.differences().size() == 1 && !pane.revertAllButton().isDisable());
            until(() -> document.canRedoProperty().get());

            JavaFxTestSupport.onFx(document::redo);
            until(() -> pane.differences().isEmpty());
            // Nothing of it was written.
            assertEquals("a\nx\nc\n", read(file));
        } finally {
            JavaFxTestSupport.onFx(() -> {
                stage[0].hide();
                document.dispose();
            });
        }
    }
}
