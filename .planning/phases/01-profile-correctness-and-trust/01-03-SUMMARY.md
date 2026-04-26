---
phase: 01-profile-correctness-and-trust
plan: 03
subsystem: ui
tags: [avalonia, reactiveui, profiles, templates, tests]

requires:
  - phase: 01-profile-correctness-and-trust
    provides: Distinct bundled profile catalog and fallback detection from plans 01-02
provides:
  - Selected-profile BodySlide XML import ViewModel coverage
  - Neutral unresolved saved-profile fallback ViewModel state
  - Tests proving unresolved profiles preserve saved names until explicit bundled selection
affects: [template-generation-flow, reactive-mvvm-conventions, phase-01-ui]

tech-stack:
  added: []
  patterns:
    - ReactiveUI source-generated read-only ViewModel state
    - ViewModel-level neutral information copy for unresolved saved profiles

key-files:
  created:
    - .planning/phases/01-profile-correctness-and-trust/01-03-SUMMARY.md
  modified:
    - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
    - tests/BS2BG.Tests/TemplatesViewModelTests.cs

key-decisions:
  - "Unbundled saved profile names stay on the selected preset while the selector resolves to bundled fallback math."
  - "Unresolved profile feedback is neutral ViewModel information, not warning, mismatch, or experimental status text."

patterns-established:
  - "Fallback profile display is derived from TemplateProfileCatalog.ContainsProfile/GetProfile and does not infer profile from paths or sliders."
  - "Explicit bundled profile selection is the only ViewModel action that overwrites an unbundled saved preset profile name."

requirements-completed: [PROF-02, PROF-03, PROF-04]

duration: 3 min
completed: 2026-04-26
---

# Phase 01 Plan 03: Selected-Profile Import and Neutral Fallback Summary

**Templates ViewModel now imports XML using the active profile and surfaces non-blocking fallback information for unbundled saved profiles while preserving round-trip profile names.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-26T13:25:12Z
- **Completed:** 2026-04-26T13:27:58Z
- **Tasks:** 2 completed
- **Files modified:** 2 code/test files plus this summary

## Accomplishments

- Added focused ViewModel tests proving BodySlide XML imports use `Fallout 4 CBBE` from the selector instead of path or slider inference.
- Added tests for exact neutral unresolved-profile copy, no warning/mismatch/experimental language, fallback generation success, and explicit bundled profile override behavior.
- Implemented `ProfileFallbackInformationText` and `IsProfileFallbackInformationVisible` in `TemplatesViewModel` while preserving unbundled saved profile names until the user chooses a bundled profile.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add ViewModel tests for selected-profile import and neutral fallback** - `f2a269e0` (test)
2. **Task 2: Implement neutral fallback state without overwriting saved profile names** - `1feff2fb` (feat)

**Plan metadata:** pending final docs commit

_Note: Task 1 followed the RED gate: the new tests failed because `TemplatesViewModel` did not yet expose fallback information properties._

## Files Created/Modified

- `tests/BS2BG.Tests/TemplatesViewModelTests.cs` - Adds profile-selection import, unresolved fallback, warning-language, and explicit bundled override tests.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` - Adds neutral fallback information state and resolves selector math through bundled catalog profiles without rewriting unbundled saved names.
- `.planning/phases/01-profile-correctness-and-trust/01-03-SUMMARY.md` - Records execution results and verification evidence.

## Decisions Made

- Unbundled saved profile names remain on `SelectedPreset.ProfileName`; `SelectedProfileName` resolves to the catalog fallback for preview/generation semantics.
- Fallback copy is intentionally neutral and uses `TemplateProfileCatalog.ContainsProfile` plus `GetProfile` rather than path, game-folder, slider-name, mismatch, or experimental heuristics.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Authentication Gates

None.

## Known Stubs

None.

## Threat Flags

None.

## Verification

- `dotnet test --filter FullyQualifiedName~TemplatesViewModelTests` — passed (15/15).
- `dotnet test` — passed (234/234).
- Acceptance checks confirmed:
  - `ProjectProfileMapping.Fallout4Cbbe` appears in selected-profile import tests.
  - Exact fallback text beginning `Saved profile "Community CBBE" is not bundled.` appears in tests.
  - Case-insensitive negative assertions cover `warning`, `mismatch`, and `experimental`.
  - `TemplatesViewModel.cs` contains `ProfileFallbackInformationText`, `IsProfileFallbackInformationVisible`, and `is not bundled. BS2BG is using`.
  - `TemplatesViewModel.cs` contains no new `mismatch` or `experimental` strings.

## TDD Gate Compliance

- RED: `f2a269e0` added failing tests for selected-profile import and neutral fallback.
- GREEN: `1feff2fb` implemented ViewModel fallback behavior and made the tests pass.
- REFACTOR: Not needed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 04 can bind the new ViewModel properties into the Avalonia UI and add release-facing profile notes. The core ViewModel contract for neutral fallback text and visibility is ready.

## Self-Check: PASSED

- Found `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`.
- Found `tests/BS2BG.Tests/TemplatesViewModelTests.cs`.
- Found `.planning/phases/01-profile-correctness-and-trust/01-03-SUMMARY.md`.
- Found task commits `f2a269e0` and `1feff2fb` in git history.

---
*Phase: 01-profile-correctness-and-trust*
*Completed: 2026-04-26*
