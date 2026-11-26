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
# CJAP Shell/UI target reference

Status: target design, 2026-11-24.
Applies to: the nbfx platform delivery (CJAP).
Implementation plan: [`../plan/cjap-shell-ui-implementation.md`](../plan/cjap-shell-ui-implementation.md).
Alignment source: [gluonhq/nbfx#104](https://github.com/gluonhq/nbfx/issues/104).

This document is the target reference for the nbfx shell, layout, content, chrome, window,
theming and UI extension APIs. It describes the intended public contracts and declarative
layer model. It is not a claim that the APIs already exist.

## 1. Scope and goals

CJAP lets a product:

- define its identity, icons, version, About data and packaging metadata;
- own its window layout and visible shell;
- use a neutral default shell or provide a custom `ShellFrame`;
- compose menus, toolbars, ribbons, branding bars, status bars and overlays declaratively;
- register actions once and reference them from every surface;
- host arbitrary content, views, editor documents and HUDs;
- float content into standard or custom windows;
- style every surface through themes, brand layers and scoped styles;
- use NBM modules/clusters/kits as the only composition mechanism;
- use only public API/SPI, never T0 implementation packages.

CJAP is the nbfx platform delivery, not a separate specification or runtime.

## 2. #104 alignment and naming

| #104 | CJAP/nbfx target |
|---|---|
| `RegionId` | `PaneRole` |
| `RegionSpec`/region type | `ViewArea` |
| `DockGroupId`/`DockGroupSpec` | same |
| `LayoutSpec`/`LayoutNode` | same |
| `Slot` | `LayoutNode.Slot` |
| `DockPolicy`/`DockRequest`/`DockDecision` | same |
| `Content`/`ContentFactory`/`ContentState` | same |
| `DockHost` | same |
| `DockController` | internal controller behind `WindowManager` |
| `DockWindow` | same |
| `LayoutStore` | `WindowStateStore` |
| `ContentStateStore` | same |
| builder + annotations + XML | same, no DSL |
| static `Docking` | application-scoped `DockRuntime` |

Additional CJAP concepts:

- `ShellRegion` — window chrome slot, separate from content `ViewArea`;
- `ShellChromeProfile` — per-window chrome configuration;
- `OverlayHost` — HUD/palette layers that are not split leaves;
- `Product` — product metadata, not a runtime plugin;
- `ThemeScope` — scoped/embedded styling.

## 3. Conceptual model

```text
Product
  identity/icons/style/theme policy/About
  layoutId + shellId + chromeProfileId

ShellApplication
  ShellRuntime
    ShellContext
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
  LayoutSpec
    ViewArea (logical region)
      DockGroup (physical group)
        DockHost (presentation)
          Content

WindowManager
  move/split/float/redock/select

WindowSystem
  load/save/reset/restore

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

## 4. Module and tier boundaries

### API modules

| Module | Tier | Responsibility |
|---|---|---|
| `api.resources` | M | `ResourceRef` and neutral resource references |
| `api.product` | M | product metadata |
| `api.shell` | T2-FX | shell/frame/context/window/chrome/profiles |
| `api.layout` | M | layout/area/group/builder/XML/style refs |
| `api.docking` | T2-FX | hosts/policy/transfer |
| `api.content` | T2-FX | content/id/factory/state/sources/drop/overlay host/transfer vocabulary |
| `api.views` | T2-FX | views/registry/manager/NodeView |
| `api.actions` | T2-FX | commands/registry/references |
| `api.nodes` | M | explorer model |
| `api.editor` | T2-FX | editor document/service |
| `api.theme` | T2-FX | theme manager/tokens/scope/policy |
| `api.workspace` | M | product workspace |
| `api.settings` | M | settings |
| `api.dialogs` | T2-FX | dialogs/notifications/About |
| `api.diagnostics` | M | diagnostics/event model |
| `api.plugins` | T1 | contribution ownership/lifecycle |
| `api.layer` | T1 | typed layer reader/registry |
| `api.status` | T2-FX | status elements/registry/scopes |

### Implementation modules

| Module | Responsibility |
|---|---|
| `platform.shell` | shell runtime/window/focus/composition |
| `platform.shell.default` | neutral default shell and chrome renderers |
| `platform.docking` | dock runtime/hosts/transfer/persistence |
| `platform.content` | content manager/registry/routing |
| `platform.views` | view registry/manager/routing |
| `platform.design` | neutral design system |
| `platform.nodes` | NodeView/property sheet |
| `platform.theme` | ThemeManager implementation |
| `platform.product` | product metadata/build integration |
| `platform.workspaces` | workspace implementation |
| `platform.settings` | settings implementation |
| `platform.dialogs` | dialogs/notifications/About |
| `platform.diagnostics` | diagnostics implementation |
| `platform.plugins` | contribution/plugin lifecycle |

API modules never depend on implementation modules. Product modules depend only on API/SPI.

Dependency direction is acyclic:

```text
api.resources (M)       → none
api.product (M)         → api.resources
api.layout (M)          → api.resources, api.layer
api.theme (T2-FX)       → api.resources, api.layer
api.content (T2-FX)     → api.layout, api.theme, api.resources
api.docking (T2-FX)     → api.layout, api.content
api.views (T2-FX)       → api.layout, api.content, api.theme
api.actions (T2-FX)     → api.layer
api.nodes (M)           → api.resources, api.layer
api.editor (T2-FX)      → api.content, api.layout, api.theme
api.shell (T2-FX)       → product/layout/content/views/actions/theme/docking/resources
api.plugins (T1)        → api.layer, api.resources
```

## 5. Product metadata

### 5.1 Contract

```java
public interface ProductProvider {
    Product product();
}
```

Exactly one `ProductProvider` is allowed per distribution. `ProductProvider.resolve()` uses the
global lookup, and `ProductProvider.resolve(Lookup)` supports explicit composition/test lookups.
Zero providers, multiple providers and a null product are errors. There is no `priority()`,
superseding or class-name selection.

```java
public record Product(
        ProductIdentity identity,
        ProductIcons icons,
        ProductStyle style,
        String themePolicyId,
        String layoutId,
        String shellId,
        String chromeProfilePolicyId,
        List<DesktopAssociation> associations,
        AboutInfo about) {}
```

`Product` is immutable metadata. It does not contain live `ShellProvider` or `LayoutProvider`
objects. Product modules register their public no-argument provider with
`@ServiceProvider(service = ProductProvider.class)`; the composition root resolves it before shell
creation.

Identity id/name/version and all four default policy/layout/shell/profile ids are required and
nonblank. `ProductIdentity.name` is localized display text supplied by the provider; it is not a
bundle-key reference. Optional blank text is treated as absent. Website/support URIs must be absolute. All
collection fields are required, defensively copied and preserve order. `ResourceRef` values are not
resolved by this API. A desktop association requires an action id and at least one extension, MIME
type or URI scheme.

### 5.2 Product identity

```java
public record ProductIdentity(
        String id,
        String name,
        String version,
        String vendor,
        String license,
        URI website,
        String copyright) {}
```

### 5.3 Product icons and style

```java
public record ProductIcons(
        List<ResourceRef> windowIcons,
        ResourceRef aboutLogo,
        List<ResourceRef> installerIcons) {}

public record ProductStyle(
        List<ResourceRef> stylesheets,
        List<ResourceRef> tokenResources,
        List<String> rootStyleClasses) {}
```

### 5.4 Theme policy

`ThemePolicy` lives in `api.theme`; `Product` carries its stable id.

```java
public record ThemePolicy(
        String id,
        String defaultTheme,
        ThemeMode mode,
        boolean userSelectable,
        List<String> shippedAccents) {}
```

### 5.5 Chrome profile policy

`ChromeProfilePolicy` lives in `api.shell`; `Product` carries its stable id.

```java
public record ChromeProfilePolicy(
        String id,
        Map<WindowKind, String> defaultProfiles,
        String fallbackProfile) {}
```

The product defines a default profile per `WindowKind`; explicit `WindowOptions` and content/group
hints override it. A product module registers a public no-argument `ChromeProfilePolicyProvider`
with `@ServiceProvider(service = ChromeProfilePolicyProvider.class)`. The composition root resolves
exactly one provider whose policy id matches `Product.chromeProfilePolicyId()`. A missing product
uses neutral defaults; a product with no matching policy provider, duplicate product providers,
duplicate matching policies and null provider results fail deterministically.

### 5.6 About and associations

```java
public record AboutInfo(
        String description,
        List<String> credits,
        List<ResourceRef> notices,
        URI support) {}

public record DesktopAssociation(
        String extension,
        String mimeType,
        String uriScheme,
        String actionId) {}
```

### 5.7 Build metadata

A single product metadata source drives:

- runtime `Product`;
- window title/icon;
- About/diagnostics;
- installer name/icon/vendor/version;
- desktop entries/file associations;
- default theme/layout/profile;
- product namespace.

Product family/variant selection is build tooling, not runtime API.

## 6. Layout model

### 6.1 `PaneRole`

`PaneRole` is the stable identity of a logical `ViewArea`.

```java
public final class PaneRole {
    public static final PaneRole MAIN = ...;
    public static final PaneRole SIDE = ...;
    public static final PaneRole BOTTOM = ...;
    public static final PaneRole CENTER = ...;

    public static PaneRole of(String value);
    public String value();
}
```

`EDITOR`, `NAVIGATOR`, `OUTPUT` and `PROPERTIES` are consumer-defined area roles, not platform
constants.

`PaneRole` is only a logical-area identity. `DOCKED`, `DETACHED` and `SLIDE` are runtime
group/window states, not area identities, and are removed from this type.

Product roles use namespaced stable ids (`namespace:name`). Persisted layouts refer to roles, not
class names.

### 6.2 `ViewAreaKind`

```java
public final class ViewAreaKind {
    public static final ViewAreaKind MAIN = ...;
    public static final ViewAreaKind SIDE = ...;
    public static final ViewAreaKind BOTTOM = ...;
    public static final ViewAreaKind CANVAS = ...;
    public static final ViewAreaKind TOOL = ...;
    public static final ViewAreaKind CUSTOM = ...;

    public static ViewAreaKind of(String value);
    public String value();
}
```

`ViewAreaKind` supplies default area behaviour. It is distinct from `PaneKind` (group semantics),
`ContentKind` (runtime content) and `ViewKind` (view menu classification).

### 6.3 `ViewArea`

`ViewArea` is the #104 logical region. It contains zero or more `DockGroup`s.

```java
public record ViewArea(
        PaneRole id,
        ViewAreaKind kind,
        String displayName,
        DockLocation defaultLocation,
        double weight,
        PresentationId defaultPresentation,
        Set<ViewAreaCapability> capabilities,
        String policyId,
        AreaStyle style,
        ViewAreaVisibility visibility,
        EmptyAreaPolicy emptyPolicy,
        int order) {}
```

Capabilities include:

```java
CLOSABLE, MOVABLE, REORDERABLE, DETACHABLE,
MINIMIZABLE, MAXIMIZABLE, SPLITTABLE, COLLAPSIBLE,
ACCEPTS_DROPS, ACCEPTS_FILES, SCROLLABLE
```

Visibility:

```java
STARTUP, ON_DEMAND, ALWAYS
```

Empty-state behavior is separate:

```java
public enum EmptyAreaPolicy {
    KEEP,
    HIDE,
    COLLAPSE
}
```

The framework registers neutral defaults. Products register their own areas.

`DockLocation` is a logical routing default (`CENTER`, `START`, `END`, `TOP`, `BOTTOM`). The default
shell maps `START`/`END` to visual left/right according to orientation/RTL. Explicit layout geometry
always wins over area routing defaults.

### 6.4 `DockGroupSpec`

```java
public record DockGroupSpec(
        DockGroupId id,
        PaneRole area,
        PaneKind kind,
        PresentationId presentation,
        ContentCapacity capacity,
        String policyId,
        GroupLifetime lifetime,
        AreaStyle style) {}
```

`PaneKind` remains the closed semantic kind:

```java
public enum PaneKind {
    EDITOR,
    VIEW
}
```

`PresentationId` is an extensible id:

```java
public record PresentationId(String namespace, String name) {
    public static final PresentationId TABBED = ...;
    public static final PresentationId SINGLE = ...;
    public static final PresentationId CANVAS = ...;
    public static final PresentationId OVERLAY = ...;
    public static PresentationId of(String value);
}
```

`ContentCapacity`:

```java
NONE, ONE, MANY
```

`GroupLifetime`:

```java
PERSISTENT, TRANSIENT, PINNED
```

### 6.5 `LayoutSpec` and `LayoutNode`

```java
public record LayoutSpec(
        String id,
        int formatVersion,
        Map<PaneRole, ViewArea> areas,
        Map<DockGroupId, DockGroupSpec> groups,
        LayoutNode root,
        Map<String, String> metadata) {}
```

```java
public sealed interface LayoutNode {
    record Split(SplitOrientation orientation,
                 List<LayoutNode> children,
                 List<Double> dividers) implements LayoutNode {}

    record Group(DockGroupId groupId) implements LayoutNode {}

    record Slot(SlotId id, PaneRole area, DockSide side) implements LayoutNode {}
}
```

Rules:

- layout contains no view/content ids;
- a layout with zero content is valid;
- editor splits create groups in the same area;
- `Slot` materializes a group at runtime;
- validation rejects duplicate ids, missing refs, cycles and invalid dividers.

Validation invariants:

- split children/dividers have matching arity; an N-child split has N-1 strictly increasing divider
  positions, each a fraction in `[0,1]`;
- root is a `Split` or `Group`; a zero-content layout uses an empty `Group`, not null or a `Slot` root;
- every group reference resolves; unreferenced groups are invalid unless transient;
- a group belongs to exactly one area; a `Slot` materializes a group in its declared area;
- area default presentation is inherited unless the group overrides it;
- empty `DockPolicySpec` sets mean deny-all; allow-all requires an explicit wildcard/default policy.

`LayoutSpec.normalized()` returns an idempotent canonical copy: unreferenced transient groups are
removed, areas are ordered by `order`/id, groups by id, metadata by key, and signed-zero dividers are
canonicalized. Invalid geometry cannot be constructed and is rejected rather than silently clamped.

### 6.6 Authoring

Typed ids use `namespace:name`; built-in constants also accept case-insensitive short and canonical
forms. Plain layout ids such as `product.default` are stable strings, not typed ids.

Canonical builder:

```java
LayoutSpec layout = LayoutBuilder.builder("product.default", 1)
    .area(new ViewArea(PaneRole.of("product:side"), ViewAreaKind.SIDE, "#side",
        DockLocation.START, 0.25d, PresentationId.TABBED, Set.of(),
        "default", AreaStyle.EMPTY, ViewAreaVisibility.STARTUP, EmptyAreaPolicy.KEEP, 0))
    .area(new ViewArea(PaneRole.of("product:editor"), ViewAreaKind.MAIN, "#editor",
        DockLocation.CENTER, 1.0d, PresentationId.TABBED, Set.of(),
        "default", AreaStyle.EMPTY, ViewAreaVisibility.STARTUP, EmptyAreaPolicy.KEEP, 1))
    .group(new DockGroupSpec(DockGroupId.of("product:side.main"), PaneRole.of("product:side"),
        PaneKind.VIEW, PresentationId.TABBED, ContentCapacity.MANY, "default",
        GroupLifetime.PERSISTENT, AreaStyle.EMPTY))
    .group(new DockGroupSpec(DockGroupId.of("product:editor.main"), PaneRole.of("product:editor"),
        PaneKind.VIEW, PresentationId.TABBED, ContentCapacity.MANY, "default",
        GroupLifetime.PERSISTENT, AreaStyle.EMPTY))
    .split(SplitOrientation.HORIZONTAL, List.of(0.25d),
        LayoutNode.group("product:side.main"),
        LayoutNode.group("product:editor.main"))
    .build();
```

Layout XML is standalone application data. It is not itself a folder-style layer registration;
`@LayoutRegistration` embeds a complete document as a generated layer resource:

```xml
<layout id="product.default" version="1">
  <areas>
    <area id="product:explorer" kind="SIDE" displayName="#explorer" presentation="tabbed"
          visibility="startup" capabilities="minimizable closable movable detachable">
      <style>
        <class name="explorer"/>
        <stylesheet module="org.example.product" path="styles/explorer.css"/>
        <themeOverlay module="org.example.theme" path="overlays/dark.css"/>
      </style>
    </area>
    <area id="product:editor" kind="MAIN" displayName="#editor" presentation="tabbed"
          visibility="startup" capabilities="maximizable splittable detachable reorderable"/>
  </areas>
  <groups>
    <group id="product:editor.main" area="product:editor" capacity="many" presentation="tabbed"/>
    <group id="product:explorer.main" area="product:explorer" capacity="many" presentation="tabbed"/>
  </groups>
  <tree>
    <split orientation="horizontal" dividers="0.25">
      <group ref="product:explorer.main"/>
      <group ref="product:editor.main"/>
    </split>
  </tree>
  <metadata>
    <entry key="owner" value="product"/>
  </metadata>
</layout>
```

Required attributes and defaults:

| Element | Required | Defaults |
|---|---|---|
| `layout` | `id`, `version`, `tree` | `areas`, `groups` and `metadata` may be omitted |
| `area` | `id`, `displayName` | `kind=MAIN`, `location=CENTER`, `weight=1.0`, `presentation=tabbed`, empty capabilities, `policyId=default`, `visibility=STARTUP`, `emptyPolicy=KEEP`, flattened declaration index for `order` |
| `group` | `id`, `area` | `kind=VIEW`, `capacity=MANY`, `lifetime=PERSISTENT`; presentation/policy inherit from the area |
| tree `group` | `ref` | none |
| tree `slot` | `id`, `area` | `side=CENTER`; slots are allowed only below the root |
| tree `split` | `dividers`, at least two children | `orientation=HORIZONTAL` |
| `style` | none | optional `class`, `stylesheet` and `themeOverlay` children |
| metadata `entry` | `key`, `value` | `value` may be empty |
| `include` | `path` | allowed only in `areas`/`groups`; maximum nesting 64 |

`LayoutXml.read(InputStream)` and `LayoutXml.write(LayoutSpec, OutputStream)` provide the standard XML
form. The caller owns and closes the main stream. Only format version `1` is currently supported.
Capability lists accept commas or whitespace; enum values and built-in short/canonical ids are
case-insensitive. Product namespaces remain case-sensitive. `displayName` is required so the XML
never invents a user-visible or accessible name from an internal id.

A group that omits `presentation` or `policyId` inherits the owning area's value. The optional
`<style>` child preserves class names, stylesheets and theme overlays for both areas and groups.
`<metadata>` preserves opaque string entries. Output is deterministic: areas are ordered by
`order`/id, groups by id and metadata by key; tree and resource-list order is preserved.

`<include path="..."/>` is allowed inside `<areas>` and `<groups>`. The included document root must
be `<areas>` or `<groups>` respectively. `LayoutXml.read(input, resolver)` resolves includes through
`LayoutXml.IncludeResolver`; the API performs no implicit filesystem, classpath or network access.
Resolver streams are closed by `LayoutXml`, include cycles are rejected, nesting is limited to 64
levels, and the no-resolver overload rejects documents containing includes.

Parsing disables DTDs, external entities, XInclude and external XML resources. Unknown attributes or
elements, duplicate ids, repeated group references, repeated slot ids and invalid values are rejected.
The root must be a `Split` or `Group`. Layout-value diagnostics use XPath-like locations such as
`/layout/areas/area[1]/@weight`; malformed XML is reported as an `IOException`. No custom layout DSL
is introduced.

Declarative registration uses the source-retained `@LayoutRegistration(id, xml)` annotation. `xml` is a
complete self-contained version 1 layout document, and its layout id must equal `id`. Includes are not
allowed in annotation XML. The processor validates the document, checks `#key` display names against
the annotated package's `Bundle.properties`, canonicalizes the resource to UTF-8 and writes
`NbFx/Layouts/<id>.xml`; declaration classes are not instantiated. Repeated declarations use
`@LayoutRegistrations`. Duplicate ids within one compilation are errors; across modules the standard
layer merge precedence applies, so products should use globally unique ids.

`LayoutRegistry.discover()` returns all valid registered layouts ordered by id, and
`LayoutRegistry.find(id)` returns one. Discovery is stateless, performs no implicit I/O beyond the
configured layer, skips malformed or id-mismatched resources with a log warning, and preserves the
same immutable `LayoutSpec` equality as the builder and XML paths.

Standalone neutral area metadata uses `@ViewAreaRegistration` and `@ViewAreaRegistrations`. The
annotation mirrors the scalar `ViewArea` contract, canonicalizes `PaneRole`/`ViewAreaKind`/
`PresentationId` values, validates `#key` names and writes `NbFx/LayoutAreas/<canonical-role>`.
`LayoutRegistry.areas()` and `findArea(id)` reconstruct immutable areas with `AreaStyle.EMPTY`; styled
areas and complete trees use `@LayoutRegistration`.

Standalone physical-group metadata uses `@DockGroupRegistration` and `@DockGroupRegistrations`.
Ids and owning areas are canonicalized, explicit presentation/policy overrides are preserved, and
empty overrides inherit from the resolved area. The processor writes `NbFx/DockGroups/<id>`;
`LayoutRegistry.groups()` and `findGroup(id)` reconstruct immutable `DockGroupSpec` values with
`AreaStyle.EMPTY`. An owning area may come from another module; unresolved areas are skipped with a
log warning. Area, group and layout catalogs are independent and are not implicitly merged.

## 7. Docking policy and transfer

### 7.1 Policy specification and evaluation

`DockPolicySpec` is immutable `api.docking` data; `DockPlacement` and `TransferMode` are neutral
`api.content` values shared by docking and drop handlers:

```java
public record DockPolicySpec(
        Set<ContentKind> acceptedKinds,
        Set<PaneRole> acceptedAreas,
        Set<DockPlacement> placements,
        Set<TransferMode> modes,
        boolean allowCrossArea,
        boolean allowFloat) {}
```

The executable SPI lives in `api.docking`:

```java
public record DockRequest(
        ContentId content,
        DockGroupId sourceGroup,
        PaneRole targetArea,
        DockGroupId targetGroup,
        ContentId targetContent,
        DockPlacement placement,
        TransferMode mode,
        Set<DockModifier> modifiers) {}

public record DockDecision(
        boolean allowed,
        DockPlacement placement,
        String reasonKey) {}

@FunctionalInterface
public interface DockPolicy {
    DockDecision evaluate(DockRequest request);
}
```

Built-in policies are composed from small predicates. Rejections always carry a reason key.

### 7.2 Placements

```java
INTO_GROUP, BEFORE, AFTER, TAB_STRIP,
SPLIT_LEFT, SPLIT_RIGHT, SPLIT_TOP, SPLIT_BOTTOM,
EDGE_LEFT, EDGE_RIGHT, EDGE_TOP, EDGE_BOTTOM,
FLOAT, NEW_WINDOW
```

`BEFORE` and `AFTER` use `DockRequest.targetContent` as the relative tab target in the target group;
missing, foreign-group, and self targets are rejected with `dock.target.missing`. `FLOAT` uses the
shell's default floating-window options; `NEW_WINDOW` remains unsupported until its distinct
semantics are defined.

### 7.3 Hosts

```java
public interface DockHost {
    Node node();
    ContentCapacity capacity();
    List<Content> content();          // unmodifiable snapshot
    void add(Content content, int index);
    Content remove(Content content);  // detach only; does not close/dispose
    ObservableValue<Boolean> empty();
    HostState state();                // EMPTY, LOADING, MISSING, ERROR
    void dispose();                   // detach content; release host resources only
}
```

Hosts do not know other hosts. Transfer is performed by the dock controller behind
`WindowManager`. `ContentManager` owns content close/dispose; host disposal never silently destroys
content.

### 7.4 `WindowManager`

`WindowManager` remains the public operation surface:

```java
public interface WindowManager {
    List<DockGroupId> groups();
    Optional<DockGroupId> primary(PaneRole area);

    DockDecision canDock(DockRequest request);
    DockResult dock(DockRequest request);

    void attach(ContentId content, DockGroupId group, int index);
    void move(ContentId content, DockGroupId target, int index);
    void select(ContentId content);

    DockWindow floatContent(ContentId content, WindowOptions options);
    DockWindow floatGroup(DockGroupId group, WindowOptions options);
    DockResult redock(DockWindow window, DockRequest request);

    List<DockWindow> windows();
}
```

Public operations use content/group handles. The current runtime enumerates live floating windows;
main/custom window enumeration is completed with the multi-window slice. `Tab` and `WindowPaneHost` are not target API types.

## 8. Windows and chrome profiles

### 8.1 `WindowKind`

```java
public enum WindowKind {
    MAIN,
    FLOATING,
    CUSTOM
}
```

- `MAIN`: primary shell window.
- `FLOATING`: detached dock group/content, redockable.
- `CUSTOM`: product-defined window content/frame.

### 8.2 `DockWindow`

`DockWindow`, `WindowKind`, `WindowOptions` and `WindowContent` are `api.shell` contracts.

```java
public interface DockWindow {
    WindowId id();
    WindowKind kind();
    Window window();
    LayoutNode layout();
    WindowContent content();
    void show();
    void hide();
    void close();
    WindowState state();
}
```

A `CUSTOM` window may have no `LayoutNode`; its `WindowContent` owns the scene graph.

```java
public record WindowId(String namespace, String name) {}

public record WindowContentState(
        WindowId windowId,
        LayoutNode layout,
        Map<String, String> payload) {}
```

`WindowContentState.payload` is opaque to the shell and owned by the content implementation.

### 8.3 `WindowContent`

```java
public interface WindowContent extends AutoCloseable {
    Node root();
    Lookup lookup();
    WindowContentState saveState();
}
```

### 8.4 `WindowOptions`

```java
public record WindowOptions(
        WindowKind kind,
        ShellChromeProfile profile,
        String title,
        ResourceRef icon,
        Window owner,
        boolean modal,
        double width,
        double height,
        boolean resizable,
        boolean rememberBounds,
        WindowDecoration decoration) {}
```

### 8.5 Chrome profiles

`ShellChromeProfile` selects shell regions and entries per window. Existing predicate-based profiles
remain valid: `id()` defaults to `main` and `includeRegion(...)` defaults to `true`.

Declarative profile:

```java
public record ShellProfileSpec(
        String id,
        WindowKind windowKind,
        Set<ShellRegionId> regions,
        WindowDecoration decoration,
        boolean systemMenuBar) implements ShellChromeProfile {}
```

```xml
<folder name="floating-editor">
    <attr name="windowKind" stringvalue="FLOATING"/>
    <attr name="regions" stringvalue="nbfx:toolbar,nbfx:dock,nbfx:status,nbfx:overlay"/>
    <attr name="decoration" stringvalue="NATIVE"/>
    <attr name="systemMenuBar" boolvalue="false"/>
</folder>
```

Profiles live under `NbFx/ShellProfiles`. `@ShellProfileRegistration` is repeatable and generates the
same folder schema; its region values accept built-in short forms and canonicalize them. Profile ids
are case-sensitive atomic tokens. A profile folder id must additionally be file-safe. `windowKind`
and `decoration` are optional enum values; `systemMenuBar` defaults to `true`. A missing/empty `regions`
attribute adds no profile-level restriction. Present-but-wrong-typed or invalid attributes make only
that profile malformed; discovery logs and skips it without affecting other profiles.

Region applicability is intersected in two steps: `ShellRegionRegistry.forProfile(id)` applies the
region's own `profiles` metadata, then `ShellRegionHost.regions(profile)` applies
`ShellProfileSpec.includeRegion(...)`. An empty allow-list does not hide regions. Neutral built-in
slots therefore have empty region profile sets and can be reused by product-defined profiles; each
neutral profile narrows those slots with its own allow-list. `DOCK` remains runtime-owned.

Default profiles:

- `main`: brand/menu/toolbar/dock/status/overlay;
- `floating-editor`: toolbar/dock/status/overlay;
- `floating-tool`: toolbar/dock/status/overlay;
- `presentation`: dock/overlay only, undecorated;
- product-defined profiles.

`ShellProfileHint(profileId)` is the neutral lookup capability for content/group defaults.
`ChromeProfilePolicy` carries immutable product defaults by `WindowKind` plus a fallback profile id;
`Product.chromeProfilePolicyId()` names that policy without embedding live shell objects in product
metadata. `ChromeProfilePolicyProvider` exposes immutable policies through NBM service lookup.

`ShellProfileResolver` is pure and returns `ShellProfileSelection(profile, decoration)`. Selection
precedence is:

1. explicit `WindowOptions.profile` (an authoritative profile object, including legacy predicates);
2. content/group `ShellProfileHint`;
3. product default for `WindowKind`;
4. policy fallback;
5. platform neutral fallback (`main`, `floating-editor` or `presentation`).

Unknown hint/policy ids fall through to the next source. Duplicate declared profile ids and a missing
neutral fallback fail. `WindowOptions.decoration()` overrides the selected `ShellProfileSpec`
decoration; otherwise a legacy profile uses `NATIVE`.

C2-6b publishes the selected main-window profile in `ShellContext.lookup()` before provider/frame
creation and applies `NATIVE`/`UNDECORATED` before the stage is shown. At most one explicit profile
and one hint may be present in the startup lookup; ambiguous lookup composition fails. The legacy
subclass chrome path is unchanged. Menu/toolbar builders use `ActionLayerReader.readForProfile`; shell-region action reads
merge the selected profile into `ActionContextMetadata`. Null/blank profile queries mean all profiles,
while missing MIME/language/context values still exclude references restricted on those dimensions.

C2-6c adds optional `WindowState.profileId` round-trip. Restored-profile frame selection before window
creation belongs to C3-3; state round-trip must not be presented as complete multi-window restore.

## 9. Shell frame and declarative chrome

### 9.1 `ShellProvider` and `ShellFrame`

```java
public interface ShellProvider {
    ShellFrame create(ShellContext context, ShellChromeProfile profile);
}

public interface ShellFrame extends AutoCloseable {
    Node root();
    void setDockRoot(Node dockRoot);
    ShellRegionHost regions();
}

public interface ShellRegionHost {
    List<ShellRegionSpec> regions(ShellChromeProfile profile);
    Node mount(ShellRegionSpec region, ShellRegionContext context);
    void update(ShellRegionSpec region, Node mounted);
    void dispose(ShellRegionSpec region, Node mounted);
}
```

Region hosts keep a stable wrapper Node across updates, apply style/visibility metadata, create fresh
contribution Nodes on the JavaFX Application Thread, reject null/parented results, isolate failures,
and dispose renderer/contribution/scoped-action resources idempotently. Empty overlay layers do not
pick up pointer bounds. Any region whose kind is `DOCK` mounts the exact runtime dock root, applies
its style/visibility metadata and never loads ordinary contributions. Region wrappers expose semantic
accessible names and menu/toolbar roles.

`ShellApplication` is the default composition root. Products do not subclass it; they provide a
`ShellProvider` or use the default. Zero providers select the neutral default frame, one selects the
product frame, and more than one fails before frame creation; there is no priority or silent fallback.
The runtime region host is published in `ShellContext.lookup()` before provider creation, and a custom
frame must return that same host and mount the exact runtime dock node beneath its stable, unparented
root. The default/custom paths share toolbar persistence and View-menu toolbar controls. Existing
IDE/test subclasses retain the legacy chrome hooks until C8 migration. C2-5 lands this frame contract
without `overlays()`;
`ShellFrame.overlays()` is added in C6-2 together with `OverlayHost` in `api.content`, so no
placeholder is exposed in the wrong module.

### 9.2 `ShellContext`

```java
public record ShellContext(
        Product product,
        ShellWindow window,
        WindowManager windowManager,
        WindowSystem windowSystem,
        ContentManager contentManager,
        ViewManager viewManager,
        ActionRegistry actionRegistry,
        ActionContext actionContext,
        ThemeManager themeManager,
        Lookup services) {}
```

All optional services have explicit defaults or `Optional` access; no deprecated overloads.

### 9.3 `ShellWindow`

```java
public interface ShellWindow {
    Window window();
    Parent root();
    WindowManager windowManager();
    WindowSystem windowSystem();
}
```

The facade does not expose `BorderPane`, `TabPane` or concrete shell implementation types.

### 9.4 Shell regions

`ShellRegion` is chrome, not content layout.

```java
public record ShellRegionId(String namespace, String name) {}
public record ShellRegionKind(String id) {}

public record ShellRegionSpec(
        ShellRegionId id,
        ShellRegionKind kind,
        int position,
        Set<String> profiles,
        AreaStyle style,
        boolean visibleByDefault) {}
```

Built-in ids and kinds:

```text
nbfx:brand, nbfx:menubar, nbfx:toolbar, nbfx:ribbon,
nbfx:dock, nbfx:status, nbfx:overlay
```

Short forms such as `BRAND`/`brand` are accepted case-insensitively. Product-defined ids/kinds are
allowed and require `namespace:name`. The neutral default shell declares reusable `BRAND`, `MENUBAR`,
`TOOLBAR`, `DOCK`, `STATUS` and `OVERLAY` slots with no region-level profile restriction; neutral and
product profiles select them through their allow-lists. `RIBBON` is supported but product opt-in.
`ShellRegionRegistry.discover()`, `find(id)` and
`forProfile(profileId)` return immutable metadata snapshots. Empty profile sets mean all profiles;
non-empty sets match exact case-sensitive atomic ids (no commas or whitespace). `visibleByDefault` is
independent of discovery. Present-but-wrong-typed layer attributes make the region malformed; they do
not silently broaden visibility, profiles or ordering.

### 9.5 Declarative layer

```text
NbFx/ShellRegions/
  nbfx:brand/
  nbfx:menubar/
    File/
  nbfx:toolbar/
    main/
  nbfx:ribbon/
    Home/
      Clipboard/
  nbfx:dock/
  nbfx:status/
  nbfx:overlay/
```

Region metadata:

```xml
<folder name="nbfx:brand">
    <attr name="kind" stringvalue="nbfx:brand"/>
    <attr name="position" intvalue="10"/>
    <attr name="profiles" stringvalue="main"/>
    <attr name="styleClass" stringvalue="acme-brand"/>
    <attr name="visibleByDefault" boolvalue="true"/>
</folder>
```

Node contribution:

```xml
<file name="product.instance"
      instanceClass="org.example.ProductBrandBar"
      position="10"
      profiles="main"/>
```

Action reference:

```xml
<file name="save.ref"
      actionId="save"
      position="20"/>
```

Nested ribbon/menu folders:

```xml
<folder name="nbfx:ribbon">
    <attr name="kind" stringvalue="nbfx:ribbon"/>
    <folder name="Home">
        <folder name="Clipboard">
            <file name="paste.ref" actionId="paste" position="10"/>
        </folder>
    </folder>
</folder>
```

### 9.6 Annotations

```java
@ShellRegionRegistration(
    id = "BRAND",
    kind = "BRAND",
    position = 10,
    profiles = {"main"})
public final class BrandRegionDeclaration {}
```

```java
@ShellContributionRegistration(region = "brand", position = 10, profiles = {"main"})
public final class ProductBrandBar implements ShellContribution {
    @Override public Node create(ShellContext context) { ... }
    @Override public void dispose() { ... }
}
```

The layer owns region/position/profile metadata. The annotation accepts strings so product-defined
kinds do not require a platform constants class. `styleClass` is one CSS class token and maps to the
region's `AreaStyle` class set; richer style resources are authored in layer XML. The contribution does not hardcode placement.

`ShellContributionRegistry.discover()` reads metadata without constructing classes or Nodes.
`discover(region, profileId)` filters by exact atomic profile ids before construction, and
`load(region, profileId)` creates fresh per-shell instances. Returned entries are never cached or shared;
the mount owner must call `ShellContribution.dispose()` when releasing them. `create` returns a fresh,
unparented Node on the JavaFX Application Thread. `DOCK` is runtime-owned and rejects ordinary
contributions. Node mounting, detachment and idempotent host disposal belong to the shell-frame slice.

### 9.7 Region renderers

```java
public interface ShellRegionRenderer {
    ShellRegionKind kind();
    Node render(ShellRegionSpec region, ShellRegionContext context);
}
```

Built-ins:

- menu bar renderer;
- toolbar renderer;
- ribbon renderer;
- status renderer;
- brand/contribution renderer;
- dock renderer, which mounts `DockRuntime.root()`.

The default shell requires exactly one `DOCK` region. A custom shell presenting dockable content must
mount `DOCK` or provide an equivalent public `DockHost` adapter; a custom window with arbitrary
`WindowContent` may omit it.

## 10. Actions and reusable registration

### 10.1 Registration vs placement

`@ActionRegistration` defines behaviour once:

```java
@ActionRegistration(id = "save")
public final class SaveCommand implements Command {
    ...
}
```

The processor writes:

```text
NbFx/Actions/save.instance
```

`@ActionReference` defines placement:

```java
@ActionReferences({
    @ActionReference(id = "save", path = "ShellRegions/nbfx:menubar/File"),
    @ActionReference(id = "save", path = "ShellRegions/nbfx:toolbar/main"),
    @ActionReference(id = "save", path = "ShellRegions/nbfx:ribbon/Home/Clipboard"),
    @ActionReference(id = "save", path = "ContextMenus/Editor")
})
```

The same `Command` is reused. No duplicate action registration exists per surface.

### 10.2 Reference metadata

`ActionReference` supports:

- `id`;
- `path`;
- `position`;
- `separatorBefore`;
- `displayName`/bundle key;
- `iconName`;
- `contextType`;
- `languageId`;
- `mimeType`;
- `profiles`;
- presentation hints.

Context queries carry neutral metadata alongside selected objects:

```java
Lookup context = Lookups.fixed(
    selectedNode,
    new ActionContextMetadata("editor", "text/x-java", "java", "main"));
List<ActionRef> refs = ActionLayerReader.readForContext("ShellRegions/nbfx:menubar/File", context);
```

Different filter dimensions are ANDed; profile ids are ORed. A missing context/MIME/language value
excludes restricted references, while a null/blank profile means all profiles. `readForProfile`
filters only profile applicability for menu/toolbar consumers and rejects malformed nonblank query
tokens. Wrong-typed layer attributes or invalid filter tokens make a reference malformed and it is
logged/skipped rather than broadened. Presentation hints use atomic `key=value` entries.
`ContextSensitiveCommand.forContext`
provides a scoped view that follows the supplied lookup. Context-aware menu construction uses
`LayerMenuBuilder.buildOwned(...)`; closing that result disposes scoped command views, while legacy
`build(...)` keeps canonical/global-context behavior.

### 10.3 Resolution

```java
Optional<Command> command = actionRegistry.resolve("save", contentLookup);
```

`ActionLayerReader.readForContext(path, Lookup)` reads and filters references for a surface.
`ActionRegistry` loads registered commands through `api.layer`; provider commands remain a
transitional fallback and valid layer registrations override the same provider id. `resolve` binds
through `Command.forContext(Lookup)`: context-free commands keep their canonical instance, while
context-aware commands may return a per-surface view. The legacy window-scoped path also honors
layer ownership before consulting provider scoped variants and follows the observable active
document, remaining disabled while no document is available.

### 10.4 Surfaces

The same placement model serves:

- menu bars;
- toolbars;
- ribbons;
- branding bars;
- context menus;
- status actions;
- command palette;
- keyboard map.

## 11. Content and documents

### 11.1 `Content`

```java
public interface Content extends Lookup.Provider, AutoCloseable {
    ContentId id();
    String instanceKey();
    ContentKind kind();
    String title();
    Node node();
    Lookup lookup();
    ContentRequirements requirements();
    boolean canClose();
    void close();
    ContentState saveState();
    void dispose();
}
```

`Content.node()` is stable across moves, theme changes, float/redock and presentation changes.

### 11.2 `ContentFactory`

```java
public interface ContentFactory {
    Content create(ContentState state) throws ContentCreationException;
    ContentState capture(Content content);
}
```

### 11.3 `ContentKind`

Extensible registered id:

```text
editor, view, output, terminal, canvas, product-defined
```

Files and URIs are sources, not content kinds.

### 11.4 `ContentManager`

`ContentManager` remains the public lifecycle/router:

```java
public interface ContentManager {
    void open(ContentSource source);
    void open(Content content);
    void close(Content content);
    Optional<Content> find(ContentId id);
    List<Content> openContent();
    ObservableValue<Content> activeContent();
}
```

### 11.5 `EditorDocument` and `EditorService`

`EditorDocument` remains the document/editor contract and extends the content model. File is
optional; text/navigation/preview/state are capabilities.

`EditorService` remains the editor factory/registry. It creates `EditorDocument`s from
`ContentSource` or state.

`EditorPreview`, `OpenSources`, `FileSelectionContext` and `FileTypes` remain useful facades over
the generic model.

## 12. Views

### 12.1 `ViewProvider`

`ViewProvider` remains the view content factory. It is a specialized content provider.

```java
public interface ViewProvider extends ContentFactory {
    String getId();
    String getTitle();
    default DockLocation getDefaultLocation() { ... }
}
```

### 12.2 `ViewManager`

```java
public interface ViewManager {
    void show(ViewProvider view);
    void show(ViewProvider view, ViewArea area, int position);
    void hide(ViewProvider view);
    boolean isShowing(ViewProvider view);
    List<ViewInfo> openViews();
    Optional<ViewInfo> activeView();
    void activate(String viewId);
    void addOpenViewsListener(Runnable listener);
    void removeOpenViewsListener(Runnable listener);
}
```

### 12.3 `ViewKind`

`ViewKind` remains an open typed id:

```java
ViewKind.NAVIGATOR
ViewKind.PROPERTIES
ViewKind.OUTPUT
ViewKind.TOOL
ViewKind.CUSTOM
ViewKind.of("acme.metrics")
```

It classifies registered views for menus/filters. It does not determine window, pane or
presentation.

Optional metadata registry:

```java
@ViewKindRegistration(id = "acme.metrics", displayName = "#Metrics", position = 500)
```

```java
public record ViewKindInfo(ViewKind kind, String displayName, int position) {}
```

## 13. Nodes and explorer

### 13.1 Model

```java
public interface NodeModel extends Lookup.Provider {
    String id();
    String displayName();
    ResourceRef icon();
    ChildrenModel children();
}

public interface ChildrenModel {
    List<NodeModel> children();
    void addChangeListener(Runnable listener);
    void removeChangeListener(Runnable listener);
}

public interface ChildFactory {
    ChildrenModel create(NodeModel parent);
}
```

`ExplorerContext`:

```java
public record ExplorerContext(
        List<NodeModel> selected,
        Lookup mergedLookup,
        NodeModel focusOwner) {}
```

No `Action[]` and no `DataObject`/`DataNode`.

### 13.2 `NodeView`

`NodeView` is a T2-FX contract in `api.views`:

```java
public interface NodeView {
    Node node();
    void setRoot(NodeModel root);
    ExplorerContext selection();
    void setContextMenuBuilder(NodeContextMenuBuilder builder);
}
```

Implementations support tree/list/tree-table/table, custom cells, badges, icons, DnD, inline
rename, multi-select, grouping/filter/sort and accessibility.

## 14. Styling and theming

### 14.1 Theme

```java
public interface Theme {
    String id();
    String displayName();
    ThemeMode mode();
    String accentScheme();
    List<ResourceRef> stylesheets();
    ThemeTokens tokens();
}
```

### 14.2 ThemeManager

```java
public interface ThemeManager {
    ObservableValue<Theme> theme();
    List<Theme> themes();
    void setTheme(String id);
    void setMode(ThemeMode mode);
    void setAccentScheme(String id);
    void attach(Window window);
    void attach(Scene scene);
    ThemeScope createScope(Parent root);
}
```

### 14.3 ThemeScope and HostTheme

```java
public interface ThemeScope extends AutoCloseable {
    ThemeTokens tokens();
    void setTheme(Theme theme);
}

public interface HostTheme {
    Theme theme();
}
```

`ThemeScope` applies/removes only its own stylesheets/classes on a `Parent`. It never mutates the
host scene.

### 14.4 Layers

1. component token defaults;
2. active theme;
3. product brand/style;
4. area/group style;
5. content style;
6. plugin scoped style.

### 14.5 AreaStyle and ContentStyleContext

`AreaStyle` is an M-tier `api.layout` value. `ContentStyleContext` is a T2-FX `api.content` value;
`api.theme` does not depend on layout/content.

```java
public record AreaStyle(
        Set<String> styleClasses,
        List<ResourceRef> stylesheets,
        List<ResourceRef> themeOverlays) {}

public record ContentStyleContext(
        ViewArea area,
        DockGroupSpec group,
        ThemeTokens tokens,
        Set<String> styleClasses) {}

public interface StyledContent extends Content {
    void applyStyle(ContentStyleContext context);
}
```

The host calls `applyStyle` on attach, move and theme change. Ordinary JavaFX content uses CSS;
Canvas/3D content reads typed tokens.

## 15. Design system

`platform.design` is an internal neutral component kit:

- buttons, toggles, fields, combo boxes, spinners, check/radio, progress, sliders;
- toolbars, status bars, cards, drawers, collapsible sections, empty states;
- tables, trees, tree-tables, lists;
- dialogs, notifications, About, file chooser;
- tabs, menus, context menus, breadcrumbs;
- density, typography, spacing, radius, focus ring, icon sizing, motion;
- accessibility roles, names, keyboard traversal and contrast.

Products may use it, restyle it or replace it. The public contract is tokens/style classes/a11y,
not concrete platform control classes.

## 16. Status bars

```java
public enum StatusScope {
    WINDOW,
    AREA,
    GROUP,
    CONTENT
}
```

`@StatusRegistration` gains scope/target metadata. `StatusElement` remains the contribution
contract. `StatusBar` remains the renderer.

Multiple hosts are allowed:

- shell window status bar;
- area-local status;
- group-local status;
- content-local status;
- custom-window status.

## 17. Overlays and HUD

```java
public enum OverlayAnchor {
    CONTENT,
    GROUP,
    AREA,
    WINDOW
}

public enum OverlayInputMode {
    PASS_THROUGH,
    HUD,
    INTERACTIVE
}

public record OverlaySpec(
        OverlayId id,
        OverlayAnchor anchor,
        int layer,
        boolean focusable,
        OverlayInputMode inputMode,
        boolean visibleByDefault) {}

public interface OverlayHost {
    void add(OverlaySpec spec, Node node, Lookup lookup);
    void remove(OverlayId id);
    void setVisible(OverlayId id, boolean visible);
}
```

`OverlayContent` is an optional content capability exposing an `OverlayHost`.

Normative mode table:

| Mode | Pointer input | Keyboard focus | Action context | Accessibility |
|---|---|---|---|---|
| `PASS_THROUGH` | ignored by overlay; delivered to content below | none | inherits owner | exposed as informational content when useful |
| `HUD` | overlay may display hover/status but is not interactive | none | inherits owner | informational role/name |
| `INTERACTIVE` | handled by overlay | participates in traversal | overlay Lookup while active | standard interactive roles |

Validation rejects `focusable=true` unless `inputMode=INTERACTIVE`. Descendants of `PASS_THROUGH`
and `HUD` cannot be interactive.

Rules:

- overlays are not split leaves;
- `ShellRegionKind.OVERLAY` is a window-level overlay slot;
- `PresentationId.OVERLAY` is a dockable overlay-group presentation;
- `OverlayHost` is the anchored HUD layer model;
- overlays follow their anchor across move/float/redock;
- closing/moving the owner disposes or reattaches the overlay;
- overlays use `ThemeScope`/typed tokens;
- overlay state persists with its owning content/area/window.

## 18. Drag and drop

### 18.1 Transfer source

```java
public interface TransferSource {
    TransferKind kind();
    Lookup data();
}
```

Kinds: `CONTENT`, `FILES`, `URIS`, `TEXT`, `NODES`, product-defined.

### 18.2 Targets, request and decision

```java
public enum TransferMode {
    MOVE,
    COPY,
    LINK
}

public record DropTarget(DropTargetKind kind, String id) {}

public record DropRequest(
        TransferSource source,
        DropTarget target,
        Point2D location,             // local to target root
        TransferMode requestedMode,
        Lookup context) {}

public record DropDecision(
        boolean allowed,
        TransferMode effect,
        String reasonKey,
        DockPlacement placement) {}
```

### 18.3 Handler

```java
public interface DropHandler {
    DropTarget target();
    Set<TransferKind> sourceKinds();
    DropDecision evaluate(DropRequest request);
    void perform(DropRequest request, DropDecision accepted);
}
```

Registration:

```java
@DropRegistration(target = "map", sources = {"files", "uris", "nodes"})
```

### 18.4 Routing

- Internal content/group drag over tab strips, headers, split handles or window edges uses
  `DockPolicy`/`DockController` before content drop handlers.
- External/domain drags use the topmost target handler in this order: content, overlay, group, area,
  global fallback.
- The accepted `DropDecision` is authoritative; `perform` cannot recompute a different effect.
- Escape cancels without mutation and restores source/focus.
- Multi-select preserves source order.
- Rejection exposes a non-color reason and an accessible announcement.
- Move/split/float/redock have keyboard/command equivalents with explicit target selection.

### 18.5 Normative interaction semantics

#### Multiwindow lifecycle

- Floating detaches a group/content without changing content ownership.
- Empty source groups are removed or retained by explicit area/product policy.
- `WindowClosePolicy.REDOCK` returns content to its default main area.
- `WindowClosePolicy.CLOSE_CONTENT` requests normal content close and honors dirty vetoes.
- `WindowClosePolicy.HIDE` hides the window without closing/redocking content.
- Main-window close applies the chosen policy to owned floating/custom windows and may be vetoed.
- A `ContentId`/instance key is unique across all windows at runtime.

#### Focus and action context

`FocusController` tracks three independent facts:

1. OS-window activation;
2. logical selection (active group/content);
3. keyboard focus.

Context precedence is:

1. interactive transient UI (palette, menu, overlay);
2. keyboard-focused content/overlay;
3. selected content in the active group;
4. active group/window fallback;
5. application-global context.

Menus use a context snapshot. Float/redock/close/overlay dismissal restores focus deterministically.

#### Empty and recovery states

`HostState` distinguishes `EMPTY`, `LOADING`, `MISSING` and `ERROR`. Empty groups/areas/windows expose
product-owned `EmptyState` contributions with accessible recovery actions. Last-content-close,
minimized-area restore and missing-content recovery return focus to a valid owner.

#### Accessibility, motion, RTL and localization

- Tabs/groups/splitters/float controls/chrome/drop feedback expose role/name/state.
- Selected state and keyboard focus are distinct.
- Menu/ribbon overflow, mnemonics and accelerator conflicts are keyboard accessible.
- Canvas/3D content provides an accessible alternative or semantic overlay.
- Reduced motion disables docking/floating/overlay/drawer animation.
- High contrast uses non-color state cues and validated product/CSS fallbacks.
- `DockSide`/placement values are logical start/end/top/bottom/center; the default shell mirrors
  start/end for RTL.
- Proper names may be raw strings; user-visible copy uses bundle keys or `#key` references.

## 19. Persistence

### 19.1 Stores

- `WindowStateStore`: owns `SessionState` (all windows plus active window);
- `ContentStateStore`: owns only per-content payloads keyed by `ContentId`/instance key;
- `WorkspaceStateStore`: workspace-scoped preferences/layout;
- `Settings`: typed settings.

`ContentStateStore` does not own content order or window placement.

### 19.2 WindowState and SessionState

`WindowState` is one window and contains:

- areas/groups/tree/dividers;
- content ids/order/active content;
- geometry/profile/decoration;
- references to content-state payloads.

`SessionState` contains all `WindowState`s plus the active window id.

`DockWindow.state()` returns `WindowState`; `WindowSystem.snapshot()` returns `SessionState`.

No JavaFX types are persisted.

### 19.3 Migration

- old sessions read once;
- backup retained;
- new format written immediately;
- missing content/window/monitor/corruption produces diagnostics and deterministic fallback to the
  normalized product default layout;
- public compatibility APIs are not retained.

## 20. Extensions and ownership

`api.plugins` provides:

```java
public interface ContributionOwner extends AutoCloseable {}

public interface Contribution {
    String id();
    ContributionOwner owner();
}

public interface ContributionRegistry {
    void register(Contribution contribution);
    void unregister(ContributionOwner owner);
    void addChangeListener(Runnable listener);
    void removeChangeListener(Runnable listener);
}
```

All chrome/content/action/theme/status/node contributions participate in ownership/invalidation.

Portable libraries may expose ServiceLoader SPIs; adapters bridge them into CJAP contributions.
NBM modules remain the installation/lifecycle mechanism. No feature-pack API exists.

## 21. Build and composition

- NBM modules are the build/install unit.
- Clusters group modules for distribution.
- Kit modules group modules as one plugin-dialog feature.
- Product variants select clusters/kits at build time.
- `product.yaml` drives identity/packaging, not a second module graph.
- `nbfx.extra.clusters` and fragments support downstream builds.
- Gradle-built libraries are wrapped as NBM library modules when used by an NBM product.

## 22. Threading and lifecycle

- Model/policy/persistence APIs are thread-safe and JavaFX-free where declared.
- UI creation/mutation runs on the JavaFX Application Thread.
- `ShellApplication` owns `ShellRuntime`, `DockRuntime`, `ThemeManager` and registries.
- Each window has its own `ShellContext`, focus scope and theme attachment.
- Contributions/content/hosts/windows are disposed on close/unload.
- Layer changes invalidate affected registries and rebuild only affected regions.
- Background source/task work never blocks the FX thread.

## 23. UX and accessibility

- active window → group → content focus is visible;
- every drag/drop/split/float operation has a keyboard/command alternative;
- tabs/groups/content expose accessible roles/names/state;
- all user-visible strings use bundle keys;
- RTL mirrors drop/split affordances;
- high contrast and reduced motion are respected;
- empty/first-run layouts are useful;
- missing content/window/monitor is reported once;
- drop rejection has a reason;
- custom shells retain the same behavior contract.

## 24. Declarative example

```text
NbFx/
  Actions/
    save.instance
    build.instance
  ShellProfiles/
    main/
    floating-editor/
  ShellRegions/
    nbfx:brand/
      product.instance
      open.ref
    nbfx:menubar/
      File/
        save.ref
    nbfx:toolbar/
      main/
        save.ref
        build.ref
    nbfx:ribbon/
      Home/
        Clipboard/
          paste.ref
    nbfx:dock/
      kind=nbfx:dock
    nbfx:status/
      progress.instance
      source-health.instance
    nbfx:overlay/
      palette.dynamic
  LayoutAreas/
    product:side/
  DockGroups/
    product:side.main/
  Layouts/
    product.default.xml
  Views/
    metrics.instance
```

The same `save` action is referenced in menu, toolbar and ribbon. The shell frame arranges the
regions; the dock runtime owns content layout; overlays sit above the appropriate scope.

## 25. Usage scenarios

### 25.1 Live-data cockpit

- dynamic per-target content tabs;
- compact toolbars/status;
- floating instrument windows;
- charts/canvas content;
- source-health diagnostics;
- context-sensitive attach/detach/profile actions.

### 25.2 Architecture/knowledge workbench

- workspace open/recent/reload;
- model tree/checks/inspector;
- document/preview content;
- perspective layouts;
- comparison custom window;
- scoped theme and product chrome.

### 25.3 Document editor/vault

- pinned tabs and recursive split groups;
- per-document state;
- palette/quick open overlays;
- external-change handling;
- plugin contributions/unload;
- product-native shell/theme.

### 25.4 Geospatial/EO workbench

- globe/map canvas content;
- HUD/overlays/drawers/timeline;
- file/URI/node drops;
- typed theme tokens;
- custom presentation windows;
- content-owned render loop and gesture routing.

## 26. Migration map

| Existing API | Target |
|---|---|
| `ShellBranding` | `Product`/`ProductIdentity`/`ProductStyle` |
| `ShellChromeProvider` | `ShellProvider` |
| `ShellChrome` | `ShellFrame` + `ShellContribution`/renderers |
| `WindowPane*` | `ViewArea` + `LayoutSpec` + `DockGroupSpec` |
| `PaneRole` fixed semantics | stable area id, product-defined roles |
| `ViewArea` lightweight record | logical region spec with capabilities/policy/style |
| `WindowManager` | retained and extended |
| `WindowSystem` | retained as layout lifecycle |
| `WindowState`/`AreaState` | retained and evolved |
| `ViewLocation` | deleted; use `DockLocation` |
| `EditorDocument` | retained as content/document contract |
| `EditorService` | retained as editor factory/registry |
| `ContentManager` | retained as generic content manager |
| `ViewProvider`/`ViewManager` | retained |
| `FileContextMenuContributor` | layer refs + `NodeContextMenuBuilder` |
| `ElementContextMenuContributor` | layer refs + `NodeContextMenuBuilder` |
| public `NbfxTheme` | `ThemeManager` |
| public `Docking`/`AppState`/`SessionRestorer` | internal `DockRuntime`/stores |
| duplicate `view` package | unified `views` package |

## 27. API governance

- New CJAP APIs are devel until the reference product and scenarios pass.
- Signature baselines are created early to detect drift.
- Stability is decided by product proof, not by compilation alone.
- Old nbfx APIs are deleted, not deprecated indefinitely.
- `arch.xml`, `apichanges.xml`, sigtests and `check-api` enforce the final surface.
- No T0 implementation type appears in public signatures.

## 28. Open decisions

1. Exact `ViewArea` type/template model: single spec vs separate type registry.
2. Whether `PaneKind` remains closed `EDITOR`/`VIEW` or becomes a typed id.
3. Whether `WindowStateStore` keeps its current name or becomes `LayoutStore`.
5. Whether custom `ShellRegionRenderer`s are layer-registered or service-registered.
6. Whether `NodeView` lives in `api.views` or a new `api.nodes.fx`.
7. Whether `ContentManager` or `WindowManager` owns cross-window content routing.
8. Final migration utility scope for old persisted sessions.

## 29. Definition of the target

The target is complete when:

- a product can define identity/layout/shell/theme/content without T0 imports;
- shell chrome is declarative and actions are reusable;
- MAIN/FLOATING/CUSTOM windows and HUD overlays work;
- content/views/editor/nodes share one lifecycle and action context;
- themes/brand/design system are layered and scoped;
- NBM modules/clusters/kits are the only composition;
- old APIs are gone;
- the reference product and four scenarios pass on the platform-only distribution;
- all guards, a11y and performance gates are green.
