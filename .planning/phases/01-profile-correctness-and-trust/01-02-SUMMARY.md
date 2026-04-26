---
phase: 01-profile-correctness-and-trust
plan: 02
subsystem: profile-catalog
flags: [profile-correctness, project-roundtrip, fallback-detection, testing]

requires:
  - phase: 01-profile-correctness-and-trust
    provides: distinct bundled profile catalog baseline from plan 01
provides:
  - Testable catalog contract for detecting unbundled saved profile names without blocking generation fallback
  - Project round-trip proof for unbundled Profile values and legacy isUUNP mapping
affects: [templates-viewmodel-fallback-ui, project-roundtrip, profile-generation]

tech-stack:
  added: []
  patterns:
    - Core catalog lookup helper shares case-insensitive matching with existing fallback generation lookup
    - Unbundled project profile names remain inert serialized strings and are not normalized on load/save

key-files:
  created:
    - tests/BS2BG.Tests/TemplateProfileCatalogTests.cs
  modified:
    - src/BS2BG.Core/Generation/TemplateProfileCatalog.cs
    - tests/BS2BG.Tests/ProjectFileServiceTests.cs

key-decisions:
  - "Keep TemplateProfileCatalog.GetProfile non-throwing for unknown profiles so generation continues to use the default bundled fallback."
  - "Expose unresolved-profile detection through ContainsProfile rather than changing project serialization or generation semantics."

patterns-established:
  - "Catalog detection: App-layer fallback UI should call ContainsProfile while generation paths continue calling GetProfile."
  - "Project round-trip: saved unbundled Profile values are preserved exactly and legacy no-Profile inputs resolve through isUUNP."

requirements-completed: [PROF-02, PROF-03]

duration: 2 min
completed: 2026-04-26
---

# Phase 01 Plan 02: Preserve Legacy/Unbundled Profile Semantics Summary

**Detectable unbundled-profile fallback while preserving legacy `.jbs2bg` profile round trips and non-blocking generation behavior.**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-26T13:21:05Z
- **Completed:** 2026-04-26T13:22:27Z
- **Tasks:** 2 completed
- **Files modified:** 3

## Accomplishments

- Added focused catalog tests proving `Community CBBE` falls back to the default profile for generation while remaining detectable as unbundled.
- Added project serialization tests proving `Profile: "Community CBBE"` loads and saves unchanged, and legacy project JSON without `Profile` maps by `isUUNP` to Skyrim UUNP/Skyrim CBBE.
- Added `TemplateProfileCatalog.ContainsProfile(string? name)` using the same case-insensitive matching semantics as `GetProfile`, while keeping unknown-name fallback to `DefaultProfile` intact.

## Task Commits

Each task was committed atomically:

1. **Task 1: Prove unbundled profile names round-trip and fallback is detectable** - `b2b9c194` (test)
2. **Task 2: Add catalog detection helpers without changing fallback generation** - `4148d96b` (feat)

**Plan metadata:** committed separately after this summary.

_Note: Task 1 intentionally produced the RED TDD commit; the focused test command failed because `ContainsProfile` did not exist yet. Task 2 produced the GREEN implementation commit._

## Files Created/Modified

- `tests/BS2BG.Tests/TemplateProfileCatalogTests.cs` - New catalog tests for unbundled fallback, bundled-profile detection, null/whitespace rejection, and case-insensitive matching.
- `tests/BS2BG.Tests/ProjectFileServiceTests.cs` - Added project round-trip coverage for unbundled saved `Profile` and legacy no-`Profile` `isUUNP` mapping.
- `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs` - Added `ContainsProfile` and shared lookup helper while preserving `return DefaultProfile;` fallback behavior.

## Decisions Made

- Kept `GetProfile` fallback behavior unchanged rather than throwing or normalizing unknown profile names, preserving D-07/D-12 generation compatibility.
- Kept fallback detection as a Core boolean contract so Plan 03 App code can show neutral informational UI without changing serialization or generation paths.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Verification

- `dotnet test --filter "FullyQualifiedName~ProjectFileServiceTests|FullyQualifiedName~TemplateProfileCatalogTests"` — passed after Task 2 (`19` tests).
- `dotnet test` — passed (`230` tests).
- Acceptance checks confirmed:
  - `TemplateProfileCatalogTests.cs` contains `Community CBBE`.
  - `ProjectFileServiceTests.cs` contains a JSON string with `"Profile": "Community CBBE"`.
  - `ProjectFileServiceTests.cs` asserts `ProjectProfileMapping.SkyrimUunp` and `ProjectProfileMapping.SkyrimCbbe` legacy fallback cases.
  - `TemplateProfileCatalog.cs` contains `public bool ContainsProfile(string? name)` and still contains `return DefaultProfile;`.

## TDD Gate Compliance

- RED gate: `b2b9c194 test(01-02): add failing profile fallback tests` — focused test run failed on missing `ContainsProfile` as expected.
- GREEN gate: `4148d96b feat(01-02): expose profile catalog detection` — focused tests and full suite passed.
- REFACTOR gate: Not needed; implementation was already minimal.

## Known Stubs

None.

## Authentication Gates

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 03 can use `TemplateProfileCatalog.ContainsProfile` to detect unresolved saved profiles and show neutral fallback information while keeping generation fallback non-blocking. No blockers remain for this plan.

## Self-Check: PASSED

- Found summary and all created/modified plan files.
- Found task commits `b2b9c194` and `4148d96b` in git history.
- No file deletions were included in task commits.

---
*Phase: 01-profile-correctness-and-trust*
*Completed: 2026-04-26*
