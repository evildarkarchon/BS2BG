---
phase: 02-workflow-persistence-filtering-and-undo-hardening
plan: 01
subsystem: app-preferences
tags: [avalonia, reactiveui, user-preferences, workflow-state, tdd]

requires:
  - phase: 01-profile-correctness-and-trust
    provides: Profile-aware template workflow and project round-trip safeguards
provides:
  - Local persistence for the Omit Redundant Sliders workflow preference
  - Backward-compatible user preferences DTO retaining theme preference behavior
  - Non-blocking preference failure handling for template generation options
affects: [template-generation-flow, workflow-persistence, project-serialization]

tech-stack:
  added: []
  patterns:
    - Best-effort local preferences through IUserPreferencesService
    - ReactiveUI property subscriptions for non-project workflow state

key-files:
  created: []
  modified:
    - src/BS2BG.App/Services/UserPreferencesService.cs
    - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - tests/BS2BG.Tests/UserPreferencesServiceTests.cs
    - tests/BS2BG.Tests/TemplatesViewModelTests.cs

key-decisions:
  - "Persist Omit Redundant Sliders only in local user preferences, not in .jbs2bg project serialization."
  - "Keep preference persistence best-effort: save/load failures report status or defaults without blocking generation."

patterns-established:
  - "Workflow preferences are hydrated from IUserPreferencesService and cached as local convenience state."
  - "Theme and workflow preference saves preserve the other preference value in the shared local DTO."

requirements-completed: [WORK-01]

duration: 5min
completed: 2026-04-27
---

# Phase 02 Plan 01: Local Workflow Preference Persistence Summary

**Omit Redundant Sliders now persists as best-effort local user preference state while project files remain shareable and unchanged.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-27T00:19:21Z
- **Completed:** 2026-04-27T00:24:34Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Extended `UserPreferences` with `OmitRedundantSliders` while preserving existing theme preference load/save compatibility.
- Hydrated `TemplatesViewModel.OmitRedundantSliders` from local preferences and saved changes without mutating `ProjectModel` or `.jbs2bg` serialization.
- Preserved the other local preference value when either theme or omit-redundant state is saved.
- Added regression tests for backward compatibility, failure fallbacks, hydration, save behavior, serialization isolation, and non-blocking generation after save failure.

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend local preference DTO and compatibility tests**
   - `baca280e` (test): add failing preference persistence tests
   - `b668331b` (feat): persist omit-redundant preference
2. **Task 2: Hydrate and save Omit Redundant Sliders from Templates workflow**
   - `ed7bf68a` (test): add failing template preference tests
   - `27d33a4c` (feat): hydrate template workflow preferences
   - `21323058` (fix): isolate template preference tests

**Plan metadata:** pending docs commit

_Note: TDD tasks produced RED and GREEN commits; the final fix commit addressed a verification-discovered test isolation bug._

## Files Created/Modified

- `src/BS2BG.App/Services/UserPreferencesService.cs` - Added `OmitRedundantSliders` to the local preferences DTO.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` - Loads/saves omit-redundant workflow state via `IUserPreferencesService` and reports non-blocking save failure status.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Preserves workflow preference state when saving theme preferences.
- `tests/BS2BG.Tests/UserPreferencesServiceTests.cs` - Covers preference compatibility, save output, corrupt/missing load fallback, and save failure behavior.
- `tests/BS2BG.Tests/TemplatesViewModelTests.cs` - Covers workflow preference hydration, local save behavior, project serialization isolation, save failure status, and generation continuity.

## Decisions Made

- Stored generation-affecting workflow preference state in local AppData preferences only, keeping `.jbs2bg` project serialization unchanged for shared project compatibility.
- Kept preference failures best-effort and non-blocking; template generation continues even if the local preference file cannot be saved.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Isolated template ViewModel tests from machine-local preferences**
- **Found during:** Plan-level targeted verification after Task 2
- **Issue:** Existing `TemplatesViewModelTests` constructed the ViewModel without an explicit preferences service, allowing the developer machine's AppData preference file to hydrate `OmitRedundantSliders` and break deterministic assertions.
- **Fix:** Updated the test helper to pass a deterministic default `CapturingUserPreferencesService` unless a test explicitly provides a preferences service.
- **Files modified:** `tests/BS2BG.Tests/TemplatesViewModelTests.cs`
- **Verification:** `dotnet test --filter "FullyQualifiedName~UserPreferencesServiceTests|FullyQualifiedName~TemplatesViewModelTests"` passed.
- **Committed in:** `21323058`

---

**Total deviations:** 1 auto-fixed (1 Rule 1 bug)
**Impact on plan:** The fix was required for deterministic tests and did not change production scope.

## Issues Encountered

- Initial Task 1 RED tests assumed string enum JSON, but the existing preference serializer writes numeric enum values. The tests were corrected during GREEN work to preserve current theme serialization compatibility.
- Full targeted verification exposed local preference test pollution; fixed in `21323058` as documented above.

## Verification

- `dotnet test --filter FullyQualifiedName~UserPreferencesServiceTests` — passed.
- `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` — passed.
- `dotnet test --filter "FullyQualifiedName~UserPreferencesServiceTests|FullyQualifiedName~TemplatesViewModelTests"` — passed.
- `dotnet test` — passed (248 tests; existing CA1861 warnings in `ExportWriterTests.cs`).

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## TDD Gate Compliance

- Task 1 RED commit: `baca280e`; GREEN commit: `b668331b`.
- Task 2 RED commit: `ed7bf68a`; GREEN commit: `27d33a4c`.
- No TDD gate violations.

## Next Phase Readiness

Ready for subsequent Phase 02 plans to build filtering and undo hardening on top of deterministic local workflow preference persistence.

## Self-Check: PASSED

- Verified all modified source/test files and `02-01-SUMMARY.md` exist.
- Verified task commits are present in git history: `baca280e`, `b668331b`, `ed7bf68a`, `27d33a4c`, `21323058`.
- Verified final working tree was clean before summary creation; metadata commit remains pending for this summary file only.

---
*Phase: 02-workflow-persistence-filtering-and-undo-hardening*
*Completed: 2026-04-27*
