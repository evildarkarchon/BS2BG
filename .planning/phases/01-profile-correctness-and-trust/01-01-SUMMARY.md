---
phase: 01-profile-correctness-and-trust
plan: 01
subsystem: profile-catalog
tags: [csharp, dotnet, profiles, fallout4-cbbe, testing]

requires:
  - phase: project-initialization
    provides: Profile correctness roadmap and Phase 1 decisions
provides:
  - Distinct root-level Fallout 4 CBBE profile JSON
  - App catalog registration for all bundled profiles
  - Tests proving Fallout 4 CBBE uses FO4-only defaults and no inverted sliders
affects: [profile-generation, template-generation-flow, phase-1]

tech-stack:
  added: []
  patterns:
    - Root-level bundled profile JSON copied as app content
    - Factory-level profile registration verified by focused xUnit tests

key-files:
  created:
    - settings_FO4_CBBE.json
    - tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs
  modified:
    - src/BS2BG.App/BS2BG.App.csproj
    - src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs
    - tests/BS2BG.Tests/TemplateGenerationServiceTests.cs

key-decisions:
  - "Keep the Phase 1 profile layout at repository root and register Fallout 4 CBBE through settings_FO4_CBBE.json."
  - "Verify default Fallout 4 generation through focused assertions instead of rebasing sacred golden expected files."

patterns-established:
  - "Bundled profile additions require content-copy wiring and factory registration tests."
  - "FO4 CBBE seed data uses neutral 1.0 defaults/multipliers with an empty inverted list until calibrated data exists."

requirements-completed: [PROF-01, PROF-04]

duration: 3 min
completed: 2026-04-26
---

# Phase 01 Plan 01: Distinct Fallout 4 CBBE Profile Summary

**Fallout 4 CBBE now loads from its own bundled neutral profile JSON instead of reusing Skyrim CBBE data.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-26T13:15:14Z
- **Completed:** 2026-04-26T13:18:11Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Added `settings_FO4_CBBE.json` at the repository root with seeded FO4 CBBE defaults, multipliers, and an empty inverted list.
- Registered the FO4 profile as app content and changed the default catalog to load it through `ProjectProfileMapping.Fallout4Cbbe`.
- Added focused factory tests proving bundled profile order, FO4-only default coverage, neutral multipliers, and no inverted FO4 sliders.

## Task Commits

Each task was committed atomically:

1. **Task 1: Prove FO4 CBBE loads from a distinct bundled profile** - `03ba0137` (test)
2. **Task 2: Add root-level FO4 profile JSON and app catalog wiring** - `74330d2a` (feat)

**Plan metadata:** committed after state/roadmap/requirements updates.

_Note: TDD task flow produced a RED test commit followed by the GREEN implementation commit._

## Files Created/Modified

- `settings_FO4_CBBE.json` - Distinct bundled Fallout 4 CBBE profile with neutral defaults/multipliers and no inverted sliders.
- `tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs` - Factory coverage for profile order and distinct FO4 profile data.
- `src/BS2BG.App/BS2BG.App.csproj` - Content-copy registration for the FO4 profile JSON.
- `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs` - Catalog registration now loads FO4 from `settings_FO4_CBBE.json`.
- `tests/BS2BG.Tests/TemplateGenerationServiceTests.cs` - Default FO4 generation assertion updated to verify factory-registered FO4 output without changing golden fixtures.

## Decisions Made

- Kept Phase 1 profile files at repository root per D-01/D-03 rather than introducing a `profiles/` folder.
- Used neutral FO4 seed values (`1.0` defaults and multipliers, empty `Inverted`) per D-02 until future authoritative calibration data exists.
- Preserved sacred golden expected files and changed the default-catalog FO4 generation test to assert the new distinct profile behavior directly.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Replaced obsolete FO4 golden comparison for the default catalog test**
- **Found during:** Task 2 (Add root-level FO4 profile JSON and app catalog wiring)
- **Issue:** Once `Fallout 4 CBBE` correctly loaded the distinct FO4 profile, `GenerateTemplatesUsesDefaultFallout4ProfileSettings` no longer matched the existing golden output that reflected the prior Skyrim-profile reuse behavior.
- **Fix:** Updated the test to verify FO4-only generated output through the default catalog without editing `tests/fixtures/expected/**`.
- **Files modified:** `tests/BS2BG.Tests/TemplateGenerationServiceTests.cs`
- **Verification:** `dotnet test --filter "FullyQualifiedName~TemplateProfileCatalogFactoryTests|FullyQualifiedName~TemplateGenerationServiceTests"` and `dotnet test`
- **Committed in:** `74330d2a`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** The adjustment preserved the no-golden-edits rule while keeping test coverage aligned with the new distinct FO4 profile behavior.

## Issues Encountered

- A full `dotnet test` run initially hit an MSBuild file-lock because it was started in parallel with the focused test run. Re-running `dotnet test` after the focused run completed passed.

## Verification

- `dotnet test --filter "FullyQualifiedName~TemplateProfileCatalogFactoryTests|FullyQualifiedName~TemplateGenerationServiceTests"` — Passed (10 tests).
- `dotnet test` — Passed (220 tests).

## Known Stubs

None.

## Authentication Gates

None.

## TDD Gate Compliance

- RED gate: `03ba0137` (`test(01-01): add failing FO4 catalog profile tests`) — focused tests failed before implementation for missing FO4-only defaults and Skyrim CBBE inverted reuse.
- GREEN gate: `74330d2a` (`feat(01-01): add distinct Fallout 4 CBBE profile`) — focused and full test suites passed after implementation.
- REFACTOR gate: Not needed.

## Self-Check: PASSED

- Found key files: `settings_FO4_CBBE.json`, `BS2BG.App.csproj`, `TemplateProfileCatalogFactory.cs`, `TemplateProfileCatalogFactoryTests.cs`, `TemplateGenerationServiceTests.cs`.
- Found task commits: `03ba0137`, `74330d2a`.

## Next Phase Readiness

Ready for `01-02-PLAN.md`; the catalog now has a distinct FO4 profile path for downstream legacy/unbundled profile semantics work.

---
*Phase: 01-profile-correctness-and-trust*
*Completed: 2026-04-26*
