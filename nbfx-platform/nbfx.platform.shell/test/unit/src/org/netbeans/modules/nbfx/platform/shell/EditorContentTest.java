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
package org.netbeans.modules.nbfx.platform.shell;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import org.junit.Test;
import org.netbeans.api.nbfx.content.ContentKind;
import org.netbeans.api.nbfx.content.ContentState;
import org.netbeans.api.nbfx.editor.CaretInfo;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.filesystems.FileObject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** The EditorContent adapter gives an editor document a stable content identity and lifecycle. */
public class EditorContentTest {

    @Test
    public void sameDocumentGetsStableIdentity() {
        EditorDocument document = new FakeEditorDocument("A");
        EditorContent first = new EditorContent(document);
        EditorContent second = new EditorContent(document);

        assertEquals(first.id(), second.id());
        assertEquals(first.instanceKey(), second.instanceKey());
    }

    @Test
    public void differentDocumentsGetDifferentIdentities() {
        EditorContent first = new EditorContent(new FakeEditorDocument("A"));
        EditorContent second = new EditorContent(new FakeEditorDocument("B"));

        assertNotEquals(first.id(), second.id());
    }

    @Test
    public void adapterDelegatesToDocument() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
        });
        EditorDocument document = new FakeEditorDocument("Title");
        EditorContent content = new EditorContent(document);

        assertEquals(ContentKind.EDITOR, content.kind());
        assertEquals("Title", content.title());
        assertTrue(content.canClose());
        JavaFxTestSupport.onFx(() -> {
            Node node = content.node();
            assertTrue("node is delegated", node instanceof Pane);
            return null;
        });
    }

    @Test
    public void saveStateIsEmptyAndCarriesIdentity() {
        EditorDocument document = new FakeEditorDocument("A");
        EditorContent content = new EditorContent(document);

        ContentState state = content.saveState();
        assertEquals(content.id(), state.id());
        assertEquals(content.instanceKey(), state.instanceKey());
        assertTrue(state.payload().isEmpty());
    }

    @Test
    public void closeWithoutEditorContextIsHarmless() {
        EditorContent content = new EditorContent(new FakeEditorDocument("A"));
        content.close(); // no EditorContext in lookup - must not throw
        content.dispose();
    }

    private static final class FakeEditorDocument implements EditorDocument {

        private final String title;
        private final ReadOnlyBooleanWrapper modified = new ReadOnlyBooleanWrapper(false);
        private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(false);
        private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(false);
        private final ReadOnlyBooleanWrapper hasSelection = new ReadOnlyBooleanWrapper(false);
        private final ReadOnlyBooleanWrapper editable = new ReadOnlyBooleanWrapper(true);
        private final SimpleObjectProperty<CaretInfo> caretInfo = new SimpleObjectProperty<>();
        private final SimpleObjectProperty<String> lineSeparator = new SimpleObjectProperty<>("\n");

        FakeEditorDocument(String title) {
            this.title = title;
        }

        @Override
        public FileObject getFileObject() {
            return null;
        }

        @Override
        public Node getNode() {
            return new Pane();
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public boolean isModified() {
            return modified.get();
        }

        @Override
        public ReadOnlyBooleanProperty modifiedProperty() {
            return modified.getReadOnlyProperty();
        }

        @Override
        public void save() {
        }

        @Override
        public void undo() {
        }

        @Override
        public void redo() {
        }

        @Override
        public void copy() {
        }

        @Override
        public void cut() {
        }

        @Override
        public void paste() {
        }

        @Override
        public ReadOnlyBooleanProperty canUndoProperty() {
            return canUndo.getReadOnlyProperty();
        }

        @Override
        public ReadOnlyBooleanProperty canRedoProperty() {
            return canRedo.getReadOnlyProperty();
        }

        @Override
        public ReadOnlyBooleanProperty hasSelectionProperty() {
            return hasSelection.getReadOnlyProperty();
        }

        @Override
        public ReadOnlyBooleanProperty editableProperty() {
            return editable.getReadOnlyProperty();
        }

        @Override
        public ObservableValue<CaretInfo> caretInfoProperty() {
            return caretInfo;
        }

        @Override
        public ObservableValue<String> lineSeparatorProperty() {
            return lineSeparator;
        }

        @Override
        public void setLineSeparator(String separator) {
            lineSeparator.set(separator);
        }
    }
}