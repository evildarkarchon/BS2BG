---
phase: 01-profile-correctness-and-trust
plan: 04
subsystem: ui
tags: [avalonia, compiled-bindings, headless-ui-tests, release-docs]

requires:
  - phase: 01-profile-correctness-and-trust
    provides: Plan 03 TemplatesViewModel fallback visibility and text properties
provides:
  - Neutral unresolved-profile fallback panel in the Templates workflow
  - Distinct light/dark info theme brushes separate from warning resources
  - Headless UI coverage for fallback panel, bundled profile labels, and forbidden warning copy
  - Release-facing Fallout 4 CBBE profile calibration context outside the app workflow
affects: [template-generation-flow, release-polish, profile-correctness]

tech-stack:
  added: []
  patterns:
    - Avalonia DynamicResource-backed neutral information panel
    - Headless UI tests over logical descendants and ViewModel profile names

key-files:
  created:
    - .planning/phases/01-profile-correctness-and-trust/01-04-SUMMARY.md
  modified:
    - tests/BS2BG.Tests/AppShellTests.cs
    - src/BS2BG.App/Themes/ThemeResources.axaml
    - src/BS2BG.App/Views/MainWindow.axaml
    - docs/release/README.md

key-decisions:
  - "Keep unresolved-profile feedback neutral in the Templates workflow and reserve Fallout 4 CBBE calibration context for release documentation."

patterns-established:
  - "Profile fallback UI uses BS2BGInfo* theme resources, not warning brushes or warning copy."
  - "Main workflow profile-label tests assert exact bundled display names and absence of mismatch/experimental language."

requirements-completed: [PROF-03, PROF-05]

duration: 2 min
completed: 2026-04-26
---

# Phase 01 Plan 04: Neutral Fallback UI and FO4 Profile Context Summary

**Neutral profile fallback panel with distinct info styling plus release-only Fallout 4 CBBE calibration context**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-26T13:30:05Z
- **Completed:** 2026-04-26T13:32:34Z
- **Tasks:** 2 completed
- **Files modified:** 4

## Accomplishments

- Added headless UI coverage proving `ProfileFallbackInformationPanel` exists with the required automation name and that bundled profile labels remain `Skyrim CBBE`, `Skyrim UUNP`, and `Fallout 4 CBBE` without experimental qualifiers.
- Added light/dark `BS2BGInfo*` brushes and wired a neutral fallback information panel directly below the Templates profile toolbar.
- Documented the Fallout 4 CBBE profile seed/calibration assumptions in release docs instead of adding warning or experimental language to the app workflow.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add headless UI tests for fallback panel and no FO4 experimental label** - `5ae7e075` (test)
2. **Task 2: Add neutral fallback panel, info brushes, and release profile note** - `48422a51` (feat)

**Plan metadata:** pending final docs commit

_Note: Task 1 followed TDD RED, then Task 2 supplied the GREEN implementation._

## Files Created/Modified

- `tests/BS2BG.Tests/AppShellTests.cs` - Added headless UI assertions for the fallback panel, profile selector names, forbidden main-workflow copy, and neutral info brush resources.
- `src/BS2BG.App/Themes/ThemeResources.axaml` - Added distinct neutral information brushes for light and dark themes.
- `src/BS2BG.App/Views/MainWindow.axaml` - Added the accessible fallback information panel bound to `Templates.IsProfileFallbackInformationVisible` and `Templates.ProfileFallbackInformationText`.
- `docs/release/README.md` - Added the release-facing Fallout 4 CBBE profile note and packaged profile file entry.

## Decisions Made

- Keep unresolved-profile feedback neutral in-app and keep Fallout 4 CBBE calibration-confidence wording in release documentation to satisfy D-06/D-08 while completing PROF-05.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- RED test path initially targeted the test output directory for `ThemeResources.axaml`; corrected the test to read the source AXAML file so the RED failure represented missing resources rather than a bad path.

## Known Stubs

None.

## Threat Flags

None.

## Verification

- `dotnet test --filter FullyQualifiedName~AppShellTests` — passed (19/19).
- `dotnet test` — passed (238/238).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 1 profile correctness UI and release-context requirements are complete. The roadmap can advance to Phase 2 workflow persistence, filtering, and undo hardening.

## Self-Check: PASSED

- Found all modified files and the plan summary on disk.
- Found task commits `5ae7e075` and `48422a51` in git history.

---
*Phase: 01-profile-correctness-and-trust*
*Completed: 2026-04-26*
