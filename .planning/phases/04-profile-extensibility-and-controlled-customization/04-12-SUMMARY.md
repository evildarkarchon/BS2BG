---
phase: 04-profile-extensibility-and-controlled-customization
plan: 12
subsystem: ui
tags: [avalonia, reactiveui, profile-manager, custom-profiles, gap-closure]
requires:
  - phase: 04-profile-extensibility-and-controlled-customization
    provides: Profile Manager workspace, profile editor, custom profile import/export, recovery diagnostics
provides:
  - Manager Save Profile command saves valid blank and copy-as-custom editor candidates
  - Committed-selection rollback preserves row-scoped command targets after declined discard
  - Dirty profile editors survive search and catalog refresh without silent replacement
  - Expected profile JSON import/export I/O failures become actionable StatusMessage text
affects: [profile-extensibility, profile-manager, diagnostics-recovery]
tech-stack:
  added: []
  patterns:
    - ReactiveCommand canExecute derived from active editor validity
    - Committed selected row tracked separately from transient ListBox selection
    - Expected file I/O failures converted to recoverable UI status outcomes
key-files:
  created: []
  modified:
    - src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs
    - tests/BS2BG.Tests/ProfileManagerViewModelTests.cs
key-decisions:
  - "Manager save eligibility follows active editor validity, not only selected LocalCustom rows, so blank and copied candidates can be saved from the visible Profiles workspace."
  - "Committed profile selection is the authoritative command target when unsaved editor discard is declined."
patterns-established:
  - "Profile manager refresh preserves dirty editor buffers deterministically because refresh is synchronous and cannot prompt."
  - "Profile JSON read/write filesystem failures are recoverable status messages; malformed JSON remains on the strict validation path."
requirements-completed: [EXT-01, EXT-02]
duration: 32min
completed: 2026-04-27
---

# Phase 04 Plan 12: Profile Manager Gap Closure Summary

**Profile Manager saves active custom-profile editor candidates safely while preserving dirty buffers and surfacing file I/O failures as recoverable status text.**

## Performance

- **Duration:** 32 min
- **Started:** 2026-04-27T11:39:26Z
- **Completed:** 2026-04-27T12:11:00Z
- **Tasks:** 3
- **Files modified:** 2

## Accomplishments

- Enabled the visible manager-level Save Profile command for valid blank and copy-as-custom candidates without requiring an existing LocalCustom row selection.
- Added committed-selection tracking so declined discard restores the row backing the visible editor instead of leaving row actions pointed at a newly clicked row.
- Preserved dirty editor instances across search and catalog refresh, preventing silent replacement of unsaved profile edits.
- Converted expected profile JSON import/export read/write failures into actionable `StatusMessage` results without swallowing malformed JSON validation or programming errors.

## Task Commits

Each task was committed atomically using TDD gates:

1. **Task 1: Save valid create/copy editor candidates through manager SaveProfileCommand**
   - `30d189e2` test: add failing manager save coverage
   - `88688d10` feat: save active profile editor candidates
2. **Task 2: Restore committed selection and preserve dirty editor during search/catalog refresh**
   - `65359827` test: add failing selection refresh coverage
   - `0c44463b` feat: preserve committed profile selection
3. **Task 3: Convert expected profile import/export file I/O failures into StatusMessage results**
   - `a9b848e2` test: add failing profile IO failure coverage
   - `ea1b27f6` feat: handle profile JSON IO failures

## Files Created/Modified

- `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` - Active-editor save gating, committed selection rollback, dirty-refresh preservation, and expected profile JSON I/O failure handling.
- `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs` - Regression coverage for CR-01, CR-02, CR-03, and WR-02 gap findings.

## Decisions Made

- Manager-level save eligibility now follows active editor validity and local-custom buildability rather than selected-row source alone, because create/copy workflows intentionally clear selection before the first save.
- `committedSelectedProfile` is maintained as the row whose editor is actually displayed; transient ListBox assignment is rolled back when discard is declined.
- Catalog/search refresh does not prompt while dirty; it preserves the editor instance and restores row identity when possible because refresh is synchronous.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Initial import failure handling still refreshed the catalog after a failed import path, replacing clean editor state. Adjusted refresh to run only after at least one successful import save so expected read failures preserve selection/editor state.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None.

## Threat Flags

None.

## TDD Gate Compliance

- RED gate commits present for all three tasks.
- GREEN gate commits present after each RED gate.

## Verification

- `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` — passed (19 tests).
- `dotnet test --filter "FullyQualifiedName~ProfileManagerViewModelTests|FullyQualifiedName~MainWindowHeadlessTests"` — passed (23 tests).
- `dotnet build BS2BG.sln` — passed with 0 warnings and 0 errors.

## Next Phase Readiness

Plan 12 closes remaining Profile Manager blockers and leaves the Profiles workspace ready for the final profile-editor filtering closure in Plan 13.

## Self-Check: PASSED

- Created summary file exists at `.planning/phases/04-profile-extensibility-and-controlled-customization/04-12-SUMMARY.md`.
- Task commits recorded: `30d189e2`, `88688d10`, `65359827`, `0c44463b`, `a9b848e2`, `ea1b27f6`.
- Modified files exist and focused verification passed.

---
*Phase: 04-profile-extensibility-and-controlled-customization*
*Completed: 2026-04-27*
