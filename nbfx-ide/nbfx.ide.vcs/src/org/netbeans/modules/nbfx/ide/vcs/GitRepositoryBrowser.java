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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import org.netbeans.api.nbfx.icons.ToolIcons;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.api.nbfx.project.ProjectRegistry;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * The Git Repository Browser: the repositories of the open projects, but those closed here, each
 * with its branches, tags, stashes and remotes ({@link RepositoryTree}). It reads them with the {@code git} command line off
 * the FX thread, again whenever the projects change, the view is come back to or Refresh is chosen,
 * and keeps the rows that are still there as they were: expanded, and selected. The rows have
 * their actions in their context menu ({@link GitBrowserActions}); a double click checks a branch or
 * a tag out, and applies a stash.
 *
 * @since 1.4
 */
final class GitRepositoryBrowser extends StackPane {

    private static final RequestProcessor RP = new RequestProcessor("nbfx-git-browser", 1);
    /** How long a read waits for another to be asked for, in milliseconds: one read serves them all. */
    private static final int READ_DELAY = 100;
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    private final TreeView<RepositoryTree.Row> tree = new TreeView<>(new TreeItem<>());
    private final Label empty = new Label(message("Git.browser.empty"));
    /** The open projects, or {@code null} without a registry of them; kept with its listener. */
    private final ObservableList<OpenProject> projects;
    private final ListChangeListener<OpenProject> projectsListener = change -> showOpenProjects();
    /** The working trees shown, in the order of their rows. On the FX thread. */
    private List<Path> roots = List.of();
    /** The working trees there are to show, those closed among them. On the FX thread. */
    private List<Path> available = List.of();
    /**
     * The working trees closed with Close Repository: not shown until a project is opened in one, or
     * the browser is opened for one. On the FX thread.
     */
    private final Set<Path> closed = new HashSet<>();
    /** What was last read of each of {@link #roots}; a repository read again shows this meanwhile. */
    private final Map<Path, RepositoryTree.Row> known = new HashMap<>();
    /** Counts the reads asked for: what an earlier one found is not shown over a later one. */
    private final AtomicLong reads = new AtomicLong();
    /** The working trees of the last read asked for; the reader takes them when it runs. */
    private volatile List<Path> requested = List.of();
    /** Reads the repositories; asked again before it ran, it runs once, for the last request. */
    private final RequestProcessor.Task reader = RP.create(this::read);
    private final GitBrowserActions rowActions = new GitBrowserActions(this::changed);
    private final ContextMenu menu = new ContextMenu();

    GitRepositoryBrowser() {
        getStylesheets().add(Objects.requireNonNull(
                GitRepositoryBrowser.class.getResource("repository-browser.css")).toExternalForm());
        tree.setShowRoot(false);
        tree.getStyleClass().add("git-repository-tree");
        tree.setCellFactory(view -> new RowCell());
        // A branch or a tag opened is one checked out, and a stash one applied, as in NetBeans; a row
        // with rows below opens those.
        tree.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            TreeItem<RepositoryTree.Row> item = tree.getSelectionModel().getSelectedItem();
            GitRepositoryInfo repository = repositoryOf(item);
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && repository != null
                    && event.getTarget() instanceof Node target && isInCell(target)) {
                if (item.getValue().item() instanceof GitRepositoryInfo.Revision revision) {
                    rowActions.checkout(repository, revision);
                } else if (item.getValue().item() instanceof GitRepositoryInfo.Stash stash) {
                    rowActions.apply(repository, stash);
                }
            }
        });
        MenuItem refresh = new MenuItem(message("Git.refresh"), ToolIcons.view(ToolIcons.REFRESH));
        refresh.setOnAction(event -> reload());
        menu.getItems().add(refresh);
        tree.setContextMenu(menu);
        // Asked for with the mouse or the keyboard, the menu is that of the row selected by then.
        tree.setOnContextMenuRequested(event -> {
            List<MenuItem> items = new ArrayList<>(actions(tree.getSelectionModel().getSelectedItem()));
            if (!items.isEmpty()) {
                items.add(new SeparatorMenuItem());
            }
            items.add(refresh);
            menu.getItems().setAll(items);
        });
        empty.getStyleClass().add("git-repository-empty");
        getChildren().addAll(tree, empty);

        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        projects = registry == null ? null : registry.getOpenProjects();
        if (projects != null) {
            projects.addListener(projectsListener);
        }
        // Coming back to the view shows what was checked out, fetched or tagged meanwhile.
        focusWithinProperty().subscribe(focused -> {
            if (focused) {
                reload();
            }
        });
        showOpenProjects();
    }

    /** What can be done with the row of {@code item}, beside reading again: the entries of its menu. */
    List<MenuItem> actions(TreeItem<RepositoryTree.Row> item) {
        GitRepositoryInfo repository = repositoryOf(item);
        int place = item == null ? -1 : tree.getRoot().getChildren().indexOf(item);
        if (place >= 0 && place < roots.size()) {
            // A repository, also one that could not be read: that one is closed, and nothing else.
            Path root = roots.get(place);
            List<MenuItem> items = new ArrayList<>();
            if (repository != null) {
                items.addAll(rowActions.items(repository));
                items.add(new SeparatorMenuItem());
            }
            MenuItem close = new MenuItem(message("Git.repository.close"));
            close.setOnAction(event -> close(root));
            items.add(close);
            return items;
        }
        if (repository != null && item.getValue().item() instanceof GitRepositoryInfo.Branch branch) {
            return rowActions.items(repository, branch);
        }
        if (repository != null && item.getValue().item() instanceof GitRepositoryInfo.Tag tag) {
            return rowActions.items(repository, tag);
        }
        if (repository != null && item.getValue().item() instanceof GitRepositoryInfo.Stash stash) {
            return rowActions.items(repository, stash);
        }
        if (repository != null && item.getValue().kind() == RepositoryTree.Kind.STASHES) {
            return rowActions.stashItems(repository);
        }
        if (repository != null && item.getValue().item() instanceof GitRepositoryInfo.Remote remote) {
            return rowActions.items(repository, remote, item.getValue().kind());
        }
        if (repository != null && item.getValue().kind() == RepositoryTree.Kind.REMOTES) {
            return rowActions.remoteItems(repository);
        }
        return List.of();
    }

    /** The repository {@code item} is a row of, or {@code null} for none, or one that is not read. */
    private static GitRepositoryInfo repositoryOf(TreeItem<RepositoryTree.Row> item) {
        for (TreeItem<RepositoryTree.Row> at = item; at != null; at = at.getParent()) {
            if (at.getValue() != null && at.getValue().item() instanceof GitRepositoryInfo repository) {
                return repository;
            }
        }
        return null;
    }

    /** The tree, whose root is not shown: its children are the repositories. */
    TreeView<RepositoryTree.Row> tree() {
        return tree;
    }

    /**
     * Shows the repositories whose working trees are {@code folders}, in that order, and reads them.
     * Must run on the JavaFX Application Thread.
     */
    void setRepositories(List<Path> folders) {
        // A repository that came among them since is one opened: closed before, it is shown again.
        Set<Path> opened = new LinkedHashSet<>(folders);
        available.forEach(opened::remove);
        closed.removeAll(opened);
        available = List.copyOf(new LinkedHashSet<>(folders));
        showAvailable();
    }

    /**
     * What an action did is shown: the repositories are read again, and the Projects and Files
     * trees mark their files anew - a commit, a checkout or a stash leaves other files changed.
     */
    private void changed() {
        roots.forEach(GitStatusCache.getDefault()::refresh);
        reload();
    }

    /**
     * Close Repository: takes the repository whose working tree is {@code root} out of the browser,
     * and does nothing else to it. It is back when a project is opened in it, or the browser is
     * opened for it ({@link #select}). Must run on the JavaFX Application Thread.
     */
    void close(Path root) {
        closed.add(root);
        showAvailable();
    }

    /** Shows the repositories there are, but those closed, and reads them. */
    private void showAvailable() {
        closed.retainAll(available);
        roots = available.stream().filter(root -> !closed.contains(root)).toList();
        known.keySet().retainAll(roots);
        reload();
    }

    /**
     * Selects the repository whose working tree holds {@code folder} and shows what is in it, also
     * one that was closed; does nothing when the browser has no such repository. Must run on the JavaFX Application Thread.
     */
    void select(Path folder) {
        Path root = folder == null ? null : GitMenus.workingTreeOf(folder.toFile());
        if (root == null) {
            return;
        }
        if (closed.remove(root)) {
            showAvailable();
        }
        String key = RepositoryTree.key(root);
        for (TreeItem<RepositoryTree.Row> item : tree.getRoot().getChildren()) {
            if (item.getValue().key().equals(key)) {
                item.setExpanded(true);
                tree.getSelectionModel().select(item);
                tree.scrollTo(tree.getRow(item));
                return;
            }
        }
    }

    /**
     * Reads the repositories again, off the FX thread; they show what was last read of them until
     * then. Asked several times in a row, it reads once. Must run on the JavaFX Application Thread.
     */
    void reload() {
        show(roots.stream().map(root -> known.getOrDefault(root, RepositoryTree.unread(root, null))).toList());
        reads.incrementAndGet();
        requested = roots;
        reader.schedule(READ_DELAY);
    }

    /** Shows the repositories the open projects are in: a repository shared by several of them once. */
    private void showOpenProjects() {
        Set<Path> folders = new LinkedHashSet<>();
        if (projects != null) {
            for (OpenProject project : projects) {
                Path folder = GitMenus.folderOf(project);
                Path root = folder == null ? null : GitMenus.workingTreeOf(folder.toFile());
                if (root != null) {
                    folders.add(root);
                }
            }
        }
        setRepositories(List.copyOf(folders));
    }

    private void read() {
        long read = reads.get();
        List<Path> asked = requested;
        Map<Path, RepositoryTree.Row> rows = new HashMap<>();
        for (Path root : asked) {
            if (read != reads.get()) {
                // Asked again meanwhile: that read follows.
                return;
            }
            try {
                rows.put(root, RepositoryTree.of(GitRefs.read(root)));
            } catch (IOException | RuntimeException ex) {
                // No repository any more, or one git cannot read: its row says so, and holds nothing.
                rows.put(root, RepositoryTree.unread(root, ex.getMessage()));
            }
        }
        Platform.runLater(() -> {
            if (read != reads.get()) {
                return;
            }
            known.clear();
            known.putAll(rows);
            show(asked.stream().map(rows::get).toList());
        });
    }

    private void show(List<RepositoryTree.Row> rows) {
        // The selection is of a place in the tree, not of a row: a branch added above it would take it.
        TreeItem<RepositoryTree.Row> selected = tree.getSelectionModel().getSelectedItem();
        sync(tree.getRoot(), rows);
        if (selected != tree.getSelectionModel().getSelectedItem()) {
            if (isShown(selected)) {
                tree.getSelectionModel().select(selected);
            } else {
                tree.getSelectionModel().clearSelection();
            }
        }
        boolean none = rows.isEmpty();
        tree.setVisible(!none);
        empty.setVisible(none);
    }

    /** Whether {@code node} is in a row, and not in the empty space below the rows. */
    private static boolean isInCell(Node node) {
        for (Node at = node; at != null; at = at.getParent()) {
            if (at instanceof TreeCell<?> cell) {
                return !cell.isEmpty();
            }
        }
        return false;
    }

    /** Whether {@code item} is in the tree, with every item above it expanded. */
    private boolean isShown(TreeItem<RepositoryTree.Row> item) {
        TreeItem<RepositoryTree.Row> at = item;
        while (at != null && at != tree.getRoot()) {
            at = at.getParent();
            if (at != null && at != tree.getRoot() && !at.isExpanded()) {
                return false;
            }
        }
        return at != null;
    }

    /**
     * Makes the items below {@code parent} those of {@code rows}. The item of a row that was there
     * is kept, with whether it is expanded and what is below it, so reading a repository again
     * moves nothing that did not change.
     */
    private static void sync(TreeItem<RepositoryTree.Row> parent, List<RepositoryTree.Row> rows) {
        Map<String, TreeItem<RepositoryTree.Row>> existing = new HashMap<>();
        for (TreeItem<RepositoryTree.Row> item : parent.getChildren()) {
            existing.put(item.getValue().key(), item);
        }
        List<TreeItem<RepositoryTree.Row>> items = new ArrayList<>(rows.size());
        for (RepositoryTree.Row row : rows) {
            TreeItem<RepositoryTree.Row> item = existing.get(row.key());
            if (item == null) {
                item = new TreeItem<>(row);
            } else if (!row.equals(item.getValue())) {
                item.setValue(row);
            }
            sync(item, row.children());
            items.add(item);
        }
        if (!items.equals(parent.getChildren())) {
            parent.getChildren().setAll(items);
        }
    }

    /** A row: its icon and text, in bold for the branch that is checked out. */
    private final class RowCell extends TreeCell<RepositoryTree.Row> {

        RowCell() {
            // A tooltip over the menu, or under it, says nothing of what the menu is about.
            menu.showingProperty().subscribe(showing -> updateTooltip());
        }

        @Override
        protected void updateItem(RepositoryTree.Row row, boolean isEmpty) {
            super.updateItem(row, isEmpty);
            if (isEmpty || row == null) {
                setText(null);
                setGraphic(null);
                pseudoClassStateChanged(ACTIVE, false);
            } else {
                setText(row.text());
                setGraphic(GitIcons.view(row.kind().icon()));
                pseudoClassStateChanged(ACTIVE, row.kind() == RepositoryTree.Kind.ACTIVE_BRANCH);
            }
            updateTooltip();
        }

        /** What the row says of itself, unless its menu is open; taking it away hides one that shows. */
        private void updateTooltip() {
            RepositoryTree.Row row = isEmpty() ? null : getItem();
            String text = row == null || menu.isShowing() ? null : row.tooltip();
            if (text == null) {
                setTooltip(null);
            } else if (getTooltip() == null || !text.equals(getTooltip().getText())) {
                setTooltip(new Tooltip(text));
            }
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitRepositoryBrowser.class, key);
    }
}
