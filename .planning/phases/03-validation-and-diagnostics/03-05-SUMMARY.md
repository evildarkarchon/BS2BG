---
phase: 03-validation-and-diagnostics
plan: 05
subsystem: import-diagnostics-ui
tags: [npc-import, preview, morphs-viewmodel, reactiveui, tdd]

requires:
  - phase: 03-validation-and-diagnostics
    provides: [Core NPC import preview service from plan 03-02, Morphs diagnostics context]
  - phase: 02-workflow-persistence-filtering-and-undo-hardening
    provides: [stable NPC row identity and scoped bulk operation resolver]
provides:
  - Optional no-mutation NPC import preview command in MorphsViewModel
  - Binding-ready NPC import preview rows with duplicate, issue, and fallback encoding copy
  - Explicit preview commit command that imports NPC database rows without assigning presets
  - Assignment effect summary counts for scoped Morphs commands
affects: [validation-and-diagnostics, morph-assignment-flow, diagnostics-ui]

tech-stack:
  added: []
  patterns:
    - ReactiveCommand preview/commit split over read-only Core preview service
    - App-layer preview rows keep import effects distinct from assignment effects

key-files:
  created:
    - src/BS2BG.App/ViewModels/Workflow/NpcImportPreviewViewModel.cs
  modified:
    - src/BS2BG.App/ViewModels/MorphsViewModel.cs
    - src/BS2BG.App/AppBootstrapper.cs
    - tests/BS2BG.Tests/MorphsViewModelTests.cs

key-decisions:
  - "Keep NPC import preview optional and no-mutation; direct import remains available through the existing ImportNpcsCommand path."
  - "Commit previewed NPC rows through the existing AddNpcsToDatabase duplicate policy and keep preset assignment as a separate Morphs action."
  - "Expose assignment effect summaries from scoped command results instead of changing assignment algorithms or random-provider behavior."

patterns-established:
  - "PreviewNpcImportCommand populates temporary preview rows; ImportPreviewedNpcsCommand is the only preview path that mutates the NPC database."
  - "Assignment effect summaries report before/after assigned-target counts for scoped bulk operations."

requirements-completed: [DIAG-03]

duration: 5 min
completed: 2026-04-27
---

# Phase 03 Plan 05: NPC Import Preview Workflow Summary

**Morphs workflow now supports optional no-mutation NPC import previews, explicit database-only preview commits, and separate assignment-effect summaries.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-27T04:08:15Z
- **Completed:** 2026-04-27T04:13:14Z
- **Tasks:** 2 completed
- **Files modified:** 4

## Accomplishments

- Added `NpcImportPreviewViewModel` rows for addable NPCs, existing duplicates, parser diagnostics, and fallback encoding cautions.
- Wired `PreviewNpcImportCommand` through `NpcImportPreviewService` without mutating `NpcDatabase`, `VisibleNpcDatabase`, `MorphedNpcs`, project dirty/change state, or undo history.
- Registered `NpcImportPreviewService` in `AppBootstrapper` while preserving the existing direct `ImportNpcsCommand` path.
- Added `ImportPreviewedNpcsCommand` to commit only addable preview rows through the existing database duplicate policy, with status copy stating that import preview does not assign presets.
- Added `LastAssignmentEffectSummary` for scoped fill, clear-assignment, and clear-NPC operations so import effects and assignment effects remain distinct.

## Task Commits

Each task was committed atomically with TDD gates:

1. **Task 1 RED: Preview command no-mutation coverage** - `f28a4d57` (test)
2. **Task 1 GREEN: Optional NPC import preview command** - `411263d0` (feat)
3. **Task 2 RED: Preview commit and assignment-effect coverage** - `2dde52b9` (test)
4. **Task 2 GREEN: Explicit preview commit and assignment summaries** - `ca3ce83b` (feat)

**Plan metadata:** pending final docs commit.

## Files Created/Modified

- `src/BS2BG.App/ViewModels/Workflow/NpcImportPreviewViewModel.cs` - Binding-ready preview row DTO for addable NPCs, duplicates, parser issues, and fallback encoding cautions.
- `src/BS2BG.App/ViewModels/MorphsViewModel.cs` - Adds preview/commit commands, preview summary state, preview row collection, and assignment-effect summary state.
- `src/BS2BG.App/AppBootstrapper.cs` - Registers `NpcImportPreviewService` for App DI.
- `tests/BS2BG.Tests/MorphsViewModelTests.cs` - Adds no-mutation preview, DI registration, explicit commit, and assignment-effect summary tests.

## Decisions Made

- Kept preview as an optional Morphs command, not a replacement for direct import, to preserve D-10 and existing import behavior.
- Used `AddNpcsToDatabase` for preview commits so duplicate handling stays shared with direct import.
- Kept assignment effect reporting in App-layer scoped command paths because effects depend on visible/selected row scope from `NpcBulkScopeResolver`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used supported dotnet test invocation**
- **Found during:** Task verification
- **Issue:** The planned `dotnet test --filter FullyQualifiedName~MorphsViewModelTests -x` command is unsupported in this environment, consistent with prior Phase 3 plans.
- **Fix:** Ran the same focused test filter without `-x`.
- **Files modified:** None
- **Verification:** `dotnet test --filter "FullyQualifiedName~MorphsViewModelTests"` passed.
- **Committed in:** N/A (command adjustment only)

---

**Total deviations:** 1 auto-fixed (1 blocking command adjustment)
**Impact on plan:** Verification coverage was preserved; implementation scope remained unchanged.

## Issues Encountered

- The first preview no-mutation test needed to mark its seeded project clean before asserting preview preserved dirty state; the implementation still preserves the clean baseline during preview.
- Existing analyzer warnings from prior diagnostics files/tests appeared during focused `dotnet test`; they are outside this plan's files and did not fail the suite.

## Known Stubs

None. The scan found an existing exception message containing "not available" for missing undo row identity; it is an error message, not a UI stub or placeholder.

## Threat Flags

None. The plan threat model already covered the local NPC file preview boundary, explicit preview-to-project commit boundary, and fallback encoding status surface.

## TDD Gate Compliance

- RED commits present: `f28a4d57`, `2dde52b9`
- GREEN commits present after RED: `411263d0`, `ca3ce83b`
- Refactor commit: not needed

## Validation Performed

- `dotnet test --filter "FullyQualifiedName~MorphsViewModelTests"` — passed (41 tests).
- Acceptance checks confirmed `PreviewNpcImportCommand`, `ImportPreviewedNpcsCommand`, `LastAssignmentEffectSummary`, `NpcImportPreviewService` DI registration, no-mutation `NpcDatabase.Count` assertions, and status copy containing `does not assign presets`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 03-06 can build export preview confirmation flows with the same read-only-preview/explicit-commit separation. Plan 03-08 can wire the Morphs preview rows into AXAML knowing the ViewModel state and commands are already tested.

## Self-Check: PASSED

- Verified key files exist: `NpcImportPreviewViewModel.cs`, `MorphsViewModel.cs`, `AppBootstrapper.cs`, and `MorphsViewModelTests.cs`.
- Verified task commits exist: `f28a4d57`, `411263d0`, `2dde52b9`, and `ca3ce83b`.

---
*Phase: 03-validation-and-diagnostics*
*Completed: 2026-04-27*
