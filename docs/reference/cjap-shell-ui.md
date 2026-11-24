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
| static `Docking` | per-shell `DockRuntime` |

Additional CJAP concepts:

- `ShellRegion` — window chrome slot, separate from content `ViewArea`;
- `ShellProfile` — per-window chrome configuration;
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
| `api.product` | M | product metadata |
| `api.shell` | T2-FX | shell/frame/context/window/chrome/overlay |
| `api.layout` | M | layout/area/group/policy/builder/XML |
| `api.docking` | T2-FX | hosts/windows/transfer |
| `api.content` | T2-FX | content/factory/state/sources/drop |
| `api.views` | T2-FX | views/registry/manager/NodeView |
| `api.actions` | T2-FX | commands/registry/references |
| `api.nodes` | M | explorer model |
| `api.editor` | T2-FX | editor document/service |
| `api.theme` | T2-FX | theme manager/tokens/scope |
| `api.workspace` | M | product workspace |
| `api.settings` | M | settings |
| `api.dialogs` | T2-FX | dialogs/notifications/About |
| `api.diagnostics` | M | diagnostics/event model |
| `api.plugins` | T1/M | contribution ownership/lifecycle |
| `api.layer` | T1 | typed layer reader/registry |

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

## 5. Product metadata

### 5.1 Contract

```java
public interface ProductProvider {
    Product product();
}
```

Exactly one `ProductProvider` is allowed per distribution. Duplicate providers are a startup
error. There is no `priority()`.

```java
public record Product(
        ProductIdentity identity,
        ProductIcons icons,
        ProductStyle style,
        ThemePolicy theme,
        String layoutId,
        String shellId,
        String chromeProfileId,
        List<DesktopAssociation> associations,
        AboutInfo about) {}
```

`Product` is immutable metadata. It does not contain live `ShellProvider` or `LayoutProvider`
objects.

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

```java
public record ThemePolicy(
        String defaultTheme,
        ThemeMode mode,
        boolean userSelectable,
        List<String> shippedAccents) {}
```

### 5.5 About and associations

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

### 5.6 Build metadata

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
    public static final PaneRole NAVIGATOR = ...;
    public static final PaneRole DOCKED = ...;
    public static final PaneRole DETACHED = ...;
    public static final PaneRole SLIDE = ...;

    public static PaneRole of(String name);
    public String name();
}
```

Product roles use namespaced stable ids. Persisted layouts refer to roles, not class names.

### 6.2 `ViewArea`

`ViewArea` is the #104 logical region. It contains zero or more `DockGroup`s.

```java
public record ViewArea(
        PaneRole id,
        String displayName,
        DockLocation defaultLocation,
        double weight,
        Set<ViewAreaCapability> capabilities,
        DockPolicy policy,
        AreaStyle style,
        ViewAreaVisibility visibility,
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
STARTUP, ON_DEMAND, HIDE_WHEN_EMPTY, ALWAYS
```

The framework registers neutral defaults. Products register their own areas.

### 6.3 `DockGroupSpec`

```java
public record DockGroupSpec(
        DockGroupId id,
        PaneRole area,
        PaneKind kind,
        PresentationId presentation,
        ContentCapacity capacity,
        DockPolicy policy,
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

### 6.4 `LayoutSpec` and `LayoutNode`

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

### 6.5 Authoring

Canonical builder:

```java
LayoutSpec layout = LayoutSpec.builder("product.default", 1)
    .area("explorer", area -> area
        .kind(ViewAreaKinds.NAVIGATOR)
        .presentation(PresentationId.TABBED)
        .visibility(ViewAreaVisibility.STARTUP))
    .area("editor", area -> area
        .kind(ViewAreaKinds.EDITOR)
        .presentation(PresentationId.TABBED)
        .visibility(ViewAreaVisibility.STARTUP))
    .group("editor.main", "editor", ContentCapacity.MANY, PresentationId.TABBED)
    .group("explorer.main", "explorer", ContentCapacity.MANY, PresentationId.TABBED)
    .split(SplitOrientation.HORIZONTAL, 0.25,
        LayoutNode.group("explorer.main"),
        LayoutNode.group("editor.main"))
    .build();
```

Layer XML:

```xml
<layout id="product.default" version="1">
  <areas>
    <area id="explorer" kind="NAVIGATOR" presentation="tabbed"
          visibility="startup" capabilities="minimizable closable movable detachable"/>
    <area id="editor" kind="EDITOR" presentation="tabbed"
          visibility="startup" capabilities="maximizable splittable detachable reorderable"/>
  </areas>
  <groups>
    <group id="editor.main" area="editor" capacity="many" presentation="tabbed"/>
    <group id="explorer.main" area="explorer" capacity="many" presentation="tabbed"/>
  </groups>
  <tree>
    <split orientation="horizontal" dividers="0.25">
      <group ref="explorer.main"/>
      <group ref="editor.main"/>
    </split>
  </tree>
</layout>
```

No custom layout DSL is introduced.

## 7. Docking policy and transfer

### 7.1 Policy

```java
public record DockRequest(
        ContentId content,
        DockGroupId sourceGroup,
        PaneRole targetArea,
        DockGroupId targetGroup,
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

### 7.3 Hosts

```java
public interface DockHost {
    Node node();
    ContentCapacity capacity();
    ObservableList<Content> content();
    void add(Content content, int index);
    Content remove(Content content);
    ObservableValue<Boolean> empty();
    void dispose();
}
```

Hosts do not know other hosts. Transfer is performed by the dock controller behind
`WindowManager`.

### 7.4 `WindowManager`

`WindowManager` remains the public operation surface:

```java
public interface WindowManager {
    List<WindowPaneHost> panes();
    Optional<WindowPaneHost> primary(PaneRole role);
    Optional<WindowPaneHost> paneOf(Tab tab);

    void attach(WindowPaneHost pane, Tab tab);
    void move(Tab tab, WindowPaneHost target, int index);
    void select(Tab tab);

    DockWindow floatContent(ContentId content, WindowOptions options);
    DockWindow floatGroup(DockGroupId group, WindowOptions options);
    void redock(DockWindow window, DockRequest request);

    List<DockWindow> windows();
}
```

The exact tab compatibility surface may be replaced by content/group handles during the API
reset; the name `WindowManager` remains.

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

`ShellChromeProfile` selects shell regions and entries per window.

Declarative profile:

```xml
<folder name="floating-editor">
    <attr name="windowKind" stringvalue="FLOATING"/>
    <attr name="regions" stringvalue="dock,toolbar,status,overlay"/>
    <attr name="decoration" stringvalue="NATIVE"/>
    <attr name="systemMenuBar" boolvalue="false"/>
</folder>
```

Default profiles:

- `main`: brand/menu/ribbon-or-toolbar/dock/status/overlay;
- `floating-editor`: compact toolbar/dock/status/overlay;
- `floating-tool`: optional toolbar/dock-or-single/status/overlay;
- `presentation`: dock/overlay only;
- product-defined profiles.

Profile selection precedence:

1. explicit `WindowOptions.profile`;
2. content/group profile hint;
3. product default for `WindowKind`;
4. platform neutral fallback.

`WindowState` persists the profile id.

## 9. Shell frame and declarative chrome

### 9.1 `ShellProvider` and `ShellFrame`

```java
public interface ShellProvider {
    ShellFrame create(ShellContext context, ShellChromeProfile profile);
}

public interface ShellFrame extends AutoCloseable {
    Node root();
    void setDockRoot(Node dockRoot);
}
```

`ShellApplication` is the default composition root. Products do not subclass it; they provide a
`ShellProvider` or use the default.

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
    WindowPaneHost mainPane();
    Node slidingArea();
    WindowManager windowManager();
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
        StyleRef style,
        boolean visibleByDefault) {}
```

Built-in ids:

```text
BRAND, MENUBAR, TOOLBAR, RIBBON, DOCK, STATUS, OVERLAY
```

Built-in kinds use the same names. Product-defined ids/kinds are allowed.

### 9.5 Declarative layer

```text
NbFx/ShellRegions/
  brand/
  menubar/
    File/
  toolbar/
    main/
  ribbon/
    Home/
      Clipboard/
  dock/
  status/
  overlay/
```

Region metadata:

```xml
<folder name="brand">
    <attr name="kind" stringvalue="BRAND"/>
    <attr name="position" intvalue="10"/>
    <attr name="profiles" stringvalue="main"/>
    <attr name="styleClass" stringvalue="acme-brand"/>
</folder>
```

Node contribution:

```xml
<file name="product.instance"
      instanceClass="org.example.ProductBrandBar"
      position="10"/>
```

Action reference:

```xml
<file name="save.ref"
      actionId="save"
      position="20"/>
```

Nested ribbon/menu folders:

```xml
<folder name="ribbon">
    <attr name="kind" stringvalue="RIBBON"/>
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
    id = "brand",
    kind = ShellRegionKinds.BRAND,
    position = 10,
    profiles = {"main"})
public final class BrandRegionDeclaration {}
```

```java
@ShellContributionRegistration(region = "brand", position = 10)
public final class ProductBrandBar implements ShellContribution {
    @Override public Node create(ShellContext context) { ... }
}
```

The layer owns region/position/profile metadata. The contribution does not hardcode placement.

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

The default shell requires exactly one `DOCK` region. A custom shell may ignore or reinterpret it.

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
    @ActionReference(id = "save", path = "ShellRegions/menubar/File"),
    @ActionReference(id = "save", path = "ShellRegions/toolbar/main"),
    @ActionReference(id = "save", path = "ShellRegions/ribbon/Home/Clipboard"),
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

### 10.3 Resolution

```java
Optional<Command> command = actionRegistry.resolve("save", contentLookup);
```

`ActionLayerReader.readForContext(path, Lookup)` reads and filters references for a surface.
`ActionRegistry` loads registered commands through `api.layer`.

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

```java
public record AreaStyle(
        Set<String> styleClasses,
        List<ResourceRef> stylesheets,
        ResourceRef themeOverlay) {}

public record ContentStyleContext(
        ViewArea area,
        DockGroupSpec group,
        WindowKind windowKind,
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

Rules:

- overlays are not split leaves;
- pass-through overlays do not steal input;
- interactive overlays participate in focus/action context;
- overlays use `ThemeScope`/typed tokens;
- overlay state persists with content/workspace/window.

## 18. Drag and drop

### 18.1 Transfer source

```java
public interface TransferSource {
    TransferKind kind();
    Lookup data();
}
```

Kinds: `CONTENT`, `FILES`, `URIS`, `TEXT`, `NODES`, product-defined.

### 18.2 Request and decision

```java
public record DropRequest(
        TransferSource source,
        DropTarget target,
        Point2D location,
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
    void perform(DropRequest request);
}
```

Registration:

```java
@DropRegistration(target = "map", sources = {"files", "uris", "nodes"})
```

### 18.4 Routing

1. content handler;
2. overlay handler;
3. group handler;
4. area handler;
5. shell docking handler for internal content drags;
6. global fallback.

Internal tab dragging uses `DockPolicy`. Domain/external dropping uses `DropHandler`.

## 19. Persistence

### 19.1 Stores

- `WindowStateStore`: layout, groups, windows, geometry, active state;
- `ContentStateStore`: content ids/order/state payloads;
- `WorkspaceStateStore`: workspace-scoped preferences/layout;
- `Settings`: typed settings.

One writer per concern.

### 19.2 WindowState

Contains:

- areas/groups/tree/dividers;
- content ids/order/active;
- main/floating/custom windows;
- geometry/profile/decoration;
- opaque per-content payload references.

No JavaFX types.

### 19.3 Migration

- old sessions read once;
- backup retained;
- new format written immediately;
- missing content/window/monitor/corruption produces diagnostics and deterministic fallback;
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
    brand/
      product.instance
      open.ref
    menubar/
      File/
        save.ref
    toolbar/
      main/
        save.ref
        build.ref
    ribbon/
      Home/
        Clipboard/
          paste.ref
    dock/
      kind=DOCK
    status/
      progress.instance
      source-health.instance
    overlay/
      palette.dynamic
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
4. Whether shell profiles are layer-only, product-metadata-only, or both with layer override.
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
