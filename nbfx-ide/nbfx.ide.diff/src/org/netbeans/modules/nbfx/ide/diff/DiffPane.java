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
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Subscription;
import org.netbeans.api.nbfx.diff.Diff;
import org.netbeans.api.nbfx.diff.DiffOptions;
import org.netbeans.api.nbfx.diff.DiffResult;
import org.netbeans.api.nbfx.diff.Difference;
import org.netbeans.api.nbfx.diff.UnifiedDiff;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.EditorService;
import org.netbeans.api.nbfx.editor.LineBand;
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
 * Two tabs above the headings choose how the differences are shown: Graphical, as said, or
 * Textual, as a unified diff in one read-only editor, its lines marked as added and deleted.
 * <p>
 * The texts are read and compared off the FX thread; until then, and when they are the same, the
 * line between the headings says so.
 */
final class DiffPane extends BorderPane implements DiffContent {

    private static final Logger LOG = Logger.getLogger(DiffPane.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("nbfx-diff", 1);
    /** Next and Previous Difference, as in NetBeans: Ctrl+Period and Ctrl+Comma, on every platform. */
    static final KeyCombination NEXT_KEY = new KeyCodeCombination(KeyCode.PERIOD, KeyCombination.CONTROL_DOWN);
    static final KeyCombination PREVIOUS_KEY = new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN);
    /** The lines kept in view above the block gone to. */
    private static final int CONTEXT_LINES = 3;

    /** The most lines that differ that are written as a unified diff; more are said to be too many. */
    static final long MOST_TEXTUAL_LINES = 100_000;
    /** No unified diff: nothing compared yet, or nothing that can be. */
    private static final UnifiedDiff NO_UNIFIED = new UnifiedDiff("", List.of());

    private DiffSource leftSource;
    /** What differs between the two sides that their texts do not show; {@code null} for nothing. */
    private String note;
    private DiffSource rightSource;
    private final Label summary = new Label();
    private final Label counter = heading("");
    private final Button previous = button(ToolIcons.PREVIOUS_MATCH, "Diff.previous", PREVIOUS_KEY);
    private final Button next = button(ToolIcons.NEXT_MATCH, "Diff.next", NEXT_KEY);
    private final DiffConnectors connectors = new DiffConnectors();
    private final DiffOverview overview = new DiffOverview();
    /** What holds the connectors, and what holds the overview: there while there are blocks to show. */
    private final VBox strip = new VBox();
    private final VBox edge = new VBox();
    private int current = -1;
    /**
     * The line of the right text at which the block the view is at starts, or -1 for none: kept
     * while the texts are compared again, to be at the block that is there afterwards.
     */
    private int currentLine = -1;
    /** Whether a comparison was asked for and its blocks are not shown yet. */
    private boolean comparing;
    /** Whether what is shown is how the two sides compare: not while they are compared, nor after that failed. */
    private boolean compared;
    private final ToggleButton graphicalTab = tab("Diff.graphical");
    private final ToggleButton textualTab = tab("Diff.textual");
    /** The two texts side by side, or the unified diff: what the tab chosen shows. */
    private final StackPane content = new StackPane();
    /** The two sides, or what is said in their place when they cannot be shown. */
    private Node sides;
    private EditorDocument left;
    private EditorDocument right;
    /**
     * The differences as a unified diff, which the Textual tab shows: written when that tab is
     * first shown after a comparison, not with each comparison - until then, none.
     */
    private UnifiedDiff unified = NO_UNIFIED;
    /** Whether {@link #unified} is of the differences shown, and whether it is being written. */
    private boolean unifiedWritten;
    private boolean unifiedWriting;
    /** Why there is no unified diff to show though the texts differ, for the user; {@code null} when there is. */
    private String unifiedProblem;
    /** The lines and the bands of the unified diff in its editor, by which the current block is marked. */
    private List<String> textualLines = List.of();
    private List<LineBand> textualBands = List.of();
    /** What the Textual tab says while it has no text to show. */
    private final Label textualNote = notice("");
    /** The editor of the unified diff, made when the Textual tab is first shown and after each comparison. */
    private EditorDocument textual;
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
        this(leftSource, rightSource, true);
    }

    /**
     * @param ownBar whether the buttons and the summary are shown above the headings; a
     *        {@link DiffBrowser} shows them in its own bar instead
     */
    DiffPane(DiffSource leftSource, DiffSource rightSource, boolean ownBar) {
        this(leftSource, rightSource, null, ownBar);
    }

    /**
     * @param note what differs between the two sides that their texts do not show, or {@code null}
     * @param ownBar whether the buttons and the summary are shown above the headings
     */
    DiffPane(DiffSource leftSource, DiffSource rightSource, String note, boolean ownBar) {
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
                place();
                // At the block the other tab was at.
                if (current >= 0) {
                    select(current);
                }
            }
        });
        HBox tabStrip = new HBox(graphicalTab, textualTab);
        tabStrip.getStyleClass().add("diff-view-tabs");
        setTop(ownBar ? new VBox(bar, tabStrip) : tabStrip);
        setCenter(content);
        strip.getStyleClass().add("diff-strip");
        edge.getStyleClass().add("diff-edge");
        overview.setOnSelect(this::select);
        // For who does not use the mouse: the keys NetBeans has for the next and the previous
        // difference, wherever in the diff the keyboard is.
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (NEXT_KEY.match(event)) {
                go(true);
                event.consume();
            } else if (PREVIOUS_KEY.match(event)) {
                go(false);
                event.consume();
            }
        });
        select(-1);
        setSources(leftSource, rightSource, note);
    }

    /**
     * Shows {@code left} and {@code right} in place of the two sides shown, and compares them: what
     * a diff asked for again is of by now - another commit, the file staged since - is not what it
     * was of when its tab was opened. Must run on the JavaFX Application Thread.
     */
    void setSources(DiffSource left, DiffSource right) {
        setSources(left, right, null);
    }

    /** As {@link #setSources(DiffSource, DiffSource)}, with what else differs between the two, or {@code null}. */
    void setSources(DiffSource left, DiffSource right, String note) {
        // Sides that show the same keep their editors, and where they are scrolled to.
        boolean same = left.equals(leftSource) && right.equals(rightSource);
        if (same && Objects.equals(note, this.note) && compared) {
            // Asked for again, and nothing is other than it was: what is shown is what there is
            // to show - the blocks, the block the view is at, the unified diff - and stays.
            return;
        }
        this.note = note;
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
        // The unified diff was of the texts that were: none until these are compared.
        forgetUnified();
        if (failure != null) {
            sides = notice(failure);
            place();
            return;
        }
        if (left != null) {
            // The line numbers of the left text next to those of the right one, as in NetBeans.
            left.gutterPositionProperty().set(HPos.RIGHT);
        }
        // The connectors are of the editors that were: none until these are compared.
        connectors.show(left, right, List.of());
        VBox.setVgrow(connectors, Priority.ALWAYS);
        strip.getChildren().setAll(counter, connectors);
        VBox.setVgrow(overview, Priority.ALWAYS);
        edge.getChildren().setAll(heading(""), overview);
        HBox both = new HBox(side(leftSource, left, true), strip, side(rightSource, right, false), edge);
        both.getStyleClass().add("diff-sides");
        sides = both;
        place();
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
    @Override
    public ObservableValue<EditorDocument> activeEditorProperty() {
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
    @Override
    public void focusEditor() {
        if (isTextual() && textual != null) {
            textual.requestFocus();
        } else if (right != null) {
            right.requestFocus();
        } else {
            requestFocus();
        }
    }

    /**
     * The differences as a unified diff: what the Textual tab shows. Empty until compared, and
     * when the same. Written here and now if it was not yet; for tests.
     */
    String unified() {
        if (!unifiedWritten && !comparing) {
            try {
                unified = write(leftSource, rightSource, differences);
            } catch (IllegalStateException tooLarge) {
                unified = NO_UNIFIED;
            }
            unifiedWritten = true;
        }
        return unified.text();
    }

    /** Whether the unified diff is being written for the Textual tab; for tests. */
    boolean isWritingUnified() {
        return unifiedWriting;
    }

    /** What the Textual tab says in place of a text, or {@code null} while it shows one; for tests. */
    String textualNote() {
        return content.getChildren().contains(textualNote) ? textualNote.getText() : null;
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

    /** The editor of the unified diff, or {@code null} while the Textual tab was not shown; for tests. */
    EditorDocument textualEditor() {
        return textual;
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

    /** Whether the connectors still listen to the editors or to the frames of the window; for tests. */
    boolean connectorsListening() {
        return connectors.isListening();
    }

    /** Whether the strip of the connectors and that of the overview are shown: there are blocks. */
    boolean stripsShown() {
        return strip.isVisible() && strip.isManaged() && edge.isVisible() && edge.isManaged();
    }

    /** The connectors drawn between the two texts: one for each block in view. */
    List<Node> connectors() {
        return connectors.connectors();
    }

    ReadOnlyStringProperty summaryProperty() {
        return summary.textProperty();
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
    @Override
    public void refresh() {
        long asked = comparison.incrementAndGet();
        compared = false;
        DiffSource from = leftSource;
        DiffSource to = rightSource;
        String noted = note;
        if (!from.isComparable() || !to.isComparable()) {
            // Nothing to compare: said, with the side it is of, and not shown as no difference.
            DiffSource unreadable = from.isComparable() ? to : from;
            show(List.of(), 1, NbBundle.getMessage(DiffPane.class, "Diff.unreadable", unreadable.title(),
                    unreadable.problem()));
            compared = true;
            return;
        }
        summary.setText(message("Diff.loading"));
        comparing = true;
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
                    show(result.differences(), rightLines, told(from, to, result.differences(), noted));
                    compared = true;
                }
            });
        });
    }

    /** What the line between the headings says of a comparison: what happened to the file, or how much differs. */
    private static String told(DiffSource from, DiffSource to, List<Difference> found, String note) {
        String texts = toldOfTexts(from, to, found);
        // What the texts do not show - the mode of the file - is said with them, or in their
        // place when they are the same: a file that changed is never just "No differences".
        if (note == null) {
            return texts;
        }
        return found.isEmpty() && !from.isMissing() && !to.isMissing() && from.text().equals(to.text()) ? note
                : NbBundle.getMessage(DiffPane.class, "Diff.withNote", texts, note);
    }

    private static String toldOfTexts(DiffSource from, DiffSource to, List<Difference> found) {
        if (from.isMissing() && to.isMissing()) {
            return message("Diff.identical");
        }
        if (from.isMissing()) {
            return message("Diff.added");
        }
        if (to.isMissing()) {
            return message("Diff.deleted");
        }
        if (!found.isEmpty()) {
            return NbBundle.getMessage(DiffPane.class, "Diff.differences", found.size());
        }
        // The same lines, and yet not the same text: only how the lines end can differ, which
        // the comparison of lines leaves out and the file has all the same.
        return from.text().equals(to.text()) ? message("Diff.identical")
                : NbBundle.getMessage(DiffPane.class, "Diff.lineEndings", from.title(),
                        DiffLines.lineEnding(from.text()), to.title(), DiffLines.lineEnding(to.text()));
    }

    /** Releases the two editors, and gives up the comparison that runs. */
    @Override
    public void dispose() {
        connectors.dispose();
        comparison.incrementAndGet();
        scrolling.unsubscribe();
        dropTextual();
        disposeEditors();
    }

    /**
     * Makes the block of that index the current one - counted, drawn as such - and, when there are
     * editors, brings it into view with a few lines above it.
     */
    void select(int index) {
        current = index < 0 || index >= differences.size() ? -1 : index;
        if (current >= 0) {
            currentLine = differences.get(current).rightStart();
        } else if (!comparing) {
            // No block to be at; while a comparison is awaited, the place is kept for its blocks.
            currentLine = -1;
        }
        counter.setText(current < 0 ? ""
                : NbBundle.getMessage(DiffPane.class, "Diff.counter", current + 1, differences.size()));
        previous.setDisable(current <= 0);
        next.setDisable(current < 0 || current >= differences.size() - 1);
        connectors.setCurrent(current);
        overview.setCurrent(current);
        markTextual();
        if (current >= 0 && isTextual()) {
            if (textual != null && current < unified.differenceLines().size()) {
                textual.scrollToParagraph(Math.max(0, unified.differenceLines().get(current) - CONTEXT_LINES));
            }
        } else if (current >= 0 && left != null && right != null) {
            // The right side leads: it has the lines of the file as it is now. The left one goes
            // beside where the right one landed, not where it was asked to go - at the end of
            // the text, or with a text that is all in view, it does not get there - and is
            // brought there here too, for a right one that was already where it is asked to go.
            right.scrollToParagraph(Math.max(0, differences.get(current).rightStart() - CONTEXT_LINES));
            follow(right.topParagraphProperty().getValue().doubleValue());
        }
    }

    private void go(boolean forward) {
        int target = DiffLines.step(current, differences.size(), forward);
        if (target != current) {
            select(target);
        }
    }

    private void show(List<Difference> found, int rightLines, String text) {
        // Where the block the view was at starts, in the right text: by which it is found again.
        int at = currentLine;
        comparing = false;
        differences = found;
        summary.setText(text);
        // Of the texts as they were compared before: written again, now if its tab is the one shown.
        forgetUnified();
        if (isTextual()) {
            place();
        }
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
        // Nothing to join or to mark without a block: the two texts take the room.
        boolean none = found.isEmpty();
        for (VBox holder : List.of(strip, edge)) {
            holder.setVisible(!none);
            holder.setManaged(!none);
        }
        // At the first block, as when the diff opens - or, compared again, at the block that is
        // where the one the view was at was; without moving the view when there is none.
        select(none ? -1 : at < 0 ? 0 : DiffLines.blockAt(found, at));
    }

    /** Puts in place what the tab chosen is of; the block the view is at is not touched. */
    private void place() {
        if (isTextual()) {
            if (textual == null) {
                // No text to show, or not yet: said in its place, never an empty editor.
                if (comparing || !unifiedWritten) {
                    textualNote.setText(message("Diff.loading"));
                    if (!comparing) {
                        writeUnified();
                    }
                } else if (unifiedProblem != null) {
                    textualNote.setText(unifiedProblem);
                } else if (unified.text().isEmpty()) {
                    // Nothing differs, or nothing can be compared: what the line above says of it.
                    textualNote.setText(summary.getText());
                } else {
                    textual = unifiedEditor();
                    textualNote.setText(message("Diff.textual.failed"));
                }
            }
            content.getChildren().setAll(textual != null ? textual.getNode() : textualNote);
            // Copy is of the text shown.
            activeEditor.set(textual);
        } else {
            if (sides != null) {
                content.getChildren().setAll(sides);
            }
            activeEditor.set(right != null ? right : left);
        }
    }

    /**
     * Writes the unified diff of the differences shown, off the FX thread, and shows it if its
     * tab is still the one chosen; once for a comparison, and not for one that was made again
     * meanwhile.
     */
    private void writeUnified() {
        if (unifiedWriting) {
            return;
        }
        unifiedWriting = true;
        long asked = comparison.get();
        DiffSource from = leftSource;
        DiffSource to = rightSource;
        List<Difference> found = differences;
        RP.post(() -> {
            UnifiedDiff written = NO_UNIFIED;
            String problem = null;
            try {
                written = write(from, to, found);
            } catch (IllegalStateException tooLarge) {
                problem = tooLarge.getMessage();
            } catch (RuntimeException ex) {
                LOG.log(Level.INFO, "Cannot write the unified diff of " + from.title() + " and " + to.title(), ex);
                problem = message("Diff.textual.failed");
            }
            UnifiedDiff text = written;
            String why = problem;
            Platform.runLater(() -> {
                unifiedWriting = false;
                if (asked == comparison.get() && !comparing) {
                    unified = text;
                    unifiedProblem = why;
                    unifiedWritten = true;
                }
                // Of a comparison made again meanwhile: asked for again, if still wanted.
                if (isTextual()) {
                    place();
                    if (current >= 0) {
                        select(current);
                    }
                }
            });
        });
    }

    /**
     * The unified diff of {@code found} between the two sides, named as git names them.
     *
     * @throws IllegalStateException when more lines differ than are shown as text, with what to
     *         tell the user for a message
     */
    private static UnifiedDiff write(DiffSource from, DiffSource to, List<Difference> found) {
        if (!from.isComparable() || !to.isComparable() || found.isEmpty()) {
            return NO_UNIFIED;
        }
        long changed = 0;
        for (Difference difference : found) {
            changed += difference.leftCount() + difference.rightCount();
        }
        if (changed > MOST_TEXTUAL_LINES) {
            throw new IllegalStateException(NbBundle.getMessage(DiffPane.class, "Diff.textual.tooLarge", changed));
        }
        return UnifiedDiff.of(from.unifiedName("a/"), to.unifiedName("b/"), from.lines(), to.lines(), found,
                UnifiedDiff.DEFAULT_CONTEXT);
    }

    /**
     * A read-only editor of the unified diff, its lines marked as the two sides mark theirs; or
     * {@code null} without an editor service. The text is that of a file in memory named after
     * the file compared.
     */
    private EditorDocument unifiedEditor() {
        EditorService editors = Lookup.getDefault().lookup(EditorService.class);
        if (editors == null) {
            return null;
        }
        try {
            EditorDocument editor = editors.createPreviewDocument(
                    inMemory(rightSource.fileName() + ".diff", unified.text()));
            editor.showBreadcrumbsProperty().set(false);
            textualLines = Diff.lines(unified.text());
            textualBands = DiffLines.unifiedBands(textualLines);
            editor.setLineBands(textualBands);
            editor.getNode().setAccessibleText(message("Diff.textual"));
            return editor;
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.INFO, "Cannot show the unified diff", ex);
            return null;
        }
    }

    /**
     * Marks, in the unified diff, the bands of the block the view is at with the style class the
     * two sides mark theirs with, so that "2/5" is a block one sees there too.
     */
    private void markTextual() {
        if (textual == null || !(textual.getNode().lookup(".line-bands") instanceof Parent overlay)) {
            return;
        }
        int start = current >= 0 && current < unified.differenceLines().size()
                ? unified.differenceLines().get(current) : -1;
        int end = start < 0 ? -1 : start + DiffLines.unifiedBlockLength(textualLines, start);
        List<Node> regions = overlay.getChildrenUnmodifiable().stream().filter(Region.class::isInstance).toList();
        for (int i = 0; i < regions.size() && i < textualBands.size(); i++) {
            int line = textualBands.get(i).paragraph();
            regions.get(i).getStyleClass().remove("diff-current");
            if (line >= start && line < end) {
                regions.get(i).getStyleClass().add("diff-current");
            }
        }
    }

    /** Lets go of the unified diff and of its editor: they were of a comparison that is no longer the one shown. */
    private void forgetUnified() {
        unified = NO_UNIFIED;
        unifiedWritten = false;
        unifiedProblem = null;
        dropTextual();
    }

    private void dropTextual() {
        if (textual != null) {
            if (activeEditor.get() == textual) {
                activeEditor.set(null);
            }
            content.getChildren().remove(textual.getNode());
            textual.dispose();
            textual = null;
            textualLines = List.of();
            textualBands = List.of();
        }
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

    /** A tab that chooses how the differences are shown; reached with the keyboard as the buttons are. */
    private static ToggleButton tab(String key) {
        ToggleButton tab = new ToggleButton(message(key));
        tab.setTooltip(new Tooltip(message(key + ".tip")));
        tab.setAccessibleHelp(message(key + ".tip"));
        tab.getStyleClass().add("diff-view-tab");
        return tab;
    }

    private static Button button(String icon, String key, KeyCombination keys) {
        Button button = new Button();
        button.setGraphic(ToolIcons.view(icon));
        // Named for who does not see it, with the keys that do the same for who does not click.
        button.setTooltip(new Tooltip(NbBundle.getMessage(DiffPane.class, "Diff.tooltip", message(key),
                keys.getDisplayText())));
        button.setAccessibleText(message(key));
        button.getStyleClass().add("diff-tool-button");
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
