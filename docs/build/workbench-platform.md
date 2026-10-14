# Workbench appearance, accessibility, and feedback checkpoint

Issue #100 delivers the application-owned Workbench platform layer on standard JavaFX controls and Modena
(delivered on JavaFX 25; the baseline is now JavaFX 27, see ADR-0007). `WorkbenchAppearance` owns the persisted System/Light/Dark choice, effective theme, reduced-motion state, and
semantic tokens without depending on JavaFX. `JavaFxWorkbenchAppearance` is the production adapter: it observes
an `AppearancePreferencesSource` and applies one coherent frame to the loaded Workbench root. High Contrast always
overrides the manual choice and ending High Contrast restores that unchanged choice.

The appearance-preferences source is the one BS2BG-owned seam for operating-system appearance
([#124](https://github.com/evildarkarchon/BS2BG/issues/124)). It has two operations: read the current
`WorkbenchAppearance.PlatformPreferences`, and subscribe to changes. The Workbench receives it through the
`WorkbenchEnvironment` slot (`withAppearancePreferences`), which defaults to `JavaFxAppearancePreferencesSource`.
That production source reads the public
[`Platform.Preferences`](https://openjfx.io/javadoc/27/javafx.graphics/javafx/application/Platform.Preferences.html)
interface, including the documented Windows `Windows.SPI.HighContrast` and system-color keys, through weak
listeners. `Platform.Preferences` itself is never injected, because it is a large interface that grows between
releases. Tests use `FakeAppearancePreferencesSource` through `WorkbenchTestEnvironment` (light, not High Contrast),
so test results never depend on the developer's OS theme or High Contrast setting.

Owned satellite surfaces live in their own scenes, so they register their root with the shell's appearance through
`WorkbenchSatellites.register(root, dismiss)`, which returns a handle. While the handle is open, the shell re-themes
that root with every appearance frame and dismisses the satellite when the Workbench window is hidden; the satellite
closes the handle as it hides. The shared NPC portrait viewer (`NpcPortraitViewer`), the Morphs Area's Fill Empty
flyout and the NPC Database Area's column-filter popup use it. The viewer looks portraits up in the `WorkbenchEnvironment` slot `withPortraitDirectory`,
which defaults to `images` under the working directory.

The Workbench uses one application-owned stylesheet, `workbench.css`. Color is never the only status cue: InfoBar,
Activity, status, selection, focus, and dialog states retain text, semantic vectors, and boundaries. Focus styling
is at least two logical pixels. Reduced motion is live and all platform-slice drawer, overlay, selection, and reflow
changes are already immediate; the centralized job slice (#101) owns later progress animation behavior.

## Shell and Area modules

The Workbench is a shell plus one module per Area
([ADR-0009](../adr/0009-workbench-areas-as-per-area-fxml-modules.md)). `WorkbenchController` is the shell, and
`workbench.fxml` holds only its chrome and the empty landmark containers. The shell owns:

- the chrome, File menu, rail, key map and navigation
- the landmark containers and the responsive layout (narrow overlays and their clip)
- the Project lifecycle, including the dirty-Settings close gate
- the Project-frame and job-frame fan-out, as one `WorkbenchContext` rendered into every module
- diagnostics arbitration between the Templates and Morphs projections
- feedback rendering (InfoBar, Activity, status) and the appearance

Each Area (`TemplatesArea`, `MorphsArea`, `NpcDatabaseArea`, `SettingsArea`) lives beside its feature in its
subpackage, with its own FXML. Its root is an `AreaPanes` holder whose three panes the shell mounts into
`primaryPane`, `editorPane` and `inspectorPane`. The Area owns its controls, renders, feedback wording, popups,
catalog sizing and exact focus restores. The Output drawer (`workbench.output.OutputDrawer`) is a module but not an
Area. Its root is an `OutputDrawerParts` holder: the shell puts the Generate button into the Area header and the
drawer into the content stack.

Modules talk to the shell only through `ShellPort`, and never to each other. Shared work sits in four modules:
`DispatchPipeline` (job gate and dialog round-trip), `WorkbenchFocusPolicy`, `NpcPortraitViewer` and `UiaListHelper`.

Tests follow the same split:

- Each module has its own suite (`<Area>AreaTest`, `OutputDrawerTest`) on the `ModuleTest` / `AreaTest` fixture.
  The fixture loads only that module's FXML.
- `WorkbenchControllerTest` keeps the shell and the cross-Area journeys, through the whole loaded Workbench, and
  reaches Area controls through `LoadedWorkbench`.

## Selected semantic icons

The predetermined bundled-vector fallback is the selected and only shipping implementation. The conditional
Ikonli candidate could not satisfy the complete adoption gate in this checkpoint because its packaged font loading,
UIA, DPI/theme, licensing, and targeted Narrator evidence were not all established. The JavaFX-free `IconKey` enum is
the feature interface (the feedback model carries it), and fresh public-JavaFX `SVGPath` nodes from `SemanticIcons`
are the sole adapter. The POM bans
`org.kordamp.ikonli:*` transitively so no dormant alternative can enter the image. `SemanticIconsTest`, the source
gate, dependency convergence, and the packaged smoke continuously verify that choice.

## Feedback and dialogs

`WorkbenchFeedback` publishes immutable InfoBar, durable session Activity, status, and pending-dialog state before
any platform effect. Validation, success, warning, and failure share the same severity, explicit text cue, semantic
icon key, and terminal disposition across those projections. Dismissing an InfoBar never deletes Activity or the
terminal summary. Tokenized answers reject stale dialogs. Standard `Dialog`/`DialogPane` rendering provides an
explicit owner, one modal at a time, safe Enter/Escape defaults, typed actions, and launcher focus restoration.

## Production platform bans

Focus intent lives in the scene's focus owner
([#123](https://github.com/evildarkarchon/BS2BG/issues/123)). Production never reads window activation and never
activates a window to deliver focus. Refills, async completions and dialog returns set the scene's focus owner, and
JavaFX gives focus to it when the user returns to the window. The only focus check is
`WorkbenchFocusPolicy.ownsSceneFocus(node)` (`node.getScene() != null && node.getScene().getFocusOwner() == node`), and
focus is requested once, synchronously. Satellite windows (the Fill Empty and NPC filter popups, the portrait viewer)
request focus right after `show()` and restore it inside `onHidden` through `WorkbenchFocusPolicy.restoreAfterSatellite`.

`WorkbenchFocusPolicy` is the shell's shared focus module ([#127](https://github.com/evildarkarchon/BS2BG/issues/127)).
It classifies the focus owner into an F6 landmark by walking up to the nearest landmark container the shell
registered (the three Area panes, the Output drawer, the Activity section, the status bar and the launchers); a
control outside every container is on the rail. Morphs' Add NPCs launcher is the one registration inside an Area
pane, so it keeps its `NPC_DATABASE_LAUNCHER` token. `WorkbenchFocusPolicyTest` pins the landmark of every
keyboard-reachable control in `workbench.fxml` and in each Area FXML the shell mounts into those containers
([ADR-0009](../adr/0009-workbench-areas-as-per-area-fxml-modules.md)), so a new control needs a deliberate
classification.

`ProductionPlatformBanGateTest` reads every production class file with `java.lang.classfile` and enforces these rules
(rules 1, 2 and 4 keep focus off window activation; rule 3 keeps OS appearance behind its seam).
Each exception is keyed by declaring class and method, and keeps a why-comment at its site:

| # | Rule | Allowed exceptions |
| --- | --- | --- |
| 1 | No `isFocused` / `focusedProperty` on any `javafx/*` type, including inherited calls from a production subclass | none |
| 2 | No `requestFocus` / `toFront` on any `javafx/stage/*` type (window activation) | `NpcPortraitViewer.openViewer`: raising the portrait viewer when it is already open (synchronous, directly commanded by the user, application already in the foreground) |
| 3 | No `Platform.getPreferences` call or method reference outside the appearance-preferences source | every method of `JavaFxAppearancePreferencesSource`: the one JavaFX appearance-preferences source |
| 4 | No `Platform.runLater` whose runnable is a `requestFocus` method reference, or a lambda that calls `requestFocus` directly | `TemplatesArea$SliderPresetCell.startEdit` (in `workbench.templates`): the rename-editor deferral |

Two focus deferrals are named, and no other is allowed:

- **Initial focus** (`attach`) is applied immediately when the stage is already showing, and otherwise from a one-shot
  `WINDOW_SHOWN` handler. It needs no rule exception because it is not a `runLater`.
- **The Slider Preset rename editor** (`startEdit`) requests focus synchronously, then once more on a `runLater`,
  because ListView's own click handling can take focus back to the list. The deferred request only applies while
  the list or that cell still owns focus, so it cannot take focus from a control the user has moved to.

The deferral check is not transitive: `Platform.runLater(this::helper)`, or a lambda calling a helper that requests
focus, is not detected. This rule still covers it: no production code defers a focus request except the rename
editor.

## Packaged verification

`smoke-app-image.ps1` drives the real packaged launcher by accessible role, stable name, state, and semantic tree
relationship. It exercises Light/Dark/System selection, High Contrast precedence/restoration, reduced motion,
keyboard landmarks including Activity, disabled Cancel state, InfoBar help text, durable Activity, and destructive
typed dialogs. It captures screenshots for High Contrast and reduced motion. The harness uses the documented
[`SPI_GETHIGHCONTRAST` / `SPI_SETHIGHCONTRAST`](https://learn.microsoft.com/en-us/windows/win32/winauto/high-contrast-parameter)
and [`SPI_GETCLIENTAREAANIMATION` / `SPI_SETCLIENTAREAANIMATION`](https://learn.microsoft.com/en-us/windows/win32/winauto/client-area-animation)
contracts. The original flags, High Contrast scheme, and animation preference are captured before launch and
restored both inside the successful test step and unconditionally in `finally`; restoration failure fails the gate.

The screenshot artifacts support visual review of contrast, clipping, and fidelity that UIA cannot prove. Targeted
human Narrator and cross-monitor inspection remain release evidence rather than claims inferred from automation.

### Mechanism at Tier 1, outcome at Tier 3

Each accessibility workaround and contract is proven twice
([#128](https://github.com/evildarkarchon/BS2BG/issues/128)). Headless Tiers 1-2 never activate accessibility, so
they cannot show that a workaround is needed or enough:

- **The shared workarounds live in one place.** `UiaListHelper` owns the empty-to-full ListView node replacement
  (with its focus capture and initialised flag) and the no-op `setAll` guard, including the keyed guard for the
  InfoBar and status icon hosts ([#147](https://github.com/evildarkarchon/BS2BG/issues/147)). Every guarded
  collection goes through it, so retiring a workaround is a one-place deletion. The single-Area workarounds (rename
  edit lifecycle, slider-row reuse, NPC Database HelpText mirror) stay with their Area.
- **Tier 1 proves the mechanism** as plain JavaFX logic. `UiaListHelperTest` covers the shared helper. Each
  single-Area workaround is pinned in its Area's suite: node replacement on an empty-to-full refill and the refill
  focus in `TemplatesAreaTest` and `MorphsAreaTest` (all four lists), the rename edit lifecycle, in-place cell update
  and slider-row identity across edits in `TemplatesAreaTest`, `labelFor` re-pointing after the Assigned Slider
  Presets swap in `MorphsAreaTest`, and the NPC Database label and portrait-status HelpText values in
  `NpcDatabaseAreaTest`. `WorkbenchAccessibilityContractTest` keeps the contracts that span the whole Workbench: no
  list-change event on a membership-preserving re-render (every guarded list, the Activity list and both severity
  icon hosts), mnemonic stripping from accessible names, every mnemonic label's `labelFor` reaching the control it
  precedes and its mnemonic surviving the shell's pane re-parenting (`MnemonicLabels`,
  [#155](https://github.com/evildarkarchon/BS2BG/issues/155)), and the typed dialog buttons' names. Each test fails
  if its workaround or contract is removed.
- **Tier 3 proves the outcome** through real UIA and never asserts how it was achieved. Its existing locators match
  ControlType, Name and HelpText, which already proves the property-to-UIA mapping. Two checks target workarounds
  directly:
  - **Slider Presets RuntimeId stability.** The smoke holds the `UUNP Athletic` ListItem, re-renders Templates
    through a keyboard selection that keeps list membership, and asserts that the held element is still live and
    that a fresh lookup returns the same RuntimeId. It uses the strict helpers `Find-UiaElementStrict` and
    `Assert-UiaElementStable`, which fail on element-unavailable errors that `Wait-UiaCondition` swallows while
    polling. Only this check uses them.
  - **NPC column filter outside click.** A pointer click on the NPC Database Area title closes the open Name filter
    popup and discards its pending choice. The popup sets `autoHide` and `consumeAutoHidingEvents`, which is
    BS2BG's own choice. The existing Fill Empty outside-click check stays.
- **Not automated.** Closing popups when the main window loses focus is JavaFX's built-in `autoHide` behaviour and
  is not tested. Focus announcements mean only that UIA reports `HasKeyboardFocus` on the expected element;
  Narrator speech stays human release evidence.

## Headless test platform workarounds

Tiers 1 and 2 run on JavaFX's built-in headless platform ([ADR-0008](../adr/0008-tiers-1-2-run-headless.md)).
That platform is new, and it has upstream gaps (for example JDK-8391883: `KeyCharacterCombination` throws headless,
fixed in jfx28). When a test needs a test-side workaround for such an upstream platform bug, the workaround must
come out in the JavaFX bump that ships the fix:

- **Comment.** The workaround carries a `JDK-NNNNNNN (fixed in jfxNN)` comment naming the JBS issue and the first
  JavaFX feature release with the fix.
- **Registry.** It also gets an entry, with the same JBS id and fix release, in a small test-side registry.
- **Tripwire.** A tripwire test reads the running `javafx.runtime.version` and fails once it reaches any
  registered fix release. The workaround is then removed in the same reviewed JavaFX bump.

The registry and the tripwire are built with the first workaround that needs them; none is needed today. A test
that cannot hold headless is rewritten to be deterministic or deleted from Tier 1, never skipped; behaviour the
headless platform cannot express moves to Tier 3 only when it proves a decision BS2BG made.
