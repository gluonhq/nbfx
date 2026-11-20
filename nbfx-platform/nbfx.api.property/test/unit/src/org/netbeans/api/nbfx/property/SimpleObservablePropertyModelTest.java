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
package org.netbeans.api.nbfx.property;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.netbeans.api.nbfx.nodes.PropertyModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Verifies the JavaFX property model and its neutral listener bridge. */
public class SimpleObservablePropertyModelTest {

    @Test
    public void valuePropertyAndListenerStayInSync() {
        SimpleObservablePropertyModel property =
                new SimpleObservablePropertyModel("name", "Name", String.class, "a");
        List<Object> changes = new ArrayList<>();
        property.addListener((model, oldValue, newValue) -> changes.add(newValue));

        property.setValue("b");

        assertEquals("b", property.getValue());
        assertEquals("b", property.valueProperty().getValue());
        assertFalse(property.isDefaultValue());
        assertEquals(List.of("b"), changes);
    }

    @Test
    public void valuePropertyIsStable() {
        SimpleObservablePropertyModel property =
                new SimpleObservablePropertyModel("name", "Name", String.class, "a");

        assertSame(property.valueProperty(), property.valueProperty());
    }

    @Test
    public void sameValueDoesNotNotify() {
        SimpleObservablePropertyModel property =
                new SimpleObservablePropertyModel("name", "Name", String.class, "a");
        List<Object> changes = new ArrayList<>();
        property.addListener((model, oldValue, newValue) -> changes.add(newValue));

        property.setValue("a");

        assertTrue(changes.isEmpty());
        assertTrue(property.isDefaultValue());
    }

    @Test
    public void listenerReceivesTheObservableModel() {
        SimpleObservablePropertyModel property =
                new SimpleObservablePropertyModel("name", "Name", String.class, "a");
        AtomicReference<PropertyModel> seen = new AtomicReference<>();
        property.addListener((model, oldValue, newValue) -> seen.set(model));

        property.setValue("b");

        assertSame(property, seen.get());
    }

    @Test
    public void readOnlyPropertyIgnoresSetValue() {
        SimpleObservablePropertyModel property = new SimpleObservablePropertyModel(
                "name", "Name", null, String.class, "a", false, false, "a");

        property.setValue("b");

        assertEquals("a", property.getValue());
        assertTrue(property.isDefaultValue());
    }
}
