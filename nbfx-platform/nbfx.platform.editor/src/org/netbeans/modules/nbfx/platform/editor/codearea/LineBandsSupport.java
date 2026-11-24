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
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
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
    /** How many pulses a scroll asked for before the area is laid out is tried again. */
    private static final int LAYOUT_RETRIES = 3;
    /** How many pulses after a scroll the top line is measured again. */
    private static final int SETTLE_PULSES = 2;
    /** How near, in lines, a scroll has to land to where it was asked. */
    private static final double SCROLL_TOLERANCE = 0.03;
    /** The most of a line told as scrolled off: all of it is the next line, not yet scrolled. */
    private static final double MAX_FRACTION = 0.99;

    private final CodeArea codeArea;
    private final Pane overlay = new Pane();
    /** How close, in pixels, the start of a row is found: well under one, or the bands would shake. */
    private static final double ROW_PRECISION = 0.05;

    private final Rectangle clip = new Rectangle();
    private final ReadOnlyDoubleWrapper topParagraph = new ReadOnlyDoubleWrapper(this, "topParagraph", 0);
    private final List<LineBand> bands = new ArrayList<>();
    private final List<Region> regions = new ArrayList<>();

    private ScrollBar vertical;
    private final Runnable afterLayout = this::update;
    /** The scene whose frames {@link #afterLayout} is told of. */
    private Scene pulsed;
    private Subscription subscription = Subscription.EMPTY;
    private boolean hooked;
    /** Set while {@link #scrollTo} looks for the place, so that the top line is not told on the way. */
    private boolean adjusting;
    /** The height of a line as last measured, by which the rows out of view are reckoned. */
    private double lineHeight;
    /** Counts the scrolls asked for: one put off until the area is ready gives way to a later one. */
    private long scrollRequest;

    LineBandsSupport(CodeArea codeArea) {
        this.codeArea = codeArea;
        overlay.setMouseTransparent(true);
        overlay.setPickOnBounds(false);
        overlay.getStyleClass().addAll("overlay-bands", "line-bands");
        // The bands keep their whole extent; only what is over the viewport is shown.
        overlay.setClip(clip);
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
    ReadOnlyDoubleProperty topParagraphProperty() {
        watch();
        return topParagraph.getReadOnlyProperty();
    }

    /**
     * Scrolls so that {@code position} is at the top of the viewport: the line of its whole part,
     * with the fraction of it that its fractional part says scrolled off. The rich text area maps
     * the value of its scroll bar to a place in the text only roughly, so the landing is measured
     * and the value nudged by the difference, a few times at most.
     */
    void scrollTo(double position) {
        scrollTo(position, LAYOUT_RETRIES, ++scrollRequest);
    }

    private void scrollTo(double position, int retries, long request) {
        RestoreViewSupport.whenSkinned(codeArea, () -> {
            if (request != scrollRequest) {
                // Asked before the area had a skin, or to be tried again, and another place was
                // asked for since: that one stands.
                return;
            }
            hook();
            int count = codeArea.getParagraphCount();
            if (vertical == null || count < 2) {
                return;
            }
            double wanted = Math.clamp(position, 0, count - 1);
            if (lineHeight <= 0) {
                // Not measured yet - no frame was drawn - and without the height of a line the part
                // of the top line scrolled off cannot be told: the landing would pass for exact.
                adjusting = true;
                update();
                adjusting = false;
            }
            double actual = measureTop();
            if (Math.abs(actual - wanted) <= SCROLL_TOLERANCE) {
                return;
            }
            double range = vertical.getMax() - vertical.getMin();
            double value = vertical.getValue() + range * (wanted - actual) / (count - 1);
            boolean atLimit = false;
            // The lines passed on the way are not told: only where the scroll ends.
            adjusting = true;
            for (int attempt = 0; attempt < 6; attempt++) {
                double clamped = Math.clamp(value, vertical.getMin(), vertical.getMax());
                atLimit = clamped != value;
                vertical.setValue(clamped);
                actual = measureTop();
                if (actual < 0 || Math.abs(actual - wanted) <= SCROLL_TOLERANCE || (attempt > 0 && atLimit)) {
                    // There, or as far as the text goes.
                    break;
                }
                value = clamped + range * (wanted - actual) / (count - 1);
            }
            adjusting = false;
            update();
            // And measured again once laid out: what is read right after a move can be a line off.
            settle(SETTLE_PULSES);
            if (Math.abs(actual - wanted) > SCROLL_TOLERANCE && !atLimit && retries > 0) {
                // Not laid out yet - asked for as the editor is first shown: once it is.
                Platform.runLater(() -> scrollTo(position, retries - 1, request));
            }
        });
    }

    /** Measures again in each of the next {@code pulses} pulses. */
    private void settle(int pulses) {
        if (pulses > 0) {
            Platform.runLater(() -> {
                update();
                settle(pulses - 1);
            });
        }
    }

    void dispose() {
        subscription.unsubscribe();
        subscription = Subscription.EMPTY;
        follow(null);
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
                .and(codeArea.sceneProperty().subscribe(scene -> {
                    follow(scene);
                    scheduleUpdate();
                }));
        if (vertical != null) {
            all = all.and(vertical.valueProperty().subscribe(v -> scheduleUpdate()));
        }
        subscription = subscription.and(all);
    }

    /** Has the bands placed in the next frame. */
    private void scheduleUpdate() {
        Platform.requestNextPulse();
    }

    /**
     * Places the bands in each frame of {@code scene}, once it is laid out and before it is drawn.
     * Nothing tells in time that the text has moved: the area moves its scroll bar as it lays the
     * text out, so that bands placed when the bar is heard of are placed for the frame after, and
     * trail the text by one - which shows as a shake. So the text is measured in every frame, where
     * it is about to be drawn; placing a band where it already is costs nothing more.
     */
    private void follow(Scene scene) {
        if (pulsed != null) {
            pulsed.removePostLayoutPulseListener(afterLayout);
        }
        pulsed = scene;
        if (pulsed != null) {
            pulsed.addPostLayoutPulseListener(afterLayout);
        }
    }

    /**
     * The place in the text at the top of the viewport - the line there, plus the fraction of it
     * that is scrolled off - or -1 when it cannot be measured.
     */
    private double measureTop() {
        Node vport = codeArea.lookup(".vport");
        if (vport == null) {
            return -1;
        }
        codeArea.layout();
        Bounds screen = vport.localToScreen(vport.getLayoutBounds());
        if (screen == null) {
            return -1;
        }
        double x = screen.getMinX() + screen.getWidth() / 2;
        double top = screen.getMinY();
        int first = indexAt(x, top + 1);
        if (first < 0) {
            return -1;
        }
        return first + scrolledOff(x, top, screen.getMaxY(), first);
    }

    /**
     * How much of line {@code first}, the one at the top of the viewport, is above it, as a
     * fraction of the height of a line: zero while that height is not known.
     */
    private double scrolledOff(double x, double top, double bottom, int first) {
        if (lineHeight <= 0 || indexAt(x, bottom - 1) <= first) {
            return 0;
        }
        double nextStart = firstRowAtLeast(x, top, Math.min(bottom, top + 2 * lineHeight), first + 1);
        return Math.clamp((lineHeight - (nextStart - top)) / lineHeight, 0, MAX_FRACTION);
    }

    /**
     * Measures the top line and places each band over its lines. The rows of the lines in view are
     * found by bisection on {@link CodeArea#getTextPosition} (monotonic in {@code y}); those of the
     * lines above and below it are reckoned from the height of a line, so that a band that is only
     * partly in view, or not at all, keeps its whole extent, and what is drawn beside the editor from
     * it does not shrink as it scrolls away. The overlay shows the part over the viewport.
     */
    private void update() {
        Node vport = codeArea.lookup(".vport");
        if (vport == null || codeArea.getScene() == null || codeArea.getScene().getWindow() == null) {
            regions.forEach(region -> region.setVisible(false));
            return;
        }
        // The lines are measured where they are laid out: a scroll that is still to be laid out
        // is laid out now, not in the next pulse, after the bands were placed by the old one.
        codeArea.layout();
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
        if (first < 0 || last < first) {
            regions.forEach(region -> region.setVisible(false));
            return;
        }
        // Across the gutter too, so that what is drawn beside the editor meets the band, not a gap.
        Node gutter = codeArea.lookup(".left-side");
        Bounds gutterView = gutter == null || !gutter.isVisible() ? null
                : overlay.sceneToLocal(gutter.localToScene(gutter.getLayoutBounds()));
        double bandX = gutterView == null ? view.getMinX() : Math.min(view.getMinX(), gutterView.getMinX());
        double bandWidth = view.getMaxX() - bandX;
        clip.setX(bandX);
        clip.setY(view.getMinY());
        clip.setWidth(bandWidth);
        clip.setHeight(view.getHeight());

        // Where the last line in view starts, and the first one - which may be cut by the top.
        double lastStart = last == first ? top : firstRowAtLeast(x, top, bottom, last);
        if (last - first >= 2) {
            double secondStart = firstRowAtLeast(x, top, bottom, first + 1);
            lineHeight = (lastStart - secondStart) / (last - first - 1);
        } else if (lineHeight <= 0) {
            lineHeight = codeArea.getFont() == null ? 16 : codeArea.getFont().getSize() * 1.4;
        }
        double firstStart = last == first ? top : firstRowAtLeast(x, top, bottom, first + 1) - lineHeight;
        if (!adjusting) {
            // In hundredths of a line: what the measuring is good for, and no more is told than moved.
            double scrolledOff = Math.clamp((top - firstStart) / lineHeight, 0, MAX_FRACTION);
            topParagraph.set(first + Math.round(scrolledOff * 100) / 100.0);
        }
        for (int i = 0; i < bands.size(); i++) {
            LineBand band = bands.get(i);
            Region region = regions.get(i);
            double from = rowOf(band.paragraph(), x, top, bottom, first, last, firstStart, lastStart);
            // On whole pixels, the foot as the top: a band drawn between them would have its edges
            // fall on one pixel or the next as it scrolls, and seem to shake.
            double y = view.getMinY() + (from - top);
            if (band.count() == 0) {
                region.resizeRelocate(bandX, overlay.snapPositionY(y - RULE_HEIGHT / 2), bandWidth, RULE_HEIGHT);
            } else {
                double to = rowOf(band.paragraph() + band.count(), x, top, bottom, first, last, firstStart, lastStart);
                double snappedTop = overlay.snapPositionY(y);
                double snappedFoot = overlay.snapPositionY(view.getMinY() + (to - top));
                region.resizeRelocate(bandX, snappedTop, bandWidth, Math.max(1, snappedFoot - snappedTop));
            }
            region.setVisible(true);
        }
    }

    /**
     * The {@code y} where paragraph {@code index} starts: measured when it is in view, and reckoned
     * from the first or the last line in view by the height of a line when it is above or below -
     * as is the foot of the last line of the text, {@code index} being the number of paragraphs.
     */
    private double rowOf(int index, double x, double top, double bottom, int first, int last,
            double firstStart, double lastStart) {
        if (index <= first) {
            return firstStart - (first - index) * lineHeight;
        }
        if (index > last) {
            return lastStart + (index - last) * lineHeight;
        }
        return index == last ? lastStart : firstRowAtLeast(x, top, bottom, index);
    }

    /** The smallest {@code y} in {@code [low, high)} whose paragraph index is at least {@code index}. */
    private double firstRowAtLeast(double x, double low, double high, int index) {
        if (indexAt(x, low + 1) >= index) {
            return low;
        }
        while (high - low > ROW_PRECISION) {
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
