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

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.netbeans.api.nbfx.diff.Difference;
import org.openide.util.NbBundle;

/**
 * The narrow strip at the right edge of a diff: a mark for each block that differs, placed by where
 * its lines are in the whole of the right text, so that all the blocks are seen at once however
 * long the text is. Clicking a mark goes to its block, as does choosing it with the keyboard.
 */
final class DiffOverview extends Pane {

    /** High enough to be seen, and hit: a block of one line in a long text would be a hairline. */
    private static final double MIN_MARK_HEIGHT = 4;

    private List<Difference> differences = List.of();
    private int lineCount = 1;
    private int current = -1;
    private IntConsumer onSelect = index -> { };

    DiffOverview() {
        getStyleClass().add("diff-overview");
    }

    /**
     * Marks the blocks of {@code found} in a right text of {@code rightLineCount} lines. The marks
     * are made here, once for a comparison: they are placed as the strip is laid out, and told
     * which is the current one, without being made again.
     */
    void show(List<Difference> found, int rightLineCount) {
        differences = found;
        lineCount = Math.max(1, rightLineCount);
        List<String> told = DiffLines.describeRight(found);
        List<Region> marks = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) {
            int index = i;
            Region mark = new Region();
            mark.getStyleClass().addAll("diff-mark", switch (found.get(i).kind()) {
                case ADD -> DiffLines.ADDED;
                case DELETE -> DiffLines.DELETED;
                case CHANGE -> DiffLines.CHANGED;
            });
            mark.setManaged(false);
            mark.setCursor(Cursor.HAND);
            mark.setOnMouseClicked(event -> onSelect.accept(index));
            // And for who does not use the mouse, or does not see the mark: reached with Tab,
            // chosen with Enter or Space, and named by what it goes to.
            String name = NbBundle.getMessage(DiffOverview.class, "Diff.mark", i + 1, found.size(), told.get(i));
            mark.setFocusTraversable(true);
            mark.setAccessibleRole(AccessibleRole.BUTTON);
            mark.setAccessibleText(name);
            Tooltip.install(mark, new Tooltip(name));
            mark.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                    onSelect.accept(index);
                    event.consume();
                }
            });
            marks.add(mark);
        }
        getChildren().setAll(marks);
        mark();
        requestLayout();
    }

    void setCurrent(int index) {
        current = index;
        mark();
    }

    /** What a click on the mark of a block does with the index of that block. */
    void setOnSelect(IntConsumer action) {
        onSelect = action;
    }

    /** The marks, in the order of the blocks. */
    List<Region> marks() {
        return getChildren().stream().map(Region.class::cast).toList();
    }

    private void mark() {
        List<Region> marks = marks();
        for (int i = 0; i < marks.size(); i++) {
            List<String> classes = marks.get(i).getStyleClass();
            classes.remove("diff-current");
            if (i == current) {
                classes.add("diff-current");
            }
        }
    }

    /** Places each mark by where its lines are in the whole of the right text, over the height there is. */
    @Override
    protected void layoutChildren() {
        List<Region> marks = marks();
        double height = getHeight();
        for (int i = 0; i < marks.size() && i < differences.size(); i++) {
            Difference difference = differences.get(i);
            double y = height * DiffLines.overviewStart(difference, lineCount);
            double markHeight = Math.max(MIN_MARK_HEIGHT, height * DiffLines.overviewExtent(difference, lineCount));
            marks.get(i).resizeRelocate(1, Math.min(y, Math.max(0, height - markHeight)), Math.max(0, getWidth() - 2),
                    markHeight);
        }
    }
}
