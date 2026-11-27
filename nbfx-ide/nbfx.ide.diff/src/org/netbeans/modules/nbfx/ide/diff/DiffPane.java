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
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Subscription;
import org.netbeans.api.nbfx.diff.Diff;
import org.netbeans.api.nbfx.diff.DiffOptions;
import org.netbeans.api.nbfx.diff.DiffResult;
import org.netbeans.api.nbfx.diff.Difference;
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
 * side lacks the lines of the other - and the left one kept in step with the right one as that is
 * scrolled, so that the same lines stay beside each other. Between the editors a strip joins each
 * block of one side with its block of the other ({@link DiffConnectors}) under a counter of the
 * blocks; at the right edge another marks them all along the text ({@link DiffOverview}); two
 * buttons above go from one block to the next.
 * <p>
 * The texts are read and compared off the FX thread; until then, and when they are the same, the
 * line between the headings says so.
 */
final class DiffPane extends BorderPane {

    private static final Logger LOG = Logger.getLogger(DiffPane.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("nbfx-diff", 1);
    /** The lines kept in view above the block gone to. */
    private static final int CONTEXT_LINES = 3;

    private DiffSource leftSource;
    private DiffSource rightSource;
    private final Label summary = new Label();
    private final Label counter = heading("");
    private final Button previous = button(ToolIcons.PREVIOUS_MATCH, "Diff.previous");
    private final Button next = button(ToolIcons.NEXT_MATCH, "Diff.next");
    private final DiffConnectors connectors = new DiffConnectors();
    private final DiffOverview overview = new DiffOverview();
    private int current = -1;
    private EditorDocument left;
    private EditorDocument right;
    private List<Difference> differences = List.of();
    private Subscription scrolling = Subscription.EMPTY;
    /** The editor the keyboard is in, or was in last; the right one until the left one is gone into. */
    private final ReadOnlyObjectWrapper<EditorDocument> activeEditor = new ReadOnlyObjectWrapper<>(this, "activeEditor");
    /**
     * Counts the comparisons asked for: the one that runs gives up as soon as another is asked
     * for, or the pane is disposed of, and what it found is not shown.
     */
    private final AtomicLong comparison = new AtomicLong();

    DiffPane(DiffSource leftSource, DiffSource rightSource) {
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
        setTop(bar);
        overview.setOnSelect(this::select);
        select(-1);
        setSources(leftSource, rightSource);
    }

    /**
     * Shows {@code left} and {@code right} in place of the two sides shown, and compares them: what
     * a diff asked for again is of by now - another commit, the file staged since - is not what it
     * was of when its tab was opened. Must run on the JavaFX Application Thread.
     */
    void setSources(DiffSource left, DiffSource right) {
        // Sides that show the same keep their editors, and where they are scrolled to.
        boolean same = left.equals(leftSource) && right.equals(rightSource);
        leftSource = Objects.requireNonNull(left);
        rightSource = Objects.requireNonNull(right);
        if (!same) {
            showEditors();
        }
        refresh();
    }

    /**
     * Puts the two sides side by side, in place of those there were: an editor of the text of a
     * side that has one - of no text for a side that is missing - and what there is to say of a
     * side that cannot be compared. The new sides are made first and put in place together; if
     * one cannot be made, what was made of the other is released, and the pane says so.
     */
    private void showEditors() {
        EditorService editors = Lookup.getDefault().lookup(EditorService.class);
        EditorDocument newLeft = null;
        EditorDocument newRight = null;
        String failure = null;
        try {
            newLeft = editor(editors, leftSource);
            newRight = editor(editors, rightSource);
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.INFO, "Cannot show " + leftSource.title() + " and " + rightSource.title(), ex);
            if (newLeft != null) {
                newLeft.dispose();
            }
            newLeft = null;
            failure = message("Diff.failed");
        }
        scrolling.unsubscribe();
        scrolling = Subscription.EMPTY;
        disposeEditors();
        differences = List.of();
        left = newLeft;
        right = newRight;
        activeEditor.set(right != null ? right : left);
        for (EditorDocument editor : new EditorDocument[] {left, right}) {
            if (editor != null) {
                // A diff shows two texts, not where the caret is in one: no breadcrumbs under them.
                editor.showBreadcrumbsProperty().set(false);
            }
        }
        if (failure != null) {
            setCenter(notice(failure));
            return;
        }
        if (left != null) {
            // The line numbers of the left text next to those of the right one, as in NetBeans.
            left.gutterPositionProperty().set(HPos.RIGHT);
        }
        // The connectors are of the editors that were: none until these are compared.
        connectors.show(left, right, List.of());
        VBox.setVgrow(connectors, Priority.ALWAYS);
        VBox strip = new VBox(counter, connectors);
        strip.getStyleClass().add("diff-strip");
        VBox.setVgrow(overview, Priority.ALWAYS);
        VBox edge = new VBox(heading(""), overview);
        edge.getStyleClass().add("diff-edge");
        HBox sides = new HBox(side(leftSource, left, true), strip, side(rightSource, right, false), edge);
        sides.getStyleClass().add("diff-sides");
        setCenter(sides);
        if (left != null && right != null) {
            // The right text leads, and the left one follows it; scrolled by itself, the left
            // one moves alone, as in NetBeans - until the right one is scrolled again.
            scrolling = right.topParagraphProperty().subscribe(top -> follow(top.doubleValue()));
        }
    }

    /**
     * An editor that shows the text of {@code source}, or {@code null} when it has none to show -
     * it cannot be compared - or there is no editor to be had. It shows a copy of the text in
     * memory, named as the file so that it is coloured as one: what is shown is what is compared,
     * whatever becomes of the file meanwhile.
     */
    private static EditorDocument editor(EditorService editors, DiffSource source) throws IOException {
        if (editors == null || !source.isComparable()) {
            return null;
        }
        return editors.createPreviewDocument(inMemory(source.fileName(), source.text() == null ? "" : source.text()));
    }

    /** A file in memory named {@code name} that holds {@code text}. */
    static FileObject inMemory(String name, String text) throws IOException {
        FileObject file = FileUtil.createMemoryFileSystem().getRoot().createData(name);
        try (OutputStream out = file.getOutputStream()) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    /** The editor the keyboard is in, or was in last: the one Copy is of. {@code null} with no editor. */
    ObservableValue<EditorDocument> activeEditorProperty() {
        return activeEditor.getReadOnlyProperty();
    }

    private void disposeEditors() {
        if (left != null) {
            left.dispose();
        }
        if (right != null) {
            right.dispose();
        }
        left = null;
        right = null;
        activeEditor.set(null);
    }

    /**
     * Gives the keyboard to the text compared to, the one on the right - the file as it is now -
     * so that the keys scroll the diff as soon as its tab is chosen.
     */
    void focusEditor() {
        if (right != null) {
            right.requestFocus();
        } else {
            requestFocus();
        }
    }

    /** The differences shown; empty until the texts are compared, and when they are the same. */
    List<Difference> differences() {
        return differences;
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

    /** The connectors drawn between the two texts: one for each block in view. */
    List<Node> connectors() {
        return connectors.connectors();
    }

    /** The text of the line above the headings. */
    String summary() {
        return summary.getText();
    }

    /**
     * Reads the two texts again, compares them and marks the blocks. A comparison still running is
     * given up: all the panes compare on one thread, and what nobody waits for must not hold up
     * what somebody does.
     */
    void refresh() {
        long asked = comparison.incrementAndGet();
        DiffSource from = leftSource;
        DiffSource to = rightSource;
        if (!from.isComparable() || !to.isComparable()) {
            // Nothing to compare: said, with the side it is of, and not shown as no difference.
            DiffSource unreadable = from.isComparable() ? to : from;
            show(List.of(), 1, NbBundle.getMessage(DiffPane.class, "Diff.unreadable", unreadable.title(),
                    unreadable.problem()));
            return;
        }
        summary.setText(message("Diff.loading"));
        RP.post(() -> {
            DiffResult result;
            int rightLines;
            try {
                if (asked != comparison.get()) {
                    return;
                }
                List<String> right = to.lines();
                // A text that is not there has no line, and is marked along its whole edge as one.
                rightLines = Math.max(1, right.size());
                result = Diff.compute(from.lines(), right, DiffOptions.EXACT, () -> asked != comparison.get());
            } catch (CancellationException ex) {
                return;
            } catch (RuntimeException ex) {
                LOG.log(Level.INFO, "Cannot compare " + from.title() + " and " + to.title(), ex);
                Platform.runLater(() -> {
                    if (asked == comparison.get()) {
                        show(List.of(), 1, message("Diff.failed"));
                    }
                });
                return;
            }
            Platform.runLater(() -> {
                if (asked == comparison.get()) {
                    show(result.differences(), rightLines, told(from, to, result.differences()));
                }
            });
        });
    }

    /** What the line between the headings says of a comparison: what happened to the file, or how much differs. */
    private static String told(DiffSource from, DiffSource to, List<Difference> found) {
        if (from.isMissing() && to.isMissing()) {
            return message("Diff.identical");
        }
        if (from.isMissing()) {
            return message("Diff.added");
        }
        if (to.isMissing()) {
            return message("Diff.deleted");
        }
        return found.isEmpty() ? message("Diff.identical")
                : NbBundle.getMessage(DiffPane.class, "Diff.differences", found.size());
    }

    /** Releases the two editors, and gives up the comparison that runs. */
    void dispose() {
        comparison.incrementAndGet();
        scrolling.unsubscribe();
        disposeEditors();
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
        if (current >= 0 && left != null && right != null) {
            // The right side leads: it has the lines of the file as it is now. The left one is
            // brought beside it here too, for a right one that is already where it is asked to go.
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

    private void show(List<Difference> found, int rightLines, String text) {
        differences = found;
        summary.setText(text);
        // Said, not only shown: how the comparison ended, and on each side which lines differ and
        // how - the colour of a band tells nobody who does not see it.
        summary.setAccessibleText(text);
        summary.notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
        describe(left, leftSource, DiffLines.describeLeft(found));
        describe(right, rightSource, DiffLines.describeRight(found));
        if (left != null && right != null) {
            left.setLineBands(DiffLines.leftBands(found));
            right.setLineBands(DiffLines.rightBands(found));
            // After the bands: the connectors join the regions the editors have just made for them.
            connectors.show(left, right, found);
        }
        overview.show(found, rightLines);
        // At the first block, as when the diff opens; without moving the view when there is none.
        select(found.isEmpty() ? -1 : Math.clamp(current, 0, found.size() - 1));
    }

    /**
     * Brings beside the place at the top of the right editor the place of the left one that
     * stands by it - to a fraction of a line, so that the two move together and not one a line at
     * a time. Only this way: the left one, which has no scroll bar of its own, is the one that is
     * kept in step, and scrolling it does not move the right one.
     */
    private void follow(double top) {
        if (left != null && right != null) {
            left.scrollToParagraph(DiffLines.toLeft(differences, top));
        }
    }

    private VBox side(DiffSource source, EditorDocument document, boolean isLeft) {
        Label heading = heading(source.title());
        // No text to show: what there is to say of this side, in its place.
        Node editor = document != null ? document.getNode() : notice(source.isComparable() ? "" : source.problem());
        VBox.setVgrow(editor, Priority.ALWAYS);
        VBox side = new VBox(heading, editor);
        side.getStyleClass().addAll("diff-side", isLeft ? "diff-side-left" : "diff-side-right");
        // The two sides share the width equally, whatever their texts would like.
        side.setMinWidth(0);
        side.setPrefWidth(0);
        HBox.setHgrow(side, Priority.ALWAYS);
        if (document != null) {
            side.focusWithinProperty().subscribe(focused -> {
                if (focused && (document == left || document == right)) {
                    activeEditor.set(document);
                }
            });
        }
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

    private static Button button(String icon, String key) {
        Button button = new Button();
        button.setGraphic(ToolIcons.view(icon));
        button.setTooltip(new Tooltip(message(key)));
        button.setAccessibleText(message(key));
        button.getStyleClass().add("diff-tool-button");
        button.setFocusTraversable(false);
        return button;
    }

    /** Has assistive technology say of the editor of {@code source} which of its lines differ. */
    private static void describe(EditorDocument editor, DiffSource source, List<String> blocks) {
        if (editor != null) {
            editor.getNode().setAccessibleText(source.title());
            editor.getNode().setAccessibleHelp(blocks.isEmpty() ? message("Diff.identical") : String.join("; ", blocks));
        }
    }

    /** What is said where a text would be shown. */
    private static Label notice(String text) {
        Label notice = new Label(text);
        notice.getStyleClass().add("diff-notice");
        notice.setMnemonicParsing(false);
        notice.setWrapText(true);
        notice.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        notice.setAlignment(Pos.CENTER);
        return notice;
    }

    private static String message(String key) {
        return NbBundle.getMessage(DiffPane.class, key);
    }
}
