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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.HPos;
import javafx.scene.AccessibleRole;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.Before;
import org.junit.Test;
import org.netbeans.api.nbfx.editor.ContentManager;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.EditorService;
import org.netbeans.api.nbfx.editor.LineBand;
import org.netbeans.junit.MockServices;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.filesystems.FileObject;

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
    public void theTwoEditorsShowWhatIsComparedAndAreMarked() throws Exception {
        DiffPane pane = pane(head("a\nb\nc\n"), working("a\nx\nc\nd\n"));
        awaitComparison(pane);

        assertEquals(2, EDITORS.size());
        Editor left = EDITORS.get(0);
        Editor right = EDITORS.get(1);
        // Each shows the text of its side - a copy of it, named as the file to be coloured as one.
        assertEquals("a\nb\nc\n", left.file.asText("UTF-8"));
        assertEquals("a\nx\nc\nd\n", right.file.asText("UTF-8"));
        assertEquals("A.txt", left.file.getNameExt());
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
        DiffPane pane = pane(head("a\nb\nc\n"), working("a\nx\nc\n"));
        awaitComparison(pane);
        assertEquals(1, JavaFxTestSupport.onFx(pane::differences).size());
        Editor oldLeft = EDITORS.get(0);
        Editor oldRight = EDITORS.get(1);

        // Committed since: the last commit has what the working tree has.
        JavaFxTestSupport.onFx(() -> pane.setSources(head("a\nx\nc\n"), working("a\nx\nc\n")));
        awaitComparison(pane);

        assertTrue(JavaFxTestSupport.onFx(pane::differences).isEmpty());
        assertEquals("No differences", JavaFxTestSupport.onFx(pane::summary));
        // New editors, of the new texts; the old ones are released, and no mark is left.
        assertEquals(4, EDITORS.size());
        assertEquals("a\nx\nc\n", EDITORS.get(2).file.asText("UTF-8"));
        assertEquals(1, oldLeft.disposed);
        assertEquals(1, oldRight.disposed);
        assertEquals(List.of(), EDITORS.get(2).bands);
        assertEquals(HPos.RIGHT, EDITORS.get(2).gutter.get());
    }

    @Test
    public void sidesThatShowTheSameKeepTheirEditors() throws Exception {
        DiffPane pane = pane(head("a\n"), working("b\n"));
        awaitComparison(pane);

        // Read again and found as it was: the same texts, whoever made the sources.
        JavaFxTestSupport.onFx(() -> pane.setSources(head("a\n"), working("b\n")));
        awaitComparison(pane);

        assertEquals(2, EDITORS.size());
        assertEquals(0, EDITORS.get(0).disposed);
        assertEquals(1, JavaFxTestSupport.onFx(pane::differences).size());
    }

    @Test
    public void aMissingSideHasAnEditorOfNoTextAndAnUnreadableOneHasNone() throws Exception {
        DiffPane added = pane(DiffSource.missing("HEAD", "A.txt"), working("new\n"));
        awaitComparison(added);

        // No file on the left: an editor with nothing in it, and the rule where the lines go.
        assertEquals(2, EDITORS.size());
        assertEquals("", EDITORS.get(0).file.asText("UTF-8"));
        assertEquals(DiffLines.leftBands(added.differences()), EDITORS.get(0).bands);
        assertEquals(1, EDITORS.get(0).bands.size());

        EDITORS.clear();
        DiffPane binary = pane(head("text\n"), DiffSource.unreadable("Working Tree", "A.txt", "A binary file."));
        // Only the side with a text has an editor; nothing is marked in it.
        assertEquals(1, EDITORS.size());
        assertEquals("text\n", EDITORS.get(0).file.asText("UTF-8"));
        assertEquals(List.of(), EDITORS.get(0).bands);
        assertEquals("Working Tree: A binary file.", JavaFxTestSupport.onFx(binary::summary));
        // And the tab still takes the keyboard without one.
        JavaFxTestSupport.onFx(() -> new DiffDocument("key", "A.txt [ Diff ]", binary).requestFocus());
        JavaFxTestSupport.onFx(binary::dispose);
        assertEquals(1, EDITORS.get(0).disposed);
    }

    @Test
    public void everyBlockIsJoinedAcrossTheTwoTexts() throws Exception {
        DiffPane pane = pane(head("a\nb\nc\n"), working("a\nx\nc\nd\n"));
        JavaFxTestSupport.onFx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 600, 300));
            stage.show();
        });
        awaitComparison(pane);

        // Drawn from the regions the editors made for the bands of this comparison.
        awaitConnectors(pane, 2);
        assertEquals("1/2", JavaFxTestSupport.onFx(pane::counter));
        // The block the view is at is marked on both sides as well as between them, and no other.
        assertEquals(List.of(List.of(true, false), List.of(true, false)), JavaFxTestSupport.onFx(() -> currentBands()));
        JavaFxTestSupport.onFx(() -> pane.select(1));
        assertEquals(List.of(List.of(false, true), List.of(false, true)), JavaFxTestSupport.onFx(() -> currentBands()));

        // Compared again: joined anew, to the regions made anew.
        JavaFxTestSupport.onFx(() -> pane.setSources(head("a\nb\nc\n"), working("a\nb\nc\nd\ne\n")));
        awaitComparison(pane);
        awaitConnectors(pane, 1);
        JavaFxTestSupport.onFx(() -> pane.setSources(head("a\n"), working("a\n")));
        awaitComparison(pane);
        awaitConnectors(pane, 0);
        JavaFxTestSupport.hideWindows();
    }

    /** For each of the two editors made last, which of its bands are marked as of the current block. */
    private static List<List<Boolean>> currentBands() {
        return EDITORS.subList(EDITORS.size() - 2, EDITORS.size()).stream()
                .map(editor -> editor.overlay.getChildren().stream()
                        .map(region -> region.getStyleClass().contains("diff-current")).toList())
                .toList();
    }

    private static void awaitConnectors(DiffPane pane, int count) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (JavaFxTestSupport.onFx(() -> pane.connectors().size()) == count) {
                return;
            }
            Thread.sleep(50);
        }
        assertEquals(count, (int) JavaFxTestSupport.onFx(() -> pane.connectors().size()));
    }

    @Test
    public void goingToABlockPutsTheLeftTextBesideWhereTheRightOneLanded() throws Exception {
        // Twenty lines, the last of them changed: the right text cannot bring that one to its top.
        String same = "line\n".repeat(19);
        DiffPane pane = pane(head("x\n" + same + "old\n"), working(same + "new\n"));
        awaitComparison(pane);
        Editor left = EDITORS.get(0);
        Editor right = EDITORS.get(1);

        JavaFxTestSupport.onFx(() -> {
            right.lastTop = 5;
            left.scrolledTo.clear();
            right.scrolledTo.clear();
            pane.select(1);

            // Asked for the lines above the block, it lands where its text ends.
            assertEquals(List.of(16.0), right.scrolledTo);
            assertEquals(5.0, right.top.get(), 0);
            // And the left one is put beside that, a line further for the line deleted at its top -
            // not beside the place the right one was asked for and did not get to.
            assertEquals(6.0, left.scrolledTo.get(left.scrolledTo.size() - 1), 1e-9);
            assertFalse(left.scrolledTo.contains(17.0));
        });
    }

    @Test
    public void theKeysGoFromOneBlockToTheNextAndTheMarksAreNamedAndChosenWithTheKeyboard() throws Exception {
        DiffPane pane = pane(head("a\nb\nc\nd\ne\n"), working("a\nx\nc\nd\ny\nz\n"));
        awaitComparison(pane);

        JavaFxTestSupport.onFx(() -> {
            assertEquals("1/2", pane.counter());
            // Ctrl+Period and Ctrl+Comma, wherever in the diff the keyboard is.
            pane.fireEvent(key(KeyCode.PERIOD, true));
            assertEquals(1, pane.current());
            pane.fireEvent(key(KeyCode.PERIOD, true));
            assertEquals(1, pane.current());
            pane.fireEvent(key(KeyCode.COMMA, true));
            assertEquals(0, pane.current());
            // Not the keys alone.
            pane.fireEvent(key(KeyCode.PERIOD, false));
            assertEquals(0, pane.current());
            // The buttons are reached with Tab, and say what keys do the same.
            assertTrue(pane.nextButton().isFocusTraversable());
            assertTrue(pane.nextButton().getTooltip().getText().startsWith("Next Difference ("));

            // The marks of the overview: named by what they go to, and chosen with Enter or Space.
            List<Region> marks = pane.overview().marks();
            assertEquals(2, marks.size());
            assertEquals("Go to difference 2 of 2: Lines 5 to 6 changed", marks.get(1).getAccessibleText());
            assertEquals(AccessibleRole.BUTTON, marks.get(1).getAccessibleRole());
            assertTrue(marks.get(1).isFocusTraversable());
            marks.get(1).fireEvent(key(KeyCode.ENTER, false));
            assertEquals(1, pane.current());
            marks.get(0).fireEvent(key(KeyCode.SPACE, false));
            assertEquals(0, pane.current());
            // Told which is the current one without being made again.
            pane.select(1);
            assertSame(marks.get(1), pane.overview().marks().get(1));
            assertTrue(marks.get(1).getStyleClass().contains("diff-current"));
            assertFalse(marks.get(0).getStyleClass().contains("diff-current"));
        });
    }

    @Test
    public void aComparisonMadeAgainStaysAtTheBlockWhereItWasAndGivesUpItsStripsWithNoBlock() throws Exception {
        DiffPane pane = pane(head("a\nb\nc\nd\ne\n"), working("a\nb\nc\nd\nE\n"));
        awaitComparison(pane);
        JavaFxTestSupport.onFx(() -> {
            assertEquals(0, pane.current());
            assertTrue(pane.stripsShown());
        });

        // Another block comes above the one the view is at: it is still at that one, now the second.
        JavaFxTestSupport.onFx(() -> pane.setSources(head("a\nb\nc\nd\ne\n"), working("A\nb\nc\nd\nE\n")));
        awaitComparison(pane);
        JavaFxTestSupport.onFx(() -> {
            assertEquals(2, pane.differences().size());
            assertEquals(1, pane.current());
            assertEquals("2/2", pane.counter());
        });

        // No block left: nothing to join or to mark, and the strips give their room to the texts.
        JavaFxTestSupport.onFx(() -> pane.setSources(head("a\n"), working("a\n")));
        awaitComparison(pane);
        JavaFxTestSupport.onFx(() -> {
            assertEquals(-1, pane.current());
            assertEquals("", pane.counter());
            assertFalse(pane.stripsShown());
        });
        JavaFxTestSupport.onFx(() -> pane.setSources(head("a\n"), working("b\n")));
        awaitComparison(pane);
        assertTrue(JavaFxTestSupport.onFx(pane::stripsShown));
    }

    @Test
    public void aClosedDiffLetsGoOfItsEditorsAndOfTheWindow() throws Exception {
        DiffPane pane = pane(head("a\nb\n"), working("a\nx\n"));
        JavaFxTestSupport.onFx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 600, 300));
            stage.show();
        });
        awaitComparison(pane);
        awaitConnectors(pane, 1);
        Editor left = EDITORS.get(0);
        assertTrue(JavaFxTestSupport.onFx(pane::connectorsListening));
        assertEquals(List.of(List.of(true), List.of(true)), JavaFxTestSupport.onFx(() -> currentBands()));

        // Closed, but still in the window, as a tab is while it is being closed.
        JavaFxTestSupport.onFx(pane::dispose);

        assertFalse(JavaFxTestSupport.onFx(pane::connectorsListening));
        assertEquals(0, (int) JavaFxTestSupport.onFx(() -> pane.connectors().size()));
        // A band that moves is no longer followed: nothing is drawn for it.
        JavaFxTestSupport.onFx(() -> left.overlay.getChildren().forEach(region -> region.setLayoutY(40)));
        Thread.sleep(200);
        assertEquals(0, (int) JavaFxTestSupport.onFx(() -> pane.connectors().size()));
        JavaFxTestSupport.hideWindows();
    }

    private static KeyEvent key(KeyCode code, boolean control) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, control, false, false);
    }

    @Test
    public void theLeftTextFollowsTheRightOneAndNotTheOtherWayRound() throws Exception {
        // A line added on the right after the second: from there on the left is a line behind.
        DiffPane pane = pane(head("a\nb\nc\nd\n"), working("a\nb\nx\nc\nd\n"));
        awaitComparison(pane);
        Editor left = EDITORS.get(0);
        Editor right = EDITORS.get(1);
        JavaFxTestSupport.onFx(() -> {
            left.scrolledTo.clear();
            right.scrolledTo.clear();

            // The right one scrolled: the left one is brought beside it, to the fraction of a line.
            right.top.set(1.5);
            right.top.set(4.25);
            assertEquals(List.of(1.5, 3.25), left.scrolledTo);
            assertEquals(List.of(), right.scrolledTo);

            // The left one scrolled by itself, with the wheel: the right one stays where it is.
            left.top.set(2);
            left.top.set(0);
            assertEquals(List.of(), right.scrolledTo);
            assertEquals(List.of(1.5, 3.25), left.scrolledTo);

            // Until the right one is scrolled again, which brings the left one back beside it.
            right.top.set(4);
            assertEquals(List.of(1.5, 3.25, 3.0), left.scrolledTo);
        });
    }

    @Test
    public void copyIsOfTheTextTheKeyboardIsIn() throws Exception {
        DiffPane pane = pane(head("left\n"), working("right\n"));
        DiffDocument document = JavaFxTestSupport.onFx(() -> new DiffDocument("key", "A.txt [ Diff ]", pane));
        Editor left = EDITORS.get(0);
        Editor right = EDITORS.get(1);

        JavaFxTestSupport.onFx(() -> {
            // The right text at first; nothing selected, nothing to copy.
            assertFalse(document.hasSelectionProperty().get());
            assertEquals("", document.getSelectedText());

            right.selection.set(true);
            assertTrue(document.hasSelectionProperty().get());
            assertEquals("selected in right", document.getSelectedText());
            document.copy();
            assertEquals(1, right.copied);
            assertEquals(0, left.copied);

            // Nothing is cut from a diff, or pasted into one.
            document.cut();
            document.paste();
            assertFalse(document.editableProperty().get());
            // A diff shows two texts, not where the caret is in one.
            assertFalse(left.breadcrumbs.get());
            assertFalse(right.breadcrumbs.get());
        });
    }

    @Test
    public void whatDiffersIsSaidAndNotOnlyColoured() throws Exception {
        DiffPane pane = pane(head("a\nb\nc\n"), working("a\nx\nc\nd\n"));
        awaitComparison(pane);

        JavaFxTestSupport.onFx(() -> {
            assertEquals("HEAD", EDITORS.get(0).node.getAccessibleText());
            assertEquals("Line 2 changed; Lines added after line 3", EDITORS.get(0).node.getAccessibleHelp());
            assertEquals("Working Tree", EDITORS.get(1).node.getAccessibleText());
            assertEquals("Line 2 changed; Line 4 added", EDITORS.get(1).node.getAccessibleHelp());
        });
        // Compared again and found the same: said so.
        JavaFxTestSupport.onFx(() -> pane.setSources(head("a\n"), working("a\n")));
        awaitComparison(pane);
        assertEquals("No differences", JavaFxTestSupport.onFx(() -> EDITORS.get(2).node.getAccessibleHelp()));
    }

    @Test
    public void aDiffAskedForTwiceInARowIsOpenedOnce() throws Exception {
        MockServices.setServices(Editors.class, Content.class);
        Content.OPENED.clear();
        Object key = new Object();

        JavaFxTestSupport.onFx(() -> {
            // Before the editor area has shown the first: it is still the one opened.
            DiffView.open(key, "A.txt", null, head("a\n"), working("b\n"));
            DiffView.open(key, "A.txt", null, head("a\n"), working("c\n"));
        });

        assertEquals(2, Content.OPENED.size());
        assertSame(Content.OPENED.get(0), Content.OPENED.get(1));
        DiffDocument document = (DiffDocument) Content.OPENED.get(0);
        assertEquals("A.txt [ Diff ]", document.getTitle());
        // And it shows what was asked for last.
        awaitComparison(document.pane());
        assertEquals("c\n", EDITORS.get(EDITORS.size() - 1).file.asText("UTF-8"));

        // Closed, it is forgotten: asked for again, another one is opened.
        JavaFxTestSupport.onFx(document::dispose);
        assertEquals(null, JavaFxTestSupport.onFx(() -> DiffView.openFor(key)));
        JavaFxTestSupport.onFx(() -> DiffView.open(key, "A.txt", null, head("a\n"), working("b\n")));
        assertEquals(3, Content.OPENED.size());
        assertNotSame(document, Content.OPENED.get(2));
        JavaFxTestSupport.onFx(() -> Content.OPENED.get(2).dispose());
    }

    @Test
    public void theTabGivesTheKeyboardToTheTextComparedTo() throws Exception {
        DiffPane pane = pane(head("a\n"), working("b\n"));
        DiffDocument document = new DiffDocument("key", "A.txt [ Diff ]", pane);

        JavaFxTestSupport.onFx(document::requestFocus);

        assertEquals(0, EDITORS.get(0).focused);
        assertEquals(1, EDITORS.get(1).focused);
    }

    @Test
    public void aComparisonNobodyWaitsForIsNotShownAndTheEditorsAreReleased() throws Exception {
        DiffPane pane = JavaFxTestSupport.onFx(() -> {
            DiffPane made = new DiffPane(head("a\n"), working("b\n"));
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

    private static DiffSource head(String text) {
        return DiffSource.of("HEAD", "A.txt", text);
    }

    private static DiffSource working(String text) {
        return DiffSource.of("Working Tree", "A.txt", text);
    }

    private static DiffPane pane(DiffSource left, DiffSource right) throws Exception {
        return JavaFxTestSupport.onFx(() -> new DiffPane(left, right));
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

    /** What an editor made for the pane was told. */
    private static final class Editor {
        private final FileObject file;
        private final Pane overlay = new Pane();
        private final Pane node = new Pane(overlay);
        private final ObjectProperty<HPos> gutter = new SimpleObjectProperty<>(HPos.LEFT);
        private final SimpleDoubleProperty top = new SimpleDoubleProperty(0);
        private final SimpleBooleanProperty breadcrumbs = new SimpleBooleanProperty(true);
        private final ReadOnlyBooleanWrapper selection = new ReadOnlyBooleanWrapper(false);
        private volatile int copied;
        private final List<Double> scrolledTo = new CopyOnWriteArrayList<>();
        /** The furthest place this editor can have at its top; any, unless a test says. */
        private volatile double lastTop = Double.MAX_VALUE;
        private volatile List<LineBand> bands = List.of();
        private volatile int focused;
        private volatile int disposed;
        private EditorDocument document;

        /** Places {@code regions} after the layout of the next frame the editor is shown in. */
        void place(List<Region> regions) {
            Scene scene = node.getScene();
            if (scene == null) {
                Platform.runLater(() -> {
                    if (overlay.getChildren().containsAll(regions) && disposed == 0) {
                        place(regions);
                    }
                });
                return;
            }
            Runnable[] once = new Runnable[1];
            once[0] = () -> {
                scene.removePostLayoutPulseListener(once[0]);
                for (int i = 0; i < regions.size(); i++) {
                    regions.get(i).resizeRelocate(0, 20 * i, 50, 10);
                    regions.get(i).setVisible(true);
                }
            };
            scene.addPostLayoutPulseListener(once[0]);
            Platform.requestNextPulse();
        }

        Editor(FileObject file) {
            this.file = file;
            overlay.getStyleClass().add("line-bands");
        }
    }

    /** An editor area that keeps the documents it is told to open, and shows none. */
    public static final class Content implements ContentManager {

        static final List<EditorDocument> OPENED = new CopyOnWriteArrayList<>();

        @Override
        public void openDocument(EditorDocument document, javafx.scene.Node graphic) {
            OPENED.add(document);
        }

        @Override
        public void openFile(FileObject file, javafx.scene.Node graphic) {
        }

        @Override
        public void openFile(FileObject file, int start, int end) {
        }

        @Override
        public void closeAll() {
        }

        @Override
        public List<EditorDocument> getMainPaneDocuments() {
            return List.of();
        }

        @Override
        public javafx.beans.value.ObservableValue<EditorDocument> mainPaneActiveDocument() {
            return new SimpleObjectProperty<>();
        }

        @Override
        public FileObject getActiveFile() {
            return null;
        }

        @Override
        public void selectFile(FileObject file) {
        }

        @Override
        public void focusActiveEditor() {
        }

        @Override
        public void closeFile(FileObject file) {
        }

        @Override
        public void closeDocument(EditorDocument document) {
        }

        @Override
        public EditorDocument cloneDocument(EditorDocument document) {
            return null;
        }

        @Override
        public EditorDocument documentForFile(FileObject file) {
            return null;
        }

        @Override
        public void deleteFile(FileObject file) {
        }

        @Override
        public void deleteFolder(FileObject folder) {
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
                    case "topParagraphProperty":
                        return editor.top;
                    case "showBreadcrumbsProperty":
                        return editor.breadcrumbs;
                    case "hasSelectionProperty":
                        return editor.selection.getReadOnlyProperty();
                    case "getSelectedText":
                        return editor.selection.get() ? "selected in " + editor.file.asText("UTF-8").strip() : "";
                    case "copy":
                        editor.copied++;
                        return null;
                    case "scrollToParagraph":
                        editor.scrolledTo.add((Double) args[0]);
                        // As far as the text goes, as an editor lands.
                        editor.top.set(Math.min((Double) args[0], editor.lastTop));
                        return null;
                    case "setLineBands":
                        editor.bands = new ArrayList<>((List<LineBand>) args[0]);
                        // As an editor does: a region for each band, new ones each time, under
                        // the node that what is drawn beside the editor finds them by.
                        List<Region> regions = new ArrayList<>();
                        for (int i = 0; i < editor.bands.size(); i++) {
                            Region region = new Region();
                            region.setManaged(false);
                            region.setVisible(false);
                            regions.add(region);
                        }
                        editor.overlay.getChildren().setAll(regions);
                        // Placed and shown as an editor does it: in a frame, once the editor is
                        // laid out where it is - until then a band is nowhere, and nothing is
                        // joined to it.
                        editor.place(regions);
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
