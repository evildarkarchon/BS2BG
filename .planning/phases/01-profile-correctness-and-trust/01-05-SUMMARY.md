---
phase: 01-profile-correctness-and-trust
plan: 05
subsystem: ui
tags: [avalonia, reactiveui, profiles, viewmodel, tdd]

requires:
  - phase: 01-profile-correctness-and-trust
    provides: Neutral unresolved-profile fallback visibility and saved-profile preservation from plans 03-04
provides:
  - Explicit bundled-profile adoption path for the displayed fallback profile
  - Separate calculation-profile helper for unresolved saved profile fallback math
  - Regression coverage for selector adoption without warning or mismatch UX
affects: [profile-correctness-and-trust, template-generation-flow, project-roundtrip]

tech-stack:
  added: []
  patterns:
    - TDD RED/GREEN commits for ViewModel profile-selector behavior
    - Separate saved-profile preservation from effective calculation-profile resolution

key-files:
  created:
    - .planning/phases/01-profile-correctness-and-trust/01-05-SUMMARY.md
  modified:
    - tests/BS2BG.Tests/TemplatesViewModelTests.cs
    - src/BS2BG.App/ViewModels/TemplatesViewModel.cs

key-decisions:
  - "Keep the profile selector empty for unbundled saved profile names so choosing a bundled profile is always an explicit adoption action."
  - "Use GetSelectedCalculationProfile for preview, BoS JSON, missing-default, and inspector math while preserving the saved unbundled profile string."

patterns-established:
  - "Unresolved saved profile preservation: UI selection state remains blank until the user chooses a bundled profile."
  - "Fallback math helper: ViewModel consumers request the calculation profile without mutating SliderPreset.ProfileName."

requirements-completed: [PROF-02, PROF-03, PROF-04]

duration: 2 min
completed: 2026-04-26
---

# Phase 01 Plan 05: Fallback Profile Adoption Summary

**Unbundled saved profiles now preserve round-trip names while fallback math stays active until an explicit bundled selector choice adopts the profile.**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-26T13:46:49Z
- **Completed:** 2026-04-26T13:48:30Z
- **Tasks:** 2 completed
- **Files modified:** 3

## Accomplishments

- Added RED regression coverage proving the verified gap: selecting an unresolved `Community CBBE` preset leaves the selector blank, keeps fallback information visible, and still uses Skyrim CBBE fallback math for preview and selected BoS JSON.
- Implemented `GetSelectedCalculationProfile()` so preview, BoS JSON, missing-default refresh, and inspector rows use fallback calculation rules without rewriting `SliderPreset.ProfileName`.
- Preserved neutral, warning-free fallback UX while enabling explicit adoption of the displayed fallback profile via `SelectedProfileName = ProjectProfileMapping.SkyrimCbbe`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add failing tests for explicit fallback-profile adoption** - `427ce28f` (test)
2. **Task 2: Separate fallback calculation from selector adoption** - `1457f981` (feat)

**Plan metadata:** committed after this summary is written.

## Files Created/Modified

- `tests/BS2BG.Tests/TemplatesViewModelTests.cs` - Added `ChoosingDisplayedFallbackProfileAdoptsBundledProfile` and strengthened explicit bundled profile adoption assertions.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` - Added calculation-profile helper and changed unresolved saved-profile sync to leave selector state empty until explicit adoption.
- `.planning/phases/01-profile-correctness-and-trust/01-05-SUMMARY.md` - Captures execution outcome, commits, verification, and self-check.

## Decisions Made

- Keep unbundled saved profile names out of the selector state rather than adding a separate button or warning UX; this makes the existing ComboBox selection path the explicit adoption mechanism.
- Centralize fallback math through `GetSelectedCalculationProfile()` so future preview/inspector call sites can preserve saved-profile semantics consistently.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Verification

- RED: `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` failed before implementation on `Expected viewModel.SelectedProfileName to be empty, but found "Skyrim CBBE".`
- GREEN focused test run: `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` passed 16/16.
- Plan-level verification: `dotnet test` passed 239/239.
- Acceptance checks confirmed required test/implementation markers and no new `mismatch` or `experimental` strings in `TemplatesViewModel.cs`.

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 05 closes the WR-01 fallback adoption gap. Phase 01 can continue to plan 06 for profile-specific BoS JSON, BodyGen export, and morph profile-independence test coverage.

## Self-Check: PASSED

- `FOUND: .planning/phases/01-profile-correctness-and-trust/01-05-SUMMARY.md`
- `FOUND: 427ce28f`
- `FOUND: 1457f981`

---
*Phase: 01-profile-correctness-and-trust*
*Completed: 2026-04-26*
