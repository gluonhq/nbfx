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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javafx.scene.layout.Region;
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
            assertEquals(1, pane.connectors().revertButtons().size());
            // Not saved: the file is as it was.
            assertEquals("a\nb\nc\n", read(file));
            pane.dispose();
        });
    }

    @Test
    public void aTextInMemoryIsNotEdited() throws Exception {
        FileObject left = inMemory("App.txt", "a\n");
        FileObject right = inMemory("App.txt", "b\n");
        DiffPane pane = JavaFxTestSupport.onFx(() -> new DiffPane(new DiffSource("HEAD", left), new DiffSource("Index", right)));
        until(() -> pane.differences().size() == 1);

        JavaFxTestSupport.onFx(() -> {
            assertFalse(pane.isEditable());
            assertNull(pane.editor());
            assertTrue(pane.revertAllButton().isDisable());
            pane.revert(0);
            pane.revertAll();
            pane.dispose();
        });
    }

    @Test
    public void aBlockIsRevertedAsAnEditThatSavingWrites() throws Exception {
        File file = onDisk("App.txt", "a\nx\nc\nd\nnew\n");
        DiffPane pane = pane("a\nb\nc\nd\n", file);
        until(() -> pane.differences().size() == 2);

        JavaFxTestSupport.onFx(() -> pane.connectors().revertButtons().get(1).fire());
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
        AtomicInteger asked = new AtomicInteger();
        DiffProvider provider = new DiffProvider() {
            @Override
            public List<DiffMode> modes() {
                return List.of(ALL);
            }

            @Override
            public List<DiffEntry> entries(DiffMode mode) {
                asked.incrementAndGet();
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
        });
        // Saved: the files are listed again - they may be changed otherwise now - and the diff
        // shown stays, as its texts are the same.
        DiffPane shown = JavaFxTestSupport.onFx(browser::pane);
        until(() -> asked.get() == 2 && !browser.isBusy());
        JavaFxTestSupport.onFx(() -> {
            assertTrue(shown == browser.pane());
            assertEquals("Second.txt", browser.selected().name());
            browser.dispose();
        });
    }

    private DiffEntry entry(File file, String committed) {
        return new DiffEntry(file.getName(), file.getName(), "Modified", DiffEntry.Kind.MODIFIED, null,
                () -> new DiffPair(new DiffSource("HEAD", inMemory(file.getName(), committed)),
                        new DiffSource("Working Tree", onDisk(file))));
    }

    private DiffPane pane(String left, File right) throws Exception {
        FileObject from = inMemory(right.getName(), left);
        FileObject to = onDisk(right);
        return JavaFxTestSupport.onFx(() -> new DiffPane(new DiffSource("HEAD", from), new DiffSource("Working Tree", to)));
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

    private static FileObject inMemory(String name, String text) throws IOException {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData(name);
        try (OutputStream out = file.getOutputStream()) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
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
}
