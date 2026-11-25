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

### 14. Review resolution: runtime ownership and public handles

- One `ShellRuntime` per product application instance owns one `DockRuntime`, `WindowSystem`,
  `ContentManager`, `ViewManager`, `ThemeManager` and focus controller.
- `DockRuntime` is application-scoped and coordinates all MAIN/FLOATING/CUSTOM windows.
- `WindowManager` public operations use `ContentId`/`DockGroupId`, not `Tab`/`WindowPaneHost`.
- `ShellWindow` exposes `Window`, `Parent`, `WindowManager` and `WindowSystem`; it has no `mainPane()`
  and no T0 `DockRuntime` type.
- `DockHost.content()` is read-only. `remove` detaches without disposal; `dispose` detaches host
  content and releases host resources. `ContentManager` owns content close/dispose.

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
- layer-only vs product-metadata shell profiles;
- `NodeView` module placement;
- cross-window content routing ownership.

These do not block the pure layout/model first slice.
