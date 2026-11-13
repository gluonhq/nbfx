<!--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->
# AppShell Hardcoding Removal Plan

Status: design + implementation notes. The status list is [`PLAN.md`](PLAN.md) §18; the API-reality baseline
is §6.1.1 and the workstream is §11 W2a/W3a/W3b.
Scope: `nbfx.platform.shell`, `nbfx.platform.menus`, `nbfx.platform.status`, `nbfx.platform.actions`, `nbfx.ide.workbench`  
Goal: a downstream product can use `ShellApplication` as-is and add menus, toolbars, status elements, application-menu entries, branding and window behaviour through registrations. It must not need to copy `ActionBars`, implement its own `WindowEnvironment`, or write its own application class just to get standard chrome.

---

## 1. Non-goals

- Redesigning the docking model (`DockArea`, `DockTree`, `ViewArea`). Tracked separately.
- Moving IDE feature behaviour (projects, builds, debug, VCS) into the platform.
- Replacing JavaFX or introducing a neutral toolkit abstraction.
- Changing the application entry point or `AppProvider` model.

---

## 2. Resolved design decisions

1. **No separate dynamic-menu root.** Dynamic entries live at the point of reference:
   `NbFx/Menus/<menu>/<id>.dynamic`, not `NbFx/DynamicMenus/...`.
2. **Menu identity and ordering come from the layer registration.** `@DynamicMenuRegistration` supplies `path`, `id`, `position`, `displayName`, `separatorBefore`; the factory supplies only creation.
3. **Factories are stateless; per-shell content is returned by `create(Lookup)`.**
4. **`EditorDocument` is not part of the shell chrome API/SPI.** Contribution contexts expose a `Lookup`/`ActionContext`; editor-specific contributions resolve `EditorDocument` themselves.
5. **`ShellChrome` is a composition contract**, not a second implementation: `Node top()`, `Node bottom()`, `void dispose()`. Concrete menu/toolbar/status controls stay inside `DefaultShellChrome`.
6. **Status bar ownership:** `platform.status` + `api.status` own the renderer; `platform.shell` composes it.
7. **Menu/toolbar ownership:** `platform.menus` owns registrations and generic menu vocabulary; `platform.shell` owns the JavaFX builder; `ide.workbench` owns IDE entries.
8. **Toolbar persistence:** `platform.shell.AppState` already persists arrangement/visibility; `ShellApplication` calls the existing API and introduces no new persistence layer.
9. **Icon source:** `Command.getIcon()` is canonical. Remove `ActionBars.ICONS`; shell-owned commands get icons from their `Command` implementation or one `ShellIconProvider` during the transition. The `api.actions.Command` javadoc exception is removed under W2.
10. **`EditorDocument` and project state are not generic shell API.** `RecentProject` moves to the IDE or becomes a generic `RecentItem`; `WindowTitles` moves behind `ShellBranding`.
11. **One `WindowEnvironment` service** with ordered contributions; not competing providers.
12. **One shell per JVM** unless a product supplies an explicit shell-scoped provider; this is documented.

---

## 3. Current state

### 3.1 `ShellApplication`

Provides window lifecycle, `WindowSystem` sink, session capture/restore, close confirmation, primary pane creation, and the hooks `prepare`, `createPanes`, `decorate`, `sceneReady`, `started`, `restoreSession`, `captureSession`, `stopping`.

Missing:

- default menu bar;
- tool bars;
- status bar;
- application menu installation;
- branding/title;
- `WindowEnvironment`;
- generic window/editor actions.

A downstream product either subclasses `ShellApplication` or depends on `ide.workbench`/`ActionBars`. Both are wrong for reuse.

### 3.2 `JavaFXLaunchApp`

The IDE main window now uses `DefaultShellChrome`. What remains in the launcher is:

- `ActionBars` only for detached-window bars (transitional);
- `WindowEnvironmentService` wiring for navigator/home/reveal and the detached-bar bridge;
- `FileUndoManager` scope on project selection;
- IDE `ShellCommandResolver`, `ShellIconProvider`, dynamic-menu factories and command registration.

The launcher no longer builds the main `VBox(menuBar, toolBars)`, creates the status elements or `StatusBar`, sets the title from `WindowTitles`, or installs `ApplicationMenu`.

### 3.3 `ActionBars`

Retained only as the detached-window builder for now. Its main-window special cases (`File`/`Edit`/`View`/`Window`, fallback lists, recent/open projects, icon map) are no longer used by the main window; they are replaced by:

- layer `@ActionReference` entries;
- platform `ViewMenuFactory` and `ConfigureWindowMenuFactory`;
- IDE `RecentProjectsMenuFactory` and `OpenProjectsMenuFactory`;
- IDE `IdeShellCommandResolver` and `IdeShellIconProvider`.

The remaining detached-bar path is the last `ActionBars` user and is the next removal target.

### 3.4 Existing platform registries

- `@ActionRegistration`, `@ActionReference`, `ActionLayerReader`.
- `@MenuRegistration` → `NbFx/<path>/<id>` folder with `position`/`displayName`/`separatorBefore`.
- `@ToolbarRegistration` → `NbFx/Toolbars/<id>`.
- `@StatusRegistration` → `NbFx/Status/<id>.instance` with alignment/position.
- `ActionRegistry` / `Command`.
- `ToolBarContainer`, `AppState.initToolbars`.
- `StatusBar` and `StatusElementRegistry`.

---

## 4. Target architecture

```
ShellApplication
 ├─ ShellChrome (top/bottom/dispose only)
 │   └─ DefaultShellChrome (implementation)
 │       ├─ ShellMenuBar      (NbFx/Menus + DynamicMenu.Factory)
 │       ├─ ShellToolBars     (NbFx/Toolbars + ToolBarContainer)
 │       └─ status composition(platform.status.StatusBar)
 ├─ ShellBranding             (app name, title, icon)
 ├─ ShellActions              (generic window/editor commands)
 ├─ DefaultWindowEnvironment  (detached bars, home; contribution factories)
 └─ ApplicationMenu installer
```

Product contributions:

- static: `@ActionRegistration`, `@MenuRegistration`, `@ActionReference`, `@ToolbarRegistration`, `@StatusRegistration`;
- dynamic menu sections: `@DynamicMenuRegistration` + `DynamicMenu.Factory`;
- chrome replacement: `ShellChromeProvider` (or `ShellApplication.createChrome`);
- branding/title: `ShellBranding`;
- detached-window behaviour: `DetachedBarsFactory`/`WindowEnvironmentContribution.Factory`;
- status: registered `StatusElementHolder`s.

`ShellApplication` remains concrete; subclassing is only for a different window layout/lifecycle, not for standard chrome.

---

## 5. API sketches

### 5.1 `ShellChrome`

```java
public interface ShellChrome {
    Node top();
    Node bottom();
    void dispose();
}
```

`top()`/`bottom()` may be `null`. Concrete `MenuBar`/`ToolBarContainer`/`StatusBar` do not appear in the product contract; `DefaultShellChrome` can expose them internally for tests.

```java
public interface ShellChromeProvider {
    ShellChrome create(ShellContext context);
}
```

`ShellApplication.createChrome(ShellContext)` is the primary override; provider lookup is a fallback with deterministic selection.

### 5.2 `ShellContext`

No editor or shell internals:

```java
public record ShellContext(Lookup lookup) {
    ActionRegistry actions();
    ActionContext actionContext();
    Command command(String id);
    Command command(String id, ObservableValue<Boolean> preferFile);
    Node icon(Command command, double size);
}
```

`ActionContext` is a `Lookup`, so a contribution that needs an `EditorDocument` does `context.actionContext().lookup(EditorDocument.class)`; that contribution is editor-specific. No `EditorDocument`, `AppState`, `DockArea`, `SlidingArea` or `NbfxTabPane` in the generic context. `command`/`icon` delegate to optional `ShellCommandResolver`/`ShellIconProvider` services in the same lookup, then to the registry/`Command.getIcon()`.

### 5.3 Dynamic menus

Menus are declared with `@MenuRegistration`; dynamic sections are declared at the point of reference.

```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(DynamicMenuRegistrations.class)
public @interface DynamicMenuRegistration {
    String path();            // "Menus/File", "Menus/Window/IDE Tools"
    String id();              // stable section id
    int position();
    String displayName() default "";
    boolean separatorBefore() default false;
}
```

The annotation processor writes:

```text
NbFx/Menus/Window/open-projects.dynamic
    factory=com.example.OpenProjectsMenuFactory
    position=300
    separatorBefore=true
```

- The parent folder is the menu id.
- `.dynamic` is the type marker; `.ref` remains a static action reference.
- No root-level dynamic menu in the first increment. A menu with only dynamic content is declared with `@MenuRegistration` and given one dynamic section.
- `ActionLayerReader.read` ignores `.dynamic`; `hasContent`/submenu detection must count `.dynamic` entries so a submenu containing only dynamic sections is still shown.

Factory API:

```java
public interface DynamicMenu {

    interface Factory {
        DynamicMenu create(Lookup context);
    }

    ObservableList<MenuItem> items();

    default void dispose() {
    }
}
```

- A factory may be registered via `@ServiceProvider(service = DynamicMenu.Factory.class)`, or supplied per shell in the `ShellContext` lookup (needed when it has launcher state, as the IDE recent/open-project factories do).
- Factory has no `menuId()`/`position()`; layer metadata is authoritative.
- `create` is called once per menu instance/per shell.
- Returned instance owns listeners and is disposed with the menu bar.
- Missing/failing factory: log and skip; do not break the menu.

`ShellMenuBar`:

1. Read `.ref` entries in each `NbFx/Menus/<menu>` folder.
2. Read `.dynamic` entries in the same folder.
3. Read child submenu folders in the same folder, and merge entries and submenus by
   `position`; tie-break by id/class name.
4. Resolve the factory from the `ShellContext` lookup by the `factory` class name.
5. Call `create(scopedLookup)`, add `items()`, listen to the observable list.
6. Keep the menu and submenu instances stable; a section change refills them in place, without recreating sibling menus or factories.
7. Dispose the dynamic instances when the menu bar/shell closes.

No `File`/`Edit`/`View`/`Window` switch.

### 5.4 Tool bars

`ShellToolBars`:

- reads `NbFx/Toolbars`;
- builds `ToolBarContainer`;
- no fallback `file`/`clipboard`/`edit` bars;
- labels from `@ToolbarRegistration.displayName`;
- a bar with no resolvable commands is omitted, so its drag handle cannot make it look populated;
- generic drag handle;
- `AppState.initToolbars` persists arrangement/visibility.

### 5.5 Icons and labels

- `Command.getIcon()` is canonical.
- `ShellIconProvider` is implemented as the transitional home for shell-owned command icons; the IDE provides it from its resources.
- Commands without an icon render by text.
- Menu/toolbar labels come from `@MenuRegistration.displayName`/`@ToolbarRegistration.displayName`/`Command.getText()` and the registering module’s bundle.
- W2 updates `api.actions.Command` javadoc to remove the shell-command exception.

### 5.6 Status

`platform.status` owns `StatusBar`; `api.status` owns `StatusElement`/`@StatusRegistration`. `ShellStatusComposer` in `platform.shell` only composes:

```java
StatusBar statusBar = new StatusBar(StatusElementRegistry.discover());
```

- No `ShellStatusBar` renderer.
- `CaretStatusElement`/`LineSeparatorStatusElement` are platform `@StatusRegistration`s; they resolve the active document via `EditorContexts` and own their subscriptions in implementation.
- `ProjectStatusElement` is an IDE `@StatusRegistration` that follows `ProjectRegistry` on its own.
- No `EditorDocument` in status SPI.

### 5.7 `ShellBranding`

```java
public interface ShellBranding {
    default int priority() { return 0; }
    String applicationName();
    String title(ShellContext context);
    Node icon(ShellContext context);
    default ObservableValue<String> titleProperty(ShellContext context) { return null; }
}
```

Default neutral title; IDE `ProjectBranding` (priority 100) supplies a live project-aware title. `ShellApplication` selects the highest-priority branding, sets the initial title and follows `titleProperty` when present.

### 5.8 `ShellActions`

Generic shell actions:

- close document / all / others (`DocumentCloser`);
- reset windows;
- select editor;
- configure window;
- no fake Save/SaveAll commands; only register commands whose providers exist;
- platform-owned ids only; IDE project commands remain IDE.

### 5.9 `DefaultWindowEnvironment`

One `WindowEnvironment` service:

- `createDetachedBars`: one `DetachedBarsFactory` (or ordered contribution factories) using `ShellMenuBar`/`ShellToolBars`;
- `homeOf`: generic resolution;
- `revealInNavigator`/project switching: default false, IDE contribution wins;
- `isNavigatorRevealInProgress`/setter: default state.

```java
public interface DetachedBarsFactory {
    Region create(Lookup context, List<Command> scopedCommands);
}
```

No `ObservableValue<EditorDocument>` in the new factory. The existing `WindowEnvironment.createDetachedBars(ObservableValue<EditorDocument>, ...)` is legacy debt; do not extend it.

### 5.10 Application menu

`ShellApplication.started()` looks up `ApplicationMenu` and installs it with:

- preferences → `ActionIds.SELECT_OPTIONS` if registered;
- quit → `requestQuit()`.

IDE no longer installs it.

---

## 6. Module placement

| Module | Responsibility |
|---|---|
| `nbfx.api.actions` | `Command`, `ActionRegistry`, `ActionLayerReader`, `ActionRef` |
| `nbfx.api.views` | `@MenuRegistration`, `@ToolbarRegistration`, `@DynamicMenuRegistration`, `DynamicMenu`, `ShellChrome`/`ShellChromeProvider` if public |
| `nbfx.api.status` | `StatusElement`, `@StatusRegistration` |
| `nbfx.platform.actions` | action registry, keymap, scoped commands |
| `nbfx.platform.status` | `StatusBar`, `StatusElementRegistry` |
| `nbfx.platform.menus` | menu/toolbar registrations and generic menu vocabulary; no JavaFX builder |
| `nbfx.platform.shell` | `DefaultShellChrome`, `ShellMenuBar`, `ShellToolBars`, `ShellStatusComposer`, `ShellActions`, `DefaultWindowEnvironment`, `ShellBranding` implementation, `ShellContext`, `DefaultShellChromeProvider` |
| `nbfx.platform.launcher` | `AppProvider`, `ShellApplication` launch |
| `nbfx.ide.workbench` | IDE contributions: project menus/recents/open projects, project status, project branding/title, navigator contributions, IDE actions |

Dependency rule: `platform.shell` may use `api.editor` internally for editor status/content, but its exported chrome API/SPI package must not import `EditorDocument`. `platform.shell` must not import `nbfx.ide`/`nbfx.java`.

API/SPI packages:

- If `ShellChrome`, `ShellChromeProvider`, `ShellContext`, `DynamicMenu`, `@DynamicMenuRegistration` are published, put them in a dedicated exported package (`platform.shell.spi` or `api.shell`), not mixed with `Docking`, `NbfxTabPane`, `AppState`.
- If they are internal, keep them in a non-exported package and do not publish.

---

## 7. Hardcoded behaviour to remove

### 7.1 `ShellApplication`

| Current | Target |
|---|---|
| `decorate` no-op | Install `ShellChrome` top/bottom; `createChrome` override for products |
| No menu/toolbar/status | `DefaultShellChrome` builds from registrations |
| `createPanes` ContentManager/MAIN | Keep default; allow primary-pane provider/override |
| No ApplicationMenu | Install by default |
| No title/branding | `ShellBranding` |
| No WindowEnvironment | `DefaultWindowEnvironment` |
| Session/window/close defaults | Keep as generic defaults; hooks remain |

### 7.2 `JavaFXLaunchApp`

| Current | Target |
|---|---|
| Creates `ActionBars` | Remove; shell chrome owns it |
| `BorderPane.top` VBox | Remove; `DefaultShellChrome` |
| Creates status elements/StatusBar | Platform status composition; IDE project status contribution |
| Configures `WindowEnvironmentService` | Remove; IDE `DetachedBarsFactory`/contributions |
| Project title/undo scope | IDE `ProjectBranding`/project contribution |
| Installs `ApplicationMenu` | `ShellApplication` |
| Recents/open projects/file dispatch | IDE `DynamicMenu.Factory`s and `ShellActionBars` configuration |

### 7.3 `ActionBars`

| Current | Target |
|---|---|
| `ICONS` map | `Command.getIcon()`; `ShellIconProvider` only transitionally |
| `createRegisteredMenu` switch | `ShellMenuBar` |
| File/Edit/View/Window fallback lists | layer `@ActionReference` + platform default `Menus` |
| Recent projects | IDE `@DynamicMenuRegistration` + factory |
| Open projects/next/prev | IDE `@DynamicMenuRegistration` + factory |
| Configure/reset/close | platform `WindowMenuContribution`/`ShellActions` |
| Toolbar fallbacks | `@ToolbarRegistration` only |
| Toolbar labels | `@ToolbarRegistration.displayName` |
| Project/registry/switch fields | IDE contributions |
| Detached bars | `DetachedBarsFactory` |
| `EditorSettings` toggles | platform `ViewMenuContribution` |

### 7.4 Window environment

| Current | Target |
|---|---|
| IDE-only service with setters | `DefaultWindowEnvironment` + `DetachedBarsFactory`/contributions |
| ActionBars field | shell builders |
| Project switcher/navigator | IDE contributions |
| `homeOf` | generic shell resolution |

### 7.5 Status/title/recent state

| Current | Target |
|---|---|
| Caret/line/project elements created in JavaFXLaunchApp | platform editor registrations; IDE project registration |
| `WindowTitles` project title | `ShellBranding` + IDE `ProjectBranding` |
| `AppState.RecentProject` | IDE state or generic `RecentItem` |

---

## 8. Implementation plan

### Phase 0 — Baseline

- Add platform-only chrome fixture:
  - default `ShellApplication` has chrome;
  - a test `@MenuRegistration`/`@ActionReference`/`@ToolbarRegistration`/`@StatusRegistration` appears;
  - no IDE class on classpath.
- Capture IDE golden menu/toolbar/status ids and command ids.
- Baseline: `ant build`, `ant test`, `ant platform-test`, `ant appshell-conformance`, `ant check-purity`, `ant patches-check`, `Counts --check`.

Exit: baseline tests and golden expectations exist.

### Phase 1 — `refactor/shell-chrome-contract`

- `ShellChrome` (`top`/`bottom`/`dispose`), `ShellChromeProvider`, narrow `ShellContext`, `DefaultShellChrome`, default provider.
- `ShellApplication.createChrome`; install top/bottom; call existing `AppState.initToolbars`; dispose chrome.
- `ShellChrome.NONE` for IDE transition; `JavaFXLaunchApp.createChrome` returns it so current behavior is unchanged.
- No concrete menu/toolbar/status controls in the public contract.
- Tests: default chrome, empty-safe, no IDE class, IDE unchanged.

Exit: contract exists and is exercised headlessly; no visible IDE change.

### Phase 2 — `refactor/menu-toolbar-builder`

- `@DynamicMenuRegistration` + processor writing `NbFx/Menus/<menu>/<id>.dynamic`.
- `DynamicMenu`/`DynamicMenu.Factory` with `create(Lookup)` only.
- `ShellMenuBar` reading `.ref` + `.dynamic` at the same menu folder; merge by position.
- `ShellToolBars` layer-only.
- Platform `ViewMenuContribution`/`WindowMenuContribution` (or equivalent platform factories) for toolbars/editor toggles/close/reset.
- `Command.getIcon` canonical; no `ActionBars.ICONS`.
- Update `ActionLayerReader`/`hasContent` for `.dynamic` submenu content.
- No `NbFx/DynamicMenus` root; no root-level dynamic menu in this increment.
- Tests: processor output, static/dynamic ordering, only-dynamic submenu, missing factory, dynamic list refresh/dispose, platform-only menu/toolbar.

Exit: platform-only shell shows menus/toolbars from registrations; IDE unchanged.

### Phase 3 — `refactor/shell-status-branding`

- Compose `platform.status.StatusBar` via `StatusElementRegistry`; no new status bar.
- Platform editor status registrations; IDE project status `@StatusRegistration`.
- `ShellBranding`/title; IDE `ProjectBranding`.
- Default `ApplicationMenu` installation.
- `ShellActions` generic commands; no fake Save.
- Tests: status, title, application menu, no `EditorDocument` in chrome SPI.

Exit: status/branding/application menu are platform defaults.

### Phase 4 — `refactor/default-window-environment`

- `DefaultWindowEnvironment` + `DetachedBarsFactory`/ordered contributions.
- Detached bars from shell builders; generic home resolution.
- IDE navigator/project contribution; remove `WindowEnvironmentService` setters.
- No `EditorDocument` in the new factory API.
- Tests: detached bars, default fallback, IDE precedence, no duplicate environment.

Exit: generic detached-window chrome works.

### Phase 5 — `refactor/ide-workbench-contributions`

- `JavaFXLaunchApp` uses default chrome; no `ActionBars`/StatusBar/WindowEnvironmentService/ApplicationMenu.
- IDE `DynamicMenu.Factory` for recents/open projects; IDE menu/toolbar/status/title contributions.
- Move `AppState.RecentProject` to IDE or generic `RecentItem`.
- Move `WindowTitles` behind `ShellBranding`.
- Remove/deprecate `ActionBars`; no UI icon map.
- Golden parity + scenario.

Exit: IDE behaviour unchanged; shell owns chrome.

### Phase 6 — `feature/appshell-downstream-fixture`

- Platform-only product with no subclass; registers actions/menus/toolbars/status/branding/dynamic menus.
- Starts `ShellApplication`; verifies chrome, no IDE class, detached bars, toolbar persistence.
- Extend `ant appshell-conformance`.

Exit: downstream reuse proven.

### Phase 7 — cleanup/governance

- Remove `ActionBars`/`WindowEnvironmentService` leftovers.
- Ensure no `EditorDocument`/project state in generic chrome API.
- W2: `arch.xml`/`apichanges`/sigtest for new API/SPI; `check-api`.
- Update `PLAN.md`/figures/guards.
- No platform reference to `ActionBars`/`WindowTitles`/`ProjectStatusElement`.

---

## 9. Tests and gates

Per phase:

```sh
ant build
ant test
ant platform-test
ant appshell-conformance
ant check-purity
ant patches-check
java docs/plan/tools/Counts.java --check
```

Additional:

- processor test for `.dynamic` under `NbFx/Menus/<menu>`;
- static + dynamic ordering;
- submenu with only `.dynamic`;
- dynamic factory missing/failing;
- dynamic list refresh and dispose;
- toolbar persistence via `AppState`;
- status registration and project status;
- application menu installation;
- detached bars and default fallback;
- IDE golden menu/toolbar/status ids;
- platform-only chrome with no `EditorDocument` import in the SPI;
- downstream fixture with no shell subclass.

---

## 10. Risks

| Risk | Mitigation |
|---|---|
| IDE menu/toolbar/status regressions | golden snapshots; parity review after Phase 5 |
| `ActionBars` becomes a new monolith | split into `ShellMenuBar`, `ShellToolBars`, `ShellStatusComposer` |
| Public SPI leaks editor/implementation | `ShellChrome` only top/bottom/dispose; narrow contexts; editor-specific contributions |
| `Command.getIcon` API change | W2 javadoc/apichanges; temporary `ShellIconProvider` |
| dynamic menu root/path inconsistency | `.dynamic` at reference point; no `NbFx/DynamicMenus`; update `hasContent` |
| contribution lifecycle/leaks | ordered, failure-isolated, disposed with shell; tests |
| multiple providers/shells | `createChrome` primary; deterministic fallback; one shell/JVM documented |
| `WindowEnvironment` duplicate | one default + ordered contributions |
| project state in platform | move `RecentProject`/`WindowTitles` |
| W2 sequencing | new API/SPI gets arch/apichanges/sigtest before publication |

---

## 11. Suggested first PR

`refactor/shell-chrome-contract` only:

- `ShellChrome`/provider/context/default skeleton;
- `ShellApplication` installs chrome and reuses `AppState.initToolbars`;
- platform-only chrome test;
- `JavaFXLaunchApp` returns `ShellChrome.NONE` to preserve current IDE behavior.

Do not start the menu/toolbar extraction until this contract is green.


---

## 12. Review remediation status (branch `refactor/shell-chrome-contract`)

Implemented after the review:

- detached-window scope is passed to the contribution as a typed document observable, not the global
  active document;
- scoped commands are cached and disposed with the chrome;
- dynamic menu sections update only their own range; static item identity is preserved;
- `DynamicMenu` lives with `@DynamicMenuRegistration`; the processor validates the factory type and
  ids; display-name submenus, empty-section handling and stable factory ids are implemented;
- the product contract/SPI lives in `platform.shell.api`; `ToolBarHost` replaces concrete
  `DefaultShellChrome` checks;
- branding selection is priority-based, deterministic and disposable;
- window-environment contributions are stateless and resolved once per environment;
- status elements receive the shell lookup through `init(Lookup)`;
- editor View toggles live in `platform.editor`;
- application-menu installation is idempotent and shell-lookup based;
- project-lifecycle, wizard, build and document-close commands are placed by ownership;
- `ProjectEditorScope` moved to `project.context`, and affected feature modules no longer depend on
  `ide.workbench`.

Detached chrome is now generic:

- `ShellChromeProfile` filters menu/toolbar surfaces without changing the layer;
- `DetachedBars`/`DetachedChromeProfile` build the detached window from `ShellMenuBar`/
  `ShellToolBars`;
- detached commands use the window's own document scope and are disposed with the window;
- `ActionBars` has been deleted;
- project action metadata now lives in `ide.project.actions`;
- `Select Editor` and document-close commands live in `platform.shell`;
- project state moved from `AppState` to the IDE `ProjectState`, with `ViewStateStore` as the
  generic session-restorer hook; legacy migration also lives in `ProjectState`;
- `ShellWindow` is the stable window facade.

Still outstanding:

- the shell API/SPI packages are still under the implementation root (`org.netbeans.modules.nbfx.platform.shell.api`/
  `.spi`); M0 moves them to `org.netbeans.api.nbfx.shell`/`org.netbeans.spi.nbfx.shell`;
- `WindowPaneProvider` returns `NbfxTabPane`; M0 introduces `WindowPaneHost`/`WindowPaneFactory` so a provider
  never constructs the concrete tab pane, and `NbfxTabPane` becomes internal;
- `JavaFXLaunchApp`/`NavigatorPaneProvider` still use `NbfxTabPane` statics; M0 moves those operations to
  `WindowManager`/`ViewManager`/`ShellWindow`, with friend packages only as the transition;
- apichanges/signature-test coverage for the new API/SPI remains a W2 item;
- `SELECT_IN_PROJECTS`/`SELECT_IN_FILES` and `RESET_WINDOWS` still depend on workbench-owned
  navigator/dock internals.

The API-only downstream fixture (`PLAN.md` §11 W2a) is the gate: if a product cannot implement the M0
extension points with API/SPI imports alone, the boundary is not done.

---

## 13. Declarative view and window composition

The action/menu/toolbar/status/view **leaves** are registration-driven, but the window
**composition** is still mostly imperative.

Implemented in this increment:

- `@AreaRegistration` and `AreaRegistrationProcessor`;
- `ViewAreaRegistry` merges layer-declared areas with the platform defaults;
- `PlatformViewAreas` declares the default areas in the layer;
- `@ViewRegistration.area` resolves custom areas through the registry;
- `Docking` uses the registered explorer weight;
- `ViewManagerService.show()` resolves `ViewSpec` and uses its area/location/position;
- `ViewManager.show(view, area, position)` lets the startup/default-view code apply the declared
  placement;
- `@WindowLayoutRegistration` + `WindowLayoutRegistry` declare the first-run dock tree, and
  `Docking.create` applies it when its permanent roles match.

Still imperative:

- `JavaFXLaunchApp.createPanes()`/`createNavigator()` decide which permanent panes exist;
- `NbfxTabPane.PaneRole` still fixes the permanent roles to `MAIN` and `NAVIGATOR`;
- custom permanent panes need a role registry before a product can declare a different window
  shape.

Target:

1. `@AreaRegistration` declares areas (`id`, `displayName`, `location`, `weight`); processors write
   `NbFx/ViewAreas/<id>` and `ViewAreaRegistry` merges platform defaults with contributions.
2. `ViewRegistry` resolves `ViewRegistration.area` against the area registry, not only the fixed
   `ViewArea` constants.
3. `ViewManagerService.show()` resolves `ViewSpec` and places the view at its declared
   `area`/`location`/`position`; the provider method is only a fallback.
4. `@WindowLayoutRegistration`/a `WindowComposition` model declares permanent panes, initial dock
   tree and area weights; `Docking.create(composition)` replaces the hardcoded navigator/editor
   shape.
5. `introduceDefaultViews()` applies the declared composition; persisted layout still wins through
   `SessionRestorer`.
6. Conformance tests cover a custom area, a custom area weight, a view placed in it, and a
   declarative first-run layout.

---

## 14. Revised permanent-pane composition plan

The declarative layer now covers view metadata, custom areas and the first-run dock tree. The
remaining composition is the permanent pane itself.

Revised ownership:

- `@AreaRegistration` owns geometry only: id, displayName, location and weight.
- `@PaneRegistration` owns a permanent pane: id, displayName, `PaneKind`, owning area,
  `keepWhenEmpty` and position.
- `@WindowLayoutRegistration` owns the initial dock tree and references the registered pane ids.
- `WindowComposition` is a runtime resolver over the pane, area and layout registries; it is not a
  fourth registration mechanism.

Required API/lifecycle decisions:

- move `PaneKind` from `NbfxTabPane` to `api.views` so pane registrations can use it;
- make `PaneRole` a canonical value object with built-in `MAIN`, `NAVIGATOR`, `DOCKED`, `DETACHED`
  and `SLIDE`, plus `PaneRole.of(id)` for registered roles;
- keep the editor `MAIN` pane special: it is supplied by `ContentManager` and is the only
  `PaneKind.EDITOR` pane;
- define `keepWhenEmpty`, persistence and minimization behavior for custom permanent panes;
- define unknown-role behavior for persisted layouts and unknown areas for views.

Implemented:

- `PaneKind` moved to `api.views`;
- `PaneRole` is a canonical value object with `PaneRole.of(id)`;
- `@PaneRegistration` + processor + `WindowPaneRegistry`;
- MAIN/NAVIGATOR declared as platform panes;
- `ViewArea` resolves to its pane through `@PaneRegistration.area`;
- `WindowComposition`/`WindowCompositionResolver`;
- `Docking.create(WindowComposition, Map<String, NbfxTabPane>)` is the only creation path; the old
  `create(NbfxTabPane...)` entry point was removed. `DockArea` takes an explicit main role, so the
  pane order can follow the declared composition tree without changing which pane owns content;
  `Docking` passes `MAIN`;
- `WindowPaneRegistry.discover()` is cached and invalidated by a `FileChangeListener`; a
  registration with a reserved non-permanent role (`DOCKED`, `DETACHED`, `SLIDE`) is rejected;
- `PaneRole.of` rejects names that are not `\w+`, so corrupt persisted pane lines are ignored;
- `ViewManagerService` fallback uses `WindowPaneRegistry.defaultViewRole()` instead of
  `NAVIGATOR`;
- `@PaneRegistration.permanent` was removed: every registered pane is permanent, and the only
  transient roles are the built-in `DOCKED`, `DETACHED` and `SLIDE`.

Now also implemented by the pane-instance step in section 15:

- `ShellApplication` consumes `WindowComposition` and lets
  `createPane(WindowPane, WindowPaneContext)` or a `WindowPaneProvider` supply the concrete
  instance;
- `keepWhenEmpty` is honored by `DockArea` through `DockHost`;
- unknown persisted roles move their tabs to the editor/default view pane.

---

## 15. Pane-instance composition plan

`WindowComposition` describes which panes exist and how they start out, but the shell still chose
the concrete instances imperatively. This step makes the shell consume the composition.

Ownership:

- `WindowComposition` is the single source of registered permanent panes and the first-run tree.
- A `WindowPaneProvider` (or the
  `ShellApplication.createPane(WindowPane, WindowPaneContext)` hook) supplies concrete instances
  for declared panes; the hook wins over providers, providers win over the default.
- The editor pane stays special: an `EDITOR` registration resolves through `ContentManager`, never
  through a provider.
- `keepWhenEmpty` is a `DockHost` decision; `Docking` answers it from `WindowPaneRegistry`.

Implementation sequence:

1. add `WindowPaneProvider` and a protected `createPane(WindowPane)` hook;
2. have `ShellApplication.start()` build its panes from the resolved composition and pass the
   composition to `Docking.create`;
3. replace `JavaFXLaunchApp.createPanes()` with a navigator `createPane` override;
4. remove the hardcoded pane factory from the shell and the duplicate built-in pane defaults from
   the registry;
5. honor `keepWhenEmpty` in `DockArea`;
6. move tabs of an unknown persisted role to the editor/default view pane instead of dropping them;
7. test provider selection, keep-when-empty and unknown-role migration.

Implemented:

- `WindowPaneProvider` + `ShellApplication.createPane(WindowPane, WindowPaneContext)` hook;
- `WindowPane`, `WindowPaneProvider` and `WindowPaneContext` live in the exported
  `org.netbeans.modules.nbfx.platform.shell.spi` package, recorded in `arch.xml` and the shell
  `apichanges.xml`;
- `WindowPaneContext` carries the shell lookup, the registered panes and the shell window;
- providers are resolved once per shell and the composition/area are process-wide;
- `Docking` retains the resolved `WindowComposition`; area, `keepWhenEmpty`, default-view and
  fallback placement resolve against that snapshot, with `WindowPaneRegistry` only as the
  headless fallback;
- `Docking.paneAt(DockLocation)` first resolves the location through the registered default area
  and its pane, so a product can own a legacy location without editing `Docking`; the fixed
  `NAVIGATOR`/`MAIN`/edge mapping is the fallback;
- `Reset Windows` applies the resolved composition tree and iterates every permanent pane, and a
  view's home resolves through its `ViewSpec` area before the legacy location;
- `NavigatorPaneProvider` receives the session restorer, navigator views and pane sink explicitly,
  with no dependency on `JavaFXLaunchApp`;
- `PaneRole.defaultKind()` is the single editor-pane rule: only `MAIN` defaults to
  `PaneKind.EDITOR`, every other role to `PaneKind.VIEW`;
- persistence ordering also uses the resolved composition, so runtime placement and capture no
  longer re-read `WindowPaneRegistry` once the shell has started;
- the one-shell-per-JVM invariant is documented on `ShellApplication` and `Docking`; restarting a
  shell in the same JVM would reuse the static docking area and pane registry.
- `WindowComposition` stays internal; `NbfxTabPane`/`PaneRole` remain the pane contract only until the M0
  shell-window extraction replaces `WindowPaneProvider`'s return type with `WindowPaneHost` and makes the
  concrete tab pane internal;
- providers may release their pane through `disposePane`, which the shell calls on stop;
- `ShellApplication.start()` builds its panes from `WindowComposition` and passes the composition
  to `Docking.create`;
- `NavigatorPaneProvider` in `ide.workbench` creates and fills the navigator pane; the
  application subclass no longer owns pane-specific composition;
- the registry no longer hardcodes MAIN/NAVIGATOR; the platform layer is the single declaration;
- `PlatformWindowLayout` declares the first-run structure with `H[](P:NAVIGATOR,P:MAIN)`; the
  initial divider is derived from the registered explorer area weight, and explicit dividers still
  override derived ones through `DockTrees.resolveDividers`;
- `DockHost.keepWhenEmpty` keeps an empty permanent pane visible when its registration asks for it;
  a keep-when-empty pane that the declared tree omits is re-shown after the tree is applied;
- a provider that returns a pane with a role other than the declared `WindowPane.role()` is
  rejected with a warning and replaced by a default pane of the declared kind;
- `SessionRestorer` moves tabs of an unknown persisted role to the editor/default view pane;
- the platform conformance fixture registers a custom pane and area in the test layer, starts the
  shell with them and shows a view in the custom pane;
- tests cover a view tab whose pane role disappeared and a view whose declared area is unknown;
- `ViewManagerService` resolves a missing area and position from the registered `ViewSpec`; an
  unknown area falls back to its location and then to the default view pane;
- the default view role falls back to the built-in navigator only when no view pane is declared,
  and that fallback is documented and tested.

Remaining follow-ups:

- a custom pane instance is still a `NbfxTabPane`; the M0 `WindowPaneHost` contract is the boundary, and a
  fully custom pane node type remains a wider window abstraction;
- on-demand, role-stable panes would need a new registration lifecycle; they are out of scope for the
  permanent-pane model;
- a non-tab pane abstraction is out of scope.

---

## 16. View placement API consolidation

`ViewArea` and `DockLocation` now live in `org.netbeans.api.nbfx.views`, next to the registration
types. `ViewLocation` was removed: registered views use `DockLocation`, while detached windows stay
a runtime action. `ViewSpec` no longer stores a separate location; its `location()` derives from
`area()`, so the registered area is the only placement truth.

`ViewPlacement` is the single runtime policy: registered area → area location → legacy
`ViewProvider.getDefaultLocation()` → default view role. `ViewManagerService`,
`JavaFXLaunchApp.homeOfView` and `SessionRestorer` use it instead of repeating the chain. If an
explicit area resolves to no pane, the legacy view location is still tried before the default
view role.

`Docking` snapshots the location-to-area and area-to-pane mappings from the resolved composition
when it builds the area, so `paneAt`/`paneOf` no longer read the configuration filesystem on every
call. `Reset Windows` takes one view catalog (registrations plus providers) for the whole pass and
uses `Docking.defaultTree()` instead of rebuilding the same fallback chain. `ViewManagerService`
treats an explicit `Integer.MAX_VALUE` position as “append”; only the no-position overload takes
the registered `ViewSpec.position()`.

The API move is recorded in `nbfx.api.views/apichanges.xml`; the module is now at specification
version 1.4.

---

## 17. Historical shell cleanup

The remaining review items were closed as follows:

- the generic command resolver no longer exposes `preferFile` in its primary methods; the old
  `ShellContext.command(id, preferFile)` and `ShellCommandResolver.resolve(id, preferFile)`
  overloads are retained as deprecated delegating methods, and file/editor dispatch stays inside
  `IdeShellCommandResolver`;
- `ProjectOpener.open()` owns the Open Project directory chooser and its owner window, and project
  commands disable themselves when their lifecycle/opener services are absent;
- `WindowEnvironmentContribution` is deprecated in favour of the focused
  `DetachedBarsProvider`, `TabHomeResolver`, `ProjectSwitcher`, `NavigatorRevealState` and
  `NavigatorRevealer` capabilities;
- `ShellIconProvider` is the documented fallback for commands without a `Command.getIcon()`;
- `nbfx.api.project`, `nbfx.api.views` and `nbfx.platform.shell` have API history entries;
- `ant check-api` verifies exported packages and public top-level types for the governed modules,
  using `docs/plan/api-snapshot.txt`. The snapshot regex ignores indented nested types; this is a
  type-level gate until a sigtest baseline is added.

---

## 18. Declarative upstream lifecycle

A product no longer needs to subclass `ShellApplication` for the standard lifecycle seams.

- `ShellWindowProvider` publishes the running `ShellWindow` in the global lookup only once the
  composition, panes and scene exist, just before `started()`; `ShellContext` exposes the same
  window to context-aware contributions.
- `@OnUiReady` is the nbfx counterpart of NetBeans `@OnShowing`: it writes to the nbfx-owned
  `NbFx/UIReady` named-service path and runs on the FX thread after the shell is showing.
  `org.openide.windows` remains forbidden by the purity guard.
- `WindowSystemListener` services are discovered from the lookup for load/save notifications.
- `NavigatorRevealer.reveal(file, index)` defaults to `false`, so an unindexed-only revealer cannot
  steal an indexed request from a later revealer.
- `ProjectOpenerService` owns the directory chooser and logs when the shell, window or selected
  directory cannot be used.
- `ShellBranding` now supplies icons, minimum size, root style classes and scene stylesheets. An
  empty icon list leaves the platform default icons untouched; an empty scene-stylesheet list adds
  nothing.
- The default ApplicationMenu is installed by the shell, not by the protected `started()` hook;
  `started()` remains a compatibility seam before the UI-ready tasks.

Startup order:

```text
@OnStart
  -> ShellApplication.start
  -> composition / panes / chrome / scene
  -> ShellBranding appearance
  -> protected sceneReady
  -> show
  -> install ApplicationMenu
  -> protected started
  -> @OnUiReady
  -> restoreSession
```

Shutdown:

```text
WindowSystemListener.saveStarted / saveCompleted
  -> ShellApplication.stop
  -> @OnStop
  -> LifecycleManager.exit
```

---

## 19. Theming and CSS token providers

The theme implementation is based on PR #83 by Geertjan Wielenga (`@geertjanw`), adapted to the
current platform shell. The CSS token values and bundled light/dark/solarized/high-contrast
themes come from that PR.

What landed:

- `nbfx.platform.theme` with `NbfxTheme` and the bundled theme files;
- `ThemeTokenProvider` and `@ThemeTokenRegistration` for component CSS token defaults;
- `ThemeTokenRegistry` resolves token-default stylesheets through the NetBeans system class
  loader, caches the ordered list, and invalidates it when the `NbFx/ThemeTokens` layer changes;
- a platform defaults sheet (`defaults.css`) defines every referenced `-nbfx-*` token, so a
  missing or outdated theme still gets working colors; a unit test checks every reference in the
  platform sources against it;
- token defaults are applied before the selected theme, and the theme keeps its position
  relative to brand stylesheets when it is reapplied; an already-correct scene is left untouched;
- bundled themes load from the module resources; the `nbfx-themes` user folder is an optional
  overlay, and a missing user directory only logs a warning;
- `NbfxTheme` is brandable through `theme.properties`;
- every window is themed as it is shown (a listener on `Window.getWindows()`), so dialogs and
  popups of any module get the token sheets, the theme and the scene color scheme without
  calling `NbfxTheme`; the shell, editor and popups also reapply the theme on scene attach, and
  `NbfxTheme.apply(DialogPane)` themes a dialog pane before it is shown (the close confirmation);
- `ThemeMenuFactory` contributes View > Theme as a dynamic menu;
- on macOS, AWT (which the platform starts before JavaFX) pins the application appearance to
  Aqua unless `apple.awt.application.appearance=system`, and JavaFX then reports `LIGHT` whatever
  the desktop uses, so Follow System never goes dark. The release conf passes the flag; for
  `ant run`, which starts `nbexec` without the conf, the `SystemAppearance` CLI handler in
  `nbfx-platform/core/nbfx-noimport.jar` sets it in the bootstrap, before AWT loads;
- shell/editor CSS and `TokenCategory` now use the `-nbfx-*` token vocabulary.

`.root` is the token contract. Component CSS may add namespaced tokens on `.root`; a theme that
predates the component still gets the component default because the default sheet is applied before
the theme.

---

## 20. OS light/dark theme mode

Theme selection now uses the JavaFX 26 platform colors:

- `Platform.getPreferences().colorSchemeProperty()` reports the OS light/dark scheme;
- `Scene.getPreferences().setColorScheme(...)` overrides the scene scheme and drives the native
  title bar on Windows/macOS.

`ThemeMode` is `SYSTEM`, `LIGHT` or `DARK`. System mode chooses the branded light/dark theme pair;
explicit mode keeps the selected theme and sets the scene scheme to match it. The View > Theme menu
offers Follow System, Light, Dark and the individual theme files.

User themes may declare a sidecar `<id>.properties` with `scheme=dark`; otherwise they default to
light. The OS listener is installed with the shell and disposed on stop. Shell, editor, popup,
detached and dialog scenes register for theme updates.

Linux native title-bar behavior remains platform-dependent and is documented as a limitation.
