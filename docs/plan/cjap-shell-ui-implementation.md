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
# CJAP Shell/UI API and implementation task plan

Status: proposed, 2026-11-24.
Scope: the nbfx platform delivery (CJAP), M0.
Companion reference: [`../reference/cjap-shell-ui.md`](../reference/cjap-shell-ui.md).
Alignment source: [gluonhq/nbfx#104](https://github.com/gluonhq/nbfx/issues/104).

This is a task-based implementation plan for the new shell, layout, content, chrome, window,
theming and UI extension contracts. It is deliberately independent of downstream product names.
Acceptance uses four usage scenarios: live-data cockpit, architecture/knowledge workbench,
document editor/vault and geospatial/EO workbench.

## 1. Architectural decisions

1. **CJAP is the nbfx platform delivery.** It is not a separate specification, runtime or product.
2. **The product owns layout and visible shell.** Framework defaults are neutral and replaceable.
3. **#104 is the layout/docking baseline.** Its region/group/content/policy model is adopted with
   nbfx naming: `ViewArea` is the logical region, `PaneRole` its stable identity, `DockGroup` the
   physical group.
4. **Existing names are reused.** `ShellApplication`, `ShellContext`, `ShellWindow`,
   `WindowManager`, `WindowSystem`, `WindowState`, `AreaState`, `ViewProvider`, `ViewManager`,
   `ContentManager`, `EditorDocument`, `EditorService`, `ActionRegistry` and `ViewKind` remain.
5. **Shell chrome is declarative.** `NbFx/ShellRegions` declares region slots, contributions and
   action references. `NbFx/ShellProfiles` selects chrome per window.
6. **Actions are registered once and placed many times.** `@ActionRegistration` owns behaviour;
   `@ActionReference(path = ...)` owns placement in menus, toolbars, ribbons, shell regions,
   context menus and palettes.
7. **No feature packs.** NBM modules, clusters and kit modules are the only composition model.
8. **No public compatibility adapters.** Old nbfx APIs are deleted after their consumers migrate.
   Persisted session data may be migrated once.
9. **No new nbfx UI/API dependency on `DataObject`/`DataNode`/`openide.loaders`.** The platform keeps
   the bridge for NetBeans core/NBM compatibility.
10. **No static UI singletons.** `WindowSystem`, `DockRuntime`, content, theme and extension
    registries are per application/shell instance.

## 2. #104 alignment

| #104 concept | nbfx target | Note |
|---|---|---|
| `RegionId` | `PaneRole` | existing stable area identity |
| `RegionTypeId`/`RegionSpec` | `ViewArea` | existing logical area name; evolves to carry capabilities/policy/style |
| `DockGroupId`/`DockGroupSpec` | same | physical tabbed/single/canvas group |
| `LayoutSpec`/`LayoutNode` | same | pure Java, content-free |
| `Slot` | `LayoutNode.Slot` | stable runtime split/drop slot |
| `DockPolicy`/`DockRequest`/`DockDecision` | same | semantic transfer decisions and reasons |
| `Content`/`ContentFactory`/`ContentState` | same | generic content lifecycle |
| `DockHost`/`DockController` | `DockHost`; controller behind `WindowManager` | public operations stay on `WindowManager` |
| `DockWindow` | same | main/floating/custom windows |
| `LayoutStore`/`ContentStateStore` | `WindowStateStore` + `ContentStateStore` | reuse existing window-state name |
| builder + annotations + XML | same | no DSL |
| no static `Docking` | application-scoped `DockRuntime` | existing static implementation is removed |
| separate layout/content persistence | same | one writer per concern |
| neutral platform defaults | same | product layout wins |

The shell-region model is additional to #104:

- `ViewArea`/`DockGroup` model the content dock.
- `ShellRegion` models window chrome: brand, menu, toolbar, ribbon, dock host, status, overlay.
- `OverlayHost` models HUD/palette layers that are not split leaves.

## 3. Target module map

### API modules

| Module | Tier | Target contents |
|---|---|---|
| `api.resources` | M | `ResourceRef` and neutral resource references |
| `api.product` | M | `Product`, `ProductProvider`, identity/icons/style/About/associations; policy ids |
| `api.shell` | T2-FX | `ShellProvider`, `ShellFrame`, `ShellContext`, `ShellWindow`, `WindowManager`, `WindowSystem`, `WindowKind`, `WindowOptions`, `DockWindow`, chrome/profile contracts |
| `api.layout` | M | `LayoutSpec`, `LayoutNode`, `ViewArea`, `ViewAreaKind`, `DockGroupSpec`, `PaneRole`, `DockLocation`, `PaneKind`, `PresentationId`, `AreaStyle`, builder/XML |
| `api.docking` | T2-FX | `DockHost`, `DockPolicySpec`, `DockRequest`, `DockDecision`, `DockPolicy`, transfer contracts |
| `api.content` | T2-FX | `Content`, `ContentId`, `ContentFactory`, `ContentState`, `ContentSource`, `SourceHandler`, `DropHandler`, `OverlayHost`, `DockPlacement`, `TransferMode` |
| `api.views` | T2-FX | `ViewProvider`, `ViewManager`, `ViewSpec`, `ViewRegistry`, `ViewInfo`, `ViewKind`, `NodeView`; depends on `api.nodes` |
| `api.actions` | T2-FX | existing action model, scoped `Lookup`, shell-region references |
| `api.nodes` | M | `NodeModel`, `ChildrenModel`, `ChildFactory`, `ExplorerContext` |
| `api.editor` | T2-FX | `EditorDocument`, `EditorService`, editor capabilities over `Content` |
| `api.theme` | T2-FX | `Theme`, `ThemeManager`, `ThemeTokens`, `ThemeScope`, `HostTheme`, `ThemePolicy`, registrations |
| `api.workspace` | M | product workspace lifecycle |
| `api.settings` | M | scoped settings |
| `api.dialogs` | T2-FX | dialogs/notifications/About/diagnostics/file chooser/close guard |
| `api.diagnostics` | M | event/source-health model |
| `api.plugins` | T1 | contribution ownership/invalidation; install/update later |
| `api.layer` | T1 | typed layer reader/registry |
| `api.status` | T2-FX | existing status element/registry contract, extended with scope |

### Implementation modules

| Module | Responsibility |
|---|---|
| `platform.shell` | `ShellApplication`/`ShellRuntime`, window/focus/composition |
| `platform.shell.default` | neutral default shell/frame and chrome renderers |
| `platform.docking` | `DockRuntime`, groups, hosts, transfer, persistence; rename/replacement of `platform.workspace` |
| `platform.content` | content registry/manager/routing |
| `platform.views` | view registry/manager/routing |
| `platform.design` | neutral component/design system |
| `platform.nodes` | `NodeView` renderer and property sheet |
| `platform.theme` | `ThemeManager` implementation |
| `platform.product` | product descriptor/build integration |
| `platform.workspaces` | product workspace implementation |
| `platform.settings` | settings implementation |
| `platform.dialogs` | dialogs/notifications/About implementation |
| `platform.diagnostics` | diagnostics/event log implementation |
| `platform.plugins` | contribution/plugin lifecycle implementation |

## 4. Layered target architecture

```text
Product
  identity/icons/style/theme policy/About
  layoutId + shellId + chromeProfileId

ShellApplication / ShellRuntime
  Window + FocusController + ShellContext
  ShellProvider → ShellFrame
    WindowDecoration
    ShellRegion(BRAND)
    ShellRegion(MENUBAR)
    ShellRegion(TOOLBAR)
    ShellRegion(RIBBON)
    ShellRegion(DOCK) → DockRuntime
    ShellRegion(STATUS)
    OverlayHost

DockRuntime
  LayoutSpec → ViewArea → DockGroup → DockHost → Content
  WindowManager / WindowSystem
  DockPolicy + transfer + persistence

ContentManager
  ContentFactory / ContentState / SourceHandler / DropHandler

ViewManager
  ViewProvider / ViewRegistry / routing

ActionRegistry
  @ActionRegistration + @ActionReference
  resolve(id, Lookup)

NodeModel / ExplorerContext / NodeView

ThemeManager / ThemeScope / platform.design

Workspace / Settings / Diagnostics / Contributions
```

## 5. Task plan

### Phase 0 — Baseline and contracts

#### T0.1 — #104 alignment decision record

- Map #104 names to nbfx names.
- Record product-owned layout, neutral defaults and deletion of old APIs.
- Record shell-region vs content-layout vs overlay boundaries.
- Record no feature packs and NBM-only composition.

Acceptance: no unresolved naming or ownership decision blocks Phase 1.

#### T0.2 — Target module skeleton and API reset

- Create new API/implementation modules in devel state.
- Add package-level `package-info.java`.
- Do not reset existing API snapshots/signatures yet; reset them in the domain PR that deletes the
  old API after the replacement exists.
- Update cluster fragments and dependency graph.

Acceptance: target modules compile; old APIs may remain temporarily but are no longer the target.

#### T0.3 — Layer registry foundation

- Extend `api.layer` with typed folder/entry reading, ordering, singleton/fresh scopes and change
  notifications.
- Support `.instance`, `.ref`, `.dynamic`, folder attributes and `.shadow` where required.
- Keep `LayerPaths` internal.

Acceptance: shell/layout/action registries can be implemented without direct `FileUtil` access.

#### T0.4 — UI test harness

- Headless JavaFX support for multiple windows, themes, drag/drop and custom content.
- Fixture for API-only product modules.
- Guards for no T0/DataObject imports and no static UI registries.

Acceptance: a minimal product fixture compiles and starts headlessly.

### Phase 1 — Pure layout and metadata model

#### T1.1 — Ids and value types

- `PaneRole`, `ViewAreaKind`, `DockGroupId`, `SlotId`, `PresentationId`.
- `PaneRole` is only a logical-area identity; `DOCKED`/`DETACHED`/`SLIDE` are removed from it.
- Neutral built-ins are `MAIN`, `SIDE`, `BOTTOM`, `CENTER`; consumer areas register their own kinds.
- Built-in constants plus `of(...)` parsing.
- One namespaced string form (`namespace:name`) with built-in short forms.
- Stable equality/hashCode and XML/layer string forms.

Acceptance: id round-trip tests; unknown product ids work.

#### T1.2 — `ViewArea`, `DockGroupSpec`, `LayoutSpec`

- Evolve `ViewArea` to carry `PaneRole`, `ViewAreaKind`, display name, default location/weight,
  default `PresentationId`, capabilities, policy id, `AreaStyle`, visibility, empty-state policy
  and order.
- Add `DockGroupSpec` with area, `PaneKind`, `PresentationId`, capacity, policy id and group lifetime.
- Add `LayoutSpec`/`LayoutNode` (`Split`, `Group`, `Slot`).
- All public records defensively copy collections; layout contains no executable policy or content.
- `DockPolicySpec` is implemented in `api.docking`, not `api.layout`.

Acceptance: zero-content layout with multiple groups/areas validates.

#### T1.3 — Builder, annotations and XML

- `LayoutBuilder` canonical API.
- `@LayoutRegistration` carries a complete self-contained layout XML document.
- `@ViewAreaRegistration` and `@DockGroupRegistration` declare neutral standalone area/group
  metadata, with group presentation/policy inheritance from the resolved area.
- Layer XML reader/writer with path diagnostics and includes.

Acceptance: builder/XML/annotation paths produce equal `LayoutSpec`.

#### T1.4 — Validation and normalization

- Duplicate ids, missing refs, bad dividers, cycles, unknown types, group/area mismatch.
- Divider arity/range/order (N-1 strictly increasing positions), root shape, group reference
  uniqueness, slot-id uniqueness and slot materialization.
- Empty policy sets are deny-all; wildcard/default policy is explicit allow-all.
- Normalize away unreferenced transient groups, canonicalize signed-zero dividers and impose
  deterministic declaration order. Invalid geometry remains rejected rather than silently clamped;
  deterministic fallback for corrupt persisted state belongs to T1.5.

Acceptance: property/fuzz tests; normalization is idempotent.

#### T1.5 — Persistence model (after T4.1)

- `WindowState` is one window: layout/groups/content order/active/geometry/profile.
- `SessionState` is all windows plus active window.
- `WindowStateStore` owns `SessionState`; `ContentStateStore` owns only per-content payloads keyed by
  `ContentId`/instance key.
- Versioned schema and one-time old-session migration utility.
- Missing/corrupt persisted state produces diagnostics and falls back deterministically to the
  normalized product default layout.

Acceptance: old fixture migrates; new state round-trips without JavaFX types; corruption fallback is
repeatable.

### Phase 2 — Product, shell regions and chrome

#### T2.1 — Product descriptor

- `ProductProvider`/`Product` exactly one per distribution, resolved through `Lookup.lookupAll`.
- Immutable product metadata source and validation.
- Product namespace through `ProductIdentity.id`.

Acceptance: missing/duplicate/null provider fails; name/icon/version/About come from one immutable
source.

#### T2.2 — Declarative shell regions

- `ShellRegionSpec`/registry.
- `NbFx/ShellRegions` layer schema.
- `@ShellRegionRegistration`.
- Built-in ids/kinds including `DOCK`.
- Neutral default declarations for brand/menubar/toolbar/dock/status/overlay; ribbon remains opt-in.

Acceptance: default shell declares brand/menubar/toolbar/dock/status/overlay in the generated layer,
with exactly one canonical `DOCK` region.

#### T2.3 — Shell contributions

- `ShellContribution` and `@ShellContributionRegistration`.
- Per-shell instantiation, ownership and disposal.
- Region/entry ordering and profile filtering.

Acceptance: a product adds a brand bar without shell implementation imports.

#### T2.4 — Action registration and placement reuse

- `@ActionRegistration` classes implement `Command`.
- `ActionRegistry` loads actions through `api.layer`.
- `@ActionReference(path = "ShellRegions/...")` places the same command in multiple surfaces.
- Context filtering by `Lookup`, MIME, language and profile.

Acceptance: one command appears in menu, toolbar, ribbon and brand bar; scoped enablement works.

#### T2.5 — Shell provider/frame/context

- `ShellProvider`, `ShellFrame`, `ShellRegionHost`, `ShellRegionContext`, `ShellRegionRenderer`.
- `ShellFrame` exposes root, dock-root mounting, region host and close. `overlays()` is deferred to
  C6-2 with `OverlayHost` in `api.content`.
- `ShellApplication` becomes the default composition root, not a subclassing API.
- Default shell consumes `ShellRegionRegistry`; custom shells use `ShellRegionHost`.
- Runtime region host/default frame implement stable mount/update/dispose and DOCK-by-kind ownership.
- `ShellApplication` selects zero/one/many `ShellProvider` implementations deterministically, publishes
  the runtime host in `ShellContext`, and preserves the legacy subclass chrome path until C8.

Acceptance: default shell and an API-only custom shell run with the same services, toolbar persistence
and View-menu controls; duplicate providers, missing DOCK mounts and invalid roots fail with rollback.

#### T2.6 — Window chrome profiles

- C2-6a: `ShellProfileSpec`, `ShellProfileRegistry`, repeatable `@ShellProfileRegistration`,
  `ShellProfileHint`, `ChromeProfilePolicy`, `WindowKind`, `WindowDecoration` and `WindowOptions`.
- `NbFx/ShellProfiles` declarations and neutral `main`, `floating-editor`, `floating-tool` and
  `presentation` profiles; profile allow-lists intersect region applicability in `ShellRegionHost`.
- C2-6b: `ShellProfileResolver`/`ShellProfileSelection` and `ChromeProfilePolicyProvider` implement
  explicit/hint/product-default/policy-fallback/neutral precedence. `ShellApplication` publishes the
  selected main profile before frame creation and applies its decoration; menu/toolbar builders and
  shell-region action reads consume profile-filtered references.
- C2-6c: optional `WindowState.profileId` persistence with backward-compatible state loading.
  Restored-profile selection before frame creation is completed with C3-3 multi-window restore.

Acceptance for C2-6a: profile metadata round-trips through annotations/layer discovery, malformed
entries are isolated, neutral profiles expose exactly one reusable `DOCK`, and allow-lists narrow
neutral/product regions without breaking legacy predicate profiles.

Acceptance for C2-6b: resolver tests prove every precedence/fallback branch and decoration override;
runtime tests prove neutral operation without a product, mandatory named policy discovery,
missing/duplicate-policy failure, hint/explicit precedence, selected-profile publication before
provider creation and native/undecorated startup. Action
reader/menu/toolbar tests prove profile filtering and null/blank all-profile semantics without
broadening other context dimensions. Full task acceptance remains: MAIN/FLOATING/CUSTOM windows
select different chrome and restored windows retain their profile.

### Phase 3 — Docking runtime and windows

#### T3.1 — `DockRuntime` and `WindowSystem`

- Application-scoped runtime; no static `Docking`.
- Build runtime from `LayoutSpec`.
- `WindowSystem` load/save/reset lifecycle.

Acceptance: isolated runtimes in tests; zero-content layout renders.

#### T3.2 — Dock hosts and presentations

- Tabbed, single, canvas, overlay and headerless hosts.
- `PresentationProvider` registry.
- Stable content node across presentation changes.

Acceptance: tabbed and canvas groups coexist; no content recreation.

#### T3.3 — `WindowManager` operations

- Public operations use `ContentId`/`DockGroupId`, not `Tab`/`WindowPaneHost`.
- `canDock` returns `DockDecision`; `dock` returns `DockResult`.
- Attach/move/select plus group split/close/float/redock.
- Undo unit = one user gesture.

Acceptance: keyboard and pointer split/move/float tests.

#### T3.4 — Multi-window

- `WindowKind.MAIN/FLOATING/CUSTOM`.
- `DockWindow`, `WindowContent`, `WindowOptions`, `WindowClosePolicy`.
- Geometry, owner, focus, close and persistence.
- Same content instance moves between windows; close policy is REDOCK/CLOSE_CONTENT/HIDE.
- `FocusController` separates window activation, logical selection and keyboard focus.

Acceptance: float/redock preserves state; custom window hosts arbitrary content.

#### T3.5 — Focus and action context

- Active window → group → content.
- Selection/lookup preserved on move.
- Global `ActionContext` follows active window.

Acceptance: focus switching updates commands and shortcuts deterministically.

#### T3.6 — Minimize/maximize/slide

- Port current anchor/slide behavior to groups.
- Region capabilities drive allowed operations.
- Keyboard equivalents and persistence.

Acceptance: no regression in split/minimize/maximize scenarios.

### Phase 4 — Content, views and editor integration

#### T4.1 — Content model

- `Content`, `ContentFactory`, `ContentState`, `ContentKind`.
- Content requirements and lifecycle.
- `ContentManager` as generic manager/router.

Acceptance: view and editor content share one lifecycle.

#### T4.2 — View model

- Keep `ViewProvider`, `ViewManager`, `ViewSpec`, `ViewRegistry`, `ViewInfo`, `ViewKind`.
- Dynamic view instances and per-view state.
- Route views to `ViewArea`/`DockGroup`.

Acceptance: dynamic view restore and open/close work.

#### T4.3 — Editor integration

- `EditorDocument` becomes a `Content` capability.
- `EditorService` remains factory/registry.
- `EditorPreview`/`OpenSources`/`FileSelectionContext`/`FileTypes` remain as facades where useful.

Acceptance: code editor and non-code content coexist; no `FileObject` requirement in base content.

#### T4.4 — Sources and drop

- `ContentSource`, `SourceHandler`, `DropHandler`, `TransferSource`, `DropRequest`, `DropDecision`.
- File/URI/text/multi-file and internal content/node transfer.
- Area/group/content drop policies.

Acceptance: map/editor/explorer targets accept different sources; illegal drops explain why.

#### T4.5 — Dynamic content and persistence

- Create/recreate content from state.
- Missing content diagnostics.
- Content state versioning.

Acceptance: session restore with missing/unknown content is deterministic.

### Phase 5 — Nodes and scoped actions

#### T5.1 — Explorer model

- `NodeModel`, `ChildrenModel`, `ChildFactory`, `ExplorerContext`.
- Typed Lookup; no `Action[]`.

Acceptance: structure/navigator model works headlessly.

#### T5.2 — NodeView renderer

- Tree/list/tree-table/table.
- Custom cells, badges, icons, DnD, inline rename, multi-select, grouping/filter/sort, a11y.

Acceptance: one renderer serves all M0 explorer scenarios.

#### T5.3 — Scoped actions

- `ActionRegistry.resolve(id, Lookup)`.
- `ActionLayerReader.readForContext`.
- Remove editor-document-specific scoped provider methods.

Acceptance: right-click uses clicked node, not global selection.

#### T5.4 — Context menus

- `NodeContextMenuBuilder`.
- Layer refs with context/language/MIME filters.
- Remove `FileContextMenuContributor`/`ElementContextMenuContributor`.

Acceptance: navigator/structure context menus are declarative.

### Phase 6 — Theme, design, status and overlays

#### T6.1 — Theme API

- `Theme`, `ThemeManager`, `ThemeTokens`, `ThemeScope`, `HostTheme`.
- Mode + accent; typed tokens; scoped/embedded application.
- Remove public static `NbfxTheme`.

Acceptance: main/detached/dialog/popup/scoped content update live.

#### T6.2 — Default design system

- `platform.design` controls/cards/drawers/tables/trees/dialogs/notifications/density/a11y.
- Default shell and NodeView use it.
- Product restyle without `!important`.

Acceptance: default shell usable; custom shell needs no platform control imports.

#### T6.3 — Status scopes

- `StatusScope.WINDOW/AREA/GROUP/CONTENT`.
- Scoped `StatusElement` registration.
- Multiple status hosts.

Acceptance: window and content-local status bars coexist.

#### T6.4 — Overlay/HUD

- `OverlaySpec`, `OverlayAnchor`, `OverlayInputMode`, `OverlayHost`; add `ShellFrame.overlays()`.
- Content/area/window overlays; distinguish shell overlay slot, dockable overlay presentation and
  anchored HUD layers.
- Normative input/focus rules and anchor lifecycle across move/float/redock.
- Scoped theme/focus/input.

Acceptance: HUD does not steal map drag; palette overlay works.

### Phase 7 — Services, extensions and build

#### T7.1 — Workspace/settings/diagnostics

- `Workspace`, `WorkspaceService`, `Settings`, `Diagnostic`, `DiagnosticsService`.
- Open/close/recent/reload/watch/trust/state.
- Bounded event/source-health log.

Acceptance: product workspace and diagnostics work without IDE modules.

#### T7.2 — Contributions and plugin lifecycle

- `Contribution`, `ContributionOwner`, `ContributionRegistry` in `api.plugins`.
- Ownership/invalidation/close.
- ServiceLoader bridge and NBM policy.

Acceptance: plugin unload leaves no ghost chrome/content/actions.

#### T7.3 — NBM composition and downstream build

- `nbfx.extra.clusters`, fragments, downstream template.
- NBM wrapper generator.
- Cluster/kit validation; no feature packs.

Acceptance: a product cluster builds without post-processing.

#### T7.4 — Product packaging

- Product metadata drives runtime identity and jpackage/jlink metadata.
- Icons, associations, About, update channel.
- Platform BOM/POMs/update-center layout.

Acceptance: packaged product has correct identity and starts.

### Phase 8 — Migration, conformance and freeze

#### T8.1 — Migrate nbfx consumers

- IDE, Java and platform consumers move to the new APIs.
- No temporary public adapters.

Acceptance: full build/test green on new APIs.

#### T8.2 — Delete old APIs

- Delete `ShellBranding`, `ShellChromeProvider`, `WindowPane*`, `ViewLocation`, old window/layout
  state APIs, public `Docking`/`AppState`/`SessionRestorer`, public `NbfxTheme`, duplicate `view`
  package and editor-specific context contributors.

Acceptance: old symbols are absent; API snapshot reflects target only.

#### T8.3 — Reference product

- Product-native shell/layout/theme/design.
- One content/action/source/status/workspace.
- No T0 imports.

Acceptance: reference product hides platform feel.

#### T8.4 — Four usage scenarios

- Live-data cockpit.
- Architecture/knowledge workbench.
- Document editor/vault.
- Geospatial/EO workbench.

Acceptance: all pass on platform-only distribution.

#### T8.5 — UX/a11y/performance and freeze

- Keyboard/focus/a11y/RTL/contrast/reduced motion.
- Startup/theme/render/leak budgets.
- Final `arch.xml`/`apichanges`/sigtest and reference docs.

Acceptance: CJAP API set frozen for release.

## 6. PR slicing

| PR | Scope | Depends on |
|---|---|---|
| C0-1 | #104 alignment ADR + target module map | — |
| C0-2 | module skeleton only; no API snapshot reset | C0-1 |
| C1-1 | ids + `ViewArea`/`DockGroupSpec`/`LayoutSpec` | C0-2 |
| C1-2 | builder/XML/annotations/validation | C1-1 |
| C1-3 | persistence model + migration fixture | C1-1, C4-1 |
| C2-1 | `Product` + metadata | C0-2 |
| C2-2 | shell-region layer/registry/defaults | C1-1, C2-1 |
| C2-3 | shell contributions/lifecycle | C2-2 |
| C2-4 | action registration/reference reuse | C2-3 |
| C2-5 | `ShellProvider`/`ShellFrame`/region host | C2-2, C2-4 |
| C2-6 | window chrome profiles | C2-5 |
| C3-1 | `DockRuntime` + tabbed/single hosts | C1-2 |
| C3-2 | `WindowManager` transfer/split/float | C3-1 |
| C3-3 | multi-window + profiles + persistence | C2-6, C3-2 |
| C3-4 | focus/minimize/maximize/slide | C3-2 |
| C4-1 | `Content`/`ContentManager` | C3-1 |
| C4-2 | views/dynamic views/editor integration | C4-1 |
| C4-3 | sources/drop/transfer | C4-1 |
| C5-1 | node model + NodeView | C4-1 |
| C5-2 | scoped actions/context menus | C5-1, C2-4 |
| C6-1 | theme API/scopes/tokens | C2-4 |
| C6-2 | design system/status/overlay | C6-1, C5-1 |
| C7-1 | workspace/settings/diagnostics | C4-1 |
| C7-2 | contributions/plugin lifecycle | C2-2, C7-1 |
| C7-3 | downstream build/NBM composition/packaging | C2-1, C7-2 |
| C8-1 | consumer migration + old API deletion | C3–C7 |
| C8-2 | reference product + scenarios | C8-1 |
| C8-3 | UX/a11y/perf/freeze | C8-2 |

## 6.1 Review resolutions carried into implementation

- `api.layout` is M-tier and contains no JavaFX, executable policy, theme or content dependency.
- `DockPolicySpec` is immutable data; executable `DockPolicy` lives in `api.docking`.
- `WindowStateStore` owns `SessionState`; `ContentStateStore` owns only content payloads.
- `WindowManager` uses `ContentId`/`DockGroupId`; `ShellWindow` exposes `WindowManager`/
  `WindowSystem`, not `mainPane` or T0 `DockRuntime`.
- Custom shells receive `ShellRegionHost` and `OverlayHost`; dockable custom shells must mount `DOCK`.
- Overlay modes, multiwindow close policy, focus precedence and drop routing are normative before runtime
  API freeze.
- Existing `DockArea` interaction behavior is adapted, not rewritten.
- API snapshot resets happen with domain deletion, not in the skeleton PR.

## 7. Test matrix

| Area | Required tests |
|---|---|
| Layout model | builder/XML equality, validation, property/fuzz, normalization |
| Layer | `.instance`/`.ref`/`.dynamic`, ordering, shadow/include, invalidation |
| Shell regions | default/custom regions, contributions, profiles, ownership |
| Actions | one registration/multiple refs, scoped enablement, context filters |
| Docking | split/move/float/redock/minimize/maximize, undo, keyboard |
| Windows | MAIN/FLOATING/CUSTOM, geometry, focus, close, persistence |
| Content | stable node, create/recreate, state, missing content |
| Views/editor | dynamic views, editor adapter, preview/open sources |
| Sources/drop | file/URI/text/multi-file, policy rejection, copy/move/link |
| Nodes | lazy children, custom cells, DnD/rename/multi-select/a11y |
| Theme | mode/accent, scoped/embedded, typed tokens, detached/dialog/popup |
| Design | default shell usable, product restyle, no `!important`, a11y |
| Status | window/area/group/content scopes |
| Overlay | pass-through/HUD/interactive input, focus, theme |
| Extensions | unload/invalidation, ServiceLoader failure containment |
| Persistence | old migration, corrupt/missing content/window/monitor |
| Performance | startup, theme switch, content repaint, render budget, leaks |

## 8. Cut line

Cut in this order:

1. individual usage-scenario fixtures;
2. extra presentations beyond tabbed/single/canvas/overlay;
3. multi-monitor polish;
4. extra design-system components.

Do not cut:

- target model/API reset;
- product/shell/layout/dock/content/theme contracts;
- declarative shell regions/action reuse;
- NBM-only composition;
- reference product;
- no-T0/no-platform-feel acceptance.

## 9. Definition of done

- #104 model implemented with nbfx naming.
- Product-owned layout and shell.
- Declarative shell regions/profiles/contributions/action refs.
- One action registry; actions reusable across all surfaces.
- MAIN/FLOATING/CUSTOM windows with per-window chrome.
- Generic content/views/editor integration.
- Node model/renderer and scoped context menus.
- Theme/brand/design system with scoped/embedded support.
- Sources/drop/overlay/status contracts.
- NBM modules/clusters/kits only; no feature packs.
- Old APIs deleted; consumers migrated.
- Reference product and four scenarios pass on platform-only distribution.
- No T0/DataObject imports in product/API modules.
- All guards, tests, a11y and performance gates green.

## 10. First tasks

1. C0-1: write the #104 alignment ADR and finalize the target naming map.
2. C0-2: create module skeleton only; defer API snapshot resets to domain deletion PRs.
3. C1-1: implement ids, `ViewArea`, `DockGroupSpec`, `LayoutSpec`.
4. C1-2: implement builder/XML/annotations/validation with headless tests.
