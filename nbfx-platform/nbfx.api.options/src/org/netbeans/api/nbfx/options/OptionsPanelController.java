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
package org.netbeans.api.nbfx.options;

import javafx.scene.Node;

/**
 * One panel of the Options dialog. A module implements this interface and registers the
 * implementation with {@link OptionsRegistration} or {@link OptionsSubRegistration}; the generated
 * layer entry owns the id, display name and position, and the options window discovers it there.
 * <p>
 * All methods are called on the JavaFX Application Thread.
 *
 * @since 1.1
 */
public interface OptionsPanelController {

    /** The panel's JavaFX component; called when the panel is selected in the options window. */
    Node getComponent();

    /** Loads the current values into the component, before it is shown. */
    default void load() {
    }

    /** Persists the panel's values. Called when the user presses OK or Apply. */
    default void apply() {
    }

    /** Discards the panel's values. Called when the user presses Cancel. */
    default void cancel() {
    }

    /** Whether the panel's values are valid. */
    default boolean isValid() {
        return true;
    }
}
