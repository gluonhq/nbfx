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

import org.netbeans.api.nbfx.views.DynamicMenu;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToolBar;
import org.netbeans.api.nbfx.views.DynamicMenuRegistration;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The platform's default View section: a tool bar visibility/reset group and the editor display
 * toggles. It is a dynamic menu so a product gets the standard View menu without hard-coding it in
 * a builder.
 *
 * @since 1.0
 */
@ServiceProvider(service = DynamicMenu.Factory.class)
@DynamicMenuRegistration(path = "Menus/View", id = "view-toggles", position = 10)
public final class ViewMenuFactory implements DynamicMenu.Factory {

    @Override
    public DynamicMenu create(Lookup context) {
        return new ViewMenu(context);
    }

    private static final class ViewMenu implements DynamicMenu {

        private final ObservableList<MenuItem> items = FXCollections.observableArrayList();

        ViewMenu(Lookup context) {
            ToolBarContainer toolBars = context.lookup(ToolBarContainer.class);
            if (toolBars != null && !toolBars.getToolBars().isEmpty()) {
                Menu toolbars = new Menu(message("Menu.view.toolbars"));
                for (ToolBar bar : toolBars.getToolBars()) {
                    CheckMenuItem item = new CheckMenuItem(name(bar));
                    item.selectedProperty().bindBidirectional(bar.visibleProperty());
                    toolbars.getItems().add(item);
                }
                toolbars.getItems().add(new SeparatorMenuItem());
                MenuItem reset = new MenuItem(message("Menu.view.resetToolbars"));
                reset.setOnAction(event -> toolBars.resetArrangement());
                toolbars.getItems().add(reset);
                items.add(toolbars);
            }
        }

        private static String name(ToolBar bar) {
            String text = bar.getAccessibleText();
            return text == null || text.isBlank() ? bar.getId() : text;
        }

        @Override
        public ObservableList<MenuItem> items() {
            return items;
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(ViewMenuFactory.class, key);
    }
}
