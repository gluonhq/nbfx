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
package org.netbeans.modules.nbfx.platform.editor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuItem;
import org.netbeans.api.nbfx.editor.EditorSettings;
import org.netbeans.api.nbfx.views.DynamicMenu;
import org.netbeans.api.nbfx.views.DynamicMenuRegistration;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The editor contribution to the View menu: line-number and breadcrumb toggles. They live with the
 * editor service rather than in the generic shell.
 *
 * @since 1.0
 */
@ServiceProvider(service = DynamicMenu.Factory.class)
@DynamicMenuRegistration(path = "Menus/View", id = "editor-toggles", position = 20)
public final class EditorViewMenuFactory implements DynamicMenu.Factory {

    @Override
    public DynamicMenu create(Lookup context) {
        return new EditorToggles(context);
    }

    private static final class EditorToggles implements DynamicMenu {

        private final ObservableList<MenuItem> items = FXCollections.observableArrayList();

        EditorToggles(Lookup context) {
            EditorSettings settings = context.lookup(EditorSettings.class);
            if (settings != null) {
                CheckMenuItem lineNumbers = new CheckMenuItem(message("Menu.view.showLineNumbers"));
                lineNumbers.selectedProperty().bindBidirectional(settings.showLineNumbers());
                items.add(lineNumbers);
                CheckMenuItem breadcrumbs = new CheckMenuItem(message("Menu.view.showBreadcrumbs"));
                breadcrumbs.selectedProperty().bindBidirectional(settings.showBreadcrumbs());
                items.add(breadcrumbs);
            }
        }

        @Override
        public ObservableList<MenuItem> items() {
            return items;
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(EditorViewMenuFactory.class, key);
    }
}
