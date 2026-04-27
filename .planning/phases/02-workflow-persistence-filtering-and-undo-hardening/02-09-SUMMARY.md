---
phase: 02-workflow-persistence-filtering-and-undo-hardening
plan: 09
subsystem: app-undo
tags: [avalonia, reactiveui, undo-redo, npc-bulk-scope, tdd]

requires:
  - phase: 02-workflow-persistence-filtering-and-undo-hardening
    provides: Plans 07/08 scoped NPC bulk operations, bounded undo history, and shared template snapshot helpers
provides:
  - Snapshot DTOs for custom targets, NPC rows, and target assignment replay
  - Morphs ViewModel undo paths that restore removed targets/NPCs from value snapshots
  - Scoped bulk assignment undo that resolves target rows by stable IDs and preset names
  - Regression tests for detached live-reference mutation and scoped bulk undo behavior
affects: [morph-assignment-flow, workflow-persistence-filtering-and-undo-hardening, undo-redo]

tech-stack:
  added: []
  patterns:
    - App-layer undo snapshots capture scalar values and preset names rather than mutable target/NPC references
    - NPC row replay preserves stable row IDs when recreating removed rows
    - Scoped assignment replay skips missing target rows and resolves assignments against current preset names

key-files:
  created: []
  modified:
    - src/BS2BG.App/ViewModels/Workflow/UndoSnapshots.cs
    - src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs
    - src/BS2BG.App/ViewModels/MorphsViewModel.cs
    - tests/BS2BG.Tests/MorphsViewModelTests.cs

key-decisions:
  - "Keep snapshot helpers in the App workflow layer so undo hardening does not affect Core output or project serialization semantics."
  - "Resolve assignment replay by stable NPC row ID or custom-target name, and by current preset names, so replay does not attach renamed live preset references."

patterns-established:
  - "Removed NPC undo recreates a fresh Npc from captured values and reattaches the original App row ID for future scoped undo/redo."
  - "Bulk assignment undo stores one operation with before/after assignment snapshots for all scoped target rows."

requirements-completed: [WORK-03, WORK-04, WORK-05]

duration: 48min
completed: 2026-04-27
---

# Phase 02 Plan 09: Morph/NPC Undo Snapshot Hardening Summary

**Morph and NPC undo/redo now replays removed rows and scoped bulk assignments from stable IDs and value snapshots instead of mutable live references.**

## Performance

- **Duration:** 48 min
- **Started:** 2026-04-27T02:02:00Z
- **Completed:** 2026-04-27T02:50:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Added value snapshot DTOs for custom morph targets, NPC rows, and morph-target assignment state.
- Reworked removed custom-target and NPC undo/redo paths to recreate fresh model instances from operation-time values.
- Preserved App-layer NPC row IDs when removed NPCs are restored so subsequent scoped undo/redo still resolves the intended row.
- Reworked scoped bulk assignment undo/redo to snapshot target row identity and preset names once per bulk command.
- Added TDD regressions for detached live-reference mutation, preset-name replay, hidden-row protection, and one undo entry per scoped bulk operation.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Snapshot-harden target and NPC row operations tests** - `1cb8d2d4` (test)
2. **Task 1 GREEN: Snapshot-harden target and NPC row operations** - `d1c9375a` (feat)
3. **Task 2 RED: Scoped bulk undo snapshot test** - `1438e5ca` (test)
4. **Task 2 GREEN: Snapshot-harden scoped assignment bulk operations** - `7c8f3dc2` (feat)

**Plan metadata:** this summary commit.

_Note: Both TDD tasks produced RED and GREEN commits._

## Files Created/Modified

- `src/BS2BG.App/ViewModels/Workflow/UndoSnapshots.cs` - Adds custom-target, NPC row, and morph-target assignment value snapshots.
- `src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs` - Allows restored rows to keep their original generated row ID.
- `src/BS2BG.App/ViewModels/MorphsViewModel.cs` - Restores removed targets/NPCs and scoped assignment changes from value snapshots.
- `tests/BS2BG.Tests/MorphsViewModelTests.cs` - Adds regression coverage for detached mutations and scoped bulk assignment undo semantics.

## Decisions Made

- Kept snapshot helpers in `BS2BG.App.ViewModels.Workflow`, matching Plan 08 and avoiding Core model or `.jbs2bg` schema changes.
- Recreated removed custom targets/NPCs from snapshots rather than reusing detached live instances, so post-removal mutations cannot corrupt replay.
- Resolved assignment snapshots by current preset names; if a captured preset name no longer exists, replay skips it instead of attaching a renamed live preset reference.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- RED-phase tests failed as expected before implementation: detached custom target/NPC mutations affected undo replay, and scoped bulk assignment undo restored a renamed live preset reference.
- Targeted and full test runs continue to report the pre-existing `CA1861` warning in `tests/BS2BG.Tests/ExportWriterTests.cs`; it is outside this plan's modified files.

## Verification

- `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` — passed (28 tests after Task 1 GREEN).
- `dotnet test --filter "FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~M6UxViewModelTests"` — passed (39 tests).
- `dotnet test` — passed (283 tests).

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## TDD Gate Compliance

- Task 1 RED commit: `1cb8d2d4`; GREEN commit: `d1c9375a`.
- Task 2 RED commit: `1438e5ca`; GREEN commit: `7c8f3dc2`.
- No TDD gate violations.

## Next Phase Readiness

Phase 02 workflow persistence, filtering, scoped bulk operations, and undo hardening are ready for orchestrator merge and phase verification.

## Self-Check: PASSED

- Verified key files exist: `UndoSnapshots.cs`, `NpcRowViewModel.cs`, `MorphsViewModel.cs`, and `MorphsViewModelTests.cs`.
- Verified task commits exist: `1cb8d2d4`, `d1c9375a`, `1438e5ca`, and `7c8f3dc2`.
- Verified no `STATE.md` or `ROADMAP.md` changes are present in this parallel executor worktree.

---
*Phase: 02-workflow-persistence-filtering-and-undo-hardening*
*Completed: 2026-04-27*
