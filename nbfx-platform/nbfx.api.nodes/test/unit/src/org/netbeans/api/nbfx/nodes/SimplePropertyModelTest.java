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
package org.netbeans.api.nbfx.nodes;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Verifies the toolkit-free simple property model and its listener subscription. */
public class SimplePropertyModelTest {

    @Test
    public void writablePropertySetsValueAndNotifiesListeners() {
        SimplePropertyModel property =
                new SimplePropertyModel("name", "Name", String.class, "a");
        List<Object> changes = new ArrayList<>();
        PropertySubscription subscription = property.addListener(
                (model, oldValue, newValue) -> changes.add(oldValue + "->" + newValue));

        assertTrue(property.isWritable());
        assertTrue(property.isDefaultValue());
        property.setValue("b");

        assertEquals("b", property.getValue());
        assertFalse(property.isDefaultValue());
        assertEquals(List.of("a->b"), changes);
        subscription.unsubscribe();
    }

    @Test
    public void readOnlyPropertyIgnoresSetValue() {
        SimplePropertyModel property = new SimplePropertyModel(
                "name", "Name", null, String.class, "a", false, false, "a");

        assertFalse(property.isWritable());
        property.setValue("b");
        assertEquals("a", property.getValue());
    }

    @Test
    public void unsubscribeStopsNotifications() {
        SimplePropertyModel property =
                new SimplePropertyModel("name", "Name", String.class, "a");
        List<Object> changes = new ArrayList<>();
        PropertySubscription subscription = property.addListener(
                (model, oldValue, newValue) -> changes.add(newValue));

        subscription.unsubscribe();
        property.setValue("b");

        assertTrue(changes.isEmpty());
        assertEquals("b", property.getValue());
    }

    @Test
    public void propertySetExposesItsProperties() {
        SimplePropertyModel property =
                new SimplePropertyModel("name", "Name", String.class, "a");
        SimplePropertySetModel set =
                new SimplePropertySetModel("general", "General", List.of(property));

        assertEquals("general", set.getName());
        assertEquals("General", set.getDisplayName());
        assertEquals(1, set.getProperties().size());
        assertSame(property, set.getProperties().get(0));
    }

    @Test
    public void expertFlagIsExposed() {
        SimplePropertyModel property = new SimplePropertyModel(
                "hidden", "Hidden", null, String.class, "a", true, true, "a");
        assertTrue(property.isExpert());
    }
}
