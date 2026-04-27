---
phase: 02-workflow-persistence-filtering-and-undo-hardening
plan: 07
subsystem: app-workflow-bulk-scopes
tags: [avalonia, reactiveui, npc-bulk-scope, undo, filtering, tdd]

requires:
  - phase: 02-workflow-persistence-filtering-and-undo-hardening
    provides: Plan 05/06 stable NPC filtering, hidden selection preservation, and Morphs checklist UI
provides:
  - Centralized NPC bulk scope resolver with materialized row ID snapshots
  - Scope-aware Morphs bulk commands for visible, selected, all, and visible-empty targets
  - Destructive all-scope confirmation for assignment/NPC clearing
  - Morphs UI Scope selector and Fill Visible Empty primary CTA
affects: [morph-assignment-flow, workflow-persistence-filtering-and-undo-hardening]

tech-stack:
  added: []
  patterns:
    - Scope resolver snapshots stable row IDs before ViewModel mutations
    - Destructive all-scope actions use App dialog confirmation while routine scoped edits rely on labels and undo
    - Enum display conversion keeps command state typed while showing UI-SPEC labels

key-files:
  created:
    - src/BS2BG.App/ViewModels/Workflow/NpcBulkScopeResolver.cs
    - src/BS2BG.App/Services/NpcBulkScopeDisplayConverter.cs
  modified:
    - src/BS2BG.App/ViewModels/MorphsViewModel.cs
    - src/BS2BG.App/Services/IAppDialogService.cs
    - src/BS2BG.App/Services/WindowAppDialogService.cs
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - src/BS2BG.App/Views/MainWindow.axaml
    - tests/BS2BG.Tests/MorphsViewModelTests.cs
    - tests/BS2BG.Tests/M6UxAppShellTests.cs
    - tests/BS2BG.Tests/MainWindowViewModelTests.cs
    - tests/BS2BG.Tests/M6UxViewModelTests.cs

key-decisions:
  - "Keep bulk scope command state as NpcBulkScope enum values and use a display converter only for UI labels."
  - "Extend the existing app dialog service for destructive all-scope confirmation instead of adding Morphs-specific modal infrastructure."

patterns-established:
  - "Bulk mutations resolve stable NPC row IDs once, then map those IDs back to NPC models in backing collection order."
  - "Fill Visible Empty always targets visible unassigned rows, while routine bulk commands use the selected scope and default to Visible when filters activate."

requirements-completed: [WORK-03, WORK-05]

duration: 8 min
completed: 2026-04-27
---

# Phase 02 Plan 07: Scoped NPC Bulk Operations Summary

**NPC bulk operations now resolve explicit All, Visible, Selected, and Visible Empty scopes before mutation, with UI scope selection and destructive all-scope confirmation.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-04-27T01:51:24Z
- **Completed:** 2026-04-27T01:58:58Z
- **Tasks:** 3 completed
- **Files modified:** 11

## Accomplishments

- Added `NpcBulkScopeResolver` and `NpcBulkScope` to centralize all/visible/selected/visible-empty row ID materialization.
- Routed Morphs bulk fill/clear operations through scoped target resolution so hidden filtered rows are protected from visible/visible-empty actions.
- Added destructive all-scope confirmation copy for clearing all NPC assignments and all NPC rows.
- Added one `Scope` selector in the Morphs toolbar and updated the primary random-fill action to `Fill Visible Empty`.
- Added TDD coverage for resolver semantics, command scoping, confirmation/undo behavior, and headless UI copy.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Resolver scope tests** - `41eea5d9` (test)
2. **Task 1 GREEN: Centralized resolver** - `74a95b3b` (feat)
3. **Task 2 RED: Scoped command tests** - `024f5057` (test)
4. **Task 2 GREEN: Scoped commands and confirmation** - `8b63cf90` (feat)
5. **Task 3 RED: Scope selector UI test** - `b2760cab` (test)
6. **Task 3 GREEN: Scope selector UI** - `6502c7f9` (feat)

**Plan metadata:** committed after this summary.

## Files Created/Modified

- `src/BS2BG.App/ViewModels/Workflow/NpcBulkScopeResolver.cs` - Defines scope enum, display labels, and materialized row ID resolver.
- `src/BS2BG.App/Services/NpcBulkScopeDisplayConverter.cs` - Converts enum scopes to exact UI labels for the ComboBox.
- `src/BS2BG.App/ViewModels/MorphsViewModel.cs` - Adds selected scope state and scoped fill/clear command paths.
- `src/BS2BG.App/Services/IAppDialogService.cs` - Adds reusable bulk-operation confirmation contract.
- `src/BS2BG.App/Services/WindowAppDialogService.cs` - Implements confirmation dialog for destructive all-scope operations.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Updates null dialog implementation for the expanded interface.
- `src/BS2BG.App/Views/MainWindow.axaml` - Adds the Scope selector and Fill Visible Empty copy.
- `tests/BS2BG.Tests/MorphsViewModelTests.cs` - Covers resolver, scoped command behavior, confirmation, and undo.
- `tests/BS2BG.Tests/M6UxAppShellTests.cs` - Covers scope selector and CTA UI.
- `tests/BS2BG.Tests/MainWindowViewModelTests.cs` / `tests/BS2BG.Tests/M6UxViewModelTests.cs` - Update test dialog fakes for the expanded service interface.

## Decisions Made

- Kept scope state typed as `NpcBulkScope` rather than strings so command logic remains compile-time checked.
- Used the existing `IAppDialogService` boundary for destructive all-scope confirmation because modal UI belongs in App services, not the Morphs ViewModel.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added dialog-service confirmation contract**
- **Found during:** Task 2 (Apply scopes to bulk commands and confirmation behavior)
- **Issue:** The existing dialog service only supported discard-changes confirmation, but D-12/T-02-07-02 required destructive all-scope bulk confirmation.
- **Fix:** Added `ConfirmBulkOperationAsync` to `IAppDialogService`, implemented it in `WindowAppDialogService`, and updated null/test fakes.
- **Files modified:** `IAppDialogService.cs`, `WindowAppDialogService.cs`, `MainWindowViewModel.cs`, related tests.
- **Verification:** `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` passed.
- **Committed in:** `8b63cf90`

---

**Total deviations:** 1 auto-fixed (1 missing critical).
**Impact on plan:** Required for the threat-model mitigation and UI-SPEC confirmation behavior; no extra product scope was added.

## Issues Encountered

- New tests and targeted runs continue to report pre-existing `CA1861` warnings in `ExportWriterTests.cs`; this file is outside the plan scope and was not modified.

## TDD Gate Compliance

- RED commits present before GREEN for all tasks: `41eea5d9`, `024f5057`, `b2760cab`.
- GREEN commits present after RED for all tasks: `74a95b3b`, `8b63cf90`, `6502c7f9`.
- No refactor commit was needed.

## Verification

- `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` — PASSED (26 tests).
- `dotnet test --filter FullyQualifiedName~M6UxAppShellTests` — PASSED (9 tests).
- `dotnet test --filter "FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~M6UxAppShellTests"` — PASSED (35 tests).

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 02-09 to harden morph/NPC undo snapshots further using the scoped bulk operation behavior added here.

## Self-Check: PASSED

- Verified key implementation files exist: `NpcBulkScopeResolver.cs`, `NpcBulkScopeDisplayConverter.cs`, `MorphsViewModel.cs`, and `MainWindow.axaml`.
- Verified plan commits exist: `41eea5d9`, `74a95b3b`, `024f5057`, `8b63cf90`, `b2760cab`, and `6502c7f9`.

---
*Phase: 02-workflow-persistence-filtering-and-undo-hardening*
*Completed: 2026-04-27*
