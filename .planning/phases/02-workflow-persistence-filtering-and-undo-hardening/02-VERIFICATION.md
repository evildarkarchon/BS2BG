---
phase: 02-workflow-persistence-filtering-and-undo-hardening
verified: 2026-04-27T02:20:09Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Exercise the Morphs tab filter popups and scope selector in the real Avalonia UI."
    expected: "All seven NPC filters are discoverable, accessible, dismissible, show active badges, and the filtered-empty state communicates that filters hid rows rather than data being lost."
    why_human: "Visual layout, tab order, popup behavior, and accessibility affordances cannot be fully proven from source and headless tests."
    status: "waived by maintainer on 2026-04-28"
  - test: "Use a large real-world NPC/preset dataset in the running app and type/search/filter repeatedly."
    expected: "Free-text search is debounced and the UI remains responsive without noticeable freezes or unbounded delays."
    why_human: "Automated tests prove debounce semantics and a smoke path, but perceived UI responsiveness with real data needs manual confirmation."
    status: "waived by maintainer on 2026-04-28"
  - test: "Perform restart workflows against the packaged app/user profile location."
    expected: "Omit Redundant Sliders and the five remembered folder channels survive process restart and invalid remembered paths remain non-blocking hints."
    why_human: "Source and unit tests verify persistence logic, but end-to-end OS storage location behavior is environment-dependent."
    status: "waived by maintainer on 2026-04-28"
---

# Phase 2: Workflow Persistence, Filtering, and Undo Hardening Verification Report

**Phase Goal:** Users can work safely across restarts and large NPC/preset datasets without hidden-row mutations, lost preferences, or corrupted undo state.
**Verified:** 2026-04-27T02:20:09Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can restart BS2BG and retain last-used folders plus generation-affecting preferences such as omit-redundant sliders. | ✓ VERIFIED | `UserPreferences` includes `OmitRedundantSliders`, `ProjectFolder`, `BodySlideXmlFolder`, `NpcTextFolder`, `BodyGenExportFolder`, and `BosJsonExportFolder` (`UserPreferencesService.cs:15-30`). `TemplatesViewModel` hydrates omit-redundant on construction and saves it best-effort while preserving folder fields (`TemplatesViewModel.cs:107-110`, `491-505`). Project/export/import pickers load remembered channels as start-folder hints and save successful selections (`WindowFileDialogService.cs:31-52`, `55-67`; `WindowBodySlideXmlFilePicker.cs:33-40`; `WindowNpcTextFilePicker.cs:31-38`). Tests cover preference and picker channels; full `dotnet test` passed 286 tests. |
| 2 | User can filter NPC rows by mod, name, editor ID, form ID, race, assignment state, and preset-related values while each NPC keeps stable identity. | ✓ VERIFIED | `NpcFilterColumn` defines all required dimensions (`NpcFilterState.cs:6-15`), predicates search/check all required values (`NpcFilterState.cs:117-190`), and `NpcRowViewModel.RowId` is generated outside Core serialization (`NpcRowViewModel.cs:16-30`). `MorphsViewModel` keeps `SourceCache<NpcRowViewModel, Guid>` sidecars and stable row maps (`MorphsViewModel.cs:40-48`, `1293-1318`). UI exposes all seven filter popups (`MainWindow.axaml:743-884`). |
| 3 | User can run bulk NPC operations with explicit all, visible, selected, and visible-empty scopes so filtered rows are not changed accidentally. | ✓ VERIFIED | `NpcBulkScope` includes `All`, `Visible`, `Selected`, and `VisibleEmpty`; resolver materializes row IDs before mutation (`NpcBulkScopeResolver.cs:6-12`, `52-73`). `MorphsViewModel` resolves scoped targets from all/visible/selected row IDs before fill/clear mutations and confirms destructive all-scope clears (`MorphsViewModel.cs:1077-1164`, `1522-1535`). UI has a `Scope` selector and `Fill Visible Empty` CTA (`MainWindow.axaml:685-706`). Tests cover visible-empty exclusions, hidden-row protection, and all-scope confirmation. |
| 4 | User can undo and redo high-risk preset, target, NPC assignment, import, clear, and profile operations without mutable live-state corruption. | ✓ VERIFIED | `UndoRedoService` records bounded operations with replay guard (`UndoRedoService.cs:28-41`, `90-100`). `UndoSnapshots.cs` provides value snapshots for presets, set sliders, custom targets, NPC rows, and morph-target assignments (`UndoSnapshots.cs:5-265`). `TemplatesViewModel` uses `PresetValueSnapshot` for import/duplicate/remove/clear/profile replay (`TemplatesViewModel.cs:283-302`, `312-354`, `455-471`, `621-638`). `MorphsViewModel` restores custom targets/NPCs and scoped assignments from value snapshots and stable row IDs (`MorphsViewModel.cs:1577-1628`, `1645-1702`). Tests cover detached mutation regressions and one-step scoped bulk undo. |
| 5 | User can operate on large real-world preset and NPC datasets without UI freezes or unbounded filter/import delays. | ✓ VERIFIED (automated) / ? HUMAN | Free-text NPC search is debounced with a 200 ms scheduler (`MorphsViewModel.cs:319-328`), import parsing runs on `Task.Run` (`MorphsViewModel.cs:1066-1069`; `TemplatesViewModel.cs:455-456`), undo history is bounded (`UndoRedoService.cs:5-16`, `82-88`), and tests include large debounced-search/large-list smoke coverage. Real UI responsiveness with large modder datasets still needs human confirmation. |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/BS2BG.App/Services/UserPreferencesService.cs` | Backward-compatible workflow preference DTO and best-effort persistence | ✓ VERIFIED | Substantive load/save implementation with failure fallbacks and all Phase 2 preference fields. |
| `src/BS2BG.App/Services/WindowFileDialogService.cs` | Project/export picker remembered-folder channels | ✓ VERIFIED | Uses channel-specific `SuggestedStartLocation` hints and best-effort saves. |
| `src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs` / `WindowNpcTextFilePicker.cs` | Import picker remembered-folder channels | ✓ VERIFIED | Separate BodySlide XML and NPC text folder channels, invalid paths resolve to no hint. |
| `src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs` | Stable App-layer NPC row identity | ✓ VERIFIED | Generated `Guid RowId`; no Core `Npc` or serialization field added. |
| `src/BS2BG.App/ViewModels/Workflow/NpcFilterState.cs` | Pure filter state/predicate/value-list contracts | ✓ VERIFIED | Covers required columns and pure predicates over `NpcRowViewModel`. |
| `src/BS2BG.App/ViewModels/MorphsViewModel.cs` | Filtering, scoped bulk operations, and snapshot-hardened morph/NPC undo | ✓ VERIFIED | Wired to filter state, scope resolver, stable row IDs, and snapshot helpers. |
| `src/BS2BG.App/Views/MainWindow.axaml(.cs)` | Filter controls, scope selector, and selection/filter glue | ✓ VERIFIED | Required controls/copy present; code-behind limited to view selection forwarding. |
| `src/BS2BG.App/Services/UndoRedoService.cs` | Bounded undo/redo history and prune notification | ✓ VERIFIED | Default 100-entry limit, deterministic pruning, `HistoryPruned` event. |
| `src/BS2BG.App/ViewModels/Workflow/UndoSnapshots.cs` | Value snapshot DTOs/helpers | ✓ VERIFIED | Preset, set-slider, target, NPC, and assignment snapshots implemented. |
| Tests under `tests/BS2BG.Tests/` | Regression coverage for preferences, filtering, scopes, undo, UI shell | ✓ VERIFIED | Full `dotnet test` passed: 286 passed, 0 failed, 0 skipped. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `TemplatesViewModel.cs` | `UserPreferencesService.cs` | `IUserPreferencesService.Load/Save` | ✓ WIRED | Constructor loads preferences; omit-redundant setter saves via service. |
| Picker services | `UserPreferences` folder fields | `ResolveStartFolderAsync`, picker result save | ✓ WIRED | Project, export, BodySlide XML, and NPC text pickers each use separate fields. |
| `MorphsViewModel.cs` | `NpcFilterState.cs` / `NpcRowViewModel.cs` | Stable row maps and predicates | ✓ WIRED | `SetNpcColumnAllowedValues`, search throttles, and refresh paths call filter predicates over row wrappers. |
| `MainWindow.axaml(.cs)` | `MorphsViewModel` filters | Compiled bindings plus selection forwarding | ✓ WIRED | Filter popup controls bind to ViewModel properties/commands; code-behind forwards selected values by `NpcFilterColumn`. |
| `MorphsViewModel.cs` | `NpcBulkScopeResolver.cs` | `Resolve` before mutation | ✓ WIRED | Scoped mutation paths resolve IDs before fill/clear operations. |
| `MainWindowViewModel.cs` | `UndoRedoService.cs` | `HistoryPruned` subscription | ✓ WIRED | Shell status receives exact prune copy. |
| `TemplatesViewModel.cs` / `MorphsViewModel.cs` | `UndoSnapshots.cs` | Value snapshots for replay | ✓ WIRED | Preset/profile/NPC/target/assignment undo paths use snapshot DTOs. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `TemplatesViewModel` | `OmitRedundantSliders` | `preferencesService.Load()` and `Save()` | Yes | ✓ FLOWING |
| Picker services | remembered folders | `UserPreferences` fields + selected file/folder paths | Yes | ✓ FLOWING |
| `MorphsViewModel` | `VisibleNpcs` / `SelectedNpcs` | `project.MorphedNpcs`, `NpcFilterState`, stable row maps | Yes | ✓ FLOWING |
| `MainWindow.axaml` filter UI | filter value lists/badges | `MorphsViewModel.Npc*ColumnValues`, `Npc*FilterBadgeText` | Yes | ✓ FLOWING |
| Scoped bulk operations | target NPC rows | `NpcBulkScopeResolver.Resolve(...)` row ID snapshots | Yes | ✓ FLOWING |
| Undo/redo replay | snapshot DTOs | Operation-time value snapshots and current preset resolution | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Full regression suite | `dotnet test` | Passed: 286 passed, 0 failed, 0 skipped | ✓ PASS |
| Preferences and picker channels | Source/test inspection | Preference DTO fields, picker start-folder/save wiring, and tests found | ✓ PASS |
| Filtering/scoped undo semantics | Source/test inspection | Required code paths and regression tests found | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| WORK-01 | 02-01, 02-02, 02-03 | Restart keeps last-used folders and workflow preferences such as omit-redundant sliders. | ✓ SATISFIED | Local preference DTO and all picker channels verified; project serialization search found no preference fields under Core serialization. |
| WORK-02 | 02-04, 02-05, 02-06 | Filter NPC rows by required fields without losing stable NPC identity. | ✓ SATISFIED | `NpcFilterColumn`, `NpcRowViewModel.RowId`, `MorphsViewModel` filtering, and UI filter popups verified. |
| WORK-03 | 02-07, 02-09 | Explicit all/visible/selected/visible-empty bulk scopes protect hidden rows. | ✓ SATISFIED | Scope resolver, scoped mutation paths, all-scope confirmation, UI scope selector, and tests verified. |
| WORK-04 | 02-08, 02-09 | Undo/redo risky preset, target, NPC assignment, import, clear, and profile operations without live-state corruption. | ✓ SATISFIED | Bounded undo service and value snapshot paths verified across Templates and Morphs workflows. |
| WORK-05 | 02-04, 02-05, 02-07, 02-08, 02-09 | Large datasets avoid UI freezes or unbounded filter/import delays. | ✓ SATISFIED (automated) / ? HUMAN | Debounced search, async import parsing, bounded undo, and smoke tests verified; real-world responsiveness remains manual. |

No orphaned Phase 2 requirement IDs were found: WORK-01 through WORK-05 are all claimed by plan frontmatter and mapped in `.planning/REQUIREMENTS.md`.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---:|---|---|---|
| Multiple picker/backend files | Various | `return null` | ℹ️ Info | Expected cancellation/invalid-hint behavior, not stubs. |
| `src/BS2BG.App/ViewModels/MorphsViewModel.cs` | 1634 | `return null` | ℹ️ Info | Expected non-NPC target row ID absence, not a stub. |

No blocker TODO/placeholder/stub implementations were found in Phase 2 source paths.

### Human Verification Waived

#### 1. Morphs UI filter and scope interaction

**Test:** In the running Avalonia app, open each Morphs NPC filter popup, search values, select/clear values, navigate by keyboard, and change the `Scope` selector.
**Expected:** All seven filters are discoverable and accessible; active badges and filtered-empty text appear; the scope selector labels are `All`, `Visible`, `Selected`, and `Visible Empty`; hidden rows are not implied to be deleted.
**Why human:** Visual layout, keyboard flow, and popup affordances are not fully proven by source/headless tests.

**Waiver:** Maintainer waived this manual check on 2026-04-28.

#### 2. Large real-world dataset responsiveness

**Test:** Import a large NPC text file and many presets, then rapidly type in global search and apply checklist filters.
**Expected:** Search waits for debounce, filtering/import does not visibly freeze the app, and undo history pruning status appears if the limit is exceeded.
**Why human:** Perceived UI responsiveness and real-world dataset size cannot be fully validated by unit tests.

**Waiver:** Maintainer waived this manual check on 2026-04-28.

#### 3. Restart persistence in packaged/runtime environment

**Test:** Change Omit Redundant Sliders and use project/export/import pickers, close BS2BG, reopen it, and repeat with one invalid/moved remembered folder.
**Expected:** Valid remembered channels are reused independently; invalid paths are ignored as hints; workflows continue.
**Why human:** OS storage-provider behavior and actual user-profile persistence are environment-dependent.

**Waiver:** Maintainer waived this manual check on 2026-04-28.

### Gaps Summary

No automated blocker gaps were found. The phase goal is implemented in source and covered by tests. The remaining UI/large-dataset/restart manual checks were waived by the maintainer, so the phase is fully passed for milestone close.

---

_Verified: 2026-04-27T02:20:09Z_
_Verifier: the agent (gsd-verifier)_
