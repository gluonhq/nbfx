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

import org.openide.util.Lookup;

/**
 * Opens the JavaFX Options dialog. The implementation is a platform service; a product opens the
 * dialog through the service, never by referencing the options window implementation.
 * <p>
 * Must be called on the JavaFX Application Thread.
 *
 * @since 1.1
 */
public interface OptionsDisplayer {

    /** The registered options displayer, or {@code null} when no platform implementation is present. */
    static OptionsDisplayer getDefault() {
        return Lookup.getDefault().lookup(OptionsDisplayer.class);
    }

    /** Opens the Options dialog on the first category. */
    void open();

    /**
     * Opens the Options dialog with the panel registered under {@code id} selected.
     *
     * @param id the registration id of the panel or category
     */
    void open(String id);
}
