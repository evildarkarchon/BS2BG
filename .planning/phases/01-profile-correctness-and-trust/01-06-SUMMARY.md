---
phase: 01-profile-correctness-and-trust
plan: 06
subsystem: testing
tags: [profile-catalog, bos-json, bodygen-export, morph-generation, xunit, fluentassertions]

requires:
  - phase: 01-profile-correctness-and-trust
    provides: Distinct bundled Skyrim CBBE, Skyrim UUNP, and Fallout 4 CBBE profile data and generation services
provides:
  - Profile-specific BoS JSON formatter assertions for all bundled profiles
  - BodyGen export integration coverage combining profile-specific templates and profile-independent morph output
  - Explicit morph-generation test proving preset profile names do not affect morph lines
affects: [profile-correctness-and-trust, template-generation-flow, morph-assignment-flow]

tech-stack:
  added: []
  patterns: [Focused profile-distinguishing tests without golden fixture rebasing]

key-files:
  created:
    - .planning/phases/01-profile-correctness-and-trust/01-06-SUMMARY.md
  modified:
    - tests/BS2BG.Tests/SliderMathFormatterTests.cs
    - tests/BS2BG.Tests/ExportWriterTests.cs
    - tests/BS2BG.Tests/MorphCoreTests.cs

key-decisions:
  - "Profile-specific BoS JSON coverage uses root bundled profile files directly and asserts distinguishing substrings instead of rebasing sacred golden fixtures."
  - "Morph generation remains explicitly profile-independent; tests assert profile-name changes do not alter morph lines."

patterns-established:
  - "Gap-closure tests assert small, profile-distinguishing output fragments rather than broad generated fixture rewrites."

requirements-completed: [PROF-01, PROF-04]

duration: 4 min
completed: 2026-04-26
---

# Phase 01 Plan 06: Profile-Specific Export and Morph Coverage Summary

**Profile-specific BoS JSON and BodyGen export tests now protect bundled profile behavior while documenting morph profile independence.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-26T13:50:06Z
- **Completed:** 2026-04-26T13:54:05Z
- **Tasks:** 2 completed
- **Files modified:** 3

## Accomplishments

- Added `BosJsonUsesBundledProfileSpecificSliderTables` to prove BoS JSON uses root bundled Skyrim CBBE, Skyrim UUNP, and Fallout 4 CBBE slider tables.
- Added `BodyGenExportCombinesProfileSpecificTemplatesWithProfileIndependentMorphs` to verify exported `templates.ini` differs by profile while `morphs.ini` carries assignment lines unchanged by profile metadata.
- Added `MorphGenerationDoesNotDependOnPresetProfileNames` to make morph profile-independence an explicit Core contract.
- Verified sacred golden fixtures under `tests/fixtures/expected/**` were not modified.

## Task Commits

Each task was committed atomically, with TDD RED/GREEN commits for the two test additions:

1. **Task 1 RED: Add profile-specific BoS JSON formatter tests** - `f031c29d` (test)
2. **Task 1 GREEN: Cover profile-specific BoS JSON** - `99a8ec00` (feat)
3. **Task 2 RED: Add export and morph profile tests** - `353995d5` (test)
4. **Task 2 GREEN: Prove profile-aware export coverage** - `932d984f` (feat)

**Plan metadata:** final docs commit records this summary and state/roadmap updates.

## Files Created/Modified

- `tests/BS2BG.Tests/SliderMathFormatterTests.cs` - Adds root-profile loading and BoS JSON assertions for profile-specific FO4-only slider names and shared-slider profile differences.
- `tests/BS2BG.Tests/ExportWriterTests.cs` - Adds integration-style BodyGen export coverage for generated templates and morph assignment files.
- `tests/BS2BG.Tests/MorphCoreTests.cs` - Adds explicit morph-generation profile-independence coverage across `SkyrimCbbe`, `SkyrimUunp`, and `Fallout4Cbbe` constants.
- `.planning/phases/01-profile-correctness-and-trust/01-06-SUMMARY.md` - Records plan execution results.

## Decisions Made

- Profile-specific BoS JSON coverage loads the root bundled JSON files (`settings.json`, `settings_UUNP.json`, `settings_FO4_CBBE.json`) rather than using or rebasing the golden expected corpus.
- Morph generation is tested as profile-independent by design because morph lines depend on assigned preset names and targets/NPCs, not slider calculation profiles.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- TDD RED gates were captured through intentionally failing expected assertions before correcting them to the observed profile-specific outputs.
- `dotnet test` emits an existing CA1861 analyzer warning in `ExportWriterTests.cs`; it does not fail verification and was not in scope for this coverage-only plan.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None.

## Threat Flags

None.

## Verification

- `dotnet test --filter FullyQualifiedName~SliderMathFormatterTests` — Passed: 28/28.
- `dotnet test --filter "FullyQualifiedName~ExportWriterTests|FullyQualifiedName~MorphCoreTests"` — Passed: 31/31.
- `dotnet test --filter "FullyQualifiedName~SliderMathFormatterTests|FullyQualifiedName~ExportWriterTests|FullyQualifiedName~MorphCoreTests"` — Passed: 59/59.
- `dotnet test` — Passed: 242/242.
- `git diff -- tests/fixtures/expected` — No changes.

## TDD Gate Compliance

- RED commits present: `f031c29d`, `353995d5`.
- GREEN commits present after RED: `99a8ec00`, `932d984f`.
- REFACTOR commits: not needed.

## Next Phase Readiness

Plan 06 closes the profile-specific BoS JSON and morph/export coverage gap for PROF-04. Phase 1 can proceed to Plan 07 to reconcile roadmap/requirements warning wording with the locked neutral-fallback decisions.

## Self-Check: PASSED

- Found modified test files: `SliderMathFormatterTests.cs`, `ExportWriterTests.cs`, `MorphCoreTests.cs`.
- Found summary file: `.planning/phases/01-profile-correctness-and-trust/01-06-SUMMARY.md`.
- Found task commits: `f031c29d`, `99a8ec00`, `353995d5`, `932d984f`.

---
*Phase: 01-profile-correctness-and-trust*
*Completed: 2026-04-26*
