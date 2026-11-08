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

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.layout.Region;
import org.junit.Test;
import org.netbeans.api.nbfx.view.ViewProvider;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A view tab whose pane role disappeared from the registrations moves to the default view pane
 * instead of being dropped.
 */
public class SessionRestorerTest {

    private static final class View implements ViewProvider {

        @Override
        public String getId() {
            return "conformance.removed-pane";
        }

        @Override
        public String getTitle() {
            return "Removed Pane View";
        }

        @Override
        public Node getView() {
            return new Region();
        }
    }

    @Test
    public void viewTabsMoveToTheDefaultViewPane() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            NbfxTabPane navigator = new NbfxTabPane(NbfxTabPane.PaneRole.NAVIGATOR);
            try {
                Tab tab = ViewTabs.create(new View(), "Removed Pane View");

                SessionRestorer.moveTabsToFallback(List.of(tab), null, navigator);

                assertSame(navigator, tab.getTabPane());
                assertTrue(navigator.getTabs().contains(tab));
            } finally {
                NbfxTabPane.unregister(navigator);
            }
            return null;
        });
    }
}
