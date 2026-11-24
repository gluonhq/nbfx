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
package org.netbeans.modules.nbfx.platform.editor.codearea;

import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Subscription;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;
import org.netbeans.api.nbfx.editor.LineBand;

/**
 * Lays {@link LineBand}s over the text of a {@link CodeArea} - the blocks a diff marks - and tells
 * which line is at the top of the viewport, so that a view can keep several editors in step. The
 * bands are a per-view overlay: they do not touch the text, the caret or the selection, follow
 * scrolling and resizing, and are styled by the view that sets them. Nothing is watched until bands
 * are set or the top line is asked for.
 */
final class LineBandsSupport {

    /** The height of a band of no lines: a rule where lines are missing. */
    private static final double RULE_HEIGHT = 2;

    private final CodeArea codeArea;
    private final Pane overlay = new Pane();
    private final ReadOnlyIntegerWrapper topParagraph = new ReadOnlyIntegerWrapper(this, "topParagraph", 0);
    private final List<LineBand> bands = new ArrayList<>();
    private final List<Region> regions = new ArrayList<>();

    private ScrollBar vertical;
    private Subscription subscription = Subscription.EMPTY;
    private boolean hooked;
    private boolean updateScheduled;

    LineBandsSupport(CodeArea codeArea) {
        this.codeArea = codeArea;
        overlay.setMouseTransparent(true);
        overlay.setPickOnBounds(false);
        overlay.getStyleClass().add("overlay-bands");
    }

    /** The node to stack over the code area. */
    Node overlay() {
        return overlay;
    }

    /** Shows {@code shown} in place of the bands set before. */
    void setBands(List<LineBand> shown) {
        bands.clear();
        bands.addAll(shown);
        regions.clear();
        for (LineBand band : bands) {
            Region region = new Region();
            region.getStyleClass().addAll("line-band", band.styleClass());
            if (band.count() == 0) {
                region.getStyleClass().add("line-band-rule");
            }
            region.setManaged(false);
            region.setVisible(false);
            regions.add(region);
        }
        overlay.getChildren().setAll(regions);
        watch();
        scheduleUpdate();
    }

    /** The line at the top of the viewport, kept up to date from the first time it is asked for. */
    ReadOnlyIntegerProperty topParagraphProperty() {
        watch();
        return topParagraph.getReadOnlyProperty();
    }

    /**
     * Scrolls {@code paragraph} to the top of the viewport. The rich text area maps the value of its
     * scroll bar to a top paragraph only roughly, so the landing is measured and the value nudged by
     * the difference, a few times at most.
     */
    void scrollTo(int paragraph) {
        RestoreViewSupport.whenSkinned(codeArea, () -> {
            hook();
            int count = codeArea.getParagraphCount();
            if (vertical == null || count < 2) {
                return;
            }
            int wanted = Math.clamp(paragraph, 0, count - 1);
            if (measureTop() == wanted) {
                return;
            }
            double range = vertical.getMax() - vertical.getMin();
            double value = vertical.getMin() + range * wanted / (count - 1);
            for (int attempt = 0; attempt < 5; attempt++) {
                double clamped = Math.clamp(value, vertical.getMin(), vertical.getMax());
                vertical.setValue(clamped);
                int actual = measureTop();
                if (actual < 0 || actual == wanted || (attempt > 0 && clamped != value)) {
                    // There, or as far as the text goes.
                    break;
                }
                value += range * (wanted - actual) / (count - 1);
            }
            update();
        });
    }

    void dispose() {
        subscription.unsubscribe();
        subscription = Subscription.EMPTY;
        vertical = null;
        bands.clear();
        regions.clear();
        overlay.getChildren().clear();
    }

    private void watch() {
        RestoreViewSupport.whenSkinned(codeArea, () -> {
            hook();
            scheduleUpdate();
        });
    }

    /** Follows the scroll bar, the size of the area and its font, once the skin exists. */
    private void hook() {
        if (hooked) {
            return;
        }
        hooked = true;
        vertical = codeArea.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(bar -> bar.getOrientation() == Orientation.VERTICAL)
                .findFirst()
                .orElse(null);
        Subscription all = codeArea.widthProperty().subscribe(w -> scheduleUpdate())
                .and(codeArea.heightProperty().subscribe(h -> scheduleUpdate()))
                .and(codeArea.fontProperty().subscribe(f -> scheduleUpdate()))
                .and(codeArea.sceneProperty().subscribe(s -> scheduleUpdate()));
        if (vertical != null) {
            // Not deferred: editors kept in step by this value must not trail a pulse behind.
            all = all.and(vertical.valueProperty().subscribe(v -> update()));
        }
        subscription = subscription.and(all);
    }

    private void scheduleUpdate() {
        if (updateScheduled) {
            return;
        }
        updateScheduled = true;
        Platform.runLater(() -> {
            updateScheduled = false;
            update();
        });
    }

    /** The paragraph at the top of the viewport, or -1 when it cannot be measured. */
    private int measureTop() {
        Node vport = codeArea.lookup(".vport");
        Bounds screen = vport == null ? null : vport.localToScreen(vport.getLayoutBounds());
        return screen == null ? -1 : indexAt(screen.getMinX() + screen.getWidth() / 2, screen.getMinY() + 1);
    }

    /**
     * Measures the top line and places each band over the viewport rows of its lines, found by
     * bisection on {@link CodeArea#getTextPosition} (monotonic in {@code y}); a band out of view is
     * hidden.
     */
    private void update() {
        Node vport = codeArea.lookup(".vport");
        if (vport == null || codeArea.getScene() == null || codeArea.getScene().getWindow() == null) {
            regions.forEach(region -> region.setVisible(false));
            return;
        }
        Bounds view = overlay.sceneToLocal(vport.localToScene(vport.getLayoutBounds()));
        Bounds screen = vport.localToScreen(vport.getLayoutBounds());
        if (view == null || screen == null || view.getHeight() <= 0) {
            regions.forEach(region -> region.setVisible(false));
            return;
        }
        double x = screen.getMinX() + screen.getWidth() / 2;
        double top = screen.getMinY();
        double bottom = screen.getMaxY();
        int first = indexAt(x, top + 1);
        int last = indexAt(x, bottom - 1);
        if (first >= 0) {
            topParagraph.set(first);
        }
        int count = codeArea.getParagraphCount();
        for (int i = 0; i < bands.size(); i++) {
            LineBand band = bands.get(i);
            Region region = regions.get(i);
            int start = band.paragraph();
            int end = start + band.count();
            if (first < 0 || end < first || start > last + 1) {
                region.setVisible(false);
                continue;
            }
            double from = start <= first ? top : rowOf(x, top, bottom, start, count);
            double to = end > last ? bottom : rowOf(x, from, bottom, end, count);
            if (band.count() == 0) {
                if (start < first) {
                    region.setVisible(false);
                    continue;
                }
                region.resizeRelocate(view.getMinX(), view.getMinY() + (from - top) - RULE_HEIGHT / 2,
                        view.getWidth(), RULE_HEIGHT);
            } else {
                region.resizeRelocate(view.getMinX(), view.getMinY() + (from - top), view.getWidth(),
                        Math.max(1, to - from));
            }
            region.setVisible(true);
        }
    }

    /**
     * The {@code y} in {@code [low, high]} where paragraph {@code index} starts; the foot of the
     * last line when {@code index} is the number of paragraphs, the text ending above {@code high}.
     */
    private double rowOf(double x, double low, double high, int index, int count) {
        if (index >= count) {
            // Below the last line: where the text stops answering with it.
            double end = firstRowAtLeast(x, low, high, count - 1);
            TextPos lastLine = codeArea.getTextPosition(x, end + 1);
            double height = end - firstRowAtLeast(x, low, end, count - 2);
            return lastLine == null || count < 2 || height <= 0 ? high : Math.min(high, end + height);
        }
        return firstRowAtLeast(x, low, high, index);
    }

    /** The smallest {@code y} in {@code [low, high)} whose paragraph index is at least {@code index}. */
    private double firstRowAtLeast(double x, double low, double high, int index) {
        if (indexAt(x, low + 1) >= index) {
            return low;
        }
        while (high - low > 0.5) {
            double mid = (low + high) / 2;
            if (indexAt(x, mid) >= index) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return high;
    }

    private int indexAt(double screenX, double screenY) {
        TextPos pos = codeArea.getTextPosition(screenX, screenY);
        return pos == null ? -1 : pos.index();
    }
}
