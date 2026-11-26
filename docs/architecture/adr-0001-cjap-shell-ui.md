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
# ADR 0001 — CJAP shell and UI architecture

Status: accepted for implementation, 2026-11-24.  
Scope: nbfx platform delivery (CJAP).  
References: [gluonhq/nbfx#104](https://github.com/gluonhq/nbfx/issues/104),
[`../plan/cjap-shell-ui-implementation.md`](../plan/cjap-shell-ui-implementation.md),
[`../reference/cjap-shell-ui.md`](../reference/cjap-shell-ui.md).

## Context

The current shell/layout API is implementation-led: fixed pane roles, view areas, a static docking
singleton, shell branding/chrome providers and text-editor-centric content. It cannot express a
product-owned layout, custom shell, generic content, HUD overlays or reusable chrome declarations
without exposing T0 implementation types.

Issue #104 proposes a region/group/content/policy model. CJAP adopts that model with nbfx naming and
adds a declarative shell-chrome layer above it.

## Decision

### 1. CJAP is the nbfx platform delivery

CJAP is not a separate specification, runtime or product. Its APIs are nbfx APIs shipped in nbfx
modules and releases.

### 2. Product owns layout and shell

The product supplies:

- `Product` metadata;
- a default `LayoutSpec`;
- a `ShellProvider`/`ShellFrame` or the neutral default shell;
- theme/brand/style;
- window chrome profiles.

The framework supplies neutral defaults only.

### 3. #104 model with nbfx names

- `PaneRole` is the stable logical area identity.
- `ViewArea` is the logical region containing zero or more physical groups.
- `DockGroupSpec` describes a physical tabbed/single/canvas/overlay group.
- `LayoutSpec`/`LayoutNode` are pure Java and content-free.
- `DockPolicy` decides transfers; `WindowManager` performs public operations.
- `DockRuntime` is application-scoped; static `Docking` is removed.
- `WindowStateStore` and `ContentStateStore` are separate persistence owners.

### 4. Existing names are reused

Keep `ShellApplication`, `ShellContext`, `ShellWindow`, `WindowManager`, `WindowSystem`,
`WindowState`, `AreaState`, `ViewProvider`, `ViewManager`, `ContentManager`, `EditorDocument`,
`EditorService`, `ActionRegistry`, `ActionContext` and `ViewKind`.

Delete rather than adapt:

- `ShellBranding`;
- `ShellChromeProvider`;
- `WindowPane*`;
- `ViewLocation`;
- public `Docking`/`AppState`/`SessionRestorer`;
- public static `NbfxTheme`;
- duplicate `view` package;
- editor-specific context-menu contributors.

### 5. Shell chrome is declarative

`NbFx/ShellRegions` declares:

- region slots (`BRAND`, `MENUBAR`, `TOOLBAR`, `RIBBON`, `DOCK`, `STATUS`, `OVERLAY`);
- node contributions;
- action references;
- nested menu/ribbon folders;
- profiles, order and style metadata.

`NbFx/ShellProfiles` selects chrome per window.

The `DOCK` region mounts `DockRuntime`. It is mandatory for the default shell and is not an ordinary
contribution region.

### 6. Actions are registered once and placed many times

`@ActionRegistration` owns behaviour under `NbFx/Actions`.

`@ActionReference(path = ...)` owns placement in any menu, toolbar, ribbon, shell region, context
menu or palette. The same command is resolved through `ActionRegistry.resolve(id, Lookup)`.

### 7. Windows and overlays are separate models

`WindowKind` is `MAIN`, `FLOATING` or `CUSTOM`.

- `FLOATING` is a redockable detached group/content window.
- `CUSTOM` is product-defined `WindowContent`/frame.
- `OverlayHost` models HUD/palette layers that are not split leaves.

### 8. Theme, brand and design system are layered

- component token defaults;
- active theme;
- product brand/style;
- area/group style;
- content style;
- plugin scoped style.

`ThemeManager`, `ThemeTokens` and `ThemeScope` replace public static theme state. `platform.design`
is a neutral internal component kit that products may use, restyle or replace.

### 9. NBM is the only composition model

No feature packs, pack manifest, pack registry or pack installer. NBM modules, clusters and kit
modules define composition and installation.

### 10. No new loader dependency

No nbfx UI/API imports `DataObject`, `DataNode` or `openide.loaders`. The platform retains the bridge
for NetBeans core/NBM compatibility. A loader-free distribution is a separate project.

### 11. Review resolution: acyclic module graph

The target dependency direction is:

```text
api.resources (M)       → none
api.product (M)         → api.resources
api.layout (M)          → api.resources, api.layer (T1)
api.theme (T2-FX)       → api.resources, api.layer (T1), JavaFX
api.content (T2-FX)     → api.layout, api.theme, api.resources
api.docking (T2-FX)     → api.layout, api.content
api.views (T2-FX)       → api.layout, api.content, api.theme, api.nodes
api.actions (T2-FX)     → api.layer, JavaFX
api.nodes (M)           → api.resources, api.layer
api.editor (T2-FX)      → api.content, api.layout, api.theme
api.shell (T2-FX)       → api.product, api.layout, api.content, api.views,
                          api.actions, api.theme, api.docking, api.resources
api.plugins (T1)        → api.layer, api.resources
```

`ResourceRef` lives in `api.resources`. `AreaStyle` lives in `api.layout` and references
`ResourceRef`. `ContentStyleContext`, `OverlayHost`, `ContentId`, `DockPlacement` and `TransferMode` live in
`api.content`. `DockPolicySpec`, `DockRequest` and `DockDecision` live in `api.docking`.
`WindowKind`, `WindowOptions`, `DockWindow`, `ShellChromeProfile` and `ChromeProfilePolicy` live in
`api.shell`. `ThemePolicy` lives in `api.theme`; `Product` carries policy ids as neutral strings.
`api.plugins` is T1, not a mixed tier.

### 12. Review resolution: layout schema

- `PaneRole` is only a stable logical-area identity. `DOCKED`, `DETACHED` and `SLIDE` are not area
  identities; they are runtime group/window states and are removed from `PaneRole`.
- Platform defaults are neutral: `PaneRole.MAIN/SIDE/BOTTOM/CENTER` and
  `ViewAreaKind.MAIN/SIDE/BOTTOM/CANVAS/TOOL/CUSTOM`. `EDITOR`, `NAVIGATOR`, `OUTPUT` and
  `PROPERTIES` are consumer registrations, not platform ontology.
- `ViewArea` gains `ViewAreaKind`, default `PresentationId`, capabilities, policy id, `AreaStyle`,
  visibility and order. `DockGroupSpec` may override presentation/policy and carries group lifetime.
- `DockPolicySpec` is immutable data in `api.docking` (accepted kinds/areas/placements/modes). The
  executable `DockPolicy` SPI evaluates a `DockRequest` against the resolved spec.
- `LayoutSpec` contains no executable policy and no content instances.
- `defaultLocation`/`weight` are routing defaults only; the explicit layout tree/dividers win.
- Public records defensively copy all collections.
- Typed ids use one namespaced string form (`namespace:name`) with built-in short forms. Plain
  layout ids such as `product.default` are stable strings, not typed ids.

### 13. Review resolution: persistence ownership

- `WindowState` is one window: layout/groups/content order/active content/geometry/profile.
- `SessionState` is all windows plus the active window.
- `WindowStateStore` owns `SessionState`; `ContentStateStore` owns only per-content payloads keyed by
  `ContentId`/instance key. It does not own order or window placement.
- `DockWindow.state()` returns `WindowState`; `WindowSystem.snapshot()` returns `SessionState`.
- Persistence implementation follows the neutral content-id/state contract; C1-3 is resequenced.
- C4-1 lands the content vocabulary and lifecycle in `api.content` (`ContentId`, `ContentKind`,
  `ContentState`, `Content`, `ContentFactory`, `ContentRequirements`, `ContentManager`) and the
  per-application `DefaultContentManager` in `platform.content`. The per-content persistence owner
  `ContentStateStore` stores only opaque payloads keyed by `ContentId` and instance key; ordering and
  placement stay with the window state. Content sources, drop handling and overlays remain
  C4-3/C6-2; the manager accepts ready-made content until then.
- C4-3 lands the neutral transfer vocabulary in `api.content`: `TransferKind`, `TransferMode`,
  `DockPlacement`, `TransferSource`, `DropTargetKind`, `DropTarget`, `DropRequest`, `DropDecision`
  and `DropHandler`. Routing, registration and drag/drop UI integration follow; an accepted decision
  is authoritative and every rejection carries a reason key.

### 14. Review resolution: runtime ownership and public handles

- One `ShellRuntime` per product application instance owns one `DockRuntime`, `WindowSystem`,
  `ContentManager`, `ViewManager`, `ThemeManager` and focus controller.
- `DockRuntime` is application-scoped and coordinates all MAIN/FLOATING/CUSTOM windows.
- `WindowManager` public operations use `ContentId`/`DockGroupId`, not `Tab`/`WindowPaneHost`.
- `ShellWindow` exposes `Window`, `Parent`, `WindowManager` and `WindowSystem`; it has no `mainPane()`
  and no T0 `DockRuntime` type.
- `DockHost.content()` is read-only. `remove` detaches without disposal; `dispose` detaches host
  content and releases host resources. `ContentManager` owns content close/dispose.
- C3-1 makes `DockRuntime` the per-shell owner of the dock area, slides, composition and
  pane/location lookups, and has `ShellApplication` create, use and dispose it. The legacy static
  `Docking` facade delegates to the most recent runtime until the T8.2 migration removes it, so
  existing readers keep working while runtimes become instance-scoped and testable in isolation.
- C4-2b has `ShellApplication` own one `DefaultContentManager` and publish it in the shell lookup,
  so views, editors and products resolve the same per-shell lifecycle. C4-2c binds view tab
  open/close to it through `ViewContent`; C4-2d does the same for editor documents through an
  internal `EditorContent` adapter. A close without an open content falls back to the view's own
  `viewClosed()` or to editor context unregister. Editor split-clone cleanup stays tab-owned;
  extracting the manager into a `ShellRuntime` and programmatic tab-close via manager follow.
- C3-2a evolves `WindowManager` to the CJAP content/group identity surface (`groups`,
  `primary(PaneRole)`, `groupOf`, `select`, `move`, `attach` on `ContentId`/`DockGroupId`) and
  removes tab/pane handle operations from the public API. NbfxTabPane carries a stable group id;
  DefaultWindowManager resolves content through the per-shell ContentManager.
- C3-2b adds `DockRequest`/`DockDecision`/`DockResult`/`DockModifier` and
  `WindowManager.canDock`/`dock`. The initial implementation accepts INTO_GROUP/TAB_STRIP into
  existing groups and rejects missing content/target or unsupported placements with reason keys;
  split, float, redock, group close/undo and window enumeration remain for later C3 slices. The
  types live in `api.shell` for now to avoid a PaneRole dependency cycle; the target module map
  still places docking contracts in `api.docking`.
- C3-2c adds `DockPolicy` in `api.shell`; `DefaultWindowManager.canDock` consults registered
  policies after structural validation. The first rejection wins; an allowing policy may refine
  the effective decision. Policy discovery currently uses `Lookup.getDefault()` through the
  manager's default constructor. `DockPolicy.allOf`/`anyOf` provide deterministic composition
  (first rejection for all-of, first allowance or first rejection for any-of); richer declarative
  predicates and `api.docking` placement remain follow-ups.
- C3-2d extends `DefaultWindowManager.canDock`/`dock` to SPLIT_LEFT/RIGHT/TOP/BOTTOM and
  EDGE_LEFT/RIGHT/TOP/BOTTOM via `DockArea.split`/`relocate`. Single-tab sources in the area are
  relocated for edge moves or splits beside a different pane; splitting a pane relative to itself
  falls back to remove+split to create a new group.
- C3-2f adds an optional `DockRequest.targetContent` and implements BEFORE/AFTER relative to that
  content in the target group. Missing, foreign-group, or self targets reject with
  `dock.target.missing`; same-pane index adjustment keeps before/after semantics correct when the
  source already precedes the target. Structural validation is centralized and re-run after policy
  placement refinement. FLOAT, NEW_WINDOW, group close/split lifecycle, redock, and undo-per-gesture
  remain follow-ups.
- C3-2g adds `DockArea.detach`/`dockDetached` lifecycle primitives for future float/redock:
  detaching preserves pane identity/content and returns its former anchor; re-docking accepts a
  requested pane/area target and re-establishes emptiness tracking. The main pane, the only pane,
  contained/minimized/set-aside panes, and invalid targets are rejected. The private tree-removal
  helper is renamed `detachNode` so internal collapse/relocate paths keep their original semantics.
- C3-2h adds shell-side floating lifecycle helpers: `NbfxTabPane.floatTab` creates a detached pane
  with the source/editor kind, `showDetachedPane` rejects double-showing, and `closeFloatingPane`
  can preserve pane/document registration for redock while still releasing detached chrome.
  Normal close keeps the existing unregister/close-confirmation behavior. Public `DockWindow`
  contracts and `WindowManager` float/redock operations follow separately.
- C3-2i proves package-private `DefaultWindowManager` float/redock runtime semantics with a
  `FloatingWindow` handle: float content or a non-main group, enumerate live windows, redock all
  tabs into a group (including relative placement) or reattach the whole pane at a split/edge.
  Owner/mismatch validation, stage cleanup, primary-pane retention, and focus restoration are
  covered. `WindowOptions` geometry, owner, modality, title and resizability are applied; icon,
  chrome profile/decoration and bounds persistence remain for the public multi-window slice.
- C4-3c adds `DropRouter` in `platform.content` as the first routing/discovery primitive:
  handlers are discovered from `Lookup.getDefault()` and the `NbFx/DropHandlers` layer path;
  `resolve(...)` tries caller-supplied targets in priority order, returning the first handler
  whose target/source kind matches and whose evaluation is accepted. NbfxTabPane external-drag
  handlers now consult DropRouter for FILES/URIS/TEXT/NODES sources and CONTENT/GROUP/AREA
  targets (CONTENT resolved conservatively from the tab header under the cursor) before the
  legacy file-open fallback. NODES adaptation is conservative (intra-app Node gesture source,
  not a tab drag) and only tried after FILES/URIS/TEXT. Existing behavior is preserved when
  no handler accepts.
  `@DropRegistration`/`@DropRegistrations` write handler instances with target/source metadata
  to `NbFx/DropHandlers`; richer node payload adaptation, accessible rejection announcements,
  and Escape-cancel semantics remain follow-up slices. CONTENT-target drags highlight the
  specific tab header; GROUP/AREA drags retain pane highlight.

### 15. Review resolution: custom-shell composition contract

A custom shell is not just an arbitrary root. `ShellFrame` exposes:

- `root()`;
- `setDockRoot(Node)`;
- `ShellRegionHost regions()`;
- `close()`.

`OverlayHost overlays()` is added in C6-2 when `api.content` exists; C2-5 does not expose a placeholder
in the wrong module.

`ShellRegionHost` lets a custom frame discover, render, update and dispose declarative regions.
A shell presenting dockable content must mount the `DOCK` region or provide an equivalent public
`DockHost` adapter. `ShellRuntime` supplies region/focus lifecycle now and overlay lifecycle when
C6-2 lands, so custom shells do not reimplement default-shell behavior.

### 16. Review resolution: multiwindow and focus state machines

- Floating detaches a group/content without changing content ownership. An empty source group is
  removed or retained by `ViewArea`/product policy.
- `WindowClosePolicy` is explicit: `REDOCK`, `CLOSE_CONTENT` or `HIDE`. The default floating-window
  policy is `REDOCK`; main-window close requests close/redock for all owned windows and may be vetoed
  by dirty content.
- Closing an owner window applies the same policy to owned floating/custom windows.
- Focus has three separate facts: OS-window activation, logical selection and keyboard focus.
  `FocusController` publishes window/group/content context and restores focus after float/redock/
  close/overlay dismissal.
- Transient interactive UI supplies a temporary Lookup override; menus use a context snapshot.

### 17. Review resolution: overlays and drag/drop

- `ShellRegionKind.OVERLAY`, `PresentationId.OVERLAY` and `OverlayHost` are distinct: window overlay
  slot, dockable overlay presentation, and anchored HUD layers.
- Overlay input modes are normative: `PASS_THROUGH` is non-focusable/mouse-transparent; `HUD` is
  informational/non-focusable; `INTERACTIVE` participates in focus/action context. Invalid
  focus/input combinations are rejected.
- Overlays follow their anchor across move/float/redock and are disposed with the owner.
- Internal content/group drags over dock affordances use `DockPolicy`; external/domain drags use
  `DropHandler`. `DropHandler.perform` receives the accepted `DropDecision`.
- Drop coordinates are local to the target root. Escape cancels and restores source/focus.
- Move/split/float/redock have keyboard/command equivalents.

### 17.1 Layout validation invariants

- Split children/dividers have matching arity; an N-child split has N-1 strictly increasing divider
  positions, each a fraction in `[0,1]`.
- A root is a `Split` or `Group`; a zero-content layout is represented by an empty `Group`, not a
  null or `Slot` root.
- Every group reference resolves and occurs once; unreferenced groups are invalid unless marked
  transient. Slot ids are unique within the tree.
- A group belongs to exactly one area; a `Slot` materializes a group in its declared area.
- `ViewArea.defaultPresentation` is inherited by groups unless overridden.
- Empty `DockPolicySpec` sets mean deny-all; an explicit wildcard/default policy means allow-all.
- Group lifetime is `PERSISTENT`, `TRANSIENT` or `PINNED`; transient empty groups are normalized away.

### 18. Review resolution: compatibility and sequencing

This ADR deliberately supersedes #104's public-API compatibility recommendation: nbfx-owned APIs
may break, and old public APIs are deleted after consumer migration. Persisted session formats are
still migrated once with backup and deterministic fallback.

C0-2 creates the target module skeleton only. Existing API snapshots/signatures are reset in the
domain PR that deletes the old API, not before the replacement exists.

The existing `DockArea` interaction implementation is adapted as the first runtime host; its
split/minimize/maximize/drop behavior and tests are preserved.

### 19. Review resolution: chrome-profile composition

- `NbFx/ShellProfiles` contains immutable metadata only; it does not instantiate profile classes.
- `ShellProfileSpec` carries a stable id, optional `WindowKind`/decoration, a region allow-list and
  the system-menu-bar flag. `ShellProfileHint` is the neutral content/group lookup capability.
- `ChromeProfilePolicy` supplies product defaults by window kind and a fallback id. `Product` carries
  only the policy id, not executable shell state. `ChromeProfilePolicyProvider` exposes policies via
  NBM service lookup; exactly one provider must match a present product's id, while no product uses
  neutral defaults.
- `ShellProfileResolver` is pure and returns the selected profile plus effective decoration. Its
  precedence is explicit profile object, content/group hint, product default, policy fallback and
  neutral fallback. Unknown hint/policy ids fall through; duplicate declared ids and a missing
  neutral profile fail. Explicit legacy predicate profiles remain authoritative.
- Region metadata and profile allow-lists are intersected. Neutral region slots have no profile
  restriction so product-defined profiles can reuse them, including the runtime-owned `DOCK`.
- Existing `ShellChromeProfile` predicates remain valid. Their default id is `main`, and the new
  region predicate defaults to inclusive, so C2-6a is source-compatible.
- C2-6b publishes the selected main profile before provider/frame creation, applies the effective
  native/undecorated style before showing, and makes menu/toolbar/shell-region action reads
  profile-aware. Ambiguous multiple explicit profiles/hints fail. Null/blank profile
  queries mean all profiles; absent MIME/language/context dimensions still exclude restricted refs.
- C2-6c adds an optional `WindowState.profileId` with a source-compatible constructor and a
  missing/blank-key fallback in the state store, so a session round-trips the id. Persisted state
  round-trip does not imply restored-frame selection; selecting a restored profile before frame
  creation remains C3-3.

## Consequences

- Public API breaks are accepted and coordinated by domain.
- Old APIs are deleted after consumer migration, not deprecated indefinitely.
- Persisted session data may be migrated once; public compatibility APIs are not retained.
- The default shell becomes a reference implementation, not a subclassing framework.
- Shell, layout, content, theme and extension state are per application instance.
- Implementation starts with pure model/layer contracts before UI runtime changes.

## UX invariants

- product-native appearance is supported without T0 imports;
- keyboard/focus/action behaviour is consistent across default and custom shells;
- every drag/drop/split/float operation has a keyboard/command alternative;
- HUD input never steals map/content gestures unless explicitly interactive;
- empty and first-run layouts are useful;
- all user-visible strings are bundle keys;
- a11y, RTL, high contrast and reduced motion are designed with the interaction model.

## Open questions

- exact `ViewArea` type/template model;
- whether `PaneKind` remains a closed enum;
- final `WindowStateStore` naming;
- `NodeView` module placement;
- cross-window content routing ownership.

These do not block the pure layout/model first slice.
