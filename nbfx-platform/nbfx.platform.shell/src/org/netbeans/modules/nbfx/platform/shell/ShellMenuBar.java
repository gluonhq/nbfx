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
import org.netbeans.api.nbfx.views.DynamicMenu;
import org.netbeans.modules.nbfx.platform.shell.api.ShellContext;
import org.netbeans.modules.nbfx.platform.shell.api.ShellIconProvider;
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
     * Fills {@code menu} from {@code model}. Each entry keeps its rendered items, so a dynamic
     * update can replace only its own range instead of rebuilding static items and sibling menus.
     */
    private void fill(Menu menu, MenuModel model) {
        menu.getItems().clear();
        for (EntryState state : model.states()) {
            state.rendered = render(state.entry(), !menu.getItems().isEmpty());
            menu.getItems().addAll(state.rendered);
        }
        for (FileObject subfolder : model.subfolders()) {
            Menu submenu = menu(subfolder);
            if (!submenu.getItems().isEmpty()) {
                menu.getItems().add(submenu);
            }
        }
    }

    /** Replaces only the items owned by {@code entry} in {@code menu}. */
    private void refreshDynamic(Menu menu, MenuModel model, DynamicEntry entry) {
        int index = 0;
        EntryState changed = null;
        boolean hasPrevious = false;
        for (EntryState state : model.states()) {
            if (state.entry() == entry) {
                changed = state;
                break;
            }
            index += state.rendered.size();
            hasPrevious |= !state.rendered.isEmpty();
        }
        if (changed == null) {
            return;
        }
        menu.getItems().removeAll(changed.rendered);
        changed.rendered = render(entry, hasPrevious);
        menu.getItems().addAll(index, changed.rendered);
    }

    private List<MenuItem> render(Entry entry, boolean hasPrevious) {
        List<MenuItem> content = new ArrayList<>();
        if (entry instanceof StaticEntry staticEntry) {
            if (staticEntry.item() != null) {
                content.add(staticEntry.item());
            }
        } else if (entry instanceof DynamicEntry dynamic) {
            if (dynamic.submenu() != null) {
                if (!dynamic.submenu().getItems().isEmpty()) {
                    content.add(dynamic.submenu());
                }
            } else if (dynamic.content() != null) {
                content.addAll(dynamic.content().items());
            }
        }
        if (content.isEmpty()) {
            return List.of();
        }
        if (entry.separatorBefore() && hasPrevious) {
            List<MenuItem> withSeparator = new ArrayList<>(content.size() + 1);
            withSeparator.add(new SeparatorMenuItem());
            withSeparator.addAll(content);
            return withSeparator;
        }
        return content;
    }

    private MenuModel model(FileObject folder, Menu owner) {
        List<Entry> entries = new ArrayList<>();
        for (ActionRef ref : ActionLayerReader.read(folder)) {
            entries.add(new StaticEntry(ref.position(), ref.actionId(), ref.separatorBefore(),
                    menuItem(ref.actionId())));
        }
        List<DynamicEntry> dynamicEntries = readDynamic(folder, owner);
        entries.addAll(dynamicEntries);
        entries.sort(Comparator.comparingInt(Entry::position).thenComparing(Entry::id));

        List<FileObject> subfolders = new ArrayList<>();
        for (FileObject child : folder.getChildren()) {
            if (child.isFolder()) {
                subfolders.add(child);
            }
        }
        subfolders.sort(Comparator
                .comparingInt((FileObject f) -> intAttribute(f, "position", Integer.MAX_VALUE))
                .thenComparing(FileObject::getName));

        List<EntryState> states = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            states.add(new EntryState(entry));
        }
        MenuModel model = new MenuModel(List.copyOf(states), List.copyOf(subfolders),
                List.copyOf(dynamicEntries));
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
            String displayName = stringAttribute(child, "displayName", "");
            DynamicMenu content = createDynamic(factoryName);
            Menu submenu = null;
            if (content != null && !displayName.isEmpty()) {
                submenu = new Menu(displayName);
                submenu.setId(child.getName());
                submenu.getItems().setAll(content.items());
                Menu dynamicSubmenu = submenu;
                content.items().addListener((ListChangeListener<MenuItem>) change ->
                        dynamicSubmenu.getItems().setAll(content.items()));
            }
            DynamicEntry entry = new DynamicEntry(position, child.getName(), separatorBefore,
                    content, submenu);
            if (content != null) {
                content.items().addListener((ListChangeListener<MenuItem>) change -> {
                    MenuModel current = models.get(folder);
                    if (current != null) {
                        refreshDynamic(owner, current, entry);
                    }
                });
            }
            entries.add(entry);
        }
        return entries;
    }

    private DynamicMenu createDynamic(String factoryName) {
        for (DynamicMenu.Factory factory : context.lookup().lookupAll(DynamicMenu.Factory.class)) {
            if (factory.factoryId().equals(factoryName)) {
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

    private record MenuModel(List<EntryState> states, List<FileObject> subfolders,
            List<DynamicEntry> dynamic) {
    }

    private static final class EntryState {
        private final Entry entry;
        private List<MenuItem> rendered = List.of();

        EntryState(Entry entry) {
            this.entry = entry;
        }

        Entry entry() {
            return entry;
        }
    }

    private sealed interface Entry permits StaticEntry, DynamicEntry {
        int position();
        String id();
        boolean separatorBefore();
    }

    private record StaticEntry(int position, String commandId, boolean separatorBefore,
            MenuItem item) implements Entry {
        @Override
        public String id() {
            return commandId;
        }
    }

    private record DynamicEntry(int position, String id, boolean separatorBefore,
            DynamicMenu content, Menu submenu) implements Entry {
    }
}
