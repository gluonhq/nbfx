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
package org.netbeans.modules.nbfx.platform.editor.codearea;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.After;
import org.junit.Test;
import org.netbeans.api.nbfx.editor.LineBand;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Verifies, with a real editor shown on the headless JavaFX toolkit, where the bands a view marks
 * lines with are drawn - at the ends of a text shorter than its view above all - and what the
 * editor tells of, and does for, the place at its top.
 */
public class CodeEditorBandsTest {

    private static final double VIEW_HEIGHT = 300;

    @After
    public void hideWindows() throws InterruptedException {
        JavaFxTestSupport.hideWindows();
    }

    @Test
    public void bandsOfAShortTextEndWithItsLastLineNotWithTheView() throws Exception {
        CodeEditor editor = show(text(3));
        // The second line, the last line, a band that names more lines than there are, and the
        // rule after the last line.
        setBands(editor, new LineBand(1, 1, "second"), new LineBand(2, 1, "last"), new LineBand(1, 100, "over"),
                new LineBand(3, 0, "end"));

        Bounds second = band(editor, "second");
        Bounds last = band(editor, "last");
        Bounds over = band(editor, "over");
        Bounds end = band(editor, "end");
        double line = second.getHeight();

        assertTrue("a line of " + line, line > 5 && line < VIEW_HEIGHT / 4);
        // The last line is as high as another, and right below it.
        assertEquals(line, last.getHeight(), 1.5);
        assertEquals(second.getMaxY(), last.getMinY(), 1.5);
        // A band over more lines than there are ends at the foot of the last.
        assertEquals(second.getMinY(), over.getMinY(), 0.5);
        assertEquals(last.getMaxY(), over.getMaxY(), 1.5);
        // And the rule after the last line is at that foot: far above the foot of the view.
        assertEquals(last.getMaxY(), end.getMinY() + end.getHeight() / 2, 1.5);
        assertTrue("rule at " + end.getMinY(), end.getMaxY() < VIEW_HEIGHT / 2);
    }

    @Test
    public void aBandGoesAcrossTheLineNumbersOnWhicheverSideTheyAre() throws Exception {
        CodeEditor editor = show(text(10));
        setBands(editor, new LineBand(2, 2, "block"));
        Bounds onTheLeft = band(editor, "block");
        Bounds leftGutter = gutter(editor);

        // On the left, as editors have them: the band starts with the numbers.
        assertEquals(leftGutter.getMinX(), onTheLeft.getMinX(), 1);

        // On the right, as the left of two texts side by side has them: it ends with them.
        JavaFxTestSupport.onFx(() -> editor.gutterPositionProperty().set(javafx.geometry.HPos.RIGHT));
        pulses(editor, 4);
        Bounds onTheRight = band(editor, "block");
        Bounds rightGutter = gutter(editor);
        assertTrue("gutter at " + rightGutter, rightGutter.getMinX() > 100);
        assertEquals(rightGutter.getMaxX(), onTheRight.getMaxX(), 1);
        assertEquals(onTheLeft.getWidth(), onTheRight.getWidth(), 2);
    }

    /** Where the line numbers of {@code editor} are, in the overlay the bands are drawn in. */
    private static Bounds gutter(CodeEditor editor) throws Exception {
        return JavaFxTestSupport.onFx(() -> {
            for (String side : List.of(".left-side", ".right-side")) {
                Node gutter = editor.codeArea.lookup(side);
                if (gutter != null && gutter.isVisible() && gutter.getScene() != null) {
                    return overlay(editor).sceneToLocal(gutter.localToScene(gutter.getLayoutBounds()));
                }
            }
            throw new AssertionError("no line numbers");
        });
    }

    @Test
    public void aTextOfOneLineHasItsBandAndItsRulesAtThatLine() throws Exception {
        CodeEditor editor = show("only\n".strip());
        setBands(editor, new LineBand(0, 1, "only"), new LineBand(0, 0, "before"), new LineBand(1, 0, "after"));

        Bounds only = band(editor, "only");
        Bounds before = band(editor, "before");
        Bounds after = band(editor, "after");

        assertTrue("a line of " + only.getHeight(), only.getHeight() > 5 && only.getHeight() < VIEW_HEIGHT / 4);
        assertEquals(only.getMinY(), before.getMinY() + before.getHeight() / 2, 1.5);
        assertEquals(only.getMaxY(), after.getMinY() + after.getHeight() / 2, 1.5);
        assertTrue("rule at " + after.getMinY(), after.getMaxY() < VIEW_HEIGHT / 2);
    }

    @Test
    public void bandsOutOfViewKeepTheirPlaceAndAreNotDrawn() throws Exception {
        CodeEditor editor = show(text(200));
        setBands(editor, new LineBand(0, 2, "top"), new LineBand(100, 1, "middle"), new LineBand(199, 1, "bottom"));
        Bounds clip = JavaFxTestSupport.onFx(() -> overlay(editor).getClip().getBoundsInParent());

        // At the top of the text: the first band is in view, the others are below it, out of what
        // is drawn - not a strip of them at its edge.
        assertTrue(clip.intersects(band(editor, "top")));
        assertFalse(clip.intersects(band(editor, "middle")));
        assertFalse(clip.intersects(band(editor, "bottom")));
        assertTrue(band(editor, "middle").getMinY() > clip.getMaxY());
        assertTrue(band(editor, "bottom").getMinY() > band(editor, "middle").getMaxY());

        // Scrolled to the middle: that one is in view, the first above, the last still below.
        scrollTo(editor, 95);
        assertTrue(clip.intersects(band(editor, "middle")));
        assertTrue(band(editor, "top").getMaxY() < clip.getMinY());
        assertTrue(band(editor, "bottom").getMinY() > clip.getMaxY());
    }

    @Test
    public void theTopOfTheViewIsToldAndSetToAFractionOfALine() throws Exception {
        CodeEditor editor = show(text(200));
        assertSame(JavaFxTestSupport.onFx(editor::topParagraphProperty), JavaFxTestSupport.onFx(editor::topParagraphProperty));
        assertEquals(0, top(editor), 0.05);

        scrollTo(editor, 40);
        assertEquals(40, top(editor), 0.1);
        scrollTo(editor, 60.5);
        assertEquals(60.5, top(editor), 0.1);
        // As far as the text goes: its last lines cannot be at the top.
        scrollTo(editor, 199);
        assertTrue("top " + top(editor), top(editor) > 150 && top(editor) < 199);
        scrollTo(editor, 0);
        assertEquals(0, top(editor), 0.05);
    }

    @Test
    public void anEditorWhoseScrollBarIsGivenNoRoomStillScrollsAndTellsItsTop() throws Exception {
        // As a view of two texts has the left of them: one scroll bar for both, the other's.
        CodeEditor editor = show(text(200));
        javafx.scene.control.ScrollBar bar = JavaFxTestSupport.onFx(() -> {
            editor.getNode().getScene().getStylesheets().add("data:text/css,"
                    + ".scroll-bar:vertical{-fx-min-width:0;-fx-pref-width:0;-fx-max-width:0;-fx-padding:0;-fx-opacity:0;}");
            return editor.codeArea.lookupAll(".scroll-bar").stream()
                    .map(javafx.scene.control.ScrollBar.class::cast)
                    .filter(found -> found.getOrientation() == javafx.geometry.Orientation.VERTICAL)
                    .findFirst().orElseThrow();
        });
        pulses(editor, 2);
        // Laid out anew, as it is from the start where the rule is there from the start.
        JavaFxTestSupport.onFx(() -> {
            javafx.stage.Window window = editor.getNode().getScene().getWindow();
            window.setWidth(window.getWidth() + 20);
        });
        pulses(editor, 4);
        assertEquals(0, JavaFxTestSupport.onFx(bar::getWidth), 0.5);
        // Still there for the editor to be moved through, and to tell there is somewhere to go.
        assertTrue(JavaFxTestSupport.onFx(bar::isVisible));
        scrollTo(editor, 40.5);
        assertEquals(40.5, top(editor), 0.1);
        scrollTo(editor, 0);
        assertEquals(0, top(editor), 0.05);
    }

    @Test
    public void aTextThatIsAllInViewStaysWhereItIs() throws Exception {
        CodeEditor editor = show(text(5));
        setBands(editor, new LineBand(1, 1, "second"));
        Bounds before = band(editor, "second");

        // Asked for every line another editor scrolls: nothing to scroll to, and nothing moves.
        for (double position : new double[] {1, 2.5, 4}) {
            // Not for a frame either: its scroll bar, which is not shown, is given no value - the
            // text would be drawn moved up by it until it is next laid out.
            JavaFxTestSupport.onFx(() -> {
                javafx.scene.control.ScrollBar bar = editor.codeArea.lookupAll(".scroll-bar").stream()
                        .map(javafx.scene.control.ScrollBar.class::cast)
                        .filter(found -> found.getOrientation() == javafx.geometry.Orientation.VERTICAL)
                        .findFirst().orElseThrow();
                double value = bar.getValue();
                editor.scrollToParagraph(position);
                assertEquals(value, bar.getValue(), 0);
            });
            scrollTo(editor, position);
            assertEquals(0, top(editor), 0.05);
            assertEquals(before.getMinY(), band(editor, "second").getMinY(), 0.5);
        }
    }

    @Test
    public void bandsFollowTheTextAsItChanges() throws Exception {
        CodeEditor editor = show(text(6));
        setBands(editor, new LineBand(4, 1, "fifth"), new LineBand(6, 0, "end"));
        Bounds fifth = band(editor, "fifth");
        Bounds end = band(editor, "end");

        // Two lines fewer: the band ends with the text, and the rule after it is at its foot.
        JavaFxTestSupport.onFx(() -> editor.codeArea.setText(text(4)));
        pulses(editor, 3);

        assertTrue(band(editor, "end").getMinY() < end.getMinY() - fifth.getHeight());
        assertTrue(band(editor, "fifth").getHeight() <= 1.5);
    }

    @Test
    public void aClosedEditorDoesNothingOfWhatWasPutOff() throws Exception {
        FileObject file = file(text(200));
        JavaFxTestSupport.onFx(() -> {
            // Never shown, so without a skin: bands and a scroll wait for one.
            CodeEditor editor = new CodeEditor(file);
            editor.setLineBands(List.of(new LineBand(1, 1, "band")));
            editor.scrollToParagraph(50);
            editor.dispose();
            // Shown after all, as a tab closed while it was being opened may be.
            Stage stage = new Stage();
            stage.setScene(new Scene(new javafx.scene.layout.StackPane(editor.getNode()), 400, VIEW_HEIGHT));
            stage.show();
        });
        CountDownLatch drawn = new CountDownLatch(3);
        Platform.runLater(() -> Platform.runLater(() -> Platform.runLater(drawn::countDown)));
        drawn.await(5, TimeUnit.SECONDS);

        // Nothing is watched, no band is drawn.
        JavaFxTestSupport.onFx(() -> { });
    }

    @Test
    public void aBandEndsAtALineThereCanBe() {
        new LineBand(Integer.MAX_VALUE, 0, "rule");
        assertThrows(IllegalArgumentException.class, () -> new LineBand(Integer.MAX_VALUE, 1, "band"));
        assertThrows(IllegalArgumentException.class, () -> new LineBand(2, Integer.MAX_VALUE, "band"));
        assertThrows(IllegalArgumentException.class, () -> new LineBand(-1, 1, "band"));
    }

    private static String text(int lines) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            text.append(i == 0 ? "" : "\n").append("line ").append(i);
        }
        return text.toString();
    }

    /** An editor of {@code text} in a window, laid out and drawn. */
    private static CodeEditor show(String text) throws Exception {
        FileObject file = file(text);
        CodeEditor editor = JavaFxTestSupport.onFx(() -> {
            CodeEditor made = new CodeEditor(file);
            Stage stage = new Stage();
            stage.setScene(new Scene(new javafx.scene.layout.StackPane(made.getNode()), 400, VIEW_HEIGHT));
            stage.show();
            // Asked for once, so that it is kept up to date.
            made.topParagraphProperty();
            return made;
        });
        pulses(editor, 4);
        return editor;
    }

    private static void setBands(CodeEditor editor, LineBand... bands) throws Exception {
        JavaFxTestSupport.onFx(() -> editor.setLineBands(List.of(bands)));
        pulses(editor, 3);
        await(() -> overlay(editor).getChildrenUnmodifiable().stream().allMatch(Node::isVisible));
    }

    private static void scrollTo(CodeEditor editor, double position) throws Exception {
        JavaFxTestSupport.onFx(() -> editor.scrollToParagraph(position));
        pulses(editor, 6);
    }

    private static double top(CodeEditor editor) throws Exception {
        return JavaFxTestSupport.onFx(() -> editor.topParagraphProperty().getValue().doubleValue());
    }

    /** Where the band of style class {@code name} is, in the overlay the bands are drawn in. */
    private static Bounds band(CodeEditor editor, String name) throws Exception {
        return JavaFxTestSupport.onFx(() -> {
            List<Node> found = new ArrayList<>(overlay(editor).lookupAll("." + name));
            assertEquals(name, 1, found.size());
            return found.get(0).getBoundsInParent();
        });
    }

    private static javafx.scene.Parent overlay(CodeEditor editor) {
        return (javafx.scene.Parent) editor.getNode().lookup(".line-bands");
    }

    /** Waits for {@code count} frames of the editor's window to be laid out and drawn. */
    private static void pulses(CodeEditor editor, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            CountDownLatch pulse = new CountDownLatch(1);
            JavaFxTestSupport.onFx(() -> {
                Scene scene = editor.getNode().getScene();
                Runnable[] once = new Runnable[1];
                once[0] = () -> {
                    scene.removePostLayoutPulseListener(once[0]);
                    pulse.countDown();
                };
                scene.addPostLayoutPulseListener(once[0]);
                Platform.requestNextPulse();
            });
            assertTrue("no frame was drawn", pulse.await(5, TimeUnit.SECONDS));
        }
    }

    private static void await(BooleanSupplier reached) throws Exception {
        long end = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < end) {
            if (JavaFxTestSupport.onFx(reached::getAsBoolean)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("not reached");
    }

    private static FileObject file(String text) throws IOException {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData("Bands.txt");
        try (OutputStream out = file.getOutputStream()) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
