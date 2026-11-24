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
import javafx.scene.Cursor;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.netbeans.api.nbfx.diff.Difference;

/**
 * The narrow strip at the right edge of a diff: a mark for each block that differs, placed by where
 * its lines are in the whole of the right text, so that all the blocks are seen at once however
 * long the text is. Clicking a mark goes to its block.
 */
final class DiffOverview extends Pane {

    private static final double MIN_MARK_HEIGHT = 3;

    private List<Difference> differences = List.of();
    private int lineCount = 1;
    private int current = -1;
    private IntConsumer onSelect = index -> { };

    DiffOverview() {
        getStyleClass().add("diff-overview");
        heightProperty().addListener(observable -> rebuild());
    }

    /** Marks the blocks of {@code found} in a right text of {@code rightLineCount} lines. */
    void show(List<Difference> found, int rightLineCount) {
        differences = found;
        lineCount = Math.max(1, rightLineCount);
        rebuild();
    }

    void setCurrent(int index) {
        current = index;
        rebuild();
    }

    /** What a click on the mark of a block does with the index of that block. */
    void setOnSelect(IntConsumer action) {
        onSelect = action;
    }

    /** The marks, in the order of the blocks. */
    List<Region> marks() {
        return getChildren().stream().map(Region.class::cast).toList();
    }

    private void rebuild() {
        List<Region> marks = new ArrayList<>(differences.size());
        double height = getHeight();
        for (int i = 0; i < differences.size(); i++) {
            Difference difference = differences.get(i);
            int index = i;
            Region mark = new Region();
            mark.getStyleClass().addAll("diff-mark", switch (difference.kind()) {
                case ADD -> DiffLines.ADDED;
                case DELETE -> DiffLines.DELETED;
                case CHANGE -> DiffLines.CHANGED;
            });
            if (i == current) {
                mark.getStyleClass().add("diff-current");
            }
            mark.setManaged(false);
            mark.setCursor(Cursor.HAND);
            mark.setOnMouseClicked(event -> onSelect.accept(index));
            double y = height * DiffLines.overviewStart(difference, lineCount);
            double markHeight = Math.max(MIN_MARK_HEIGHT, height * DiffLines.overviewExtent(difference, lineCount));
            mark.resizeRelocate(1, Math.min(y, Math.max(0, height - markHeight)), Math.max(0, getWidth() - 2), markHeight);
            marks.add(mark);
        }
        getChildren().setAll(marks);
    }

    @Override
    protected void layoutChildren() {
        // The marks are placed by rebuild(); only their width follows the strip.
        for (Region mark : marks()) {
            mark.resize(Math.max(0, getWidth() - 2), mark.getHeight());
        }
    }
}
