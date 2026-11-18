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
package org.netbeans.modules.nbfx.platform.output;

import org.netbeans.api.nbfx.views.DockLocation;
import org.netbeans.api.nbfx.views.ViewKind;
import org.netbeans.api.nbfx.views.ViewRegistration;
import org.netbeans.api.nbfx.view.ViewManager;
import org.netbeans.api.nbfx.view.ViewProvider;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Output view: a dockable tab, opening along the bottom of the main area by default, that shows
 * every console of the {@link ConsoleRegistry} as an inner tab of an {@link OutputView}.
 *
 * @since 1.0
 */
@ServiceProvider(service = ViewProvider.class)
@ViewRegistration(id = OutputViewProvider.ID, displayName = "Output",
        kind = ViewKind.OUTPUT_ID,
        iconName = "org/netbeans/modules/nbfx/platform/output/output.png",
        location = DockLocation.CENTER_BOTTOM, position = 30)
public final class OutputViewProvider implements ViewProvider {

    /** The stable id of the view, used by the layout persistence and the view manager. */
    public static final String ID = "output";

    private Node view;
    private final StringProperty title = new SimpleStringProperty(this, "title",
            NbBundle.getMessage(OutputViewProvider.class, "Output.title"));

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(OutputViewProvider.class, "Output.title");
    }

    @Override
    public DockLocation getDefaultLocation() {
        return DockLocation.CENTER_BOTTOM;
    }

    /** The tab title: the view's once there is one, the plain name of the view until then. */
    @Override
    public ObservableValue<String> titleProperty() {
        return title;
    }

    @Override
    public synchronized Node getView() {
        if (view == null) {
            OutputView created = new OutputView(ConsoleRegistry.getDefault(), OutputSettings.getDefault());
            title.bind(created.titleProperty());
            view = created;
        }
        return view;
    }

    /** Brings the Output view on screen. Must run on the JavaFX Application Thread. */
    public void show() {
        ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
        if (manager != null) {
            manager.show(this);
        }
    }

    /**
     * Brings the Output view on screen, if the application has one and a view manager to show it:
     * a headless application has neither, and its consoles only keep their text. Must run on the
     * JavaFX Application Thread.
     */
    static void reveal() {
        OutputViewProvider provider = instance();
        if (provider != null) {
            provider.show();
        }
    }

    /** The registered instance, or {@code null} when the module is not loaded. */
    public static OutputViewProvider instance() {
        return Lookup.getDefault().lookupAll(ViewProvider.class).stream()
                .filter(OutputViewProvider.class::isInstance)
                .map(OutputViewProvider.class::cast)
                .findFirst()
                .orElse(null);
    }
}
