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
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.Test;
import org.netbeans.api.nbfx.nodes.PropertiesProvider;
import org.netbeans.api.nbfx.nodes.PropertyModel;
import org.netbeans.api.nbfx.nodes.PropertySetModel;
import org.netbeans.api.nbfx.nodes.SimplePropertyModel;
import org.netbeans.api.nbfx.nodes.SimplePropertySetModel;
import org.openide.util.test.MockLookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Verifies the property sheet against the toolkit-free model. */
public class PropertySheetTest {

    @Test
    public void showsTheProviderProperties() throws Exception {
        PropertyModel name = new SimplePropertyModel("name", "Name", String.class, "value");
        PropertySetModel set = new SimplePropertySetModel("general", "General", List.of(name));
        PropertiesProvider provider = context -> List.of(set);
        MockLookup.setInstances(provider);
        try {
            JavaFxTestSupport.onFx(() -> {
                PropertySheet sheet = new PropertySheet();
                sheet.setContext("context");
                assertEquals(1, sheet.tree().getRoot().getChildren().size());
                assertEquals(1, sheet.tree().getRoot().getChildren().get(0).getChildren().size());
                assertEquals(name, sheet.tree().getRoot().getChildren().get(0).getChildren().get(0).getValue());
                return null;
            });
        } finally {
            MockLookup.setInstances();
        }
    }

    @Test
    public void keepsAFocusedEditorAndRefreshesItOnFocusLoss() throws Exception {
        SimplePropertyModel model = new SimplePropertyModel("name", "Name", String.class, "a");
        PropertiesProvider provider = context -> List.of(
                new SimplePropertySetModel("general", "General", List.of(model)));
        MockLookup.setInstances(provider);
        AtomicReference<PropertySheet> sheet = new AtomicReference<>();
        AtomicReference<TextField> field = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        try {
            JavaFxTestSupport.onFx(() -> {
                PropertySheet sheetValue = new PropertySheet();
                Button elsewhere = new Button("elsewhere");
                VBox root = new VBox(sheetValue, elsewhere);
                Stage stageValue = new Stage();
                stageValue.setScene(new Scene(root, 400, 300));
                stageValue.show();
                sheetValue.setContext("context");
                sheetValue.tree().getRoot().getChildren().get(0).setExpanded(true);
                sheetValue.applyCss();
                sheetValue.layout();
                TextField fieldValue = (TextField) sheetValue.lookupAll(".text-field")
                        .iterator().next();
                fieldValue.requestFocus();
                assertTrue(fieldValue.isFocusWithin());
                model.setValue("b");
                assertSame("the focused editor is kept", fieldValue,
                        sheetValue.lookupAll(".text-field").iterator().next());
                assertEquals("the editor keeps the user's text", "a", fieldValue.getText());
                elsewhere.requestFocus();
                sheet.set(sheetValue);
                field.set(fieldValue);
                stage.set(stageValue);
                return null;
            });
            JavaFxTestSupport.onFx(() -> {
                TextField refreshed = (TextField) sheet.get().lookupAll(".text-field")
                        .iterator().next();
                assertEquals("the editor is refreshed with the model value", "b",
                        refreshed.getText());
                return null;
            });
        } finally {
            if (stage.get() != null) {
                JavaFxTestSupport.onFx(() -> {
                    stage.get().hide();
                    return null;
                });
            }
            MockLookup.setInstances();
        }
    }
}
