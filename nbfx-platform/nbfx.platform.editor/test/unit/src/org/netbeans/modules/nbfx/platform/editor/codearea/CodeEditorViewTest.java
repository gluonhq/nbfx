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
import javafx.geometry.HPos;
import org.junit.Test;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.EditorSettings;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies, on the headless JavaFX toolkit, what an editor has of its own as a view: the side of
 * its gutter, and whether it shows the breadcrumbs bar the shared setting has shown.
 */
public class CodeEditorViewTest {

    @Test
    public void theGutterIsOnTheSideAsked() throws Exception {
        FileObject file = file("Gutter.txt");
        JavaFxTestSupport.onFx(() -> {
            CodeEditor editor = new CodeEditor(file);
            assertEquals(HPos.LEFT, editor.gutterPositionProperty().get());
            assertNotNull(editor.codeArea.getLeftDecorator());
            assertNull(editor.codeArea.getRightDecorator());

            editor.gutterPositionProperty().set(HPos.RIGHT);
            assertNull(editor.codeArea.getLeftDecorator());
            assertNotNull(editor.codeArea.getRightDecorator());

            // Of the view alone: a clone has it where editors do.
            EditorDocument clone = editor.cloneDocument();
            assertEquals(HPos.LEFT, clone.gutterPositionProperty().get());
            assertNotNull(((CodeEditor) clone).codeArea.getLeftDecorator());

            // Anything else is the left.
            editor.gutterPositionProperty().set(HPos.CENTER);
            assertNotNull(editor.codeArea.getLeftDecorator());
            assertNull(editor.codeArea.getRightDecorator());
            clone.dispose();
            editor.dispose();
        });
    }

    @Test
    public void theBreadcrumbsAreShownWhenTheSettingAndTheViewHaveThem() throws Exception {
        FileObject file = file("Crumbs.txt");
        EditorSettings settings = Lookup.getDefault().lookup(EditorSettings.class);
        assertNotNull(settings);
        JavaFxTestSupport.onFx(() -> {
            boolean before = settings.showBreadcrumbs().get();
            settings.showBreadcrumbs().set(true);
            CodeEditor editor = new CodeEditor(file);
            CodeEditor other = new CodeEditor(file);
            try {
                assertTrue(editor.showBreadcrumbsProperty().get());
                assertTrue(editor.breadcrumbsBar.isVisible());
                assertTrue(editor.breadcrumbsBar.isManaged());

                editor.showBreadcrumbsProperty().set(false);
                assertFalse(editor.breadcrumbsBar.isVisible());
                assertFalse(editor.breadcrumbsBar.isManaged());
                // The setting is not touched, nor the other views.
                assertTrue(settings.showBreadcrumbs().get());
                assertTrue(other.breadcrumbsBar.isVisible());

                // Hidden for every editor: the view cannot show it.
                settings.showBreadcrumbs().set(false);
                editor.showBreadcrumbsProperty().set(true);
                assertFalse(editor.breadcrumbsBar.isVisible());
                assertFalse(other.breadcrumbsBar.isVisible());

                settings.showBreadcrumbs().set(true);
                assertTrue(editor.breadcrumbsBar.isVisible());
                assertTrue(other.breadcrumbsBar.isVisible());
            } finally {
                other.dispose();
                editor.dispose();
                settings.showBreadcrumbs().set(before);
            }
        });
    }

    private static FileObject file(String name) throws IOException {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData(name);
        try (OutputStream out = file.getOutputStream()) {
            out.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
