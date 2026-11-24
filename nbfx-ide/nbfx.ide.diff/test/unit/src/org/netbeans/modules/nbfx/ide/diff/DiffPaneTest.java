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
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.netbeans.api.nbfx.diff.Difference;
import org.netbeans.api.nbfx.diff.Difference.Kind;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
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
    public void sameFilesHaveNoDifferences() throws Exception {
        DiffPane pane = pane("same\n", "same\n");

        awaitComparison(pane);

        assertTrue(JavaFxTestSupport.onFx(pane::differences).isEmpty());
        assertEquals("No differences", JavaFxTestSupport.onFx(pane::summary));
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
        JavaFxTestSupport.onFx(document::dispose);
    }

    private static DiffPane pane(String left, String right) throws Exception {
        FileObject from = inMemory(left);
        FileObject to = inMemory(right);
        return JavaFxTestSupport.onFx(() -> new DiffPane(new DiffSource("HEAD", from), new DiffSource("Working Tree", to)));
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

    private static FileObject inMemory(String text) throws Exception {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData("A.txt");
        try (OutputStream out = file.getOutputStream()) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
