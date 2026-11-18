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

import java.util.List;
import org.junit.Test;
import org.netbeans.api.nbfx.nodes.PropertiesProvider;
import org.netbeans.api.nbfx.nodes.PropertyModel;
import org.netbeans.api.nbfx.nodes.PropertySetModel;
import org.netbeans.api.nbfx.nodes.SimplePropertyModel;
import org.netbeans.api.nbfx.nodes.SimplePropertySetModel;
import org.openide.util.test.MockLookup;

import static org.junit.Assert.assertEquals;

/** Verifies the property sheet against the toolkit-free model. */
public class PropertySheetTest {

    @Test
    public void showsTheProviderProperties() throws Exception {
        PropertyModel name = new SimplePropertyModel("name", "Name", String.class, "value");
        PropertySetModel set = new SimplePropertySetModel("general", "General", List.of(name));
        PropertiesProvider provider = context -> List.of(set);
        MockLookup.setInstances(provider);
        try {
            FxTestSupport.onFx(() -> {
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
}
