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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The generic tool bar builder skips a bar with no resolvable commands, so its drag handle cannot
 * make an empty bar look populated.
 */
public class ShellToolBarsTest {

    @Test
    public void emptyToolBarsAreSkipped() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            FileObject root = FileUtil.getConfigRoot();
            FileObject empty = createFolder(root, "NbFx/Toolbars/emptybar");
            FileObject full = createFolder(root, "NbFx/Toolbars/fullbar");
            try {
                createRef(full, "action.ref", "test.action");

                ShellToolBars bars = new ShellToolBars(
                        new ShellContext(Lookups.fixed(resolver())));

                assertEquals(List.of("fullbar"), bars.container().getToolBars().stream()
                        .map(ToolBar::getId).toList());
                ToolBar bar = bars.container().getToolBars().get(0);
                assertEquals(2, bar.getItems().size());
                assertTrue(bar.getItems().get(0).getStyleClass().contains("toolbar-drag-handle"));
                assertTrue(bar.getItems().get(1) instanceof Button);
            } finally {
                deleteTree(empty);
                deleteTree(full);
            }
            return null;
        });
    }

    private static ShellCommandResolver resolver() {
        return id -> "test.action".equals(id)
                ? new RunnableCommand(id, "Test", null, () -> {
                })
                : null;
    }

    private static void createRef(FileObject folder, String name, String actionId) {
        FileObject file = createData(folder, name);
        set(file, "actionId", actionId);
        set(file, "position", 10);
    }

    private static FileObject createFolder(FileObject parent, String path) {
        try {
            return FileUtil.createFolder(parent, path);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static FileObject createData(FileObject folder, String name) {
        try {
            return FileUtil.createData(folder, name);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void set(FileObject file, String name, Object value) {
        try {
            file.setAttribute(name, value);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void deleteTree(FileObject file) {
        if (file == null) {
            return;
        }
        for (FileObject child : file.getChildren()) {
            deleteTree(child);
        }
        try {
            file.delete();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
