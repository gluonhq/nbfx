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

/**
 * A property shown in the property sheet: one editable or read-only value of a context object.
 * <p>
 * The model is toolkit-free. A JavaFX-facing implementation with an {@code ObservableValue} lives
 * in {@code org.netbeans.api.nbfx.property.ObservablePropertyModel}; the property sheet adapts this model
 * to JavaFX when needed.
 *
 * @since 1.0
 */
public interface PropertyModel {

    /** The stable name of the property, unique within its set. */
    String getName();

    /** The display name shown in the sheet. */
    String getDisplayName();

    /** A short help text, or {@code null}. */
    String getShortDescription();

    /** The type of the value, used to choose an editor; may be {@code null} when unknown. */
    Class<?> getValueType();

    /** The current value, or {@code null}. */
    Object getValue();

    /** Sets the value; ignored when {@link #isWritable()} is {@code false}. */
    void setValue(Object value);

    /** Whether the value can be edited. */
    boolean isWritable();

    /** Whether the property is only shown with the expert toggle on. */
    boolean isExpert();

    /** Whether the value currently equals the property's default. */
    boolean isDefaultValue();

    /**
     * Registers a listener notified when the value changes.
     *
     * @param listener the listener
     * @return a subscription that removes the listener
     */
    PropertySubscription addListener(PropertyListener listener);
}
