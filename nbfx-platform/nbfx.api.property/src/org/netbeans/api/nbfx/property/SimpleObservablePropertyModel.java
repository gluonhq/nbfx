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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import org.netbeans.api.nbfx.nodes.PropertyListener;
import org.netbeans.api.nbfx.nodes.PropertySubscription;

/**
 * A simple {@link ObservablePropertyModel} backed by a JavaFX property. Convenience for providers
 * that already hold their values as JavaFX properties.
 *
 * @since 1.0
 */
public final class SimpleObservablePropertyModel implements ObservablePropertyModel {

    private final String name;
    private final String displayName;
    private final String shortDescription;
    private final Class<?> valueType;
    private final boolean writable;
    private final boolean expert;
    private final Object defaultValue;
    private final ReadOnlyObjectWrapper<Object> value = new ReadOnlyObjectWrapper<>(this, "value");
    private final List<PropertyListener> listeners = new CopyOnWriteArrayList<>();

    /** A writable property whose initial value is also its default. */
    public SimpleObservablePropertyModel(String name, String displayName, Class<?> valueType, Object value) {
        this(name, displayName, null, valueType, value, true, false, value);
    }

    /**
     * Creates a property.
     *
     * @param name             the stable property name
     * @param displayName      the display name
     * @param shortDescription a short help text, or {@code null}
     * @param valueType        the value type, or {@code null} when unknown
     * @param value            the initial value
     * @param writable         whether the value can be edited
     * @param expert           whether the property is only shown with the expert toggle on
     * @param defaultValue     the value considered the default
     */
    public SimpleObservablePropertyModel(String name, String displayName, String shortDescription,
            Class<?> valueType, Object value, boolean writable, boolean expert,
            Object defaultValue) {
        this.name = Objects.requireNonNull(name, "name");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.shortDescription = shortDescription;
        this.valueType = valueType;
        this.writable = writable;
        this.expert = expert;
        this.defaultValue = defaultValue;
        this.value.set(value);
        this.value.addListener((observable, oldValue, newValue) -> notifyListeners(oldValue, newValue));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getShortDescription() {
        return shortDescription;
    }

    @Override
    public Class<?> getValueType() {
        return valueType;
    }

    @Override
    public Object getValue() {
        return value.get();
    }

    @Override
    public void setValue(Object newValue) {
        if (writable) {
            value.set(newValue);
        }
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    @Override
    public boolean isExpert() {
        return expert;
    }

    @Override
    public boolean isDefaultValue() {
        return Objects.equals(value.get(), defaultValue);
    }

    @Override
    public ObservableValue<Object> valueProperty() {
        return value.getReadOnlyProperty();
    }

    @Override
    public PropertySubscription addListener(PropertyListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void notifyListeners(Object oldValue, Object newValue) {
        for (PropertyListener listener : listeners) {
            listener.changed(this, oldValue, newValue);
        }
    }
}
