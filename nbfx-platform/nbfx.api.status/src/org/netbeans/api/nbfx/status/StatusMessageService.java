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
package org.netbeans.api.nbfx.status;

import java.util.function.Consumer;

/**
 * The transient text shown at the start of the status bar, mirroring the Swing
 * {@code StatusDisplayer}: the message fills the remaining space while progress and the fixed
 * elements stay on the right.
 *
 * @since 1.1
 */
public interface StatusMessageService {

    /** Sets the current message; {@code null} clears it. */
    void setMessage(String message);

    /** The current message, never {@code null}. */
    String getMessage();

    /** Registers a listener notified whenever the message changes. */
    void addListener(Consumer<String> listener);

    /** Removes a listener added with {@link #addListener(Consumer)}. */
    void removeListener(Consumer<String> listener);
}
