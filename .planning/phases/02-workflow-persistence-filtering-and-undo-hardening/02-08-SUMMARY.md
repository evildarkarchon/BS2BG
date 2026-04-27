---
phase: 02-workflow-persistence-filtering-and-undo-hardening
plan: 08
subsystem: app-undo
tags: [avalonia, reactiveui, undo-redo, workflow-state, tdd]

requires:
  - phase: 02-workflow-persistence-filtering-and-undo-hardening
    provides: Local workflow preference persistence and existing template workflow undo paths
provides:
  - Bounded undo/redo operation history with deterministic pruning
  - Non-blocking shell status when undo history is trimmed
  - Value snapshot helpers for template preset undo/redo operations
  - Snapshot-hardened template import, duplicate, remove, clear, bulk slider, and profile replay paths
affects: [template-generation-flow, workflow-persistence, undo-redo]

tech-stack:
  added: []
  patterns:
    - Operation-count bounded UndoRedoService history
    - App-layer preset value snapshots for undo replay
    - Shell status subscription for non-blocking undo-prune feedback

key-files:
  created:
    - src/BS2BG.App/ViewModels/Workflow/UndoSnapshots.cs
  modified:
    - src/BS2BG.App/Services/UndoRedoService.cs
    - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - tests/BS2BG.Tests/TemplatesViewModelTests.cs
    - tests/BS2BG.Tests/M6UxViewModelTests.cs

key-decisions:
  - "Use a deterministic 100-operation default undo history limit, with constructor injection for focused tests."
  - "Restore template preset undo paths from value snapshots instead of replaying removed/cleared live preset references."

patterns-established:
  - "UndoRedoService emits HistoryPruned separately from StateChanged so shell status can report pruning without changing command availability semantics."
  - "PresetValueSnapshot recreates fresh SliderPreset instances for collection restore and can apply captured values to an existing selected preset for profile replay."

requirements-completed: [WORK-04, WORK-05]

duration: 67min
completed: 2026-04-27
---

# Phase 02 Plan 08: Undo History and Template Snapshot Hardening Summary

**Undo/redo now prunes old operations deterministically and template preset/profile replay uses captured values instead of mutable live references.**

## Performance

- **Duration:** 67 min
- **Started:** 2026-04-27T00:26:49Z
- **Completed:** 2026-04-27T01:33:39Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Added a bounded `UndoRedoService` history with configurable operation limit, deterministic oldest-entry pruning, redo clearing on new records, and replay-guard preservation.
- Routed undo history pruning to `MainWindowViewModel.StatusMessage` with the required UI-SPEC copy: `Undo history trimmed to keep large workflows responsive.`
- Added `PresetValueSnapshot` and `SetSliderValueSnapshot` helpers under the App workflow layer to snapshot preset names, profile names, set slider values, missing-default sliders, and restore fresh model instances.
- Hardened template undo paths for import, duplicate/remove, clear presets, and profile selection changes so replay uses captured values and keeps preview/dirty-related refresh behavior in sync.
- Added TDD regression coverage for pruning semantics, prune status feedback, mutable removed/cleared reference corruption, and profile selection undo/redo.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add bounded undo history and pruning status event**
   - `8f75abea` (test): add failing bounded undo history tests
   - `bcec9648` (feat): bound undo history
2. **Task 2: Snapshot-harden preset and profile undo paths**
   - `470d64ba` (test): add failing template undo snapshot tests
   - `bcf81d34` (feat): harden template undo snapshots

**Plan metadata:** this summary commit.

_Note: TDD tasks produced RED and GREEN commits._

## Files Created/Modified

- `src/BS2BG.App/Services/UndoRedoService.cs` - Adds operation-count history limit, oldest-entry pruning, and `HistoryPruned` notification.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Subscribes to prune notifications and updates shell status with required non-blocking copy.
- `src/BS2BG.App/ViewModels/Workflow/UndoSnapshots.cs` - Adds value snapshot DTOs/helpers for set sliders and presets.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` - Uses preset value snapshots for template import, duplicate/remove, clear, and profile selection undo/redo paths.
- `tests/BS2BG.Tests/M6UxViewModelTests.cs` - Covers bounded undo history, redo clearing, and prune status routing.
- `tests/BS2BG.Tests/TemplatesViewModelTests.cs` - Covers mutable-reference corruption regressions and profile undo/redo replay.

## Decisions Made

- Chose a deterministic 100-operation default for bounded undo history, with constructor injection for tests and future tuning.
- Kept snapshot helpers in `BS2BG.App.ViewModels.Workflow` because they serve App workflow replay and should not affect Core output semantics or project serialization.
- Used value snapshots rather than full project serialization to satisfy D-13 while avoiding `.jbs2bg` schema or byte-output changes.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- RED-phase tests failed as expected before implementation: missing `historyLimit`, live-reference restore corruption, and missing profile selection undo records.
- Targeted test runs still report an existing `CA1861` warning in `tests/BS2BG.Tests/ExportWriterTests.cs`; it is pre-existing and outside this plan's modified files.

## Verification

- `dotnet test --filter FullyQualifiedName~M6UxViewModelTests` — passed (10 tests).
- `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` — passed (22 tests).
- `dotnet test --filter "FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~M6UxViewModelTests"` — passed (32 tests).

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## TDD Gate Compliance

- Task 1 RED commit: `8f75abea`; GREEN commit: `bcec9648`.
- Task 2 RED commit: `470d64ba`; GREEN commit: `bcf81d34`.
- No TDD gate violations.

## Next Phase Readiness

Ready for Phase 02 Plan 09 to extend the same bounded undo and value snapshot principles into morph/NPC undo and scoped bulk behavior.

## Self-Check: PASSED

- Verified `src/BS2BG.App/ViewModels/Workflow/UndoSnapshots.cs` exists.
- Verified task commits are present in git history: `8f75abea`, `bcec9648`, `470d64ba`, `bcf81d34`.
- Verified no `STATE.md` or `ROADMAP.md` changes are present in this parallel executor worktree.

---
*Phase: 02-workflow-persistence-filtering-and-undo-hardening*
*Completed: 2026-04-27*
