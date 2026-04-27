---
phase: 02-workflow-persistence-filtering-and-undo-hardening
plan: 02
subsystem: app-preferences
tags: [avalonia, file-dialogs, user-preferences, workflow-state, tdd]

requires:
  - phase: 02-workflow-persistence-filtering-and-undo-hardening
    provides: Omit Redundant Sliders local workflow preference from Plan 01
provides:
  - Independent local preference fields for project, BodyGen export, and BoS JSON export folders
  - Remembered project open/save, BodyGen export, and BoS JSON picker start-folder channels
  - Best-effort invalid-path handling for picker hints without blocking file workflows
affects: [workflow-persistence, project-file-workflow, export-workflow]

tech-stack:
  added: []
  patterns:
    - Testable file dialog backend seam over Avalonia StorageProvider
    - Best-effort local folder preferences kept out of project/export data

key-files:
  created: []
  modified:
    - src/BS2BG.App/Services/UserPreferencesService.cs
    - src/BS2BG.App/Services/WindowFileDialogService.cs
    - src/BS2BG.App/AppBootstrapper.cs
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
    - tests/BS2BG.Tests/UserPreferencesServiceTests.cs
    - tests/BS2BG.Tests/MainWindowViewModelTests.cs
    - tests/BS2BG.Tests/TemplatesViewModelTests.cs

key-decisions:
  - "Use separate local preference channels for project files, BodyGen exports, and BoS JSON exports rather than a global last-used folder."
  - "Keep remembered folder paths advisory: invalid paths resolve to no picker hint and never cancel the underlying workflow."

patterns-established:
  - "WindowFileDialogService delegates picker calls through IFileDialogBackend so start-folder behavior can be tested without implementing Avalonia storage interfaces."
  - "Preference updates preserve unrelated workflow fields so theme, omit-redundant, and folder channels do not overwrite each other."

requirements-completed: [WORK-01]

duration: 70min
completed: 2026-04-27
---

# Phase 02 Plan 02: Remembered Project and Export Folder Summary

**Project open/save, BodyGen export, and BoS JSON export now retain independent best-effort local folder hints across restarts.**

## Performance

- **Duration:** 70 min
- **Started:** 2026-04-27T00:26:48Z
- **Completed:** 2026-04-27T01:36:56Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Added `ProjectFolder`, `BodyGenExportFolder`, and `BosJsonExportFolder` to local user preferences with backward-compatible defaults for existing preference files.
- Wired `WindowFileDialogService` to seed project open/save, BodyGen export, and BoS JSON export pickers from their own remembered folder channel.
- Saved successful picker selections back to the matching local folder channel without blocking workflows when preference persistence or remembered-path resolution fails.
- Preserved folder channels when other local preferences such as theme and Omit Redundant Sliders are saved.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add project and export folder preference fields**
   - `f6393c9c` (test): add failing folder preference tests
   - `656ba540` (feat): persist remembered folder preferences
2. **Task 2: Seed and update project/export picker folders**
   - `f50ac845` (test): add failing picker folder channel tests
   - `70c0b6bf` (feat): remember project and export picker folders

**Plan metadata:** pending docs commit

_Note: TDD tasks produced RED and GREEN commits. Task 2 GREEN also includes preference-preservation hardening discovered during implementation._

## Files Created/Modified

- `src/BS2BG.App/Services/UserPreferencesService.cs` - Added separate nullable local folder preference fields.
- `src/BS2BG.App/Services/WindowFileDialogService.cs` - Added remembered-folder seeding/updating and an Avalonia storage-provider backend seam.
- `src/BS2BG.App/AppBootstrapper.cs` - Constructs `WindowFileDialogService` with the shared `IUserPreferencesService` singleton.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Preserves folder channels while saving theme preferences.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` - Preserves folder channels while saving Omit Redundant Sliders.
- `tests/BS2BG.Tests/UserPreferencesServiceTests.cs` - Covers folder field round-trip and legacy default compatibility.
- `tests/BS2BG.Tests/MainWindowViewModelTests.cs` - Covers project/open save and export picker channel seeding/updating with invalid hint fallback.
- `tests/BS2BG.Tests/TemplatesViewModelTests.cs` - Covers preserving folder channels when saving omit-redundant preference state.

## Decisions Made

- Used independent local preference fields for project, BodyGen export, and BoS JSON export folder channels to satisfy D-02 without changing `.jbs2bg` project serialization.
- Added a small `IFileDialogBackend` seam because Avalonia storage interfaces are not user-implementable in tests, while the production backend still maps hints to `SuggestedStartLocation`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Preserved folder channels when saving unrelated preferences**
- **Found during:** Task 2 (Seed and update project/export picker folders)
- **Issue:** Existing theme and omit-redundant preference save paths rebuilt `UserPreferences` without the new folder fields, which would wipe remembered folders after changing another local preference.
- **Fix:** Updated `MainWindowViewModel` and `TemplatesViewModel` preference saves to copy `ProjectFolder`, `BodyGenExportFolder`, and `BosJsonExportFolder` forward.
- **Files modified:** `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `tests/BS2BG.Tests/TemplatesViewModelTests.cs`
- **Verification:** `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~UserPreferencesServiceTests|FullyQualifiedName~TemplatesViewModelTests"` passed.
- **Committed in:** `70c0b6bf`

---

**Total deviations:** 1 auto-fixed (1 Rule 2 missing critical functionality)
**Impact on plan:** The fix was necessary to keep the new local folder preferences reliable and did not expand product scope.

## Issues Encountered

- Avalonia storage interfaces include non-user-implementable members, so direct fake implementations are not viable. A small backend seam keeps production behavior on `StorageProvider.SuggestedStartLocation` while allowing deterministic unit coverage.

## Verification

- `dotnet test --filter FullyQualifiedName~UserPreferencesServiceTests` — passed during Task 1 GREEN.
- `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~UserPreferencesServiceTests"` — passed (39 tests).
- `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~UserPreferencesServiceTests|FullyQualifiedName~TemplatesViewModelTests"` — passed (58 tests).
- `dotnet test` — passed (258 tests).

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## TDD Gate Compliance

- Task 1 RED commit: `f6393c9c`; GREEN commit: `656ba540`.
- Task 2 RED commit: `f50ac845`; GREEN commit: `70c0b6bf`.
- No TDD gate violations.

## Next Phase Readiness

Ready for Plan 03 to add separate BodySlide XML and NPC text import folder channels using the same local preference and picker-backend pattern.

## Self-Check: PASSED

- Verified all modified source/test files exist.
- Verified task commits are present in git history: `f6393c9c`, `656ba540`, `f50ac845`, `70c0b6bf`.
- Verified targeted and full test suites pass.
- Verified working tree was clean before summary creation; metadata commit remains pending for this summary file only.

---
*Phase: 02-workflow-persistence-filtering-and-undo-hardening*
*Completed: 2026-04-27*
