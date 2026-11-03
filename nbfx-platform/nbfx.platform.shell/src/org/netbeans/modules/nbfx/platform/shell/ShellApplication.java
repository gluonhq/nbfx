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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Subscription;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.editor.ContentManager;
import org.netbeans.api.nbfx.editor.EditorSettings;
import org.netbeans.api.nbfx.view.ApplicationMenu;
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
 * and adds its own panes, chrome, views and services through the hooks called from {@link #start}:
 * {@link #prepare()}, {@link #createPanes()}, {@link #decorate(BorderPane)}, {@link #sceneReady(Scene)}
 * and {@link #started()}, in that order, then {@link #restoreSession()}. {@link #captureSession()}
 * and {@link #stopping()} run during shutdown. This class itself knows no project, IDE or Java
 * concept.
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
    private ShellChrome chrome;
    private Subscription titleSubscription;
    private boolean stateCaptured;

    @Override
    public final void start(Stage stage) {
        Budget.mark("fxStart");
        this.stage = stage;
        prepare();
        List<NbfxTabPane> panes = createPanes();
        if (panes.isEmpty()) {
            throw new IllegalStateException("The shell has no primary pane");
        }
        mainPane = panes.stream()
                .filter(pane -> NbfxTabPane.roleOf(pane) == NbfxTabPane.PaneRole.MAIN)
                .findFirst()
                .orElse(panes.get(0));
        dockArea = Docking.create(panes.toArray(new NbfxTabPane[0]));
        slidingArea = Docking.slides();
        root = new BorderPane(slidingArea);
        ShellContext context = new ShellContext(shellLookup());
        chrome = createChrome(context);
        if (chrome.top() != null) {
            root.setTop(chrome.top());
        }
        if (chrome.bottom() != null) {
            root.setBottom(chrome.bottom());
        }
        if (chrome instanceof DefaultShellChrome defaultChrome) {
            appState.initToolbars(defaultChrome.toolBars());
        }
        decorate(root);

        Scene scene = new Scene(root, AppState.DEFAULT_WIDTH, AppState.DEFAULT_HEIGHT);
        scene.getStylesheets().add(NbfxTabPane.stylesheet());
        sceneReady(scene);
        stage.setScene(scene);
        ShellBranding branding = context.lookup().lookupAll(ShellBranding.class).stream()
                .max(Comparator.comparingInt(ShellBranding::priority))
                .orElse(null);
        stage.setTitle(branding == null ? "NetBeansFX" : branding.title(context));
        if (branding != null) {
            ObservableValue<String> title = branding.titleProperty(context);
            if (title != null) {
                titleSubscription = title.subscribe(stage::setTitle);
            }
        }
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
        if (titleSubscription != null) {
            titleSubscription.unsubscribe();
            titleSubscription = null;
        }
        if (chrome != null) {
            chrome.dispose();
        }
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

    /** The chrome installed for this shell. */
    protected final ShellChrome getChrome() {
        return chrome;
    }

    /** The persisted shell state, for a product that contributes project/session state. */
    protected final AppState getAppState() {
        return appState;
    }

    /** The session restorer, for a product that restores or captures its own session. */
    protected final SessionRestorer getSessionRestorer() {
        return sessionRestorer;
    }

    /**
     * The lookup exposed to the chrome and its dynamic contributions. The default is the global
     * lookup; a product overrides this to add per-shell services (its command resolver, dynamic
     * menu factories, branding and so on).
     *
     * @return the lookup, never {@code null}
     */
    protected Lookup shellLookup() {
        return Lookup.getDefault();
    }

    /**
     * Creates the chrome for this shell. The default resolves the highest-priority
     * {@link ShellChromeProvider}; a product may override this method directly.
     *
     * @param context the shell context
     * @return the chrome, never {@code null}
     */
    protected ShellChrome createChrome(ShellContext context) {
        return Lookup.getDefault().lookupAll(ShellChromeProvider.class).stream()
                .max(Comparator.comparingInt(ShellChromeProvider::priority))
                .map(provider -> provider.create(context))
                .orElse(ShellChrome.NONE);
    }

    /** Called before the primary panes are created, on the JavaFX Application Thread. */
    protected void prepare() {
    }

    /**
     * The primary panes of the window, the main editor pane first or in any order. The default is
     * the editor pane alone; a product adds its navigator panes here.
     *
     * @return the primary panes; must not be empty
     */
    protected List<NbfxTabPane> createPanes() {
        return List.of(createMainPane());
    }

    /**
     * Adds the product's chrome and contributions to the window. Called before the scene is shown,
     * on the JavaFX Application Thread. The default does nothing.
     *
     * @param root the window's root layout
     */
    protected void decorate(BorderPane root) {
    }

    /**
     * Called once the scene exists, before it is shown, so a product can install focus listeners and
     * shortcuts. The default does nothing.
     *
     * @param scene the window's scene
     */
    protected void sceneReady(Scene scene) {
    }

    /** Called after the window is showing, on the JavaFX Application Thread. */
    protected void started() {
        ApplicationMenu menu = Lookup.getDefault().lookup(ApplicationMenu.class);
        if (menu != null) {
            menu.install(this::openPreferences, this::requestQuit);
        }
    }

    /** Runs the Options command, so a native application menu can open the Options dialog. */
    protected final void openPreferences() {
        ActionRegistry registry = Lookup.getDefault().lookup(ActionRegistry.class);
        if (registry != null) {
            registry.find(ActionIds.SELECT_OPTIONS).ifPresent(Command::run);
        }
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
    protected boolean requestQuit() {
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

    protected void captureSession() {
        sessionRestorer.captureSession(List.of(), List.of());
    }

    protected void restoreSession() {
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

    protected Tab viewTabFor(String viewId) {
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
