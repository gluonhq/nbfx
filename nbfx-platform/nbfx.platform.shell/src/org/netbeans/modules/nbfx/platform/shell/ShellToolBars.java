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

import org.netbeans.modules.nbfx.platform.shell.api.ShellChromeProfile;
import org.netbeans.modules.nbfx.platform.shell.api.ShellContext;
import org.netbeans.modules.nbfx.platform.shell.api.ShellIconProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import org.netbeans.api.nbfx.actions.ActionLayerReader;
import org.netbeans.api.nbfx.actions.ActionRef;
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
    private final ShellChromeProfile profile;
    private final ToolBarContainer container;

    public ShellToolBars(ShellContext context) {
        this.context = context;
        this.profile = context.profile();
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
                if (child.isFolder() && profile.includeToolBar(child.getName())) {
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
        String displayName = stringAttribute(folder, "displayName", folder.getName());
        bar.setAccessibleText(displayName);
        List<Button> buttons = new ArrayList<>();
        for (ActionRef ref : ActionLayerReader.read(folder)) {
            if (!profile.includeToolBarItem(folder.getName(), ref.actionId())) {
                continue;
            }
            Command command = context.command(ref.actionId());
            if (command != null) {
                buttons.add(button(command));
            }
        }
        if (buttons.isEmpty()) {
            return bar;
        }
        if (profile.movableToolBars()) {
            Label handle = new Label("\u22ee");
            handle.getStyleClass().add("toolbar-drag-handle");
            handle.setFocusTraversable(false);
            handle.setTooltip(new Tooltip(displayName));
            bar.getItems().add(handle);
        }
        bar.getItems().addAll(buttons);
        return bar;
    }

    private Button button(Command command) {
        Button button = new Button();
        button.getStyleClass().add("toolbar-button");
        button.setFocusTraversable(false);
        javafx.scene.Node icon = context.icon(command, ShellIconProvider.TOOLBAR_SIZE);
        if (icon != null) {
            button.setGraphic(icon);
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
