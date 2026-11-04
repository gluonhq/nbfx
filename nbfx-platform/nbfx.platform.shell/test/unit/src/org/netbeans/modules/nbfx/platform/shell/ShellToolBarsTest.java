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
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.modules.nbfx.platform.shell.api.ShellChromeProfile;
import org.netbeans.modules.nbfx.platform.shell.api.ShellContext;
import org.netbeans.modules.nbfx.platform.shell.api.ShellCommandResolver;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tool bars follow the chrome profile: excluded bars are absent and a non-movable profile has no
 * leading drag handle.
 */
public class ShellToolBarsTest {

    @Test
    public void profileFiltersToolBarsAndDragHandle() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            FileObject root = FileUtil.getConfigRoot();
            FileObject folder = createFolder(root, "NbFx/Toolbars/profilebar");
            try {
                createRef(folder, "action.ref", "test.action");

                ShellChromeProfile noHandles = new ShellChromeProfile() {
                    @Override
                    public boolean includeMenu(String menuId) {
                        return true;
                    }

                    @Override
                    public boolean includeMenuItem(String menuId, String itemId) {
                        return true;
                    }

                    @Override
                    public boolean includeToolBar(String toolBarId) {
                        return true;
                    }

                    @Override
                    public boolean movableToolBars() {
                        return false;
                    }
                };
                ShellToolBars bars = new ShellToolBars(new ShellContext(
                        Lookups.fixed(resolver(), noHandles)));
                ToolBar bar = bars.container().getToolBars().get(0);
                assertEquals(1, bar.getItems().size());
                assertTrue(bar.getItems().get(0) instanceof Button);

                ShellChromeProfile excluded = new ShellChromeProfile() {
                    @Override
                    public boolean includeMenu(String menuId) {
                        return true;
                    }

                    @Override
                    public boolean includeMenuItem(String menuId, String itemId) {
                        return true;
                    }

                    @Override
                    public boolean includeToolBar(String toolBarId) {
                        return false;
                    }
                };
                ShellToolBars none = new ShellToolBars(new ShellContext(
                        Lookups.fixed(resolver(), excluded)));
                assertTrue(none.container().getToolBars().isEmpty());
            } finally {
                deleteTree(folder);
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
