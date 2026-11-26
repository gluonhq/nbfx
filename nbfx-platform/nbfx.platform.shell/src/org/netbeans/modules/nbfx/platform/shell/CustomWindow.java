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
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.netbeans.api.nbfx.shell.DockWindow;
import org.netbeans.api.nbfx.shell.WindowContent;
import org.netbeans.api.nbfx.shell.WindowId;
import org.netbeans.api.nbfx.shell.WindowKind;
import org.netbeans.api.nbfx.shell.WindowOptions;
import org.netbeans.api.nbfx.shell.WindowState;
import org.netbeans.modules.nbfx.platform.theme.NbfxTheme;

/** Product-owned custom window backed by a JavaFX stage. */
final class CustomWindow implements DockWindow {

    private final DefaultWindowManager owner;
    private final WindowId id;
    private final WindowOptions options;
    private final WindowContent content;
    private final Stage stage;
    private boolean closed;

    CustomWindow(DefaultWindowManager owner, WindowId id, WindowOptions options,
            WindowContent content, Consumer<Stage> configure) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.id = Objects.requireNonNull(id, "id");
        this.options = Objects.requireNonNull(options, "options");
        this.content = Objects.requireNonNull(content, "content");
        Node root = Objects.requireNonNull(content.root(), "content root");
        if (root.getScene() != null || root.getParent() != null) {
            throw new IllegalArgumentException("Custom window content root is already attached");
        }
        this.stage = new Stage();
        Parent sceneRoot = root instanceof Parent parent ? parent : new StackPane(root);
        Scene scene = new Scene(sceneRoot, options.width(), options.height());
        scene.getStylesheets().add(NbfxTabPane.stylesheet());
        NbfxTheme.apply(scene);
        stage.setScene(scene);
        Objects.requireNonNull(configure, "configure").accept(stage);
        stage.setOnCloseRequest(event -> dispose());
    }

    DefaultWindowManager owner() {
        return owner;
    }

    WindowId windowId() {
        return id;
    }

    WindowOptions options() {
        return options;
    }

    Stage stage() {
        return stage;
    }

    @Override
    public WindowId id() {
        return id;
    }

    @Override
    public WindowKind kind() {
        return WindowKind.CUSTOM;
    }

    @Override
    public javafx.stage.Window window() {
        return stage;
    }

    @Override
    public org.netbeans.api.nbfx.layout.LayoutNode layout() {
        return null;
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
            owner.saveCustomWindowBounds(this);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        stage.setOnCloseRequest(null);
        dispose();
        stage.close();
    }

    @Override
    public WindowState state() {
        String profileId = options.profile() == null ? null : options.profile().id();
        return new WindowState(List.of(), stage.getX(), stage.getY(),
                stage.getWidth(), stage.getHeight(), false, Map.of(), profileId);
    }

    private void dispose() {
        if (closed) {
            return;
        }
        closed = true;
        owner.saveCustomWindowBounds(this);
        try {
            content.close();
        } finally {
            owner.removeCustomWindow(this);
        }
    }
}
