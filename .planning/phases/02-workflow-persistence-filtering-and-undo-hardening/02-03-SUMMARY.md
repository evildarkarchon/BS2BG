---
phase: 02-workflow-persistence-filtering-and-undo-hardening
plan: 03
subsystem: app-preferences
tags: [avalonia, file-pickers, user-preferences, workflow-state, tdd]

requires:
  - phase: 02-workflow-persistence-filtering-and-undo-hardening
    provides: Local workflow preference service and project/export remembered folder patterns from Plans 01-02
provides:
  - Independent local preference fields for BodySlide XML import and NPC text import folders
  - Remembered BodySlide XML and NPC text picker start-folder channels
  - Best-effort import folder preference updates that do not block import workflows
affects: [workflow-persistence, template-import-workflow, npc-import-workflow]

tech-stack:
  added: []
  patterns:
    - Testable import picker backend seams over Avalonia StorageProvider
    - Best-effort local folder preferences kept out of project data and filter state

key-files:
  created: []
  modified:
    - src/BS2BG.App/Services/UserPreferencesService.cs
    - src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs
    - src/BS2BG.App/Services/WindowNpcTextFilePicker.cs
    - src/BS2BG.App/AppBootstrapper.cs
    - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - tests/BS2BG.Tests/TemplatesViewModelTests.cs
    - tests/BS2BG.Tests/MorphsViewModelTests.cs

key-decisions:
  - "Use separate local preference channels for BodySlide XML imports and NPC text imports rather than sharing project or export folders."
  - "Keep import folder paths advisory and best-effort so invalid paths or save failures never block parsing selected files."

patterns-established:
  - "WindowBodySlideXmlFilePicker and WindowNpcTextFilePicker delegate Avalonia-specific picker work through backend seams for deterministic preference-channel tests."
  - "Preference-preserving save paths copy all workflow folder fields forward when unrelated theme or omit-redundant preferences are saved."

requirements-completed: [WORK-01]

duration: 8min
completed: 2026-04-27
---

# Phase 02 Plan 03: Remembered Import Folder Summary

**BodySlide XML and NPC text imports now retain independent best-effort local folder hints across restarts without persisting transient NPC filters.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-04-27T01:38:55Z
- **Completed:** 2026-04-27T01:46:40Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Added `BodySlideXmlFolder` and `NpcTextFolder` to local user preferences for separate import-folder channels.
- Wired BodySlide XML and NPC text picker services to resolve remembered folders as Avalonia `SuggestedStartLocation` hints when available.
- Saved successful import selections back to the matching local folder preference without blocking imports when preference saves fail.
- Added tests proving NPC search/filter state is not serialized into local preferences.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add remembered BodySlide XML import folder wiring**
   - `439db7ad` (test): add failing BodySlide import folder tests
   - `924fb81e` (feat): remember BodySlide import folders
2. **Task 2: Add remembered NPC text import folder wiring**
   - `9a9495cd` (test): add failing NPC import folder tests
   - `d973033a` (feat): remember NPC import folders

**Plan metadata:** pending docs commit

_Note: Both TDD tasks produced RED and GREEN commits._

## Files Created/Modified

- `src/BS2BG.App/Services/UserPreferencesService.cs` - Added separate nullable local import folder preference fields.
- `src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs` - Added remembered-folder seeding/updating and a testable Avalonia backend seam for XML import picking.
- `src/BS2BG.App/Services/WindowNpcTextFilePicker.cs` - Added remembered-folder seeding/updating and a testable Avalonia backend seam for NPC text import picking.
- `src/BS2BG.App/AppBootstrapper.cs` - Constructs import picker services with the shared `IUserPreferencesService` singleton.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` - Preserves import folder channels while saving Omit Redundant Sliders.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Preserves import folder channels while saving theme preferences.
- `tests/BS2BG.Tests/TemplatesViewModelTests.cs` - Covers XML picker channel seeding/updating and non-blocking preference save failures.
- `tests/BS2BG.Tests/MorphsViewModelTests.cs` - Covers NPC picker channel seeding/updating, non-blocking save failures, and absence of NPC filter/search state from preference JSON.

## Decisions Made

- Used independent local preference fields for BodySlide XML and NPC text import folders to satisfy D-02 without changing `.jbs2bg` project serialization.
- Kept remembered import folders advisory: invalid path resolution falls back to no hint, and preference save failures do not stop template or NPC imports.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Preserved import folder channels when saving unrelated preferences**
- **Found during:** Task 2 (Add remembered NPC text import folder wiring)
- **Issue:** Existing theme and Omit Redundant Sliders preference save paths rebuild `UserPreferences`; after adding import folder fields, those saves would otherwise drop remembered import folders.
- **Fix:** Updated `MainWindowViewModel` and `TemplatesViewModel` to copy `BodySlideXmlFolder` and `NpcTextFolder` forward with the other workflow folder preferences.
- **Files modified:** `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`
- **Verification:** `dotnet test --filter "FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~UserPreferencesServiceTests"` passed.
- **Committed in:** `d973033a`

---

**Total deviations:** 1 auto-fixed (1 Rule 2 missing critical functionality)
**Impact on plan:** The fix was necessary to keep import folder persistence reliable and did not expand product scope.

## Issues Encountered

None.

## Verification

- `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` — passed (24 tests) after Task 1 GREEN.
- `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` — passed (21 tests) after Task 2 GREEN.
- `dotnet test --filter "FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~UserPreferencesServiceTests"` — passed (51 tests).

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## TDD Gate Compliance

- Task 1 RED commit: `439db7ad`; GREEN commit: `924fb81e`.
- Task 2 RED commit: `9a9495cd`; GREEN commit: `d973033a`.
- No TDD gate violations.

## Next Phase Readiness

Ready for subsequent Phase 2 plans to rely on independent local folder channels while keeping NPC filter/search state session-only.

## Self-Check: PASSED

- Verified all modified source/test files and this summary exist.
- Verified task commits are present in git history: `439db7ad`, `924fb81e`, `9a9495cd`, `d973033a`.
- Verified targeted plan test suite passes.
- Verified working tree contains only the pending summary metadata file before metadata commit.

---
*Phase: 02-workflow-persistence-filtering-and-undo-hardening*
*Completed: 2026-04-27*
