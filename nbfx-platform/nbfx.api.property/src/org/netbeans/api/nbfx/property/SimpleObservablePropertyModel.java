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

import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import org.netbeans.api.nbfx.nodes.PropertyListener;
import org.netbeans.api.nbfx.nodes.PropertySubscription;
import org.netbeans.api.nbfx.nodes.SimplePropertyModel;

/**
 * A simple {@link ObservablePropertyModel} backed by a JavaFX observable and a neutral
 * {@link SimplePropertyModel}. Providers that already hold their values as JavaFX properties can
 * use this; the neutral model keeps the value, the equality check and the listener identity.
 *
 * @since 1.1
 */
public final class SimpleObservablePropertyModel implements ObservablePropertyModel {

    private final SimplePropertyModel delegate;
    private final ReadOnlyObjectWrapper<Object> valueWrapper;
    private final ReadOnlyObjectProperty<Object> valueProperty;

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
        delegate = new SimplePropertyModel(name, displayName, shortDescription, valueType,
                value, writable, expert, defaultValue);
        valueWrapper = new ReadOnlyObjectWrapper<>(this, "value", value);
        valueProperty = valueWrapper.getReadOnlyProperty();
        delegate.addListener((property, oldValue, newValue) -> valueWrapper.set(newValue));
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public String getShortDescription() {
        return delegate.getShortDescription();
    }

    @Override
    public Class<?> getValueType() {
        return delegate.getValueType();
    }

    @Override
    public Object getValue() {
        return delegate.getValue();
    }

    @Override
    public void setValue(Object newValue) {
        delegate.setValue(newValue);
    }

    @Override
    public boolean isWritable() {
        return delegate.isWritable();
    }

    @Override
    public boolean isExpert() {
        return delegate.isExpert();
    }

    @Override
    public boolean isDefaultValue() {
        return delegate.isDefaultValue();
    }

    @Override
    public ObservableValue<Object> valueProperty() {
        return valueProperty;
    }

    @Override
    public PropertySubscription addListener(PropertyListener listener) {
        Objects.requireNonNull(listener, "listener");
        return delegate.addListener(
                (property, oldValue, newValue) -> listener.changed(this, oldValue, newValue));
    }
}
