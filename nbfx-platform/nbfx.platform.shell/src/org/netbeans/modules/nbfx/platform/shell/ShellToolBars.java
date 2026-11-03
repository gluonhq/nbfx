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
import java.util.Comparator;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import org.netbeans.api.nbfx.actions.ActionLayerReader;
import org.netbeans.api.nbfx.actions.ActionRef;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.actions.Command;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Builds tool bars from the {@code NbFx/Toolbars} layer. There are no hardcoded fallback bars.
 *
 * @since 1.0
 */
public final class ShellToolBars {

    private final ShellContext context;
    private final ToolBarContainer container;

    public ShellToolBars(ShellContext context) {
        this.context = context;
        this.container = build();
    }

    /** The built tool bar container. */
    public ToolBarContainer container() {
        return container;
    }

    private ToolBarContainer build() {
        List<ToolBar> bars = new ArrayList<>();
        FileObject root = FileUtil.getConfigFile("NbFx/Toolbars");
        if (root != null) {
            List<FileObject> folders = new ArrayList<>();
            for (FileObject child : root.getChildren()) {
                if (child.isFolder()) {
                    folders.add(child);
                }
            }
            folders.sort(Comparator
                    .comparingInt((FileObject folder) -> intAttribute(folder, "position", Integer.MAX_VALUE))
                    .thenComparing(FileObject::getName));
            for (FileObject folder : folders) {
                ToolBar bar = createBar(folder);
                if (!bar.getItems().isEmpty()) {
                    bars.add(bar);
                }
            }
        }
        return new ToolBarContainer(bars.toArray(ToolBar[]::new));
    }

    private ToolBar createBar(FileObject folder) {
        ToolBar bar = new ToolBar();
        bar.setId(folder.getName());
        Label handle = new Label("\u22ee");
        handle.getStyleClass().add("toolbar-drag-handle");
        handle.setFocusTraversable(false);
        handle.setTooltip(new Tooltip(stringAttribute(folder, "displayName", folder.getName())));
        bar.getItems().add(handle);
        ActionRegistry registry = context.actions();
        if (registry != null) {
            for (ActionRef ref : ActionLayerReader.read(folder)) {
                Command command = registry.find(ref.actionId()).orElse(null);
                if (command != null) {
                    bar.getItems().add(button(command));
                }
            }
        }
        return bar;
    }

    private static Button button(Command command) {
        Button button = new Button();
        button.getStyleClass().add("toolbar-button");
        button.setFocusTraversable(false);
        if (command.getIcon() != null) {
            button.setGraphic(command.getIcon());
        } else {
            button.setText(command.getText());
        }
        button.disableProperty().bind(command.disabledProperty());
        button.setOnAction(event -> command.run());
        String tooltip = command.getAccelerator() == null
                ? command.getText()
                : command.getText() + " (" + command.getAccelerator().getDisplayText() + ")";
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private static int intAttribute(FileObject file, String name, int fallback) {
        return file.getAttribute(name) instanceof Integer value ? value : fallback;
    }

    private static String stringAttribute(FileObject file, String name, String fallback) {
        return file.getAttribute(name) instanceof String value && !value.isBlank() ? value : fallback;
    }
}
