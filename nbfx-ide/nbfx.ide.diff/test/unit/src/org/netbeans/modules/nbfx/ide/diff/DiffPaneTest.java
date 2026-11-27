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
        assertThrows(IllegalArgumentException.class, () -> new DiffSource("HEAD", "A.txt", "text", "and a problem"));
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
}
