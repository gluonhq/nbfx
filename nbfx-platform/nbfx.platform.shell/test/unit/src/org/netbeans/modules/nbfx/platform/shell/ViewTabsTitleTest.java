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

import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.assertEquals;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import org.junit.Test;
import org.netbeans.api.nbfx.view.ViewProvider;

/** Verifies that the tab of a view shows the view's title and follows it when the view changes it. */
public class ViewTabsTitleTest {

    @Test
    public void theTabShowsTheNameOfAViewWithoutATitleOfItsOwn() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            ViewProvider view = new ViewProvider() {
                @Override
                public String getTitle() {
                    return "Plain";
                }

                @Override
                public Node getView() {
                    return new Label("content");
                }
            };

            Tab tab = ViewTabs.create(view, "tooltip");

            assertEquals("Plain", ((Label) tab.getGraphic()).getText());
            return null;
        });
    }

    @Test
    public void theTabFollowsTheTitleOfTheView() throws Exception {
        JavaFxTestSupport.onFx(() -> {
            StringProperty title = new SimpleStringProperty("Output");
            ViewProvider view = new ViewProvider() {
                @Override
                public String getId() {
                    return "view-tabs-title-test";
                }

                @Override
                public String getTitle() {
                    return "Output";
                }

                @Override
                public ObservableValue<String> titleProperty() {
                    return title;
                }

                @Override
                public Node getView() {
                    return new Label("content");
                }
            };

            Tab tab = ViewTabs.create(view, "tooltip");
            Label label = (Label) tab.getGraphic();
            assertEquals("Output", label.getText());

            title.set("Output - Run (mavenproject5)");
            assertEquals("Output - Run (mavenproject5)", label.getText());

            title.set("Output");
            assertEquals("Output", label.getText());
            return null;
        });
    }
}
