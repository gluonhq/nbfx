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
package org.netbeans.modules.nbfx.ide.diff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.netbeans.api.nbfx.editor.SavedDocument;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.icons.ToolIcons;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * The diff of what a {@link DiffProvider} has, as NetBeans' diff window: a bar with the ways to
 * compare, the buttons that go from one difference to the next and Refresh; under it, when the
 * provider lists its files, a table of them - one after the other or under their folders, with
 * the columns to choose from, as the Git window has them; and the two texts of the file selected, side by side
 * in a {@link DiffPane}. Choosing a way to compare, and Refresh, ask the provider again and keep
 * the file selected when it is still there.
 * <p>
 * The provider is asked, and the texts are read, off the FX thread.
 */
final class DiffBrowser extends BorderPane implements DiffContent {

    private static final Logger LOG = Logger.getLogger(DiffBrowser.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("nbfx-diff-browser", 1);
    /** The most of the height the table of files takes at first. */
    private static final double TABLE_SHARE = 0.25;
    /** The height of the header of the table and of one of its rows, about: what it is sized by at first. */
    private static final double ROW_HEIGHT = 24;

    private final DiffProvider provider;
    private final List<ToggleButton> toggles = new ArrayList<>();
    private final TreeTableView<DiffTree.Row> table = new TreeTableView<>();
    private final TreeItem<DiffTree.Row> root = new TreeItem<>(new DiffTree.Folder("", ""));
    private final ToggleButton listToggle = toggle(ToolIcons.FILE_VIEW, "Diff.view.list");
    private final ToggleButton treeToggle = toggle(ToolIcons.LOGICAL_VIEW, "Diff.view.tree");
    private List<DiffEntry> entries = List.of();
    /** The folders the user collapsed, by path, so that a refresh keeps them collapsed. */
    private final Set<String> collapsed = new HashSet<>();
    private Runnable onLayoutChanged = () -> { };
    private boolean restoringColumns;
    private boolean layoutChangePending;
    private final StackPane holder = new StackPane();
    private final Label placeholder = new Label();
    private final Label summary = new Label();
    private final Button previous = button(ToolIcons.PREVIOUS_MATCH, "Diff.previous");
    private final Button next = button(ToolIcons.NEXT_MATCH, "Diff.next");
    private final Button refresh = button(ToolIcons.REFRESH, "Diff.refresh");
    /** The table over the diff, or {@code null} when the provider lists no files. */
    private SplitPane split;
    /** Whether the table was given the height of its rows: once, then it is the user's to size. */
    private boolean sized;
    private DiffMode mode;
    private DiffPane pane;
    /** The editor the keyboard is in, or was in last, of the diff shown; of none while there is none. */
    private final ReadOnlyObjectWrapper<EditorDocument> activeEditor = new ReadOnlyObjectWrapper<>(this, "activeEditor");
    /** Set while the table is filled: the selection is acted on once, when it is. */
    private boolean filling;
    private long listing;
    private long loading;
    /** What was asked off the FX thread and is not answered yet. */
    private int busy;

    DiffBrowser(DiffProvider provider, DiffMode initial) {
        this.provider = Objects.requireNonNull(provider);
        List<DiffMode> modes = provider.modes();
        mode = initial != null && modes.contains(initial) ? initial : modes.isEmpty() ? null : modes.getFirst();
        getStyleClass().add("diff-browser");
        getStylesheets().add(Objects.requireNonNull(DiffBrowser.class.getResource("diff.css")).toExternalForm());

        HBox bar = new HBox();
        bar.getStyleClass().add("diff-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup group = new ToggleGroup();
        for (DiffMode offered : modes) {
            ToggleButton toggle = new ToggleButton();
            toggle.setGraphic(offered.icon().get());
            toggle.setTooltip(new Tooltip(offered.title()));
            toggle.setAccessibleText(offered.title());
            toggle.getStyleClass().add("diff-tool-button");
            toggle.setFocusTraversable(false);
            toggle.setUserData(offered);
            toggle.setToggleGroup(group);
            toggle.setSelected(offered.equals(mode));
            toggles.add(toggle);
        }
        group.selectedToggleProperty().subscribe((was, now) -> {
            if (now == null) {
                // A click on the selected toggle would leave none selected.
                group.selectToggle(was);
            } else if (!now.getUserData().equals(mode)) {
                mode = (DiffMode) now.getUserData();
                refresh();
            }
        });
        bar.getChildren().addAll(toggles);
        if (!toggles.isEmpty()) {
            bar.getChildren().add(new Separator(Orientation.VERTICAL));
        }
        if (provider.listed()) {
            ToggleGroup flavours = new ToggleGroup();
            listToggle.setToggleGroup(flavours);
            treeToggle.setToggleGroup(flavours);
            listToggle.setSelected(true);
            flavours.selectedToggleProperty().subscribe((was, now) -> {
                if (now == null) {
                    flavours.selectToggle(was);
                } else if (was != null) {
                    arrange(selected());
                    layoutChanged();
                }
            });
            bar.getChildren().addAll(listToggle, treeToggle, new Separator(Orientation.VERTICAL));
        }
        summary.getStyleClass().add("diff-summary");
        summary.setMaxWidth(Double.MAX_VALUE);
        summary.setAlignment(Pos.CENTER);
        HBox.setHgrow(summary, Priority.ALWAYS);
        bar.getChildren().addAll(previous, next, new Separator(Orientation.VERTICAL), refresh, summary);
        previous.setOnAction(event -> fire(true));
        next.setOnAction(event -> fire(false));
        refresh.setOnAction(event -> refresh());
        setTop(bar);

        placeholder.getStyleClass().add("diff-placeholder");
        holder.getChildren().add(placeholder);
        if (provider.listed()) {
            buildTable();
            split = new SplitPane(table, holder);
            split.setOrientation(Orientation.VERTICAL);
            split.setDividerPositions(TABLE_SHARE);
            split.getStyleClass().add("diff-split");
            SplitPane.setResizableWithParent(table, false);
            split.getDividers().getFirst().positionProperty().subscribe(position -> layoutChanged());
            setCenter(split);
        } else {
            setCenter(holder);
        }
        show((DiffPane) null, "");
        refresh();
    }

    /** The files listed, as last told by the provider. */
    List<DiffEntry> entries() {
        return entries;
    }

    /** The file whose diff is shown, or {@code null}. */
    DiffEntry selected() {
        TreeItem<DiffTree.Row> item = table.getSelectionModel().getSelectedItem();
        return item != null && item.getValue() instanceof DiffTree.File file ? file.entry() : null;
    }

    /** Shows the diff of the file of that index among {@link #entries()}. */
    void select(int index) {
        select(root, entries.get(index));
    }

    /** What the rows of the table read in the File column, top to bottom. */
    List<String> rows() {
        List<String> names = new ArrayList<>();
        for (int row = 0; row < table.getExpandedItemCount(); row++) {
            names.add(table.getTreeItem(row).getValue().name());
        }
        return names;
    }

    /**
     * A column of the table as the user arranged it.
     *
     * @param id the column: {@code file}, {@code status} or {@code path}
     * @param width its width, restored as the preferred width the columns share the table from
     * @param visible whether it is shown
     */
    record ColumnState(String id, double width, boolean visible) {

        ColumnState {
            Objects.requireNonNull(id);
        }
    }

    /** Whether the files are laid out under their folders, not one after the other. */
    boolean isTree() {
        return treeToggle.isSelected();
    }

    /** Lays the files out under their folders, or one after the other. */
    void setTree(boolean tree) {
        (tree ? treeToggle : listToggle).setSelected(true);
    }

    /**
     * The share of the height the table of files takes, from 0 to 1; -1 when the provider lists no
     * files.
     */
    double tableShare() {
        return split == null ? -1 : split.getDividerPositions()[0];
    }

    /** Gives the table of files that share of the height, in place of the height of its rows. */
    void setTableShare(double share) {
        if (split != null && share > 0 && share < 1) {
            sized = true;
            split.setDividerPositions(share);
        }
    }

    /** The columns in their order, with their widths and visibility. */
    List<ColumnState> columns() {
        return table.getColumns().stream()
                .map(column -> new ColumnState(column.getId(), column.getWidth(), column.isVisible())).toList();
    }

    /** Arranges the columns as {@code saved}; a column it does not name keeps its place after the others. */
    void restoreColumns(List<ColumnState> saved) {
        restoringColumns = true;
        try {
            Map<String, TreeTableColumn<DiffTree.Row, ?>> byId = new LinkedHashMap<>();
            for (TreeTableColumn<DiffTree.Row, ?> column : table.getColumns()) {
                byId.put(column.getId(), column);
            }
            List<TreeTableColumn<DiffTree.Row, ?>> ordered = new ArrayList<>();
            for (ColumnState state : saved) {
                TreeTableColumn<DiffTree.Row, ?> column = byId.remove(state.id());
                if (column != null) {
                    column.setPrefWidth(state.width());
                    // The File column is always shown: its visibility is bound.
                    if (!column.visibleProperty().isBound()) {
                        column.setVisible(state.visible());
                    }
                    ordered.add(column);
                }
            }
            ordered.addAll(byId.values());
            table.getColumns().setAll(ordered);
        } finally {
            restoringColumns = false;
        }
    }

    /**
     * Runs after the user chose the list or the tree, moved, resized, showed or hid a column, or
     * moved the divider under the table, once per pulse.
     */
    void setOnLayoutChanged(Runnable action) {
        onLayoutChanged = Objects.requireNonNull(action);
    }

    private void layoutChanged() {
        if (restoringColumns || layoutChangePending) {
            return;
        }
        layoutChangePending = true;
        Platform.runLater(() -> {
            layoutChangePending = false;
            onLayoutChanged.run();
        });
    }

    /** The toggle that lays the files out one after the other. */
    ToggleButton listToggle() {
        return listToggle;
    }

    /** The toggle that lays the files out under their folders. */
    ToggleButton treeToggle() {
        return treeToggle;
    }

    TreeTableView<DiffTree.Row> table() {
        return table;
    }

    /** The way to compare chosen, or {@code null} when the provider has none. */
    DiffMode mode() {
        return mode;
    }

    /** The toggles of the ways to compare, in the order of the provider. */
    List<ToggleButton> modeToggles() {
        return List.copyOf(toggles);
    }

    Button previousButton() {
        return previous;
    }

    Button refreshButton() {
        return refresh;
    }

    /** The two texts shown, or {@code null} when no file is selected or its texts are not read yet. */
    DiffPane pane() {
        return pane;
    }

    /** The text beside the buttons: of the diff shown, or why there is none. */
    String summary() {
        return summary.getText();
    }

    /** Whether the provider was asked and has not answered, or the texts of a file are being read. */
    boolean isBusy() {
        return busy > 0;
    }

    /** Asks the provider again, and shows again the file that was selected if it is still there. */
    @Override
    public void refresh() {
        long asked = ++listing;
        DiffMode compared = mode;
        busy++;
        RP.post(() -> {
            List<DiffEntry> found;
            String failure = null;
            try {
                found = List.copyOf(provider.entries(compared));
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.INFO, "Cannot list what differs", ex);
                found = List.of();
                failure = message("Diff.failed");
            }
            List<DiffEntry> entries = found;
            String why = failure;
            Platform.runLater(() -> {
                busy--;
                if (asked == listing) {
                    list(entries, why);
                }
            });
        });
    }

    /** To the text compared to of the diff shown; to the table of the files while there is none. */
    @Override
    public void focusEditor() {
        if (pane != null) {
            pane.focusEditor();
        } else {
            requestFocus();
        }
    }

    @Override
    public ObservableValue<EditorDocument> activeEditorProperty() {
        return activeEditor.getReadOnlyProperty();
    }

    @Override
    public SavedDocument savedState() {
        return provider.savedState(mode);
    }

    @Override
    public void dispose() {
        listing++;
        loading++;
        show((DiffPane) null, "");
    }

    private void list(List<DiffEntry> found, String failure) {
        DiffEntry was = selected();
        entries = found;
        DiffEntry now = found.stream()
                .filter(entry -> was != null && entry.name().equals(was.name())
                        && entry.location().equals(was.location()))
                .findFirst().orElse(found.isEmpty() ? null : found.getFirst());
        arrange(now);
        if (split != null && !sized && !found.isEmpty()) {
            sized = true;
            // Once laid out: as high as its rows, a few of them, so that one file takes one row.
            Platform.runLater(() -> {
                if (split.getHeight() > 0) {
                    double wanted = (table.getExpandedItemCount() + 1.5) * ROW_HEIGHT / split.getHeight();
                    split.setDividerPositions(Math.min(TABLE_SHARE, wanted));
                }
            });
        }
        if (now == null) {
            loading++;
            show((DiffPane) null, failure != null ? failure : message("Diff.noChanges"));
        } else if (was == null || now != was) {
            load(now);
        }
    }

    /** Lays the files out as the toggles say, with {@code chosen} selected; its diff is not read again. */
    private void arrange(DiffEntry chosen) {
        filling = true;
        List<DiffTree.Node> nodes = treeToggle.isSelected() ? DiffTree.tree(entries) : DiffTree.list(entries);
        root.getChildren().setAll(nodes.stream().map(this::item).toList());
        if (chosen == null || !select(root, chosen)) {
            table.getSelectionModel().clearSelection();
        }
        filling = false;
    }

    private TreeItem<DiffTree.Row> item(DiffTree.Node node) {
        TreeItem<DiffTree.Row> item = new TreeItem<>(node.row());
        item.getChildren().setAll(node.children().stream().map(this::item).toList());
        if (node.row() instanceof DiffTree.Folder folder) {
            item.setExpanded(!collapsed.contains(folder.path()));
            item.expandedProperty().subscribe(expanded -> {
                if (expanded) {
                    collapsed.remove(folder.path());
                } else {
                    collapsed.add(folder.path());
                }
            });
        }
        return item;
    }

    private boolean select(TreeItem<DiffTree.Row> under, DiffEntry entry) {
        for (TreeItem<DiffTree.Row> item : under.getChildren()) {
            if (item.getValue() instanceof DiffTree.File file && file.entry() == entry) {
                // In view, whatever was collapsed: a row that is not shown cannot be the selected one.
                for (TreeItem<DiffTree.Row> parent = item.getParent(); parent != null; parent = parent.getParent()) {
                    parent.setExpanded(true);
                }
                table.getSelectionModel().select(item);
                return true;
            }
            if (select(item, entry)) {
                return true;
            }
        }
        return false;
    }

    private void load(DiffEntry entry) {
        long asked = ++loading;
        busy++;
        RP.post(() -> {
            DiffPair pair = null;
            try {
                pair = entry.loader().load();
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.INFO, "Cannot read " + entry.location(), ex);
            }
            DiffPair read = pair;
            Platform.runLater(() -> {
                busy--;
                if (asked != loading) {
                    return;
                }
                if (read == null) {
                    show((DiffPane) null, message("Diff.failed"));
                } else {
                    show(new DiffPane(read.left(), read.right(), false), "");
                }
            });
        });
    }

    /** Shows {@code shown} in place of the diff before, or {@code why} there is none. */
    private void show(DiffPane shown, String why) {
        if (pane != null) {
            previous.disableProperty().unbind();
            next.disableProperty().unbind();
            summary.textProperty().unbind();
            pane.dispose();
        }
        pane = shown;
        activeEditor.unbind();
        if (pane == null) {
            activeEditor.set(null);
            previous.setDisable(true);
            next.setDisable(true);
            summary.setText("");
            placeholder.setText(why);
            holder.getChildren().setAll(placeholder);
        } else {
            previous.disableProperty().bind(pane.previousButton().disableProperty());
            next.disableProperty().bind(pane.nextButton().disableProperty());
            summary.textProperty().bind(pane.summaryProperty());
            activeEditor.bind(pane.activeEditorProperty());
            holder.getChildren().setAll(pane);
        }
    }

    private void fire(boolean back) {
        if (pane != null) {
            (back ? pane.previousButton() : pane.nextButton()).fire();
        }
    }

    private void buildTable() {
        table.setRoot(root);
        table.setShowRoot(false);
        root.setExpanded(true);
        table.getStyleClass().add("diff-files");
        table.setPlaceholder(new Label(""));
        TreeTableColumn<DiffTree.Row, DiffTree.Row> file = new TreeTableColumn<>(message("Diff.column.file"));
        file.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getValue()));
        file.setCellFactory(column -> new FileCell());
        file.setId("file");
        file.setPrefWidth(280);
        // Always shown: the table's column menu disables the entry of a column whose visibility is bound.
        file.visibleProperty().bind(new ReadOnlyBooleanWrapper(true));
        TreeTableColumn<DiffTree.Row, String> status = new TreeTableColumn<>(message("Diff.column.status"));
        status.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getValue() instanceof DiffTree.File row ? row.entry().status() : ""));
        status.setId("status");
        status.setPrefWidth(140);
        TreeTableColumn<DiffTree.Row, String> path = new TreeTableColumn<>(message("Diff.column.location"));
        path.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getValue() instanceof DiffTree.File row ? row.entry().location() : ""));
        path.setId("path");
        path.setPrefWidth(280);
        table.getColumns().setAll(List.of(file, status, path));
        table.setTreeColumn(file);
        // The "+" button of the header shows and hides columns, as in the Search view and the Git window.
        table.setTableMenuButtonVisible(true);
        // The columns always fill the table, as there: a resize is shared in proportion to the
        // preferred widths, a dragged edge is absorbed by the columns after it, and there is no
        // horizontal scroll bar.
        table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        for (TreeTableColumn<DiffTree.Row, ?> column : table.getColumns()) {
            // The rows keep the order of the provider, and of the tree.
            column.setSortable(false);
            column.widthProperty().subscribe(width -> layoutChanged());
            column.visibleProperty().subscribe(visible -> layoutChanged());
        }
        table.getColumns().addListener((ListChangeListener<TreeTableColumn<DiffTree.Row, ?>>) change -> layoutChanged());
        table.getSelectionModel().selectedItemProperty().subscribe(item -> {
            if (!filling && item != null && item.getValue() instanceof DiffTree.File row) {
                load(row.entry());
            }
        });
    }

    /** The File column: the icon and the name, coloured by how the file changed. */
    private static final class FileCell extends TreeTableCell<DiffTree.Row, DiffTree.Row> {

        private static final List<String> KINDS = List.of("diff-file-added", "diff-file-modified",
                "diff-file-deleted", "diff-file-conflicted");

        FileCell() {
            getStyleClass().add("diff-file-cell");
        }

        @Override
        protected void updateItem(DiffTree.Row row, boolean empty) {
            super.updateItem(row, empty);
            getStyleClass().removeAll(KINDS);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(row.name());
            setGraphic(icon(row));
            if (row instanceof DiffTree.File file) {
                switch (file.entry().kind()) {
                    case ADDED -> getStyleClass().add("diff-file-added");
                    case MODIFIED -> getStyleClass().add("diff-file-modified");
                    case DELETED -> getStyleClass().add("diff-file-deleted");
                    case CONFLICTED -> getStyleClass().add("diff-file-conflicted");
                    case OTHER -> { }
                }
            }
        }

        private static Node icon(DiffTree.Row row) {
            if (row instanceof DiffTree.File file) {
                Node icon = file.entry().icon() == null ? null : file.entry().icon().get();
                if (icon == null) {
                    // Keeps the name in line with those that have one.
                    Region blank = new Region();
                    blank.setMinSize(ToolIcons.SIZE, ToolIcons.SIZE);
                    blank.setPrefSize(ToolIcons.SIZE, ToolIcons.SIZE);
                    return blank;
                }
                return icon;
            }
            return ToolIcons.view(ToolIcons.FOLDER);
        }
    }

    private static ToggleButton toggle(String icon, String key) {
        ToggleButton button = new ToggleButton();
        button.setGraphic(ToolIcons.view(icon));
        button.setTooltip(new Tooltip(message(key)));
        button.setAccessibleText(message(key));
        button.getStyleClass().add("diff-tool-button");
        button.setFocusTraversable(false);
        return button;
    }

    private static Button button(String icon, String key) {
        Button button = new Button();
        button.setGraphic(ToolIcons.view(icon));
        button.setTooltip(new Tooltip(message(key)));
        button.setAccessibleText(message(key));
        button.getStyleClass().add("diff-tool-button");
        button.setFocusTraversable(false);
        return button;
    }

    private static String message(String key) {
        return NbBundle.getMessage(DiffBrowser.class, key);
    }
}
