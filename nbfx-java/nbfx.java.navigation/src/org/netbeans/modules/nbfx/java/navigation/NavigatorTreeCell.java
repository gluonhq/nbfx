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
package org.netbeans.modules.nbfx.java.navigation;

import javafx.stage.Window;
import org.netbeans.modules.nbfx.project.ui.TreeCellDnD;
import org.netbeans.modules.nbfx.java.navigation.utils.NavigatorIcons;
import org.netbeans.modules.nbfx.project.ui.utils.Projects;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.netbeans.api.nbfx.vcs.VcsFileState;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

/**
 * Base class for navigator tree cells (the logical Project view and the physical Files view). It
 * installs the shared drag-and-drop move support ({@link TreeCellDnD}) and the shared
 * {@link NavigatorContextMenuFactory context menu}, driven by a view-specific
 * {@link NavigatorContextMenuFactory.Host}. It also marks the project root nodes, so that with
 * several projects open the tree shows which one the project-scoped actions apply to, and the files
 * version control reports as changed ({@link VcsStates}), whose names are coloured, and the
 * folders that hold them, whose icons get a badge ({@link #setIcon}). Subclasses
 * only implement {@code updateItem} since their cell graphics differ.
 *
 * @param <T> the tree item value type
 */
abstract class NavigatorTreeCell<T> extends TreeCell<T> {

    /** Style class of the top-level node of every open project. */
    private static final String ROOT_STYLE_CLASS = "project-root";
    /** Style class of the top-level node of the selected project (the one actions apply to). */
    private static final String SELECTED_STYLE_CLASS = "project-root-selected";

    /** The pseudo-classes {@code vcs-added}, {@code vcs-modified}, ... the name of a changed file is coloured by. */
    private static final Map<VcsFileState, PseudoClass> VCS_STATES = new EnumMap<>(VcsFileState.class);

    static {
        for (VcsFileState state : VcsFileState.values()) {
            VCS_STATES.put(state, PseudoClass.getPseudoClass("vcs-" + state.name().toLowerCase(Locale.ROOT)));
        }
    }

    private static final double BADGE_SIZE = 8;
    private static final Image MODIFIED_BADGE = badge("modified-badge.png");
    private static final Image CONFLICTS_BADGE = badge("conflicts-badge.png");

    private final NavigatorContextMenuFactory.Host<T> host;
    /** The badge and the box it sits in, right of the icon and at its foot. */
    private final ImageView vcsBadge = new ImageView();
    private final HBox vcsBadged = new HBox();
    /** The state of the changed files the folder of this cell holds, or {@code null}. */
    private VcsFileState within;
    private final Label vcsTooltipName = new Label();
    private final Label vcsTooltipState = new Label();
    private final ImageView vcsTooltipBadge = new ImageView();
    private final Tooltip vcsTooltip = createVcsTooltip();

    /**
     * @param reselect selects the moved files at their new location once the tree has rebuilt
     * @param host     the view-specific hooks the shared drag-and-drop and context menu are built on
     */
    protected NavigatorTreeCell(Consumer<List<FileObject>> reselect,
            NavigatorContextMenuFactory.Host<T> host) {
        this.host = host;
        vcsBadge.setFitWidth(BADGE_SIZE);
        vcsBadge.setFitHeight(BADGE_SIZE);
        vcsBadged.setAlignment(Pos.BOTTOM_LEFT);
        TreeCellDnD.install(this, host::fileOf, host::isDeletable, reselect);
        NavigatorContextMenuFactory<T> factory = new NavigatorContextMenuFactory<>(host);
        setOnContextMenuRequested(e -> {
            TreeItem<T> treeItem = getTreeItem();
            TreeView<T> tree = getTreeView();
            // Right-clicking outside the current multi-selection collapses it to the clicked row.
            if (tree != null && treeItem != null
                    && !tree.getSelectionModel().getSelectedItems().contains(treeItem)) {
                tree.getSelectionModel().clearSelection();
                tree.getSelectionModel().select(treeItem);
            }
            List<TreeItem<T>> selection = tree == null
                    ? List.of()
                    : new ArrayList<>(tree.getSelectionModel().getSelectedItems());
            ContextMenu contextMenu = factory.create(treeItem, selection);
            Window window = tree == null || tree.getScene() == null ? null : tree.getScene().getWindow();
            if (contextMenu != null && window != null) {
                // Shown on the window, as a menu set with setContextMenu is, not on the tree: a popup
                // does not close on a press inside the node it is shown on, so a menu anchored to the
                // tree stayed open while other rows were clicked.
                contextMenu.show(window, e.getScreenX(), e.getScreenY());
            }
            e.consume();
        });
    }

    /**
     * Marks this cell when it holds a project's top-level node, and additionally when that project
     * is the selected one. Subclasses get this for free by calling {@code super.updateItem}.
     */
    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        TreeItem<T> treeItem = empty ? null : getTreeItem();
        boolean root = treeItem != null && host.isRoot(treeItem);
        setStyleClass(ROOT_STYLE_CLASS, root);
        setStyleClass(SELECTED_STYLE_CLASS, root && Projects.isSelected(host.fileOf(treeItem)));
        FileObject file = treeItem == null ? null : host.fileOf(treeItem);
        VcsFileState state = VcsStates.stateOf(file);
        for (VcsFileState marked : VcsFileState.values()) {
            pseudoClassStateChanged(VCS_STATES.get(marked), marked == state);
            vcsTooltipState.pseudoClassStateChanged(VCS_STATES.get(marked), marked == state);
        }
        List<FileObject> listed = treeItem == null ? null : host.listedFiles(treeItem);
        if (listed != null) {
            within = VcsStates.stateAmong(listed);
        } else {
            within = treeItem == null ? null : VcsStates.stateWithin(file, host.isRecursive(treeItem));
        }
        if (state != null) {
            // A changed file: its state, in its colour.
            vcsTooltipState.setText(NbBundle.getMessage(NavigatorTreeCell.class, "Vcs.state." + state.name()));
            vcsTooltipState.setGraphic(null);
            setTooltip(vcsTooltip);
            setAccessibleHelp(vcsTooltipState.getText());
        } else if (within != null) {
            // What holds changed files: its badge and what the badge says.
            vcsTooltipBadge.setImage(within == VcsFileState.CONFLICTED ? CONFLICTS_BADGE : MODIFIED_BADGE);
            vcsTooltipState.setText(NbBundle.getMessage(NavigatorTreeCell.class, "Vcs.within." + within.name()));
            vcsTooltipState.setGraphic(vcsTooltipBadge);
            setTooltip(vcsTooltip);
            // The colour and the badge say it to the eye only.
            setAccessibleHelp(vcsTooltipState.getText());
        } else {
            setTooltip(null);
            setAccessibleHelp(null);
        }
    }

    /**
     * Shows {@code icon} as the graphic of this cell, with the version control badge of the folder,
     * package or project it holds at its lower right, as in NetBeans: one for changed files in it,
     * another when some have conflicts. Subclasses call it after {@code super.updateItem}.
     */
    protected final void setIcon(Node icon) {
        if (icon == null || within == null) {
            setGraphic(icon);
            return;
        }
        vcsBadge.setImage(within == VcsFileState.CONFLICTED ? CONFLICTS_BADGE : MODIFIED_BADGE);
        vcsBadged.getChildren().setAll(icon, vcsBadge);
        setGraphic(vcsBadged);
    }

    /**
     * The tooltip of a changed file and of what holds changed files, as in NetBeans: what the row
     * is and, below, the state of the file in its colour, or the badge and what it stands for.
     */
    private Tooltip createVcsTooltip() {
        // File names are user data: an underscore in one must not become a mnemonic.
        vcsTooltipName.setMnemonicParsing(false);
        vcsTooltipState.getStyleClass().add("vcs-tooltip-state");
        vcsTooltipBadge.setFitWidth(BADGE_SIZE);
        vcsTooltipBadge.setFitHeight(BADGE_SIZE);
        Tooltip tooltip = new Tooltip();
        // Described when shown, not for every row drawn: telling the kind of a package is a query.
        tooltip.setOnShowing(event -> {
            TreeItem<T> treeItem = getTreeItem();
            vcsTooltipName.setText(treeItem == null ? "" : host.describe(treeItem));
        });
        tooltip.getStyleClass().add("vcs-tooltip");
        tooltip.setGraphic(new VBox(vcsTooltipName, vcsTooltipState));
        tooltip.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        return tooltip;
    }

    private static Image badge(String name) {
        return new Image(Objects.requireNonNull(NavigatorIcons.class.getResource(name), name).toExternalForm());
    }

    private void setStyleClass(String styleClass, boolean present) {
        if (present) {
            if (!getStyleClass().contains(styleClass)) {
                getStyleClass().add(styleClass);
            }
        } else {
            getStyleClass().remove(styleClass);
        }
    }
}
