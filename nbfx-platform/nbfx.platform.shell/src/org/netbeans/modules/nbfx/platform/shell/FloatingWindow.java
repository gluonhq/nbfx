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
import java.util.Map;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.layout.LayoutNode;
import org.netbeans.api.nbfx.shell.AreaState;
import org.netbeans.api.nbfx.shell.DockWindow;
import org.netbeans.api.nbfx.shell.WindowClosePolicy;
import org.netbeans.api.nbfx.shell.WindowContent;
import org.netbeans.api.nbfx.shell.WindowContentState;
import org.netbeans.api.nbfx.shell.WindowId;
import org.netbeans.api.nbfx.shell.WindowKind;
import org.netbeans.api.nbfx.shell.WindowOptions;
import org.netbeans.api.nbfx.shell.WindowState;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.modules.nbfx.platform.workspace.DockArea;
import org.openide.util.Lookup;

/**
 * Shell implementation of a floating {@link DockWindow}. The pane and its tabs are never rebuilt;
 * showing, hiding and redocking only change their window/layout parent.
 */
final class FloatingWindow implements DockWindow {

    private final DefaultWindowManager owner;
    private final NbfxTabPane pane;
    private final Stage stage;
    private final DockArea.Anchor<TabPane> anchor;
    private final WindowOptions options;
    private final WindowContent content;
    private boolean closed;

    FloatingWindow(DefaultWindowManager owner, NbfxTabPane pane, Stage stage,
            DockArea.Anchor<TabPane> anchor, WindowOptions options) {
        this.owner = owner;
        this.pane = pane;
        this.stage = stage;
        this.anchor = anchor;
        this.options = options;
        this.content = new Content();
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (options.closePolicy() == WindowClosePolicy.HIDE) {
                event.consume();
                hide();
            } else if (options.closePolicy() == WindowClosePolicy.REDOCK) {
                event.consume();
                owner.closeFloatingWindow(this);
            }
        });
    }

    DefaultWindowManager owner() {
        return owner;
    }

    NbfxTabPane pane() {
        return pane;
    }

    Stage stage() {
        return stage;
    }

    DockArea.Anchor<TabPane> anchor() {
        return anchor;
    }

    WindowOptions options() {
        return options;
    }

    void markClosed() {
        closed = true;
    }

    @Override
    public WindowId id() {
        return WindowId.of("nbfx:window." + pane.groupId().name());
    }

    @Override
    public WindowKind kind() {
        return WindowKind.FLOATING;
    }

    @Override
    public javafx.stage.Window window() {
        return stage;
    }

    @Override
    public LayoutNode layout() {
        return LayoutNode.group(pane.groupId());
    }

    @Override
    public WindowContent content() {
        return content;
    }

    @Override
    public void show() {
        if (closed) {
            throw new IllegalStateException("Window is closed");
        }
        stage.show();
    }

    @Override
    public void hide() {
        if (!closed) {
            stage.hide();
        }
    }

    @Override
    public void close() {
        if (!closed) {
            owner.closeFloatingWindow(this);
        }
    }

    @Override
    public WindowState state() {
        List<String> viewIds = pane.getTabs().stream()
                .map(NbfxTabPane::viewId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        Tab selected = pane.getSelectionModel().getSelectedItem();
        String activeViewId = selected == null ? null : NbfxTabPane.viewId(selected);
        AreaState area = new AreaState(pane.groupId().value(), viewIds, activeViewId,
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), true);
        String profileId = options.profile() == null ? null : options.profile().id();
        return new WindowState(List.of(area), stage.getX(), stage.getY(),
                stage.getWidth(), stage.getHeight(), false, Map.of(), profileId);
    }

    private final class Content implements WindowContent {

        @Override
        public javafx.scene.Node root() {
            return pane;
        }

        @Override
        public Lookup lookup() {
            Tab selected = pane.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return Lookup.EMPTY;
            }
            EditorDocument document = NbfxTabPane.documentOf(selected);
            if (document != null) {
                return document.getLookup();
            }
            Object provider = selected.getProperties().get(NbfxTabPane.VIEW_PROVIDER_KEY);
            return provider instanceof ViewProvider view ? view.getLookup() : Lookup.EMPTY;
        }

        @Override
        public WindowContentState saveState() {
            int selected = pane.getSelectionModel().getSelectedIndex();
            return new WindowContentState(id(), layout(),
                    Map.of("selectedTab", Integer.toString(Math.max(selected, 0))));
        }
    }
}
