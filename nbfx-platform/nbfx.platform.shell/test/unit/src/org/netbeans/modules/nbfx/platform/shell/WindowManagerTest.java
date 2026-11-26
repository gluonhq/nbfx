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

import org.netbeans.modules.nbfx.test.fx.JavaFxTestSupport;

import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import org.junit.Test;
import java.util.List;
import java.util.Set;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.DockPlacement;
import org.netbeans.api.nbfx.content.TransferMode;
import org.netbeans.api.nbfx.layout.DockGroupId;
import org.netbeans.api.nbfx.layout.LayoutNode;
import org.netbeans.api.nbfx.shell.DockDecision;
import org.netbeans.api.nbfx.shell.DockWindow;
import org.netbeans.api.nbfx.shell.DockRequest;
import org.netbeans.api.nbfx.shell.DockResult;
import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.api.nbfx.shell.WindowKind;
import org.netbeans.api.nbfx.shell.WindowState;
import org.netbeans.api.nbfx.shell.WindowManager;
import org.netbeans.api.nbfx.shell.WindowOptions;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.api.nbfx.views.ViewContent;
import org.netbeans.modules.nbfx.platform.content.DefaultContentManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The public {@link WindowManager} exposes dock group and content operations by stable identity.
 */
public class WindowManagerTest {

    @Test
    public void exposesGroupAndContentOperations() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
            NbfxTabPane other = new NbfxTabPane(PaneRole.NAVIGATOR);
            NbfxTabPane custom = new NbfxTabPane(PaneRole.of("TEST_PRIMARY"));
            DefaultContentManager contentManager = new DefaultContentManager();
            try {
                WindowManager manager = new DefaultWindowManager(contentManager);
                ViewProvider view = new FakeView("demo.view", "Demo");
                ViewContent content = new ViewContent(view);
                ContentId contentId = content.id();
                contentManager.open(content);

                Tab viewTab = new Tab(view.getTitle());
                NbfxTabPane.setViewId(viewTab, view.getId());
                NbfxTabPane.attachTab(main, viewTab);

                assertTrue(manager.groups().contains(main.groupId()));
                assertSame(main.groupId(), manager.groupOf(contentId).orElseThrow());
                assertSame(main.groupId(), manager.primary(PaneRole.MAIN).orElseThrow());
                assertSame(custom.groupId(), manager.primary(custom.role()).orElseThrow());

                DockGroupId otherGroup = other.groupId();
                manager.move(contentId, otherGroup, 0);
                assertSame(otherGroup, manager.groupOf(contentId).orElseThrow());
                assertEquals(0, other.getTabs().indexOf(viewTab));

                Tab second = new Tab("second");
                NbfxTabPane.setViewId(second, "other.view");
                NbfxTabPane.attachTab(other, second);
                assertSame(second, other.getSelectionModel().getSelectedItem());
                manager.select(contentId);
                assertSame(viewTab, other.getSelectionModel().getSelectedItem());

                manager.attach(contentId, main.groupId(), -1);
                assertSame(main.groupId(), manager.groupOf(contentId).orElseThrow());
            } finally {
                NbfxTabPane.unregister(main);
                NbfxTabPane.unregister(other);
                NbfxTabPane.unregister(custom);
            }
            return null;
        });
    }

    @Test
    public void dockedGroupsHaveUniqueStableIdentities() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
            NbfxTabPane first = new NbfxTabPane(PaneRole.DOCKED);
            NbfxTabPane second = new NbfxTabPane(PaneRole.DOCKED);
            DefaultContentManager contentManager = new DefaultContentManager();
            try {
                WindowManager manager = new DefaultWindowManager(contentManager);
                ViewContent content = new ViewContent(new FakeView("docked.identity", "Identity"));
                contentManager.open(content);
                Tab tab = new Tab("Identity");
                NbfxTabPane.setViewId(tab, "docked.identity");
                NbfxTabPane.attachTab(main, tab);

                assertTrue(!first.groupId().equals(second.groupId()));
                assertTrue(manager.groups().containsAll(List.of(first.groupId(), second.groupId())));
                manager.move(content.id(), second.groupId(), 0);
                assertEquals(second.groupId(), manager.groupOf(content.id()).orElseThrow());
                assertEquals(List.of(tab), second.getTabs());
                assertTrue(first.getTabs().isEmpty());
            } finally {
                NbfxTabPane.unregister(main);
                NbfxTabPane.unregister(first);
                NbfxTabPane.unregister(second);
            }
            return null;
        });
    }

    @Test
    public void dockPolicyCanReject() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
            NbfxTabPane other = new NbfxTabPane(PaneRole.NAVIGATOR);
            DefaultContentManager contentManager = new DefaultContentManager();
            try {
                WindowManager manager = new DefaultWindowManager(contentManager,
                        () -> List.of(request -> DockDecision.reject("dock.policy.denied")));
                ViewContent content = new ViewContent(new FakeView("policy.view", "Policy"));
                contentManager.open(content);
                Tab tab = new Tab("Policy");
                NbfxTabPane.setViewId(tab, "policy.view");
                NbfxTabPane.attachTab(main, tab);

                DockRequest request = new DockRequest(content.id(), main.groupId(), null,
                        other.groupId(), DockPlacement.INTO_GROUP, TransferMode.MOVE, Set.of());
                DockDecision decision = manager.canDock(request);
                assertTrue(!decision.allowed());
                assertEquals("dock.policy.denied", decision.reasonKey());
                DockResult result = manager.dock(request);
                assertTrue(!result.decision().allowed());
                assertSame(main.groupId(), manager.groupOf(content.id()).orElseThrow());
            } finally {
                NbfxTabPane.unregister(main);
                NbfxTabPane.unregister(other);
            }
            return null;
        });
    }

    @Test
    public void canDockAndDockIntoExistingGroup() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
            NbfxTabPane other = new NbfxTabPane(PaneRole.NAVIGATOR);
            DefaultContentManager contentManager = new DefaultContentManager();
            try {
                WindowManager manager = new DefaultWindowManager(contentManager);
                ViewContent content = new ViewContent(new FakeView("dock.view", "Dock"));
                contentManager.open(content);
                Tab tab = new Tab("Dock");
                NbfxTabPane.setViewId(tab, "dock.view");
                NbfxTabPane.attachTab(main, tab);

                DockRequest request = new DockRequest(content.id(), main.groupId(), null,
                        other.groupId(), DockPlacement.INTO_GROUP, TransferMode.MOVE, Set.of());
                DockDecision decision = manager.canDock(request);
                assertTrue(decision.allowed());

                DockResult result = manager.dock(request);
                assertTrue(result.decision().allowed());
                assertEquals(other.groupId(), result.group());
                assertEquals(other.groupId(), manager.groupOf(content.id()).orElseThrow());

                DockRequest missing = new DockRequest(ContentId.of("nbfx:view.missing"), null, null,
                        other.groupId(), DockPlacement.INTO_GROUP, TransferMode.MOVE, Set.of());
                assertTrue(!manager.canDock(missing).allowed());
                assertTrue(!manager.dock(missing).decision().allowed());
            } finally {
                NbfxTabPane.unregister(main);
                NbfxTabPane.unregister(other);
            }
            return null;
        });
    }

    @Test
    public void docksBeforeAndAfterTargetContent() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
            NbfxTabPane other = new NbfxTabPane(PaneRole.NAVIGATOR);
            DefaultContentManager contentManager = new DefaultContentManager();
            try {
                WindowManager manager = new DefaultWindowManager(contentManager);
                ViewContent target = new ViewContent(new FakeView("relative.target", "Target"));
                ViewContent source = new ViewContent(new FakeView("relative.source", "Source"));
                contentManager.open(target);
                contentManager.open(source);
                Tab targetTab = new Tab("Target");
                Tab sourceTab = new Tab("Source");
                NbfxTabPane.setViewId(targetTab, "relative.target");
                NbfxTabPane.setViewId(sourceTab, "relative.source");
                NbfxTabPane.attachTab(main, targetTab);
                NbfxTabPane.attachTab(other, sourceTab);

                DockRequest before = new DockRequest(source.id(), other.groupId(), null,
                        main.groupId(), target.id(), DockPlacement.BEFORE, TransferMode.MOVE, Set.of());
                assertTrue(manager.canDock(before).allowed());
                DockResult beforeResult = manager.dock(before);
                assertTrue(beforeResult.decision().allowed());
                assertEquals(main.groupId(), beforeResult.group());
                assertEquals(List.of(sourceTab, targetTab), main.getTabs());

                DockRequest after = new DockRequest(source.id(), main.groupId(), null,
                        main.groupId(), target.id(), DockPlacement.AFTER, TransferMode.MOVE, Set.of());
                assertTrue(manager.canDock(after).allowed());
                DockResult afterResult = manager.dock(after);
                assertTrue(afterResult.decision().allowed());
                assertEquals(List.of(targetTab, sourceTab), main.getTabs());

                DockRequest beforeAgain = new DockRequest(source.id(), main.groupId(), null,
                        main.groupId(), target.id(), DockPlacement.BEFORE, TransferMode.MOVE, Set.of());
                assertTrue(manager.dock(beforeAgain).decision().allowed());
                assertEquals(List.of(sourceTab, targetTab), main.getTabs());

                DockRequest missingTarget = new DockRequest(source.id(), main.groupId(), null,
                        main.groupId(), ContentId.of("nbfx:view.missing"), DockPlacement.BEFORE,
                        TransferMode.MOVE, Set.of());
                assertTrue(!manager.canDock(missingTarget).allowed());
                DockRequest selfTarget = new DockRequest(source.id(), main.groupId(), null,
                        main.groupId(), source.id(), DockPlacement.AFTER, TransferMode.MOVE, Set.of());
                assertTrue(!manager.canDock(selfTarget).allowed());
            } finally {
                NbfxTabPane.unregister(main);
                NbfxTabPane.unregister(other);
            }
            return null;
        });
    }

    @Test
    public void docksWithSplitAndEdgePlacements() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
            NbfxTabPane other = new NbfxTabPane(PaneRole.NAVIGATOR);
            DefaultContentManager contentManager = new DefaultContentManager();
            try {
                java.util.Map<String, NbfxTabPane> panes = new java.util.LinkedHashMap<>();
                panes.put(PaneRole.MAIN.name(), main);
                panes.put(PaneRole.NAVIGATOR.name(), other);
                Docking.create(new WindowComposition(List.of(), null), panes);

                WindowManager manager = new DefaultWindowManager(contentManager);
                ViewContent content = new ViewContent(new FakeView("split.view", "Split"));
                contentManager.open(content);
                Tab tab = new Tab("Split");
                NbfxTabPane.setViewId(tab, "split.view");
                NbfxTabPane.attachTab(main, tab);

                DockRequest split = new DockRequest(content.id(), main.groupId(), null,
                        main.groupId(), DockPlacement.SPLIT_RIGHT, TransferMode.MOVE, Set.of());
                DockDecision decision = manager.canDock(split);
                assertTrue(decision.allowed());
                DockResult result = manager.dock(split);
                assertTrue(result.decision().allowed());
                DockGroupId createdGroup = result.group();
                assertTrue(createdGroup != null && !createdGroup.equals(main.groupId()));
                assertEquals(createdGroup, manager.groupOf(content.id()).orElseThrow());

                DockRequest edge = new DockRequest(content.id(), createdGroup, PaneRole.MAIN,
                        null, DockPlacement.EDGE_BOTTOM, TransferMode.MOVE, Set.of());
                assertTrue(manager.canDock(edge).allowed());
                DockResult edgeResult = manager.dock(edge);
                assertTrue(edgeResult.decision().allowed());
                assertEquals(edgeResult.group(), manager.groupOf(content.id()).orElseThrow());

                DockRequest unsupported = new DockRequest(content.id(), null, null,
                        main.groupId(), DockPlacement.FLOAT, TransferMode.MOVE, Set.of());
                assertTrue(!manager.canDock(unsupported).allowed());
            } finally {
                for (javafx.scene.control.TabPane pane : new java.util.ArrayList<>(NbfxTabPane.panesByRole())) {
                    if (pane instanceof NbfxTabPane nbfx && nbfx.role() == PaneRole.DOCKED) {
                        NbfxTabPane.unregister(pane);
                    }
                }
                NbfxTabPane.unregister(main);
                NbfxTabPane.unregister(other);
                disposeDocking();
            }
            return null;
        });
    }

    @Test
    public void floatsContentAndRedocksIntoGroup() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
            NbfxTabPane other = new NbfxTabPane(PaneRole.NAVIGATOR);
            DefaultContentManager contentManager = new DefaultContentManager();
            try {
                java.util.Map<String, NbfxTabPane> panes = new java.util.LinkedHashMap<>();
                panes.put(PaneRole.MAIN.name(), main);
                panes.put(PaneRole.NAVIGATOR.name(), other);
                Docking.create(new WindowComposition(List.of(), null), panes);
                DefaultWindowManager manager = new DefaultWindowManager(contentManager);
                ViewContent content = new ViewContent(new FakeView("float.content", "Float"));
                contentManager.open(content);
                Tab tab = new Tab("Float");
                NbfxTabPane.setViewId(tab, "float.content");
                NbfxTabPane.attachTab(main, tab);

                DockWindow window = manager.floatContent(content.id(), floatingOptions());
                DockGroupId floatingGroup = manager.groupOf(content.id()).orElseThrow();
                assertTrue(window.window().isShowing());
                assertEquals(WindowKind.FLOATING, window.kind());
                assertEquals(LayoutNode.group(floatingGroup), window.layout());
                assertEquals(floatingGroup, manager.groupOf(content.id()).orElseThrow());
                assertEquals(List.of(window), manager.windows());
                WindowState state = window.state();
                assertTrue(state.areas().getFirst().detached());
                assertEquals(floatingGroup.value(), state.areas().getFirst().areaId());
                assertTrue(window.content().root() instanceof TabPane);

                window.hide();
                assertFalse(window.window().isShowing());
                assertEquals(List.of(window), manager.windows());

                DockRequest request = new DockRequest(content.id(), floatingGroup, null,
                        other.groupId(), DockPlacement.INTO_GROUP, TransferMode.MOVE, Set.of());
                DockResult result = manager.redock(window, request);
                assertTrue(result.decision().allowed());
                assertEquals(other.groupId(), result.group());
                assertEquals(other.groupId(), manager.groupOf(content.id()).orElseThrow());
                assertFalse(window.window().isShowing());
                assertTrue(manager.windows().isEmpty());
            } finally {
                NbfxTabPane.unregister(main);
                NbfxTabPane.unregister(other);
                disposeDocking();
            }
            return null;
        });
    }

    @Test
    public void dockWindowHideShowAndCloseStayManaged() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
            NbfxTabPane other = new NbfxTabPane(PaneRole.NAVIGATOR);
            DefaultContentManager contentManager = new DefaultContentManager();
            try {
                java.util.Map<String, NbfxTabPane> panes = new java.util.LinkedHashMap<>();
                panes.put(PaneRole.MAIN.name(), main);
                panes.put(PaneRole.NAVIGATOR.name(), other);
                Docking.create(new WindowComposition(List.of(), null), panes);
                DefaultWindowManager manager = new DefaultWindowManager(contentManager);
                ViewContent content = new ViewContent(new FakeView("float.lifecycle", "Lifecycle"));
                contentManager.open(content);
                Tab tab = new Tab("Lifecycle");
                NbfxTabPane.setViewId(tab, "float.lifecycle");
                NbfxTabPane.attachTab(main, tab);

                DockWindow window = manager.floatContent(content.id(), floatingOptions());
                assertEquals("0", window.content().saveState().payload().get("selectedTab"));
                window.hide();
                assertFalse(window.window().isShowing());
                assertEquals(List.of(window), manager.windows());
                assertEquals(window.layout(), LayoutNode.group(manager.groupOf(content.id()).orElseThrow()));

                window.show();
                assertTrue(window.window().isShowing());
                window.close();
                assertFalse(window.window().isShowing());
                assertTrue(manager.windows().isEmpty());
                try {
                    window.show();
                    fail("a closed window was shown again");
                } catch (IllegalStateException expected) {
                    // as documented
                }
            } finally {
                NbfxTabPane.unregister(main);
                NbfxTabPane.unregister(other);
                disposeDocking();
            }
            return null;
        });
    }

    @Test
    public void floatsGroupAndRedocksToEdgePreservingGroup() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            NbfxTabPane main = new NbfxTabPane(PaneRole.MAIN);
            NbfxTabPane other = new NbfxTabPane(PaneRole.NAVIGATOR);
            DefaultContentManager contentManager = new DefaultContentManager();
            try {
                java.util.Map<String, NbfxTabPane> panes = new java.util.LinkedHashMap<>();
                panes.put(PaneRole.MAIN.name(), main);
                panes.put(PaneRole.NAVIGATOR.name(), other);
                Docking.create(new WindowComposition(List.of(), null), panes);
                DefaultWindowManager manager = new DefaultWindowManager(contentManager);
                ViewContent content = new ViewContent(new FakeView("float.group", "Group"));
                contentManager.open(content);
                Tab tab = new Tab("Group");
                NbfxTabPane.setViewId(tab, "float.group");
                NbfxTabPane.attachTab(main, tab);

                DockRequest split = new DockRequest(content.id(), main.groupId(), null,
                        main.groupId(), DockPlacement.SPLIT_RIGHT, TransferMode.MOVE, Set.of());
                DockGroupId group = manager.dock(split).group();
                NbfxTabPane docked = (NbfxTabPane) tab.getTabPane();
                DockWindow window = manager.floatGroup(group, floatingOptions());
                assertTrue(window.window().isShowing());
                assertFalse(Docking.area().contains(docked));
                assertEquals(group, manager.groupOf(content.id()).orElseThrow());
                assertEquals(LayoutNode.group(group), window.layout());

                DockRequest edge = new DockRequest(content.id(), group, PaneRole.MAIN,
                        null, DockPlacement.EDGE_BOTTOM, TransferMode.MOVE, Set.of());
                DockResult result = manager.redock(window, edge);
                assertTrue(result.decision().allowed());
                assertEquals(group, result.group());
                assertTrue(Docking.area().contains(docked));
                assertEquals(group, manager.groupOf(content.id()).orElseThrow());
                assertFalse(window.window().isShowing());
                assertTrue(manager.windows().isEmpty());
            } finally {
                for (javafx.scene.control.TabPane pane : new java.util.ArrayList<>(NbfxTabPane.panesByRole())) {
                    if (pane instanceof NbfxTabPane nbfx && nbfx.role() == PaneRole.DOCKED) {
                        NbfxTabPane.unregister(pane);
                    }
                }
                NbfxTabPane.unregister(main);
                NbfxTabPane.unregister(other);
                disposeDocking();
            }
            return null;
        });
    }

    private static void disposeDocking() {
        DockRuntime runtime = Docking.runtime();
        if (runtime != null) {
            runtime.dispose();
        }
    }

    private static WindowOptions floatingOptions() {
        return new WindowOptions(WindowKind.FLOATING, null, "Floating", null, null,
                false, 400, 300, true, false, null);
    }

    private static final class FakeView implements ViewProvider {

        private final String id;
        private final String title;

        FakeView(String id, String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public Node getView() {
            return new Pane();
        }
    }
}
