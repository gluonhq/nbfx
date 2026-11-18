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
package org.netbeans.modules.nbfx.platform.nodes;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import org.netbeans.api.nbfx.nodes.PropertiesProvider;
import org.netbeans.api.nbfx.nodes.PropertyModel;
import org.netbeans.api.nbfx.nodes.PropertySetModel;
import org.netbeans.api.nbfx.nodes.PropertySubscription;
import org.netbeans.api.nbfx.property.ObservablePropertyModel;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * A pure-JavaFX property sheet: a two-column tree (Name / Value) whose branches are the
 * {@link PropertySetModel}s and whose leaves are the {@link PropertyModel properties}, with an
 * expert toggle and per-type inline editors from {@link PropertyEditors}.
 *
 * <p>Reused for Project Properties, File Properties and the future FXML component inspector. The
 * caller sets the context object; the sheet asks every registered {@link PropertiesProvider} for
 * its property sets.</p>
 */
final class PropertySheet extends BorderPane {

    private final TreeTableView<Object> tree = new TreeTableView<>();
    private final CheckBox expert = new CheckBox(message("PropertySheet.expert"));
    private Object context;
    private boolean showExpert;

    public PropertySheet() {
        TreeTableColumn<Object, String> nameColumn = new TreeTableColumn<>(message("PropertySheet.name"));
        nameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(displayName(cell.getValue().getValue())));
        nameColumn.setPrefWidth(180);

        TreeTableColumn<Object, Object> valueColumn = new TreeTableColumn<>(message("PropertySheet.value"));
        valueColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getValue()));
        valueColumn.setCellFactory(column -> new ValueCell());

        tree.getColumns().add(nameColumn);
        tree.getColumns().add(valueColumn);
        tree.setShowRoot(false);
        tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        expert.selectedProperty().addListener((observable, old, now) -> {
            showExpert = now;
            rebuild();
        });
        setTop(expert);
        setCenter(tree);
    }

    /** Sets the context object whose properties are shown, and rebuilds the sheet. */
    public void setContext(Object context) {
        this.context = context;
        rebuild();
    }

    /** The tree, for tests. */
    TreeTableView<Object> tree() {
        return tree;
    }

    private void rebuild() {
        TreeItem<Object> root = new TreeItem<>();
        if (context != null) {
            for (PropertiesProvider provider : Lookup.getDefault().lookupAll(PropertiesProvider.class)) {
                for (PropertySetModel set : provider.getPropertySets(context)) {
                    TreeItem<Object> setItem = new TreeItem<>(set);
                    for (PropertyModel property : set.getProperties()) {
                        if (property.isExpert() && !showExpert) {
                            continue;
                        }
                        setItem.getChildren().add(new TreeItem<>(property));
                    }
                    root.getChildren().add(setItem);
                }
            }
        }
        tree.setRoot(root);
    }

    private static String displayName(Object value) {
        if (value instanceof PropertySetModel set) {
            return set.getDisplayName();
        }
        if (value instanceof PropertyModel property) {
            return property.getDisplayName();
        }
        return "";
    }

    private static String message(String key) {
        return NbBundle.getMessage(PropertySheet.class, key);
    }

    /**
     * The value cell: an editor for a writable property, a label otherwise. It observes the model
     * directly - an FX model through its {@code ObservableValue}, a neutral model through its
     * listener - and unsubscribes when the cell is reused.
     */
    private static final class ValueCell extends TreeTableCell<Object, Object> {

        private PropertyModel property;
        private Runnable unsubscribe = () -> {
        };

        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            unsubscribe.run();
            unsubscribe = () -> {
            };
            property = null;
            setText(null);
            setGraphic(null);
            if (empty) {
                return;
            }
            TreeItem<Object> row = getTreeTableRow() == null ? null : getTreeTableRow().getTreeItem();
            if (row == null || !(row.getValue() instanceof PropertyModel model)) {
                return;
            }
            property = model;
            observe(model);
            showEditor(model);
        }

        private void observe(PropertyModel model) {
            if (model instanceof ObservablePropertyModel fx) {
                ChangeListener<Object> listener = (observable, oldValue, newValue) -> refresh(model);
                fx.valueProperty().addListener(listener);
                unsubscribe = () -> fx.valueProperty().removeListener(listener);
            } else {
                PropertySubscription subscription = model.addListener(
                        (changed, oldValue, newValue) -> refresh(model));
                unsubscribe = subscription::unsubscribe;
            }
        }

        private void refresh(PropertyModel model) {
            if (Platform.isFxApplicationThread()) {
                refreshOnFx(model);
            } else {
                Platform.runLater(() -> refreshOnFx(model));
            }
        }

        private void refreshOnFx(PropertyModel model) {
            if (property != model) {
                return;
            }
            // The editor writes the model while it has the focus; replacing it then would take
            // the caret away mid-edit. External changes, with the editor unfocused, do refresh it.
            Node graphic = getGraphic();
            if (graphic != null && graphic.isFocused()) {
                return;
            }
            showEditor(model);
        }

        private void showEditor(PropertyModel model) {
            setGraphic(PropertyEditors.createEditor(model));
        }
    }
}
