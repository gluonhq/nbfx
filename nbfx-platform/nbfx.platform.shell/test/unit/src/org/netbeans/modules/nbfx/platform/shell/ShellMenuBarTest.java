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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The generic menu builder: static references and dynamic sections merge by position, a dynamic
 * section follows its items, a submenu containing only a dynamic section is still shown, and the
 * section is disposed with the bar.
 */
public class ShellMenuBarTest {

    @Test
    public void dynamicSectionsMergeAndFollowTheirItems() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            FileObject root = configRoot();
            FileObject folder = createFolder(root, "NbFx/Menus/TestMenu");
            FileObject sub = createFolder(folder, "OnlyDynamic");
            try {
                createRef(folder, "static.ref", "test.static", 10);
                createDynamic(folder, "section.dynamic", TestFactory.class.getName(), 20);
                createDynamic(sub, "section.dynamic", SubFactory.class.getName(), 10);

                TestFactory factory = new TestFactory();
                ShellContext context = new ShellContext(new org.openide.util.lookup.ProxyLookup(
                        Lookups.fixed(factory, new SubFactory(), resolver()), Lookup.getDefault()));
                ShellMenuBar bar = new ShellMenuBar(context);
                try {
                    Menu menu = menu(bar, "TestMenu");
                    assertNotNull("the test menu was not built", menu);
                    assertEquals(List.of("Static", "Dynamic", "OnlyDynamic"), texts(menu));
                    Menu onlyDynamic = menuContaining(menu, "OnlyDynamic");
                    assertNotNull("the dynamic-only submenu was not built", onlyDynamic);
                    assertEquals(List.of("Sub"), texts(onlyDynamic));

                    factory.items.add(new MenuItem("More"));
                    assertEquals(List.of("Static", "Dynamic", "More", "OnlyDynamic"), texts(menu));
                } finally {
                    bar.dispose();
                }
                assertTrue("the dynamic section was not disposed", factory.disposed);
            } finally {
                deleteTree(folder);
            }
            return null;
        });
    }


    @Test
    public void submenusMergeByPositionAndSeparator() throws InterruptedException {
        FxTestSupport.onFx(() -> {
            FileObject root = configRoot();
            FileObject folder = createFolder(root, "NbFx/Menus/PositionMenu");
            FileObject early = createFolder(folder, "EarlySub");
            FileObject late = createFolder(folder, "LateSub");
            try {
                set(early, "position", 5);
                createRef(early, "sub.ref", "test.static", 10);
                set(late, "position", 200);
                set(late, "separatorBefore", true);
                createRef(late, "sub.ref", "test.static", 10);
                createRef(folder, "first.ref", "test.static", 10);
                createRef(folder, "last.ref", "test.static", 300);

                ShellMenuBar bar = new ShellMenuBar(
                        new ShellContext(Lookups.fixed(resolver())));
                try {
                    Menu menu = menu(bar, "PositionMenu");
                    assertNotNull("the test menu was not built", menu);
                    assertEquals(List.of("EarlySub", "Static", "---", "LateSub", "Static"),
                            itemDescriptions(menu));
                } finally {
                    bar.dispose();
                }
            } finally {
                deleteTree(folder);
            }
            return null;
        });
    }

    @Test
    public void configureWindowRegistrationKeepsSeparator() {
        FileObject file = FileUtil.getConfigFile("NbFx/Menus/Window/configure-window.dynamic");
        assertNotNull("the Configure Window registration was not generated", file);
        assertEquals(Boolean.TRUE, file.getAttribute("separatorBefore"));
        assertEquals(60, file.getAttribute("position"));
    }

    private static ShellCommandResolver resolver() {
        return id -> "test.static".equals(id)
                ? new RunnableCommand("test.static", "Static", null, () -> {
                })
                : null;
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

    private static FileObject configRoot() {
        return FileUtil.getConfigRoot();
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

    private static void createRef(FileObject folder, String name, String actionId, int position) {
        FileObject file = createData(folder, name);
        set(file, "actionId", actionId);
        set(file, "position", position);
    }

    private static void createDynamic(FileObject folder, String name, String factory, int position) {
        FileObject file = createData(folder, name);
        set(file, "factory", factory);
        set(file, "position", position);
    }

    private static void set(FileObject file, String name, Object value) {
        try {
            file.setAttribute(name, value);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Menu menu(ShellMenuBar bar, String id) {
        for (Menu menu : bar.menuBar().getMenus()) {
            if (id.equals(menu.getId())) {
                return menu;
            }
        }
        return null;
    }

    private static Menu menuContaining(Menu parent, String id) {
        for (MenuItem item : parent.getItems()) {
            if (item instanceof Menu menu) {
                if (id.equals(menu.getId())) {
                    return menu;
                }
                Menu nested = menuContaining(menu, id);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static List<String> texts(Menu menu) {
        return menu.getItems().stream().map(MenuItem::getText).toList();
    }

    private static List<String> itemDescriptions(Menu menu) {
        return menu.getItems().stream()
                .map(item -> item instanceof SeparatorMenuItem ? "---" : item.getText())
                .toList();
    }

    private static final class SubFactory implements DynamicMenu.Factory {

        @Override
        public DynamicMenu create(Lookup context) {
            ObservableList<MenuItem> items = FXCollections.observableArrayList(new MenuItem("Sub"));
            return new DynamicMenu() {
                @Override
                public ObservableList<MenuItem> items() {
                    return items;
                }
            };
        }
    }

    private static final class TestFactory implements DynamicMenu.Factory {

        private final ObservableList<MenuItem> items = FXCollections.observableArrayList(
                new MenuItem("Dynamic"));
        private boolean disposed;

        @Override
        public DynamicMenu create(Lookup context) {
            return new DynamicMenu() {
                @Override
                public ObservableList<MenuItem> items() {
                    return items;
                }

                @Override
                public void dispose() {
                    disposed = true;
                }
            };
        }
    }
}
