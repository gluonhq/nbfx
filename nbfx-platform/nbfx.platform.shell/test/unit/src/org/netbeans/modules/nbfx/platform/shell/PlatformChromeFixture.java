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

import java.util.Collection;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import org.netbeans.api.nbfx.actions.ActionReference;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.CommandsProvider;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.api.nbfx.status.StatusAlignment;
import org.netbeans.api.nbfx.status.StatusRegistration;
import org.netbeans.api.nbfx.views.DynamicMenu;
import org.netbeans.api.nbfx.views.DynamicMenuRegistration;
import org.netbeans.api.nbfx.views.MenuRegistration;
import org.netbeans.api.nbfx.views.ToolbarRegistration;
import org.netbeans.modules.nbfx.platform.status.StatusElementHolder;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

/**
 * A platform-only product fixture: it registers an action, menu, tool bar, dynamic menu section and
 * status element through annotations. No IDE and no {@code ShellApplication} subclass are involved.
 */
@MenuRegistration(id = "Conformance", position = 500)
@ActionReference(id = PlatformChromeFixture.ACTION, path = "Menus/Conformance", position = 10)
@ToolbarRegistration(id = "conformance", displayName = "Conformance", position = 500)
@ActionReference(id = PlatformChromeFixture.ACTION, path = "Toolbars/conformance", position = 10)
@DynamicMenuRegistration(path = "Menus/Conformance", id = "conformance.dynamic", position = 20)
@StatusRegistration(id = "conformance.status", alignment = StatusAlignment.RIGHT, position = 500)
@ServiceProviders({
    @ServiceProvider(service = CommandsProvider.class),
    @ServiceProvider(service = DynamicMenu.Factory.class)
})
public final class PlatformChromeFixture implements CommandsProvider, DynamicMenu.Factory,
        StatusElementHolder {

    /** The fixture action id. */
    public static final String ACTION = "conformance.action";

    /** The fixture dynamic item text. */
    public static final String DYNAMIC_TEXT = "Conformance Dynamic";

    private final Label status = new Label("Conformance Status");

    @Override
    public Collection<Command> createCommands() {
        return List.of(new RunnableCommand(ACTION, "Conformance", null, () -> {
        }));
    }

    @Override
    public DynamicMenu create(Lookup context) {
        ObservableList<MenuItem> items = FXCollections.observableArrayList(
                new MenuItem(DYNAMIC_TEXT));
        return () -> items;
    }

    @Override
    public String getId() {
        return "conformance.status";
    }

    @Override
    public Node getNode() {
        return status;
    }
}
