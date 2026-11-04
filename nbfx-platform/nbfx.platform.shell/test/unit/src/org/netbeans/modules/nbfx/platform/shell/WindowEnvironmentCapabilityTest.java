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
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;
import org.junit.Test;
import org.netbeans.modules.nbfx.platform.shell.api.DetachedBarsContext;
import org.netbeans.modules.nbfx.platform.shell.api.DetachedBarsProvider;
import org.netbeans.modules.nbfx.platform.shell.api.NavigatorRevealState;
import org.netbeans.modules.nbfx.platform.shell.api.ProjectSwitcher;
import org.netbeans.modules.nbfx.platform.shell.api.TabHomeResolver;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The focused window capabilities are composed in lookup order and the legacy contribution path
 * remains a fallback.
 */
public class WindowEnvironmentCapabilityTest {

    @Test
    public void projectSwitcherAndRevealStateAreComposed() {
        AtomicBoolean next = new AtomicBoolean();
        AtomicBoolean previous = new AtomicBoolean();
        AtomicBoolean revealing = new AtomicBoolean();
        ProjectSwitcher switcher = new ProjectSwitcher() {
            @Override
            public boolean selectNext() {
                next.set(true);
                return true;
            }

            @Override
            public boolean selectPrevious() {
                previous.set(true);
                return true;
            }
        };
        NavigatorRevealState state = new NavigatorRevealState() {
            @Override
            public boolean isRevealing() {
                return revealing.get();
            }

            @Override
            public void setRevealing(boolean value) {
                revealing.set(value);
            }
        };
        DefaultWindowEnvironment environment = new DefaultWindowEnvironment(
                Lookups.fixed(switcher, state));

        assertTrue(environment.selectNextProject());
        assertTrue(environment.selectPreviousProject());
        assertTrue(next.get());
        assertTrue(previous.get());
        assertFalse(environment.isNavigatorRevealInProgress());
        environment.setNavigatorRevealInProgress(true);
        assertTrue(environment.isNavigatorRevealInProgress());
        assertTrue(revealing.get());
    }

    @Test
    public void tabHomeResolverWins() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            Tab tab = new Tab();
            TabPane pane = new TabPane();
            TabHomeResolver resolver = candidate -> candidate == tab ? pane : null;
            DefaultWindowEnvironment environment = new DefaultWindowEnvironment(
                    Lookups.fixed(resolver));

            assertSame(pane, environment.homeOf(tab));
            return null;
        });
    }

    @Test
    public void detachedBarsProviderReceivesTheContext() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            SimpleObjectProperty<org.netbeans.api.nbfx.editor.EditorDocument> scope = new SimpleObjectProperty<>();
            Region bars = new Region();
            DetachedBarsProvider provider = context -> bars;
            DefaultWindowEnvironment environment = new DefaultWindowEnvironment(
                    Lookups.fixed(provider));

            assertSame(bars, environment.createDetachedBars(scope, List.of()));
            return null;
        });
    }
}
