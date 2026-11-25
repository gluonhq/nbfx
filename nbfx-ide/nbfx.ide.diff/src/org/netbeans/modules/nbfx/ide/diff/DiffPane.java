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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Subscription;
import org.netbeans.api.nbfx.diff.Diff;
import org.netbeans.api.nbfx.diff.DiffResult;
import org.netbeans.api.nbfx.diff.Difference;
import org.netbeans.api.nbfx.diff.UnifiedDiff;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.EditorService;
import org.netbeans.api.nbfx.icons.ToolIcons;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Two texts side by side, as NetBeans' graphical diff: each in a read-only editor under its
 * heading, the blocks that differ marked - lines added, deleted and changed, and a rule where one
 * side lacks the lines of the other - and the two scrolled in step, so that the same lines stay
 * beside each other. Between the editors a strip joins each block of one side with its block of
 * the other ({@link DiffConnectors}) under a counter of the blocks; at the right edge another
 * marks them all along the text ({@link DiffOverview}); two buttons above go from one block to the
 * next.
 * <p>
 * Two tabs above the headings choose how the differences are shown: Graphical, as said, or
 * Textual, as a unified diff in one read-only editor, its lines marked as added and deleted.
 * <p>
 * The texts are read and compared off the FX thread; until then, and when they are the same, the
 * line above the headings says so.
 */
final class DiffPane extends BorderPane implements DiffContent {

    private static final Logger LOG = Logger.getLogger(DiffPane.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("nbfx-diff", 1);
    /** The lines kept in view above the block gone to. */
    private static final int CONTEXT_LINES = 3;

    private final DiffSource leftSource;
    private final DiffSource rightSource;
    private final Label summary = new Label();
    private final Label counter = heading("");
    private final Button previous = button(ToolIcons.PREVIOUS_MATCH, "Diff.previous");
    private final Button next = button(ToolIcons.NEXT_MATCH, "Diff.next");
    private final DiffConnectors connectors = new DiffConnectors();
    private final DiffOverview overview = new DiffOverview();
    private final ToggleButton graphicalTab = tab("Diff.graphical");
    private final ToggleButton textualTab = tab("Diff.textual");
    /** The two editors side by side, or the unified diff: what the tab chosen shows. */
    private final StackPane content = new StackPane();
    private final EditorService editors = Lookup.getDefault().lookup(EditorService.class);
    private Node sides;
    private EditorDocument left;
    private EditorDocument right;
    /** The differences as a unified diff; what the Textual tab shows. */
    private UnifiedDiff unified = new UnifiedDiff("", List.of());
    /** The editor of the unified diff, made when the Textual tab is first shown and after each comparison. */
    private EditorDocument textual;
    private List<Difference> differences = List.of();
    private int current = -1;
    private Subscription scrolling = Subscription.EMPTY;
    /**
     * Whether the left editor is the one the user scrolls - the one last under the mouse, pressed
     * or typed in - and the right one follows it; else the other way round. Only the leading
     * editor is followed: the follower cannot always get to the line asked for - its text may be
     * the shorter - and following it back would move the leader, which would be followed again,
     * without end.
     */
    private boolean leftLeads;
    private long comparison;

    DiffPane(DiffSource leftSource, DiffSource rightSource) {
        this(leftSource, rightSource, true);
    }

    /**
     * @param ownBar whether the buttons and the summary are shown above the headings; a
     *        {@link DiffBrowser} shows them in its own bar instead
     */
    DiffPane(DiffSource leftSource, DiffSource rightSource, boolean ownBar) {
        this.leftSource = Objects.requireNonNull(leftSource);
        this.rightSource = Objects.requireNonNull(rightSource);
        getStyleClass().add("diff-pane");
        getStylesheets().add(Objects.requireNonNull(DiffPane.class.getResource("diff.css")).toExternalForm());

        summary.getStyleClass().add("diff-summary");
        summary.setMaxWidth(Double.MAX_VALUE);
        summary.setAlignment(Pos.CENTER);
        HBox.setHgrow(summary, Priority.ALWAYS);
        previous.setOnAction(event -> go(false));
        next.setOnAction(event -> go(true));
        HBox bar = new HBox(previous, next, summary);
        bar.getStyleClass().add("diff-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup tabs = new ToggleGroup();
        graphicalTab.setToggleGroup(tabs);
        textualTab.setToggleGroup(tabs);
        graphicalTab.setSelected(true);
        tabs.selectedToggleProperty().subscribe((was, now) -> {
            if (now == null) {
                // A click on the tab shown would leave none chosen.
                tabs.selectToggle(was);
            } else if (was != null) {
                showTab();
            }
        });
        HBox tabStrip = new HBox(graphicalTab, textualTab);
        tabStrip.getStyleClass().add("diff-view-tabs");
        setTop(ownBar ? new VBox(bar, tabStrip) : tabStrip);
        setCenter(content);

        if (editors != null) {
            left = editors.createPreviewDocument(leftSource.file());
            right = editors.createPreviewDocument(rightSource.file());
            VBox.setVgrow(connectors, Priority.ALWAYS);
            VBox strip = new VBox(counter, connectors);
            strip.getStyleClass().add("diff-strip");
            VBox.setVgrow(overview, Priority.ALWAYS);
            VBox edge = new VBox(heading(""), overview);
            edge.getStyleClass().add("diff-edge");
            HBox both = new HBox(side(leftSource.title(), left.getNode(), true), strip,
                    side(rightSource.title(), right.getNode(), false), edge);
            both.getStyleClass().add("diff-sides");
            sides = both;
            content.getChildren().setAll(sides);
            scrolling = left.topParagraphProperty().subscribe(top -> follow(top.doubleValue(), true))
                    .and(right.topParagraphProperty().subscribe(top -> follow(top.doubleValue(), false)));
        }
        overview.setOnSelect(this::select);
        select(-1);
        refresh();
    }

    /** The differences shown; empty until the texts are compared, and when they are the same. */
    List<Difference> differences() {
        return differences;
    }

    /** The text of the line above the headings. */
    String summary() {
        return summary.getText();
    }

    ReadOnlyStringProperty summaryProperty() {
        return summary.textProperty();
    }

    /** The differences as a unified diff: what the Textual tab shows. Empty until compared, and when the same. */
    String unified() {
        return unified.text();
    }

    /** Whether the Textual tab is the one shown. */
    boolean isTextual() {
        return textualTab.isSelected();
    }

    /** Shows the Textual tab, or the Graphical one. */
    void setTextual(boolean shown) {
        (shown ? textualTab : graphicalTab).setSelected(true);
    }

    ToggleButton graphicalTab() {
        return graphicalTab;
    }

    ToggleButton textualTab() {
        return textualTab;
    }

    /** The block the view is at, counted from zero; -1 when the texts are the same. */
    int current() {
        return current;
    }

    /** What the counter above the connectors reads: "2/5", or nothing without differences. */
    String counter() {
        return counter.getText();
    }

    Button previousButton() {
        return previous;
    }

    Button nextButton() {
        return next;
    }

    DiffOverview overview() {
        return overview;
    }

    /** Reads the two texts again, compares them and marks the blocks. */
    @Override
    public void refresh() {
        long asked = ++comparison;
        summary.setText(message("Diff.loading"));
        RP.post(() -> {
            DiffResult result;
            UnifiedDiff text;
            int rightLines;
            try {
                List<String> to = Diff.lines(rightSource.file().asText("UTF-8"));
                List<String> from = Diff.lines(leftSource.file().asText("UTF-8"));
                rightLines = to.size();
                result = Diff.compute(from, to);
                text = UnifiedDiff.of(leftSource.unifiedName("a/"), rightSource.unifiedName("b/"), from, to, result.differences(),
                        UnifiedDiff.DEFAULT_CONTEXT);
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.INFO, "Cannot compare " + leftSource.file() + " and " + rightSource.file(), ex);
                Platform.runLater(() -> {
                    if (asked == comparison) {
                        show(List.of(), new UnifiedDiff("", List.of()), 1, message("Diff.failed"));
                    }
                });
                return;
            }
            Platform.runLater(() -> {
                if (asked == comparison) {
                    List<Difference> found = result.differences();
                    show(found, text, rightLines, found.isEmpty() ? message("Diff.identical")
                            : NbBundle.getMessage(DiffPane.class, "Diff.differences", found.size()));
                }
            });
        });
    }

    /** Releases the two editors. */
    @Override
    public void dispose() {
        comparison++;
        scrolling.unsubscribe();
        dropTextual();
        if (left != null) {
            left.dispose();
        }
        if (right != null) {
            right.dispose();
        }
    }

    /**
     * Makes the block of that index the current one - counted, drawn as such - and, when there are
     * editors, brings it into view with a few lines above it.
     */
    void select(int index) {
        current = index < 0 || index >= differences.size() ? -1 : index;
        counter.setText(current < 0 ? "" : (current + 1) + "/" + differences.size());
        previous.setDisable(current <= 0);
        next.setDisable(current < 0 || current >= differences.size() - 1);
        connectors.setCurrent(current);
        overview.setCurrent(current);
        if (current >= 0 && isTextual()) {
            if (textual != null && current < unified.differenceLines().size()) {
                textual.scrollToParagraph(Math.max(0, unified.differenceLines().get(current) - CONTEXT_LINES));
            }
        } else if (current >= 0 && left != null && right != null) {
            // The right side leads: it has the lines of the file as it is now.
            leftLeads = false;
            int top = Math.max(0, differences.get(current).rightStart() - CONTEXT_LINES);
            right.scrollToParagraph(top);
            left.scrollToParagraph(DiffLines.toLeft(differences, top));
        }
    }

    private void go(boolean forward) {
        int target = DiffLines.step(current, differences.size(), forward);
        if (target != current) {
            select(target);
        }
    }

    private void show(List<Difference> found, UnifiedDiff text, int rightLines, String told) {
        differences = found;
        unified = text;
        summary.setText(told);
        // Of the texts as they were: written again when its tab is shown.
        dropTextual();
        if (isTextual()) {
            showTab();
        }
        if (left != null && right != null) {
            left.setLineBands(DiffLines.leftBands(found));
            right.setLineBands(DiffLines.rightBands(found));
            connectors.show(left, right, found);
        }
        overview.show(found, rightLines);
        // At the first block, as when the diff opens; without moving the view when there is none.
        select(found.isEmpty() ? -1 : Math.clamp(current, 0, found.size() - 1));
    }

    /** Shows what the tab chosen is of, and brings the current block into view in it. */
    private void showTab() {
        if (isTextual()) {
            if (textual == null) {
                textual = textualEditor();
            }
            if (textual != null) {
                content.getChildren().setAll(textual.getNode());
            } else {
                content.getChildren().clear();
            }
        } else if (sides != null) {
            content.getChildren().setAll(sides);
        }
        if (current >= 0) {
            select(current);
        }
    }

    /**
     * A read-only editor of the unified diff, its lines marked as the two sides mark theirs; or
     * {@code null} without an editor service. The text is that of a file in memory named after
     * the file compared.
     */
    private EditorDocument textualEditor() {
        if (editors == null) {
            return null;
        }
        try {
            FileObject file = FileUtil.createMemoryFileSystem().getRoot()
                    .createData(rightSource.file().getNameExt() + ".diff");
            try (OutputStream out = file.getOutputStream()) {
                out.write(unified.text().getBytes(StandardCharsets.UTF_8));
            }
            EditorDocument editor = editors.createPreviewDocument(file);
            editor.setLineBands(DiffLines.unifiedBands(Diff.lines(unified.text())));
            editor.getNode().getStyleClass().add("diff-textual");
            return editor;
        } catch (IOException ex) {
            LOG.log(Level.INFO, "Cannot show the unified diff", ex);
            return null;
        }
    }

    private void dropTextual() {
        if (textual != null) {
            content.getChildren().remove(textual.getNode());
            textual.dispose();
            textual = null;
        }
    }

    /** Brings beside the top of the leading editor the place of the other that stands by it. */
    private void follow(double top, boolean leftScrolled) {
        if (leftScrolled != leftLeads || left == null || right == null) {
            return;
        }
        if (leftScrolled) {
            right.scrollToParagraph(DiffLines.toRight(differences, top));
        } else {
            left.scrollToParagraph(DiffLines.toLeft(differences, top));
        }
    }

    private VBox side(String title, Node editor, boolean isLeft) {
        VBox.setVgrow(editor, Priority.ALWAYS);
        VBox side = new VBox(heading(title), editor);
        side.getStyleClass().add("diff-side");
        // The two sides share the width equally, whatever their texts would like.
        side.setMinWidth(0);
        side.setPrefWidth(0);
        HBox.setHgrow(side, Priority.ALWAYS);
        // Before the editor sees them: whatever scrolls it - the wheel, its scroll bars, the keys -
        // starts here.
        side.addEventFilter(MouseEvent.MOUSE_ENTERED, event -> leftLeads = isLeft);
        side.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> leftLeads = isLeft);
        side.addEventFilter(ScrollEvent.ANY, event -> leftLeads = isLeft);
        side.addEventFilter(KeyEvent.KEY_PRESSED, event -> leftLeads = isLeft);
        return side;
    }

    private static Label heading(String title) {
        Label heading = new Label(title);
        heading.getStyleClass().add("diff-heading");
        heading.setMaxWidth(Double.MAX_VALUE);
        heading.setAlignment(Pos.CENTER);
        // File names and revisions are data: an underscore in one must not become a mnemonic.
        heading.setMnemonicParsing(false);
        return heading;
    }

    private static ToggleButton tab(String key) {
        ToggleButton tab = new ToggleButton(message(key));
        tab.getStyleClass().add("diff-view-tab");
        tab.setFocusTraversable(false);
        return tab;
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
        return NbBundle.getMessage(DiffPane.class, key);
    }
}
