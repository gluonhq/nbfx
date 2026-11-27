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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Change;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Commit;
import org.netbeans.modules.nbfx.ide.vcs.GitHistory.Criteria;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.CommitRow;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.Field;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.FileRow;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.MoreRow;
import org.netbeans.modules.nbfx.ide.vcs.HistoryRows.Row;
import org.netbeans.modules.nbfx.ide.vcs.model.VcsChangeKind;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * The summary of the History view, as NetBeans' Show History: the commits a search found, each
 * opened to its whole message and the files it changed, a filter over what was found, and the
 * Search Options the search is made with. It searches off the FX thread through its
 * {@link Reader}, and knows nothing of where it is shown.
 * <p>
 * It is made for some paths of a repository - a project, a folder, a file - and searches the
 * commits that changed them; Show All Files lists, for those commits, the files outside those
 * paths too. Must be made and used on the JavaFX Application Thread.
 *
 * @since 1.4
 */
final class GitHistoryPane extends BorderPane {

    /** What reads the history: git in the IDE, a stand-in in a test. Called off the FX thread. */
    interface Reader {

        /** The commits {@code criteria} asks for, past the first {@code skip} of them. */
        GitHistory read(Criteria criteria, int skip) throws IOException;

        /** All the files the commit {@code id} changed. */
        List<Change> changes(String id) throws IOException;
    }

    private static final RequestProcessor RP = new RequestProcessor("nbfx-git-history", 1);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault());
    private static final PseudoClass ADDED = PseudoClass.getPseudoClass("vcs-added");
    private static final PseudoClass MODIFIED = PseudoClass.getPseudoClass("vcs-modified");
    private static final PseudoClass DELETED = PseudoClass.getPseudoClass("vcs-deleted");
    private static final PseudoClass EXPANDED = PseudoClass.getPseudoClass("expanded");

    private final Reader reader;
    private final List<String> paths;
    private final ListView<Row> list = new ListView<>();
    private final Label placeholder = new Label();
    private final CheckBox showAll = new CheckBox(message("Git.history.showAll"));
    private final ComboBox<Field> field = new ComboBox<>();
    private final TextField contains = new TextField();
    private final TextField message = new TextField();
    private final TextField author = new TextField();
    private final TextField limit = new TextField(String.valueOf(Criteria.DEFAULT_LIMIT));
    private final CheckBox merges = new CheckBox(message("Git.history.merges"));
    private final TextField branch = new TextField();
    private final TextField from = new TextField();
    private final TextField to = new TextField();
    private final Button search = new Button(message("Git.history.search"));

    /** The commits found so far, and whether the search has more. */
    private final List<Commit> commits = new ArrayList<>();
    private boolean more;
    /** The ids of the commits that are opened. */
    private final Set<String> expanded = new HashSet<>();
    /** All the files of the commits they were read for, by id. */
    private final Map<String, List<Change>> allChanges = new HashMap<>();
    /** What the commits shown were searched with. */
    private Criteria searched;
    /** Counts the searches: what an earlier one finds is no longer wanted. */
    private final AtomicLong searches = new AtomicLong();
    private boolean searching;
    private boolean readingMore;
    private String failure;

    /** The history of {@code paths}, relative to {@code root}, in the repository at {@code root}. */
    GitHistoryPane(Path root, List<String> paths) {
        this(new Reader() {
            @Override
            public GitHistory read(Criteria criteria, int skip) throws IOException {
                return GitLog.read(root, criteria, skip);
            }

            @Override
            public List<Change> changes(String id) throws IOException {
                return GitLog.changes(root, id);
            }
        }, paths);
    }

    GitHistoryPane(Reader reader, List<String> paths) {
        this.reader = Objects.requireNonNull(reader);
        this.paths = List.copyOf(paths);
        getStylesheets().add(Objects.requireNonNull(GitHistoryPane.class.getResource("history.css")).toExternalForm());
        getStyleClass().add("git-history");
        setTop(createBar());
        setCenter(createList());
        setBottom(createOptions());
        search();
    }

    private HBox createBar() {
        // Nothing more to show when the history is that of the whole repository.
        showAll.setDisable(paths.isEmpty());
        showAll.setTooltip(new Tooltip(message("Git.history.showAll.tooltip")));
        showAll.selectedProperty().subscribe(selected -> {
            if (selected) {
                commits.stream().filter(commit -> expanded.contains(commit.id())).forEach(this::readAllChanges);
            }
            showRows();
        });
        field.getItems().setAll(Field.values());
        field.setValue(Field.ALL);
        field.setConverter(new StringConverter<>() {
            @Override
            public String toString(Field value) {
                return value == null ? "" : message("Git.history.field." + value.name());
            }

            @Override
            public Field fromString(String text) {
                return null;
            }
        });
        field.valueProperty().subscribe(this::showRows);
        contains.textProperty().subscribe(this::showRows);
        contains.setPrefColumnCount(24);
        Label filter = new Label(message("Git.history.filter"));
        filter.setLabelFor(field);
        Label containsLabel = new Label(message("Git.history.contains"));
        containsLabel.setLabelFor(contains);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox bar = new HBox(showAll, gap, filter, field, containsLabel, contains);
        bar.getStyleClass().add("git-history-bar");
        return bar;
    }

    private ListView<Row> createList() {
        list.getStyleClass().add("git-history-list");
        list.setCellFactory(view -> new RowCell());
        placeholder.getStyleClass().add("git-history-empty");
        list.setPlaceholder(placeholder);
        list.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            Row row = list.getSelectionModel().getSelectedItem();
            if (row instanceof CommitRow commit && (event.getCode() == KeyCode.ENTER
                    || event.getCode() == KeyCode.SPACE
                    || (event.getCode() == KeyCode.RIGHT && !commit.expanded())
                    || (event.getCode() == KeyCode.LEFT && commit.expanded()))) {
                toggle(commit.commit());
                event.consume();
            } else if (row instanceof MoreRow && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)) {
                showMore();
                event.consume();
            }
        });
        return list;
    }

    private TitledPane createOptions() {
        merges.setSelected(true);
        limit.setPrefColumnCount(6);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        ColumnConstraints label = new ColumnConstraints();
        label.setHalignment(HPos.RIGHT);
        ColumnConstraints value = new ColumnConstraints();
        value.setHgrow(Priority.ALWAYS);
        value.setFillWidth(true);
        grid.getColumnConstraints().setAll(label, value, label, value);
        HBox limited = new HBox(12, limit, merges);
        limited.setAlignment(Pos.CENTER_LEFT);
        grid.addRow(0, labelFor("Git.history.message", message), message, labelFor("Git.history.branch", branch), branch);
        grid.addRow(1, labelFor("Git.history.author", author), author, labelFor("Git.history.from", from), from);
        grid.addRow(2, labelFor("Git.history.limit", limit), limited, new Region(), hint());
        grid.add(labelFor("Git.history.to", to), 2, 3);
        grid.add(to, 3, 3);
        grid.add(hint(), 3, 4);
        search.setOnAction(event -> search());
        grid.add(search, 3, 5);
        GridPane.setHalignment(search, HPos.RIGHT);
        for (TextField text : List.of(message, author, limit, branch, from, to)) {
            // Enter in a field searches, as the button does.
            text.setOnAction(event -> search());
        }
        TitledPane options = new TitledPane(message("Git.history.options"), grid);
        options.getStyleClass().add("git-history-options");
        options.setAnimated(false);
        return options;
    }

    private static Label labelFor(String key, TextField text) {
        Label label = new Label(message(key));
        label.setLabelFor(text);
        return label;
    }

    private static Label hint() {
        Label hint = new Label(message("Git.history.hint"));
        hint.getStyleClass().add("git-history-hint");
        return hint;
    }

    /** Search: finds the commits the Search Options ask for, in place of those shown. */
    void search() {
        Criteria criteria = criteria();
        long asked = searches.incrementAndGet();
        searched = criteria;
        commits.clear();
        more = false;
        expanded.clear();
        allChanges.clear();
        failure = null;
        searching = true;
        readingMore = false;
        showRows();
        RP.post(() -> {
            GitHistory found = null;
            String said = null;
            try {
                found = reader.read(criteria, 0);
            } catch (IOException | RuntimeException ex) {
                said = String.valueOf(ex.getMessage());
            }
            GitHistory history = found;
            String failed = said;
            Platform.runLater(() -> {
                if (searches.get() != asked) {
                    return;
                }
                searching = false;
                failure = failed;
                if (history != null) {
                    commits.addAll(history.commits());
                    more = history.more();
                }
                showRows();
            });
        });
    }

    /** Show more: the next commits of the search, after those shown. */
    void showMore() {
        if (!more || searching || readingMore) {
            return;
        }
        long asked = searches.get();
        Criteria criteria = searched;
        int skip = commits.size();
        readingMore = true;
        RP.post(() -> {
            GitHistory found = null;
            try {
                found = reader.read(criteria, skip);
            } catch (IOException | RuntimeException ex) {
                // The commits found stay, and the row to ask again.
            }
            GitHistory history = found;
            Platform.runLater(() -> {
                if (searches.get() != asked) {
                    return;
                }
                readingMore = false;
                if (history != null) {
                    commits.addAll(history.commits());
                    more = history.more();
                    showRows();
                }
            });
        });
    }

    /** What the Search Options ask for; a limit that is no number above zero is the usual one. */
    Criteria criteria() {
        int most;
        try {
            most = Integer.parseInt(limit.getText().strip());
        } catch (NumberFormatException ex) {
            most = 0;
        }
        if (most < 1) {
            most = Criteria.DEFAULT_LIMIT;
            limit.setText(String.valueOf(most));
        }
        return new Criteria(branch.getText(), message.getText(), author.getText(), from.getText(), to.getText(), most,
                merges.isSelected(), paths);
    }

    /** Opens {@code commit} to its message and files, or closes it. */
    void toggle(Commit commit) {
        if (!expanded.remove(commit.id())) {
            expanded.add(commit.id());
            if (showAll.isSelected()) {
                readAllChanges(commit);
            }
        }
        showRows();
    }

    /** Reads all the files of {@code commit}, once; until they are read it lists those of the search. */
    private void readAllChanges(Commit commit) {
        if (paths.isEmpty() || allChanges.containsKey(commit.id())) {
            return;
        }
        // Marked as asked for with what is known, so that it is read once.
        allChanges.put(commit.id(), commit.changes());
        long asked = searches.get();
        RP.post(() -> {
            List<Change> all;
            try {
                all = reader.changes(commit.id());
            } catch (IOException | RuntimeException ex) {
                return;
            }
            Platform.runLater(() -> {
                if (searches.get() == asked) {
                    allChanges.put(commit.id(), all);
                    showRows();
                }
            });
        });
    }

    private List<Change> changesOf(Commit commit) {
        return showAll.isSelected() ? allChanges.getOrDefault(commit.id(), commit.changes()) : commit.changes();
    }

    /** Shows the rows for what was found, with the row that was selected still selected. */
    private void showRows() {
        Row selected = list.getSelectionModel().getSelectedItem();
        List<Row> rows = HistoryRows.of(commits, expanded, this::changesOf, field.getValue(), contains.getText(),
                more);
        if (searching) {
            placeholder.setText(message("Git.history.searching"));
        } else if (failure != null) {
            placeholder.setText(failure);
        } else {
            placeholder.setText(message(commits.isEmpty() ? "Git.history.none" : "Git.history.filtered"));
        }
        // With no commit to show, the row to ask for more says nothing: the placeholder does.
        list.getItems().setAll(rows.stream().anyMatch(CommitRow.class::isInstance) ? rows : List.of());
        int again = selected == null ? -1 : indexOf(list.getItems(), selected);
        if (again >= 0) {
            list.getSelectionModel().select(again);
        } else {
            list.getSelectionModel().clearSelection();
        }
    }

    /** Where the row for what {@code row} stood for is now: the same commit, opened or not, the same file. */
    private static int indexOf(List<Row> rows, Row row) {
        for (int i = 0; i < rows.size(); i++) {
            Row at = rows.get(i);
            boolean same = switch (row) {
                case CommitRow commit -> at instanceof CommitRow other && other.commit().id().equals(commit.commit().id());
                case FileRow file -> at instanceof FileRow other && other.commit().id().equals(file.commit().id())
                        && other.change().path().equals(file.change().path());
                case MoreRow ignored -> at instanceof MoreRow;
            };
            if (same) {
                return i;
            }
        }
        return -1;
    }

    /** The rows shown. */
    List<Row> rows() {
        return list.getItems();
    }

    ListView<Row> list() {
        return list;
    }

    /** What is said in place of the rows when there are none. */
    String placeholderText() {
        return placeholder.getText();
    }

    CheckBox showAll() {
        return showAll;
    }

    ComboBox<Field> filterField() {
        return field;
    }

    TextField filterText() {
        return contains;
    }

    /** A field of the Search Options: {@code message}, {@code author}, {@code limit}, {@code branch}, {@code from}, {@code to}. */
    TextField option(String name) {
        return switch (name) {
            case "message" -> message;
            case "author" -> author;
            case "limit" -> limit;
            case "branch" -> branch;
            case "from" -> from;
            case "to" -> to;
            default -> throw new IllegalArgumentException(name);
        };
    }

    CheckBox includeMerges() {
        return merges;
    }

    /** The letter a change is told by, as git does. */
    static String letter(VcsChangeKind kind) {
        return switch (kind) {
            case ADDED, UNTRACKED -> "A";
            case MODIFIED, CONFLICTED -> "M";
            case DELETED -> "D";
            case RENAMED -> "R";
            case COPIED -> "C";
            case TYPE_CHANGED -> "T";
        };
    }

    /** Who made {@code commit} and when, as its row says it. */
    static String who(Commit commit) {
        String name = commit.email().isBlank() ? commit.author()
                : NbBundle.getMessage(GitHistoryPane.class, "Git.history.who", commit.author(), commit.email());
        return name + "  " + DATE.format(commit.date());
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitHistoryPane.class, key);
    }

    /** Draws a row: a commit, a file of one, or the request for more. */
    private final class RowCell extends ListCell<Row> {

        private final Region arrow = new Region();
        private final Label id = new Label();
        private final Label who = new Label();
        private final Label summary = new Label();
        private final Label body = new Label();
        private final HBox commitBox;
        private final Label kind = new Label();
        private final Label folder = new Label();
        private final Label name = new Label();
        private final HBox fileBox = new HBox(kind, folder, name);
        private final Hyperlink moreLink = new Hyperlink(message("Git.history.more"));
        private final Tooltip moved = new Tooltip();

        RowCell() {
            arrow.getStyleClass().add("git-history-arrow");
            StackPane arrowBox = new StackPane(arrow);
            arrowBox.getStyleClass().add("git-history-arrow-box");
            arrowBox.setOnMouseClicked(event -> {
                if (getItem() instanceof CommitRow row) {
                    toggle(row.commit());
                    event.consume();
                }
            });
            who.getStyleClass().add("git-history-who");
            summary.getStyleClass().add("git-history-summary");
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            VBox text = new VBox(new HBox(id, gap, who), summary, body);
            HBox.setHgrow(text, Priority.ALWAYS);
            commitBox = new HBox(arrowBox, text);
            commitBox.setAlignment(Pos.TOP_LEFT);
            kind.getStyleClass().add("git-history-kind");
            folder.getStyleClass().add("git-history-folder");
            // Names are user data: an underscore in one is no mnemonic.
            for (Label label : List.of(id, who, summary, body, folder, name)) {
                label.setMnemonicParsing(false);
            }
            moreLink.setOnAction(event -> showMore());
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                        && getItem() instanceof CommitRow row) {
                    toggle(row.commit());
                }
            });
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            getStyleClass().removeAll("git-history-commit", "git-history-file", "git-history-more");
            setText(null);
            setTooltip(null);
            switch (empty ? null : row) {
                case CommitRow commit -> showCommit(commit);
                case FileRow file -> showFile(file.change());
                case MoreRow ignored -> {
                    getStyleClass().add("git-history-more");
                    setGraphic(moreLink);
                    setAccessibleText(moreLink.getText());
                }
                case null -> {
                    setGraphic(null);
                    setAccessibleText(null);
                }
            }
        }

        private void showCommit(CommitRow row) {
            Commit commit = row.commit();
            getStyleClass().add("git-history-commit");
            arrow.pseudoClassStateChanged(EXPANDED, row.expanded());
            id.setText(commit.shortId());
            who.setText(who(commit));
            summary.setText(commit.summary());
            // Opened, the rest of the message follows its first line.
            int firstLineEnd = commit.message().indexOf('\n');
            String rest = firstLineEnd < 0 ? "" : commit.message().substring(firstLineEnd).strip();
            boolean shown = row.expanded() && !rest.isEmpty();
            body.setText(shown ? rest : "");
            body.setVisible(shown);
            body.setManaged(shown);
            setGraphic(commitBox);
            setAccessibleText(commit.shortId() + " " + commit.summary() + ", " + who(commit));
        }

        private void showFile(Change change) {
            getStyleClass().add("git-history-file");
            kind.setText(letter(change.kind()));
            kind.pseudoClassStateChanged(ADDED, change.kind() == VcsChangeKind.ADDED || change.kind() == VcsChangeKind.COPIED);
            kind.pseudoClassStateChanged(MODIFIED, change.kind() == VcsChangeKind.MODIFIED
                    || change.kind() == VcsChangeKind.RENAMED || change.kind() == VcsChangeKind.TYPE_CHANGED);
            kind.pseudoClassStateChanged(DELETED, change.kind() == VcsChangeKind.DELETED);
            int slash = change.path().lastIndexOf('/');
            folder.setText(change.path().substring(0, slash + 1));
            name.setText(change.path().substring(slash + 1));
            if (change.originalPath() != null) {
                moved.setText(NbBundle.getMessage(GitHistoryPane.class, "Git.history.file.moved",
                        change.originalPath(), change.path()));
                setTooltip(moved);
            }
            setGraphic(fileBox);
            setAccessibleText(NbBundle.getMessage(GitHistoryPane.class, "Git.history.file.accessible",
                    message("Git.kind." + change.kind().name()), change.path()));
        }
    }
}
