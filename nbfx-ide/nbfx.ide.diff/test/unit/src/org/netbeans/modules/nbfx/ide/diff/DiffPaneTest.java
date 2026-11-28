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

import java.util.List;
import org.netbeans.junit.MockServices;
import org.junit.Before;
import org.junit.Test;
import org.netbeans.api.nbfx.diff.Difference;
import org.netbeans.api.nbfx.diff.Difference.Kind;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies what the diff pane makes of two files on the headless JavaFX toolkit. There is no editor
 * service on the test path, so the pane has no editors to fill: the comparison and its summary are
 * what is checked here, the marking of the lines in {@link DiffLinesTest}.
 */
public class DiffPaneTest {

    /** Without editors: these tests are of what is around the two texts, not of the texts. */
    @Before
    public void noEditors() {
        MockServices.setServices(NoEditors.class);
    }

    @Test
    public void comparesTheTwoFilesAndTellsHowManyBlocksDiffer() throws Exception {
        DiffPane pane = pane("a\nb\nc\n", "a\nx\nc\nd\n");

        awaitComparison(pane);

        assertEquals(List.of(new Difference(Kind.CHANGE, 1, 1, 1, 1), new Difference(Kind.ADD, 3, 0, 3, 1)),
                JavaFxTestSupport.onFx(pane::differences));
        assertEquals("2 differences", JavaFxTestSupport.onFx(pane::summary));
    }

    @Test
    public void goesFromOneBlockToTheNextAndCountsThem() throws Exception {
        DiffPane pane = pane("a\nb\nc\nd\ne\n", "a\nx\nc\nd\ny\n");
        awaitComparison(pane);

        JavaFxTestSupport.onFx(() -> {
            // Opens at the first block.
            assertEquals(0, pane.current());
            assertEquals("1/2", pane.counter());
            assertTrue(pane.previousButton().isDisable());
            assertFalse(pane.nextButton().isDisable());

            pane.nextButton().fire();
            assertEquals(1, pane.current());
            assertEquals("2/2", pane.counter());
            assertFalse(pane.previousButton().isDisable());
            assertTrue(pane.nextButton().isDisable());

            pane.previousButton().fire();
            assertEquals("1/2", pane.counter());
        });
    }

    @Test
    public void overviewMarksEveryBlockAndGoesToTheOneClicked() throws Exception {
        DiffPane pane = pane("a\nb\nc\nd\ne\n", "a\nx\nc\nd\ny\n");
        awaitComparison(pane);

        JavaFxTestSupport.onFx(() -> {
            assertEquals(2, pane.overview().marks().size());
            assertTrue(pane.overview().marks().get(0).getStyleClass().contains("diff-current"));

            pane.overview().marks().get(1).getOnMouseClicked().handle(null);

            assertEquals(1, pane.current());
            assertTrue(pane.overview().marks().get(1).getStyleClass().contains("diff-current"));
        });
    }

    @Test
    public void theUnifiedDiffNamesTheSidesByTheirPath() throws Exception {
        DiffPane pane = JavaFxTestSupport.onFx(() -> new DiffPane(
                DiffSource.of("HEAD", "App.java", "a\nb\n").withPath("src/App.java"),
                DiffSource.of("Working Tree", "App.java", "a\nx\n").withPath("src/App.java")));
        awaitComparison(pane);

        JavaFxTestSupport.onFx(() -> assertTrue(pane.unified(),
                pane.unified().startsWith("--- a/src/App.java\n+++ b/src/App.java\n@@ ")));
    }

    @Test
    public void aSideWithNoFileIsNamedAsGitNamesItAndOneThatCannotBeComparedHasNoUnifiedDiff() throws Exception {
        DiffPane added = JavaFxTestSupport.onFx(() -> new DiffPane(
                DiffSource.missing("HEAD", "New.java").withPath("src/New.java"),
                DiffSource.of("Working Tree", "New.java", "a\nb\n").withPath("src/New.java")));
        awaitComparison(added);
        JavaFxTestSupport.onFx(() -> assertEquals("--- /dev/null\n+++ b/src/New.java\n@@ -0,0 +1,2 @@\n+a\n+b\n",
                added.unified()));

        DiffPane binary = JavaFxTestSupport.onFx(() -> new DiffPane(
                DiffSource.unreadable("HEAD", "logo.png", "Binary file").withPath("logo.png"),
                DiffSource.of("Working Tree", "logo.png", "text\n").withPath("logo.png")));
        JavaFxTestSupport.onFx(() -> {
            assertEquals("", binary.unified());
            // Its tab can be shown all the same, and shows nothing.
            binary.setTextual(true);
            assertTrue(binary.isTextual());
        });

        // Asked for again with other texts: the unified diff is of those.
        JavaFxTestSupport.onFx(() -> added.setSources(DiffSource.of("HEAD", "New.java", "a\n"),
                DiffSource.of("Working Tree", "New.java", "b\n")));
        awaitComparison(added);
        JavaFxTestSupport.onFx(() -> assertEquals("--- HEAD\n+++ Working Tree\n@@ -1,1 +1,1 @@\n-a\n+b\n", added.unified()));
    }

    @Test
    public void theTextualTabIsOfTheUnifiedDiff() throws Exception {
        DiffPane pane = pane("a\nb\nc\n", "a\nx\nc\n");
        awaitComparison(pane);

        JavaFxTestSupport.onFx(() -> {
            assertEquals("--- HEAD\n+++ Working Tree\n@@ -1,3 +1,3 @@\n a\n-b\n+x\n c\n", pane.unified());
            assertTrue(pane.graphicalTab().isSelected());
            assertFalse(pane.isTextual());

            pane.textualTab().fire();
            assertTrue(pane.isTextual());
            // The blocks are still gone through, and a click on the tab shown leaves it shown.
            assertEquals("1/1", pane.counter());
            pane.textualTab().fire();
            assertTrue(pane.isTextual());

            pane.setTextual(false);
            assertTrue(pane.graphicalTab().isSelected());
        });
    }

    @Test
    public void sameFilesHaveNoDifferences() throws Exception {
        DiffPane pane = pane("same\n", "same\n");

        awaitComparison(pane);

        assertTrue(JavaFxTestSupport.onFx(pane::differences).isEmpty());
        assertEquals("No differences", JavaFxTestSupport.onFx(pane::summary));
        JavaFxTestSupport.onFx(() -> {
            assertEquals(-1, pane.current());
            assertEquals("", pane.counter());
            assertTrue(pane.previousButton().isDisable());
            assertTrue(pane.nextButton().isDisable());
        });
    }

    @Test
    public void aFileThatIsNotThereIsNotAnEmptyOne() throws Exception {
        // Added since: every line of it, and not a blank line changed into them.
        DiffPane added = pane(DiffSource.missing("HEAD", "A.txt"), DiffSource.of("Working Tree", "A.txt", "a\nb\n"));
        awaitComparison(added);
        assertEquals(List.of(new Difference(Kind.ADD, 0, 0, 0, 3)), JavaFxTestSupport.onFx(added::differences));
        assertEquals("Added: there is no file to compare with", JavaFxTestSupport.onFx(added::summary));

        // An empty file added is a file added, of its one empty line: not "No differences".
        DiffPane addedEmpty = pane(DiffSource.missing("HEAD", "A.txt"), DiffSource.of("Working Tree", "A.txt", ""));
        awaitComparison(addedEmpty);
        assertEquals(List.of(new Difference(Kind.ADD, 0, 0, 0, 1)), JavaFxTestSupport.onFx(addedEmpty::differences));
        assertEquals("Added: there is no file to compare with", JavaFxTestSupport.onFx(addedEmpty::summary));

        DiffPane deleted = pane(DiffSource.of("HEAD", "A.txt", "a\n"), DiffSource.missing("Working Tree", "A.txt"));
        awaitComparison(deleted);
        assertEquals(List.of(new Difference(Kind.DELETE, 0, 2, 0, 0)), JavaFxTestSupport.onFx(deleted::differences));
        assertEquals("Deleted: the file is no longer there", JavaFxTestSupport.onFx(deleted::summary));

        // Whereas an empty file compared with an empty file is the same.
        DiffPane empty = pane("", "");
        awaitComparison(empty);
        assertEquals("No differences", JavaFxTestSupport.onFx(empty::summary));
    }

    @Test
    public void aSideThatCannotBeComparedSaysWhyAndNothingIsCompared() throws Exception {
        DiffPane pane = pane(DiffSource.of("HEAD", "image.png", "text once\n"),
                DiffSource.unreadable("Working Tree", "image.png", "A binary file."));

        // Said at once: there is nothing to wait for.
        assertEquals("Working Tree: A binary file.", JavaFxTestSupport.onFx(pane::summary));
        assertTrue(JavaFxTestSupport.onFx(pane::differences).isEmpty());

        // Readable again, it is compared.
        JavaFxTestSupport.onFx(() -> pane.setSources(DiffSource.of("HEAD", "image.png", "text once\n"),
                DiffSource.of("Working Tree", "image.png", "text twice\n")));
        awaitComparison(pane);
        assertEquals("1 difference", JavaFxTestSupport.onFx(pane::summary));
    }

    @Test
    public void aSourceIsOneOfThreeThings() {
        DiffSource text = DiffSource.of("HEAD", "A.txt", "");
        DiffSource missing = DiffSource.missing("HEAD", "A.txt");
        DiffSource unreadable = DiffSource.unreadable("HEAD", "A.txt", "Too large.");

        assertEquals(List.of(""), text.lines());
        assertEquals(List.of(), missing.lines());
        assertTrue(text.isComparable() && !text.isMissing());
        assertTrue(missing.isComparable() && missing.isMissing());
        assertTrue(!unreadable.isComparable() && !unreadable.isMissing());
        // Equal when they would show the same, whoever made them.
        assertEquals(text, DiffSource.of("HEAD", "A.txt", ""));
        assertNotEquals(text, missing);
        assertNotEquals(text, DiffSource.of("HEAD", "A.txt", "\n"));
        assertThrows(IllegalArgumentException.class, () -> new DiffSource("HEAD", "A.txt", "text", "and a problem", null, null));
        // Where it is named differently in the unified diff, it does not show the same.
        assertNotEquals(text, text.withPath("src/A.txt"));
        assertEquals("a/src/A.txt", text.withPath("src/A.txt").unifiedName("a/"));
        assertEquals("HEAD", text.unifiedName("a/"));
        assertEquals("/dev/null", missing.withPath("src/A.txt").unifiedName("a/"));
        assertThrows(NullPointerException.class, () -> DiffSource.of("HEAD", "A.txt", null));
    }

    @Test
    public void aDiffIsTheEditorOfNoFile() throws Exception {
        DiffPane pane = pane("a\n", "b\n");
        DiffDocument document = new DiffDocument("key", "A.java [ Diff ]", pane);

        assertNull(document.getFileObject());
        assertEquals("A.java [ Diff ]", document.getTitle());
        assertEquals(pane, document.getNode());
        assertEquals(false, document.isModified());
        assertEquals(false, document.editableProperty().get());
        // Two texts handed over: nothing to open them again from.
        assertNull(document.getSavedState());
        JavaFxTestSupport.onFx(document::dispose);
    }

    private static DiffPane pane(String left, String right) throws Exception {
        return pane(DiffSource.of("HEAD", "A.txt", left), DiffSource.of("Working Tree", "A.txt", right));
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

    @Test
    public void textsThatDifferOnlyInHowTheirLinesEndAreNotSaidToBeTheSame() throws Exception {
        DiffPane pane = pane("a\nb\n", "a\r\nb\r\n");
        awaitComparison(pane);
        JavaFxTestSupport.onFx(() -> {
            assertTrue(pane.differences().isEmpty());
            assertEquals("Only line endings differ: LF in HEAD, CRLF in Working Tree", pane.summary());
        });

        assertEquals("LF", DiffLines.lineEnding("a\nb\n"));
        assertEquals("CRLF", DiffLines.lineEnding("a\r\nb"));
        assertEquals("CR", DiffLines.lineEnding("a\rb\r"));
        assertEquals("mixed", DiffLines.lineEnding("a\nb\r\n"));
        assertEquals("none", DiffLines.lineEnding("a"));
    }

    @Test
    public void whatTheTextsDoNotShowIsSaidInPlaceOfNoDifferencesOrWithThem() throws Exception {
        DiffPane same = JavaFxTestSupport.onFx(() -> new DiffPane(DiffSource.of("HEAD", "run.sh", "echo\n"),
                DiffSource.of("Working Tree", "run.sh", "echo\n"), "File mode changed: 100644 \u2192 100755", true));
        awaitComparison(same);
        JavaFxTestSupport.onFx(() -> assertEquals("File mode changed: 100644 \u2192 100755", same.summary()));

        JavaFxTestSupport.onFx(() -> same.setSources(DiffSource.of("HEAD", "run.sh", "echo\n"),
                DiffSource.of("Working Tree", "run.sh", "echo hi\n"), "File mode changed: 100644 \u2192 100755"));
        awaitComparison(same);
        JavaFxTestSupport.onFx(() -> assertEquals("1 difference. File mode changed: 100644 \u2192 100755", same.summary()));

        // Asked for again with nothing more to say: said no longer.
        JavaFxTestSupport.onFx(() -> same.setSources(DiffSource.of("HEAD", "run.sh", "echo\n"),
                DiffSource.of("Working Tree", "run.sh", "echo\n")));
        awaitComparison(same);
        JavaFxTestSupport.onFx(() -> assertEquals("No differences", same.summary()));
    }

    @Test
    public void aDiffWhoseContentFailsToCloseIsForgottenAllTheSame() throws Exception {
        boolean[] forgotten = new boolean[1];
        DiffDocument document = JavaFxTestSupport.onFx(
                () -> new DiffDocument("key", "A.txt [ Diff ]", new Failing(), () -> forgotten[0] = true));

        // The failure is still told, to who closes it.
        AssertionError failure = assertThrows(AssertionError.class, () -> JavaFxTestSupport.onFx(document::dispose));
        assertTrue(String.valueOf(failure.getCause()), failure.getCause() instanceof IllegalStateException);
        assertTrue(forgotten[0]);
    }

    /** The content of a diff that cannot be closed. */
    private static final class Failing extends javafx.scene.layout.Region implements DiffContent {

        @Override
        public void refresh() {
        }

        @Override
        public void dispose() {
            throw new IllegalStateException("an editor that could not be released");
        }

        @Override
        public org.netbeans.api.nbfx.editor.SavedDocument savedState() {
            return null;
        }

        @Override
        public void focusEditor() {
        }

        @Override
        public javafx.beans.value.ObservableValue<org.netbeans.api.nbfx.editor.EditorDocument> activeEditorProperty() {
            return new javafx.beans.property.SimpleObjectProperty<>();
        }
    }

    @Test
    public void theTextualTabSaysWhyItHasNoTextToShowAndIsNeverAnEmptyEditor() throws Exception {
        // Nothing differs: what the line above says.
        DiffPane same = pane("a\n", "a\n");
        awaitComparison(same);
        JavaFxTestSupport.onFx(() -> same.setTextual(true));
        awaitTextual(same);
        JavaFxTestSupport.onFx(() -> assertEquals("No differences", same.textualNote()));

        // A side that cannot be compared: that it cannot, and why.
        DiffPane binary = JavaFxTestSupport.onFx(() -> new DiffPane(DiffSource.unreadable("HEAD", "logo.png", "A binary file."),
                DiffSource.of("Working Tree", "logo.png", "text\n")));
        JavaFxTestSupport.onFx(() -> binary.setTextual(true));
        awaitTextual(binary);
        JavaFxTestSupport.onFx(() -> assertEquals("HEAD: A binary file.", binary.textualNote()));

        // A file with nothing in it, added: nothing to write of it, and no failure for that.
        DiffPane empty = JavaFxTestSupport.onFx(() -> new DiffPane(DiffSource.missing("HEAD", "empty.txt"),
                DiffSource.of("Working Tree", "empty.txt", "")));
        awaitComparison(empty);
        JavaFxTestSupport.onFx(() -> empty.setTextual(true));
        awaitTextual(empty);
        JavaFxTestSupport.onFx(() -> {
            assertEquals("Added: there is no file to compare with", empty.textualNote());
            assertEquals("", empty.unified());
        });

        // More than is written as text: how much.
        DiffPane large = JavaFxTestSupport.onFx(() -> new DiffPane(DiffSource.missing("HEAD", "big.txt"),
                DiffSource.of("Working Tree", "big.txt", "x\n".repeat((int) DiffPane.MOST_TEXTUAL_LINES + 1))));
        awaitComparison(large);
        JavaFxTestSupport.onFx(() -> large.setTextual(true));
        awaitTextual(large);
        JavaFxTestSupport.onFx(() -> assertTrue(large.textualNote(),
                large.textualNote().startsWith("Too much differs to show as text: ")));

        // Texts that differ, and no editor to show them in.
        DiffPane differs = pane("a\n", "b\n");
        awaitComparison(differs);
        JavaFxTestSupport.onFx(() -> differs.setTextual(true));
        awaitTextual(differs);
        JavaFxTestSupport.onFx(() -> {
            assertEquals("The differences could not be shown as text", differs.textualNote());
            // The tabs say what they are of, and are reached with the keyboard.
            assertTrue(differs.textualTab().isFocusTraversable());
            assertEquals("The differences as a unified diff", differs.textualTab().getTooltip().getText());
            // Back on the two sides nothing is said in their place.
            differs.setTextual(false);
            assertNull(differs.textualNote());
        });
    }

    private static void awaitTextual(DiffPane pane) throws Exception {
        for (int i = 0; i < 400; i++) {
            if (JavaFxTestSupport.onFx(() -> !pane.isWritingUnified() && !"Comparing...".equals(pane.textualNote()))) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("the unified diff was not shown");
    }
}
