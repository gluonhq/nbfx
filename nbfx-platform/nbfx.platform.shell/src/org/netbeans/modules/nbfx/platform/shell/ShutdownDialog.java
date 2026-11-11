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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;
import org.netbeans.modules.nbfx.platform.shell.api.shutdown.ShutdownImpact;
import org.netbeans.modules.nbfx.platform.shell.api.shutdown.ShutdownSelection;

/** Generic impact-report dialog used by the shutdown coordinator. */
final class ShutdownDialog {

    private static final String SUMMARY_SEPARATOR = "  \u00b7  ";

    private ShutdownDialog() {
    }

    static ShutdownSelection confirm(ShutdownImpact impact) {
        Dialog<ButtonType> dialog = new Dialog<>();
        ShellDialogSupport.install(dialog, message("ShutdownDialog.title"));
        dialog.getDialogPane().getStyleClass().add("shutdown-dialog");
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Button quit = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        quit.setText(message("ShutdownDialog.button.quit"));

        VBox content = new VBox();
        content.getStyleClass().add("shutdown-dialog-content");
        Label summary = new Label(summary(impact));
        summary.getStyleClass().add("shutdown-dialog-summary");
        summary.setWrapText(true);
        content.getChildren().add(summary);

        if (hasDataLoss(impact)) {
            Label warning = new Label(message("ShutdownDialog.warning.dataLoss"));
            warning.getStyleClass().add("shutdown-dialog-warning");
            warning.setWrapText(true);
            content.getChildren().add(warning);
        }

        Map<String, ComboBox<ShutdownImpact.Choice>> choices = new LinkedHashMap<>();
        Map<String, List<ShutdownImpact.Item>> groups = new LinkedHashMap<>();
        for (ShutdownImpact.Item item : impact.items()) {
            groups.computeIfAbsent(item.category(), key -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<String, List<ShutdownImpact.Item>> group : groups.entrySet()) {
            String category = group.getKey().isBlank()
                    ? message("ShutdownDialog.section.other") : group.getKey();
            content.getChildren().add(sectionTitle(category, group.getValue().size()));
            VBox rows = new VBox(8);
            for (ShutdownImpact.Item item : group.getValue()) {
                rows.getChildren().add(itemRow(item, choices));
            }
            content.getChildren().add(rows);
        }

        Map<String, Node> optionControls = new LinkedHashMap<>();
        if (!impact.options().isEmpty()) {
            content.getChildren().add(new Separator());
            content.getChildren().add(sectionTitle(
                    message("ShutdownDialog.section.options"), impact.options().size()));
            VBox options = new VBox(8);
            for (ShutdownImpact.Option option : impact.options()) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                Label label = new Label(option.label());
                label.getStyleClass().add("shutdown-dialog-option-label");
                Node control;
                if (option.type() == ShutdownImpact.Type.BOOLEAN) {
                    CheckBox check = new CheckBox();
                    check.setSelected(Boolean.parseBoolean(option.defaultValue()));
                    control = check;
                } else {
                    TextField field = new TextField(option.defaultValue());
                    HBox.setHgrow(field, Priority.ALWAYS);
                    if (option.type() == ShutdownImpact.Type.DIRECTORY) {
                        Button browse = new Button(message("ShutdownDialog.browse"));
                        browse.setOnAction(event -> {
                            DirectoryChooser chooser = new DirectoryChooser();
                            chooser.setTitle(option.label());
                            File directory = chooser.showDialog(ShellDialogSupport.owner());
                            if (directory != null) {
                                field.setText(directory.getAbsolutePath());
                            }
                        });
                        HBox box = new HBox(6, field, browse);
                        HBox.setHgrow(field, Priority.ALWAYS);
                        control = box;
                    } else {
                        control = field;
                    }
                }
                optionControls.put(option.id(), control);
                row.getChildren().addAll(label, control);
                HBox.setHgrow(control, Priority.ALWAYS);
                options.getChildren().add(row);
            }
            content.getChildren().add(options);
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(700);
        scroll.setPrefViewportHeight(500);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(760);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return null;
        }

        Map<String, String> selectedChoices = new LinkedHashMap<>();
        for (Map.Entry<String, ComboBox<ShutdownImpact.Choice>> entry : choices.entrySet()) {
            ShutdownImpact.Choice choice = entry.getValue().getValue();
            if (choice != null) {
                selectedChoices.put(entry.getKey(), choice.id());
            }
        }
        Map<String, String> selectedOptions = new LinkedHashMap<>();
        for (ShutdownImpact.Option option : impact.options()) {
            Object control = optionControls.get(option.id());
            if (control instanceof CheckBox check) {
                selectedOptions.put(option.id(), Boolean.toString(check.isSelected()));
            } else if (control instanceof TextField field) {
                selectedOptions.put(option.id(), field.getText());
            } else if (control instanceof HBox box) {
                for (Node node : box.getChildren()) {
                    if (node instanceof TextField field) {
                        selectedOptions.put(option.id(), field.getText());
                    }
                }
            }
        }
        return new ShutdownSelection(selectedChoices, selectedOptions);
    }

    private static Node itemRow(ShutdownImpact.Item item,
            Map<String, ComboBox<ShutdownImpact.Choice>> choices) {
        Label title = new Label(item.title());
        title.getStyleClass().add("shutdown-dialog-item-title");
        Label detail = new Label(item.detail());
        detail.getStyleClass().add("shutdown-dialog-item-detail");
        detail.setWrapText(true);
        VBox text = new VBox(2, title, detail);
        HBox.setHgrow(text, Priority.ALWAYS);

        Region marker = new Region();
        marker.getStyleClass().addAll("shutdown-dialog-marker", severityClass(item.severity()));

        HBox row = new HBox(10, marker, text);
        row.setAlignment(Pos.CENTER_LEFT);
        if (!item.choices().isEmpty()) {
            ComboBox<ShutdownImpact.Choice> combo = new ComboBox<>(
                    FXCollections.observableArrayList(item.choices()));
            combo.setConverter(new StringConverter<>() {
                @Override
                public String toString(ShutdownImpact.Choice choice) {
                    return choice == null ? "" : choice.label();
                }

                @Override
                public ShutdownImpact.Choice fromString(String string) {
                    return null;
                }
            });
            ShutdownImpact.Choice initial = item.choices().stream()
                    .filter(choice -> choice.id().equals(item.defaultChoice()))
                    .findFirst().orElse(item.choices().get(0));
            combo.setValue(initial);
            combo.getStyleClass().add("shutdown-dialog-choice");
            choices.put(item.id(), combo);
            row.getChildren().add(combo);
        }
        return row;
    }

    private static String severityClass(ShutdownImpact.Severity severity) {
        return switch (severity) {
            case DATA_LOSS -> "data-loss";
            case WARNING -> "warning";
            case IMPACT -> "impact";
            default -> "info";
        };
    }

    private static boolean hasDataLoss(ShutdownImpact impact) {
        return impact.items().stream().anyMatch(item ->
                item.severity() == ShutdownImpact.Severity.DATA_LOSS
                        || item.choices().stream().anyMatch(ShutdownImpact.Choice::dataLoss));
    }

    private static Label sectionTitle(String text, int count) {
        Label label = new Label(count > 0 ? text + "  (" + count + ")" : text);
        label.getStyleClass().add("shutdown-dialog-section");
        return label;
    }

    private static String summary(ShutdownImpact impact) {
        if (!impact.summary().isEmpty()) {
            return String.join(SUMMARY_SEPARATOR, impact.summary().values());
        }
        if (impact.items().isEmpty()) {
            return message("ShutdownDialog.summary.empty");
        }
        int count = impact.items().size();
        return message(count == 1
                ? "ShutdownDialog.summary.item" : "ShutdownDialog.summary.items", count);
    }

    private static String message(String key, Object... args) {
        return ShellDialogSupport.message(key, args);
    }
}
