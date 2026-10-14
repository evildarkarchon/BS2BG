# WorkbenchController responsibility and coupling survey

Asset for the wayfinder ticket *Survey WorkbenchController responsibilities and cross-area coupling* (#126), part of the map *Wayfinder: non-disruptive test suite and WorkbenchController decomposition* (#120).

- **Snapshot:** `main` at `4a8de76`.
- **Files read:**
  - `src/com/asdasfa/jbs2bg/workbench/WorkbenchController.java` (4,823 lines, 205 `@FXML` fields)
  - `src/com/asdasfa/jbs2bg/workbench.fxml` (978 lines)
  - `test/com/asdasfa/jbs2bg/workbench/WorkbenchControllerTest.java` (3,875 lines)
  - the existing `*Feature`, `WorkbenchNavigation`, `WorkbenchFeedback`, `WorkbenchProjectFlow` and `JobCoordinator` classes

**Nothing is decided here.** The survey only makes the current shape visible for the *WorkbenchController decomposition shape* ticket (#127). Line numbers refer to `WorkbenchController.java` unless stated otherwise.

## 1. Shape at a glance

- **One FXML, one controller.** `workbench.fxml` declares one `fx:controller` and uses no `fx:include`. `Main.java:177-185` loads it, calls the public `attach(flow, stage, profileDir, settingsStartup)`, then shows the Stage.
- **Area panes are interleaved by landmark, not grouped by area.** The FXML lays out four landmark containers, and each holds one sibling subtree per area. Visibility is toggled by `renderNavigation`. The pane ranges below are FXML line numbers:

  | FXML landmark | Templates | Morphs | Settings | NPC Database |
  | --- | --- | --- | --- | --- |
  | `primaryPane` (116) | 120-199 | 200-342 | 343-376 | 377-409 |
  | `editorPane` (410) | 413-447 | 448-474 | 475-514 | 515-599 |
  | `inspectorPane` (600) | 604-704 | 705-771 | 772-795 | 796-860 |

  The remaining FXML regions are shell-owned:
  - Shell chrome: menu, rail and theme at 30-73; header and global InfoBar at 74-111.
  - `overlayLayer` at 861-863.
  - Diagnostics and Activity at 866-888.
  - The Output drawer at 891-960.
  - The status bar at 961-978.

  An FXML split per area would therefore cut each area into three fragments, one per landmark, or would need a per-area triple.
- **Area controls cross area boundaries in the FXML.** Two examples:
  - `importBodySlideButton` sits in the Templates primary pane (FXML 192), but it dispatches a *Project* intent and Settings and the job lane both disable it.
  - `addNpcsFromDatabaseButton` sits in the Morphs primary pane (FXML 270) and launches the NPC Database.
- **Feature classes already hold most state.** The controller constructs six feature objects plus the importer in `attach` (757-763). It translates control events into typed intents, renders immutable frames, and realises effects (dialogs, choosers, popups, focus). The coordination between areas lives in the controller and nowhere else (see §3).

## 2. Responsibility inventory

### 2.1 Per area

| Area | Responsibility | Lines |
| --- | --- | --- |
| **Templates** | `@FXML` controls | 178-206, 292-348 |
| | Wiring: filter, sort, profile, create, duplicate, rename, remove, clear, import launcher | 831-864 |
| | Gang/bulk controls and the coalesced Slider gesture | 2698-2743 |
| | Slider Preset list config (selection, type-ahead) | 2748-2767 |
| | Intent dispatch, mutation gate, update render | 2772-2819 |
| | Destructive confirmation effect | 2825-2844 |
| | Outcome → feedback tier routing (validation, mutation, slider-choice gesture vs bulk) | 2849-2920, 3034-3040 |
| | Failure dialog with exact focus capture/restore (row control, then control family, then landmark) | 2925-3029, records 4587-4617 |
| | Frame render (InfoBar, button states, inspector text) | 3205-3254 |
| | `SliderChoiceRow` reconciliation by identity; gang render | 3259-3321 |
| | Selection reconcile; rename editor sync | 3490-3534 |
| | Inline-rename `SliderPresetCell` (owns `activeRenameCell`/`activeRenameField`) | 4634-4758 |
| | Package-private test accessors (`templatesFrame`, `activeRenameField`, `sliderPresetListNode`) | 3539-3555 |
| **Morphs** | `@FXML` controls | 350-442 |
| | Wiring: target and NPC catalogs, authoring, relationships, NPC DB launcher, portrait viewer launcher | 869-924 |
| | Fill Empty light-dismiss flyout (a `Popup` built in code) | 932-1047 |
| | NPC portrait thumbnail (async `Image`, stale-load guard) | 1066-1125 |
| | Catalog list configs (assigned presets, targets, NPCs) | 1306-1401 |
| | Intent dispatch, mutation gate, update render | 2091-2143 |
| | Destructive confirmation effect | 2148-2177 |
| | Outcome → feedback, invalid-field refocus, failure dialog | 2180-2260 |
| | Frame render | 3045-3200 |
| | Test accessors | 3558-3570 |
| **NPC Database** | `@FXML` controls | 208-290 |
| | Wiring: sources, table columns and row factory, sort policy, type-ahead, Enter-to-promote, sort choice, filter buttons, promote, back | 1404-1523 |
| | Portrait thumbnail (a near-copy of the Morphs one) | 1128-1185 |
| | Morphs ↔ NPC Database hand-off (`openNpcDatabaseFromMorphs`, `backToMorphs`) | 1526-1543 |
| | Promotion: report → Project render, inline InfoBar, status/Activity | 1546-1627 |
| | Column-filter checklist `Popup` and filter button render | 1630-1774 |
| | Frame render | 1777-1879 |
| | Source import (job admission), remove/clear with confirmation | 1882-1948 |
| **Settings** | `@FXML` controls | 444-482 |
| | Wiring | 1953-1978 |
| | Intent dispatch, Output refresh on accepted change | 2263-2297 |
| | Dirty Reload confirmation | 2300-2322 |
| | Job submission, retry factory and recapture | 2325-2388 |
| | **Worker body** `static runSettingsEffect`: `Settings.persist`, `Settings.initialize`, `GenerationPreferencesStore` | 2394-2460 |
| | Diagnostic converters | 2463-2492 |
| | Completion: close continuation, published-Settings fan-out | 2495-2553 |
| | Outcome → feedback; startup evidence | 2556-2635 |
| | Frame render (also disables `importBodySlideButton`) | 2638-2692 |
| | Dirty-Settings close confirmation | 4186-4211 |
| | Close-focus restore from the job lane | 4468-4473, record 4570-4582 |
| | `SettingsEntryCell` | 4779-4799 |
| **Output** | `@FXML` drawer controls | 488-514 |
| | Wiring | 1981-2005 |
| | Dispatch (with Settings prerequisite) and observed update | 2008-2022 |
| | Effects: reveal drawer, clipboard, directory/file choosers | 2025-2046 |
| | Frame render (reads job blocked state) | 2049-2088 |
| | Drawer geometry and height clamp | 3607-3617, 3765-3780 |

### 2.2 Cross-cutting concerns

| Concern | Responsibility | Lines |
| --- | --- | --- |
| **Composition / lifecycle** | `attach` overloads | 685-810 |
| | Production overload: loads the appearance store, builds `JavaFxWorkbenchPlatform` | 685-697 |
| | Test overload: `Path.of(".")` + `Settings.publishedState()` | 709-714 |
| | Isolated-Settings overload | 728-734 |
| | Core attach: builds features, wires the Stage close request and `WINDOW_HIDDEN` cleanup, subscribes to jobs and output, initial render, initial focus `runLater` | 750-810 |
| **Project lifecycle (shell)** | File menu accelerators | 815-826 |
| | `dispatch` (close-during-job, dirty-Settings gate) | 4162-4173 |
| | `dispatchProject` | 4176-4183 |
| | Synchronous effect loop `apply` (dialogs, choosers, close window) | 4216-4256 |
| | Cancellation feedback | 4259-4264 |
| | `render(ProjectFlow.Frame)`: title, status, diagnostics, fan-out to Templates, Morphs and Output `acceptProjectFrame`, and NPC DB reset on NEW/OPEN | 4269-4305 |
| | `publishProjectFeedback` | 4502-4525 |
| | Static helpers (`dialogAction`, `operationDescription`, `projectStatus`, `shortcut`, `OperationDescription`) | 587-672, 4561-4567 |
| **Shell / navigation** | Rail toggles, overlay buttons, key filter, width listener, overlay clip | 3575-3602 |
| | Global key map (`handleNavigationKey`): Ctrl+G/E Output, per-area Ctrl+K, F2/Escape rename, Ctrl+1..5, F6, F7, Escape dismiss/clear selection | 3785-3882 |
| | `navigate` / `applyNavigation`: leaving NPC DB clears visit state, hides the filter popup, resets inspector scroll | 3887-3920 |
| | `renderNavigation`: per-area visibility toggles, landmark accessible text | 3925-3978 |
| | `areaButton` | 4150-4157 |
| | Semantic icons | 579-582, 3724-3730 |
| **Responsive layout** | Reparents the real side panes into `overlayLayer` (`renderResponsivePanes`) | 3983-4006 |
| | Width → `navigation.resize` | 805-806, 3593-3596 |
| | Catalog height binding to `stage.renderScaleYProperty()` (`configureCatalogHeight`) | 3426-3437 |
| **Focus policy** | `currentSemanticFocus`: a 60-line identity chain over controls of *every* area, mapped to a `Landmark` | 4011-4079 |
| | `requestFocus`: fallback to the area default, then `isFocused()` → `runLater` retry | 4084-4099 |
| | `resolveFocusNode`: landmark × area → node | 4104-4137 |
| | `firstSliderChoiceControl` | 4142-4145 |
| | `canRestoreFocus` | 1050-1058 |
| | Per-area exact restore: Templates failure | 2946-3029 |
| | Invalid-input refocus: Morphs 2189-2194, Templates 2865-2866 | |
| **Feedback / InfoBar** | Activity cell factory, dismiss, retry, cancel button | 3622-3664 |
| | `renderFeedback`: global InfoBar, Activity list, status bar | 4530-4556 |
| | Severity style and Activity text/help | 600-604, 632-656 |
| | `ActivityCell` | 4804-4822 |
| | **Dialog round-trip** (`feedback.requestDialog` → `platform.complete*` → `feedback.answerDialog`), repeated 9 times | 1938-1948, 2148-2162, 2247-2260, 2300-2309, 2825-2837, 2925-2938, 4186-4194, 4239-4251, 4478-4494 |
| **Theme / appearance** | `configureAppearance`: reads `Platform.getPreferences()` at 3671 | 3667-3688 |
| | Theme job submission | 3698-3719 |
| | `renderAppearance`: also re-themes the open satellite roots | 3735-3755 |
| | `applySatelliteAppearance` | 3758-3760 |
| | `ThemeChoiceSaver` | 4623-4628 |
| **Popups / flyouts** | Fill Empty (Morphs) | 932-1038 |
| | NPC column filter (NPC DB) | 1630-1721 |
| | Both anchor with `localToScreen`, then `popup.show(stage, x, y)` (1034-1036, 1717-1718). Both add a scene-level Escape filter as a packaged-runtime workaround (937-943, 1691-1697), and focus through `runLater`. | |
| | Owner cleanup on `WINDOW_HIDDEN` | 786-796 |
| | Navigation hides the filter popup | 3903-3904 |
| **Portrait viewer Stage** | `showNpcPortraitViewer` (Morphs) and `showNpcDatabasePortraitViewer` (NPC DB) share `showPortraitViewer`: `new Stage()`, `initOwner(stage)`, `show()`, focus `runLater` on open and on close. One viewer field is shared by both areas. | 1191-1303 |
| **UIA ListView workarounds** | Node replacement on empty → refill: `replaceEmpty*` for the slider preset, custom target, NPC and assigned lists | 3358-3401, 3439-3485 |
| | No-op `setAll` avoidance plus the `*ListInitialized` flags: `reconcile*Items` | 3326-3356, 3403-3415 |
| | The same equality guard inline in other renders | 1780, 1791, 2642, 3283 |
| | Edit lifecycle instead of `refresh()` | 3509-3534; in-place `SliderPresetCell.renderRename` 4706-4734 |
| | `accessibleHelp` mirror of dynamic label text | 1875-1879 |
| | `SliderChoiceRow` identity reuse "preserves the UIA subtree" | 3256-3285 |
| **Job / publication lanes** | Subscriptions | 783-784 |
| | `renderJobFrame`: sets the 3 `*MutationsBlocked` flags; disables menu, theme, import and generate controls; **re-renders all five areas**; Open-Project reset flags; terminal publish; async close; close-after-job | 4310-4392 |
| | Progress | 4397-4423 |
| | Terminal Activity | 4428-4465 |
| | Failure dialog with Retry | 4478-4497 |
| | NPC import job: `NpcDatabaseImporter` with a `renderNpcDatabase` callback | 760 |
| | Settings jobs | 2325-2523 |
| | Theme job | 3698-3719 |

### 2.3 Hidden global / environment reads

| Read | Lines | Note |
| --- | --- | --- |
| `Settings.publishedState()` | 712 | Package `attach` overload used by most tests |
| `Settings.snapshot()` | 763 | Output generation-settings supplier, read on every Generate |
| `Settings.persist` / `Settings.initialize` | 2414, 2422 | Static worker body; publishes the global pair |
| `Path.of(".")` | 712 | The current working directory becomes the settings directory in the test overload |
| `Path.of("images")` | 1085, 1145 | Portrait lookup relative to the current working directory |
| `Platform.getPreferences()` | 3671 | OS colour scheme, High Contrast, reduced motion |
| `Clock.systemUTC()` | 104, 757, 758 | Feedback, Templates and Morphs timestamps (not injectable) |
| `System.nanoTime()` | 1469 | NPC DB type-ahead timing |
| `stage.renderScaleYProperty()` | 3433 | Catalog height |
| `new WorkbenchNavigation()` / `new WorkbenchFeedback(...)` | 103-104 | Field initialisers; not injectable |

### 2.4 OS-window and desktop-coupled call sites

- **`isFocused()` (8):**
  - 2972 and 3000: Templates failure restore.
  - 3360, 3383, 3441 and 3466: `replaceEmpty*` focus carry-over.
  - 4097: `requestFocus` retry.
  - 4728: rename field.

  The ticket *Focus preservation semantics without the OS-active window* (#123) replaces them with `ownsSceneFocus`.
- **`Platform.runLater` (25), all focus or scroll deferrals:**
  - 808: initial focus. Kept by #123.
  - 1032, 1037: Fill Empty.
  - 1293, 1302: viewer.
  - 1709, 1720: filter popup.
  - 2190, 2194: Morphs invalid input.
  - 2719: go-to-set-sliders.
  - 2784, 2866: Templates.
  - 2973, 3001: failure restore.
  - 3377, 3399, 3457, 3483: node replacement.
  - 3801, 3823, 3830: Ctrl+K.
  - 3912, 3917: inspector scroll reset.
  - 4098: `requestFocus`.
  - 4729: rename. This is the guarded rename deferral #123 keeps.
- **Stage use:**
  - `setTitle`: 4275.
  - `setOnCloseRequest` and `WINDOW_HIDDEN`: 777-796.
  - `getScene().getFocusOwner()` (×3): 2947, 2981, 4012.
  - `isShowing()`: 1031, 1292, 1708.
  - `getIcons()`: 1267.
  - Owner of popups, the viewer, dialogs and choosers.

## 3. Shared state and cross-area coupling

### 3.1 Controller fields read or written by more than one area

| Field (line) | Writers | Readers |
| --- | --- | --- |
| `navigation`, `navigationFrame` (103, 515) | `applyNavigation` (shell); NPC DB `openNpcDatabaseFromMorphs` / `backToMorphs`; Output `RevealDrawer` effect; `render` / `renderJobFrame` (`clearNpcDatabaseReturn`) | Key map; focus policy; popup and viewer `onHidden` focus fallbacks (Morphs, NPC DB); overlay buttons |
| `feedback` (104) | Every area, the Project lifecycle and the job lane | `renderFeedback` |
| `platform`, `stage` (523-524) | `attach` | NPC DB (chooser, confirm); Morphs, Templates and Settings (confirm, failure); Output (clipboard, choosers); Project `apply`; job failure; satellites (owner) |
| `projectFlow` (516) | `attach` | `jobs().frame()` blocked checks in NPC DB render/actions, Output render, Settings render/submit, Activity retry, theme. `frame()` in NPC DB promotion, `render`, `renderMorphsUpdate`/`renderTemplatesUpdate`, `refreshPublishedSettings`, `renderJobFrame` |
| `renderedProjectSequence` (527) | `attach` 797; NPC DB promotion 1558; Morphs 2139; Settings 2549; Templates 2815; `render` 4302; `renderJobFrame` 4365 | `render` 4278/4301; Templates and Morphs update renders |
| `activeOperation` (528) | `dispatchProject` | `render` (lifecycle reset on NEW/OPEN); `apply`; `publishProjectFeedback`; `renderJobFrame` (async close) |
| `templatesOwnProjectDiagnostics`, `morphsOwnProjectDiagnostics` (541-542) | `dispatchTemplates`; `dispatchMorphs`; NPC DB `promoteNpcDatabase` (both) | `render` 4292/4296. **Each suppresses the *other* area's diagnostics.** |
| `templatesMutationsBlocked`, `morphsMutationsBlocked`, `settingsMutationsBlocked` (545-547) | `renderJobFrame` (all from `frame.projectMutationsBlocked()`) | Their own area's dispatch, render, gang, row and relationship controls |
| `resetTemplatesOnNextProjectFrame`, `resetMorphsOnNextProjectFrame` (548-549) | `renderJobFrame` (Open Project terminal) | `render` (also derives `resetOutput`) |
| `settingsCloseContinuation` (533) | Settings dispatch / recapture / completion | `completeSettingsEffect` → `dispatchProject(CLOSE)`; `restoreSettingsCloseFocus` (job lane) |
| `settingsRefreshDeferred` (535) | `completeSettingsEffect` | `renderJobFrame` (close-after-job) |
| `closeAfterActiveJob`, `finalClose`, `renderedTerminalAttemptId` (526, 531-532) | `dispatch`; `apply`; `renderJobFrame`; close request | Same |
| `npcPortraitViewer` / `Root` / `Path` (566-568) | Morphs and NPC DB viewer launchers | `WINDOW_HIDDEN`; `renderAppearance` |
| `fillEmptyPopup*` (564-565), `npcFilterPopup*` (569-571) | Morphs; NPC DB | `WINDOW_HIDDEN`; `renderAppearance`; `applyNavigation` (filter) |
| `sliderChoiceRowsByName` (574) | Templates | `currentSemanticFocus`; `resolveFocusNode`; go-to-set-sliders |
| `npcFilterButtons` (572) | NPC DB | `currentSemanticFocus` |
| `appearanceAdapter` (525) | `configureAppearance` | Satellites (Morphs, NPC DB); `WINDOW_HIDDEN` |

Single-area fields (no cross-area use):
- the `rendering*` guards (536-540)
- `activeRename*` (543-544)
- `*ListInitialized` (550-553)
- `renderedMorphTarget/NpcSelection` (554-555)
- `renderedPortrait*` (556-559) and `renderedDatabasePortrait*` (560-563)
- `npcSortChoices` (573)

### 3.2 Controls written by more than one area

- **`importBodySlideButton` (Templates pane):** the Templates action (862); `renderSettings` disable (2687-2688); `renderJobFrame` disable (4321). Two writers race on the same `disable` flag, and the last render wins.
- **`generateOutputButton` (header):** Output action (1982); `renderJobFrame` disable from `settingsFeature.frame().liveAvailable()` (4325). `dispatchOutput` repeats the Settings check for Ctrl+G (2009).
- **`importNpcSourcesButton`, `removeNpcSourceButton`, `clearNpcDatabaseButton`:** `renderNpcDatabase` (1828-1830) and `renderJobFrame` (4322-4324) compute the same disables.
- **`themeChoice`:** appearance wiring, plus the `renderJobFrame` disable (4319).
- **`npcMorphAssignmentList` (Morphs):** NPC DB `backToMorphs` scrolls it (1539-1541).

### 3.3 Cross-area edges

| # | From → to | Where | What |
| --- | --- | --- | --- |
| E1 | Project → Templates, Morphs, Output, NPC DB | `render` 4285-4298 | `acceptProjectFrame` fan-out; NPC DB `clearAllSelections` on NEW/OPEN |
| E2 | Templates / Morphs → Project chrome | 2136-2143, 2812-2819 | A sequence change re-runs `render(projectFlow.frame())`, which fans out again (E1) |
| E3 | NPC DB → Project, Templates, Morphs | 1554-1565 | Promotion sets both `*OwnProjectDiagnostics` flags and renders the Project frame |
| E4 | NPC DB → Morphs | 1536-1542 | `backToMorphs` dispatches `MorphsFeature.SelectNpc` and scrolls the Morphs list |
| E5 | Morphs → NPC DB | 904, 1526-1530 | The launcher navigates with a `NPC_DATABASE_LAUNCHER` return target |
| E6 | Settings → Output | 762-763, 2294-2295, 2507-2508, 2552 | Generation-settings supplier reads `Settings.snapshot()` and `settingsFeature`; refresh after accepted edits and published saves |
| E7 | Settings → Templates, Project | 2546-2551, 2687 | `projectFlow.refreshSettings()`, `templatesFeature.refreshSettings()`, and the `importBodySlideButton` disable |
| E8 | Settings ↔ close lifecycle | 4168-4169, 4186-4211, 2514-2521, 4468-4473 | Dirty-Settings close dialog; close continues after the save job; focus restore from the job lane |
| E9 | Settings → Output dispatch gate | 2009, 4325 | Generate needs live Settings |
| E10 | Jobs → every area | 4313-4330 | Blocked flags and a full re-render of all five areas on every coordinator frame |
| E11 | Jobs → Templates, Morphs, NPC DB | 4344-4363 | An Open Project terminal schedules the Templates and Morphs resets and clears NPC DB selections |
| E12 | Navigation → NPC DB, Morphs | 3895-3918 | Leaving NPC DB calls `leaveArea`; hides the filter popup; resets the inspector scroll on overlay open |
| E13 | Output → navigation | 2027 | `RevealDrawer` effect |
| E14 | Key map → every area | 3785-3882 | Shell-level filter on `workbenchRoot` dispatches to Output, Templates, Morphs and NPC DB |
| E15 | Focus policy → every area's controls | 4011-4137 | Identity chains and per-area defaults; reads `morphsFeature.frame().npcSelection()` and the disabled state of Templates, Morphs and Settings buttons |
| E16 | Appearance → satellites | 3735-3741, 3758-3760 | Re-themes the Morphs and NPC DB popups and the viewer |
| E17 | Shared satellite | 1191-1303 | One portrait viewer for Morphs and NPC DB |
| E18 | Shared cell | 4761-4776 | `SliderPresetDisplayCell`, used by the Morphs lists and the Fill Empty flyout |

**Coupling hubs.** Three controller methods carry most of the edges:
- `render(ProjectFlow.Frame, diagnostics)`: E1-E3.
- `renderJobFrame`: E10, E11 and the close lifecycle.
- The focus-policy trio `currentSemanticFocus`, `resolveFocusNode` and `requestFocus`: E15. It is the only place that knows every area's controls.

The Settings close continuation (E8) runs from Settings through the job lane back to the Project lifecycle, which makes it the longest single chain.

## 4. Overlap with the existing feature classes

### 4.1 What already exists

None of the following classes import JavaFX:

| Class | Lines | Contract | Used by the controller as |
| --- | --- | --- | --- |
| `TemplatesFeature` | 1,238 | `dispatch(Intent)` → `Update(frame, effect)`; `respond(token, …)`; `acceptProjectFrame`; `refreshSettings`; `observe` | Synchronous dispatch. **`observe` is unused.** |
| `MorphsFeature` | 1,235 | `dispatch` → `Update(frame, effect, fillEmptyOffer)`; `respond`, `respondFillEmpty`, `cancelFillEmpty`; `acceptProjectFrame`; `observe` | Synchronous dispatch. **`observe` is unused.** |
| `SettingsFeature` | 1,004 | `dispatch` → `Update(frame, Optional<Effect>)`; `respondReload`, `retry`, `complete`, `cancel` | Dispatch. The controller runs the effects. |
| `OutputFeature` | 923 | `dispatch` → `Update(frame, effect)`; `completeExport`, `completeSelectedExport`; `acceptProjectFrame`; `refreshGenerationSettings`; `observe` | Dispatch plus `observe`. This is the only feature observer the controller subscribes to (784). |
| `NpcDatabaseFeature` | 513 | **Imperative mutators** (`selectSource`, `setSortOrder`, `setCriterion`, `promoteSelected`, …). The caller pulls `frame()`; there are no intents, effects or observers. | 18 call sites each mutate, then call `renderNpcDatabase(npcDatabaseFeature.frame())` by hand. |
| `NpcDatabaseImporter` | 125 | Submits the source-read job and reports frames through a `Consumer` callback | The callback is `this::renderNpcDatabase` (760) |
| `WorkbenchNavigation` | 336 | Pure reducer → `Transition(frame, Optional<FocusTarget>)` | All transitions go through `applyNavigation` |
| `WorkbenchFeedback` | 470 | `publish*`, `requestDialog` / `answerDialog`, `dismissInfoBar` → `Frame` | 22 publish sites and 9 dialog round-trips. References `SemanticIcons.IconKey`, a nested type of a JavaFX-importing class. |
| `WorkbenchProjectFlow` | 971 | `request` / `respond` effect loop, `apply`, `promoteNpcs`, `refreshSettings`, `jobs()` | **No observers.** Async Project frames are discovered by pulling `projectFlow.frame()` inside `renderJobFrame`. |
| `JobCoordinator` | 1,206 | `submit`, `retry`, `requestCancel`, `requestShutdown`, `observe` | One subscription (`renderJobFrame`) |
| `WorkbenchPlatform` | 92 | Package seam: choosers, dialogs, clipboard, `closeWindow` | Injected; `RecordingPlatform` in tests |
| `JavaFxWorkbenchAppearance` | 173 | Preference listener and CSS token applier | Owned; the controller keeps satellite roots for re-theming |
| `SliderChoiceRow` | 267 | JavaFX row control with a `Consumer<TemplatesFeature.Intent>` dispatcher | Created and reconciled by the controller |
| `WorkbenchGeometry` | 54 | Static math | Not used by the controller (`Main` only) |

### 4.2 Duplication and bypasses

- **The dispatch pipeline is written three times.** `dispatchTemplates` (2772), `dispatchMorphs` (2091) and `dispatchSettings` (2274) each run:
  1. a mutation gate;
  2. `feature.dispatch`;
  3. an update render that re-renders Project chrome on a sequence change;
  4. an effect → dialog round-trip → `feature.respond`;
  5. outcome → feedback tier routing.

  The surrounding steps are not shared either.
- **Each gate duplicates feature knowledge.**
  - `isTemplatesMutation` (2794), `isMorphsMutation` (2121) and `isSettingsMutation` (2556) list which intents mutate.
  - `SettingsFeature` already has an identical private `isMutation` (`SettingsFeature.java:134`).
  - `TemplatesFeature` and `MorphsFeature` only reject during a pending effect (`TemplatesFeature.java:124`, `MorphsFeature.java:167`), not during a job.
  - So the "job blocks Project mutation" rule lives only in the controller's flags.
- **The gated pipeline is bypassed in two places.**
  - `backToMorphs` (1538) calls `morphsFeature.dispatch(new SelectNpc…)` directly.
  - Fill Empty Apply (1014-1016) calls `respondFillEmpty`, `renderMorphsUpdate` and `publishMorphsOutcome` directly, outside `dispatchMorphs`.
  - `SliderChoiceRow` goes through `dispatchTemplates`, so it is gated.
- **The dialog round-trip is copied nine times** (§2.2). `WorkbenchFeedback` owns `requestDialog` / `answerDialog`, and `WorkbenchPlatform` owns `complete*`. Nothing composes the two.
- **Feedback policy lives in the controller, not in the features.** The controller decides the wording, severity, disposition and tier (InfoBar, status, Activity or dialog):
  - `publishTemplatesOutcome`, `publishSliderChoiceOutcome` and `publishTemplatesMutation`
  - `publishMorphsOutcome` and `publishMorphsMutation`
  - `publishSettingsOutcome` and `publishInitialSettingsEvidence`
  - NPC DB `promotionSummary`, `promotionSeverity` and `promotionDetails`
  - `publishCopyOutcome`, `publishProjectFeedback` and `renderTerminalJob`

  `promotionSummary` and `promotionSeverity` are also called again from `renderNpcDatabase` (1858-1859) for the inline InfoBar.
- **Settings job plumbing lives in the controller, unlike the other job owners.**
  - `OutputFeature` and `WorkbenchProjectFlow` submit their own jobs, and the NPC import has `NpcDatabaseImporter`.
  - For Settings, the submission, retry factory, recapture and **worker body** (`runSettingsEffect`, `applySettingsCompletion`) are in the controller (2325-2542).
  - The theme job is also built in the controller (3698-3719).
- **NPC DB return context is split.** `WorkbenchNavigation` holds `npcDatabaseReturnTarget` and `NpcDatabaseFeature` holds `returnAssignment`. The controller clears both in step: `render` 4286-4287, `renderJobFrame` 4360-4361, `applyNavigation` 3900.
- **The NPC DB feature contract differs from the others:**
  - It has no intents, effects or `Update`.
  - Confirmations are realised by the controller around plain mutators (1900-1948).
  - Type-ahead time is passed in by the controller as `System.nanoTime()`, whereas Templates and Morphs use an injected `Clock`.
- **Disable state is computed twice.** For the NPC DB buttons, both `renderNpcDatabase` (1828-1830) and `renderJobFrame` (4322-4324) compute it. The Generate prerequisite is checked in `dispatchOutput` (2009) and again in `renderJobFrame` (4325). `importBodySlideButton` is disabled by `renderSettings` (2687) and `renderJobFrame` (4321).
- **Some view-model logic sits in the controller with no feature counterpart:**
  - The NPC column-filter distinct-value projection (1637-1645).
  - The portrait thumbnail lookup, async load and stale guard, implemented twice (1066-1125 and 1128-1185). Only the path search is factored out, into `NpcPortraitFiles`.
- **The seam leaks in two ways.**
  - `apply` calls the static `JavaFxWorkbenchPlatform.dialogSpec(effect.kind())` (4240). The controller asks the JavaFX implementation, not the `WorkbenchPlatform` seam, for a pure mapping from effect to spec, so tests with `RecordingPlatform` still execute it.
  - `WorkbenchFeedback` → `SemanticIcons.IconKey` pulls a JavaFX-importing class into the feedback model's type graph.

### 4.3 Global Settings reads that bypass `SettingsFeature`

These are relevant to *Explicit settings for WorkbenchController and its tests* (#125).

- **Output generation settings** (763) combine `Settings.snapshot()` with `settingsFeature.frame().omitRedundantSliders()`.
- **Templates previews and redundancy** go through `ProjectOutputFormatter.formatSliderChoicePreview` / `isSliderChoiceRedundant`, which read `Settings.snapshot()`:
  - `TemplatesFeature.java:721, 733`
  - `SliderChoiceRow.java:134`
  - `ProjectOutputFormatter.java:273, 323`
- **`SettingsFeature` itself** reads `Settings.snapshot()` at construction (66) and at completion (407, 431). It also reads Java `Preferences.userRoot()` through `GenerationPreferencesStore` (95) during construction.
- **The `ProjectSession` path** (`WorkbenchProjectFlow.refreshSettings` / `apply`) reads it through `DefaultProjectSession.java:175` and `SliderChoiceDefaults`.
- **The controller's worker body** publishes the global through `Settings.persist` / `Settings.initialize` (2414, 2422).

The other non-injectable inputs are `Clock.systemUTC()` (controller 104, 757-758) and `RandomGenerator.getDefault()` (`NpcDatabaseFeature.java:102`, `MorphsFeature.java:96`).

## 5. Test coverage of `WorkbenchControllerTest`

### 5.1 Overview

The class has **65 test methods**: 63 `@Test` and 2 `@ParameterizedTest`. They expand to 73 invocations, because `shortCatalogsFitAllRowsAtFractionalRenderScales` runs 8 cases and `confirmedMorphsRemovalReportsRejectedAndFailedOutcomes` runs 2.

- **Fixtures.** There is no `@BeforeEach` or `@AfterEach`; the only fixture is `@TempDir`.
- **Construction.** Every UI test builds its own `FXMLLoader` graph and `new Stage()`, and reaches controls through `loader.getNamespace()` or the package accessors.
- **Fakes.** Effects go through `RecordingPlatform`, a scripted `WorkbenchPlatform` that never opens native dialogs and never really closes the Stage. Jobs use `JobCoordinator` with `ManualExecutor` or `InlineExecutorService` and a fixed `Clock`.
- **Input.** All input is synthetic: `fireEvent(KeyEvent)`, `Button.fire()` and selection-model calls. No test uses Robot or AWT.

**Which `attach` overloads the tests use:**
- Most tests use the 3-argument overload. It pulls in `Settings.publishedState()` and `Path.of(".")`.
- Settings and close tests use the 5-argument isolated-Settings overload.
- Only `themePersistenceRunsAfterVisualSelectionOnTheWorker` uses the 8-argument core overload.
- No test uses the production overload.

### 5.2 Desktop coupling

| Coupling | Tests |
| --- | --- |
| `stage.show()` (real window) | **28** (#1-11, 13, 14, 30, 33-35, 40, 46, 48, 55, 57-62, 65) |
| Stage with a Scene that is never shown | 22 |
| Stage without a Scene (graph never parented) | 14 |
| No Stage (static `runSettingsEffect` only) | 1 (#26). It also spawns a child JVM lock holder. |
| Asserts `scene.getFocusOwner()` | **18** (#2, 10, 30, 33, 34, 40, 46, 48, 57-61, 65). No test calls `isFocused()`; production does. |
| Popup found through `Window.getWindows()` | 3 (#3 and #5 via `hideNpcColumnValue` at test line 2159, plus direct lookups at 324, 627 and 638) |
| Nested event loop (`settleCatalogLayout`, test line 766) | 1 (#14, which also sets render scale) |
| Assertions overridden by OS High Contrast | 2 (#63, #64). Every attached test reads `Platform.getPreferences()`. |
| Global `Settings` left re-initialised to a deleted `@TempDir` | ≈33 (#23-31, 38-50, 52-56, 59-64) |
| Global `Settings` restored via `SettingsTestSupport` | 3 (#33, 34, 51) |
| Off-FX worker threads (`runNextAsync`) alternating with publication lanes | 14 |

### 5.3 Responsibility → tests

Test numbers follow file order (§5.4).

| Responsibility (§2) | Tests | Gaps |
| --- | --- | --- |
| NPC DB import, catalog and inspector | 1, 6, 7, 8, 9 | – |
| NPC DB promotion, Morphs hand-off (E3-E5) | 2, 3, 4 | – |
| NPC DB column-filter popup, sort, clear | 3, 5 | – |
| NPC DB type-ahead and Ctrl+K | 10 | – |
| NPC DB portrait | 1 (missing-image text only) | No loaded-image or viewer test |
| Morphs catalogs, authoring, relationships | 15, 16, 18, 19, 20 | – |
| Morphs confirmation and failure tiers | 16, 17 | – |
| Morphs Fill Empty flyout | 11 | – |
| Morphs portrait and viewer | 12 (disabled-button path), 13 (narrow inspector scroll) | **No test opens the portrait viewer Stage** |
| Templates create, filter, duplicate | 32, 51 | – |
| Templates rows, gang, profile, exact focus | 33, 34 | No test covers the failure-dialog exact focus restore (2925-3029) |
| Templates inline rename (edit lifecycle) | 35 | – |
| Templates remove and clear confirmation | 36 | – |
| BodySlide import launcher (job + focus) | 30, 31 | – |
| Settings edit, save, reload, preference (job lanes) | 23, 24, 25, 27, 28 | – |
| Settings worker cancellation (static seam) | 26 | – |
| Settings startup evidence | 31 | – |
| Close lifecycle: dirty Project, dirty Settings, close-after-job (E8) | 29, 39, 41-50, 52 | – |
| Project open, recovery and diagnostics | 38, 53, 54 | – |
| Project chooser cancellation | 40 | – |
| Activity retry and eviction | 7, 47, 54, 55 | – |
| Job admission, progress, cancel (E10) | 8, 56, 62 | – |
| Output generate, copy, export, stale (E6, E13) | 59, 60, 61, 62 | – |
| Navigation: rail, keyboard, drawer, F6 landmarks | 57, 58 | – |
| Responsive layout | 13, 65 | – |
| Render scale / catalog height | 14 | – |
| Theme and semantic icons | 63, 64 | Appearance re-theme of open satellites (E16) is untested |
| UIA node replacement | 21, 22, 37 explicitly; implicitly 15, 16, 19, 20, 30 | – |
| UIA no-op `setAll` avoidance | none | Unasserted |
| UIA edit lifecycle | 35 | – |

### 5.4 Per-test list

Codes used in the Coupling column:

| Code | Meaning |
| --- | --- |
| SHOW | Real window shown with `stage.show()` |
| SC | Stage has a Scene but is never shown |
| NS | Stage has no Scene |
| FO | Asserts the scene focus owner |
| WIN | Finds a popup through `Window.getWindows()` |
| NEL | Nested event loop |
| HC | Outcome depends on OS High Contrast |
| SET | Re-initialises the global `Settings` singleton |
| SET-R | Reads the global `Settings` through the 3-argument `attach` |
| XT | Off-FX worker thread |
| CLOSE | Drives the close request or Exit |

| # | Test (test line) | Area | Coupling |
| --- | --- | --- | --- |
| 1 | `npcDatabaseImportsSourcesAndInspectsSelectedCatalogRow` (106) | NPC DB | SHOW, SET-R |
| 2 | `npcDatabaseAddReturnsToMorphsLauncherAndPromotedAssignment` (141) | NPC DB ↔ Morphs | SHOW, FO, SET-R |
| 3 | `npcDatabaseAddAllReportsFilteredDuplicatesAndRejections` (194) | NPC DB | SHOW, WIN, SET-R |
| 4 | `npcDatabaseRailExitDiscardsEarlierPromotionReturnContext` (255) | NPC DB / nav | SHOW, SET-R |
| 5 | `npcDatabaseClearUsesFilteredVisibleScope` (299) | NPC DB popup | SHOW, WIN, SET-R |
| 6 | `npcDatabaseMixedImportKeepsValidSourcesAndReportsRejectedLine` (357) | NPC DB | SHOW, SET-R |
| 7 | `npcDatabaseFailedImportCanRetryWithoutLosingCommittedSource` (404) | NPC DB / Activity | SHOW, SET-R |
| 8 | `npcDatabaseCancelRetainsEarlierSourcesAcrossNewProject` (451) | NPC DB / jobs | SHOW, SET-R |
| 9 | `npcDatabaseCancellationAtSourceBoundaryReportsOnlyPriorCommit` (518) | NPC DB / jobs | SHOW, SET-R |
| 10 | `npcDatabaseKeyboardSearchCyclesAndFocusesFiltering` (572) | NPC DB / keys | SHOW, FO, SET-R |
| 11 | `fillEmptyFlyoutCancelsAndAppliesOnlyChosenPresets` (611) | Morphs popup | SHOW, WIN, SET-R |
| 12 | `npcPortraitInspectorClearsWithSelectionAndDescribesMissingImage` (660) | Morphs portrait | SC, SET-R |
| 13 | `narrowNpcInspectorOpensAtPortraitAfterEarlierRelationshipScroll` (694) | Responsive | SHOW, SET-R |
| 14 | `shortCatalogsFitAllRowsAtFractionalRenderScales` ×8 (728) | Render scale | SHOW, NEL, SET-R |
| 15 | `morphsControlsCreateFilterAndAssignThroughTheAuthoritativeProjectFlow` (799) | Morphs | SC, SET-R |
| 16 | `morphsValidationAndDestructiveActionsUseSafeTypedFeedback` (877) | Morphs | NS, SET-R |
| 17 | `confirmedMorphsRemovalReportsRejectedAndFailedOutcomes` ×2 (958) | Morphs | NS, SET-R |
| 18 | `unassignedMorphEntriesDescribeTheirEmittedOutput` (997) | Morphs | NS, SET-R |
| 19 | `morphsNpcControlsCreateInspectAndSelectWithoutRetargeting` (1033) | Morphs | SC, SET-R |
| 20 | `morphsNpcControlsEditRelationshipsAndClearOnlyVisibleAssignments` (1101) | Morphs | SC, SET-R |
| 21 | `morphsEmptyToNonEmptyTransitionReplacesOnlyTheListViewAdapter` (1164) | UIA | NS, SET-R |
| 22 | `morphsAssignedRelationshipsEmptyToNonEmptyTransitionReplacesOnlyTheListViewAdapter` (1193) | UIA | NS, SET-R |
| 23 | `settingsAreaEditsAndPersistsBothProfilesThroughTheWorkbench` (1225) | Settings | SC, SET, XT |
| 24 | `generationPreferenceToggleRunsOnTheApplicationWorker` (1317) | Settings | SC, SET, XT |
| 25 | `settingsReloadRunsOnTheApplicationWorkerBeforePublishingItsFrame` (1372) | Settings | SC, SET, XT |
| 26 | `settingsReloadCanBeCancelledWhileWaitingForTheDirectoryLock` (1438) | Settings worker | No Stage, SET, child JVM |
| 27 | `dirtySettingsReloadConfirmsAndSavesBeforeReloading` (1536) | Settings | SC, SET, XT |
| 28 | `dirtySettingsReloadDiscardAdmitsReloadWithoutSaving` (1625) | Settings | SC, SET, XT |
| 29 | `shutdownRaceCancelsChainedSettingsReloadWithoutCallbackFailure` (1695) | Settings / close | SC, SET, XT, CLOSE |
| 30 | `bodySlideImportLauncherUsesTheCentralJobWithoutStealingFocus` (1763) | Templates / jobs / UIA | SHOW, FO, SET |
| 31 | `invalidStartupSettingsAreVisibleAndBlockBodySlideImport` (1813) | Settings → Templates, Output | SC, SET |
| 32 | `templatesControlsCreateAndFilterThroughTheAuthoritativeProjectFlow` (1846) | Templates | NS, SET-R |
| 33 | `templatesEditorRendersAccessibleRowsAndPreservesFocusAcrossEdits` (1890) | Templates | SHOW, FO, SET (restored) |
| 34 | `templatesGangControlsAreAtomicExclusiveAndKeyboardReachable` (1950) | Templates | SHOW, FO, SET (restored) |
| 35 | `templatesInlineRenameIsKeyboardCompleteAcrossRejectionAndSelectionClear` (2014) | Templates / UIA | SHOW, SET-R |
| 36 | `templatesRemoveAndClearVisibleRequireSafeTypedConfirmation` (2064) | Templates | NS, SET-R |
| 37 | `templatesEmptyToNonEmptyTransitionReplacesOnlyTheListViewAdapter` (2111) | UIA | SC, SET-R |
| 38 | `openCommandRendersTheAuthoritativeRecoveredFrame` (2186) | Project | NS, SET |
| 39 | `dirtyExitCanBeCancelledBeforeDiscardClosesTheWindow` (2233) | Close | NS, SET, CLOSE |
| 40 | `cancelledProjectChooserPublishesFeedbackAndRestoresFocus` (2267) | Project / focus | SHOW, FO, SET |
| 41 | `dirtySettingsCloseCanBeCancelledBeforeDiscardClosesTheWindow` (2302) | Close | SC, SET, CLOSE |
| 42 | `dirtySettingsSaveCompletesBeforeDirtyProjectCloseConfirmation` (2346) | Close | SC, SET, XT, CLOSE |
| 43 | `dirtySettingsCloseSaveSurvivesInlineWorkerAndPublicationCompletion` (2408) | Close | SC, SET, CLOSE |
| 44 | `failedDirtySettingsCloseSaveKeepsTheDraftAndWindowOpen` (2448) | Close | SC, SET, XT, CLOSE |
| 45 | `rejectedSettingsAdmissionDoesNotLeaveAPhantomCloseContinuation` (2511) | Close | SC, SET, XT, CLOSE |
| 46 | `failedDirtySettingsCloseSaveRetryPreservesTheCloseIntentAndFocus` (2577) | Close / focus | SHOW, FO, SET, XT, CLOSE |
| 47 | `cleanSettingsDraftMakesFailedSaveRetryUnavailableWithoutTechnicalFailure` (2656) | Settings / Activity | SC, SET, XT |
| 48 | `cancelledDirtySettingsCloseSaveRestoresFocusWithoutClosing` (2725) | Close / focus | SHOW, FO, SET, CLOSE |
| 49 | `dirtyExitWaitsForTheApplicationWorkerSaveBeforeClosingTheWindow` (2788) | Close | NS, SET, CLOSE |
| 50 | `closeDuringAnActiveJobCanSaveAfterThatJobSettles` (2836) | Close / jobs | NS, SET, CLOSE |
| 51 | `templatesDuplicateWithUnavailablePreviewRecordsTheMutation` (2894) | Templates | NS, SET (restored) |
| 52 | `pendingShutdownFailureDialogDoesNotOfferUnadmittableRetry` (2937) | Jobs / close | NS, SET, CLOSE |
| 53 | `malformedOpenRendersCompleteSourceCoordinates` (2986) | Project | NS, SET |
| 54 | `failedOpenActivityCanRetryWithFreshlyCapturedInputs` (3016) | Activity | NS, SET |
| 55 | `evictedActivityRetryIsDisabled` (3054) | Activity | SHOW, SET |
| 56 | `activeJobOwnsAdmissionProgressAndCancelControls` (3124) | Jobs | NS, SET |
| 57 | `keyboardNavigationPreservesTheActiveAreaWhenOutputToggles` (3188) | Nav / Output drawer | SHOW, FO, SET-R |
| 58 | `semanticFocusTraversalAndOutputFocusRestorationArePredictable` (3232) | Focus policy | SHOW, FO, SET-R |
| 59 | `generateRendersReadOnlyOutputTabsWithoutStealingFocus` (3272) | Output | SHOW, FO, SET |
| 60 | `copyAndExportOutputUseThePlatformAdapterAndCentralJobPath` (3326) | Output | SHOW, FO, SET |
| 61 | `projectEditDuringGenerateProducesStaleActivityWithoutRevealingOutput` (3395) | Output / jobs | SHOW, FO, SET |
| 62 | `cancelledGeneratePublishesActivityWithoutOutput` (3445) | Output / jobs | SHOW, SET |
| 63 | `themeChoiceAndSemanticIconsRenderThroughTheLoadedWorkbench` (3491) | Theme | SC, HC, SET |
| 64 | `themePersistenceRunsAfterVisualSelectionOnTheWorker` (3521) | Theme / jobs | SC, HC, SET, XT |
| 65 | `narrowLayoutKeepsTheEditorInlineAndUsesFocusRestoringSideOverlays` (3578) | Responsive / focus | SHOW, FO, SET-R |

### 5.5 Observations

These are observations for the decomposition decision, not recommendations.

- **Coverage is organised by user journey, not by unit.** Several tests span areas:
  - #2: NPC DB → Morphs.
  - #30 and #31: Settings → Templates and Output.
  - #39-#50: Settings → close lifecycle.

  These map onto the edges in §3.3 rather than onto any single area. The tests that already stay within one area are:
  - Templates: #32-#37.
  - Morphs: #15-#22.
  - NPC DB: #1, #5, #6.
  - Settings without close: #23-#28.
  - Output: #59-#62.
- **Focus-owner tests cluster on cross-cutting focus policy.** They are #2, #40, #46, #48, #57, #58 and #65; #33 and #34 cover exact Templates focus. They exercise `currentSemanticFocus`, `resolveFocusNode` and `requestFocus`, which span every area (E15).
- **`runSettingsEffect` and `applySettingsCompletion` are already package-static seams** (#26). They are the only controller logic tested without a scene graph.
