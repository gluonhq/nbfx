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

import org.netbeans.modules.nbfx.platform.shell.api.DetachedBarsContext;
import org.netbeans.modules.nbfx.platform.shell.api.WindowEnvironmentContribution;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.Region;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The default environment must hand a detached window's own document scope to the contribution;
 * using the global active document would make every detached window act on the focused one.
 */
public class DefaultWindowEnvironmentTest {

    @Test
    public void detachedBarsReceiveTheWindowScope() {
        SimpleObjectProperty<EditorDocument> scope = new SimpleObjectProperty<>();
        List<Command> scopedCommands = List.of();
        AtomicReference<DetachedBarsContext> received = new AtomicReference<>();
        WindowEnvironmentContribution contribution = new WindowEnvironmentContribution() {
            @Override
            public Region createDetachedBars(DetachedBarsContext context) {
                received.set(context);
                return null;
            }
        };
        DefaultWindowEnvironment environment = new DefaultWindowEnvironment(
                Lookups.fixed(contribution));

        assertNull(environment.createDetachedBars(scope, scopedCommands));
        ObservableValue<EditorDocument> actual = received.get().activeDocument(EditorDocument.class);
        assertSame(scope, actual);
        assertSame(scopedCommands, received.get().scopedCommands());
    }
}
