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
package org.netbeans.modules.nbfx.platform.shell;

import java.util.Optional;
import javafx.beans.property.SimpleObjectProperty;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.editor.EditorDocument;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Close Document and Close Other Documents have window-scoped variants, so a detached window's
 * Cmd+W acts on that window's document rather than the globally focused one.
 */
public class ShellCommandsProviderTest {

    @Test
    public void closeDocumentAndCloseOthersAreScoped() {
        ShellCommandsProvider provider = new ShellCommandsProvider();
        SimpleObjectProperty<EditorDocument> scope = new SimpleObjectProperty<>();

        Optional<Command> close = provider.createScoped(ActionIds.CLOSE_DOCUMENT, scope);
        assertTrue(close.isPresent());
        assertTrue("a scoped close is disabled without a document", close.get().isDisabled());

        assertTrue(provider.createScoped(ActionIds.CLOSE_OTHER_DOCUMENTS, scope).isPresent());
        assertFalse("Close All remains the global command",
                provider.createScoped(ActionIds.CLOSE_ALL_DOCUMENTS, scope).isPresent());
        assertTrue(provider.createScoped(ActionIds.CLOSE_DOCUMENT, null).isEmpty());
    }
}
