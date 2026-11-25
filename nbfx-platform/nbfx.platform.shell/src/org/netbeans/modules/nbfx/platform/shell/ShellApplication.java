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

import org.netbeans.api.nbfx.shell.PaneRole;
import org.netbeans.api.nbfx.shell.ShellChrome;
import org.netbeans.api.nbfx.shell.ShellWindow;
import org.netbeans.api.nbfx.shell.ShellChromeProvider;
import org.netbeans.api.nbfx.shell.ShellCommandResolver;
import org.netbeans.api.nbfx.shell.ShellContext;
import org.netbeans.api.nbfx.shell.ShellFrame;
import org.netbeans.api.nbfx.shell.ShellProvider;
import org.netbeans.api.nbfx.shell.shutdown.ShutdownCoordinator;
import org.netbeans.api.nbfx.shell.shutdown.ShutdownImpact;
import org.netbeans.api.nbfx.shell.shutdown.ShutdownSelection;
import org.netbeans.api.nbfx.shell.ShellBranding;
import org.netbeans.api.nbfx.shell.ShellChromeProfile;
import org.netbeans.api.nbfx.shell.ShellProfileHint;
import org.netbeans.api.nbfx.shell.ShellProfileRegistry;
import org.netbeans.api.nbfx.shell.ShellProfileResolver;
import org.netbeans.api.nbfx.shell.ShellProfileSelection;
import org.netbeans.api.nbfx.shell.ChromeProfilePolicy;
import org.netbeans.api.nbfx.shell.ChromeProfilePolicyProvider;
import org.netbeans.api.nbfx.shell.WindowDecoration;
import org.netbeans.api.nbfx.shell.WindowKind;
import org.netbeans.api.nbfx.shell.WindowManager;
import org.netbeans.api.nbfx.shell.WindowPaneHost;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Subscription;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.editor.ContentManager;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.EditorSettings;
import org.netbeans.api.nbfx.view.ActiveComponent;
import org.netbeans.api.nbfx.view.ActiveComponentContext;
import org.netbeans.api.nbfx.view.ApplicationMenu;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.api.nbfx.views.PaneKind;
import org.netbeans.modules.nbfx.platform.launcher.Budget;
import org.netbeans.modules.nbfx.platform.theme.NbfxTheme;
import org.netbeans.api.nbfx.product.ProductProvider;
import org.netbeans.spi.nbfx.shell.WindowPane;
import org.netbeans.spi.nbfx.shell.WindowPaneContext;
import org.netbeans.spi.nbfx.shell.WindowPaneProvider;
import org.netbeans.modules.nbfx.platform.workspace.DockArea;
import org.netbeans.api.nbfx.shell.WindowSink;
import org.netbeans.api.nbfx.shell.WindowState;
import org.netbeans.api.nbfx.shell.WindowSystem;
import org.openide.LifecycleManager;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 * The platform's generic JavaFX application shell: the main window, the editor pane, docking,
 * sliding sides, the persisted window layout and the window-system lifecycle. Products use a
 * {@link ShellProvider} or the neutral default frame. Existing subclasses retain the legacy hooks
 * called from {@link #start} only until the C8 migration:
 * {@link #prepare()}, {@link #createPane(WindowPane, WindowPaneContext)}, {@link #decorate(BorderPane)},
 * {@link #sceneReady(Scene)} and {@link #started()}, in that order, then
 * {@link #restoreSession()}. {@link #captureSession()}
 * and {@link #stopping()} run during shutdown. This class itself knows no project, IDE or Java
 * concept.
 * <p>
 * The shell registers itself as the window system's {@link WindowSink} and, via
 * {@link ShellAppProvider}, is the default application when no product provides one.
 * <p>
 * The shell is one per JVM: the docking area, sliding area, composition and tab-pane registry are
 * process-wide.
 */
public class ShellApplication extends Application implements WindowSink, ShellWindow {

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
    private final SessionRestorer sessionRestorer = new SessionRestorer(appState, this::viewTabFor, this::viewStateStore);

    private NbfxTabPane mainPane;
    private DockArea<TabPane> dockArea;
    private SlidingArea slidingArea;
    private BorderPane root;
    private ShellChrome chrome;
    private ShellFrame shellFrame;
    private ShellRegionHostImpl regionHost;
    private boolean shellResolverClosed;
    private ShellContext shellContext;
    private ShellBranding branding;
    private boolean applicationMenuInstalled;
    private Subscription titleSubscription;
    private boolean stateCaptured;
    private ShutdownSelection shutdownSelection;
    private boolean shutdownStarted;
    private boolean shutdownCompleted;
    private Consumer<TabPane> activeComponentListener;
    private Subscription activeComponentFocusSubscription;
    /** Providers resolved for this shell, in priority order. */
    private List<WindowPaneProvider> paneProviderList = List.of();
    /** Providers that created a pane, so their resources are released with the shell. */
    private final Map<WindowPaneHost, WindowPaneProvider> paneProviders = new LinkedHashMap<>();
    private final WindowManager windowManager = new DefaultWindowManager();

    @Override
    public final void start(Stage stage) {
        Budget.mark("fxStart");
        this.stage = stage;
        NbfxTheme.install();
        prepare();
        WindowComposition composition = WindowCompositionResolver.resolve();
        Lookup shellLookup = shellLookup();
        WindowPaneContext paneContext = new WindowPaneContext(shellLookup, composition.panes(), this,
                this::createPaneHost);
        paneProviderList = providers(paneContext);
        Map<String, NbfxTabPane> panes = instantiatePanes(composition, paneContext);
        if (panes.isEmpty()) {
            throw new IllegalStateException("The shell has no primary pane");
        }
        mainPane = panes.get(PaneRole.MAIN.name());
        if (mainPane == null) {
            mainPane = panes.values().iterator().next();
        }
        dockArea = Docking.create(composition, panes);
        slidingArea = Docking.slides();
        ShellContext context = new ShellContext(shellLookup);
        shellContext = context;
        List<ShellProvider> frameProviders = List.copyOf(shellLookup.lookupAll(ShellProvider.class));
        if (frameProviders.size() > 1) {
            closeShellResolver();
            throw new IllegalStateException("Exactly one ShellProvider is allowed; found "
                    + frameProviders.size());
        }
        if (!frameProviders.isEmpty() || getClass() == ShellApplication.class) {
            ShellFrame frame = null;
            Node frameRoot = null;
            try {
                ShellProfileSelection selection = selectMainProfile(shellLookup);
                applyWindowDecoration(stage, selection.decoration());
                InstanceContent frameServices = new InstanceContent();
                frameServices.set(List.of(selection.profile()), null);
                context = new ShellContext(new ProxyLookup(new AbstractLookup(frameServices), shellLookup));
                shellContext = context;
                ShellToolBars toolBars = new ShellToolBars(context);
                regionHost = new ShellRegionHostImpl(context, slidingArea,
                        DefaultShellFrame.renderers(context, toolBars));
                frameServices.set(List.of(selection.profile(), regionHost, toolBars.container()), null);
                if (!frameProviders.isEmpty()) {
                    frame = frameProviders.get(0).create(context, context.profile());
                    if (frame == null) {
                        throw new IllegalStateException("ShellProvider returned null");
                    }
                    frameRoot = Objects.requireNonNull(frame.root(), "ShellFrame root");
                    if (frameRoot.getParent() != null) {
                        throw new IllegalStateException("ShellFrame root must be unparented");
                    }
                    if (frame.regions() != regionHost) {
                        throw new IllegalStateException("ShellFrame must use the runtime ShellRegionHost");
                    }
                } else {
                    frame = new DefaultShellFrame(context, regionHost);
                    frameRoot = Objects.requireNonNull(frame.root(), "ShellFrame root");
                }
                frame.setDockRoot(slidingArea);
                if (slidingArea.getParent() == null || !isWithin(slidingArea, frameRoot)
                        || !regionHost.isDockMounted(slidingArea)) {
                    throw new IllegalStateException("ShellFrame did not mount the runtime DOCK root");
                }
                shellFrame = frame;
                appState.initToolbars(toolBars.container());
                root = new BorderPane(frameRoot);
            } catch (RuntimeException | Error ex) {
                if (frame != null) {
                    try {
                        frame.close();
                    } catch (RuntimeException closeFailure) {
                        LOG.log(Level.WARNING, "Cannot close failed shell frame", closeFailure);
                    }
                }
                if (regionHost != null) {
                    regionHost.close();
                }
                closeShellResolver();
                shellFrame = null;
                regionHost = null;
                throw ex;
            }
        } else {
            try {
                root = new BorderPane(slidingArea);
                chrome = createChrome(context);
                if (chrome.top() != null) {
                    root.setTop(chrome.top());
                }
                if (chrome.bottom() != null) {
                    root.setBottom(chrome.bottom());
                }
                if (chrome instanceof ToolBarHost toolBarHost) {
                    appState.initToolbars(toolBarHost.toolBars());
                }
                decorate(root);
            } catch (RuntimeException | Error ex) {
                if (chrome != null) {
                    try {
                        chrome.dispose();
                    } catch (RuntimeException disposeFailure) {
                        LOG.log(Level.WARNING, "Cannot dispose failed legacy chrome", disposeFailure);
                    }
                    chrome = null;
                }
                closeShellResolver();
                throw ex;
            }
        }
        branding = ShellBranding.select(context.lookup()).orElse(null);
        if (branding != null) {
            root.getStyleClass().addAll(branding.rootStyleClasses());
        }

        Scene scene = new Scene(root, AppState.DEFAULT_WIDTH, AppState.DEFAULT_HEIGHT);
        scene.getStylesheets().add(NbfxTabPane.stylesheet());
        NbfxTheme.apply(scene);
        if (branding != null) {
            scene.getStylesheets().addAll(branding.sceneStylesheets());
            if (!branding.icons().isEmpty()) {
                stage.getIcons().setAll(branding.icons());
            }
            if (branding.minimumWidth() > 0) {
                stage.setMinWidth(branding.minimumWidth());
            }
            if (branding.minimumHeight() > 0) {
                stage.setMinHeight(branding.minimumHeight());
            }
        }
        sceneReady(scene);
        stage.setScene(scene);
        stage.setTitle(branding == null ? ShellBranding.DEFAULT_APPLICATION_NAME : branding.title(context));
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
        installActiveComponentBridge(scene);

        Budget.mark("usable");
        Budget.markUsable();
        installApplicationMenu();
        ShellWindowProviderService.set(this);
        started();
        UiReady.dispatch();
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
        if (activeComponentListener != null) {
            NbfxTabPane.removeActivePaneListener(activeComponentListener);
            activeComponentListener = null;
        }
        if (activeComponentFocusSubscription != null) {
            activeComponentFocusSubscription.unsubscribe();
            activeComponentFocusSubscription = null;
        }
        shutdownWithoutConfirmation();
        if (titleSubscription != null) {
            titleSubscription.unsubscribe();
            titleSubscription = null;
        }
        if (branding != null) {
            branding.dispose();
            branding = null;
        }
        closeShellFrame();
        shellContext = null;
        applicationMenuInstalled = false;
        ShellWindowProviderService.set(null);
        NbfxTheme.dispose();
        disposePanes();
        stopping();
        LOG.info("JavaFX shell stopping, shutting down the NetBeans Platform...");
        LifecycleManager.getDefault().exit();
    }

    private void closeShellFrame() {
        if (shellFrame == null) {
            try {
                if (chrome != null) {
                    chrome.dispose();
                }
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Cannot close legacy shell chrome", ex);
            } finally {
                chrome = null;
                closeShellResolver();
            }
            return;
        }
        ShellFrame frame = shellFrame;
        try {
            frame.close();
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Cannot close shell frame", ex);
        } finally {
            if (regionHost != null) {
                try {
                    regionHost.close();
                } catch (RuntimeException ex) {
                    LOG.log(Level.WARNING, "Cannot close shell region host", ex);
                }
                regionHost = null;
            }
            closeShellResolver();
            shellFrame = null;
        }
    }

    /** Applies the selected decoration before the stage is shown. */
    static void applyWindowDecoration(Stage stage, WindowDecoration decoration) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(decoration, "decoration");
        StageStyle target = decoration == WindowDecoration.UNDECORATED
                ? StageStyle.UNDECORATED : StageStyle.DECORATED;
        if (stage.getStyle() != target) {
            stage.initStyle(target);
        }
    }

    /** Resolves the main-window profile without requiring product metadata. */
    static ShellProfileSelection selectMainProfile(Lookup lookup) {
        List<ProductProvider> productProviders = List.copyOf(lookup.lookupAll(ProductProvider.class));
        if (productProviders.size() > 1) {
            throw new IllegalStateException("Exactly one ProductProvider is allowed; found "
                    + productProviders.size());
        }
        ChromeProfilePolicy policy = null;
        if (!productProviders.isEmpty()) {
            String policyId = ProductProvider.resolve(lookup).chromeProfilePolicyId();
            policy = ChromeProfilePolicyProvider.resolve(policyId, lookup).orElseThrow(
                    () -> new IllegalStateException("No ChromeProfilePolicyProvider for product policy "
                            + policyId));
        }
        return ShellProfileResolver.resolve(WindowKind.MAIN,
                single(lookup, ShellChromeProfile.class, "ShellChromeProfile"),
                single(lookup, ShellProfileHint.class, "ShellProfileHint"),
                policy, ShellProfileRegistry.discover());
    }

    private static <T> T single(Lookup lookup, Class<T> type, String description) {
        List<T> values = List.copyOf(lookup.lookupAll(type));
        if (values.size() > 1) {
            throw new IllegalStateException("At most one " + description + " is allowed; found "
                    + values.size());
        }
        return values.isEmpty() ? null : values.get(0);
    }

    private void closeShellResolver() {
        if (shellResolverClosed || shellContext == null) {
            return;
        }
        shellResolverClosed = true;
        ShellCommandResolver resolver = shellContext.lookup().lookup(ShellCommandResolver.class);
        if (resolver != null) {
            try {
                resolver.close();
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Cannot close shell command resolver", ex);
            }
        }
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

    /** The declarative frame installed for this shell, or {@code null} on the legacy subclass path. */
    protected final ShellFrame getShellFrame() {
        return shellFrame;
    }

    /** The stable window facade for products and contributions. */
    public final ShellWindow getWindow() {
        return this;
    }

    @Override
    public final javafx.stage.Stage stage() {
        return stage;
    }

    @Override
    public final javafx.scene.layout.BorderPane root() {
        return root;
    }

    @Override
    public final javafx.scene.control.TabPane mainPane() {
        return mainPane;
    }

    @Override
    public final javafx.scene.Node slidingArea() {
        return slidingArea;
    }

    @Override
    public final WindowManager windowManager() {
        return windowManager;
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
     * The product's project-tree state store. The default discards tree state; a product that owns
     * projects overrides this to persist expansion and selection.
     */
    protected ViewStateStore viewStateStore() {
        return ViewStateStore.NONE;
    }

    /**
     * The lookup exposed to the chrome and its dynamic contributions. The default is the global
     * lookup. This is a legacy subclass hook; new products publish per-shell services through their
     * NBM modules and use {@link ShellProvider} for the frame.
     *
     * @return the lookup, never {@code null}
     */
    protected Lookup shellLookup() {
        return new ProxyLookup(Lookups.fixed(this, windowManager), Lookup.getDefault());
    }

    /**
     * Creates legacy chrome for subclass-based shells. The declarative default/custom frame path does
     * not call this method; new products use {@link ShellProvider}.
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

    /** Creates the shell-owned host a provider fills; the concrete pane stays internal. */
    private WindowPaneHost createPaneHost(WindowPane pane, WindowPaneContext context) {
        return new NbfxTabPane(pane.role(), pane.kind());
    }

    /**
     * Instantiates a registered permanent pane. The default resolves the editor pane through the
     * content manager, then asks the highest-priority {@link WindowPaneProvider}, then creates a
     * plain pane of the declared kind. A product that owns a pane's content overrides this; the
     * default provider path is for products that only register a custom pane.
     *
     * @param pane    the declared pane
     * @param context the narrow shell context
     * @return the pane host, never {@code null}
     */
    protected WindowPaneHost createPane(WindowPane pane, WindowPaneContext context) {
        if (pane.role().isMain() || pane.kind() == PaneKind.EDITOR) {
            return createMainPane();
        }
        List<WindowPaneProvider> candidates = paneProviderList.isEmpty()
                ? providers(context) : paneProviderList;
        for (WindowPaneProvider provider : candidates) {
            WindowPaneHost instance = provider.createPane(pane, context);
            if (instance != null) {
                paneProviders.put(instance, provider);
                return instance;
            }
        }
        return new NbfxTabPane(pane.role(), pane.kind());
    }

    /** Providers from {@code context}, highest priority first. */
    private static List<WindowPaneProvider> providers(WindowPaneContext context) {
        List<WindowPaneProvider> providers = new ArrayList<>(
                context.lookup().lookupAll(WindowPaneProvider.class));
        providers.sort(Comparator.comparingInt(WindowPaneProvider::priority).reversed());
        return List.copyOf(providers);
    }

    /** Instantiates the composition's permanent panes, keyed by role name. */
    Map<String, NbfxTabPane> instantiatePanes(WindowComposition composition,
            WindowPaneContext context) {
        Map<String, NbfxTabPane> panes = new LinkedHashMap<>();
        for (WindowPane pane : composition.panes()) {
            PaneRole declared = pane.role();
            WindowPaneHost host = createPane(pane, context);
            if (host == null) {
                LOG.warning("No pane instance for registered pane " + pane.id());
                continue;
            }
            NbfxTabPane instance = host instanceof NbfxTabPane paneInstance ? paneInstance : null;
            if (instance == null || !declared.equals(instance.role())) {
                LOG.warning("Pane provider returned "
                        + (instance == null ? "an unsupported host" : "role " + instance.role())
                        + " for declared pane " + pane.id() + "; using a default pane");
                WindowPaneProvider provider = paneProviders.remove(host);
                if (provider != null) {
                    provider.disposePane(host);
                }
                instance = new NbfxTabPane(declared, pane.kind());
            }
            panes.put(declared.name(), instance);
        }
        return panes;
    }

    /** Releases panes created by providers; a provider that owns resources cleans up here. */
    private void disposePanes() {
        for (Map.Entry<WindowPaneHost, WindowPaneProvider> entry : paneProviders.entrySet()) {
            try {
                entry.getValue().disposePane(entry.getKey());
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Failed to dispose pane " + entry.getKey(), ex);
            }
        }
        paneProviders.clear();
        paneProviderList = List.of();
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

    /** Installs the platform application menu once the window is showing. */
    private void installApplicationMenu() {
        if (applicationMenuInstalled || shellContext == null) {
            return;
        }
        ApplicationMenu menu = shellContext.lookup().lookup(ApplicationMenu.class);
        if (menu != null) {
            menu.install(this::openPreferences, this::requestQuit);
            applicationMenuInstalled = true;
        }
    }

    /**
     * Called after the window is showing, on the JavaFX Application Thread, before the
     * {@code @OnUiReady} tasks. Kept as a subclass compatibility hook; product contributions use
     * {@link org.netbeans.api.nbfx.shell.OnUiReady} instead.
     */
    protected void started() {
    }

    /** Runs the Options command, so a native application menu can open the Options dialog. */
    protected final void openPreferences() {
        ActionRegistry registry = shellContext == null ? null : shellContext.actions();
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
     * cancels, or while the shutdown coordinator runs its cleanup in the background; the caller
     * keeps the window open then and {@link Platform#exit()} follows when cleanup finishes.
     */
    protected boolean requestQuit() {
        if (shutdownStarted) {
            return false;
        }
        if (!confirmClose()) {
            return false;
        }
        ShutdownCoordinator coordinator = Lookup.getDefault().lookup(ShutdownCoordinator.class);
        ShutdownSelection selection = null;
        if (coordinator != null) {
            ShutdownImpact impact = coordinator.impact();
            if (!impact.isEmpty()) {
                selection = coordinator.confirm(impact);
                if (selection == null) {
                    return false;
                }
                shutdownSelection = selection;
            }
            if (selection == null) {
                selection = ShutdownSelection.defaults(impact);
            }
        }
        captureSession();
        stateCaptured = true;
        if (windowSystemShown) {
            windowSystem.save();
        }
        if (coordinator == null) {
            shutdownCompleted = true;
            Platform.exit();
            return true;
        }
        // Cleanup may block (a save, a recording, temporary data), so keep the FX thread free and
        // exit only when the coordinator is done. The close request is consumed meanwhile and the
        // window is disabled, so nothing can be edited into the gap; a hung participant is bounded
        // by the timeout.
        shutdownStarted = true;
        if (root != null) {
            root.setDisable(true);
        }
        ShutdownSelection plan = selection;
        CompletableFuture.runAsync(() -> coordinator.shutdown(plan))
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((unused, error) -> {
                    if (error != null) {
                        LOG.log(Level.WARNING, "Shutdown cleanup failed or timed out", error);
                    }
                    Platform.runLater(() -> {
                        shutdownCompleted = true;
                        Platform.exit();
                    });
                });
        return false;
    }

    /**
     * Shuts the application-owned resources down when the FX application stops without going
     * through {@link #requestQuit()}. Uses the safe defaults from the impact description. This is
     * the last-resort path where the UI is already stopping, so it runs synchronously.
     */
    private void shutdownWithoutConfirmation() {
        if (shutdownCompleted) {
            return;
        }
        shutdownCompleted = true;
        ShutdownCoordinator coordinator = Lookup.getDefault().lookup(ShutdownCoordinator.class);
        if (coordinator == null) {
            return;
        }
        try {
            ShutdownImpact impact = coordinator.impact();
            coordinator.shutdown(shutdownSelection == null
                    ? ShutdownSelection.defaults(impact) : shutdownSelection);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Could not run the shutdown coordinator", ex);
        }
    }

    protected void captureSession() {
        sessionRestorer.captureSession(List.of(), List.of());
    }

    protected void restoreSession() {
        sessionRestorer.restoreSession(null);
        Docking.completeStartupLayout();
    }

    private static NbfxTabPane createMainPane() {
        ContentManager manager = Lookup.getDefault().lookup(ContentManager.class);
        if (manager instanceof ContentManagerService service && service.getMainPane() instanceof NbfxTabPane pane) {
            return pane;
        }
        return new NbfxTabPane(PaneRole.MAIN);
    }

    /**
     * Publishes the pane's active editor/view to the {@link ActiveComponentContext}, so the nbfx
     * global action context (and therefore the toolbar commands) follows the JavaFX selection.
     * The scene's focus owner is followed as well, so moving between split-editor halves updates
     * the active component even when the selected tab does not change.
     */
    private void installActiveComponentBridge(Scene scene) {
        ActiveComponentContext components =
                Lookup.getDefault().lookup(ActiveComponentContext.class);
        if (components == null) {
            return;
        }
        activeComponentListener = pane -> publishActiveComponent(components, pane);
        NbfxTabPane.addActivePaneListener(activeComponentListener);
        activeComponentFocusSubscription = scene.focusOwnerProperty().subscribe(owner -> {
            // Only a focus owner inside the dock area changes the active component. A toolbar or
            // menu must not republish, and must not reach into a pane of another (detached) window.
            if (owner != null && slidingArea != null && isWithin(owner, slidingArea)) {
                publishActiveComponent(components, NbfxTabPane.focusedPane());
            }
        });
        publishActiveComponent(components, NbfxTabPane.componentPane());
    }

    private static boolean isWithin(Node node, Node ancestor) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static void publishActiveComponent(ActiveComponentContext components, TabPane pane) {
        Tab tab = pane == null ? null : pane.getSelectionModel().getSelectedItem();
        EditorDocument document = NbfxTabPane.focusedDocumentOf(tab);
        ViewProvider view = NbfxTabPane.viewOf(tab);
        if (document != null) {
            components.setActiveComponent(new ActiveComponent.EditorComponent(document));
            EditorContexts.setActiveDocument(document);
        } else if (view != null) {
            components.setActiveComponent(new ActiveComponent.ViewComponent(view));
        } else {
            components.setActiveComponent(null);
            EditorContexts.setActiveDocument(null);
        }
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
