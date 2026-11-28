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
package org.netbeans.modules.nbfx.ide.vcs;

import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ContextMenu;
import javafx.collections.ListChangeListener;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.application.Platform;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.netbeans.api.nbfx.editor.ContentManager;
import org.netbeans.api.nbfx.editor.FileIconProvider;
import org.netbeans.api.nbfx.icons.ToolIcons;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsCompareMode;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsStatus;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The tool bar and the table of the Versioning view, as NetBeans' Git status window: the three compare
 * modes, the tree and the list view, Refresh and Commit over a table of the changed files with their
 * status and path. It shows the {@link VcsStatus} it is given and reads no repository itself; switching
 * the mode or the view rearranges that status.
 *
 * @since 1.2
 */
final class GitStatusPane extends BorderPane {

    /** How the changed files are laid out. */
    enum Flavour {
        /** Under their folders. */
        TREE,
        /** One after the other. */
        LIST
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

    private static final List<PseudoClass> KIND_STATES = List.of(
            PseudoClass.getPseudoClass("vcs-added"), PseudoClass.getPseudoClass("vcs-modified"),
            PseudoClass.getPseudoClass("vcs-deleted"), PseudoClass.getPseudoClass("vcs-conflicted"));

    private final ObjectProperty<VcsCompareMode> mode;
    private final ObjectProperty<Flavour> flavour;
    private final TreeTableView<StatusRow> table = new TreeTableView<>();
    private final TreeItem<StatusRow> root = new TreeItem<>(new StatusRow.Folder("", ""));
    private final Label branch = new Label();
    private final Label placeholder = new Label();
    /** Why there is nothing to commit, when the rows do not tell: a conflict to resolve first. */
    private final Label notice = new Label();
    private final Button commit;
    /** The folders the user collapsed, by path, so that a refresh keeps them collapsed. */
    private final Set<String> collapsed = new HashSet<>();
    private Runnable onRefresh = () -> { };
    private Runnable onCommit = () -> { };
    private Runnable onColumnsChanged = () -> { };
    private boolean restoringColumns;
    private boolean columnsChangePending;
    private Path repository;
    private VcsStatus status = VcsStatus.CLEAN;
    private String message;

    GitStatusPane(VcsCompareMode initialMode, Flavour initialFlavour) {
        mode = new SimpleObjectProperty<>(Objects.requireNonNull(initialMode));
        flavour = new SimpleObjectProperty<>(Objects.requireNonNull(initialFlavour));
        getStyleClass().add("git-view");
        getStylesheets().add(Objects.requireNonNull(GitStatusPane.class.getResource("vcs.css")).toExternalForm());

        commit = button(GitIcons.COMMIT, "Git.commit.all", () -> onCommit.run());
        commit.setDisable(true);
        setTop(createToolBar());
        createTable();
        setCenter(table);

        mode.subscribe(this::rebuild);
        flavour.subscribe(this::rebuild);
        rebuild();
    }

    /** What the table compares. */
    ObjectProperty<VcsCompareMode> modeProperty() {
        return mode;
    }

    /** How the table lays the files out. */
    ObjectProperty<Flavour> flavourProperty() {
        return flavour;
    }

    void setOnRefresh(Runnable action) {
        onRefresh = Objects.requireNonNull(action);
    }

    void setOnCommit(Runnable action) {
        onCommit = Objects.requireNonNull(action);
    }

    /** Runs after the user moved, resized, showed or hid a column, once per pulse. */
    void setOnColumnsChanged(Runnable action) {
        onColumnsChanged = Objects.requireNonNull(action);
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
            Map<String, TreeTableColumn<StatusRow, ?>> byId = new LinkedHashMap<>();
            for (TreeTableColumn<StatusRow, ?> column : table.getColumns()) {
                byId.put(column.getId(), column);
            }
            List<TreeTableColumn<StatusRow, ?>> ordered = new ArrayList<>();
            for (ColumnState state : saved) {
                TreeTableColumn<StatusRow, ?> column = byId.remove(state.id());
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

    private void columnsChanged() {
        if (restoringColumns || columnsChangePending) {
            return;
        }
        columnsChangePending = true;
        Platform.runLater(() -> {
            columnsChangePending = false;
            onColumnsChanged.run();
        });
    }

    void setCommitDisabled(boolean disabled) {
        commit.setDisable(disabled);
    }

    /**
     * As {@link #setCommitDisabled(boolean)}, with the reason there is nothing to commit, which the
     * button and the place of the branch tell; {@code null} for none worth telling.
     */
    void setCommitDisabled(boolean disabled, String reason) {
        commit.setDisable(disabled);
        String text = reason == null ? message("Git.commit.all") : reason;
        commit.getTooltip().setText(text);
        commit.setAccessibleText(text);
        notice.setText(reason == null ? "" : reason);
        notice.setVisible(reason != null);
        notice.setManaged(reason != null);
    }

    /** The text next to the buttons: the branch. */
    void setBranch(String text) {
        branch.setText(text);
    }

    /**
     * Shows the changed files of the repository at {@code root}. The same status again leaves the table
     * as it is, with its selection and scroll position.
     */
    void setStatus(Path root, VcsStatus newStatus) {
        Objects.requireNonNull(newStatus);
        if (message == null && Objects.equals(root, repository) && newStatus.equals(status)) {
            return;
        }
        if (!Objects.equals(root, repository)) {
            collapsed.clear();
        }
        repository = root;
        status = newStatus;
        message = null;
        rebuild();
    }

    /** Empties the table and shows {@code text} in its place: no project, or no repository. */
    void setMessage(String text) {
        repository = null;
        status = VcsStatus.CLEAN;
        message = Objects.requireNonNull(text);
        collapsed.clear();
        rebuild();
    }

    /** The table, for tests. */
    TreeTableView<StatusRow> table() {
        return table;
    }

    // -- tool bar ------------------------------------------------------------------------------

    private ToolBar createToolBar() {
        ToggleGroup modes = new ToggleGroup();
        ToggleButton headVsWorking = modeToggle(modes, VcsCompareMode.HEAD_VS_WORKING_TREE, GitIcons.HEAD_VS_WORKING_TREE);
        ToggleButton headVsIndex = modeToggle(modes, VcsCompareMode.HEAD_VS_INDEX, GitIcons.HEAD_VS_INDEX);
        ToggleButton indexVsWorking = modeToggle(modes, VcsCompareMode.INDEX_VS_WORKING_TREE, GitIcons.INDEX_VS_WORKING_TREE);
        keepOneSelected(modes);
        modes.selectedToggleProperty().subscribe(toggle -> {
            if (toggle != null) {
                mode.set((VcsCompareMode) toggle.getUserData());
            }
        });

        ToggleGroup flavours = new ToggleGroup();
        ToggleButton list = toggle(ToolIcons.FILE_VIEW, "Git.view.list");
        list.setUserData(Flavour.LIST);
        list.setToggleGroup(flavours);
        ToggleButton tree = toggle(ToolIcons.LOGICAL_VIEW, "Git.view.tree");
        tree.setUserData(Flavour.TREE);
        tree.setToggleGroup(flavours);
        (flavour.get() == Flavour.LIST ? list : tree).setSelected(true);
        keepOneSelected(flavours);
        flavours.selectedToggleProperty().subscribe(toggle -> {
            if (toggle != null) {
                flavour.set((Flavour) toggle.getUserData());
            }
        });

        Button refresh = button(ToolIcons.REFRESH, "Git.refresh", () -> onRefresh.run());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        branch.getStyleClass().add("git-branch");

        notice.getStyleClass().add("git-notice");
        notice.setVisible(false);
        notice.setManaged(false);

        // The buttons take the focus, by Tab and by a click: every one is reached without the mouse,
        // and a click on one brings the focus into the view as a click on a row does.
        ToolBar bar = new ToolBar(headVsWorking, headVsIndex, indexVsWorking, new Separator(),
                list, tree, new Separator(), refresh, commit, notice, spacer, branch);
        bar.getStyleClass().add("git-toolbar");
        return bar;
    }

    private ToggleButton modeToggle(ToggleGroup group, VcsCompareMode value, String icon) {
        ToggleButton button = toggle(icon, "Git.mode." + value.name());
        button.setUserData(value);
        button.setToggleGroup(group);
        button.setSelected(mode.get() == value);
        return button;
    }

    /** A click on the selected toggle of a group would leave none selected: select it again. */
    private static void keepOneSelected(ToggleGroup group) {
        group.selectedToggleProperty().subscribe((was, now) -> {
            if (now == null && was != null) {
                group.selectToggle(was);
            }
        });
    }

    private static Button button(String icon, String tooltipKey, Runnable action) {
        Button button = new Button();
        decorate(button, icon, tooltipKey);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static ToggleButton toggle(String icon, String tooltipKey) {
        ToggleButton button = new ToggleButton();
        decorate(button, icon, tooltipKey);
        return button;
    }

    private static void decorate(ButtonBase button, String icon, String tooltipKey) {
        button.setGraphic(GitIcons.view(icon));
        button.setTooltip(new Tooltip(message(tooltipKey)));
        button.setAccessibleText(message(tooltipKey));
        button.getStyleClass().add("git-tool-button");
    }

    // -- table ---------------------------------------------------------------------------------

    private void createTable() {
        table.setRoot(root);
        table.setShowRoot(false);
        root.setExpanded(true);
        table.getStyleClass().add("git-status-table");
        placeholder.getStyleClass().add("git-status-empty");
        table.setPlaceholder(placeholder);

        TreeTableColumn<StatusRow, StatusRow> file = new TreeTableColumn<>(message("Git.column.file"));
        file.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getValue()));
        file.setCellFactory(column -> new FileCell());
        file.setId("file");
        file.setPrefWidth(280);
        // Always shown: the table's column menu disables the entry of a column whose visibility is bound.
        file.visibleProperty().bind(new ReadOnlyBooleanWrapper(true));
        TreeTableColumn<StatusRow, String> statusColumn = new TreeTableColumn<>(message("Git.column.status"));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getValue() instanceof StatusRow.File row ? label(row.kind()) : ""));
        statusColumn.setId("status");
        statusColumn.setPrefWidth(140);
        TreeTableColumn<StatusRow, String> path = new TreeTableColumn<>(message("Git.column.path"));
        path.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getValue() instanceof StatusRow.File row ? row.path() : ""));
        path.setId("path");
        path.setPrefWidth(280);
        table.getColumns().setAll(List.of(file, statusColumn, path));
        table.setTreeColumn(file);
        // The "+" button of the header shows and hides columns, as in the Search view.
        table.setTableMenuButtonVisible(true);
        // The columns always fill the table, as NetBeans' outline in its AUTO_RESIZE_SUBSEQUENT_COLUMNS
        // mode and as the Search view: a resize is shared in proportion to the preferred widths, a
        // dragged edge is absorbed by the columns after it, and there is no horizontal scroll bar.
        table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        for (TreeTableColumn<StatusRow, ?> column : table.getColumns()) {
            // The rows keep the order of the tree: folders, then files, by name.
            column.setSortable(false);
            column.widthProperty().subscribe(width -> columnsChanged());
            column.visibleProperty().subscribe(visible -> columnsChanged());
        }
        table.getColumns().addListener((ListChangeListener<TreeTableColumn<StatusRow, ?>>) change -> columnsChanged());
        table.setContextMenu(createContextMenu());

        table.setRowFactory(view -> {
            TreeTableRow<StatusRow> row = new TreeTableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                        && row.getItem() instanceof StatusRow.File changed) {
                    activate(changed);
                    event.consume();
                }
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && selectedFile() != null) {
                activate(selectedFile());
                event.consume();
            }
        });
    }

    /**
     * Open, Diff, Refresh and Commit All, the actions of NetBeans' menu that the view has. The
     * commit is of every change the view shows, not of the selected rows, and is named so.
     */
    private ContextMenu createContextMenu() {
        MenuItem open = new MenuItem(message("Git.open"));
        open.setOnAction(event -> {
            if (selectedFile() != null) {
                open(selectedFile());
            }
        });
        MenuItem diff = new MenuItem(message("Git.diff"));
        diff.setOnAction(event -> {
            StatusRow.File row = selectedFile();
            if (row != null && repository != null) {
                // In the mode of the table, so that the diff shows the change the row tells of.
                GitDiff.open(repository, row.path(), row.name(), icon(row), mode.get());
            }
        });
        MenuItem commitItem = new MenuItem(message("Git.commit.menu"));
        commitItem.setOnAction(event -> onCommit.run());
        commitItem.disableProperty().bind(commit.disableProperty());
        MenuItem refreshItem = new MenuItem(message("Git.refresh"));
        refreshItem.setOnAction(event -> onRefresh.run());
        ContextMenu menu = new ContextMenu(open, diff, new SeparatorMenuItem(), refreshItem, commitItem);
        menu.setOnShowing(event -> {
            StatusRow.File row = selectedFile();
            FileObject file = row == null ? null : fileObject(row);
            open.setDisable(file == null || !file.isData());
            // A deleted file too: its diff shows what is gone.
            diff.setDisable(row == null || repository == null);
        });
        return menu;
    }

    private StatusRow.File selectedFile() {
        TreeItem<StatusRow> selected = table.getSelectionModel().getSelectedItem();
        return selected != null && selected.getValue() instanceof StatusRow.File file ? file : null;
    }

    private void rebuild() {
        List<StatusTree.Node> nodes = flavour.get() == Flavour.TREE
                ? StatusTree.tree(status, mode.get()) : StatusTree.list(status, mode.get());
        root.getChildren().setAll(nodes.stream().map(this::item).toList());
        // Nothing in this mode is not nothing at all: the other modes may hold changes.
        placeholder.setText(message != null ? message : message("Git.status.clean." + mode.get().name()));
    }

    private TreeItem<StatusRow> item(StatusTree.Node node) {
        TreeItem<StatusRow> item = new TreeItem<>(node.row());
        item.getChildren().setAll(node.children().stream().map(this::item).toList());
        if (node.row() instanceof StatusRow.Folder folder) {
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

    /**
     * What a double click or Enter on {@code row} does, as in NetBeans' Versioning window: shows
     * what changed in the file - its diff - where one is offered, and else opens the file.
     */
    private void activate(StatusRow.File row) {
        if (repository != null) {
            // In the mode of the table, so that the diff shows the change the row tells of; a
            // deleted file too, whose diff shows what is gone.
            GitDiff.open(repository, row.path(), row.name(), icon(row), mode.get());
        } else {
            open(row);
        }
    }

    /** Opens the file in the editor; a deleted file has nothing to open. */
    private void open(StatusRow.File row) {
        FileObject file = fileObject(row);
        ContentManager content = Lookup.getDefault().lookup(ContentManager.class);
        if (file != null && file.isData() && content != null) {
            content.openFile(file, null);
        }
    }

    private FileObject fileObject(StatusRow.File row) {
        return repository == null ? null : FileUtil.toFileObject(repository.resolve(row.path()).toFile());
    }

    private Node icon(StatusRow row) {
        if (row instanceof StatusRow.File changed) {
            FileObject file = fileObject(changed);
            FileIconProvider provider = Lookup.getDefault().lookup(FileIconProvider.class);
            Node icon = file == null || provider == null ? null : provider.createIcon(file);
            if (icon == null) {
                // A deleted file has no icon: keep its name in line with the others.
                Region blank = new Region();
                blank.setMinSize(ToolIcons.SIZE, ToolIcons.SIZE);
                blank.setPrefSize(ToolIcons.SIZE, ToolIcons.SIZE);
                return blank;
            }
            return icon;
        }
        return ToolIcons.view(ToolIcons.FOLDER);
    }

    /** The CSS state of a file name: the colour NetBeans gives its change. */
    private static String state(VcsChangeKind kind) {
        return switch (kind) {
            case ADDED, COPIED, UNTRACKED -> "vcs-added";
            case MODIFIED, RENAMED, TYPE_CHANGED -> "vcs-modified";
            case DELETED -> "vcs-deleted";
            case CONFLICTED -> "vcs-conflicted";
        };
    }

    /** Where a renamed or copied file comes from and where it is now, or {@code null} for any other row. */
    static String movedFrom(StatusRow row) {
        if (row instanceof StatusRow.File changed && changed.status().originalPath() != null
                && (changed.kind() == VcsChangeKind.RENAMED || changed.kind() == VcsChangeKind.COPIED)) {
            return NbBundle.getMessage(GitStatusPane.class, "Git.moved",
                    changed.status().originalPath(), changed.status().path());
        }
        return null;
    }

    static String label(VcsChangeKind kind) {
        return message("Git.kind." + kind.name());
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitStatusPane.class, key);
    }

    /** The File column: the icon and the name, coloured by the change. */
    private final class FileCell extends TreeTableCell<StatusRow, StatusRow> {

        FileCell() {
            getStyleClass().add("git-file-cell");
        }

        @Override
        protected void updateItem(StatusRow row, boolean empty) {
            super.updateItem(row, empty);
            String state = !empty && row instanceof StatusRow.File changed ? state(changed.kind()) : null;
            for (PseudoClass kind : KIND_STATES) {
                pseudoClassStateChanged(kind, kind.getPseudoClassName().equals(state));
            }
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
            } else {
                setText(row.name());
                setGraphic(icon(row));
                String moved = movedFrom(row);
                setTooltip(moved == null ? null : new Tooltip(moved));
                setAccessibleText(moved == null ? row.name() : row.name() + ", " + moved);
            }
        }
    }
}
