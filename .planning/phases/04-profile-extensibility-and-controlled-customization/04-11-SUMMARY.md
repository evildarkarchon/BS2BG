---
phase: 04-profile-extensibility-and-controlled-customization
plan: 11
subsystem: profile-project-sharing-gap-closure
tags: [csharp, avalonia, project-roundtrip, custom-profiles, profile-conflicts, tdd]

requires:
  - phase: 04-profile-extensibility-and-controlled-customization
    provides: Core ProjectSaveContext serialization and project-open profile conflict transactions from plans 04-03 and 04-06
provides:
  - GUI project saves that pass a case-insensitive ProjectSaveContext to Core serialization
  - App-level regression coverage for referenced local custom profile embedding without unrelated local profile disclosure
  - Source-aware Rename Project Copy validation that cannot collide with local custom profile names
affects: [profile-extensibility, project-sharing, project-roundtrip, profile-recovery]

tech-stack:
  added: []
  patterns:
    - App save context snapshots runtime local and project-scoped custom profiles while Core performs referenced-only filtering
    - Rename conflict validation separates bundled, local, embedded, and newly renamed name occupancy

key-files:
  created:
    - .planning/phases/04-profile-extensibility-and-controlled-customization/04-11-SUMMARY.md
  modified:
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - tests/BS2BG.Tests/MainWindowViewModelTests.cs
    - tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs
    - tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs

key-decisions:
  - "GUI saves provide available custom definitions as context and continue to rely on ProjectFileService for legacy fields and referenced-only CustomProfiles filtering."
  - "Rename Project Copy validation treats local profile names as a separate occupied source so removing the embedded candidate cannot remove the conflicting local name."

patterns-established:
  - "MainWindowViewModel.BuildProjectSaveContext captures local and active project profile snapshots for save-time profile resolution."
  - "Conflict rename validation uses source-specific occupancy sets rather than mutating one combined occupied-name set."

requirements-completed: [EXT-03, EXT-05]

duration: 4 min
completed: 2026-04-27
---

# Phase 04 Plan 11: GUI Profile Sharing Gap Closure Summary

**GUI project saves now embed referenced local custom profiles through Core save context while conflict rename validation rejects local-profile name collisions.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-27T10:58:42Z
- **Completed:** 2026-04-27T11:02:24Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Wired normal GUI saves to `ProjectFileService.SaveToString(project, ProjectSaveContext)` with a case-insensitive context built from local custom profiles and active project-scoped profiles.
- Added an app-level save regression proving a project preset referencing a local custom profile serializes `CustomProfiles` while an unrelated local custom profile is omitted.
- Replaced single-set conflict rename validation with source-aware name checks so Rename Project Copy cannot keep the original local conflict name or choose another local custom profile name.
- Added focused conflict recovery tests for both original same-name and other-local custom profile rename collision rejection.

## Task Commits

Each task was committed atomically with TDD gates:

1. **Task 1 RED: GUI save profile embedding regression** - `dfb0a4ef` (test)
2. **Task 1 GREEN: Context-aware GUI project saves** - `5e117f37` (feat)
3. **Task 2 RED: Rename conflict validation regressions** - `82c5ac9b` (test)
4. **Task 2 GREEN: Source-aware rename validation** - `8c4a11c7` (feat)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Adds `BuildProjectSaveContext`, calls the context-aware project serializer during GUI saves, and validates rename choices with source-specific occupancy sets.
- `tests/BS2BG.Tests/MainWindowViewModelTests.cs` - Adds app-level save regression and a test catalog-service stub for local custom profile save context coverage.
- `tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs` - Adds rename collision tests for original local conflict names and other local custom profile names.
- `tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs` - Normalizes legacy-shape expected JSON line endings with `ReplaceLineEndings()` so focused verification is platform-stable.
- `.planning/phases/04-profile-extensibility-and-controlled-customization/04-11-SUMMARY.md` - Captures execution outcome and verification evidence.

## Decisions Made

- GUI save context construction remains App-layer glue only; `ProjectFileService` continues to own JSON shape, legacy field preservation, bundled-name exclusion, and referenced-only custom profile filtering.
- Active project-scoped profiles are added after local profiles in the save context so an explicit project overlay can provide the definition for the current project without mutating local storage.
- Rename validation uses separate bundled, local, embedded, and renamed-name sets instead of a combined set so local profile occupancy cannot be erased by removing the embedded candidate.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Stabilized project serialization test line endings**
- **Found during:** Task 1 (Wire GUI project saves to ProjectSaveContext)
- **Issue:** The focused verification command included `ProjectFileServiceCustomProfileTests`, and `Save_NoCustomProfiles_ProducesByteIdenticalOutputToV1Format` double-expanded CRLF in this Windows worktree by replacing `\n` inside a raw string that already used platform line endings.
- **Fix:** Replaced the manual `Replace("\n", Environment.NewLine, ...)` with `ReplaceLineEndings()` so the expected JSON matches the platform line ending exactly once.
- **Files modified:** `tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs`
- **Verification:** `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~ProjectFileServiceCustomProfileTests"` passed.
- **Committed in:** `5e117f37`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** The fix was necessary to run the plan-mandated focused verification on Windows and did not change production behavior or golden fixtures.

## Issues Encountered

- Existing analyzer warnings remain in focused test output and were not modified because they are outside this gap-closure scope.

## Known Stubs

None. Empty array initializers in tests and null-returning test services are intentional test scaffolding, not user-facing placeholder data.

## Threat Flags

None - the plan threat model covered the runtime local profile catalog to project save JSON boundary and the embedded/local conflict rename trust boundary.

## Verification

- `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~ProjectFileServiceCustomProfileTests"` — passed (52 tests).
- `dotnet test --filter FullyQualifiedName~MainWindowViewModelProfileRecoveryTests` — passed (12 tests).
- `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~MainWindowViewModelProfileRecoveryTests|FullyQualifiedName~ProjectFileServiceCustomProfileTests"` — passed (64 tests).
- Acceptance checks confirmed `SaveToString(project, BuildProjectSaveContext())`, `CustomProfiles` save regression with unrelated local profile exclusion, both rename collision tests, and no remaining `occupied.Remove(conflict.Embedded.Name)` pattern.

## TDD Gate Compliance

- RED gate commits: `dfb0a4ef`, `82c5ac9b`
- GREEN gate commits: `5e117f37`, `8c4a11c7`
- Refactor gate: not needed; focused verification passed without a separate cleanup commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 4 EXT-03/EXT-05 GUI save and conflict rename verification gaps are closed for this plan. Remaining Wave 7/Wave 8 gap-closure plans can proceed without relying on manual project JSON editing for profile sharing.

## Self-Check: PASSED

- Created/modified files verified: `MainWindowViewModel.cs`, `MainWindowViewModelTests.cs`, `MainWindowViewModelProfileRecoveryTests.cs`, `ProjectFileServiceCustomProfileTests.cs`, and `04-11-SUMMARY.md` exist.
- Commits verified: `dfb0a4ef`, `5e117f37`, `82c5ac9b`, and `8c4a11c7` exist in git history.
- Verification commands passed as documented.

---
*Phase: 04-profile-extensibility-and-controlled-customization*
*Completed: 2026-04-27*
