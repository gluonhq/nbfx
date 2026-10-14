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
package com.gluonhq.netbeans.nbfx.ui.context;

import com.gluonhq.netbeans.nbfx.api.editor.CaretInfo;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;

import java.io.IOException;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.test.MockLookup;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * Verifies that the nbfx {@code ContextGlobalProvider} makes the global context follow the active
 * component (the nbfx replacement for the activated {@code TopComponent}): a view publishes its own
 * lookup, an editor publishes its document, two navigators do not answer for each other, and a
 * follower view that exposes nothing keeps the last selection.
 */
public class NbfxGlobalContextProviderTest {

    private ViewSelectionContextImpl views;
    private EditorContextImpl editors;

    @Before
    public void setUp() {
        this.views = new ViewSelectionContextImpl();
        this.editors = new EditorContextImpl();
        MockLookup.setInstances(views, editors);
    }

    @After
    public void tearDown() {
        MockLookup.setInstances();
    }

    @Test
    public void followsActiveView() throws IOException {
        FileObject file = newFile("A.java");
        Lookup global = new NbfxGlobalContextProvider().createGlobalContext();

        views.setActiveView(new FakeView(file));

        assertSame("global context does not carry the active view's file", file, global.lookup(FileObject.class));
        assertNotNull("global context does not carry the active view", global.lookup(ViewProvider.class));
    }

    @Test
    public void followsActiveEditor() throws IOException {
        FileObject file = newFile("B.java");
        FakeEditorDocument document = new FakeEditorDocument(file);
        Lookup global = new NbfxGlobalContextProvider().createGlobalContext();

        views.setActiveView(null);
        editors.setActiveDocument(document);

        assertSame("global context does not carry the active document", document, global.lookup(EditorDocument.class));
        assertSame("global context does not carry the editor's file", file, global.lookup(FileObject.class));
    }

    @Test
    public void editorWinsOverEarlierNavigatorSelection() throws IOException {
        FileObject a = newFile("A.java");
        FileObject b = newFile("B.java");
        FakeEditorDocument document = new FakeEditorDocument(b);
        Lookup global = new NbfxGlobalContextProvider().createGlobalContext();

        // A navigator carries a file; the focus then moves to an editor tab (no active view).
        views.setActiveView(new FakeView(a));
        editors.setActiveDocument(document);
        assertSame("navigator's file missing", a, global.lookup(FileObject.class));

        views.setActiveView(null);

        assertSame("the active editor did not replace the navigator's selection", document,
                global.lookup(EditorDocument.class));
        assertSame("the active editor's file is missing", b, global.lookup(FileObject.class));
    }

    @Test
    public void navigatorsDoNotAnswerForEachOther() throws IOException {
        FileObject a = newFile("A.java");
        FileObject c = newFile("C.java");
        Lookup global = new NbfxGlobalContextProvider().createGlobalContext();

        views.setActiveView(new FakeView(a));
        assertSame("first navigator's file missing", a, global.lookup(FileObject.class));

        views.setActiveView(new FakeView(c));
        assertSame("second navigator's file missing", c, global.lookup(FileObject.class));

        views.setActiveView(new FakeView(a));
        assertSame("first navigator's file not restored", a, global.lookup(FileObject.class));
    }

    @Test
    public void followerViewKeepsLastSelection() throws IOException {
        FileObject a = newFile("A.java");
        Lookup global = new NbfxGlobalContextProvider().createGlobalContext();

        // A navigator carries a file; a follower view then exposes nothing of its own.
        views.setActiveView(new FakeView(a));
        assertSame("navigator's file missing", a, global.lookup(FileObject.class));

        editors.setActiveDocument(null);
        views.setActiveView(new FakeView(null));

        assertSame("a follower view blanked the global selection", a, global.lookup(FileObject.class));
    }

    private static FileObject newFile(String name) throws IOException {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        return fs.getRoot().createData(name);
    }

    /** A view that publishes {@code file} (when non-null) and itself, like a navigator. */
    private static final class FakeView implements ViewProvider {
        private final FileObject file;

        FakeView(FileObject file) {
            this.file = file;
        }

        @Override
        public String getTitle() {
            return "fake";
        }

        @Override
        public Node getView() {
            return null;
        }

        @Override
        public Lookup getLookup() {
            return file == null ? Lookup.EMPTY : Lookups.fixed(this, file);
        }
    }

    /** The minimum an {@link EditorDocument} needs for the context; the rest are defaults. */
    private static final class FakeEditorDocument implements EditorDocument {
        private final FileObject file;
        private final SimpleBooleanProperty modified = new SimpleBooleanProperty();

        FakeEditorDocument(FileObject file) {
            this.file = file;
        }

        @Override
        public FileObject getFileObject() {
            return file;
        }

        @Override
        public Node getNode() {
            return null;
        }

        @Override
        public String getTitle() {
            return file.getName();
        }

        @Override
        public ReadOnlyBooleanProperty modifiedProperty() {
            return modified;
        }

        @Override
        public boolean isModified() {
            return modified.get();
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
            return new SimpleBooleanProperty();
        }

        @Override
        public ReadOnlyBooleanProperty canRedoProperty() {
            return new SimpleBooleanProperty();
        }

        @Override
        public ReadOnlyBooleanProperty hasSelectionProperty() {
            return new SimpleBooleanProperty();
        }

        @Override
        public ReadOnlyBooleanProperty editableProperty() {
            return new SimpleBooleanProperty();
        }

        @Override
        public ObservableValue<CaretInfo> caretInfoProperty() {
            return new SimpleObjectProperty<>();
        }

        @Override
        public ObservableValue<String> lineSeparatorProperty() {
            return new SimpleObjectProperty<>();
        }

        @Override
        public void setLineSeparator(String separator) {
        }
    }
}
