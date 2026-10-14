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
package com.gluonhq.netbeans.nbfx.structure;

import com.gluonhq.netbeans.nbfx.api.actions.FxActionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The Structure view's content: a panel selector (one entry per {@link FxStructurePanel} that handles
 * the current content type) above the active panel's component.
 * <p>
 * It follows the global action context: when the active file or editor document changes, it resolves
 * the panels for the new content type, activates the first (or keeps the current one) and tells the
 * panels when they become active or inactive.
 */
final class StructurePanel extends BorderPane {

    private final ComboBox<FxStructurePanel> selector = new ComboBox<>();
    private final StackPane content = new StackPane();
    private final Label empty = new Label(NbBundle.getMessage(StructurePanel.class, "StructurePanel.empty"));
    private FxStructurePanel active;
    private String currentContentType;

    StructurePanel() {
        empty.setDisable(true);
        selector.setMaxWidth(Double.MAX_VALUE);
        selector.setConverter(new StringConverter<>() {
            @Override
            public String toString(FxStructurePanel panel) {
                return panel == null ? "" : panel.getDisplayName();
            }

            @Override
            public FxStructurePanel fromString(String string) {
                return null;
            }
        });
        selector.valueProperty().addListener((observable, old, now) -> activate(now));
        setTop(selector);
        setCenter(content);
        content.getChildren().add(empty);

        FxActionContext context = FxActionContext.getDefault();
        if (context != null) {
            // The global context follows the active component (§22): the file it carries is all that
            // decides the content type, so a single FileObject result is enough.
            context.lookupResult(FileObject.class).addLookupListener(event -> update(context));
            update(context);
        }
    }

    /** Re-resolves the panels for the current content type, rebuilding the selector when it changes. */
    private void update(FxActionContext context) {
        String contentType = contentTypeOf(context);
        if (Objects.equals(contentType, currentContentType)) {
            return;
        }
        currentContentType = contentType;
        List<FxStructurePanel> panels = panelsFor(contentType);
        selector.getItems().setAll(panels);
        boolean hasPanels = !panels.isEmpty();
        selector.setVisible(hasPanels);
        selector.setManaged(hasPanels);
        if (!hasPanels) {
            activate(null);
        } else if (active != null && panels.contains(active)) {
            selector.setValue(active);
        } else {
            selector.getSelectionModel().selectFirst();
        }
    }

    private void activate(FxStructurePanel panel) {
        if (panel == active) {
            return;
        }
        if (active != null) {
            active.panelDeactivated();
        }
        active = panel;
        if (panel == null) {
            content.getChildren().setAll(empty);
        } else {
            content.getChildren().setAll(panel.getComponent());
            FxActionContext context = FxActionContext.getDefault();
            if (context != null) {
                panel.panelActivated(context);
            }
        }
    }

    /** The content type to show the structure for, or {@code null} when there is no file. */
    private static String contentTypeOf(FxActionContext context) {
        FileObject file = FxStructurePanel.selectedFile(context);
        return file == null ? null : file.getMIMEType();
    }

    private static List<FxStructurePanel> panelsFor(String contentType) {
        if (contentType == null) {
            return List.of();
        }
        List<FxStructurePanel> panels = new ArrayList<>();
        for (FxStructurePanel panel : Lookup.getDefault().lookupAll(FxStructurePanel.class)) {
            for (String type : panel.contentTypes()) {
                if (contentType.equals(type)) {
                    panels.add(panel);
                    break;
                }
            }
        }
        return panels;
    }
}
