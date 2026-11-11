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
package org.netbeans.modules.nbfx.ide.workbench;

import org.netbeans.modules.nbfx.platform.shell.api.ShellChrome;
import org.netbeans.modules.nbfx.platform.shell.api.ShellContext;

import javafx.collections.FXCollections;
import javafx.beans.property.SimpleObjectProperty;
import org.netbeans.modules.nbfx.platform.shell.ContentManagerService;
import org.netbeans.modules.nbfx.platform.shell.EditorContexts;
import org.netbeans.modules.nbfx.platform.shell.FileSelectionContextService;
import org.netbeans.modules.nbfx.platform.shell.AppState;
import org.netbeans.modules.nbfx.platform.shell.CloseConfirmation;
import org.netbeans.modules.nbfx.platform.shell.DefaultShellChrome;
import org.netbeans.modules.nbfx.platform.shell.api.DetachedBarsContext;
import org.netbeans.modules.nbfx.platform.shell.SessionRestorer;
import org.netbeans.modules.nbfx.platform.shell.ShellApplication;
import org.netbeans.modules.nbfx.platform.shell.WindowComposition;
import org.netbeans.modules.nbfx.platform.shell.spi.WindowPane;
import org.netbeans.modules.nbfx.project.context.ProjectEditorScope;
import org.netbeans.modules.nbfx.platform.shell.Docking;
import org.netbeans.modules.nbfx.platform.workspace.DockTree;
import org.netbeans.modules.nbfx.platform.shell.NbfxTabPane;
import org.netbeans.modules.nbfx.platform.shell.ViewStateStore;
import org.netbeans.modules.nbfx.platform.shell.ViewPlacement;
import org.netbeans.modules.nbfx.platform.shell.ViewTabs;
import org.netbeans.modules.nbfx.platform.workspace.WindowSystem;
import org.netbeans.modules.nbfx.platform.workspace.DockTree;
import org.netbeans.modules.nbfx.platform.workspace.ViewSpec;
import org.netbeans.modules.nbfx.platform.workspace.ViewRegistry;

import org.netbeans.api.nbfx.editor.ContentManager;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.editor.EditorContext;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.file.FileSelectionContext;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.api.nbfx.project.ProjectIconProvider;
import org.netbeans.api.nbfx.project.ProjectRegistry;
import org.netbeans.api.nbfx.progress.Progress;
import org.netbeans.api.nbfx.view.ViewManager;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.netbeans.modules.nbfx.ide.workbench.NavigatorView;
import org.netbeans.modules.nbfx.ide.file.FileUndoManager;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import javafx.util.Subscription;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.NbBundle;

public class JavaFXLaunchApp extends ShellApplication {

    private final static Logger LOG = Logger.getLogger(JavaFXLaunchApp.class.getName());

    private Collection<? extends NavigatorView> providers;
    private NbfxTabPane navigatorPane;
    /** Still owns the detached-window bars while those move to the shell builders. */
    private org.netbeans.modules.nbfx.platform.shell.ToolBarHost mainToolBars;
    private final ProjectRegistry projectRegistry = projectRegistry();
    /** The running application, so services (e.g. the project opener) can reach it. */
    private static volatile JavaFXLaunchApp instance;
    /** The projects whose trees are still loading; several can load at once. */
    private final ProjectLoads loads = new ProjectLoads();
    /** The task reported while projects load; separate from builds, which own their own task. */
    private Progress.Handle loadProgress;
    /** Moves the selection between open projects, from the Window menu and its shortcuts. */
    private final ProjectSwitcher projectSwitcher = new ProjectSwitcher(projectRegistry, this::revealProject);
    /** Watches the open projects' root folders, so one that is gone from disk does not stay open. */
    private final ProjectRootWatcher rootWatcher = new ProjectRootWatcher(this::projectRootGone);
    /** Picks up changes made to the open projects outside the IDE, on window activation. */
    private final FileSystemRefresher refresher = new FileSystemRefresher(this::openProjectRoots, this::refreshProjects);
    /** Whether the whole application has lost focus, i.e. the user went to another application. */
    private boolean applicationDeactivated;
    /** The focus subscription of every window that is showing, so none is subscribed to twice. */
    private final Map<Window, Subscription> focusWatches = new IdentityHashMap<>();
    /** The projects of the restored session that have not finished loading yet. */
    private final Set<String> pendingSessionProjects = new LinkedHashSet<>();
    /** Whether the last session's projects are still being reopened. */
    private boolean restoringSession;
    /** The project that was selected when the last session ended, reselected once it is restored. */
    private String sessionSelected;
    /* Dispatch for Cut/Copy/Paste/Undo/Redo on files while the project tree is focused, on the editor otherwise. */
    private final FileSelectionContext fileSelection = fileSelectionContext();
    /** Whether the treeView is focused or not. */
    private final BooleanProperty treeFocused = new SimpleBooleanProperty(false);
    /** Whether a navigator reveal is currently moving focus. */
    private boolean navigatorRevealInProgress;
    /** IDE project/session state, no longer owned by the generic shell. */
    private final ProjectState projectState = new ProjectState(getAppState());

    @Override
    protected ShellChrome createChrome(ShellContext context) {
        ObservableValue<EditorDocument> mainScope = mainPaneActiveDocument();
        WorkspaceCommands.register(Lookup.getDefault().lookup(ActionRegistry.class),
                windowActions(mainScope),
                file -> revealFileInNavigator(file, 0), file -> revealFileInNavigator(file, 1));
        DefaultShellChrome chrome = new DefaultShellChrome(context);
        mainToolBars = chrome;
        return chrome;
    }

    @Override
    protected ViewStateStore viewStateStore() {
        return projectState;
    }

    @Override
    protected Lookup shellLookup() {
        return new ProxyLookup(Lookups.fixed(
                new IdeShellCommandResolver(mainPaneActiveDocument(), treeFocused),
                IdeShellIconProvider.INSTANCE,
                new RecentProjectsMenuFactory(projectState::getRecentProjects, this::openProject,
                        this::clearRecentProjects, this::projectIcon),
                new OpenProjectsMenuFactory(projectRegistry, projectSwitcher, this::projectIcon),
                new NavigatorPaneProvider(getSessionRestorer(),
                        providers == null ? List.of() : providers, this::setNavigatorPane)),
                super.shellLookup());
    }

    @Override
    protected void prepare() {
        instance = this;
        // Resolve the navigator views up front (the ViewProviders registered as navigators).
        providers = navigatorViews();
        LOG.fine("PROVIDERS = " + providers);
        // Publish the navigator-focused state so the file commands can track it (clipboard refresh).
        treeFocused.subscribe(fileSelection::setNavigatorFocused);
        for (NavigatorView provider : providers) {
            // A project's tree is built in the background and reported back through this callback,
            // which resolves the project from the loaded root: several can be loading at once.
            provider.setOnProjectLoaded(this::onProjectLoaded);
            provider.setOnProjectLoadFailed(this::onProjectLoadFailed);
        }
    }

    @Override
    protected void decorate(BorderPane borderPane) {
        if (getSessionRestorer().startupLayout().isEmpty()) {
            // First run: no layout to restore, so the default one also holds the default views.
            introduceDefaultViews();
        }

        projectRegistry.selectedProjectProperty().subscribe(project ->
                // Undo/Redo of file operations apply to the selected project's own history.
                FileUndoManager.getDefault().setScope(project == null ? null : project.getPath()));
    }

    @Override
    protected void sceneReady(Scene scene) {
        // Reflect which area (navigator tree vs editor) holds keyboard focus, both when the focus
        // owner changes within the window and when the window itself (re)gains focus.
        ObservableValue<EditorDocument> activeDocument = EditorContexts.activeDocument();
        scene.focusOwnerProperty().subscribe(fo -> {
            if (getStage().isFocused()) {
                updateFocusScope(fo);
                // Moving the focus into a pane makes what is current there decide the project.
                if (isNavigatorFocused(fo)) {
                    selectProjectOf(focusedNavigatorFile(fo));
                } else if (isWithin(fo, getSlidingArea()) && activeDocument != null) {
                    selectProjectOf(activeDocument.getValue());
                }
            }
        });
        getStage().focusedProperty().subscribe(focused -> {
            Node focusOwner = scene.getFocusOwner();
            if (!focused) {
                getDockArea().markFocused(false, focusOwner);
                treeFocused.set(false);
            } else {
                updateFocusScope(focusOwner);
            }
        });
        // Shift+Cmd+1 / Shift+Cmd+2 reveal the active editor's file in the Logical / Physical view.
        NbfxTabPane.installNavigatorRevealShortcut(scene, this::activeEditorFile);
        NbfxTabPane.installProjectSwitchShortcut(scene);
        if (activeDocument != null) {
            activeDocument.subscribe(this::selectProjectOf);
        }
    }

    @Override
    protected void started() {
        super.started();

        // Everything the IDE knows about the open projects comes from the platform's filesystem
        // layer, which does not see changes made outside the IDE until it is refreshed.
        watchApplicationFocus();
    }

    @Override
    protected void restoreSession() {
        restoreSessionProjects();
    }

    @Override
    protected void stopping() {
        refresher.shutdown();
    }

    private void watchApplicationFocus() {
        ObservableList<Window> windows = Window.getWindows();
        windows.forEach(this::watchFocus);
        windows.addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                change.getRemoved().forEach(this::unwatchFocus);
                change.getAddedSubList().forEach(this::watchFocus);
            }
        });
    }

    private void watchFocus(Window window) {
        // That list follows show/hide, not the life of a window: a completion popup or a drag
        // overlay is shown again and again, and would collect a subscription every time.
        focusWatches.computeIfAbsent(window,
                w -> w.focusedProperty().subscribe((was, now) -> applicationFocusChanged(now)));
    }

    private void unwatchFocus(Window window) {
        Subscription watch = focusWatches.remove(window);
        if (watch != null) {
            watch.unsubscribe();
        }
    }

    private void applicationFocusChanged(boolean focused) {
        if (focused) {
            if (applicationDeactivated) {
                applicationDeactivated = false;
                refresher.requestRefresh();
            }
        } else {
            // Focus may just be moving to another window of ours, which is only visible once that
            // window has taken it.
            Platform.runLater(() -> {
                if (Window.getWindows().stream().noneMatch(Window::isFocused)) {
                    applicationDeactivated = true;
                }
            });
        }
    }

    /**
     * Brings the open projects in line with what is on disk, on the refresh thread: first the ones
     * that are no longer there, which are closed rather than refreshed, then the rest.
     */
    private void refreshProjects(File[] roots) {
        rootWatcher.checkRoots();
        FileUtil.refreshFor(roots);
    }

    /** The root folders of the open projects, as the refresher needs them: plain files, off the FX thread. */
    private List<File> openProjectRoots() {
        List<File> dirs = new ArrayList<>();
        for (OpenProject project : List.copyOf(projectRegistry.getOpenProjects())) {
            dirs.add(new File(project.getPath()));
        }
        return dirs;
    }

    /** The navigator pane, once the navigator provider has created it. */
    void setNavigatorPane(NbfxTabPane pane) {
        navigatorPane = pane;
    }


    /**
     * The tab of the view identified by {@code viewId}: the live one, wherever it currently is, or a
     * freshly built one if that view has no tab. Navigators are looked up first, then every other
     * {@link ViewProvider} published in the Lookup. Returns {@code null} for an unknown view (one
     * persisted by an earlier run but no longer installed).
     */
    @Override
    protected Tab viewTabFor(String viewId) {
        return NbfxTabPane.findViewTab(viewId).orElseGet(() -> {
            for (NavigatorView provider : providers) {
                if (provider.getId().equals(viewId)) {
                    return createNavigatorTab(provider);
                }
            }
            ViewProvider view = viewById(viewId);
            return view == null ? null : ViewTabs.create(view, view.getDescription());
        });
    }

    /** The non-navigator view published under {@code viewId}, or {@code null}. */
    private static ViewProvider viewById(String viewId) {
        for (ViewProvider view : Lookup.getDefault().lookupAll(ViewProvider.class)) {
            if (view.getId().equals(viewId)) {
                return view;
            }
        }
        for (ViewSpec registration : ViewRegistry.discover()) {
            if (registration.id().equals(viewId)) {
                return registration.view();
            }
        }
        return null;
    }

    /** Builds a navigator tab for {@code provider}, whose tooltip names the shortcut selecting it. */
    Tab createNavigatorTab(NavigatorView provider) {
        return NavigatorPaneProvider.createTab(provider, providers);
    }

    /**
     * The navigator views: the {@link ViewProvider}s whose {@code @ViewRegistration} marks them as
     * navigators, ordered by their declared position. Resolved from the global {@code Lookup} - the
     * same service instances their tabs are built from.
     */
    private static List<NavigatorView> navigatorViews() {
        Map<String, Integer> positions = new HashMap<>();
        for (ViewSpec registration : ViewRegistry.discover()) {
            if (registration.navigator()) {
                positions.put(registration.id(), registration.position());
            }
        }
        List<NavigatorView> navigators = new ArrayList<>();
        for (ViewProvider view : Lookup.getDefault().lookupAll(ViewProvider.class)) {
            if (view instanceof NavigatorView navigator && positions.containsKey(view.getId())) {
                navigators.add(navigator);
            }
        }
        navigators.sort(Comparator.comparingInt(view -> positions.getOrDefault(view.getId(), Integer.MAX_VALUE)));
        return navigators;
    }

    /** The main pane's active-document observable, or {@code null} if no content manager is present. */
    private ObservableValue<EditorDocument> mainPaneActiveDocument() {
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        return contentManager != null ? contentManager.mainPaneActiveDocument() : null;
    }

    private ContentManager contentManager() {
        return Lookup.getDefault().lookup(ContentManager.class);
    }

    /** Builds the Window-menu actions and their context-driven enablement, scoped to the main editor pane. */
    private WorkspaceCommands.WindowActions windowActions(ObservableValue<EditorDocument> mainScope) {
        return new WorkspaceCommands.WindowActions(
                () -> selectNavigator(firstNavigatorId(0)),
                () -> selectNavigator(firstNavigatorId(1)),
                this::resetWindows);
    }

    /** The stable navigator id of the provider at {@code index} in natural order, or {@code null} if absent. */
    private String firstNavigatorId(int index) {
        List<NavigatorView> list = new ArrayList<>(providers);
        return index < list.size() ? list.get(index).getId() : null;
    }

    /** Shows the navigator tab for {@code providerId}: selects it or reopens it in the docked navigator pane. */
    private void selectNavigator(String providerId) {
        selectNavigator(providerId, true);
    }

    private void selectNavigator(String providerId, boolean focusTree) {
        if (providerId == null) {
            return;
        }
        for (TabPane pane : NbfxTabPane.tabPanes()) {
            for (Tab tab : pane.getTabs()) {
                if (providerId.equals(NbfxTabPane.viewId(tab))) {
                    NbfxTabPane.selectTabAndMoveToFront(tab);
                    Node content = tab.getContent();
                    if (focusTree && content != null) {
                        Platform.runLater(content::requestFocus);
                    }
                    return;
                }
            }
        }
        reopenNavigatorTab(providerId);
    }

    /** Recreates and selects a closed navigator tab in the docked navigator pane. */
    private void reopenNavigatorTab(String providerId) {
        Tab tab = viewTabFor(providerId);
        if (tab != null) {
            NbfxTabPane.attachTab(navigatorPane, tab);
            navigatorPane.getSelectionModel().select(tab);
        }
    }

    /**
     * Reveals {@code file} in the navigator provider at {@code index}, showing its tab first and
     * bringing the main window to the front.
     */
    void revealFileInNavigator(FileObject file, int index) {
        if (file == null) {
            return;
        }
        List<NavigatorView> list = new ArrayList<>(providers);
        if (index < 0 || index >= list.size()) {
            return;
        }
        NavigatorView provider = list.get(index);
        setNavigatorRevealInProgress(true);
        selectNavigator(provider.getId(), false);
        provider.revealFile(file);
        Platform.runLater(() -> focusNavigatorView(provider.getView()));
    }

    /**
     * Reveals {@code file} in the first navigator whose projects contain it, showing that view. This
     * backs the {@code NavigatorRevealer} service so the search-results UI can show a hit without
     * knowing about the navigators itself.
     */
    boolean revealFileInOwningNavigator(FileObject file) {
        if (file == null || providers == null || providers.isEmpty()) {
            return false;
        }
        if (projectRegistry.ownerOf(file) == null) {
            return false;
        }
        // Prefer the first navigator (the Projects view, in declared position order).
        revealFileInNavigator(file, 0);
        return true;
    }

    /**
     * Activates the main window and moves keyboard focus into the navigator {@code view}.
     * <p>
     * Focus may not land synchronously (activating the main window from a detached one is
     * asynchronous), so the reveal flag is cleared from a one-shot listener once focus actually
     * arrives, with an unconditional fallback on the next pulse. Leaving the flag set would
     * permanently stop {@link NbfxTabPane} from re-focusing the editor on window activation.
     */
    private void focusNavigatorView(Node view) {
        if (view == null) {
            setNavigatorRevealInProgress(false);
            return;
        }
        if (getStage() != null) {
            getStage().toFront();
            getStage().requestFocus();
        }
        ChangeListener<Boolean> onFocused = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> obs, Boolean was, Boolean isFocused) {
                if (isFocused) {
                    view.focusedProperty().removeListener(this);
                    setNavigatorRevealInProgress(false);
                }
            }
        };
        view.focusedProperty().addListener(onFocused);
        view.requestFocus();
        Platform.runLater(() -> {
            view.focusedProperty().removeListener(onFocused);
            setNavigatorRevealInProgress(false);
        });
    }

    /** The file of the editor visible in the main pane, falling back to the globally active document. */
    private FileObject activeEditorFile() {
        ContentManager cm = contentManager();
        FileObject file = cm != null ? cm.getActiveFile() : null;
        if (file != null) {
            return file;
        }
        EditorContext context = Lookup.getDefault().lookup(EditorContext.class);
        EditorDocument document = context != null ? context.getActiveDocument() : null;
        return document != null ? document.getFileObject() : null;
    }

    /** Focuses the main editor pane's selected tab so its caret blinks. */
    /**
     * Restores the default layout, the way NetBeans' Reset Windows puts every window back in its
     * default mode: the layout is first collapsed to the default arrangement of the navigator and
     * editor panes - the maximized pane restored, the minimized groups brought back, the detached
     * windows closed, every docked pane disposed of - and then each tab goes to its default place:
     * editors to the editor pane, the navigators to the navigator pane in registration order
     * (Projects selected), every other view to its registered dock location, which recreates the
     * panes below the two (the Navigator window below Projects, Output below the editors). Closed
     * default views are shown again.
     */
    private void resetWindows() {
        ContentManager cm = contentManager();
        TabPane mainPane = cm instanceof ContentManagerService cmi ? (TabPane) cmi.getMainPane() : null;
        if (mainPane == null) {
            return;
        }
        // 1. The declared first-run shape. Detached windows empty into the permanent panes first
        //    (where exactly does not matter yet); applyTree restores what is maximized or minimized
        //    and moves the docked panes' content into the editor pane.
        NbfxTabPane.redockAll(tab -> NbfxTabPane.documentOf(tab) != null ? mainPane : navigatorPane);
        DockTree tree = Docking.defaultTree();
        if (tree == null || !getDockArea().accepts(tree)) {
            tree = getDockArea().defaultTree();
        }
        getDockArea().applyTree(tree);
        // 2. Every tab to its default place. A view's registered area creates or reuses its pane
        //    now, in the declared shape, where it stays.
        ViewCatalog catalog = catalog();
        for (TabPane pane : permanentPanes()) {
            for (Tab tab : List.copyOf(pane.getTabs())) {
                TabPane home = NbfxTabPane.documentOf(tab) != null ? mainPane : homeOfView(tab, catalog);
                if (home != null && home != tab.getTabPane()) {
                    NbfxTabPane.moveTab(home, tab, home.getTabs().size());
                }
            }
        }
        // The navigators in registration order, Projects first and selected.
        int index = 0;
        for (NavigatorView provider : providers) {
            Tab tab = viewTabFor(provider.getId());
            if (tab != null) {
                NbfxTabPane.moveTab(navigatorPane, tab, index++);
            }
        }
        if (!navigatorPane.getTabs().isEmpty()) {
            navigatorPane.getSelectionModel().select(0);
        }
        showDefaultViews();
        Docking.logLayout("Reset Windows");
    }

    /**
     * Docks every view registered to {@linkplain ViewSpec#openAtStartup() open at start-up}
     * that has no tab yet at its default location, the way the Navigator is part of NetBeans' default
     * layout. A view that already has a tab, wherever it is, is left alone. The view shown is the one
     * published in the Lookup (see {@link #viewById}), as everywhere else, so a command of the view's
     * module that reaches its instance from the Lookup talks to the content on screen.
     */
    private void showDefaultViews() {
        try {
            ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
            if (manager == null) {
                return;
            }
            ViewCatalog catalog = catalog();
            for (ViewSpec registration : catalog.specs) {
                ViewProvider view = registration.openAtStartup() ? catalog.views.get(registration.id()) : null;
                if (view != null && !manager.isShowing(view)) {
                    manager.show(view, registration.area(), registration.position());
                }
            }
            getAppState().setIntroducedViews(defaultViewIds(catalog.specs));
        } finally {
            Docking.completeStartupLayout();
        }
    }

    /**
     * Docks the default views the user has never been offered - on the first run, all of them; after
     * an update that brings a new one, that one - and records them as introduced, so a view the user
     * closes afterwards stays closed. Called once the session's layout is in place, as applying a
     * layout removes the view tabs it does not mention. This is the shell-side counterpart of the
     * window system's start-up tabs ({@code WindowSystem.defaultState()}) while the layout itself
     * still lives in {@link AppState}/{@link SessionRestorer}.
     */
    private void introduceDefaultViews() {
        try {
            ViewCatalog catalog = catalog();
            Set<String> introduced = getAppState().getIntroducedViews();
            Set<String> defaults = defaultViewIds(catalog.specs);
            if (introduced.containsAll(defaults)) {
                return;
            }
            ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
            if (manager == null) {
                return;
            }
            for (ViewSpec registration : catalog.specs) {
                ViewProvider view = registration.openAtStartup() && !introduced.contains(registration.id())
                        ? catalog.views.get(registration.id()) : null;
                if (view != null && !manager.isShowing(view)) {
                    manager.show(view, registration.area(), registration.position());
                }
            }
            introduced.addAll(defaults);
            getAppState().setIntroducedViews(introduced);
        } finally {
            Docking.completeStartupLayout();
        }
    }

    /** The ids of the non-navigator views registered to open at start-up, in registration order. */
    private static Set<String> defaultViewIds(List<ViewSpec> specs) {
        Set<String> ids = new LinkedHashSet<>();
        for (ViewSpec registration : specs) {
            if (registration.openAtStartup() && !registration.navigator()) {
                ids.add(registration.id());
            }
        }
        return ids;
    }

    /** The permanent panes of the resolved composition, in composition order. */
    private List<TabPane> permanentPanes() {
        WindowComposition composition = Docking.composition();
        if (composition != null) {
            List<TabPane> panes = new ArrayList<>();
            for (WindowPane pane : composition.panes()) {
                NbfxTabPane resolved = Docking.primary(pane.role());
                if (resolved != null) {
                    panes.add(resolved);
                }
            }
            return panes;
        }
        return List.of(navigatorPane, getMainPane());
    }

    /**
     * The pane a view tab belongs in by default: the pane of its registered
     * {@link ViewSpec#area() area}; a navigator falls back to the navigator pane, and a view without
     * a registration falls back to its legacy {@link ViewProvider#getDefaultLocation() location}.
     */
    private TabPane homeOfView(Tab tab) {
        return homeOfView(tab, catalog());
    }

    private TabPane homeOfView(Tab tab, ViewCatalog catalog) {
        String id = NbfxTabPane.viewId(tab);
        if (id == null) {
            return navigatorPane;
        }
        TabPane home = ViewPlacement.homeFor(catalog.byId.get(id), catalog.views.get(id));
        return home != null ? home : navigatorPane;
    }

    /** One snapshot of the view registrations and providers for a placement operation. */
    private static ViewCatalog catalog() {
        List<ViewSpec> specs = ViewRegistry.discover();
        Map<String, ViewSpec> byId = new HashMap<>();
        for (ViewSpec spec : specs) {
            byId.put(spec.id(), spec);
        }
        Map<String, ViewProvider> views = new HashMap<>();
        for (ViewProvider view : Lookup.getDefault().lookupAll(ViewProvider.class)) {
            views.putIfAbsent(view.getId(), view);
        }
        for (ViewSpec spec : specs) {
            views.putIfAbsent(spec.id(), spec.view());
        }
        return new ViewCatalog(specs, byId, views);
    }

    private record ViewCatalog(List<ViewSpec> specs, Map<String, ViewSpec> byId,
            Map<String, ViewProvider> views) {
    }

    /**
     * Sets the {@code dock-focused} styling on the docked pane that currently owns keyboard
     * focus, and tracks whether that focus is in a navigator view. Focus outside the dock area (in
     * the menu or tool bars) leaves the styling as it was.
     */
    private void updateFocusScope(Node focusOwner) {
        // The sliding area holds the dock area and the panel a minimized tab slides in to.
        if (isWithin(focusOwner, getSlidingArea())) {
            getDockArea().markFocused(true, focusOwner);
            treeFocused.set(isNavigatorFocused(focusOwner));
        }
    }

    /**
     * Whether {@code focusOwner} sits inside one of the navigator views.
     * <p>
     * This drives which target Cut/Copy/Paste act on, so it must test the view itself and not the
     * pane holding it: tabs can be dragged between panes, so an editor may well live in the
     * navigator pane (where treating it as the tree would cut the file selected in the tree instead
     * of the editor's selection) and a navigator view in the editor pane.
     */
    private boolean isNavigatorFocused(Node focusOwner) {
        if (focusOwner == null || providers == null) {
            return false;
        }
        for (NavigatorView provider : providers) {
            if (isWithin(focusOwner, provider.getView())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The file selected in the navigator view that holds {@code focusOwner}, or {@code null} if the
     * focus is not inside a navigator tree. Read from the focused provider rather than from the
     * shared selection so the Logical and Physical views do not answer for each other.
     */
    private FileObject focusedNavigatorFile(Node focusOwner) {
        if (focusOwner == null || providers == null) {
            return null;
        }
        for (NavigatorView provider : providers) {
            if (isWithin(focusOwner, provider.getView())) {
                return provider.selectedFile().getValue();
            }
        }
        return null;
    }

    private static boolean isWithin(Node node, Node ancestor) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }

    void setLogging() {
        System.setProperty("netbeans.logger.console", "true");
        java.util.logging.Logger rootLog = java.util.logging.Logger.getLogger("");
        rootLog.getHandlers()[0].setLevel(java.util.logging.Level.ALL);
        java.util.logging.Logger.getLogger("org.openide.util.Lookup").setLevel(java.util.logging.Level.FINEST);
        java.util.logging.Logger.getLogger("org.openide.util.lookup.MetaInfServicesLookup").setLevel(java.util.logging.Level.FINEST);
        java.util.logging.Logger.getLogger("org.netbeans.core.startup").setLevel(java.util.logging.Level.FINEST);
    }

    /** Builds the bars for a detached window with its own document scope. */
    Region createDetachedBars(DetachedBarsContext context) {
        return DetachedBars.create(context, mainToolBars);
    }

    /** The pane a tab docks back into by default. */
    TabPane homeOf(Tab tab) {
        if (NbfxTabPane.documentOf(tab) != null) {
            TabPane main = contentManager() instanceof ContentManagerService cmi
                    ? (TabPane) cmi.getMainPane() : null;
            return main == null ? null
                    : NbfxTabPane.editorDockHome(main, Lookup.getDefault().lookup(EditorContext.class));
        }
        return homeOfView(tab);
    }

    /** The status bar progress service, or {@code null} if not registered. */
    private static Progress progress() {
        return Lookup.getDefault().lookup(Progress.class);
    }

    /** Whether a navigator reveal is in progress, so panes do not steal focus back. */
    boolean isNavigatorRevealInProgress() {
        return navigatorRevealInProgress;
    }

    /** Marks a navigator reveal as in progress (or finished), so panes do not steal focus back. */
    void setNavigatorRevealInProgress(boolean inProgress) {
        navigatorRevealInProgress = inProgress;
    }

    /** The registry of open projects, resolved from the Lookup or a local instance if the service is not registered. */
    private static ProjectRegistry projectRegistry() {
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        if (registry == null) {
            LOG.warning("No ProjectRegistry found in the Lookup; using a local instance");
            registry = new EmptyProjectRegistry();
        }
        return registry;
    }

    private static FileSelectionContext fileSelectionContext() {
        FileSelectionContext context = Lookup.getDefault().lookup(FileSelectionContext.class);
        if (context == null) {
            LOG.warning("No FileSelectionContext found in the Lookup; using a local instance");
            context = new FileSelectionContextService();
        }
        return context;
    }

    /**
     * Selects the project owning {@code document}, so that moving to another editor tab moves the
     * project-scoped actions (Close Project, Save Project, file Undo/Redo) with it. Documents that
     * belong to no open project leave the selection alone.
     */
    private void selectProjectOf(EditorDocument document) {
        if (document == null) {
            return;
        }
        // Resolved from the file rather than from the document's own project path, so a document
        // that was created before its project was registered still finds its owner.
        selectProjectOf(document.getFileObject());
    }

    /** Selects the project owning {@code file}; files outside every open project change nothing. */
    private void selectProjectOf(FileObject file) {
        if (file == null) {
            return;
        }
        OpenProject owner = projectRegistry.ownerOf(file);
        if (owner != null) {
            projectRegistry.select(owner);
        }
    }

    /** The directories of every open project, in the order they were opened. */
    private List<File> openProjectFiles() {
        return projectRegistry.getOpenProjects().stream()
                .map(project -> new File(project.getPath()))
                .toList();
    }

    /**
     * Captures the tree state of every open project, plus the layout of the whole session. Quitting
     * while the last session is still being reopened leaves it untouched: a half-restored window
     * would otherwise overwrite it with the tabs that did make it back.
     */
    @Override
    protected void captureSession() {
        if (restoringSession) {
            LOG.info("The session is still being restored; keeping the persisted one");
            return;
        }
        getSessionRestorer().captureSession(openProjectFiles(), providers);
        persistSession();
    }

    /**
     * Records which projects are open and which one is selected, so the next launch reopens this
     * same session. Called whenever that set changes, not only at exit.
     */
    private void persistSession() {
        OpenProject selected = projectRegistry.getSelected();
        projectState.setOpenProjects(
                projectRegistry.getOpenProjects().stream().map(OpenProject::getPath).toList(),
                selected == null ? null : selected.getPath());
    }

    /**
     * Opens {@code dir} <em>alongside</em> the projects already open: every navigator view gains a
     * root for it and its tree is built in the background, while the other projects keep their trees,
     * their editors and their own loading. Reopening an already open project just selects it.
     */
    void openProject(File dir) {
        if (dir == null || !dir.isDirectory()) {
            LOG.warning("Project directory does not exist: " + dir);
            if (dir != null) {
                projectState.removeRecentProject(dir.getPath());
            }
            return;
        }
        // Normalized so the directory, the FileObject and the persisted state all agree on one path.
        File projectDir = FileUtil.normalizeFile(dir);
        FileObject rootFileObject = FileUtil.toFileObject(projectDir);
        if (rootFileObject == null) {
            LOG.warning("Can't create a rootFoContext for " + projectDir);
            return;
        }
        String path = OpenProject.pathOf(rootFileObject);
        OpenProject alreadyOpen = projectRegistry.find(path);
        if (alreadyOpen != null) {
            LOG.info("Project already open, selecting it: " + path);
            projectRegistry.select(alreadyOpen);
            return;
        }
        if (loads.isLoading(path)) {
            LOG.info("Project already loading: " + path);
            return;
        }
        // Nesting is allowed (NetBeans does the same), but ownership is then resolved to the nearest
        // root, so both projects showing the same files is worth a warning.
        OpenProject enclosing = projectRegistry.ownerOf(rootFileObject);
        if (enclosing != null) {
            LOG.warning("Opening " + path + " inside the already open project " + enclosing.getPath());
        }
        if (providers == null || providers.isEmpty()) {
            LOG.warning("No ViewProviders found");
            return;
        }
        // Read with the normalized path, the same key the state was captured under, and stash the
        // pending expansion/selection on the views before opening: they mirror the registry and build
        // a project's tree as soon as it is opened, applying this pending state.
        List<String> expandedPaths = projectState.getExpandedNodes(projectDir.getPath());
        String selectedPath = projectState.getSelectedNode(projectDir.getPath());
        for (NavigatorView provider : providers) {
            provider.restoreExpandedPaths(rootFileObject, expandedPaths);
            provider.restoreSelectedPath(rootFileObject, selectedPath);
        }
        loads.begin(path, projectDir);
        updateLoadingProgress();
        // Opening the registry is what makes the views build the tree (they observe it).
        projectRegistry.open(rootFileObject);
    }

    /** The running application, or {@code null} before it starts. */
    public static JavaFXLaunchApp instance() {
        return instance;
    }

    /** Opens the project rooted at {@code root} (the full flow: navigators and registry). */
    public void openProject(FileObject root) {
        File file = root == null ? null : FileUtil.toFile(root);
        if (file != null) {
            openProject(file);
        }
    }

    /**
     * Shows the status bar's progress for the projects being loaded, or hides it once none is
     * loading. Projects load in parallel, so a single one is reported by name and its cancel button
     * cancels that load; when several are loading at once, naming any one of them would be
     * arbitrary, so they are reported together and cancelling cancels all of them. The background
     * scan that follows is reported by the progress API itself, so it needs no watcher here.
     */
    private void updateLoadingProgress() {
        Progress progress = progress();
        if (progress == null) {
            return;
        }
        File current = loads.current();
        if (current == null) {
            if (loadProgress != null) {
                loadProgress.finish();
                loadProgress = null;
            }
            return;
        }
        int loading = loads.size();
        String message = loading > 1
                ? NbBundle.getMessage(JavaFXLaunchApp.class, "StatusBar.openingProjects", loading)
                : NbBundle.getMessage(JavaFXLaunchApp.class, "StatusBar.openingProject", current.getName());
        // Stable callback: when the number of loading projects changes, cancel the right set.
        Runnable cancel = () -> {
            if (loads.size() > 1) {
                cancelAllProjectLoading();
            } else {
                cancelProjectLoading(loads.currentPath());
            }
        };
        if (loadProgress == null) {
            loadProgress = progress.start(message, cancel);
        } else {
            loadProgress.setMessage(message);
        }
    }

    /**
     * Cancels the loading of the project at {@code path}, if it is still loading, and drops the
     * partially built tree it left in the navigator views. Every other project is untouched.
     */
    private void cancelProjectLoading(String path) {
        File dir = loads.dirOf(path);
        if (dir == null) {
            return;
        }
        // Closing the registry entry makes the views stop their load and drop the tree (they observe
        // the registry); a failed or cancelled load therefore leaves no partial root behind.
        OpenProject project = projectRegistry.find(path);
        if (project != null) {
            projectRegistry.close(project);
        }
        loads.end(path);
        updateLoadingProgress();
        // A cancelled load never reports back, so the session must stop waiting for it: the projects
        // that did load still get their editors and their arrangement back.
        sessionProjectSettled(path);
    }

    /** Cancels every project still loading. */
    private void cancelAllProjectLoading() {
        for (String path : loads.paths()) {
            cancelProjectLoading(path);
        }
    }

    /**
     * Invoked on the FX thread once a project's tree has loaded. Registers it as the (now selected)
     * open project, records it as the most recent project together with its resolved icon name, and
     * reopens the editors it was left with.
     */
    private void onProjectLoaded(FileObject rootFileObject) {
        String path = OpenProject.pathOf(rootFileObject);
        loads.end(path);
        updateLoadingProgress();
        OpenProject project = projectRegistry.find(path);
        if (project == null) {
            // Closed while it was still loading: nothing to record.
            sessionProjectSettled(path);
            return;
        }
        File dir = new File(path);
        // The project was registered when it was opened (the views observe the registry), so its
        // editors can already resolve their owning project; only the post-load bookkeeping is left.
        rootWatcher.watch(project);
        ProjectIconProvider icons = Lookup.getDefault().lookup(ProjectIconProvider.class);
        String iconName = icons == null ? null : icons.iconName(rootFileObject);
        projectState.addRecentProject(dir.getPath(), iconName);
        persistSession();
        // The persisted layout describes the whole window across every project of the session, so it
        // is applied once - when the last of them has loaded. A project opened later comes up empty:
        // the tabs it had are only restored as part of the session they were captured in.
        sessionProjectSettled(path);
    }

    /**
     * Invoked on the FX thread when a project's tree could not be built: the directory is not a
     * project, or reading it failed. Stops waiting for it and drops the partial root the views may
     * already show, so the user can fix the project and open it again.
     */
    private void onProjectLoadFailed(FileObject rootFileObject) {
        String path = OpenProject.pathOf(rootFileObject);
        if (!loads.isLoading(path)) {
            return;
        }
        LOG.warning("Project could not be loaded: " + path);
        cancelProjectLoading(path);
        // A project of the session that never loads must not hold the layout restore back.
        sessionProjectSettled(path);
    }

    /**
     * Closes the selected project without closing the application: confirms its unsaved changes,
     * closes its editor tabs, removes it from the navigator views and drops its persisted state. The
     * other open projects keep their trees, tabs and state.
     * <p>
     * Closing a project resets all persisted state for it, so it is not reopened on the next launch and,
     * if picked from Recent Projects, opens fresh at the project root.
     */
    void closeProject(OpenProject project) {
        closeProject(project, true);
    }

    /**
     * Closes {@code project}, asking about its unsaved changes only when {@code confirmUnsaved} is
     * set: a project whose folder has just disappeared from disk cannot be saved anywhere, so it is
     * closed without a question that has no good answer.
     */
    private void closeProject(OpenProject project, boolean confirmUnsaved) {
        if (project == null) {
            return;
        }
        String path = project.getPath();
        if (confirmUnsaved) {
            List<EditorDocument> documents = ProjectEditorScope.documentsOf(path);
            if (!documents.isEmpty() && !CloseConfirmation.confirmClose(documents)) {
                return;
            }
        }
        rootWatcher.unwatch(path);
        // Closing a project of the session being restored means the user has taken over: the
        // previous arrangement is no longer what they want back.
        abortSessionRestore();
        // A project still loading stops loading now: nothing should keep building for a closed project.
        cancelProjectLoading(path);
        ProjectEditorScope.closeFiles(path);
        // Closing the registry entry makes the views drop the project's tree (they observe it).
        projectRegistry.close(project);
        // Only this project's file-operation history goes: the other projects keep theirs.
        FileUndoManager.getDefault().clear(path);
        projectState.clearProjectState(new File(path));
        persistSession();
    }

    /**
     * Closes every open project without closing the application. Same contract as
     * {@link #closeProject(OpenProject)}, applied to all of them: a single unsaved-changes confirmation, then
     * each project's navigator root and persisted state are dropped.
     */
    void closeAllProjects() {
        List<OpenProject> projects = List.copyOf(projectRegistry.getOpenProjects());
        if (projects.isEmpty()) {
            return;
        }
        EditorContext context = Lookup.getDefault().lookup(EditorContext.class);
        List<EditorDocument> documents = context != null
                ? List.copyOf(context.getDocuments()) : List.of();
        if (!documents.isEmpty() && !CloseConfirmation.confirmClose(documents)) {
            return;
        }
        abortSessionRestore();
        rootWatcher.unwatchAll();
        cancelAllProjectLoading();
        ContentManager contentManager = Lookup.getDefault().lookup(ContentManager.class);
        if (contentManager != null) {
            contentManager.closeAll();
        }
        // File undo history refers to paths inside the closed projects; drop it.
        FileUndoManager.getDefault().clear();
        // Closing the registry makes the views drop every project's tree (they observe it).
        projectRegistry.closeAll();
        for (OpenProject project : projects) {
            projectState.clearProjectState(new File(project.getPath()));
        }
        persistSession();
    }

    /**
     * Invoked off the FX thread when an open project's root folder is deleted or renamed on disk:
     * the project is closed as if the user had closed it, dropping its tree, its tabs and its
     * persisted state - it can no longer be reopened at that path.
     */
    private void projectRootGone(OpenProject project) {
        Platform.runLater(() -> {
            if (projectRegistry.find(project.getPath()) == null) {
                return;
            }
            closeProject(project, false);
            projectState.removeRecentProject(project.getPath());
            });
    }

    /** Selects and reveals the next open project, wrapping around. */
    void selectNextProject() {
        projectSwitcher.next();
    }

    /** Selects and reveals the previous open project, wrapping around. */
    void selectPreviousProject() {
        projectSwitcher.previous();
    }

    private void clearRecentProjects() {
        projectState.clearRecentProjects();
    }

    /**
     * Reveals {@code project}'s root in the navigator and moves the focus there, so switching
     * project from the Window menu or its shortcut lands the user in that project's tree.
     * <p>
     * The reveal happens in the navigator view the user is currently looking at (Projects, Files,
     * or whichever is selected), falling back to the first one when the navigator pane holds none.
     */
    private void revealProject(OpenProject project) {
        NavigatorView provider = project == null ? null : selectedViewProvider();
        if (provider == null) {
            return;
        }
        setNavigatorRevealInProgress(true);
        selectNavigator(provider.getId(), false);
        provider.revealFile(project.getRoot());
        Platform.runLater(() -> focusNavigatorView(provider.getView()));
    }

    /** The provider of the navigator tab currently selected in the navigator pane, or the first one. */
    private NavigatorView selectedViewProvider() {
        List<NavigatorView> list = new ArrayList<>(providers);
        if (list.isEmpty()) {
            return null;
        }
        Tab selected = navigatorPane == null ? null : navigatorPane.getSelectionModel().getSelectedItem();
        String id = selected == null ? null : NbfxTabPane.viewId(selected);
        if (id != null) {
            for (NavigatorView provider : list) {
                if (id.equals(provider.getId())) {
                    return provider;
                }
            }
        }
        return list.get(0);
    }

    /** The icon of an open project, resolved from its root through the {@link ProjectIconProvider}. */
    private Node projectIcon(OpenProject project) {
        if (project == null) {
            return null;
        }
        ProjectIconProvider icons = Lookup.getDefault().lookup(ProjectIconProvider.class);
        return icons == null ? null : icons.createIcon(project.getRoot());
    }

    private Node projectIcon(File dir, String iconName) {
        if (dir == null || iconName == null) {
            return null;
        }
        FileObject fo = FileUtil.toFileObject(dir);
        if (fo == null) {
            return null;
        }
        ProjectIconProvider icons = Lookup.getDefault().lookup(ProjectIconProvider.class);
        return icons == null ? null : icons.createIcon(fo, iconName);
    }

    /**
     * Reopens every project of the last session, in the order they were opened. Each one loads on its
     * own; the window layout - which spans all of them - is applied by
     * {@link #sessionProjectSettled(String)} once the last has finished. Projects whose directory is
     * gone are pruned.
     */
    private void restoreSessionProjects() {
        List<String> projects = projectState.getOpenProjects();
        if (projects.isEmpty()) {
            introduceDefaultViews();
            return;
        }
        sessionSelected = projectState.getSelectedProject();
        List<File> dirs = new ArrayList<>();
        for (String path : projects) {
            File dir = FileUtil.normalizeFile(new File(path));
            if (dir.isDirectory()) {
                dirs.add(dir);
                pendingSessionProjects.add(dir.getPath());
            } else {
                LOG.info("Project of the last session no longer exists, pruning: " + path);
                projectState.removeRecentProject(path);
            }
        }
        if (dirs.isEmpty()) {
                introduceDefaultViews();
            persistSession();
            return;
        }
        restoringSession = true;
        LOG.info("Reopening the last session: " + pendingSessionProjects);
        for (File dir : dirs) {
            openProject(dir);
            // A project whose load never started (a directory that is no longer a project, or no
            // navigator at all) would otherwise keep the session waiting for a load that will never
            // report back.
            if (!loads.isLoading(dir.getPath())) {
                sessionProjectSettled(dir.getPath());
            }
        }
    }

    /**
     * Gives up on restoring the last session, keeping whatever is already on screen. Used when the
     * user closes projects while it is still coming back.
     */
    private void abortSessionRestore() {
        if (restoringSession) {
            LOG.info("Session restore abandoned: a project was closed while it was still loading");
        }
        restoringSession = false;
        pendingSessionProjects.clear();
        sessionSelected = null;
    }

    /**
     * Notes that a project of the restored session has finished loading (or failed to). Once they all
     * have, the session's layout is applied and the project that was selected at exit is reselected.
     */
    private void sessionProjectSettled(String path) {
        if (!restoringSession || !pendingSessionProjects.remove(path) || !pendingSessionProjects.isEmpty()) {
            return;
        }
        restoringSession = false;
        String selected = sessionSelected;
        sessionSelected = null;
        getSessionRestorer().restoreSession(focused -> {
            // The tab the session comes back focused on decides the project, exactly as moving to a
            // tab does while the app runs. The project that was selected at exit only applies when
            // the focus does not land on an editor of an open project (an empty or navigator pane).
            OpenProject owner = focused == null ? null
                    : projectRegistry.ownerOf(focused.getFileObject());
            if (owner == null && selected != null) {
                owner = projectRegistry.find(selected);
            }
            if (owner != null) {
                projectRegistry.select(owner);
            }
            introduceDefaultViews();
            persistSession();
            Docking.completeStartupLayout();
        });
    }


    /** Minimal registry used only when no {@link ProjectRegistry} service is registered. */
    private static final class EmptyProjectRegistry implements ProjectRegistry {

        private final ObservableList<OpenProject> projects = FXCollections.observableArrayList();
        private final SimpleObjectProperty<OpenProject> selected = new SimpleObjectProperty<>();

        @Override
        public ObservableList<OpenProject> getOpenProjects() {
            return FXCollections.unmodifiableObservableList(projects);
        }

        @Override
        public javafx.beans.property.ReadOnlyObjectProperty<OpenProject> selectedProjectProperty() {
            return selected;
        }

        @Override
        public OpenProject getSelected() {
            return selected.get();
        }

        @Override
        public void select(OpenProject project) {
            selected.set(project);
        }

        @Override
        public OpenProject find(String path) {
            return null;
        }

        @Override
        public OpenProject ownerOf(FileObject file) {
            return null;
        }

        @Override
        public OpenProject open(FileObject root) {
            return null;
        }

        @Override
        public void close(OpenProject project) {
        }

        @Override
        public void closeAll() {
            projects.clear();
            selected.set(null);
        }
    }
}
