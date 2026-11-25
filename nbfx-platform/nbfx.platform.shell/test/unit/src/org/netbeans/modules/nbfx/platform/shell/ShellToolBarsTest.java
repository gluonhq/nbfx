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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.api.nbfx.shell.ShellChromeProfile;
import org.netbeans.api.nbfx.shell.ShellContext;
import org.netbeans.api.nbfx.shell.ShellCommandResolver;
import org.netbeans.api.nbfx.shell.ShellProfileSpec;
import org.netbeans.api.nbfx.shell.WindowKind;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tool bars follow the chrome profile: excluded bars are absent, a non-movable profile has no
 * leading drag handle, and a bar with no resolvable commands is omitted entirely.
 */
public class ShellToolBarsTest {

    @Test
    public void profileFiltersToolBarsAndDragHandle() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
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

    @Test
    public void profileFiltersToolBarItems() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            FileObject root = FileUtil.getConfigRoot();
            FileObject folder = createFolder(root, "NbFx/Toolbars/filteredbar");
            try {
                createRef(folder, "action.ref", "test.action");

                ShellChromeProfile profile = new ShellChromeProfile() {
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
                    public boolean includeToolBarItem(String toolBarId, String itemId) {
                        return !"test.action".equals(itemId);
                    }
                };
                ShellToolBars bars = new ShellToolBars(new ShellContext(
                        Lookups.fixed(resolver(), profile)));
                assertTrue("a bar whose items are all filtered out must be skipped",
                        bars.container().getToolBars().isEmpty());
            } finally {
                deleteTree(folder);
            }
            return null;
        });
    }

    @Test
    public void actionReferenceProfilesFilterToolBarItems() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            FileObject folder = createFolder(FileUtil.getConfigRoot(), "NbFx/Toolbars/actionprofilebar");
            try {
                createRef(folder, "any.ref", "test.action");
                createRef(folder, "main.ref", "test.action");
                FileObject other = createData(folder, "other.ref");
                set(other, "actionId", "test.action");
                set(other, "position", 20);
                set(other, "profiles", "presentation");
                ShellProfileSpec profile = new ShellProfileSpec("main", WindowKind.MAIN,
                        Set.of(), null, true);
                ShellToolBars bars = new ShellToolBars(new ShellContext(
                        Lookups.fixed(resolver(), profile)));
                assertEquals(1, bars.container().getToolBars().size());
                assertEquals(3, bars.container().getToolBars().get(0).getItems().size());
            } finally {
                deleteTree(folder);
            }
            return null;
        });
    }

    @Test
    public void emptyToolBarsAreSkipped() throws InterruptedException {
        JavaFxTestSupport.onFx(() -> {
            FileObject root = FileUtil.getConfigRoot();
            FileObject empty = createFolder(root, "NbFx/Toolbars/emptybar");
            FileObject full = createFolder(root, "NbFx/Toolbars/fullbar");
            try {
                createRef(full, "action.ref", "test.action");

                ShellToolBars bars = new ShellToolBars(new ShellContext(
                        Lookups.fixed(resolver())));

                assertEquals(1, bars.container().getToolBars().size());
                ToolBar bar = bars.container().getToolBars().get(0);
                assertEquals("fullbar", bar.getId());
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
