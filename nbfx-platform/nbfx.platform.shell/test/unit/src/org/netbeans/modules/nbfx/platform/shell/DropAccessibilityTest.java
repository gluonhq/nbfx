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

import javafx.scene.control.TabPane;
import org.junit.Test;
import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import static org.junit.Assert.*;

public class DropAccessibilityTest {

    @Test
    public void announcesAcceptedAndRejectedDrops() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            TabPane pane = new TabPane();
            DropAccessibility.accepted(pane);
            assertEquals("Drop accepted.", pane.getAccessibleText());

            TabPane rejectionPane = new TabPane();
            DropAccessibility.rejected(rejectionPane, "drop.no-handler");
            assertTrue(rejectionPane.getAccessibleText().startsWith("Drop rejected:"));
            assertTrue(rejectionPane.getAccessibleText().contains("no handler accepts"));

            TabPane policyPane = new TabPane();
            DropAccessibility.rejected(policyPane, "drop.policy.denied");
            assertTrue(policyPane.getAccessibleText().contains("drop.policy.denied"));

            pane.setAccessibleText("Editor drop area");
            DropAccessibility.accepted(pane);
            assertEquals("Editor drop area", pane.getAccessibleText());
            assertEquals("Drop accepted.", pane.getAccessibleHelp());
            return null;
        });
    }
}
