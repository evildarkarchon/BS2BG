---
phase: 02-workflow-persistence-filtering-and-undo-hardening
plan: 04
subsystem: app-workflow-filtering
tags: [avalonia, reactiveui, dynamicdata, npc-filtering, tdd]

requires:
  - phase: 01-profile-correctness-and-trust
    provides: bundled profile correctness and stable existing App/Core boundaries
provides:
  - Stable App-layer NPC row identity wrapper
  - Pure NPC checklist and global-search filter predicate contracts
  - Central DynamicData package reference for later Morphs ViewModel integration
affects: [morph-assignment-flow, workflow-persistence-filtering-and-undo-hardening]

tech-stack:
  added: [DynamicData 9.4.31]
  patterns:
    - App-layer row wrapper keeps UI identity out of Core serialization
    - Pending/applied global search split supports debounced ViewModel text filtering
    - Checklist filters build side-effect-free predicates over stable row wrappers

key-files:
  created:
    - src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs
    - src/BS2BG.App/ViewModels/Workflow/NpcFilterState.cs
    - tests/BS2BG.Tests/NpcFilterStateTests.cs
  modified:
    - Directory.Packages.props
    - src/BS2BG.App/BS2BG.App.csproj

key-decisions:
  - "Keep generated NPC row IDs in the App workflow wrapper instead of Core Npc so project serialization and BodyGen output remain unchanged."
  - "Split pending and applied global search text in NpcFilterState so MorphsViewModel can debounce typing while checklist changes apply immediately."

patterns-established:
  - "NpcRowViewModel exposes stable RowId plus live convenience accessors over the mutable Core NPC."
  - "NpcFilterState creates pure Func<NpcRowViewModel, bool> predicates and distinct sorted value lists for filter popups."

requirements-completed: [WORK-02, WORK-05]

duration: 4 min
completed: 2026-04-27
---

# Phase 02 Plan 04: Stable NPC Row Identity and Filter Contracts Summary

**DynamicData-backed workflow groundwork with App-layer NPC row identity and pure checklist/global-search filter predicates.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-27T00:20:40Z
- **Completed:** 2026-04-27T00:24:13Z
- **Tasks:** 2 completed
- **Files modified:** 5

## Accomplishments

- Added `DynamicData` 9.4.31 to central package management and referenced it from the App project for later keyed reactive collection integration.
- Created `NpcRowViewModel` with generated stable `Guid RowId` values independent of mutable NPC display/export fields and without modifying Core `Npc` serialization.
- Created `NpcFilterState` and `NpcFilterColumn` covering mod, name, editor ID, form ID, race, assignment state, and preset values with pure predicates and distinct available-value lists.
- Added focused TDD coverage for row identity stability, Core serialization isolation, checklist filters, applied-vs-pending global search, assignment state, preset matching, and large-list side-effect safety.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: NPC row identity tests** - `5b41bea7` (test)
2. **Task 1 GREEN: Stable NPC row identity wrapper** - `ac571cc0` (feat)
3. **Task 2 RED: NPC filter contract tests** - `56231d6a` (test)
4. **Task 2 RED refinement: Global search test token coverage** - `293e2351` (test)
5. **Task 2 GREEN: NPC filter predicate contracts** - `dfa7b8d1` (feat)

**Plan metadata:** committed after this summary.

## Files Created/Modified

- `Directory.Packages.props` - Added central `DynamicData` 9.4.31 package version.
- `src/BS2BG.App/BS2BG.App.csproj` - Referenced `DynamicData` from the App project.
- `src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs` - New App-layer NPC wrapper with stable generated row identity and live filter-facing accessors.
- `src/BS2BG.App/ViewModels/Workflow/NpcFilterState.cs` - New filter state, column enum, checklist state, pending/applied global search, predicate builder, and value-list helper.
- `tests/BS2BG.Tests/NpcFilterStateTests.cs` - New TDD coverage for row identity and filter contracts.

## Decisions Made

- Kept row identity in `BS2BG.App.ViewModels.Workflow.NpcRowViewModel` rather than adding `RowId` to Core `Npc`, preserving `.jbs2bg` serialization and output semantics.
- Modeled global text search as pending/applied state so the future Morphs ViewModel can debounce typed search while checklist changes still affect predicates immediately.
- Represented assignment filter values as stable labels (`Assigned`, `Empty`) to make popup values and predicates testable without binding to Avalonia controls.

## Deviations from Plan

None - plan executed as written.

**Total deviations:** 0 auto-fixed.
**Impact on plan:** No scope changes; all implementation stayed within App workflow helpers, package wiring, and focused tests.

## Issues Encountered

- The first RED test pass lacked an explicit `Xunit` using and failed before reaching the intended missing-wrapper error; the test was corrected before committing the RED gate.
- Running `dotnet test` and `dotnet build` concurrently caused an App project output file lock during the test command. The build succeeded, and the filtered test command passed when rerun sequentially.

## TDD Gate Compliance

- RED commits present before GREEN for both tasks: `5b41bea7`, `56231d6a`, and `293e2351`.
- GREEN commits present after RED for both tasks: `ac571cc0` and `dfa7b8d1`.
- No refactor commit was needed.

## Verification

- `dotnet test --filter FullyQualifiedName~NpcFilterStateTests` — PASSED (7 tests).
- `dotnet build BS2BG.sln` — PASSED (2 pre-existing analyzer warnings in `ExportWriterTests.cs`, unrelated to this plan).

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 02-05 to integrate stable, debounced NPC filtering into `MorphsViewModel` using the row wrappers and pure filter predicates created here.

## Self-Check: PASSED

- Verified created files exist: `NpcRowViewModel.cs`, `NpcFilterState.cs`, `NpcFilterStateTests.cs`, and this summary.
- Verified task commits exist: `5b41bea7`, `ac571cc0`, `56231d6a`, `293e2351`, and `dfa7b8d1`.

---
*Phase: 02-workflow-persistence-filtering-and-undo-hardening*
*Completed: 2026-04-27*
