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
package org.netbeans.api.nbfx.wizard;

import org.openide.util.Lookup;

/**
 * Shows a {@link Wizard} in the platform's wizard dialog. The platform implementation is
 * {@code platform.wizard}; callers use this API seam instead of the dialog class.
 *
 * @since 1.1
 */
public interface WizardDisplayer {

    /** The registered displayer, or {@code null} when no platform implementation is present. */
    static WizardDisplayer getDefault() {
        return Lookup.getDefault().lookup(WizardDisplayer.class);
    }

    /** Shows {@code wizard}. Must be called on the JavaFX Application Thread. */
    void show(Wizard wizard);
}
