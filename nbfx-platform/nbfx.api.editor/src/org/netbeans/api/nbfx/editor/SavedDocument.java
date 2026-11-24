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
package org.netbeans.api.nbfx.editor;

import java.util.Objects;

/**
 * What it takes to open again, in a later session, a document that is the editor of no file - a
 * diff: who can open it and what of. The shell keeps it with the layout of the windows and hands it
 * back to the {@link org.netbeans.spi.nbfx.editor.DocumentRestorer} of that id when it restores
 * the tabs.
 *
 * @param restorerId the {@link org.netbeans.spi.nbfx.editor.DocumentRestorer#id() id} of the
 *        restorer: letters, digits, {@code _}, {@code .} and {@code -}
 * @param state what the restorer needs to open the document again, in a form of its own; any text
 * @since 1.4
 */
public record SavedDocument(String restorerId, String state) {

    /** Checks the id; both are required. */
    public SavedDocument {
        Objects.requireNonNull(restorerId, "restorerId");
        Objects.requireNonNull(state, "state");
        if (!restorerId.matches("[\\w.-]+")) {
            throw new IllegalArgumentException("Not a restorer id: " + restorerId);
        }
    }
}
