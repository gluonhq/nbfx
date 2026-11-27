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

import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.HPos;
import javafx.scene.layout.Region;
import org.junit.Before;
import org.junit.Test;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.EditorService;
import org.netbeans.api.nbfx.editor.LineBand;
import org.netbeans.junit.MockServices;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Verifies what the diff pane does with its two editors, over stand-ins for them that keep what
 * they are told: which files they are made for, where their line numbers go, the blocks marked,
 * which of them gets the keyboard, and that they are released.
 */
public class DiffPaneEditorsTest {

    /** The stand-ins made, in the order they were asked for. */
    private static final List<Editor> EDITORS = new CopyOnWriteArrayList<>();

    @Before
    public void installEditors() {
        EDITORS.clear();
        MockServices.setServices(Editors.class);
    }

    @Test
    public void theTwoEditorsAreMarkedAndTheLeftOneHasItsLineNumbersByTheOther() throws Exception {
        FileObject from = inMemory("a\nb\nc\n");
        FileObject to = inMemory("a\nx\nc\nd\n");
        DiffPane pane = JavaFxTestSupport.onFx(() -> new DiffPane(new DiffSource("HEAD", from),
                new DiffSource("Working Tree", to)));
        awaitComparison(pane);

        assertEquals(2, EDITORS.size());
        Editor left = EDITORS.get(0);
        Editor right = EDITORS.get(1);
        assertSame(from, left.file);
        assertSame(to, right.file);
        assertEquals(HPos.RIGHT, left.gutter.get());
        assertEquals(HPos.LEFT, right.gutter.get());
        // A changed line on both sides, and on the right the line added, with a rule on the left.
        assertEquals(DiffLines.leftBands(pane.differences()), left.bands);
        assertEquals(DiffLines.rightBands(pane.differences()), right.bands);
        assertEquals(2, left.bands.size());
        assertEquals(2, right.bands.size());
    }

    @Test
    public void aDiffAskedForAgainShowsWhatItIsOfByNow() throws Exception {
        FileObject working = inMemory("a\nx\nc\n");
        DiffPane pane = JavaFxTestSupport.onFx(() -> new DiffPane(new DiffSource("HEAD", inMemoryOrFail("a\nb\nc\n")),
                new DiffSource("Working Tree", working)));
        awaitComparison(pane);
        assertEquals(1, JavaFxTestSupport.onFx(pane::differences).size());
        Editor oldLeft = EDITORS.get(0);
        Editor oldRight = EDITORS.get(1);

        // Committed since: the last commit has what the working tree has.
        FileObject committed = inMemory("a\nx\nc\n");
        JavaFxTestSupport.onFx(() -> pane.setSources(new DiffSource("HEAD", committed),
                new DiffSource("Working Tree", working)));
        awaitComparison(pane);

        assertTrue(JavaFxTestSupport.onFx(pane::differences).isEmpty());
        assertEquals("No differences", JavaFxTestSupport.onFx(pane::summary));
        // New editors, of the new files; the old ones are released, and no mark is left.
        assertEquals(4, EDITORS.size());
        assertSame(committed, EDITORS.get(2).file);
        assertNotSame(oldLeft, EDITORS.get(2));
        assertEquals(1, oldLeft.disposed);
        assertEquals(1, oldRight.disposed);
        assertEquals(List.of(), EDITORS.get(2).bands);
        assertEquals(HPos.RIGHT, EDITORS.get(2).gutter.get());
    }

    @Test
    public void theSameSourcesKeepTheirEditors() throws Exception {
        FileObject from = inMemory("a\n");
        FileObject to = inMemory("b\n");
        DiffSource left = new DiffSource("HEAD", from);
        DiffSource right = new DiffSource("Working Tree", to);
        DiffPane pane = JavaFxTestSupport.onFx(() -> new DiffPane(left, right));
        awaitComparison(pane);

        // The file changed on disk meanwhile: compared again, in the editors that are there.
        try (OutputStream out = to.getOutputStream()) {
            out.write("a\n".getBytes(StandardCharsets.UTF_8));
        }
        JavaFxTestSupport.onFx(() -> pane.setSources(new DiffSource("HEAD", from), new DiffSource("Working Tree", to)));
        awaitComparison(pane);

        assertEquals(2, EDITORS.size());
        assertEquals(0, EDITORS.get(0).disposed);
        assertTrue(JavaFxTestSupport.onFx(pane::differences).isEmpty());
    }

    @Test
    public void theTabGivesTheKeyboardToTheTextComparedTo() throws Exception {
        DiffPane pane = JavaFxTestSupport.onFx(() -> new DiffPane(new DiffSource("HEAD", inMemoryOrFail("a\n")),
                new DiffSource("Working Tree", inMemoryOrFail("b\n"))));
        DiffDocument document = new DiffDocument("key", "A.txt [ Diff ]", pane);

        JavaFxTestSupport.onFx(document::requestFocus);

        assertEquals(0, EDITORS.get(0).focused);
        assertEquals(1, EDITORS.get(1).focused);
    }

    @Test
    public void aComparisonNobodyWaitsForIsNotShownAndTheEditorsAreReleased() throws Exception {
        DiffPane pane = JavaFxTestSupport.onFx(() -> {
            DiffPane made = new DiffPane(new DiffSource("HEAD", inMemoryOrFail("a\n")),
                    new DiffSource("Working Tree", inMemoryOrFail("b\n")));
            // Before the comparison asked for on making it has run.
            made.dispose();
            return made;
        });
        Thread.sleep(300);

        assertEquals("Comparing...", JavaFxTestSupport.onFx(pane::summary));
        assertTrue(JavaFxTestSupport.onFx(pane::differences).isEmpty());
        assertEquals(1, EDITORS.get(0).disposed);
        assertEquals(1, EDITORS.get(1).disposed);
        assertFalse(EDITORS.get(0).bands.size() > 0);
    }

    private static void awaitComparison(DiffPane pane) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (!"Comparing...".equals(JavaFxTestSupport.onFx(pane::summary))) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("the files were not compared");
    }

    private static FileObject inMemoryOrFail(String text) {
        try {
            return inMemory(text);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static FileObject inMemory(String text) throws Exception {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData("A.txt");
        try (OutputStream out = file.getOutputStream()) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    /** What an editor made for the pane was told. */
    private static final class Editor {
        private final FileObject file;
        private final Region node = new Region();
        private final ObjectProperty<HPos> gutter = new SimpleObjectProperty<>(HPos.LEFT);
        private volatile List<LineBand> bands = List.of();
        private volatile int focused;
        private volatile int disposed;
        private EditorDocument document;

        Editor(FileObject file) {
            this.file = file;
        }
    }

    /** Makes the stand-ins: documents that keep what the pane tells them, and do nothing else. */
    public static final class Editors implements EditorService {

        @Override
        public EditorDocument createDocument(FileObject file) {
            Editor editor = new Editor(file);
            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getNode":
                        return editor.node;
                    case "getFileObject":
                        return file;
                    case "gutterPositionProperty":
                        return editor.gutter;
                    case "setLineBands":
                        editor.bands = new ArrayList<>((List<LineBand>) args[0]);
                        return null;
                    case "requestFocus":
                        editor.focused++;
                        return null;
                    case "dispose":
                        editor.disposed++;
                        return null;
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    case "toString":
                        return "editor of " + file;
                    default:
                        if (method.isDefault()) {
                            return InvocationHandler.invokeDefault(proxy, method, args);
                        }
                        // What is left is asked of an editor by its tab, not by the pane.
                        return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                }
            };
            editor.document = (EditorDocument) Proxy.newProxyInstance(EditorDocument.class.getClassLoader(),
                    new Class<?>[] {EditorDocument.class}, handler);
            EDITORS.add(editor);
            return editor.document;
        }
    }
}
