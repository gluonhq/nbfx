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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.netbeans.api.nbfx.actions.ActionLayerReader;
import org.netbeans.api.nbfx.actions.ActionRef;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 * Builds a menu bar from the {@code NbFx/Menus} layer and the dynamic sections registered in the
 * same folders. This is the generic builder; it has no IDE-specific menu.
 *
 * @since 1.0
 */
public final class ShellMenuBar {

    private static final String DYNAMIC_EXT = LayerPaths.DYNAMIC_SUFFIX.startsWith(".")
            ? LayerPaths.DYNAMIC_SUFFIX.substring(1) : LayerPaths.DYNAMIC_SUFFIX;

    private final ShellContext context;
    private final Map<FileObject, MenuModel> models = new IdentityHashMap<>();
    private final Map<FileObject, Menu> menus = new IdentityHashMap<>();
    private final MenuBar menuBar;

    public ShellMenuBar(ShellContext context) {
        this.context = context;
        this.menuBar = build();
    }

    /** The built menu bar. */
    public MenuBar menuBar() {
        return menuBar;
    }

    /** Disposes the dynamic sections created for this bar. */
    public void dispose() {
        for (MenuModel model : models.values()) {
            for (DynamicEntry entry : model.dynamic()) {
                if (entry.content() != null) {
                    entry.content().dispose();
                }
            }
        }
        models.clear();
        menus.clear();
    }

    private MenuBar build() {
        List<Menu> topLevel = new ArrayList<>();
        FileObject root = FileUtil.getConfigFile(LayerPaths.MENUS);
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
                Menu menu = menu(folder);
                if (!menu.getItems().isEmpty()) {
                    topLevel.add(menu);
                }
            }
        }
        MenuBar bar = new MenuBar(topLevel.toArray(Menu[]::new));
        bar.setUseSystemMenuBar(true);
        return bar;
    }

    /** The stable menu instance for {@code folder}, built once and refilled as sections change. */
    private Menu menu(FileObject folder) {
        Menu existing = menus.get(folder);
        if (existing != null) {
            return existing;
        }
        String title = stringAttribute(folder, "displayName", folder.getName());
        Menu menu = new Menu(title);
        menu.setId(folder.getName());
        menus.put(folder, menu);
        MenuModel model = model(folder, menu);
        fill(menu, model);
        return menu;
    }

    /**
     * Fills {@code menu} from {@code model}. Entries and submenu folders share one position-ordered
     * list; a dynamic section may update its items at any time, and rebuilding the menu keeps every
     * section at its declared position without the section knowing it.
     */
    private void fill(Menu menu, MenuModel model) {
        menu.getItems().clear();
        for (Entry entry : model.entries()) {
            List<MenuItem> content = content(entry);
            if (content.isEmpty()) {
                continue;
            }
            if (entry.separatorBefore() && !menu.getItems().isEmpty()
                    && !(menu.getItems().get(menu.getItems().size() - 1) instanceof SeparatorMenuItem)) {
                menu.getItems().add(new SeparatorMenuItem());
            }
            menu.getItems().addAll(content);
        }
    }

    /** The items an entry contributes; empty when a command is missing or a section is empty. */
    private List<MenuItem> content(Entry entry) {
        if (entry instanceof StaticEntry staticEntry) {
            MenuItem item = menuItem(staticEntry.commandId());
            return item == null ? List.of() : List.of(item);
        }
        if (entry instanceof DynamicEntry dynamic) {
            return dynamic.content() == null ? List.of() : List.copyOf(dynamic.content().items());
        }
        if (entry instanceof SubmenuEntry submenu) {
            Menu child = menu(submenu.folder());
            return child.getItems().isEmpty() ? List.of() : List.of(child);
        }
        return List.of();
    }

    private MenuModel model(FileObject folder, Menu owner) {
        List<Entry> entries = new ArrayList<>();
        for (ActionRef ref : ActionLayerReader.read(folder)) {
            entries.add(new StaticEntry(ref.position(), ref.actionId(), ref.separatorBefore()));
        }
        List<DynamicEntry> dynamicEntries = readDynamic(folder, owner);
        entries.addAll(dynamicEntries);
        for (FileObject child : folder.getChildren()) {
            if (child.isFolder()) {
                entries.add(new SubmenuEntry(
                        intAttribute(child, "position", Integer.MAX_VALUE),
                        child.getName(),
                        Boolean.TRUE.equals(child.getAttribute("separatorBefore")),
                        child));
            }
        }
        entries.sort(Comparator.comparingInt(Entry::position).thenComparing(Entry::id));
        MenuModel model = new MenuModel(List.copyOf(entries), List.copyOf(dynamicEntries));
        models.put(folder, model);
        return model;
    }

    private List<DynamicEntry> readDynamic(FileObject folder, Menu owner) {
        List<DynamicEntry> entries = new ArrayList<>();
        for (FileObject child : folder.getChildren()) {
            if (!child.isData() || !child.hasExt(DYNAMIC_EXT)) {
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
                content.items().addListener((ListChangeListener<MenuItem>) change -> {
                    MenuModel current = models.get(folder);
                    if (current != null) {
                        fill(owner, current);
                    }
                });
            }
            entries.add(new DynamicEntry(position, child.getName(), separatorBefore, content));
        }
        return entries;
    }

    private DynamicMenu createDynamic(String factoryName) {
        for (DynamicMenu.Factory factory : context.lookup().lookupAll(DynamicMenu.Factory.class)) {
            if (factory.getClass().getName().equals(factoryName)) {
                try {
                    return factory.create(context.lookup());
                } catch (RuntimeException ex) {
                    Exceptions.printStackTrace(ex);
                    return null;
                }
            }
        }
        Exceptions.printStackTrace(new IllegalStateException(
                "No DynamicMenu.Factory registered for " + factoryName));
        return null;
    }

    private MenuItem menuItem(String commandId) {
        Command command = context.command(commandId);
        if (command == null) {
            return null;
        }
        MenuItem item = new MenuItem(command.getText(),
                context.icon(command, ShellIconProvider.MENU_SIZE));
        item.disableProperty().bind(command.disabledProperty());
        item.setOnAction(event -> command.run());
        if (command.getAccelerator() != null) {
            item.setAccelerator(command.getAccelerator());
        }
        return item;
    }

    private static int intAttribute(FileObject file, String name, int fallback) {
        return file.getAttribute(name) instanceof Integer value ? value : fallback;
    }

    private static String stringAttribute(FileObject file, String name, String fallback) {
        return file.getAttribute(name) instanceof String value && !value.isBlank() ? value : fallback;
    }

    private record MenuModel(List<Entry> entries, List<DynamicEntry> dynamic) {
    }

    private sealed interface Entry permits StaticEntry, DynamicEntry, SubmenuEntry {
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

    private record SubmenuEntry(int position, String id, boolean separatorBefore, FileObject folder)
            implements Entry {
    }
}
