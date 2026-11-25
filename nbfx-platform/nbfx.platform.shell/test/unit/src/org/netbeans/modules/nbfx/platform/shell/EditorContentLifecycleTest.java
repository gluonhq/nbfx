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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import org.junit.Test;
import org.netbeans.api.nbfx.editor.CaretInfo;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;
import org.openide.filesystems.FileObject;

import static org.junit.Assert.assertTrue;

/** An editor tab publishes its open and close to a bound lifecycle exactly once. */
public class EditorContentLifecycleTest {

    @Test
    public void editorTabsPublishOpenAndClose() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
        });
        List<EditorDocument> opened = new CopyOnWriteArrayList<>();
        List<EditorDocument> closed = new CopyOnWriteArrayList<>();
        NbfxTabPane.EditorLifecycle lifecycle = new NbfxTabPane.EditorLifecycle() {
            @Override
            public void editorOpened(EditorDocument document) {
                opened.add(document);
            }

            @Override
            public void editorClosed(EditorDocument document) {
                closed.add(document);
            }
        };
        NbfxTabPane.addEditorLifecycle(lifecycle);
        try {
            EditorDocument document = new FakeEditorDocument("Editor");
            AtomicReference<Tab> tab = new AtomicReference<>();

            JavaFxTestSupport.onFx(() -> {
                TabPane pane = new NbfxTabPane(PaneRole.MAIN);
                Tab created = new Tab("Editor");
                created.setContent(document.getNode());
                NbfxTabPane.setDocument(created, document);
                NbfxTabPane.notifyEditorOpened(document);
                NbfxTabPane.attachTab(pane, created);
                tab.set(created);
                return null;
            });

            assertTrue("a created editor tab is published as opened", opened.contains(document));

            JavaFxTestSupport.onFx(() -> {
                NbfxTabPane.closeTab(tab.get());
                return null;
            });
            // closeTab defers the removal, which defers unregistering: drain one pulse in between.
            JavaFxTestSupport.onFx(() -> {
            });
            JavaFxTestSupport.onFx(() -> {
                assertTrue("a closed editor tab is published as closed", closed.contains(document));
                return null;
            });
        } finally {
            NbfxTabPane.removeEditorLifecycle(lifecycle);
        }
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