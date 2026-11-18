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

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.Test;
import org.netbeans.api.nbfx.nodes.SimplePropertyModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Verifies the layer-registered editor lookup. */
public class PropertyEditorsTest {

    @Test
    public void findsTheStringEditorFromTheLayer() {
        assertTrue(PropertyEditors.findEditor(String.class) instanceof TextPropertyEditor);
    }

    @Test
    public void writablePropertyGetsAnEditorAndReadOnlyPropertyDoesNot() throws Exception {
        FxTestSupport.onFx(() -> {
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
}
