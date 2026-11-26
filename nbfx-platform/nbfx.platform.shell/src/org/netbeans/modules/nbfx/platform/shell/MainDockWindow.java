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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javafx.geometry.Orientation;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.netbeans.api.nbfx.layout.LayoutNode;
import org.netbeans.api.nbfx.layout.SplitOrientation;
import org.netbeans.api.nbfx.shell.AreaState;
import org.netbeans.api.nbfx.shell.DockWindow;
import org.netbeans.api.nbfx.shell.WindowContent;
import org.netbeans.api.nbfx.shell.WindowContentState;
import org.netbeans.api.nbfx.shell.WindowId;
import org.netbeans.api.nbfx.shell.WindowKind;
import org.netbeans.api.nbfx.shell.WindowState;
import org.netbeans.api.nbfx.view.ActiveComponent;
import org.netbeans.api.nbfx.view.ActiveComponentContext;
import org.netbeans.modules.nbfx.platform.workspace.DockArea;
import org.netbeans.modules.nbfx.platform.workspace.DockTree;
import org.openide.util.Lookup;

/** Main-shell adapter for the public {@link DockWindow} contract. */
final class MainDockWindow implements DockWindow {

    private static final WindowId ID = WindowId.of("nbfx:window.main");

    private final SlidingArea root;
    private final DockArea<TabPane> area;
    private final Stage stage;
    private final Supplier<WindowState> shellState;
    private final WindowContent content;

    MainDockWindow(SlidingArea root, DockArea<TabPane> area, Stage stage,
            Supplier<WindowState> shellState) {
        this.root = root;
        this.area = area;
        this.stage = stage;
        this.shellState = shellState;
        this.content = new Content();
    }

    SlidingArea root() {
        return root;
    }

    DockArea<TabPane> area() {
        return area;
    }

    Stage stage() {
        return stage;
    }

    @Override
    public WindowId id() {
        return ID;
    }

    @Override
    public WindowKind kind() {
        return WindowKind.MAIN;
    }

    @Override
    public javafx.stage.Window window() {
        return stage;
    }

    @Override
    public LayoutNode layout() {
        List<TabPane> leaves = area.leaves();
        int[] index = {0};
        return layout(area.tree(), leaves, index);
    }

    private static LayoutNode layout(DockTree tree, List<TabPane> leaves, int[] index) {
        if (tree instanceof DockTree.Leaf) {
            TabPane pane = leaves.get(index[0]++);
            return LayoutNode.group(((NbfxTabPane) pane).groupId());
        }
        DockTree.Split split = (DockTree.Split) tree;
        List<LayoutNode> children = new ArrayList<>();
        for (DockTree child : split.children()) {
            children.add(layout(child, leaves, index));
        }
        SplitOrientation orientation = split.orientation() == Orientation.HORIZONTAL
                ? SplitOrientation.HORIZONTAL : SplitOrientation.VERTICAL;
        return new LayoutNode.Split(orientation, children, split.dividers());
    }

    @Override
    public WindowContent content() {
        return content;
    }

    @Override
    public void show() {
        stage.show();
    }

    @Override
    public void hide() {
        stage.hide();
    }

    @Override
    public void close() {
        stage.close();
    }

    @Override
    public WindowState state() {
        List<AreaState> areas = new ArrayList<>();
        for (TabPane pane : area.leaves()) {
            List<String> viewIds = pane.getTabs().stream()
                    .map(NbfxTabPane::viewId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            Tab selected = pane.getSelectionModel().getSelectedItem();
            String activeViewId = selected == null ? null : NbfxTabPane.viewId(selected);
            areas.add(AreaState.docked(((NbfxTabPane) pane).groupId().value(), viewIds, activeViewId));
        }
        WindowState shell = shellState.get();
        double x = shell != null && Double.isFinite(shell.x()) ? shell.x() : stage.getX();
        double y = shell != null && Double.isFinite(shell.y()) ? shell.y() : stage.getY();
        double width = shell != null && Double.isFinite(shell.width()) ? shell.width() : stage.getWidth();
        double height = shell != null && Double.isFinite(shell.height()) ? shell.height() : stage.getHeight();
        boolean maximized = shell != null && shell.maximized();
        String profileId = shell == null ? null : shell.profileId();
        Map<String, String> viewState = shell == null ? Map.of() : shell.viewState();
        return new WindowState(List.copyOf(areas), x, y, width, height, maximized, viewState, profileId);
    }

    private final class Content implements WindowContent {

        @Override
        public javafx.scene.Node root() {
            return root;
        }

        @Override
        public Lookup lookup() {
            if (!stage.isFocused()) {
                return Lookup.EMPTY;
            }
            ActiveComponentContext context = Lookup.getDefault().lookup(ActiveComponentContext.class);
            ActiveComponent component = context == null ? null : context.activeComponent().getValue();
            return component == null ? Lookup.EMPTY : component.getLookup();
        }

        @Override
        public WindowContentState saveState() {
            return WindowContentState.empty(id(), layout());
        }
    }
}
