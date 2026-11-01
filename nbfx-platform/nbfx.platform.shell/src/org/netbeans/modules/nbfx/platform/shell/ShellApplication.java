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
package org.netbeans.modules.nbfx.platform.shell;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.netbeans.api.nbfx.editor.ContentManager;
import org.netbeans.api.nbfx.editor.EditorSettings;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.modules.nbfx.platform.launcher.Budget;
import org.netbeans.modules.nbfx.platform.workspace.DockArea;
import org.netbeans.modules.nbfx.platform.workspace.WindowSink;
import org.netbeans.modules.nbfx.platform.workspace.WindowState;
import org.netbeans.modules.nbfx.platform.workspace.WindowSystem;
import org.openide.LifecycleManager;
import org.openide.util.Lookup;

/**
 * The platform's generic JavaFX application shell: the main window, the editor pane, docking,
 * sliding sides, the persisted window layout and the window-system lifecycle. A product extends it
 * by overriding {@link #decorate(BorderPane)} and adding its own menus, views and services; this
 * class itself knows no project, IDE or Java concept.
 * <p>
 * The shell registers itself as the window system's {@link WindowSink} and, via
 * {@link ShellAppProvider}, is the default application when no product provides one.
 */
public class ShellApplication extends Application implements WindowSink {

    private static final Logger LOG = Logger.getLogger(ShellApplication.class.getName());

    private static final double MAXIMIZED_TOLERANCE = 4;

    private Stage stage;
    /** The window system driving the main-window lifecycle; {@code null} when none is registered. */
    private WindowSystem windowSystem;
    /** Whether the window system reached the SHOWN phase, so {@link WindowSystem#save()} is legal. */
    private boolean windowSystemShown;
    /** Last non-maximized geometry, so a maximized window still persists a restore-size. */
    private double restoreX = Double.NaN, restoreY = Double.NaN;
    private double restoreWidth = AppState.DEFAULT_WIDTH, restoreHeight = AppState.DEFAULT_HEIGHT;

    private final AppState appState = new AppState();
    private final SessionRestorer sessionRestorer = new SessionRestorer(appState, this::viewTabFor);

    private NbfxTabPane mainPane;
    private DockArea<TabPane> dockArea;
    private SlidingArea slidingArea;
    private BorderPane root;
    private boolean stateCaptured;

    @Override
    public final void start(Stage stage) {
        Budget.mark("fxStart");
        this.stage = stage;
        mainPane = createMainPane();
        dockArea = Docking.create(mainPane);
        slidingArea = Docking.slides();
        root = new BorderPane(slidingArea);
        decorate(root);

        Scene scene = new Scene(root, AppState.DEFAULT_WIDTH, AppState.DEFAULT_HEIGHT);
        scene.getStylesheets().add(NbfxTabPane.stylesheet());
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (!requestQuit()) {
                event.consume();
            }
        });

        trackWindowBounds();
        appState.initViewSettings(Lookup.getDefault().lookup(EditorSettings.class));
        appState.installShutdownHook();

        windowSystem = Lookup.getDefault().lookup(WindowSystem.class);
        if (windowSystem != null) {
            windowSystem.setSink(this);
            windowSystem.init();
            windowSystem.load();
            windowSystem.show();
            windowSystemShown = true;
        } else {
            stage.show();
        }

        Budget.mark("usable");
        Budget.markUsable();
        started();
        Platform.runLater(this::restoreSession);
    }

    @Override
    public void stop() {
        if (!stateCaptured) {
            captureSession();
        }
        if (windowSystemShown) {
            windowSystem.save();
        }
        appState.save();
        stopping();
        LOG.info("JavaFX shell stopping, shutting down the NetBeans Platform...");
        LifecycleManager.getDefault().exit();
    }

    /** The shell's stage. */
    protected final Stage getStage() {
        return stage;
    }

    /** The main editor pane. */
    protected final NbfxTabPane getMainPane() {
        return mainPane;
    }

    /** The dock area holding the main pane and any docked panes. */
    protected final DockArea<TabPane> getDockArea() {
        return dockArea;
    }

    /** The dock area with the sliding sides minimized panes go to; the window's root content. */
    protected final SlidingArea getSlidingArea() {
        return slidingArea;
    }

    /** The window's root layout, for a product that adds chrome around the dock area. */
    protected final BorderPane getRoot() {
        return root;
    }

    /**
     * Adds the product's chrome and contributions to the window. Called before the scene is shown,
     * on the JavaFX Application Thread. The default does nothing.
     *
     * @param root the window's root layout
     */
    protected void decorate(BorderPane root) {
    }

    /** Called after the window is showing, on the JavaFX Application Thread. */
    protected void started() {
    }

    /** Called during shutdown, before the platform exits, on the JavaFX Application Thread. */
    protected void stopping() {
    }

    /** Confirms unsaved documents before closing; a product may extend the confirmation. */
    protected boolean confirmClose() {
        return CloseConfirmation.confirmClose(EditorContexts.documentsSnapshot());
    }

    /**
     * Confirms unsaved changes, captures the session and exits. Returns {@code false} when the user
     * cancels; the caller keeps the window open then.
     */
    private boolean requestQuit() {
        if (!confirmClose()) {
            return false;
        }
        captureSession();
        stateCaptured = true;
        if (windowSystemShown) {
            windowSystem.save();
        }
        Platform.exit();
        return true;
    }

    private void captureSession() {
        sessionRestorer.captureSession(List.of(), List.of());
    }

    private void restoreSession() {
        if (!sessionRestorer.startupLayout().isEmpty()) {
            sessionRestorer.restoreSession(null);
        }
    }

    private static NbfxTabPane createMainPane() {
        ContentManager manager = Lookup.getDefault().lookup(ContentManager.class);
        if (manager instanceof ContentManagerService service && service.getMainPane() instanceof NbfxTabPane pane) {
            return pane;
        }
        return new NbfxTabPane(NbfxTabPane.PaneRole.MAIN);
    }

    private Tab viewTabFor(String viewId) {
        for (ViewProvider view : Lookup.getDefault().lookupAll(ViewProvider.class)) {
            if (viewId.equals(view.getId())) {
                return ViewTabs.create(view, view.getDescription());
            }
        }
        return null;
    }

    // --- WindowSink: the JavaFX adapter the window system drives ---------------------------------

    @Override
    public void apply(WindowState state) {
        if (stage == null) {
            return;
        }
        double x = state.x();
        double y = state.y();
        double width = state.width();
        double height = state.height();
        boolean maximized = state.maximized();
        if (!(width > 0)) {
            AppState.WindowBounds legacy = appState.persistedWindowBounds();
            x = legacy.x();
            y = legacy.y();
            width = legacy.width();
            height = legacy.height();
            maximized = legacy.maximized();
        }
        stage.setWidth(width);
        stage.setHeight(height);
        if (!Double.isNaN(x) && !Double.isNaN(y) && AppState.isVisibleOnAnyScreen(x, y, width, height)) {
            stage.setX(x);
            stage.setY(y);
        } else if (!Double.isNaN(x) || !Double.isNaN(y)) {
            LOG.info("Stored window position is off-screen; centering instead");
        }
        restoreWidth = width;
        restoreHeight = height;
        stage.setMaximized(maximized);
    }

    @Override
    public WindowState snapshot() {
        boolean maximized = isMaximized();
        double x = maximized ? restoreX : stage.getX();
        double y = maximized ? restoreY : stage.getY();
        double width = maximized ? restoreWidth : stage.getWidth();
        double height = maximized ? restoreHeight : stage.getHeight();
        return new WindowState(List.of(), x, y, width, height, maximized, Map.of());
    }

    @Override
    public void show() {
        stage.show();
    }

    @Override
    public void hide() {
        stage.hide();
    }

    private void trackWindowBounds() {
        stage.xProperty().subscribe(nv -> {
            if (!isMaximized()) {
                restoreX = nv.doubleValue();
            }
        });
        stage.yProperty().subscribe(nv -> {
            if (!isMaximized()) {
                restoreY = nv.doubleValue();
            }
        });
        stage.widthProperty().subscribe(nv -> {
            if (!isMaximized()) {
                restoreWidth = nv.doubleValue();
            }
        });
        stage.heightProperty().subscribe(nv -> {
            if (!isMaximized()) {
                restoreHeight = nv.doubleValue();
            }
        });
    }

    private boolean isMaximized() {
        if (!stage.isMaximized()) {
            return false;
        }
        double x = stage.getX();
        double y = stage.getY();
        double w = stage.getWidth();
        double h = stage.getHeight();
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(w) || Double.isNaN(h)) {
            return true;
        }
        List<Screen> screens = Screen.getScreensForRectangle(x, y, w, h);
        if (screens.isEmpty()) {
            return true;
        }
        Rectangle2D visual = screens.getFirst().getVisualBounds();
        return w >= visual.getWidth() - MAXIMIZED_TOLERANCE
                && h >= visual.getHeight() - MAXIMIZED_TOLERANCE;
    }
}
