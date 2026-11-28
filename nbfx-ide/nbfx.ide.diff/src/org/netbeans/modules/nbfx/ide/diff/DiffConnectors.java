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
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import org.netbeans.api.nbfx.diff.Difference;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.LineBand;

/**
 * The strip between the two editors of a diff: for each block that differs, a shape that joins its
 * lines on the left with its lines on the right, as NetBeans draws them, so that the eye finds
 * which lines became which while the two sides scroll. The shapes are placed from the bands the
 * editors lay over those lines, and follow them.
 */
final class DiffConnectors extends Pane {

    /** Where the block of one side is: the top and the foot of its lines in this strip. */
    private record Span(double top, double bottom, boolean inView) {
    }

    /** The shape of one block, kept and moved: one made anew would be drawn once before it is styled. */
    private record Shape(Path path, MoveTo start, CubicCurveTo upper, LineTo side, CubicCurveTo lower) {

        static Shape of(Difference.Kind kind) {
            Shape shape = new Shape(new Path(), new MoveTo(), new CubicCurveTo(), new LineTo(), new CubicCurveTo());
            shape.path().getElements().addAll(shape.start(), shape.upper(), shape.side(), shape.lower(),
                    new ClosePath());
            shape.path().getStyleClass().addAll("diff-connector", switch (kind) {
                case ADD -> DiffLines.ADDED;
                case DELETE -> DiffLines.DELETED;
                case CHANGE -> DiffLines.CHANGED;
            });
            shape.path().setManaged(false);
            shape.path().setVisible(false);
            return shape;
        }

        void join(Span from, Span to, double width) {
            double middle = width / 2;
            start.setX(0);
            start.setY(from.top());
            curve(upper, middle, from.top(), to.top(), width);
            side.setX(width);
            side.setY(to.bottom());
            curve(lower, middle, to.bottom(), from.bottom(), 0);
        }

        private static void curve(CubicCurveTo curve, double middle, double fromY, double toY, double toX) {
            curve.setControlX1(middle);
            curve.setControlY1(fromY);
            curve.setControlX2(middle);
            curve.setControlY2(toY);
            curve.setX(toX);
            curve.setY(toY);
        }
    }

    /** Half the width of the outline of a shape. */
    private static final double STROKE_INSET = 0.5;

    private final Rectangle clip = new Rectangle();
    private final InvalidationListener moved = observable -> requestRedraw();
    private final Runnable beforeLayout = () -> drawnThisFrame = false;
    private final Runnable afterLayout = this::redrawInFrame;
    private final List<Region> watched = new ArrayList<>();
    private List<Difference> differences = List.of();
    private List<LineBand> leftBands = List.of();
    private List<LineBand> rightBands = List.of();
    /** The style class of what is drawn of the block the view is at. */
    private static final String CURRENT = "diff-current";

    private List<Region> leftRegions = List.of();
    private List<Region> rightRegions = List.of();
    private List<Shape> shapes = List.of();
    private int current = -1;
    private boolean redrawRequested;
    /** Set when the diff is closed: nothing is drawn or listened to from then on. */
    private boolean disposed;
    /** Set from this drawing in a frame until the next frame starts. */
    private boolean drawnThisFrame;

    DiffConnectors() {
        getStyleClass().add("diff-connectors");
        setClip(clip);
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        widthProperty().addListener(moved);
        heightProperty().addListener(moved);
        sceneProperty().addListener((observable, old, scene) -> {
            if (old != null) {
                old.removePostLayoutPulseListener(afterLayout);
                old.removePreLayoutPulseListener(beforeLayout);
            }
            if (scene != null && !disposed) {
                scene.addPostLayoutPulseListener(afterLayout);
                scene.addPreLayoutPulseListener(beforeLayout);
            }
        });
    }

    /**
     * Lets go of the editors and of the frames of the window: the bands are no longer listened
     * to nor marked, nothing is drawn any more, and nothing is done in a frame. For the diff whose
     * tab is closed, which may stay in the window a while longer, or be held by what closed it.
     */
    void dispose() {
        disposed = true;
        current = -1;
        mark();
        watched.forEach(this::unwatch);
        watched.clear();
        leftRegions = List.of();
        rightRegions = List.of();
        shapes = List.of();
        differences = List.of();
        getChildren().clear();
        widthProperty().removeListener(moved);
        heightProperty().removeListener(moved);
        clip.widthProperty().unbind();
        clip.heightProperty().unbind();
        if (getScene() != null) {
            getScene().removePostLayoutPulseListener(afterLayout);
            getScene().removePreLayoutPulseListener(beforeLayout);
        }
    }

    /** Whether anything of an editor, or of a frame, is still listened to; for tests. */
    boolean isListening() {
        return !disposed || !watched.isEmpty();
    }

    /**
     * Joins the blocks of {@code found} as the two editors show them. Called after the bands were
     * set on both, in the order of the differences.
     */
    void show(EditorDocument left, EditorDocument right, List<Difference> found) {
        if (disposed) {
            return;
        }
        // The bands of the block that was the current one are those of an editor that may stay.
        int was = current;
        current = -1;
        mark();
        current = was;
        watched.forEach(this::unwatch);
        watched.clear();
        differences = found;
        leftBands = DiffLines.leftBands(found);
        rightBands = DiffLines.rightBands(found);
        leftRegions = bandRegions(left);
        rightRegions = bandRegions(right);
        leftRegions.forEach(this::watch);
        rightRegions.forEach(this::watch);
        shapes = found.stream().map(difference -> Shape.of(difference.kind())).toList();
        getChildren().setAll(shapes.stream().map(Shape::path).toList());
        mark();
        requestRedraw();
    }

    /** Draws the block of that index - its connector and its bands - as the current one; -1 for none. */
    void setCurrent(int index) {
        current = index;
        mark();
    }

    /**
     * Marks the current block with the style class {@code diff-current}: its connector, and the
     * band of each side it joins, so that the block the view is at reads as one shape too.
     */
    private void mark() {
        for (int i = 0; i < shapes.size(); i++) {
            mark(shapes.get(i).path().getStyleClass(), i == current);
        }
        for (List<Region> regions : List.of(leftRegions, rightRegions)) {
            for (int i = 0; i < regions.size(); i++) {
                mark(regions.get(i).getStyleClass(), i == current);
            }
        }
    }

    private static void mark(List<String> classes, boolean isCurrent) {
        classes.remove(CURRENT);
        if (isCurrent) {
            classes.add(CURRENT);
        }
    }

    /** The shapes drawn, one for each block in view on either side. */
    List<Node> connectors() {
        return getChildren().stream().filter(Node::isVisible).toList();
    }

    /**
     * The regions of the bands of {@code editor}, in the order they were set: the children of the
     * node with style class {@code line-bands} under it.
     */
    private static List<Region> bandRegions(EditorDocument editor) {
        Node overlay = editor == null ? null : editor.getNode().lookup(".line-bands");
        if (!(overlay instanceof Parent parent)) {
            return List.of();
        }
        return parent.getChildrenUnmodifiable().stream()
                .filter(Region.class::isInstance).map(Region.class::cast).toList();
    }

    private void watch(Region region) {
        region.layoutYProperty().addListener(moved);
        region.heightProperty().addListener(moved);
        region.visibleProperty().addListener(moved);
        watched.add(region);
    }

    private void unwatch(Region region) {
        region.layoutYProperty().removeListener(moved);
        region.heightProperty().removeListener(moved);
        region.visibleProperty().removeListener(moved);
    }

    /**
     * Has the shapes drawn again before the next frame is: in the frame in which the bands moved,
     * once they are all placed, or the shapes would trail them by one. The editors place their bands
     * as a frame is about to be drawn, as this does; if they did after this drew - told by a band
     * moving before the next frame starts - this takes its turn again, behind them from then on.
     */
    private void requestRedraw() {
        if (disposed) {
            return;
        }
        if (drawnThisFrame && getScene() != null) {
            drawnThisFrame = false;
            getScene().removePostLayoutPulseListener(afterLayout);
            getScene().addPostLayoutPulseListener(afterLayout);
        }
        if (redrawRequested) {
            return;
        }
        redrawRequested = true;
        Platform.requestNextPulse();
        Platform.runLater(this::redrawIfRequested);
    }

    private void redrawIfRequested() {
        if (redrawRequested) {
            redrawRequested = false;
            redraw();
        }
    }

    private void redrawInFrame() {
        redrawIfRequested();
        drawnThisFrame = true;
    }

    private void redraw() {
        boolean placed = getScene() != null && leftRegions.size() == shapes.size()
                && rightRegions.size() == shapes.size();
        for (int i = 0; i < shapes.size(); i++) {
            Shape shape = shapes.get(i);
            if (!placed) {
                shape.path().setVisible(false);
                continue;
            }
            Span from = span(leftRegions.get(i), leftBands.get(i));
            Span to = span(rightRegions.get(i), rightBands.get(i));
            boolean inView = from.inView() || to.inView();
            if (inView) {
                shape.join(from, to, getWidth());
            }
            shape.path().setVisible(inView);
        }
    }

    /**
     * Where {@code region} is in this strip: the whole extent of the lines of its band, which may
     * reach beyond the top or the foot of the strip, where the shape is cut.
     */
    private Span span(Region region, LineBand band) {
        if (!region.isVisible()) {
            // Not placed: the editor is not laid out yet.
            return new Span(0, 0, false);
        }
        Point2D top = sceneToLocal(region.localToScene(0, 0));
        double from = top.getY();
        double to = from + region.getHeight();
        if (band.count() == 0) {
            // A rule: where lines are missing, the shape comes to a point.
            from = (from + to) / 2;
            to = from;
        } else if (to - from > 2 * STROKE_INSET) {
            // The outline of the shape is drawn half on each side of it, that of the band inside
            // it: moved in by that half, the two fall on the same pixels.
            from += STROKE_INSET;
            to -= STROKE_INSET;
        }
        return new Span(from, to, to >= 0 && from <= getHeight());
    }
}
