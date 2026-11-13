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
# nbfx — the plan

**One document. One plan.** Everything else under `docs/plan/archive/` is historical research kept for
evidence; it is not the plan and is not maintained.

This plan is written so that a new contributor can (1) understand the architecture, (2) know the naming and
API rules, (3) find the module they need to touch, and (4) pick the next step. When this document and a
module disagree, the document wins until it is changed here.

---

## 1. What nbfx is

nbfx has **two deliverables**.

**The IDE is the primary deliverable.** It is a pure-JavaFX IDE built on the NetBeans runtime:

- a JavaFX **application shell** owns the window, docking, menus, views and dialogs;
- nbfx provides JavaFX replacements for every **user-facing** service of the NetBeans platform, IDE and
  Java clusters;
- the NetBeans **headless engines** are reused as-is (project model, parsing/indexing, `java.source`,
  build/execution primitives, debugger, profiler, VCS, templates, autoupdate) — "as-is" means **per the
  matrix in [Appendix D](appendix/D-bridge-and-retirement-matrix.md)**: reuse / bridge / replace / drop, one
  row per upstream module, with owners and milestones;
- **the FX shell renders no Swing**; upstream Swing modules still load until the retirement-matrix rows land
  ([Appendix D.4](appendix/D-bridge-and-retirement-matrix.md)) — `openide.nodes` is enabled and initialized via
  `core.ui` and `DataObject` today ([D.2.1](appendix/D-bridge-and-retirement-matrix.md));
- a **published plugin API** lets third parties build NBMs on nbfx.

**The platform is the secondary deliverable.** Its application framework is the **AppShell**: the JavaFX
application platform on which the IDE is built. The AppShell is not just `platform.shell` (the composition
root); it is the
platform's whole application framework — window system and docking, actions and action context, keymap,
menus/toolbars, status, options/settings, wizards, nodes/explorer and property sheet, dialogs/notifications,
progress, output, execution, terminal, quick search, theme, credentials, favorites, templates and the
launcher. Its service inventory is §6.6.

Downstream projects can use the AppShell, individual platform services, or both, and may assemble an
application however they choose — from source clusters or prebuilt binary modules. nbfx does not define a
product, product tier or application-assembly model; it guarantees the platform's module boundaries,
published APIs and artifacts.

The IDE is the AppShell's **primary consumer and completeness proof**. A platform feature the IDE does not
need is not thereby forbidden, but the IDE's needs decide priority.

**Non-goals.** Web/JS/JavaEE/PHP/C++ languages, the Swing form editor, WebStart/JNLP execution, Java ME,
`native-image`, JavaHelp topics, database/server tooling. These are **product** non-goals: the architecture is
validated against a second language by the soundness spikes of [Appendix I](appendix/I-language-soundness.md)
— a non-goal is not a licence for a hole.

## 2. Deliverables, clusters and dependency rules

**Deliverables are cluster combinations, not modules or assembly tiers:**

```
platform deliverable   nbfx-libs + nbfx-platform
IDE deliverable        platform + nbfx-ide + nbfx-java
                       (+ nbfx-javafx when its first module lands)
```

```
clusters (build order, dependencies downward only):

  nbfx-libs      bundled external libraries            (jline, async-profiler, javafx, jgit, jacoco, jna)
      │
  nbfx-platform  AppShell + platform services + APIs    org.netbeans.modules.nbfx.platform.*
      │
  nbfx-ide       IDE building blocks + IDE APIs         org.netbeans.modules.nbfx.ide.*
      │
  nbfx-java      Java/JVM usage + Java APIs             org.netbeans.modules.nbfx.java.*
  nbfx-javafx    JavaFX usage + JavaFX APIs             org.netbeans.modules.nbfx.javafx.*
```

**There is no `nbfx-api` cluster.** An API is a module tier, not a cluster: each API module lives in the
cluster that owns its domain. `refactor/api-clusters` has dissolved the transitional `nbfx-api` cluster
(W1.7, §11); the placement is §6.6.

**Rules.**

1. **Downward only (nbfx edges).** `javafx`/`java` → `ide` → `platform` → `libs`. No upward edges, no
   cycles. Siblings are allowed only in the declared order `java` → `javafx`. **Upstream reuse edges** (an
   nbfx module depending on a NetBeans module) are not layer edges; they are allowlisted separately with a
   justification (Appendix A). Enforced by the module-graph guard.
2. **The platform must not depend on the IDE.** No `nbfx-platform` module may depend on `nbfx-ide`,
   `nbfx-java` or `nbfx-javafx`. This is the AppShell boundary; `check-appshell` enforces it.
3. **Services do not depend on the shell.** `platform.shell` composes the platform services; it does not own
   them. Downstream may use the shell, only the services, or another composition.
4. **No split packages.** A package has exactly one owning module.
5. **Models are toolkit-free.** Any module that a headless engine or a plugin backend uses contains no
   `javafx.*`, no `javax.swing`/`java.awt`, no legacy NetBeans UI.
6. **The UI toolkit lives in implementations**, never in an API type name.
7. **State the tree.** A claim in this plan refers to `main`; local branches are named explicitly.
8. **One semantic boundary per language.** The Java semantic layer (`java.*`) is the only place that may
   import `org.netbeans.api.java.source`/`org.netbeans.libs.javacapi`; every other layer consumes the
   toolkit-free seams (`api.editor.EditorDocument`, `api.editor.OpenSources`, `api.language.ElementRef`). See
   [Appendix B](appendix/B-java-source-isolation.md).
9. **The toolkit policy is Appendix A.** Allowed/forbidden types per tier, the exemption register and the
   guard mechanism live in [Appendix A](appendix/A-toolkit-policy.md); §9 states the guard, Appendix A
   states the rule.
10. **Test edges are not layer edges.** `<test-dependency>` may point upward — the W0 scenario in `nbfx-ide`
    needs the Java project type from `nbfx-java` — but it must never be a run dependency, and the test artefact
    is not shipped. The graph guard distinguishes test edges.

## 3. Naming conventions

### 3.1 Modules

```
org.netbeans.modules.nbfx.api.<capability>            published/shared API        (cluster of its domain)
org.netbeans.modules.nbfx.platform.<capability>       AppShell/platform impl      (cluster nbfx-platform)
org.netbeans.modules.nbfx.ide.<capability>            IDE implementation          (cluster nbfx-ide)
org.netbeans.modules.nbfx.java.<capability>           Java/JVM usage              (cluster nbfx-java)
org.netbeans.modules.nbfx.javafx.<capability>         JavaFX usage                (cluster nbfx-javafx)
org.netbeans.libs.nbfx.<library>                      bundled external library    (cluster nbfx-libs)
```

- An **API module** lives in the cluster of its domain (§6.6), not in a shared API cluster. The API tier is
  module metadata; the cluster is the deliverable/domain boundary.
- Module code names are `org.netbeans.modules.nbfx.<module>`; module directories are `nbfx.<module>`.
  **Package == module code-name** no longer holds: API packages are `org.netbeans.api.nbfx.*`, SPI packages
  are `org.netbeans.spi.nbfx.*`, and implementation packages are `org.netbeans.modules.nbfx.*`. A
  domain-specific API may add the domain segment, for example `org.netbeans.api.nbfx.java.elements`; it
  still follows the `org.netbeans.api.nbfx.*` root. Library directories use `libs.nbfx.<library>` because
  `nbbuild` derives the directory from the `org.netbeans.libs.*` code name.
- A module that must expose a model to headless code but a UI elsewhere is split:
  `…ide.<capability>` (UI) + `…api.<capability>` (model). A module never mixes a public model with FX UI.
- `<capability>` is a noun: `actions`, `views`, `status`, `dialogs`, `options`, `settings`, `progress`,
  `output`, `execution`, `terminal`, `language`, `nodes`, `property`, `project`, `build`, `editor`, `wizard`,
  `templates`, `search`, `debug`, `profile`, `tests`, `vcs`, `diff`, `tasklist`, `plugins`, `shell`,
  `workbench`.

### 3.2 Types

| Kind | Rule | Examples |
|---|---|---|
| SPI/API type | capability noun, no toolkit | `OptionsPanelController`, `WizardIterator`, `ViewProvider`, `PropertyEditor`, `ProjectModel`, `NodeModel`, `Settings`, `Dialogs`, `TerminalSession` |
| Registration annotation | `@<Capability>Registration` | `@ViewRegistration`, `@ActionRegistration`, `@OptionsRegistration`, `@ProjectTypeRegistration` |
| Implementation type | may carry the toolkit **and must be non-public** in API packages | `FxOptionsWindow` in `…platform.options` (private/package-private) |
| Service | registered via `@ServiceProvider(service = X.class)` for singletons; via layer annotations for contributions | `Dialogs`, `Settings`, `ActionRegistry`, `FxOptions` |
| Ids | one vocabulary per domain, `@since`-tagged | `ActionIds`, `TemplateIds`, `EditorCommands` |

**Forbidden in public API:** `Fx*`/`Sw*` names, `*Impl` names, `Object`-typed factories,
`Map`-parameters with string keys. The only permitted shared constant class is `LayerPaths` (§3.3).
The current tree has **4 public `Fx*` types**, all implementation types in non-exported packages
(`nbfx.progress`/`platform.actions`, where C.1 allows the prefix — [`figures.md`](figures.md)); the annotations,
`api.*` and implementation renames have landed, `LayerPaths` lives in `api.layer`, and `check-naming` is green.
The writer/reader contract test landed in PR #33 and was split per owning module in #63.

### 3.3 Layer paths

Layer folder names are a **contract**, not an implementation detail: the writer (annotation processor) and
the reader (the shell) must query the same folder. The module `nbfx.api.layer` (target cluster `nbfx-platform`,
tier T1) now owns `LayerPaths`; every processor writes through it and every registry reads through it. The writer/reader agreement is asserted by
**per-module processor tests**, each of which builds a layer from a sample annotated class and checks
its emitted folders against `LayerPaths`. "Private constant per reader" is rejected: no test can compare two privates, so a typo becomes a
silent runtime no-op. See [Appendix C](appendix/C-naming-and-rename-table.md) §C.3.

## 4. API tiers and governance

| Tier | Who consumes it | Toolkit | Promise |
|---|---|---|---|
| **T0** implementation | its own module, plus named friends | any | none; publishes nothing (friend packages only, named and dated) |
| **T1** shared API | other nbfx layers | documented per package | internal compatibility only |
| **T2-FX** published plugin API | third-party NBMs | **JavaFX allowed** (`Node`, `ObservableValue`, `KeyCombination`) | additive compatibility, version range, docs, signature tests, deprecation |
| **M** model/backend API | nbfx layers, plugin backends | **toolkit-free** | as T2, no toolkit types |
| **L** library | nbfx | bundled library | licence/notice; allowlisted in the guard |

**The tier is module metadata, not a cluster.** Each nbfx module records its tier in
`nbproject/nbfx-policy.properties` (`tier=T0|T1|T2-FX|M`); W2a adds the file to every module and the guard
reads it instead of the module name or the cluster. `L` is the library tier for `libs.nbfx.*` modules and is
not written in `nbfx-policy.properties`. T1/T2-FX/M modules live in the cluster of their domain
(§6.6). An API module may depend only on other API modules in the same or a lower cluster, on `nbfx-libs`, and
on allowlisted upstream APIs; it may not depend on an implementation module (`check-api-isolation`, §9).

**T0 publishes nothing.** A T0 module keeps its implementation packages unexported. When another nbfx module
needs one of them, the package is exported as a **friend package** to that named module, with an owner and a
removal milestone. Friend packages are internal compatibility only; they are not part of the plugin contract
and cannot be used by a third-party NBM. A package shared beyond a named friend is promoted to T1/T2-FX/M
deliberately. T0 modules carry no specification version, `arch.xml` or `apichanges.xml`; a module that becomes T0
drops its spec version when its last public package is removed, and its friend consumers use implementation
dependencies.

**Governance per T1/T2-FX/M module:** explicit `<public-packages>` (one package per capability, never
`<subpackages>`), `arch.xml` `<api>` entries, `apichanges.xml` on every change, `@since` on new elements,
a signature test, and a **starting specification version of 1.0** with the bump rule: additive → 1.1,
incompatible → 2.0. The guard checks that a dependant's `<specification-version>` exists. The
signature-test harness is the NetBeans `sigtest` support in `nbbuild`; name the target per module.

**Contract completeness is the gate (M0).** A module is *landed* only when the contract it promises exists in
its exported API/SPI packages, its consumers import only API/SPI, and a downstream fixture can implement the
extension point using the plugin allowlist. A directory, an annotation and a processor are not a contract:
the type a plugin implements must live in the API/SPI module, never in a T0 module. Modules whose contract is
still being completed stay `devel` and are exempt from sigtest, with a named removal milestone; they are not
listed as landed in §6.1 or §18. The API reality in §6.1.1 records the current gap.

**Why the API split existed (W1, landed).** `nbfx.api`/`nbfx.annotations` exported their whole subtree, so
every class was public API and `arch.xml` had no `<api>` entries. W1 replaced subtree exports with explicit
packages plus `arch.xml` entries; `check-api` asserts *exported set ⊆ arch set*. The split was a **module
move**, not a contract extraction: the API modules exist, but several of them still hold only annotations or
utilities while the SPI a consumer implements remains in a T0 module. W2a completes those contracts before W2b
signs them.

**Plugin contract.** A plugin may use: the registration annotations, the T2-FX/M API modules of the
extension points it implements, the platform core (`org.openide.util`, `org.openide.filesystems`,
`org.openide.util.lookup`), plus `org.openide.loaders` **API only** (`DataObject`/`DataFolder`, never
`DataNode`) and `org.netbeans.api.project` up to `Project` — the full allowlist is
[Appendix E.6](appendix/E-contracts.md); note that using `DataObject` also initializes `openide.nodes`
([D.2.1](appendix/D-bridge-and-retirement-matrix.md)). It may not use: T0 modules, layer paths (`LayerPaths`), `*Impl`
classes, view layouts, theme CSS classes. A `Node`-returning API documents ownership (new node per call; the
shell owns what a view hands over) and the shell sets an `AccessibleRole` where the plugin does not. Menu
contributions are FX `MenuItem`s.

**Trust (proposed until shipped).** NBM JAR signature verification with a system/pinned trust anchor, HTTPS
update centers, pinned SHA-256 for bundled native artifacts, install-time capability display.

## 5. Architecture of the UI layer

1. **One model, many views.** `api.nodes` (M) defines the node/children/property model and the
   explorer context; the platform provides the FX views (tree, tree-table, table, list) and the property
   sheet. The navigator, structure view, breakpoints, test results, variables and task list all render
   through it. Explorer-like panels use it; popups/overlays (completion, breadcrumbs) keep their private
   list models. The inventory and the migration order are in
   [Appendix F](appendix/F-node-and-view-migration.md) (25 view files, ≥5 item models today).
2. **Model-first panels, concrete FX factories.** Component-producing SPIs are **concrete and FX-typed**
   (`OptionsPanelFactory` returning `javafx.scene.Node`), because there is exactly one front end; the
   portability lives in the toolkit-free model (`PanelModel`, `FieldModel`, `Validator`), which a future
   front end binds. The `OptionsPanelFactory<C>`/`WizardUi<C>`/`ViewFactory<C>` generics are removed: NetBeans
   `Lookup` cannot resolve a parameterised type, so the plan forbade the only mechanism it needed. See
   [Appendix E.7](appendix/E-contracts.md).
3. **Model-first panels.** A panel that is only fields/validation is a `PanelModel` (ordered `FieldModel`s
   with a `PropertyEditorRef`, actions as command ids, a validator) rendered by the property sheet — no
   component factory at all. Use the factory only for genuinely custom visualisations.
4. **Uniform async.** Analysis, usages, hints, refactoring preview, output, execution, VCS and profiler all
   run as cancellable jobs with progress; the FX thread never blocks. One job/task model drives the status
   line and the all-tasks popup.
5. **One notification surface.** `api.dialogs`: dialogs with roles, notification line, toasts,
   "show again" keys, exception reporting.
6. **One settings layer.** `api.settings` (M) is a typed facade over `NbPreferences`; panels bind through a
   UI adapter; keys are a documented, stable convention (`nbfx.<capability>.<key>`).
7. **One search surface.** `api.search` (quick search/command palette) + providers; find-in-projects,
   find-usages and Go-to are providers over the same index.
8. **One theme.** Semantic token CSS for every FX surface, light/dark, brandable (doc: theming section in
   the archived research).
9. **One Java semantic boundary.** `java.source`/`libs.javacapi` are confined to the Java layer behind one
   bridge (`java.model`) and the toolkit-free seams; the editor core, the generic usages engine and the
   platform never import them. The editor's text model is the FX rich-text model, and the semantic layer
   deals in character offsets — never in a Swing `Document`. See
   [Appendix B](appendix/B-java-source-isolation.md).
10. **Every SPI states its thread; every subscription is owned and disposed; every published view is
    accessible.** The thread rule per SPI, the disposal/ownership rules (a `subscribe` returns a resource),
    the leak test and the a11y requirement are normative in
    [Appendix E](appendix/E-contracts.md) §§E.1–E.3.
11. **One language registry.** `api.language` (M) is the only place a language is registered (`LanguageId`,
    `LanguageRegistration`, `Languages`); every provider SPI is scoped by `LanguageId`, and syntax decoration
    is a provider, not a `switch` in the core. See [Appendix I](appendix/I-language-soundness.md).
12. **The AppShell is the platform deliverable's application framework.** `platform.shell` is its
    composition root; the AppShell also includes the platform services of §6.6 — actions and **action
    context**, options/settings, wizards,
    nodes/explorer and property sheet, dialogs, progress, output, execution, terminal, quick search, theme,
    credentials, favorites, templates and the launcher. The IDE contributes through `ide.workbench`.
    The default `ShellApplication` must itself provide the generic chrome — menu bar, tool bars, status bar,
    application menu, branding/title and window environment — from registrations; a downstream product must
    not need to subclass the shell just to get standard chrome. Downstream may use the whole AppShell, individual
    services, or another composition.
13. **Action context is a platform service.** The global context, active component, focus scope, enablement
    and keymap dispatch live in `platform.actions`/`platform.shell`. Project and editor contexts are IDE
    contributions; no platform service imports project types.

## 6. Module catalogue

### 6.1 API modules — published/shared APIs

The table is the API **catalogue**, not a cluster: each module lives in its domain cluster (§6.6). The
`nbfx-api` cluster is dissolved by `refactor/api-clusters` (W1.7, landed). The catalogue is the **target**;
§6.1.1 is the current contract baseline. A `partial` row means the module exists but the type a consumer or
plugin implements is not yet in its API/SPI packages.

| Module | Tier | State | Contents |
|---|---|---|---|
| `api.actions` | T2-FX | **landed** | `Command`, `CommandsProvider`, `ActionRegistry`, `KeyMap`, `ActionContext`, `@ActionRegistration`, `@ActionReference(s)`, generic `ActionIds` (IDE ids live in `ide.workbench.IdeActionIds`) |
| `api.views` | T2-FX | **landed; M0 adds `ViewSpec`/`ViewRegistry`** | `ViewProvider`, `ViewManager`, `ViewSpec`, `ViewRegistry`, `ViewArea`, `DockLocation`, `ViewKind`, `PaneKind`, `@ViewRegistration`, `@MenuRegistration(s)`, `@ToolbarRegistration(s)` |
| `api.status` | T2-FX | **partial — contract M0** | `StatusAlignment`, `StatusMessageService`, `@StatusRegistration`; the element SPI and `StatusElement` still move from `platform.status` to `api.status` in M0 |
| `api.dialogs` | T2-FX | **partial — M1** | only `ErrorReporter`; `Dialogs`, `DialogDescriptor`, `DialogOption`, `Notification` are the M1 contract |
| `api.options` | T2-FX | **landed (#162)** | `OptionsPanelController`, `OptionsDisplayer`, `@OptionsRegistration`/`Container`/`Sub`; the layer entry owns id/display name/position, and `platform.options` is the T0 implementation |
| `api.settings` | M | **planned — M1** | `Settings`, typed keys, change listeners |
| `api.progress` | M | **landed** | `Progress`, `Progress.Handle`, `Cancellation`, task tree, determinate progress |
| `api.output` | M | **planned — M1** | `OutputConsole`, `OutputWriter`, hyperlinks, folds, colours |
| `api.execution` | M | **planned — M1** | `ExecutionService`, `ExecutionDescriptor`, `ExecutionHandle`, `PtySupport`; process execution first |
| `api.terminal` | M | **planned — M2** | `TerminalModel`, `PtyProcess`, `TerminalSession`; M1 decides the VT parser/library and scope |
| `api.language` | M | **landed** | `LanguageId`, `LanguageRegistration`, `Languages`, neutral `ElementRef`/`SourceLocation`/`SourceElementKind`/`SourceTypeKind`/`ElementModifiers` — the one language registry and the neutral element vocabulary ([Appendix I](appendix/I-language-soundness.md)) |
| `api.elements` | dissolved | **dissolved** | neutral `SourceElementKind`/`SourceTypeKind`/`SourceLocation`/`ElementModifiers` → `api.language` (no `java.lang.reflect.Modifier` bits); Java conversion (`SourceModifiers`, `JavaElementTypes`) → `java.elements`; FX `ElementIcons` → `api.editor` |
| `api.nodes` | M | **planned — contract M0** | `NodeModel`, `ChildrenModel`, `ChildFactory`, `PropertyModel`, `PropertySetModel`, `PropertiesProvider`, `PropertyEditorRef` (opaque handle), `ExplorerContext`, property-change events; the property sheet is its first consumer |
| `api.property` | T2-FX | **partial — contract M0** | `PropertyEditor`, `PropertyEditorRegistration`, `PropertyEnv`; the editor takes a `PropertyModel` and the handle lives in `api.nodes` (M never depends on a T2-FX module) |
| `api.usages` | M | **landed** | `UsagesTarget` (opaque `ElementRef` + `LanguageId` + location), `UsagesProvider`, `UsageResult`, `UsageKind` — the generic usages SPI; the Java provider is `java.usages` |
| `api.project` | M | **partial — M1** | `ProjectRegistry`, `ProjectOpener`, `ProjectLifecycle`, `OpenProject`, `SourceRoot`, `@ProjectTypeRegistration`; the project-kind/build SPI still moves from `project.ui.api`, and `OpenProjects` remains the documented IDE-tier bridge |
| `api.build` | M | **planned — M1** | `BuildActionProvider`, `BuildExecution`, `BuildOutput`, `BuildProgress`, `BuildCommands`, `RunConfiguration` |
| `api.project.customizer` | T2-FX | **planned — M1** | `ProjectCustomizerPanel`, `@CustomizerRegistration`, `ProjectCustomizerPanel` rendering |
| `api.editor` | T2-FX | **partial — contract M0; migration M1** | `EditorService`, `EditorDocument`, `EditorContext`, annotations, completion/breadcrumbs SPIs, `ElementIcons`; the language decoration/lex SPI still moves out of `platform.editor` |
| `api.editor.intelligence` | M | **planned — M2** | `GotoProvider`, `NavigationProvider`, `StructureProvider`, `UsagesProvider`, `HierarchyProvider`, `HintProvider`, `RefactoringProvider`, `CodeGenerator` — all scoped by `LanguageId` ([Appendix I.3](appendix/I-language-soundness.md)) |
| `api.wizard` | T2-FX | **partial — contract M0** | only `@WizardRegistration`; `Wizard`, `WizardPanel`, `WizardEntry`/`WizardRegistry` move from `platform.wizard` in M0; the plan's former `WizardIterator`/`WizardDescriptor` names were never implemented |
| `api.templates` | M | **planned — M1** | `TemplateRegistry`, `TemplateCreator`, `@TemplateRegistration` |
| `api.search` | T2-FX | **partial — M1** | only `TextSearch`/`SearchOptions`; `SearchProvider`, `SearchRequest`, `SearchResult`, `SearchScope` are the M1 contract (`SearchScope` currently lives in `ide.search`) |
| `api.file` | T2-FX | **transitional — dissolve M0/M1** | `FileIconProvider`, `FileTypes`, `FileSelectionContext`; target is `api.views`/`api.editor`/`ide.file`, not a permanent module |
| `api.debug` | M | **planned — M2** | `DebuggerBackend`, `DebuggerSession`, `Breakpoint`, `StackFrame`, `Variable` |
| `api.profile` | M | **planned — M2** | `ProfileBackend`, `ProfileSession`, `StackTree`, `HeapSnapshot` |
| `api.tests` | M | **planned — M2** | `TestRunner`, `TestSession`, `TestReportParser`, `CoverageProvider` |
| `api.vcs` | M | **planned — M2** | `VcsProvider`, `VcsStatus`, `VcsHistoryProvider`, `VcsAnnotator` |
| `api.diff` | M | **planned — M2** | `Diff`, `DiffResult`, `Patch`, `Merge` (headless engine) |
| `api.tasklist` | M | **planned — M2** | `TaskProvider`, `Task`, `TaskScope` |
| `api.plugins` | T1 | **planned — M5** | plugin metadata, install/update service surface |
| `api.layer` | T1 | **landed** | `LayerPaths` — the one owner of the layer folder constants (`NbFx/Views`, …); nbfx-internal, not part of the published plugin API (§3.3, Appendix C.3) |

**Rule of creation:** an `api.*` module exists only when a second module (or a plugin) needs it. Start with
T0/T1 inside the owning module and promote deliberately. The table is the **target catalogue**, not a backlog:
`refactor/api-split` creates only the modules whose consumers exist at that point; the rest are created by their
own step. A module with a planned consumer is `M`/T2-FX from the start and published `devel` when its first
consumer lands — the word *proposed* does not appear in a tier column (Decision 21). A module that exists but
whose contract is incomplete is `partial`, not `landed`; it stays `devel` and is not signed until W2a completes
the contract (Decision 26).

### 6.1.1 API reality (M0 baseline)

The API split created the modules; it did not finish the contracts. A module is only *landed* when the type a
consumer or plugin implements is in its exported API/SPI packages and a downstream fixture can implement it
without importing a T0 module. The remaining M0 gaps are the wizard, status and property/nodes contracts;
dialogs, search and project complete in M1; the editor/workspace extraction starts in M0 and finishes in M1.
Options landed with #162.

State vocabulary: **planned** = module not created; **partial** = module exists but its contract is incomplete;
**landed** = contract complete and consumable; **dissolved** = removed by a step. Run `ant api-audit` for the
generated, per-module report (`build/api-audit.txt`); it is a report, not a gate.

### 6.2 `nbfx-platform` — shell and platform services

| Module | Purpose | Notes |
|---|---|---|
| `platform.shell` | AppShell composition root: main window, docking, session, menu/toolbar/status wiring, active component and focus scope | landed with the generic core (tab panes, docking, sliding areas, toolbar, status helpers, file drag-and-drop, desktop actions, close/document adapters, tab-title SPI, action-context services, progress overlay, session state and restorer) and the default `ShellApplication`/`ShellAppProvider`; `ide.workbench` contributes the IDE assembly and overrides the app provider by priority. **Generic chrome landed:** `platform.shell.api` contracts/SPI, `DefaultShellChrome`/`ToolBarHost`, `ShellMenuBar`/`ShellToolBars`, `ShellChromeProfile`, `DynamicMenu`, `ShellBranding`, `ShellCommandResolver`, `ShellIconProvider`, `ShellCommandsProvider`, `DefaultWindowEnvironment` and status-bar composition (`platform.status`). The IDE main window and detached windows both use it and supply contributions; `ActionBars` has been removed. **M0 extraction:** API/SPI packages move to `org.netbeans.api.nbfx.shell`/`org.netbeans.spi.nbfx.shell`; `WindowPaneProvider` returns a `WindowPaneHost`, not `NbfxTabPane`; `NbfxTabPane` and the implementation root become internal (friend packages during the transition); the stable window facade is `ShellWindow` |
| `platform.editor` | FX code area, buffers, decorations, search/replace, completion popup; **no language** | landed; project/Java-vocabulary coupling removed. **M0/M1 extraction:** the language decoration/lex SPI moves to `api.editor`/`org.netbeans.spi.nbfx.editor`; `CodeEditor`, `SearchSupport` and the breadcrumb widgets stay internal; the language/IDE consumers migrate off implementation packages |
| `platform.workspace` | docking model + FX docking view | landed; replaces `nbfx.docking`/`nbfx.windows`. **M0 extraction:** `ViewSpec`/`ViewRegistry` move to `api.views`; `WindowSystem`/`WindowState`/`WindowSink` move to the shell API; the docking internals stay internal friend packages for `platform.shell` |
| `platform.actions` | command registry, keymap, action context impl | replaces `nbfx.actions` + `nbfx.context`; the generic context services live in `platform.shell` |
| `platform.menus` | menu/toolbar registrations and generic menu vocabulary; the JavaFX menu/toolbar builder is in `platform.shell`, IDE entries are contributed by `ide.workbench` | replaces `nbfx.menus` |
| `platform.status` | status bar + element rendering | replaces `nbfx.statusbar` |
| `platform.dialogs` | dialog host, notification line, toasts, exception reporting | API partial (`ErrorReporter` only); API contract and implementation M1 |
| `platform.progress` | progress UI (status line, all-tasks popup, task details) | replaces `nbfx.progress` (landed, PR #2 — §18) |
| `platform.theme` | theme manager, light/dark palettes, branding, editor syntax palettes | **landed** (themes, token providers, OS light/dark); `org.netbeans.api.nbfx.theme` is T1, the implementation package is internal |
| `platform.settings` | settings storage UI (preferences browser, export/import) | planned — M1 |
| `platform.options` | options window (categories, tabs, search, validity) | **T0** implementation; the panel SPI and displayer live in `api.options` (#162); no public package, spec, arch or apichanges |
| `platform.nodes` | FX node views + generic property sheet; project/editor property contributors live in `ide.workbench` | property sheet landed; node views planned M1; T0 implementation, the model is `api.nodes`/`api.property` |
| `platform.output` | output window view on `api.output` | landed UI; `api.output` extraction M1 |
| `platform.execution` | process launcher, pumps, PTY (JLine), cancellation | planned — M1 (process first; terminal separate) |
| `platform.terminal` | VT/xterm model + FX renderer + tabs | planned — M2, after the VT parser/library decision in M1 |
| `platform.quicksearch` | command palette + providers | planned — M1, after `api.search` is redesigned |
| `platform.credentials` | keyring service + unlock prompt | planned — M1 |
| `platform.favorites` | favourites view | planned — M1 |
| `platform.templates` | template model (discovery/creation) | planned — M1; replaces `nbfx.templates` |
| `platform.wizard` | wizard framework + themed shell | replaces `nbfx.wizard` |
| `platform.launcher` | application entry point; `AppProvider` selects the JavaFX application | landed; replaces `nbfx.launcher`; the IDE app provider is `ide.workbench.NbfxAppProvider` |
| `platform.macos` | macOS integration (menus, file associations) | replaces `nbfx.macos` |

The platform cluster also owns the platform API modules listed in §6.6. The **AppShell** is this cluster's
application framework; `platform.shell` is its composition root, not the whole AppShell.

*(Shell annotations — action/view/menu/toolbar/status — live in their `api.*` module, not in a separate
`platform.annotations` module; the annotation processors ship in the same jar. See Decision 4.)*

### 6.3 `nbfx-ide` — IDE building blocks

| Module | Purpose | Notes |
|---|---|---|
| `ide.actions` | stable IDE-only command ids (`IdeActionIds`), shared by project, file, editor, debug, search, VCS and workbench modules | new; kept below `ide.workbench` so those modules do not depend on the assembly |
| `ide.workbench` | IDE assembly and contributions to the AppShell: app provider, launcher app, project switching and loading, project session state, project-aware tab titles, IDE branding and the IDE scenario runner; owns `IdeMenus`; the action vocabulary lives in `ide.actions` | landed; absorbs the former `nbfx.ui` module. The main window and detached windows use the generic shell chrome; contributions include `RecentProjectsMenuFactory`, `OpenProjectsMenuFactory`, `IdeShellCommandResolver`, `IdeShellIconProvider`, `ProjectBranding`, `DetachedBars` and `DetachedChromeProfile`; `ProjectEditorScope` moved to `project.context`; project/session state moved to `ProjectState`; `ActionBars` removed; the IDE is the AppShell's primary consumer |
| `ide.project` | project model impl, registry, kind detection | on `api.project` |
| `ide.project.ui` | New File/New Project wizards, project views, project context, and **project problems UI** | replaces `nbfx.project`/`project.ui`; implements `ProjectProblemsImplementation` in JavaFX (alert + resolve customizer), replacing `projectui`’s `BrokenReferencesImpl` Swing panels |
| `ide.project.actions` | project commands/menus | replaces `nbfx.project.actions`; owns the project lifecycle commands (open/close/close all/next/previous), New Project/New File wizard commands, the File/Window menu and File toolbar metadata, and the build commands, with the shell providing `ProjectOpener`/`ProjectLifecycle` |
| `ide.project.customizer` | customizer host + built-in categories | on `api.project.customizer` |
| `ide.build` | build/run orchestration on `api.build` + `api.execution` | replaces `nbfx.run` |
| `ide.editor.xml`, `ide.editor.css` | XML/CSS syntax decoration, breadcrumbs and the CSS Find Usages provider (language modules, not core) | from `nbfx.editor` |
| `ide.editor.intelligence` | UI for goto/search, usages, hierarchies, hints, refactoring preview, codegen, Javadoc | new; engines/SPIs are `api.editor.intelligence` (M) |
| `ide.javadoc` | Javadoc viewing/index/completion | new |
| `ide.structure` | structure/navigator view on `api.nodes` | replaces `nbfx.structure` |
| `ide.structure.xml`, `ide.structure.css` | XML/CSS structure providers for the generic structure panel | from `nbfx.structure.xml`/`nbfx.structure.css` (landed, PR #5) |
| `ide.search` | find in projects + results view | replaces `nbfx.findinprojects` |
| `ide.usages` | generic usages engine + results tree/UI over `api.usages` | generic half of `nbfx.findusages` |
| `ide.file` | file actions/desktop API | replaces `nbfx.file.actions` |
| `ide.debug` | debugger views + session UI on `api.debug` | replaces `nbfx.debug` UI |
| `ide.profiler` | profiler views on `api.profile` | new |
| `ide.tests` | test runner UI + coverage view | new |
| `ide.vcs` | VCS framework + status/badges | replaces `nbfx.vcs` |
| `ide.vcs.git` | Git provider (jgit) | new |
| `ide.diff` | diff/merge view + patch UI | new |
| `ide.localhistory` | local history view | new |
| `ide.tasklist` | task list view + providers | new |
| `ide.bookmarks` | bookmarks view | new |
| `ide.libraries` | libraries manager | new |
| `ide.plugins` | plugin manager UI | replaces `nbfx.plugins` |
| `ide.modulegraph` | module dependency graph | new |
| `ide.i18n`, `ide.beans`, `ide.jshell` | i18n/beans/JShell tooling | new |

The IDE cluster also owns the IDE API modules listed in §6.6. Projects are an **IDE-tier** capability: the
platform AppShell starts without them.

### 6.4 `nbfx-java` — Java/JVM usage

| Module | Purpose |
|---|---|
| `java.platforms` | Java Platforms (JDK storage, sources/javadoc, JavaFX capability) + options |
| `java.projecttype.java` | J2SE (Ant) project type |
| `java.projecttype.maven` | Maven project type (process-based builds) |
| `java.projecttype.gradle` | Gradle project type |
| `java.projecttype.ant` | Ant project support |
| `java.build.ant` | Ant runner (in-process listeners) |
| `java.model` | **T0 bridge** over `org.netbeans.api.java.source`/`libs.javacapi`: classpath cache, in-memory `FileObject`, task runner, position↔offset, nb-javac normalisation into `api.language`/`java.elements` values (Appendix B) |
| `java.elements` | the Java element vocabulary (`SourceElementKind`, `SourceTypeKind`, `SourceModifiers`) moved out of the neutral `api.*` ([Appendix I.3](appendix/I-language-soundness.md)) |
| `java.editor` | Java semantics of the editor: lexer decoration, semantic completion, mark-occurrences, highlights, diagnostics, Java breadcrumbs, hints/refactoring/codegen/Javadoc engines |
| `java.structure` | Java structure provider |
| `java.usages` | Java provider for `api.usages` (Java search over `java.model`) |
| `java.navigation` | open-at-caret / `TreeNav` |
| `java.debug` | JPDA backend (`api.debug`) |
| `java.profiler` | JFR + async-profiler backends (`api.profile`) |
| `java.tests` | JUnit/TestNG + Ant/Maven/Gradle + JaCoCo (`api.tests`) |
| `java.module` | module-info support + module graph data |
| `java.beans`, `java.i18n`, `java.jshell` | language-specific tooling |

### 6.5 `nbfx-javafx` and `nbfx-libs`

| Module | Purpose |
|---|---|
| `javafx.project` | JavaFX project usage (J2SE Ant + `javafx.enabled=true`), customizers, run/build |
| `javafx.fxml` | FXML editing/completion |
| `javafx.scenebuilder` | embedded SceneBuilder (kit 26.0.0) |
| `libs.javafx` | bundled JavaFX runtime |

**Deferred — created only when the owning step lands, never before:** `libs.jline` (execution/terminal,
M1/M2), `libs.asyncprofiler.<os>-<arch>` (profiler, M2), `libs.jgit` (Git, M1 — reuse NetBeans' jgit modules),
`libs.jacoco` (coverage, M2 — reuse the shipped `org.jacoco.core`). A `libs.*` module exists only when the step
that needs it starts ([Appendix G](appendix/G-licences-and-notices.md) rule 6).

### 6.6 Deliverables, the AppShell and API placement

The two deliverables are cluster combinations; neither is a module or an assembly tier:

| Deliverable | Clusters | Acceptance |
|---|---|---|
| **IDE** (primary) | `nbfx-libs`, `nbfx-platform`, `nbfx-ide`, `nbfx-java` (+ `nbfx-javafx` when it lands) | the W0/M1/M2 scenarios on a real project; the application image |
| **Platform** (secondary) | `nbfx-libs`, `nbfx-platform` | `ant platform-build` produces the platform-only distribution; the default `ShellApplication` starts without IDE modules; `ant appshell-conformance` passes; platform modules and APIs are published as source and binary modules |

Each deliverable also includes the upstream clusters its nbfx cluster dependencies require (today the
platform set uses upstream `platform` and `ide`; the IDE set adds upstream `ide`, `java`, `extide` and
`apisupport`).

**The AppShell** is the platform deliverable's application framework. `platform.shell` is its composition
root; the services below are the AppShell. The IDE is its primary consumer; downstream may use the whole
AppShell, individual services, or another composition. "State" is honest: `landed` (the UI/implementation
works), `partial` (the API/SPI is not yet the contract a plugin can implement), or `planned`.

| AppShell service | Modules | State |
|---|---|---|
| Window system, docking, tabs, detached windows | `platform.workspace` + `platform.shell` | workspace landed; generic shell and session landed; public surface extraction (ViewSpec/ViewRegistry, WindowSystem, `WindowPaneHost`) M0 |
| Actions, keymap, command registry | `platform.actions` + `api.actions` | landed |
| Action context, global selection, focus, enablement | `platform.actions` + `platform.shell` | landed; generic services in `platform.shell`, `OpenProject` contexts in `ide.workbench` |
| Views and view areas | `platform.workspace` (`ViewSpec`, `ViewRegistry`, `ViewAreaRegistry`) + `api.views` (`@ViewRegistration`, `@AreaRegistration`) + `platform.shell` (view manager) | view metadata, areas, permanent panes, pane instances and the first-run dock tree are declared; view placement, area-to-pane resolution and shell start-up all consume the registrations; the dock area takes an explicit main role, so pane order no longer decides where content goes, and keep-when-empty panes stay visible; non-permanent pane lifecycles remain a follow-up. M0 moves `ViewSpec`/`ViewRegistry` into `api.views` |
| Menus, toolbars, context menus | `platform.menus` (registrations and generic menu ids) + `platform.shell` (JavaFX menu/toolbar builder) + `ide.workbench` (IDE entries/contributions) | landed; `ShellMenuBar`/`ShellToolBars` build from the layer and dynamic sections, the IDE main window uses them, and `ViewMenuFactory`/`ConfigureWindowMenuFactory` supply the platform sections; detached windows use `DetachedBars` with a reduced profile |
| Shell chrome | `platform.shell` (composition, menu/tool bar builder, application menu, branding, default window environment) + `platform.status`/`api.status` (status bar) + `platform.menus` (registrations) + `ide.workbench` contributions | landed for the main window; `ShellChrome` exposes only top/bottom/dispose; the default `ShellApplication` gets the generic chrome, branding, status bar, application menu and window environment without a subclass; detached windows use the generic builders too. M0 moves the API/SPI packages to the global roots and makes `NbfxTabPane` internal |
| Status bar | `platform.status` + `api.status` | landed renderer; **API partial: `StatusElement`/element SPI move to `api.status` in M0** |
| Options | `platform.options` + `api.options` | **landed (#162)**; `platform.options` is T0, the controller/displayer contract is `api.options` |
| Settings | `platform.settings` + `api.settings` | planned — M1 |
| Wizards | `platform.wizard` + `api.wizard` | landed UI; **API partial: `Wizard`/`WizardPanel` move to `api.wizard` in M0** |
| Nodes/explorer + property sheet | `platform.nodes` + `api.nodes` + `api.property` | property sheet landed; **API partial: the model moves to `api.nodes` and the editor SPI to `api.property` in M0**; explorer views M1; IDE contributors in `ide.workbench` |
| Dialogs, notifications | `platform.dialogs` + `api.dialogs` | **API partial (`ErrorReporter` only); contract + implementation M1** |
| Progress | `platform.progress` + `api.progress` | landed |
| Output | `platform.output` + `api.output` | output UI landed; **`api.output` extraction M1** |
| Execution | `platform.execution` + `api.execution` | planned — M1 (process first) |
| Terminal | `platform.terminal` + `api.terminal` | planned — M2; M1 chooses the VT parser/library |
| Quick search | `platform.quicksearch` + `api.search` | **API partial; search contract + palette M1** |
| Theme/branding | `platform.theme` | **landed** (light/dark, token providers, OS mode); impl package internal |
| Credentials | `platform.credentials` | planned — M1 |
| Favorites | `platform.favorites` | planned — M1 |
| Templates | `platform.templates` + `api.templates` | planned — M1 |
| Launcher | `platform.launcher` | landed; `AppProvider` + generic `GuiRunLevel`/`LauncherInstall` |
| Editor core | `platform.editor` + `api.editor` | landed; **API extraction M0 start, M1 finish** (language SPI off the implementation packages) |
| Projects | `api.project` + `ide.project` | **IDE-tier**; the AppShell starts without projects; `OpenProjects` is the documented bridge until M1 review |
| File actions | platform file service + `ide.file` | drag-and-drop and desktop integration landed in `platform.shell`; IDE-specific file actions stay in `ide.file`; `api.file` dissolves in M0/M1 |

**Chrome contract rules.** `ShellChrome` is a composition contract, not a second implementation:
`Node top()`, `Node bottom()` and `void dispose()`. Concrete `MenuBar`/`ToolBarContainer`/`StatusBar`
controls stay inside `DefaultShellChrome` and are not part of the product SPI. Contribution contexts are
narrow (the action registry, `ActionContext` and a `Lookup`); they do not expose `AppState`,
`DockArea`, `SlidingArea` or `NbfxTabPane`. `DynamicMenu.Factory`, `StatusContribution` and
`WindowEnvironmentContribution` are ordered, failure-isolated and disposed with the shell. New chrome SPIs
are T1/T2-FX API and must be covered by W2 `arch.xml`/`apichanges`/`check-api`; anything not intended for
downstream stays in an internal package. M0 renames the exported API/SPI packages to
`org.netbeans.api.nbfx.shell`/`org.netbeans.spi.nbfx.shell`; `WindowPaneProvider` returns a `WindowPaneHost`,
and the implementation root is removed from `public-packages`. The detailed contracts and layer rules are in
[`appshell-chrome.md`](appshell-chrome.md).

**API placement.** The API tier is module metadata, not a cluster. The `nbfx-api` cluster is dissolved
(W1.7, landed); each API module lives in its domain cluster:

| Cluster | API modules |
|---|---|
| `nbfx-platform` | `api.actions`, `api.dialogs`, `api.editor`, `api.file` (transitional), `api.layer`, `api.options`, `api.progress`, `api.property`, `api.search`, `api.status`, `api.views`, `api.wizard`, `api.settings`, `api.output`, `api.execution`, `api.terminal`, `api.language`, `api.nodes`, `api.templates`, `api.plugins` |
| `nbfx-ide` | `api.project`, `api.build`, `api.project.customizer`, `api.editor.intelligence`, `api.usages`, `api.debug`, `api.profile`, `api.tests`, `api.vcs`, `api.diff`, `api.tasklist` |
| `nbfx-java` | `java.elements` (from `api.elements`) |

Boundary decisions: projects are IDE-tier (a downstream app that needs projects adds the IDE cluster); the
plugin API/manager is platform, the plugin UI is `ide.plugins`; generic file selection/drag-drop/desktop
integration is platform, IDE-specific file actions stay in `ide.file`.

**API isolation.** An API module may depend only on other API modules in the same or a lower cluster, on
`nbfx-libs`, and on allowlisted upstream APIs. It may not depend on an implementation module. Before the
cluster move, the leaks are removed: `api.editor` loses `api.project`/`api.elements`, `api.usages` loses the
Java vocabulary, `api.actions` loses the Java-specific element location, `api.views` loses project-specific
navigator callbacks, and `api.actions.ActionIds` loses the IDE command ids. W2a adds the **consumer edge**:
a module outside an implementation module may import its `org.netbeans.modules.nbfx.*` packages only through
a declared friend package; the compiler plus the friend guard enforce it.

## 7. Module migration map (current → target)

| Current module | Target |
|---|---|
| `nbfx.api` (61 classes in 12 packages + 4 root classes) | `api.actions`; `api.views` (from `api.view` + `api.icons`); `api.status`; `api.editor` (from `api.editor` + `api.breadcrumbs` + `api.completion`); `api.elements` → `api.language` (neutral `SourceElementKind`/`SourceTypeKind`/`SourceLocation`/`ElementModifiers`) + `java.elements` (`SourceModifiers`/`JavaElementTypes`) + `api.editor` (`ElementIcons`); `api.project`; `api.build`; `api.progress`; `api.search`; `api.file` → `api.views`/`api.editor`/`ide.file`. Root classes: `Cancellation` → `api.progress`, `ContentManager` → `api.editor`, `ErrorReporter` → `api.dialogs`, `FxApplicationMenu` → `api.views`/`platform.menus` |
| `nbfx.annotations` | dissolved: annotations move into their `api.*` module; path constants are owned by `api.layer.LayerPaths` |
| `nbfx-api` cluster | **dissolved (W1.7, landed)**: each API module moved to its domain cluster (§6.6); the API tier stays module metadata |
| `nbfx.actions`, `nbfx.context` | `platform.actions` |
| `nbfx.docking`, `nbfx.windows` | `platform.workspace` |
| `nbfx.menus` | `platform.menus` (registrations and vocabulary) + `platform.shell` (JavaFX builder) + `ide.workbench` (IDE entries) |
| `nbfx.statusbar` | `platform.status` (+ `api.status`) |
| `nbfx.options` | `platform.options` (+ `api.options`) |
| `nbfx.output` | `platform.output` (+ `api.output`) |
| `nbfx.wizard` | `platform.wizard` (+ `api.wizard`) |
| `nbfx.ui` | **dissolved**: generic shell → `platform.shell`, IDE assembly/project/editor contributions → `ide.workbench`; generic menu/toolbar/status/application-menu/branding/window-environment chrome is extracted from `ide.workbench.ActionBars`/`JavaFXLaunchApp` into `platform.shell` in W3a `feature/shell` (landed) |
| `nbfx.ui.context` | generic action-context services → `platform.shell` (landed); `OpenProject` contexts → `ide.workbench` (landed) |
| `nbfx.launcher` | `platform.launcher` (generic lifecycle) + `ide.workbench` (`NbfxAppProvider` + scenario runner); landed |
| `nbfx.macos` | `platform.macos` |
| `libs.nbfx.javafx` | `libs.javafx` |
| `nbfx.propertysheet` | `platform.nodes` (generic FX sheet) + `api.property` (editor SPI) + `api.nodes` (model); project/editor property contributors → `ide.workbench` (landed) |
| `nbfx.project` (wizards) | `ide.project.ui` |
| `nbfx.project.ui`, `nbfx.project.actions` | `ide.project.ui`, `ide.project.actions` |
| `nbfx.project.ui.api` | `api.project` (kind + identity), `api.build` (build/run), UI → `ide.project.ui` |
| `nbfx.project.context` | `api.project` (context service) or `ide.project` |
| `nbfx.project.customizer` | `ide.project.customizer` (+ `api.project.customizer`) |
| `nbfx.run` | `ide.build` (+ `api.build`, `api.execution`) |
| `nbfx.editor`, `nbfx.editor.actions` (88 files, `figures.md`) | `platform.editor` (Java-free core, ~50 files) + `java.editor` (Java, ~36 files) + `ide.editor.{xml,css}` + `api.editor` (seam) |
| `nbfx.structure` | `ide.structure` |
| `nbfx.structure.css`, `nbfx.structure.xml` | `ide.structure.css`, `ide.structure.xml` |
| `nbfx.progress` | `platform.progress` (+ `api.progress` seam) |
| `nbfx.findinprojects` | `ide.search` |
| `nbfx.findusages` (34 files) | `ide.usages` (generic engine + UI) + `java.usages` (Java search) + `api.usages` |
| `nbfx.file.actions` | `ide.file` (+ `api.views` file context) |
| `nbfx.debug` | `ide.debug` (+ `api.debug` + `java.debug`) |
| `nbfx.vcs` | `ide.vcs` (+ `ide.vcs.git` + `api.vcs`) |
| `nbfx.plugins` | `ide.plugins` (+ `api.plugins`) |
| `nbfx.build.ant` | `java.build.ant` |
| `nbfx.project.ui.java` | `java.projecttype.java` (+ `java.navigation` for `utils/TreeNav`) |
| `nbfx.project.ui.maven` | `java.projecttype.maven` |
| `nbfx.project.ui.gradle` | `java.projecttype.gradle` |
| `nbfx.project.ui.ant` | `java.projecttype.ant` |
| `nbfx.structure.java` | `java.structure` (+ `java.model`) |

The table is **total**: every current module and every root-package class has exactly one target. The
tree's current counts are generated in [`figures.md`](figures.md); do not hand-type them. A reverse index
(target ← sources) is added when the split starts; a target with neither a source nor a "new" note is a bug
in the table.

The rename (`refactor/module-names`, landed) was one step, mechanical in *code*, and it ran **before** the
API split so the API modules got the final names. It was **not** behaviour-free: a module code-name change
moves the `NbPreferences` root, the `Modules/<code-name>` layer namespace, the autoupdate id and the
cluster/guard paths. Pre-1.0 that is allowed (no released version ⇒ code-name churn is permitted); the rule
to record is **code-names freeze at 1.0**, and the step adds a settings/layer round-trip test before and
after.

## 8. Data and compatibility

- **Project properties** keep the NetBeans names (`javac.classpath`, `run.classpath`, `javadoc.*`, …) so
  Ant/Maven/Gradle projects round-trip; the customizer is the only FX part.
- **Project type ids are user data.** An existing project stores `<type>` in `nbproject/project.xml`
  (J2SE: `org.netbeans.modules.java.j2seproject`). Our modules may be renamed, but the *provided project
  type id* and the `javafx.enabled` property must match the NetBeans convention exactly, or every existing
  project stops opening. This is a hard rule, not a preference.
- **nb-javac duplicates JDK types.** The IDE can run on nb-javac, which bundles its own
  `javax.lang.model` classes; never compare element/modifier identity across the semantic boundary —
  convert by name (Appendix A.4, `java.elements.SourceModifiers`).
- **Preferences** keep the NetBeans nodes where a reused headless module owns them; nbfx-specific keys use
  `nbfx.<capability>.<key>`. The project-problems “show this warning again” preference is
  `nbfx/project/problems/showAgain`. Module code-name changes move `NbPreferences` roots: covered by a round-trip
  test in `refactor/module-names`, and code-names freeze at 1.0.
- **Formats**: we *read* `.nps`, `.hprof`, `.jfr`; we write only formats we own or that are public.
- **Keymap/breakpoints/platform storage** stay in the platform storage; the UI reads/writes them.
- **Debuggees/VCS/native tools** are launched through `api.execution` (PTY by default for run), so no
  module starts processes itself.

### 8.1 Baseline (the compatibility contract)

The plan stated none of this; a plugin author and the CI both need it.

| Item | Value | Consequence |
|---|---|---|
| **Minimum JDK** | **25** (LTS) | every nbfx module compiles with `javac.release=25` (as `lib.javafx` does today) → class file 69 |
| **JavaFX** | **27** (`libs.javafx/javafx-sdk.properties`, a pinned OpenJFX SDK per platform) | jars fetched into `libs.javafx/release/modules/ext/` at bootstrap: `javafx.base`, `javafx.graphics`, `javafx.controls`, `javafx.fxml`, `jfx.incubator.richtext`, `jfx.incubator.input`; **no `javafx.web`** (licence and size, [Appendix G](appendix/G-licences-and-notices.md)) |
| **FX internals** | `platform.editor` uses `com.sun.jfx.incubator.scene.control.richtext.{RichTextAreaSkinHelper,VFlow,CaretInfo}` | reachable today because the FX jars sit on the **classpath (unnamed module)**; if FX ever moves to the module path, the launcher must add `--add-exports javafx.controls/com.sun.jfx.incubator.scene.control.richtext=ALL-UNNAMED` — centralised in one launcher config, never scattered |
| **JPMS posture** | NetBeans module system on the classpath; FX is an autoload module (`is.autoload=true`) started by `launcher.FxGuiRunLevel` (`Application.launch`) | no module-path requirement today; a future `jpackage`/`jlink` step must revisit this ([§11 W10](PLAN.md)) |
| **Profiler** | **agent-free** | the NetBeans profiler agent cannot read class files newer than **55 (JDK 11)** while the target is JDK 25 → profiling = JFR + bundled async-profiler + a JMX sampler. Record this, or someone will "fix" it by re-adding the agent |
| **Test JVM** | Headless glass (§10.3) | FX tests run without a window in CI; no Monocle |

Checked by `check-baseline` (§9): one `javac.release` across nbfx modules; no `javafx.web` in the bundle; the
launcher's `--add-exports` list is empty today and lives in one file.

## 9. Guardrails (structural, in the build)

The rule is [Appendix A](appendix/A-toolkit-policy.md); this is the implementation. **Status is honest**:
the checks marked **exists** run today (`check-purity.xml` has `check-sources`, `check-java-source`,
`check-languages`, `check-naming`, `check-namespace`, `check-layers`, `check-api-isolation`, `check-appshell`,
`check-dependencies`, `check-jars`, `check-packaging`, `check-figures`, `check-budget`, `check-baseline`,
`check-logging`; the root `ant guards` target adds `check-api`); the rest are `pending` with the step that
lands them — a pending check is not a guardrail until `feature/ci` runs it (§11 W0/W12).

| Target | Status | Mechanism | Fails when |
|---|---|---|---|
| `check-sources` | **exists** | import denylist + per-module allowlist | a forbidden import family outside its declared owner |
| `check-dependencies` | **exists** | walk each `project.xml` graph | a forbidden NetBeans UI module |
| `check-layers` | **exists** (W1.7) | compare every nbfx module dependency with the cluster order | an upward nbfx edge (`ide` → `platform`, `platform` → `ide`, …) |
| `check-api-isolation` | **exists** (W1.7); W2a extends it to the tier graph | T1/T2-FX/M modules may depend only on API modules in the same or a lower cluster, plus libs/upstream APIs; T2/M may not depend on T0; T0 may depend on anything | an API module depends on an implementation module, or a published API depends on a T0 package |
| `check-appshell` | **exists** | source scan of `nbfx-platform` modules | an AppShell module imports project/IDE/Java/JavaFX-usage/parsing types |
| `check-platform-delivery` | **exists** (W1.7) | the platform-only configuration selects no `nbfx-ide`/`nbfx-java` cluster, and the platform cluster contains no IDE/Java jars and does contain `platform.shell` | an IDE/Java cluster or module leaks into the platform deliverable |
| `check-jars` | **exists** (the current four cluster directories; `jdeps` + `nbfx-javafx` pending) | jar listing (bundled classes) on the cluster directories of the selected deliverable; `jdeps`/`nbfx-javafx` when the cluster lands | a reference/bundle of a forbidden package outside an allowed tier or exemption |
| `check-packaging` | **exists** (extend to the retirement matrix and per deliverable) | the retirement matrix (absent modules per milestone); the platform artifact must not contain `nbfx-ide`/`nbfx-java` | a retired module reappears in the distribution, or an IDE module appears in the platform artifact |
| `check-figures` | **exists** | `ant -f check-purity.xml check-figures` → `java docs/plan/tools/Counts.java --check` | stale figures, a hand-typed count ([`figures.md`](figures.md)) |
| `check-java-source` | **exists** (W1) | `org.netbeans.api.java.source`/`libs.javacapi` only in the `java.*` semantic modules | imported anywhere else (Appendix B.5) |
| `check-languages` | **exists** (W1) | `LanguageGuard` rejects concrete `java`/`css`/`xml`/`fxml` branches in `platform.editor`; providers are language-scoped through `SyntaxDecoratorFactory`, and unknown extensions get a plain-text decorator (Appendix I.3/I.4) | an `if (java)` in the core, or an unknown language falling back to Java |
| `check-api` | **exists** (type snapshot); W2a/W2b extend it | exported set ⊆ `arch.xml` set; governed module has `apichanges`/`.sig`; contract-complete modules only; dependant spec versions exist; the API tier is read from `nbproject/nbfx-policy.properties`, not from a cluster | a subtree export, a missing `arch.xml` `<api>` entry, a signed stub, or a tier inferred from `nbfx-api` |
| `check-naming` | **exists** (W1) | no `Fx*`/`Impl`/`Object`-factory in public API; `LayerPaths` is the only shared constants class | a forbidden public name |
| AppShell conformance | **exists** (first slice, W1.7/W3) | platform-only fixture asserts no IDE class is loadable, the default `ShellApplication` starts, and exercises the action registry/context, property sheet, progress, output and wizard registry; grows with the W3 services, starting with generic shell chrome (menus/toolbars/status/application menu/branding), then nodes, dialogs, terminal, quick search, theme, credentials, favorites, templates. M0 adds an **API-only downstream fixture** that implements the plugin extension points with API/SPI imports only | a service cannot be used without IDE modules, downstream must subclass `ShellApplication` for standard chrome, or an extension point needs a T0 import |
| `check-namespace` | **exists** (W1.6); W2a extends it to the tier metadata | `NamespaceGuard` enforces module code names, directories and API/SPI/implementation package roots; API packages are `org.netbeans.api.nbfx.*`, SPI packages `org.netbeans.spi.nbfx.*`, implementation packages `org.netbeans.modules.nbfx.*`; the tier is read from `nbproject/nbfx-policy.properties` | an old `com.gluonhq` module directory, a non-namespaced package, or an API/SPI-root package exported by a T0 module |
| `check-baseline` | **exists** (W1) | one `javac.release`; no `javafx.web`; one `--add-exports` file (§8.1) | a drifting JDK/FX/class-file baseline |
| `check-i18n` | pending (W4) | every user-visible string is an `NbBundle` key in `Bundle.properties` | a missing key, a hardcoded string |
| `check-bundles` | **exists** (W1.7) | `BundleGuard`: every literal key passed to `NbBundle.getMessage(X.class, "key")`, directly or through a one-line `message(key)` helper, exists in the `Bundle.properties` of `X`'s package | a key stranded in another module after a class moved (a `MissingResourceException` at run time) |
| `check-logging` | **exists** (W1) | no `System.out`/`printStackTrace`; `Logger` named after the module | a stray print |
| `check-lookup` | pending (W3) | the `Lookups.forPath` invocation count is constant across N user actions (Appendix E.5) | a per-action layer lookup |
| leak test | pending (W3) | open/close a view N times; listener count returns to baseline and the view is weakly reachable (Appendix E.2) | a leaked subscription |
| `check-licences` | pending (W10) | every bundled jar/native has a `NOTICE` entry; `javafx.web` absent (`LICENSE`/`THIRDPARTY` present) ([Appendix G](appendix/G-licences-and-notices.md)) | an unnotified bundle |
| `check-budget` | **exists** (W0) | startup, "usable", first completion, module count, memory | >20 % regression vs the milestone baseline |

The grep-only `check-jars` now covers the nbfx cluster directories of the selected deliverable for bundled
`javax.swing/` and `java/awt/` classes; it still cannot see a class that merely imports a JDK Swing type,
which is the gap `jdeps` closes. `nbfx-javafx` is scanned when its first module lands.

## 10. Roadmap

M0 stays at **2026-12**. The milestone dates are unchanged; M0 is re-scoped to include the API-contract work
and the shell/window/workspace public surface. M1 stays at **2027-03** and carries both the remaining AppShell
services and IDE core. If M0 or M1 does not fit, that is a cut-list decision or an explicit plan change — not a
pre-emptive date move.

| Milestone | Target | Content | Exit |
|---|---|---|---|
| **M0 — Foundation + plugin contracts** | **2026-12** | shell/window/workspace public surface; options, wizard, status, property/nodes contracts; property sheet on `api.nodes`; `api.file` dissolution; progress, theme, options/wizard/property-sheet UI, output UI, launcher, packaging, conformance; W2a contract completion and W2b governance for the complete APIs | the walking-skeleton scenario (open→edit→build→run→output→commit) runs on a **real Ant/J2SE fixture project**; `refactor/api-clusters` + `refactor/appshell-boundary` landed; the platform-only distribution starts without IDE modules; an **API-only downstream fixture** implements the M0 extension points (options, wizard, status, property) using only API/SPI imports and the plugin allowlist; T0 modules publish no packages (named friend packages only); the conformance fixture covers the M0 services; the M0 rows of the retirement matrix are absent; all guards green; budget baseline recorded; **the M0 contract modules are complete and signed; `api.editor`'s exported SPI is complete in M0 and its consumer migration continues in M1** |
| **M1 — AppShell services + IDE core** | **2027-03** | AppShell services: finish the `platform.editor` consumer migration; dialogs, output API, settings, search+quicksearch, execution (process/PTY), templates, credentials, favorites, indexing; terminal scope/parser decision. IDE core: project model, project kind/build extraction, editor (core + Java), structure, search, usages (generic + Java), file actions, VCS+Git, diff, local history, tests **UI**, libraries, task list/bookmarks, plugins UI | every service is platform-only and covered by the conformance fixture; no product/IDE consumer imports a T0 package outside a declared friend; `api.dialogs`, `api.output`, `api.settings`, `api.search`, `api.execution`, `api.templates` are complete and signed; open/edit/search/find/commit/run-tests in FX on a J2SE project; every explorer panel renders through `api.nodes` (except popups); the M1 rows of the retirement matrix absent; guards green |
| **M2 — Java tooling** | **2027-07** | project types (J2SE/Maven/Gradle/Ant), Java Platforms, editor intelligence (goto, usages, hints, refactoring, codegen, Javadoc), debugger, profiler, Java tests engines/coverage, terminal | daily Java work on a Maven **and** an Ant project; the M2 rows of the retirement matrix absent; guards green |
| **M3 — JavaFX usage** | **2027-09** | JavaFX project, FXML editing, SceneBuilder | a JavaFX project opens, edits FXML, and runs; `javafx.enabled` round-trips |
| **M4 — Breadth** | **2027-12** | i18n, beans, JShell, remote debug/profiler, export/import, accessibility, multi-window, module graph view, OS bundles/installers (jpackage, Decision 19) | the intended feature scope is present; guards green; a11y pass recorded |
| **M5 — Ecosystem** | **2028** | plugin API hardening, compatibility reports, update centers, docs/tutorials | a third-party plugin builds against the published API and is published |

### 10.1 Critical path

The chain that decides the dates; everything else can slip around it:

```
feature/walking-skeleton → feature/ci → refactor/guard-java-source → refactor/api-naming → refactor/module-names → refactor/api-split
  → refactor/api-contracts (options/wizard/status/property/nodes) → refactor/shell-window-contract
  → refactor/workspace-public-surface                   → refactor/editor-spi
  → feature/appshell-conformance (API-only)             → M0
  → refactor/editor-consumers                           → feature/dialogs/output/settings/search
  → feature/execution                                   → feature/terminal (M2)
  → feature/projecttypes.java (J2SE)                    → feature/debugger (java.debug)
```

The platform branch is now on the critical path for M0: the plugin contracts (options, wizard, status,
property/nodes) and the shell/workspace public surface gate the platform deliverable. The editor branch
(`platform.editor` → `java.editor`) and the project branch (`api.project` → `java.projecttype.java`) run in
parallel after the contract work; they meet at `feature/build-run`. M1 (AppShell services + IDE core) is
gated by the editor consumer migration; M2 ("daily Java work") is gated by `feature/debugger`.

The **platform-deliverable path** after `refactor/api-split`:
`refactor/api-neutral → refactor/api-clusters → refactor/appshell-boundary → refactor/api-contracts →
refactor/shell-window-contract → refactor/workspace-public-surface → feature/appshell-conformance`.
W2a (contract completion) must land before W2b (governance/sigtest); governance for a `partial` module is
deferred, not faked.

Two upstream prototypes run in parallel with the critical path and are **not** allowed to delay it:
`chore/upstream-module-tools` (H.6, portable `nbbuild` checks → `nbfx-tools/`) with `refactor/guardrails`, and
`spike/java-source-headless` (H.1) with `refactor/java-plumbing`; `spike/progress-neutral-spi` (H.2) is the M0
upstream win.

### 10.2 Cut list (in cut order)

If a date is missed, cut in this order; each cut is a plan change, not a silent slip:
1. **M4 breadth** — JShell, beans/i18n tooling, module-graph view, remote debug/profiler, multi-window.
2. **M3 SceneBuilder** — FXML editing stays; the embedded SceneBuilder editor goes.
3. **Terminal** — `api.terminal`/`platform.terminal` stay at M2; if M2 slips, M4. Process execution stays.
4. **Profiler** beyond JFR sampling; heap-dump analysis visualisation.
5. **Non-Git VCS** providers.
6. **Maven/Gradle** project types — J2SE + Ant first, Maven/Gradle later (the M2 exit changes to "Ant and
   J2SE", the original JavaFX-cluster scope).
7. **M1 breadth** — credentials/favorites, then templates, then quicksearch.
Not cuttable: the walking skeleton, the guards, the API split, the **AppShell module boundary**, the
**M0 plugin contracts**, the editor core, the J2SE project type. The platform **distribution** (source/binary
artifacts) may follow the IDE if dates slip; the module boundary and the plugin contracts may not.

### 10.3 Test kit and test gate (implemented before W2a)

- **FX headless**: **glass headless** (`-Dglass.platform=Headless -Dprism.order=sw`), already used by the
  platform and shell modules; no test opens a real window in CI. Monocle is not bundled and not needed.
- **UI**: TestFX (`FxRobot`) only for shell scenarios; the W0 scenario is service-level (no UI events).
  Use `org.testfx:testfx-core:4.0.19-gluon` (the Gluon build) with the Headless platform; official TestFX
  still pulls in Monocle, which nbfx does not use.
- **QA placement**: IDE end-to-end scenarios live in the IDE cluster's test module (`ide.workbench` or a
  test-only IDE module) — not in the platform launcher; platform modules keep service tests, and the
  AppShell conformance fixture lives with the platform. `<test-dependency>` may cross layers, is never a run
  dependency and the test artefact is not shipped; the graph guard treats test edges separately. If scenarios
  multiply, move them to a test-only `nbfx-qa` cluster.
- **Harness**: NetBeans `nbjunit` (the test-file count is in [`figures.md`](figures.md)) + `nbbuild` `sigtest` targets for the
  governed API modules.
- **All unit tests are a PR gate.** `ant test` discovers every module under `nbfx-*/*` and runs its
  `test-unit` target. Modules without `test/unit/src` no-op, so the gate has no hand-maintained module list
  that can drift when a module is renamed. `-Dtest.modules=nbfx-platform/*` narrows the run for the local loop.
  CI runs all of them on every PR and on `main`; a failure uploads the module JUnit XML.
- **Scenario is part of the suite.** The headless IDE `ScenarioTest` lives in the IDE test module and is one
  of the `test-unit` runs; it proves the module system, layer, launcher and fixture project still work
  together. The platform shell smoke and AppShell conformance fixture are separate runs on the platform
  deliverable. CI does not repeat them in a second step.
- **Required test types**: unit (model, `M` modules), contract (threading per SPI, layer writer/reader), leak
  (open/close, Appendix E.2), budget (startup/usable/first completion, `check-budget`), smoke (walking
  skeleton, W0), AppShell conformance (platform-only), API-only downstream fixture (plugin contracts), and a
  project-problems test that the nbfx `ProjectProblemsImplementation` wins, the alert and customizer dialogs
  open with the expected problems, and the alert/resolver-list UI creates no Swing dialog. Dialogs opened by
  upstream resolvers (Ant Library Manager, variables customizer, file choosers, Maven platform customizer)
  remain Swing until those resolvers are replaced.
  Baseline to beat: **79 test files, 33 of 63 modules with tests, zero signature tests, headless FX tests
  present** — state the numbers in the W2 acceptance.
- **Fast loop**: `nbfx.overlay=symlink` and `ant module -Dmodule=<cluster>/<module>` (PR #8) build one module in
  seconds; `ant test -Dtest.modules=<cluster>/*` runs its tests. CI and the local loop use the same targets,
  so the loop and the gate cannot diverge.

### 10.4 Owners and capacity

A plan without owners is a wish. Every step in §11 must have an owner and a size (S/M/L) before M0 starts;
the table is maintained in the issue tracker, not here. Until then, no step is "in progress".

## 11. Workstreams (the work, sliced)

Each workstream is a branch family; each step lands alone. **Order inside a workstream matters; across
workstreams only the stated dependencies do.**

### 11.0 Definition of done (every step, no exceptions)

1. **Scope** — one concern, one sentence; no drive-by refactors.
2. **Acceptance test** — named, added in the same commit, failing before the change.
3. **Guards green** — the §9 checks, plus `check-figures` when a number changed and `check-baseline` when a
   build setting changed.
4. **Docs** — the module's `arch.xml`/`apichanges.xml` entry when a public type changed; this plan updated when
   a rule or a figure changed.
5. **Rollback** — revertable without touching later steps; no half-migrations.
6. **Green trunk** — after every integration commit the application still **starts, opens a file, edits it,
   builds and runs**. A refactor that leaves the app unusable for more than one step is rejected; the module
   being replaced stays in the distribution until its replacement passes the scenario.

### 11.0.1 Acceptance per workstream (the evidence that closes it)

| Workstream | Acceptance |
|---|---|
| W0 walking skeleton | the scenario (open → edit → build → run → output → commit) runs on a J2SE project; budget baseline recorded |
| W1 containment/naming/split | `check-java-source` green with the 4-module allowlist; no `Fx*` in an exported package; all consumers compile after `api-split`; layer contract test green; `check-jars` scans the cluster set |
| W1.5 full test gate | `ant test` discovers every nbfx test module; all unit tests and the headless scenario pass on `main`; the full suite is a required CI check; no hardcoded module list |
| W1.6 namespace alignment | all module code names are under `org.netbeans.modules.nbfx.*` / `org.netbeans.libs.nbfx.*`; directories use `nbfx.` (libraries `libs.nbfx.`); API packages are `org.netbeans.api.nbfx.*`; SPI packages are `org.netbeans.spi.nbfx.*`; implementation packages are `org.netbeans.modules.nbfx.*`; no live `com.gluonhq` module directory remains |
| W1.7 platform boundary/API clusters | APIs live in their domain clusters and `nbfx-api` is gone; `ant platform-build` produces a platform-only distribution whose default `ShellApplication` starts; `ant appshell-conformance` passes; `check-appshell`, `check-platform-delivery` and `check-api-isolation` are green; the IDE scenario is green |
| W2a contract completion | every M0 extension point (options, wizard, status, property/nodes) has its implementable type in the API/SPI packages; consumers import only API/SPI; an API-only downstream fixture compiles; `partial` modules are listed with a removal milestone |
| W2b governance | every **complete** governed module has `arch.xml` + `apichanges` + a `sigtest` target; incomplete modules are `devel` with a named exemption, not signed; spec-version guard green; exemption/friend register has owners and dates; the language conformance fixture passes |
| W3a AppShell chrome | shell chrome, declarative views/panes/layouts and theme are platform-only and covered by the conformance fixture; `ActionBars` is gone; no IDE class is needed for standard chrome |
| W3b AppShell services | each remaining service is platform-only, documents its thread, disposal and lease tests, and is covered by the AppShell conformance fixture; index-on-open has progress and cancellation; every product/IDE consumer imports API/SPI only, except a declared service friend |
| W4 IDE core | the M1 scenario (open/edit/search/find/commit/run-tests) on a J2SE project; every explorer panel renders through `api.nodes` |
| W5 Java tooling | the M2 scenario on an Ant **and** a Maven project, including a debugging session |
| W6 JavaFX usage | FXML edits and runs; `javafx.enabled` round-trips |
| W7 *(withdrawn)* | — |
| W8 upstream/retirement | the milestone rows of the retirement matrix are absent (guard); `chore/upstream-sync` green |
| W9 upstream proposals | ≥1 accepted upstream change; each proposal filed with its prototype, size and fallback |
| W10 packaging | a clean machine installs, launches and passes the guards on the packaged image |
| W11 language soundness | the conformance fixture passes in CI; a second language (Groovy) is highlighted, completed and run without editing core; the PHP/web spikes decided `api.servers`; `check-languages` green |
| W12 CI | the workflow is red on a planted violation and green on the current tree |

**W0 — Walking skeleton & baseline (first on the critical path).** `feature/walking-skeleton`: make the
existing shell modules run **open → edit → build → run → output → commit** on a real Ant/J2SE project and
record the budget baseline. The skeleton is the file set that **exists today** (`figures.md`: launcher, ui,
docking, windows, menus, statusbar, output, editor, project.context, api) — **no new module, no split**.

1. **Fixture** — `nbfx-tests/fixtures/ant-hello`, adapted from
   `netbeans/ide/jellytools.ide/test/qa-functional/data/SampleProject` (J2SE Ant, `build-impl.xml` checked
   in); `hello.Main` prints `NB_FX_SCENARIO_OK`. Acceptance: `ant clean build run` prints the marker.
2. **Scenario** — `ScenarioTest` in `nbfx-ide/.../nbfx.ide.workbench/test/unit` (moved from `nbfx.launcher`) with
   test-only deps on
   `nbfx.editor`, `nbfx.run`, `nbfx.vcs`, `nbfx.output`, `project.ui.ant`: copy the fixture to a temp git
   repo, wait for the async `OpenProjects` open, edit+save, build, run (assert the marker), commit, assert a
   clean status. Headless: `-Dglass.platform=Headless -Dprism.order=sw`.
3. **Budget** — a `--budget` mode on the launcher records startup→usable, open, first editor, build; median of
   5; commit the baseline; `check-budget` compares.
4. **CI** — Linux, JDK 25: `ant bootstrap`, `ant build`, `ant test` (all nbfx unit tests,
   including the headless scenario), `check-purity` and `Counts --check`; PR template and required check.
5. **Close** — green from a clean checkout, `docs/w0-scenario.md`, a §18 row.

Acceptance: the scenario runs headless from a clean checkout, the budget numbers are recorded, and everything
else keeps the trunk green (§11.0.6).

**W1 — Containment, naming and API split.** `refactor/guard-java-source` (denylist/allowlist guard over the
current 4-module allowlist; [Appendix B.5](appendix/B-java-source-isolation.md)) → `refactor/api-naming`
`refactor/guard-java-source` (**landed**, PR #20) → `refactor/api-naming` (**landed**, PRs #21, #30 and #32: drop
`Fx*` from the remaining exported types, move processors out of exported packages, introduce
`api.layer.LayerPaths`; no behaviour change) →
`refactor/java-plumbing` (`java.model` bridge; dedupe the three in-memory-file helpers and the position
conversions) → `refactor/editor-split` (`platform.editor` / `java.editor` / `ide.editor.{xml,css}`; delivers the
`api.editor` decoration SPI + `api.language`, acceptance: XML/CSS are modules and the core contains no
language branch) →
`refactor/usages-split` (`api.usages` + `ide.usages` + `java.usages`) → `refactor/cluster-layout` (**landed**,
PRs #31 and #42: `nbfx-libs` and the transitional `nbfx-api` created; `nbfx.actions`/`nbfx.context` merged into
`platform.actions`; `nbfx.docking`/`nbfx.windows` merged into `platform.workspace`; `check-jars`/`${nbfx.dist.dir}`
scans cover the cluster set. `nbfx-javafx` is deferred until its first module lands) →
`refactor/module-names` (**landed**, PRs #34–#38) → `refactor/api-split` (**landed**, PRs #22–#28: create the
`api.*` modules; move packages **wholesale** — same package names, consumers update only their `project.xml`;
topological order: elements/file/project → editor → actions/views → root last; no shims) →
`refactor/objects-not-statics` (services with lifecycles; typed contracts; no `Lookup.forPath` per call).
The writer/reader layer contract test landed in PR #33.

**W1.5 — Full test gate (before W2a).** `ci/all-unit-tests` (**landed**, PR #70): the root `ant test` target
discovers every module with tests and runs `test-unit`; CI uses that target, uploads the module JUnit XML on
failure, and runs the headless scenario as part of the same suite. Acceptance: the suite is green on `main`,
the full run is a required check, and neither the CI job nor the local loop uses a hand-maintained module
list.

**W1.6 — Namespace alignment (before W2a).** `refactor/namespace-modules` (**landed**, PR #71) renamed module
directories to `nbfx.<module>` and code names to `org.netbeans.modules.nbfx.<module>`, with the bundled
library at `org.netbeans.libs.nbfx.javafx` / `libs.nbfx.javafx`; packages stayed unchanged.
`refactor/namespace-packages` (**landed**, PR #72) moved API packages to `org.netbeans.api.nbfx.*`, SPI
packages to `org.netbeans.spi.nbfx.*`, implementation packages to `org.netbeans.modules.nbfx.*`, and
processors to non-exported `org.netbeans.modules.nbfx.api.*` packages; `check-namespace` enforces the
contract. Acceptance: full tests and scenario green, no live `com.gluonhq` module directory, module
graph/build/patches guards green.

**W1.7 — Platform boundary and API clusters (before W2a).** This is the platform-deliverable path; the IDE
scenario remains the gate.

1. `refactor/api-neutral` (**landed**) — remove the API leaks the single API cluster hides: `api.editor` loses
   `api.project` and `api.elements`; `api.usages` loses the Java vocabulary; `api.actions` uses the neutral
   `SourceLocation` from `api.language` and loses the IDE command ids; `api.views` loses project-specific
   navigator callbacks. Split `api.elements`: neutral kinds/location/modifiers to `api.language`, Java
   conversions to `java.elements`, and the FX icon mapping to `api.editor`. Then `refactor/api-editor-neutral`
   removes the project path from `api.editor` and moves the project-aware editor scope to `ide.workbench`.
2. `refactor/api-clusters` (**landed**) — moved each API module into its domain cluster (§6.6); dissolved
   `nbfx-api`; updated `nbfx.properties`, the cluster fragment, `build.xml`, the guards, the
   launcher-cluster patch and the docs; added `check-layers` and `check-api-isolation`.
3. `refactor/appshell-boundary` (**landed**) — extracted `ide.workbench` from `nbfx.ui`; moved `nbfx.ui` → `platform.shell`,
   `nbfx.launcher` → `platform.launcher`, `nbfx.propertysheet` → `platform.nodes`, and the generic
   action-context services → `platform.shell`; added `check-appshell`; dissolved `nbfx.ui`.
4. `feature/appshell-conformance` — a platform-only fixture that starts the AppShell and exercises options,
   action context, wizards, property sheet, nodes, dialogs, progress, output, terminal, quick search, theme,
   credentials, favorites and templates. The fixture starts here and grows with each W3b service.

Acceptance: the IDE scenario is green; the platform-only distribution starts with no IDE modules; the
AppShell conformance fixture passes; `check-appshell` and `check-api-isolation` are green.

**W2a — Contract completion (before W2b, M0).** `refactor/api-contracts`: move the implementable type of each
M0 extension point into its API/SPI module and migrate consumers:

- `refactor/options-contract` (**landed, #162**) — `OptionsPanelHost` → `api.options.OptionsPanelController`, add
  `OptionsDisplayer`, validate the registration type in the processor, migrate the options window and built-in
  panels; `platform.options` is T0.
- `refactor/wizard-contract` — `Wizard`/`WizardPanelHost` → `api.wizard.Wizard`/`WizardPanel`; keep
  `WizardEntry`/`WizardRegistry` as the discovery contract; update the plan names (`WizardIterator`/
  `WizardDescriptor` were never implemented).
- `refactor/status-contract` — merge `StatusElementHolder` into `api.status.StatusElement` (the current
  `platform.status.StatusElement` record becomes the internal registration spec); migrate the caret,
  line-separator and project elements; validate the registration type.
- `refactor/nodes-property-contract` — create `api.nodes` (`NodeModel`, `ChildrenModel`, `ChildFactory`,
  `PropertyModel`, `PropertySetModel`, `PropertiesProvider`, `PropertyEditorRef`, `ExplorerContext`), move
  `PropertyEditor`/`PropertyEnv` to `api.property`, and put the property sheet on the model as its first
  consumer.
- `refactor/shell-window-contract` — rename the shell API/SPI packages to `org.netbeans.api.nbfx.shell`/
  `org.netbeans.spi.nbfx.shell`; `WindowPaneProvider` returns a `WindowPaneHost`; `NbfxTabPane` becomes
  internal; `WindowManager`/`ViewManager`/`ShellWindow` replace the static tab operations.
- `refactor/workspace-public-surface` — `ViewSpec`/`ViewRegistry` → `api.views`; `WindowSystem`/`WindowState`/
  `WindowSink` → the shell API; docking internals become friend packages for `platform.shell`.
- `refactor/editor-spi` — move the language decoration/lex SPI to `api.editor`/`org.netbeans.spi.nbfx.editor`;
  migrate `java.editor`, `ide.editor.xml` and `ide.editor.css`; `CodeEditor`/`SearchSupport`/breadcrumb widgets
  stay internal. The consumer migration may finish in M1.
- `refactor/api-file-dissolve` — move `FileSelectionContext`/`FileTypes`/`FileIconProvider` to
  `api.views`/`api.editor`/`ide.file`.

Acceptance: the API-only downstream fixture implements options, wizard, status and property using API/SPI only;
no M0 consumer imports a T0 package except through a named friend; the property sheet runs on `api.nodes`.

**W2b — API governance (after W2a).** `feature/api-governance`: `arch.xml`/`apichanges`/signature tests for
every **complete** T1/T2-FX/M module, the version baseline (1.0 + bump rule) and the spec-version guard; the
audit/guard implementation (`refactor/guardrails`); the exemption/friend register with owners and dates.
Incomplete modules stay `devel` and are not signed. `feature/language-conformance`: the **fake-language
fixture** (`LanguageId("text/x-nbfx-conformance")` with decoration, completion, structure, goto and usages) —
the CI proof of the core freeze ([Appendix I.4](appendix/I-language-soundness.md)).

**W3a — AppShell chrome (landed).** The `feature/shell` branches are on `main`:

1. `refactor/shell-chrome-contract` — `ShellChrome`, `ShellChromeProvider`, narrow `ShellContext`, default
   provider; `ShellApplication` installs the chrome and reuses `AppState` toolbar persistence.
2. `refactor/menu-toolbar-builder` — layer-only `ShellMenuBar`/`ShellToolBars`, ordered `DynamicMenu.Factory`,
   `Command.getIcon()` as the icon source; no File/Edit/View/Window switch.
3. `refactor/shell-status-branding` — compose `platform.status.StatusBar` from registrations;
   `ShellBranding`, default `ApplicationMenu`.
4. `refactor/default-window-environment` — one `DefaultWindowEnvironment` plus ordered contributions; generic
   detached bars.
5. `refactor/ide-workbench-contributions` — `JavaFXLaunchApp` keeps only IDE contributions; `ActionBars` is
   gone; `ProjectState`/`ProjectBranding` own IDE state.
6. `feature/appshell-downstream-fixture` — the platform-only fixture; M0 grows it into the **API-only**
   downstream proof.

**W3b — AppShell services (M1).** Each service is a platform service, not an IDE service; the IDE contributes
through `ide.workbench`, and the AppShell conformance fixture grows with each branch. Order:
`feature/dialogs` (contract + implementation), `feature/output-api` (extract `api.output`), `feature/settings-ui`
(+ `api.settings`), `feature/search-api` (+ `platform.quicksearch`), `feature/execution` (process/PTY first),
`feature/templates`, `feature/credentials`, `feature/favorites`, `feature/indexing` (reuse
`parsing.api`/`parsing.indexing`; index-on-open with progress, cancellation and a memory budget —
[Appendix D.5](appendix/D-bridge-and-retirement-matrix.md#d5-the-indexing-workstream-currently-missing)).
`feature/terminal` stays M2 until the VT parser/library decision; process execution does not depend on it.

`feature/shell` acceptance: a platform-only `ShellApplication` shows menus, tool bars and a status bar from
registrations with no IDE class; `ShellChrome` exposes no concrete `MenuBar`/`ToolBarContainer`/`StatusBar`
in its product contract; the IDE menu/toolbar/status IDs and behaviour are unchanged; downstream adds chrome
through registrations, not by subclassing. The M0 shell-window contract removes the implementation package
from `public-packages` and lets `WindowPaneProvider` return a `WindowPaneHost`.

The detailed API/SPI contracts, layer-registration rules, use cases, UX interaction rules, test matrix and
per-phase acceptance criteria are in [`appshell-chrome.md`](appshell-chrome.md).

**W4 — IDE core.** `feature/project-model`, `feature/project-ui`,
`refactor/nodes-model` → `refactor/nodes-usages` → `refactor/nodes-navigator` →
`refactor/nodes-properties` → `refactor/nodes-misc` (the node/view consolidation;
[Appendix F.3](appendix/F-node-and-view-migration.md)), `feature/editor`,
`feature/navigation-search`, `feature/refactoring`, `feature/hints`, `feature/codegen`,
`feature/javadoc`, `feature/structure`, `feature/diff`, `feature/vcs`, `feature/localhistory`,
`feature/tests`, `feature/libraries`, `feature/tasklist-bookmarks`, `feature/plugins-ui`,
`feature/modulegraph`.

**W5 — Java tooling.** `feature/java-platforms`, `feature/projecttypes` (java/maven/gradle/ant),
`feature/java-editor`, `feature/debugger`, `feature/profiler`, `feature/java-tests-coverage`.

**W6 — JavaFX usage.** `feature/javafx-project`, `feature/fxml`, `feature/scenebuilder`.

**W7 — (withdrawn).** The front-end factory migration is void: §5.2 and
[Appendix E.7](appendix/E-contracts.md) publish **concrete FX factories** (one front end), so
`spike/front-end-component-factory` and `spi-factory-migration` are removed. Portability lives in the
toolkit-free `PanelModel`/`FieldModel`; revisit generics only if a second component toolkit appears.

**W8 — Upstream and retirement.** `chore/upstream-sync` (monthly pin/patch rebase, patch register, patch
budget, CI), `refactor/swing-retirement` (execute the retirement matrix of
[Appendix D.4](appendix/D-bridge-and-retirement-matrix.md); `check-packaging` asserts the absence),
`feature/plugin-api` (contract docs, sample plugin, trust), `feature/plugin-quickstart` (the skeleton plugin,
its NBM build and a view + a command — [Appendix E.6.1](appendix/E-contracts.md)).

**W12 — CI.** `feature/ci`: one workflow running, in order, the IDE cluster build, the **platform-only
build**, **all nbfx unit tests** (`ant test`, including the headless W0 scenario), the AppShell conformance
fixture, `check-purity` and `check-figures`, and publishing the freeze baseline. The all-tests gate landed
before W2 so a namespace or module rename cannot hide a broken module test. Acceptance: red on a planted
violation or failing test, green on the current tree — without it "green trunk" is an assertion (§9).

**W10 — Packaging and distribution.** `feature/packaging`: the **IDE image** is the primary artifact, built
from `nbbuild/netbeans` (M0), then OS bundles/installers (M4) carrying the `libs.*` natives and the licences
([Appendix G](appendix/G-licences-and-notices.md)); branding (icons, splash, about), first-run and the update
centers. The **platform distribution** is the secondary artifact: the platform cluster set as source and
prebuilt binary modules, with API docs and dependency metadata, and without IDE modules. **Decide jpackage
vs NBI here** (Decision 19). Acceptance: a clean machine installs, launches and passes
`check-licences`/`check-baseline`/`check-figures` on the IDE image; the platform artifact contains no IDE
modules and its conformance fixture runs against it.

**W11 — Language soundness.** Per [Appendix I](appendix/I-language-soundness.md): `spike/lang-groovy` (JVM,
no build, no server — the headline exercise of `api.language`), `spike/lang-php` (no build, interpreter +
dev server — decides `api.servers`), `spike/web-javaee` (a thin project-type + deploy slice). Time-boxed, after
`platform.editor` + `api.*` and before the M5 freeze; the deliverable is a **hole list + the smallest core API
fix**, and the core freeze (`check-languages`) is the acceptance. Each spike has a branch, a time box, an owner
and a **kill criterion**; spike code is deleted unless it survives as a real (optional) provider module. No
feature growth — the cut list kills a spike that becomes a language IDE.


**W9 — Upstream proposals.** Per [Appendix H](appendix/H-upstream-proposals.md), each with a tracked upstream
issue, a prototype in nbfx and a recorded fallback: `chore/upstream-module-tools` (the portable `nbbuild`
checks, H.6 — portable ant tasks under `nbfx-tools/`), `spike/progress-neutral-spi` (H.2),
`spike/java-source-headless` (H.1), `spike/actions-neutral-core` (H.3), `spike/nodes-icon-source` (H.4),
`spike/loaders-node-free` (H.4.1),
`spike/runlevel-api` (H.7), `spike/maven-embedder-facade` (H.7), `spike/editor-model-spi` (H.5, last).
Order and the per-proposal credibility test are in [H.8](appendix/H-upstream-proposals.md).

## 12. Mirror and evidence

- **No hand-typed numbers.** Every count the plan states is generated by
  [`tools/Counts.java`](tools/Counts.java) into [`figures.md`](figures.md); `check-figures` fails when the file is
  stale. If a number is worth writing, it is worth deriving.
- The **archived** documents under `docs/plan/archive/` hold the research that justifies each workstream
  (NetBeans class/file references, counts, formats, licences). Consult them for evidence; do not treat
  them as the plan.
- `docs/architecture-rules.md` is a one-screen **index** of every rule and its normative source in this plan;
  it deliberately restates no rule detail. `docs/repo-and-build.md` documents the harness; `docs/MVP.md` is a
  historical draft.

## 13. Decisions (locked unless revised here)

1. **Deliverables/clusters**: the **IDE** is primary (`nbfx-libs` + `nbfx-platform` + `nbfx-ide` +
   `nbfx-java`, plus `nbfx-javafx` when it lands); the **platform** is secondary (`nbfx-libs` +
   `nbfx-platform`). Downward dependencies only; no `nbfx-api` cluster in the target.
2. **Module/package naming** per §3; packages are namespaced per §3, not equal to module code names; no
   split packages.
3. **API tiers** per §4; the published plugin API is JavaFX (T2-FX); models/backends are toolkit-free (M);
   the tier is module metadata, not a cluster.
4. **Registration annotations live in the owning `api.*` module** — no separate annotation modules.
5. **The `nbfx.api` split (landed) was a module-ownership migration**: same package names, consumer
   dependency updates only, topological order, **no shims**.
6. **Typed factories** for component-producing SPIs; model-first panels where possible; no `Object`.
7. **Nodes/property model** (`api.nodes`, M) drives every tree/table/sheet view; one property/editor model
   for sheet, outline columns and customizers.
8. **Execution/output/terminal** as platform services on `api.execution`/`api.output`/`api.terminal`; run
   defaults to the terminal; Maven builds run as processes.
9. **Tests and iterations**: every step has tests and guardrails; the module graph and audit are re-run per
   milestone; budgets are baselined and checked.
10. **Upstream first**: SPI generalisations are prototyped in nbfx and proposed to `apache/netbeans`; friend
    patches require an upstream issue (delete `0002`, retire `0003` with the Maven process, keep `0001` with
    a `RunLevel` proposal).
11. **Retirement is the deliverable**: a milestone is done when its Swing modules leave the distribution,
    asserted by the guard, per the matrix of
    [Appendix D.4](appendix/D-bridge-and-retirement-matrix.md). For every upstream UI API, the mechanism is
    *reimplement behind the API* when the API is provider-based (and the bridge carries the documented Swing
    exemption), *replace with an nbfx API* otherwise ([Appendix A.3](appendix/A-toolkit-policy.md)).
12. **Layer-path constants have one owner**, the module `api.layer` (T1) with the class `LayerPaths`; the
    writer/reader agreement is a contract test (§3.3). No "private constant per reader".
13. **The Java semantic layer is the only importer of `java.source`/`libs.javacapi`**, behind the `java.model`
    bridge; `javax.swing.text.Document` is banned everywhere
    ([Appendix B](appendix/B-java-source-isolation.md)).
14. **Upstream reuse edges are not layer edges**: an nbfx module depending on a NetBeans module is recorded
    with a justification and a retirement plan; the layer rule constrains nbfx → nbfx edges only.
15. **Project type ids and the `javafx.enabled` property match the NetBeans convention**
    (`org.netbeans.modules.java.j2seproject`); module renames never change them (§8).
16. **Compiler**: JDK `javac` by default, nb-javac only where the project's source level requires it; the
    choice is owned by `java.model` and recorded in the module metadata
    ([Appendix D.6](appendix/D-bridge-and-retirement-matrix.md)).
17. **Upstream agenda** per [Appendix H](appendix/H-upstream-proposals.md): additive-only, new module +
    compatibility wrapper, `ModuleAutoDeps` carries the move, the surface is frozen by `sigtest`, a prototype in
    nbfx is the first customer, and a fallback is recorded. **At least one accepted upstream change per
    milestone.**
18. **Baseline**: JDK 25, JavaFX 27, `javac.release=25`, FX on the classpath (unnamed module) with the
    `--add-exports` list centralised if that ever changes, agent-free profiling, Headless glass for tests (§8.1).
19. **Packaging**: build the IDE app image from `nbbuild/netbeans` at M0, and produce OS bundles/installers
    with **jpackage** at M4 unless a spike shows it cannot meet the requirement (then NBI). Also publish the
    **platform distribution** (platform clusters as source and binary modules, without IDE modules). Plugins
    are built as **NBMs** with the NetBeans `nbm` Ant harness (Appendix E.6.1).
20. **Language registry**: `api.language` (M) is the one registry; providers are scoped by `LanguageId`;
    syntax decoration is a provider, not a core branch; the neutral `ElementRef` (with a language-defined
    `handle()`) lives in `api.language` and the Java vocabulary moves to `java.elements`; an unregistered MIME
    resolves to **plain text** ([Appendix I](appendix/I-language-soundness.md)). The core must not branch on a
    language (`check-languages`). `api.language` ships `arch.xml category="devel"` and is **not frozen** until
    the conformance fixture and one real second language (Groovy) pass.
21. **Tier discipline**: an `api.*` module with a planned consumer is `M`/T2-FX from the start and published
    `devel` when its first consumer lands; no "proposed" tier, no `libs.*` before its step, no capability
    before a spike decides it (§17).
22. **QA placement**: IDE end-to-end scenarios live in the IDE cluster's test module (`ide.workbench` or a
    test-only IDE module); platform modules keep service tests, and the AppShell conformance fixture lives
    with the platform; `<test-dependency>` may cross layers, never run dependencies, never shipped; a
    test-only `nbfx-qa` cluster when scenarios multiply (§10.3).
23. **Two deliverables, one AppShell**: the IDE is the primary deliverable; the platform (the AppShell plus
    platform services) is the secondary deliverable. The AppShell is the platform's whole application
    framework, not just `platform.shell`. Downstream projects assemble applications however they choose
    (source clusters or prebuilt binary modules); nbfx does not define a product or assembly model.
24. **APIs live in their domain cluster**: the `nbfx-api` cluster is dissolved; an API is a module tier, not a
    cluster; API modules may depend only on API modules in the same or a lower cluster (`check-api-isolation`).
25. **The AppShell boundary is structural**: no platform module may depend on an IDE/Java/JavaFX module; the
    services do not depend on the shell; IDE behaviour lives in `ide.workbench`. `check-appshell` asserts it.
26. **M0 is the foundation and the plugin contracts; M1 is services plus IDE core.** The milestone dates are
    unchanged: M0 stays at 2026-12 and M1 at 2027-03. M0 includes the shell/window/workspace public surface and
    the complete plugin contracts for options, wizard, status and property/nodes (because the M0 property sheet
    needs `api.nodes`). Dialogs, output API, settings, search, execution, templates, credentials, favorites,
    indexing and the finished editor extraction are M1, as is IDE core; terminal is M2. A module whose
    contract is incomplete is `partial`/`devel`, is not signed, and does not close M0. If the scope does not
    fit, the cut list or an explicit plan change applies; do not pre-emptively move the dates.
27. **T0 publishes nothing; friend packages are named and dated.** A T0 module may export an implementation
    package only through `<friend-packages>` to a named nbfx module, with an owner and a removal milestone.
    Shared packages are promoted to T1/T2-FX/M deliberately. A module is *landed* only when the type a
    consumer or plugin implements is in its exported API/SPI packages, a downstream fixture can implement it
    using API/SPI imports alone, and no M0 consumer imports a T0 package except through a named friend.
    `platform.editor` and `platform.workspace` are **extracted**, not promoted: their product-facing types
    move to API/SPI, and their internals remain T0 friend packages until the consumer migration completes.
28. **Project problems are replaced behind `ProjectProblemsImplementation`, not bridged.** The project problem
    alert and resolver UI are JavaFX in `ide.project.ui`; the upstream `ProjectProblems` model and
    `ProjectProblemsProvider` SPI are reused. The Swing `projectui`/`BrokenReferencesImpl` panels are not
    embedded and are retired with `projectui`. The project-root “Resolve Project Problems...” entry is a
    `FileContextMenuContributor` in the same module, next to the resolver it opens, not an
    `ide.project.actions` command; `ProjectProblemsNotifier` owns the open-project trigger when
    `projectui` is retired. Resolver-owned dialogs (Ant Library Manager, the variables customizer,
    file choosers, Maven's platform customizer) remain upstream Swing and are replaced one by one.
    This is the model for upstream UI APIs with a provider SPI: replace the implementation, keep the
    API.

## 14. Open questions

1. **Plugin API stability**: publish T2-FX at M0/M1 or at M5 (after the ecosystem hardening)? Proposed:
   publish the **complete** M0 contracts as *devel* (`arch.xml category="devel"`), keep `partial` modules
   unpublished, and freeze at M5.
2. **Multi-window/detached views**: M4 or earlier? (Proposed: M4.)
3. **Coverage licence**: JaCoCo is EPL-2.0 and already shipped by NetBeans; confirm the policy for a
   *published* nbfx feature before bundling.
4. **Async jobs model**: fold into `api.progress` or a separate `api.jobs`? (Proposed: `api.progress`.)
5. **FX editor core**: is `jfx.incubator.scene.control.richtext` (the current code area, in `libs.javafx`)
   acceptable long-term, given `platform.editor` also reaches into
   `com.sun.jfx.incubator…RichTextAreaSkinHelper`/`VFlow`/`CaretInfo` (non-public FX internals)? If not, the
   fallback is a `TextFlow`/`Skin` build; decide before M1 (Appendix B.7).
6. **Platform distribution form**: source cluster zip, NBM repository, or both? Proposed: both, generated
   from the same platform cluster set.
7. **Upstream dependency of the platform artifact**: bundle the required upstream NetBeans clusters, or
   reference a pinned Apache NetBeans platform release? Proposed: reference the pinned release and bundle
   only the nbfx clusters.
8. **Projects**: IDE-tier or platform-tier? Proposed: IDE-tier; the AppShell must start without projects,
   and downstream applications that need projects add the IDE cluster.
9. **Plugin API/manager placement**: platform API and manager with the UI in `ide.plugins`, or all in the
   IDE? Proposed: platform API/manager, IDE UI.
10. **AppShell conformance scope** — *resolved by Decision 26*: M0 covers the shell chrome, action context,
    options, wizards, status, property/nodes and the API-only downstream fixture; dialogs, output, settings,
    search, execution, templates, credentials, favorites and indexing join in M1; terminal joins in M2.

## 15. Appendices

| Appendix | Content |
|---|---|
| [A — Toolkit policy](appendix/A-toolkit-policy.md) | allowed/forbidden types per tier, the `java.awt`/`javax.swing` decision, the bridge-vs-replace table, the nb-javac rule, the exemption register, the guard mechanism |
| [B — Java source isolation](appendix/B-java-source-isolation.md) | the `java.source` audit, the existing seams, the `java.model` bridge, the module split, the policy and the migration steps |
| [C — Naming and rename table](appendix/C-naming-and-rename-table.md) | the rule per location, all 28 public `Fx*` types and their targets, `api.layer.LayerPaths`, the `refactor/api-naming` step |
| D — [Bridge and retirement matrix](appendix/D-bridge-and-retirement-matrix.md) | every upstream module (Swing API bridges, the 50 editor/Java modules (`figures.md`), the indexing dependency, the compiler choice) with mechanism, owner, milestone and exemptions, plus the retirement matrix per milestone |
| E — [Contracts](appendix/E-contracts.md) | threading per SPI, disposal/ownership + the leak test, accessibility, error/logging, the precise `Lookup` rule, the plugin allowlist, and the removal of the generic-`C` factories |
| F — [Node and view migration](appendix/F-node-and-view-migration.md) | the 25 tree/table/list view files, today's item models, the target `api.nodes` model, and the migration order |
| G — [Licences and notices](appendix/G-licences-and-notices.md) | JavaFX 27 (GPLv2+CE), async-profiler, JLine, jgit, JaCoCo; the NOTICE rules and the `check-licences` guard |
| H — [Upstream proposals](appendix/H-upstream-proposals.md) | the Swing-free API agenda for upstream NetBeans (`java.source`, `api.progress`, `openide.awt`, `openide.nodes`, the editor SPI, the `nbbuild` checks, the friend APIs) and where nbfx prototypes each one first |
| I — [Language soundness](appendix/I-language-soundness.md) | the second-implementation test: the four holes (core `switch`, unscoped providers, the Java-shaped `api.elements`, missing servers/build-less capabilities), the `api.language` design and core freeze, the Groovy/PHP/web spikes, and the measured reuse ratio across the other clusters |

## 16. Risks

Every entry has a **trigger**, so it is a decision rule, not a worry. Owners are filled before M0 (§10.4).

| Risk | Likelihood | Impact | Mitigation | Trigger |
|---|---|---|---|---|
| **R1** upstream refuses the `java.source` split (H.1) | medium | high | keep `java.model`, write the diff-apply path; time-boxed | no upstream decision by M1 |
| **R2** FX internals (`com.sun.jfx.*`) change or vanish in a JavaFX upgrade | medium | high | one adapter (`RichTextView`), pin FX 27, an upgrade spike per FX release | an FX 28 bump |
| **R3** the editor replacement (≈600 upstream files) overruns M1 | high | high | M1 editor scope = edit/highlight/complete/format/find; defer folding, macros, code templates | M1 mid-point slips one milestone |
| **R4** indexing memory/time on large projects | medium | medium | lazy indexing, budget in `check-budget`, degrade (no blocking) | first 10k-file project |
| **R5** pinned NetBeans submodule + friend patches drift | medium | medium | monthly `chore/upstream-sync`, patch budget, rebase CI | a patch fails to apply |
| **R6** JavaFX GPLv2+CE obligations conflict with the distribution | low | high | `check-licences`, NOTICE, exclude `javafx.web`, legal review before M0 ships | a clean-machine installer is produced |
| **R7** no third-party plugin authors appear | medium | high | publish the API as *devel* early, quickstart + sample, compatibility report | M5 with zero external plugins |
| **R8** Maven/Gradle project types need the embedder/friend patches | high | medium | process-based builds; Maven/Gradle are on the cut list (behind J2SE+Ant) | `0003` blocks an M2 step |
| **R9** FX threading/leak defects | high | medium | §E.1–E.3 contracts, leak test, green trunk | a leak test regression |
| **R10** startup/performance regression vs Swing | medium | high | budget baseline at M0, `check-budget` blocks | baseline recorded |
| **R11** settings/keymap data lost on module renames | medium | medium | round-trip test in `refactor/module-names`; code-names freeze at 1.0 | first code-name rename lands |
| **R12** JPDA/debugger integration (attach, sources, hot swap) underestimated | medium | medium | `java.debug` typed session services; M2 gate | debugger step slips |
| **R13** scope creep from the module catalogue | high | medium | Rule of Creation (§6.1), cut list (§10.2), owners before M0 | a new `api.*` module without a consumer #2 |
| **R14** a11y postponed and unfixable later | medium | medium | a11y is an M1 contract of `api.views` (E.3), checked in the shell | M1 exit |
| **R15** the API cluster move breaks the build or hides a new upward edge | medium | medium | cleanups first (`refactor/api-neutral`), then the move; `check-layers`/`check-api-isolation`; green-trunk rule | an API module gains an implementation or IDE dependency |
| **R16** AppShell scope grows beyond M0 | high | medium | Decision 26 fixes the M0/M1 line; the §6.6 inventory is the scope; the conformance fixture grows per service; a service branch that enters M0 is a plan change | a W3b service lands before M0 closes |
| **R17** generic chrome stays in `ide.workbench`, so downstream must build an own AppShell | high | high | `feature/shell` extracted `ShellChrome`/menu/toolbar/status/branding/window environment into `platform.shell`; the API-only platform fixture proves no subclass; `check-appshell` stays green | a downstream product depends on `ide.workbench` or copies `ActionBars` |
| **R18** published API modules are stubs while consumers use T0 packages | high | high | W2a contract completion (options, wizard, status, property/nodes) before W2b; the API-only downstream fixture is the gate; `partial` modules are not signed | a module is marked landed without an API-only consumer |
| **R19** friend packages become permanent hidden API | medium | high | every friend has an owner and removal milestone; `check-api` fails on an expired or undeclared friend; T0 publishes no packages | a friend entry survives its milestone |
| **R20** editor/workspace extraction overruns M0 | high | high | extract the SPI in M0, finish the consumer migration in M1; friend packages are the transition, not the destination; the cut list protects the M0 contracts | the M0 API-only fixture cannot compile without a T0 import |

## 17. Plan budget and freeze

The plan is a means, not a deliverable. It now has implementation behind it: W0, W1, W1.5, W1.6, W1.7 and
the AppShell chrome/declarative composition are on `main`. This re-scope records the API-contract gap and
moves service breadth to M1; it is the last review round before M0.

- **Budget ~1,200 lines.** This round replaces speculation (stub “landed” rows, the old 42-test baseline)
  with the API-reality summary and the measured baseline.
- **No new appendix without merging or removing one.**
- **No further review round** until the API-only downstream fixture passes or the platform distribution is
  produced; W0 and `feature/ci` have already met the earlier gate.
- **Delete speculation on sight**: a tier marked "proposed", a `libs.*` row for a library that is not bundled,
  an `api.*` module without a consumer #2, a capability (`servers`) before a spike decides it.
- The plan's purpose is to make the next step obvious; when it stops doing that, it is a cost.

## 18. Landed on main

The plan refers to `main`; this is what landed there after the plan was written. It is the only status list;
add a row when a PR lands and update the affected chapter in the same change.

| PR | What | Plan impact |
|---|---|---|
| #2–#50 | W0/W1 foundation: FX progress, navigator/structure, fast loop, guardrails, api-naming, api-split, cluster layout, module renames, layer contract test, signatures, RAT | W0/W1 closed; details in git history |
| #51–#63 | W1 completion: landed-status docs, launcher dependency cleanup, project registry, `java.model`/`SourceAnalysis`, editor split, usages split, object lifecycle cleanup, `check-languages`, annotation dissolution | W1 closed; `platform.editor`, `java.model`, `api.*` modules; `check-languages` |
| #66–#96 | W1.5 full test gate; W1.6 namespace moves; W1.7 platform boundary, platform-only delivery, AppShell foundation and conformance | W1.5/W1.6/W1.7 closed |
| #97–#110 | macOS module gating, `JdkCheck`, JDK dialog styling, bundle guard, installer workflow added/removed, plan chrome revision | build hygiene and release workflow; no architecture change |
| #112–#120 | AppShell chrome contract, detached chrome/actions/project state, declarative view areas/placement/layout, permanent panes, upstream lifecycle contributions, theme/OS light-dark | W3a chrome and declarative composition landed; `ActionBars` removed; `platform.theme` landed |
| #122–#126 | macOS appearance flag, theme CSS, `ant guards` | theme follow-up; CI guard aggregate |
| #140–#159 | shell/view/status/context fixes: dynamic view providers, tab view resolution, active component, open-views menu, shutdown coordinator, progress fill, primary-pane hiding, start-up views, duplicate positions, tests, KEYS, headers | W3a stabilised; no new API contract |
| #162 | options contract: `OptionsPanelController`/`OptionsDisplayer` in `api.options`; `platform.options` T0 | first W2a contract slice landed; §6.1/§6.1.1 options row |
| #163 | project problems UI: JavaFX `ProjectProblemsImplementation` in `ide.project.ui`, superseding `projectui`/`BrokenReferencesImpl`; project-root resolver entry, show-again preference and open-project notifier | §6.3 `ide.project.ui`, §8 preference, §10.3 tests, Appendix D.2/D.4, Decision 28 |

**Current status:** W0, W1, W1.5, W1.6, W1.7 and the W3a AppShell chrome/declarative composition are on
`main`. The shell builds menus, tool bars, status and detached windows from registrations, composes the
declarative panes/areas/layout, owns branding, the application menu and the window environment, and the theme
supports light/dark/OS mode. `ActionBars` is gone.

The **API split is complete at module level, not at contract level**. `api.wizard`, `api.status`,
`api.property`, `api.nodes`, `api.dialogs`, `api.search` and `api.project` are `partial` until W2a moves the
implementable SPI out of `platform.*`; the options contract landed with #162. `platform.editor` and
`platform.workspace` still export implementation packages. M0 is **2026-12** (dates unchanged) and now closes
the M0 plugin contracts, the shell/window/workspace public surface, the API-only downstream fixture and the W2b
governance for the complete modules. M1 (2027-03) carries AppShell services and IDE core. The next W2a slice is
wizard/status/property/nodes, not a new service.
The module and source counts are in [`figures.md`](figures.md), not in this table.
