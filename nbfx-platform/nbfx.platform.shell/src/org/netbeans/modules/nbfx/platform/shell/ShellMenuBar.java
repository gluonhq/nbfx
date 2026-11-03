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
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Builds a menu bar from the {@code NbFx/Menus} layer and the dynamic sections registered in the
 * same folders. This is the generic builder; it has no IDE-specific menu.
 *
 * @since 1.0
 */
public final class ShellMenuBar {

    private final ShellContext context;
    private final List<DynamicMenu> dynamicMenus = new ArrayList<>();
    private final javafx.scene.control.MenuBar menuBar;

    public ShellMenuBar(ShellContext context) {
        this.context = context;
        this.menuBar = build();
    }

    /** The built menu bar. */
    public javafx.scene.control.MenuBar menuBar() {
        return menuBar;
    }

    /** Disposes the dynamic sections created for this bar. */
    public void dispose() {
        for (DynamicMenu menu : dynamicMenus) {
            menu.dispose();
        }
        dynamicMenus.clear();
    }

    private javafx.scene.control.MenuBar build() {
        List<javafx.scene.control.Menu> menus = new ArrayList<>();
        FileObject root = FileUtil.getConfigFile("NbFx/Menus");
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
                javafx.scene.control.Menu menu = createMenu(folder);
                if (menu != null && !menu.getItems().isEmpty()) {
                    menus.add(menu);
                }
            }
        }
        javafx.scene.control.MenuBar bar = new javafx.scene.control.MenuBar(
                menus.toArray(javafx.scene.control.Menu[]::new));
        bar.setUseSystemMenuBar(true);
        return bar;
    }

    private javafx.scene.control.Menu createMenu(FileObject folder) {
        String title = stringAttribute(folder, "displayName", folder.getName());
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu(title);
        addItems(menu, folder);
        addSubmenus(menu, folder);
        return menu;
    }

    private void addItems(javafx.scene.control.Menu menu, FileObject folder) {
        List<Entry> entries = new ArrayList<>();
        for (org.netbeans.api.nbfx.actions.ActionRef ref
                : org.netbeans.api.nbfx.actions.ActionLayerReader.read(folder)) {
            entries.add(new StaticEntry(ref.position(), ref.actionId(), ref.separatorBefore()));
        }
        for (DynamicEntry dynamic : readDynamic(folder)) {
            entries.add(dynamic);
        }
        entries.sort(Comparator.comparingInt(Entry::position).thenComparing(Entry::id));
        for (Entry entry : entries) {
            if (entry.separatorBefore() && !menu.getItems().isEmpty()
                    && !(menu.getItems().get(menu.getItems().size() - 1)
                            instanceof javafx.scene.control.SeparatorMenuItem)) {
                menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            }
            if (entry instanceof StaticEntry staticEntry) {
                javafx.scene.control.MenuItem item = menuItem(staticEntry.commandId());
                if (item != null) {
                    menu.getItems().add(item);
                }
            } else if (entry instanceof DynamicEntry dynamic) {
                DynamicMenu content = dynamic.content();
                if (content != null) {
                    menu.getItems().addAll(content.items());
                    content.items().addListener((javafx.collections.ListChangeListener<javafx.scene.control.MenuItem>) change -> {
                        menu.getItems().removeAll(change.getRemoved());
                        menu.getItems().addAll(change.getAddedSubList());
                    });
                }
            }
        }
    }

    private List<DynamicEntry> readDynamic(FileObject folder) {
        List<DynamicEntry> entries = new ArrayList<>();
        for (FileObject child : folder.getChildren()) {
            if (!child.isData() || !child.hasExt("dynamic")) {
                continue;
            }
            String factoryName = stringAttribute(child, "factory", "");
            if (factoryName.isEmpty()) {
                continue;
            }
            int position = intAttribute(child, "position", Integer.MAX_VALUE);
            boolean separatorBefore = Boolean.TRUE.equals(child.getAttribute("separatorBefore"));
            DynamicMenu content = createDynamic(factoryName);
            if (content != null) {
                dynamicMenus.add(content);
            }
            entries.add(new DynamicEntry(position, child.getName(), separatorBefore, content));
        }
        return entries;
    }

    private DynamicMenu createDynamic(String factoryName) {
        for (DynamicMenu.Factory factory : Lookup.getDefault().lookupAll(DynamicMenu.Factory.class)) {
            if (factory.getClass().getName().equals(factoryName)) {
                try {
                    return factory.create(context.lookup());
                } catch (RuntimeException ex) {
                    org.openide.util.Exceptions.printStackTrace(ex);
                    return null;
                }
            }
        }
        org.openide.util.Exceptions.printStackTrace(new IllegalStateException(
                "No DynamicMenu.Factory registered for " + factoryName));
        return null;
    }

    private javafx.scene.control.MenuItem menuItem(String commandId) {
        org.netbeans.api.nbfx.actions.ActionRegistry registry = context.actions();
        if (registry == null) {
            return null;
        }
        org.netbeans.api.nbfx.actions.Command command = registry.find(commandId).orElse(null);
        if (command == null) {
            return null;
        }
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(command.getText(), command.getIcon());
        item.disableProperty().bind(command.disabledProperty());
        item.setOnAction(event -> command.run());
        if (command.getAccelerator() != null) {
            item.setAccelerator(command.getAccelerator());
        }
        return item;
    }

    private void addSubmenus(javafx.scene.control.Menu parent, FileObject folder) {
        List<FileObject> subfolders = new ArrayList<>();
        for (FileObject child : folder.getChildren()) {
            if (child.isFolder()) {
                subfolders.add(child);
            }
        }
        subfolders.sort(Comparator
                .comparingInt((FileObject f) -> intAttribute(f, "position", Integer.MAX_VALUE))
                .thenComparing(FileObject::getName));
        for (FileObject subfolder : subfolders) {
            javafx.scene.control.Menu menu = createMenu(subfolder);
            if (menu != null && !menu.getItems().isEmpty()) {
                parent.getItems().add(menu);
            }
        }
    }

    private static int intAttribute(FileObject file, String name, int fallback) {
        return file.getAttribute(name) instanceof Integer value ? value : fallback;
    }

    private static String stringAttribute(FileObject file, String name, String fallback) {
        return file.getAttribute(name) instanceof String value && !value.isBlank() ? value : fallback;
    }

    private sealed interface Entry permits StaticEntry, DynamicEntry {
        int position();
        String id();
        boolean separatorBefore();
    }

    private record StaticEntry(int position, String commandId, boolean separatorBefore) implements Entry {
        @Override
        public String id() {
            return commandId;
        }
    }

    private record DynamicEntry(int position, String id, boolean separatorBefore, DynamicMenu content)
            implements Entry {
    }
}
