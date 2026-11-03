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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.MenuItem;
import org.netbeans.api.nbfx.views.DynamicMenuRegistration;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Window menu's "Configure Window" submenu. It is a dynamic section so the generic builder does
 * not need a special case for it.
 *
 * @since 1.0
 */
@ServiceProvider(service = DynamicMenu.Factory.class)
@DynamicMenuRegistration(path = "Menus/Window", id = "configure-window", position = 60)
public final class ConfigureWindowMenuFactory implements DynamicMenu.Factory {

    @Override
    public DynamicMenu create(Lookup context) {
        return new ConfigureWindowMenu();
    }

    private static final class ConfigureWindowMenu implements DynamicMenu {

        private final ObservableList<MenuItem> items = FXCollections.observableArrayList(
                TabContextMenu.createConfigureWindowMenu());

        @Override
        public ObservableList<MenuItem> items() {
            return items;
        }
    }
}
