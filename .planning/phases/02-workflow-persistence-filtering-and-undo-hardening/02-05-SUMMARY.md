---
phase: 02-workflow-persistence-filtering-and-undo-hardening
plan: 05
subsystem: app-workflow-filtering
tags: [avalonia, reactiveui, dynamicdata, npc-filtering, selection, debounce, tdd]

requires:
  - phase: 02-workflow-persistence-filtering-and-undo-hardening
    provides: Plan 04 stable NPC row wrappers, NpcFilterState predicates, and DynamicData package wiring
provides:
  - DynamicData SourceCache-backed NPC row identity integration in MorphsViewModel
  - Required NPC filter columns routed through NpcFilterState while preserving public Npc collections
  - Hidden selected NPC preservation by stable row IDs
  - Debounced free-text NPC search with large-dataset regression coverage
affects: [morph-assignment-flow, workflow-persistence-filtering-and-undo-hardening]

tech-stack:
  added: []
  patterns:
    - SourceCache-backed App-layer row sidecars keep Core NPC models unchanged
    - Pending/applied search text debounces free-text filtering while checklist filters apply immediately
    - Visible selection reconciliation preserves hidden selected rows by stable row ID

key-files:
  created: []
  modified:
    - src/BS2BG.App/ViewModels/MorphsViewModel.cs
    - src/BS2BG.App/Views/MainWindow.axaml.cs
    - tests/BS2BG.Tests/MorphsViewModelTests.cs
    - tests/BS2BG.Tests/M6UxViewModelTests.cs

key-decisions:
  - "Preserve existing public ObservableCollection<Npc> surfaces while using SourceCache<NpcRowViewModel, Guid> internally for stable row identity."
  - "Use an injectable IScheduler for NPC search debounce tests because ReactiveUI 23 in this project no longer exposes RxApp directly."

patterns-established:
  - "MorphsViewModel maps mutable Core NPC objects to stable App-layer row wrappers without serializing UI identity."
  - "MainWindow selection changes call a ViewModel reconciliation method instead of replacing SelectedNpcs directly."

requirements-completed: [WORK-02, WORK-05]

duration: 24 min
completed: 2026-04-27
---

# Phase 02 Plan 05: Stable Debounced Morphs NPC Filtering Summary

**MorphsViewModel now filters NPCs through stable keyed row wrappers, preserves hidden selections, and debounces large free-text searches.**

## Performance

- **Duration:** 24 min
- **Started:** 2026-04-27T00:26:48Z
- **Completed:** 2026-04-27T00:50:00Z
- **Tasks:** 2 completed
- **Files modified:** 4

## Accomplishments

- Integrated Plan 04 `NpcRowViewModel` and `NpcFilterState` into `MorphsViewModel` with `SourceCache<NpcRowViewModel, Guid>` sidecars for morphed NPCs and imported NPC database rows.
- Replaced Morphs ViewModel's local filter enum/manual column predicate path with the shared required filter dimensions: mod, name, editor ID, form ID, race, assignment state, and preset.
- Preserved existing public `ObservableCollection<Npc>` binding/command surfaces while filtering visible projections from stable row wrappers.
- Added hidden-selection preservation through stable row IDs and updated MainWindow selection forwarding to reconcile visible-only selections without dropping hidden rows.
- Added debounced global NPC search and large-dataset smoke coverage proving typed search waits for the debounce window before rebuilding visible rows.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: NPC filter integration coverage** - `817d66ee` (test)
2. **Task 1 GREEN: Keyed row filter pipeline** - `7ef9ba7a` (feat)
3. **Task 2 RED: Hidden selection/debounce coverage** - `488ee806` (test)
4. **Task 2 GREEN: Hidden selection and debounce implementation** - `a8242922` (feat)

**Plan metadata:** committed after this summary.

## Files Created/Modified

- `src/BS2BG.App/ViewModels/MorphsViewModel.cs` - Added SourceCache-backed NPC row sidecars, shared `NpcFilterState` filtering, hidden selection row ID tracking, and debounced search scheduling.
- `src/BS2BG.App/Views/MainWindow.axaml.cs` - Routes visible NPC selection changes through `UpdateVisibleNpcSelection` so hidden selected rows survive filtering.
- `tests/BS2BG.Tests/MorphsViewModelTests.cs` - Added required column filter integration, hidden-selection preservation, and large debounced-search smoke tests.
- `tests/BS2BG.Tests/M6UxViewModelTests.cs` - Updated filter enum import to the shared workflow filter contract.

## Decisions Made

- Preserved `VisibleNpcs`, `VisibleNpcDatabase`, and `SelectedNpcs` as public `ObservableCollection<Npc>` properties to avoid deliberate binding churn before Plan 06 UI work.
- Kept generated row IDs in App-layer wrappers only; Core `Npc`, project serialization, and generation/export models remain unchanged.
- Used an injectable `IScheduler` debounce seam for tests because the project's ReactiveUI 23 setup uses `RxAppBuilder` services and does not expose the older `RxApp` static API.

## Deviations from Plan

None - plan executed as written.

**Total deviations:** 0 auto-fixed.
**Impact on plan:** No scope changes; implementation remained within Morphs ViewModel filtering, MainWindow selection forwarding, and focused tests.

## Issues Encountered

- Task 1 RED initially surfaced the expected incompatibility between the old Morphs filter enum and the new workflow `NpcFilterColumn` contract.
- The Avalonia documentation migration tool was consulted per session instructions, but no diagnostics migration changes were made because this plan did not modify DevTools setup.

## TDD Gate Compliance

- RED commits present before GREEN for both tasks: `817d66ee`, `488ee806`.
- GREEN commits present after RED for both tasks: `7ef9ba7a`, `a8242922`.
- No refactor commit was needed.

## Verification

- `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` — PASSED (18 tests).
- `dotnet test --filter "FullyQualifiedName~MorphsViewModelTests&FullyQualifiedName~Large"` — PASSED (1 test).
- `dotnet test --filter FullyQualifiedName~NpcFilterStateTests` — PASSED (7 tests).
- Related coverage: `dotnet test --filter "FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~M6UxViewModelTests"` — PASSED (26 tests).
- Note: test runs still report pre-existing analyzer warnings in `ExportWriterTests.cs`; they are outside this plan's files and were not modified.

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 02-06 to expose the full checklist filtering UI over the shared Morphs ViewModel filter contract while preserving the stable row and hidden-selection behavior added here.

## Self-Check: PASSED

- Verified key files exist: `MorphsViewModel.cs`, `NpcFilterState.cs`, and `MorphsViewModelTests.cs`.
- Verified task commits exist: `817d66ee`, `7ef9ba7a`, `488ee806`, and `a8242922`.

---
*Phase: 02-workflow-persistence-filtering-and-undo-hardening*
*Completed: 2026-04-27*
