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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Subscription;
import org.netbeans.api.nbfx.diff.Diff;
import org.netbeans.api.nbfx.diff.DiffOptions;
import org.netbeans.api.nbfx.diff.DiffResult;
import org.netbeans.api.nbfx.diff.Difference;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.EditorService;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Two texts side by side, as NetBeans' graphical diff: each in a read-only editor under its
 * heading, the blocks that differ marked - lines added, deleted and changed, and a rule where one
 * side lacks the lines of the other - and the two scrolled in step, so that the same lines stay
 * beside each other.
 * <p>
 * The texts are read and compared off the FX thread; until then, and when they are the same, the
 * line between the headings says so.
 */
final class DiffPane extends BorderPane {

    private static final Logger LOG = Logger.getLogger(DiffPane.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("nbfx-diff", 1);

    private DiffSource leftSource;
    private DiffSource rightSource;
    private final Label summary = new Label();
    private EditorDocument left;
    private EditorDocument right;
    private List<Difference> differences = List.of();
    private Subscription scrolling = Subscription.EMPTY;
    /**
     * Whether the left editor is the one the user scrolls - the one last under the mouse, pressed
     * or typed in - and the right one follows it; else the other way round. Only the leading
     * editor is followed: the follower cannot always get to the line asked for - its text may be
     * the shorter - and following it back would move the leader, which would be followed again,
     * without end.
     */
    private boolean leftLeads;
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
        setTop(summary);
        setSources(leftSource, rightSource);
    }

    /**
     * Shows {@code left} and {@code right} in place of the two texts shown, and compares them: what
     * a diff asked for again is of by now - another commit, the file staged since - is not what it
     * was of when its tab was opened. Must run on the JavaFX Application Thread.
     */
    void setSources(DiffSource left, DiffSource right) {
        // The same files under the same headings keep their editors, and where they are scrolled to.
        boolean same = left.equals(leftSource) && right.equals(rightSource);
        leftSource = Objects.requireNonNull(left);
        rightSource = Objects.requireNonNull(right);
        if (!same) {
            showEditors();
        }
        refresh();
    }

    /** Puts an editor of each of the two files side by side, in place of those there were. */
    private void showEditors() {
        scrolling.unsubscribe();
        scrolling = Subscription.EMPTY;
        disposeEditors();
        differences = List.of();
        EditorService editors = Lookup.getDefault().lookup(EditorService.class);
        if (editors == null) {
            return;
        }
        left = editors.createPreviewDocument(leftSource.file());
        right = editors.createPreviewDocument(rightSource.file());
        // The line numbers of the left text next to those of the right one, as in NetBeans.
        left.gutterPositionProperty().set(HPos.RIGHT);
        SplitPane sides = new SplitPane(side(leftSource.title(), left.getNode(), true),
                side(rightSource.title(), right.getNode(), false));
        sides.getStyleClass().add("diff-sides");
        setCenter(sides);
        scrolling = left.topParagraphProperty().subscribe(top -> follow(top.intValue(), true))
                .and(right.topParagraphProperty().subscribe(top -> follow(top.intValue(), false)));
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

    /** The text of the line between the headings. */
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
        summary.setText(message("Diff.loading"));
        RP.post(() -> {
            DiffResult result;
            try {
                if (asked != comparison.get()) {
                    return;
                }
                result = Diff.compute(Diff.lines(from.file().asText("UTF-8")), Diff.lines(to.file().asText("UTF-8")),
                        DiffOptions.EXACT, () -> asked != comparison.get());
            } catch (CancellationException ex) {
                return;
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.INFO, "Cannot compare " + from.file() + " and " + to.file(), ex);
                Platform.runLater(() -> {
                    if (asked == comparison.get()) {
                        show(List.of(), message("Diff.failed"));
                    }
                });
                return;
            }
            Platform.runLater(() -> {
                if (asked == comparison.get()) {
                    List<Difference> found = result.differences();
                    show(found, found.isEmpty() ? message("Diff.identical")
                            : NbBundle.getMessage(DiffPane.class, "Diff.differences", found.size()));
                }
            });
        });
    }

    /** Releases the two editors, and gives up the comparison that runs. */
    void dispose() {
        comparison.incrementAndGet();
        scrolling.unsubscribe();
        disposeEditors();
    }

    private void show(List<Difference> found, String text) {
        differences = found;
        summary.setText(text);
        if (left != null && right != null) {
            left.setLineBands(DiffLines.leftBands(found));
            right.setLineBands(DiffLines.rightBands(found));
        }
    }

    /** Brings beside the top line of the leading editor the line of the other that stands by it. */
    private void follow(int top, boolean leftScrolled) {
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
        Label heading = new Label(title);
        heading.getStyleClass().add("diff-heading");
        heading.setMaxWidth(Double.MAX_VALUE);
        heading.setAlignment(Pos.CENTER);
        // File names and revisions are data: an underscore in one must not become a mnemonic.
        heading.setMnemonicParsing(false);
        VBox.setVgrow(editor, Priority.ALWAYS);
        VBox side = new VBox(heading, editor);
        side.getStyleClass().add("diff-side");
        side.setMinWidth(0);
        // Before the editor sees them: whatever scrolls it - the wheel, its scroll bars, the keys -
        // starts here.
        side.addEventFilter(MouseEvent.MOUSE_ENTERED, event -> leftLeads = isLeft);
        side.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> leftLeads = isLeft);
        side.addEventFilter(ScrollEvent.ANY, event -> leftLeads = isLeft);
        side.addEventFilter(KeyEvent.KEY_PRESSED, event -> leftLeads = isLeft);
        // And the side the keyboard is given to - by the keys, or as the tab is chosen - leads
        // before a key scrolls it.
        side.focusWithinProperty().subscribe(focused -> {
            if (focused) {
                leftLeads = isLeft;
            }
        });
        return side;
    }

    private static String message(String key) {
        return NbBundle.getMessage(DiffPane.class, key);
    }
}
