---
phase: 02-workflow-persistence-filtering-and-undo-hardening
plan: 06
subsystem: avalonia-ui-filtering
tags: [avalonia, reactiveui, npc-filtering, compiled-bindings, headless-ui, tdd]

requires:
  - phase: 02-workflow-persistence-filtering-and-undo-hardening
    provides: Plan 05 stable MorphsViewModel NPC filter state, row identity, hidden selection preservation, and debounced search
provides:
  - Full Morphs UI checklist filter popups for mod, name, editor ID, form ID, race, assignment state, and preset values
  - Active checklist filter badge copy and accent resources
  - Filtered-empty NPC list state with user-facing recovery copy
affects: [morph-assignment-flow, workflow-persistence-filtering-and-undo-hardening]

tech-stack:
  added: []
  patterns:
    - View-owned multi-select ListBox events are forwarded to MorphsViewModel checklist filter state
    - Active filter badges are derived from ViewModel filter counts for compiled-bound UI discovery
    - Filtered-empty list state is bound to ViewModel filter activity instead of implying data loss

key-files:
  created:
    - .planning/phases/02-workflow-persistence-filtering-and-undo-hardening/02-06-SUMMARY.md
  modified:
    - src/BS2BG.App/Views/MainWindow.axaml
    - src/BS2BG.App/Views/MainWindow.axaml.cs
    - src/BS2BG.App/Themes/ThemeResources.axaml
    - src/BS2BG.App/ViewModels/MorphsViewModel.cs
    - src/BS2BG.App/ViewModels/Workflow/NpcFilterState.cs
    - tests/BS2BG.Tests/M6UxAppShellTests.cs

key-decisions:
  - "Kept checklist popup selection glue in MainWindow code-behind because Avalonia multi-selection exposes control state rather than a simple command parameter."
  - "Kept Core NPC models unchanged; UI badges and empty-state visibility are derived from existing App-layer filter state."

patterns-established:
  - "Per-column Morphs filter controls use named compiled-bound popup controls plus minimal code-behind selection forwarding."
  - "Filtered-empty UI uses informational resources and exact UI-SPEC copy instead of warnings or destructive colors."

requirements-completed: [WORK-02]

duration: 11 min
completed: 2026-04-27
---

# Phase 02 Plan 06: Full Morphs Checklist Filtering UI Summary

**Morphs UI now exposes all required NPC checklist filters with searchable popups, active filter badges, and filtered-empty recovery copy.**

## Performance

- **Duration:** 11 min
- **Started:** 2026-04-27T01:38:55Z
- **Completed:** 2026-04-27T01:49:47Z
- **Tasks:** 2 completed
- **Files modified:** 6

## Accomplishments

- Added Morphs list header filter popups for mod, name, editor ID, form ID, race, assignment state, and preset values with exact UI-SPEC search placeholders and `Clear` buttons.
- Routed checklist multi-selection from the Avalonia view into `MorphsViewModel.SetNpcColumnAllowedValues` while keeping business filtering in the ViewModel.
- Added filter badge text such as `Race: 1 selected` and active-filter accent resources for visual discoverability.
- Added a filtered-empty state in the NPC list area with the exact required heading/body copy so hidden rows do not look like data loss.
- Expanded headless UI coverage for control discovery, popup copy, non-race selection routing, badge text, and filtered-empty copy.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Filter popup UI coverage** - `5af786f6` (test)
2. **Task 1 GREEN: Required checklist filter popups** - `48f9907b` (feat)
3. **Task 2 RED: Badge and filtered-empty coverage** - `24141b15` (test)
4. **Task 2 GREEN: Badges and empty state** - `17d6e412` (feat)

**Plan metadata:** committed after this summary.

## Files Created/Modified

- `src/BS2BG.App/Views/MainWindow.axaml` - Added per-column filter buttons/popups, active badge bindings, and filtered-empty NPC list state.
- `src/BS2BG.App/Views/MainWindow.axaml.cs` - Added view-only checklist selection and clear forwarding for all NPC filter columns.
- `src/BS2BG.App/Themes/ThemeResources.axaml` - Added light/dark active-filter accent brushes.
- `src/BS2BG.App/ViewModels/MorphsViewModel.cs` - Exposed per-column search/value/badge properties, popup commands, and filtered-empty visibility.
- `src/BS2BG.App/ViewModels/Workflow/NpcFilterState.cs` - Added filter activity helpers for badge/empty-state derivation.
- `tests/BS2BG.Tests/M6UxAppShellTests.cs` - Added headless UI tests for filter popups, selection routing, badges, and filtered-empty copy.

## Decisions Made

- Kept code-behind changes limited to view glue because Avalonia `ListBox.SelectedItems` multi-select changes are control-owned state and not naturally expressible as a simple command parameter.
- Used informational/active-filter resources for empty-state and badge presentation; warning/destructive colors remain reserved for their existing semantics.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Avoided parallel build/test output lock**
- **Found during:** Overall verification
- **Issue:** Running `dotnet test` and `dotnet build` in parallel caused `CS2012` because both tried to write the test assembly simultaneously.
- **Fix:** Re-ran `dotnet build BS2BG.sln` sequentially after the UI tests completed.
- **Files modified:** None
- **Verification:** Sequential build passed.
- **Committed in:** Not applicable; verification-only issue.

---

**Total deviations:** 1 auto-fixed (1 blocking verification issue).
**Impact on plan:** No code scope changes; the issue was limited to command scheduling during verification.

## Issues Encountered

- The initial badge test selected a non-existent checklist value; the test was corrected to apply two valid conflicting checklist filters through the ViewModel so the filtered-empty state is reachable without relying on impossible UI selections.
- Existing analyzer warnings in `ExportWriterTests.cs` appeared during targeted test runs and are pre-existing/out of scope for this plan.

## TDD Gate Compliance

- RED commits present before GREEN for both tasks: `5af786f6`, `24141b15`.
- GREEN commits present after RED for both tasks: `48f9907b`, `17d6e412`.
- No refactor commit was needed.

## Verification

- `dotnet test --filter FullyQualifiedName~M6UxAppShellTests` — PASSED (8 tests).
- `dotnet build BS2BG.sln` — PASSED (0 warnings, 0 errors on final sequential run).

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 02-07 to add explicit scoped NPC bulk operations and scope selector UI over the filtered/visible row state.

## Self-Check: PASSED

- Verified key implementation files exist in the worktree.
- Verified plan commits exist: `5af786f6`, `48f9907b`, `24141b15`, and `17d6e412`.

---
*Phase: 02-workflow-persistence-filtering-and-undo-hardening*
*Completed: 2026-04-27*
