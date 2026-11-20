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
package org.netbeans.modules.nbfx.platform.nodes;

import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.Test;
import org.netbeans.api.nbfx.layer.LayerEntry;
import org.netbeans.api.nbfx.nodes.PropertyModel;
import org.netbeans.api.nbfx.nodes.SimplePropertyModel;
import org.netbeans.api.nbfx.property.PropertyEditor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Verifies the layer-registered editor lookup. */
public class PropertyEditorsTest {

    @Test
    public void findsTheStringEditorFromTheLayer() {
        assertTrue(PropertyEditors.findEditor(String.class) instanceof TextPropertyEditor);
    }

    @Test
    public void skipsNonInlineEditorsAndKeepsLayerOrder() throws Exception {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        FileObject folder = fs.getRoot().createFolder("editors");
        FileObject dialogFile = folder.createData("dialog", "instance");
        dialogFile.setAttribute("inline", Boolean.FALSE);
        FileObject inlineFile = folder.createData("inline", "instance");
        inlineFile.setAttribute("inline", Boolean.TRUE);
        PropertyEditor dialog = new TestEditor();
        PropertyEditor inline = new TestEditor();

        PropertyEditor found = PropertyEditors.findEditor(
                List.of(new LayerEntry<>(dialog, dialogFile), new LayerEntry<>(inline, inlineFile)),
                String.class);

        assertSame(inline, found);
    }

    @Test
    public void writablePropertyGetsAnEditorAndReadOnlyPropertyDoesNot() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            Node editor = PropertyEditors.createEditor(
                    new SimplePropertyModel("name", "Name", String.class, "value"));
            assertTrue(editor instanceof TextField);

            Node readOnly = PropertyEditors.createEditor(new SimplePropertyModel(
                    "name", "Name", null, String.class, "value", false, false, "value"));
            assertTrue(readOnly instanceof Label);
            assertEquals("value", ((Label) readOnly).getText());
            assertNotNull(PropertyEditors.findEditor(Number.class));
            return null;
        });
    }

    /** A source-free editor used to check the layer attribute filter. */
    private static final class TestEditor implements PropertyEditor {

        @Override
        public Class<?> valueType() {
            return String.class;
        }

        @Override
        public Node createEditor(PropertyModel property) {
            return new Label();
        }
    }
}
