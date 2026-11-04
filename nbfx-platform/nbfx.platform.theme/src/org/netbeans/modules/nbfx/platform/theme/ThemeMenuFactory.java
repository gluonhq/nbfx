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
package org.netbeans.modules.nbfx.platform.theme;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import org.netbeans.api.nbfx.theme.ThemeMode;
import org.netbeans.api.nbfx.views.DynamicMenu;
import org.netbeans.api.nbfx.views.DynamicMenuRegistration;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The View &gt; Theme section. It lists the theme files currently available and applies the
 * selected one to every open window.
 *
 * @since 1.0
 */
@ServiceProvider(service = DynamicMenu.Factory.class)
@DynamicMenuRegistration(path = "Menus/View", id = "themes", position = 30)
public final class ThemeMenuFactory implements DynamicMenu.Factory {

    @Override
    public DynamicMenu create(Lookup context) {
        return new ThemeMenu();
    }

    private static final class ThemeMenu implements DynamicMenu {

        private final ObservableList<MenuItem> items = FXCollections.observableArrayList();
        private final Menu menu = new Menu(NbBundle.getMessage(ThemeMenuFactory.class, "Menu.view.themes"));

        ThemeMenu() {
            menu.setOnShowing(event -> refresh());
            refresh();
            items.add(menu);
        }

        private void refresh() {
            menu.getItems().clear();
            ToggleGroup modes = new ToggleGroup();
            RadioMenuItem system = modeItem("Menu.view.theme.system", ThemeMode.SYSTEM, modes);
            RadioMenuItem light = modeItem("Menu.view.theme.light", ThemeMode.LIGHT, modes);
            RadioMenuItem dark = modeItem("Menu.view.theme.dark", ThemeMode.DARK, modes);
            system.setSelected(NbfxTheme.mode() == ThemeMode.SYSTEM);
            light.setSelected(NbfxTheme.mode() == ThemeMode.LIGHT);
            dark.setSelected(NbfxTheme.mode() == ThemeMode.DARK);
            menu.getItems().addAll(system, light, dark);
            menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());

            ToggleGroup themes = new ToggleGroup();
            String current = NbfxTheme.current();
            for (String id : NbfxTheme.themes()) {
                RadioMenuItem item = new RadioMenuItem(NbfxTheme.displayName(id));
                item.setToggleGroup(themes);
                item.setSelected(id.equals(current));
                item.setOnAction(event -> NbfxTheme.setTheme(id));
                menu.getItems().add(item);
            }
        }

        private static RadioMenuItem modeItem(String key, ThemeMode mode, ToggleGroup group) {
            RadioMenuItem item = new RadioMenuItem(NbBundle.getMessage(ThemeMenuFactory.class, key));
            item.setToggleGroup(group);
            item.setOnAction(event -> NbfxTheme.setMode(mode));
            return item;
        }

        @Override
        public ObservableList<MenuItem> items() {
            return items;
        }
    }
}
