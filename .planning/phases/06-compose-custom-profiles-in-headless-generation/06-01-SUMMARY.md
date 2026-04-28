---
phase: 06-compose-custom-profiles-in-headless-generation
plan: 01
subsystem: headless-generation-custom-profiles
tags: [csharp, core, cli, portable-bundle, custom-profiles, request-scoped-catalog, tdd]

requires:
  - phase: 05-automation-sharing-and-release-trust
    provides: CLI generation, portable bundle generation, and request-scoped bundle profile output semantics
  - phase: 04-profile-extensibility-and-controlled-customization
    provides: project-embedded custom profile serialization and save-context resolution
provides:
  - Shared Core request-scoped profile catalog composer for bundled plus referenced custom profile definitions
  - CLI/headless generation validation, BodyGen output, and BoS JSON output through one composed request catalog
  - Portable bundle validation, missing-profile checks, profile zip entries, and generated outputs through the same composer semantics
  - Regression coverage for composer edge cases and embedded custom-profile CLI output bytes
affects: [automation-sharing-and-release-trust, profile-extensibility, cli-automation, portable-bundles, AUTO-01]

tech-stack:
  added: []
  patterns:
    - Request-scoped catalog composition immediately after project load
    - Project-embedded custom profile precedence over save-context definitions
    - Output-byte regression comparison against request-scoped expected generation and bundled fallback divergence

key-files:
  created:
    - src/BS2BG.Core/Generation/RequestScopedProfileCatalogComposer.cs
    - tests/BS2BG.Tests/RequestScopedProfileCatalogComposerTests.cs
    - tests/BS2BG.Tests/TestProfiles.cs
    - .planning/phases/06-compose-custom-profiles-in-headless-generation/06-01-SUMMARY.md
  modified:
    - src/BS2BG.Core/Automation/HeadlessGenerationService.cs
    - src/BS2BG.Core/Bundling/PortableProjectBundleService.cs
    - tests/BS2BG.Tests/CliGenerationTests.cs
    - tests/BS2BG.Tests/PortableBundleServiceTests.cs

key-decisions:
  - "CLI generate uses bundled profiles plus project-embedded custom profile definitions only; no local profile-store lookup or new CLI option was added."
  - "Portable bundle and headless generation now share RequestScopedProfileCatalogComposer so validation and output bytes cannot drift between automation paths."
  - "Project-owned custom profile definitions win over same-name save-context definitions during request-scoped resolution."

patterns-established:
  - "Build one request-scoped catalog after project load and pass that same catalog to validation, templates.ini generation, and BoS JSON writing."
  - "Composer edge cases are covered directly before relying on CLI or bundle integration tests."

requirements-completed: [AUTO-01]

duration: 8 min
completed: 2026-04-28
---

# Phase 06 Plan 01: Compose Custom Profiles in Headless Generation Summary

**CLI and portable bundle automation now generate from embedded custom profile definitions through one shared request-scoped catalog path.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-04-28T06:57:26Z
- **Completed:** 2026-04-28T07:04:58Z
- **Tasks:** 3
- **Files modified:** 7 source/test/planning files

## Accomplishments

- Added `RequestScopedProfileCatalogComposer` as a Core-only helper that keeps bundled profiles first, resolves only referenced custom profile names, filters bundled/blank/unreferenced definitions, deduplicates case-insensitively, and gives project-embedded profiles precedence over save-context profiles.
- Refactored `PortableProjectBundleService` so validation, generated outputs, profile zip entries, and missing-profile checks consume the shared composer semantics instead of private duplicated catalog-building logic.
- Wired `HeadlessGenerationService` to build one request-scoped catalog immediately after project load and use it for validation, `templates.ini`, and BoS JSON generation.
- Added direct composer tests plus service-level and `Program.Main generate` regressions proving embedded custom-profile output bytes match request-scoped expected output and differ from bundled fallback.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Add composer and headless custom-profile regressions** - `1d07a54a` (test)
2. **Task 2 GREEN: Extract shared request-scoped profile catalog composition** - `d565d30f` (feat)
3. **Task 3: Wire headless generation to the request-scoped catalog** - `469ce21a` (fix)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `src/BS2BG.Core/Generation/RequestScopedProfileCatalogComposer.cs` - Shared Core composer for request-scoped bundled plus referenced custom profile catalogs.
- `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` - Uses the composed request catalog for validation, BodyGen templates, and BoS JSON output.
- `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` - Delegates bundle catalog/profile resolution to the shared composer.
- `tests/BS2BG.Tests/RequestScopedProfileCatalogComposerTests.cs` - Covers first-seen ordering, bundled/blank/unreferenced filtering, project-over-save-context precedence, and save-context fallback.
- `tests/BS2BG.Tests/TestProfiles.cs` - Provides shared custom profile, catalog, project, and expected-output test helpers.
- `tests/BS2BG.Tests/CliGenerationTests.cs` - Covers service-level and in-process CLI embedded custom-profile output bytes.
- `tests/BS2BG.Tests/PortableBundleServiceTests.cs` - Covers referenced/deduplicated profile entries and serializes console capture for CLI bundle tests.

## Decisions Made

- Standalone CLI generation remains portable-project scoped: it composes bundled profiles with embedded project custom profiles only, without `%APPDATA%`, `IUserProfileStore`, environment variable, or `--profiles-dir` lookup.
- The composer returns source profile definitions without cloning because catalog construction is read-only and does not mutate project or save-context profile data.
- Missing portable-bundle custom profile detection now checks unresolved preset profile names against the composed request catalog, keeping the missing-profile decision aligned with validation and output generation.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Serialized CLI console-capture tests across CLI and bundle suites**
- **Found during:** Task 3 (Wire headless generation to the request-scoped catalog)
- **Issue:** Running the expanded filtered suite exposed stdout/stderr capture interference between `CliGenerationTests` and `PortableBundleServiceTests` because both call `Program.Main` in-process.
- **Fix:** Added `PortableBundleServiceTests` to the existing `ConsoleCaptureCollection` so `Program.Main` output redirection is serialized across both classes.
- **Files modified:** `tests/BS2BG.Tests/PortableBundleServiceTests.cs`
- **Verification:** Re-ran the targeted suite successfully: 61 passed.
- **Committed in:** `469ce21a`

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** The fix stabilizes the newly expanded CLI regression suite without changing production behavior or expanding Phase 6 scope.

## Issues Encountered

- The initial RED test run failed on the absent `RequestScopedProfileCatalogComposer`, as intended. A test-helper namespace ambiguity was corrected before the RED commit so the remaining failure represented the planned missing production code.
- Targeted test runs emitted existing analyzer warnings in source/test files; the final `dotnet build BS2BG.sln` completed with 0 warnings and 0 errors.

## User Setup Required

None - no external service configuration required.

## Verification

- `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter "FullyQualifiedName~RequestScopedProfileCatalogComposerTests|FullyQualifiedName~CliGenerationTests|FullyQualifiedName~PortableBundleServiceTests"` - passed (61 tests).
- `dotnet build BS2BG.sln` - passed with 0 warnings and 0 errors.
- `dotnet test` - passed (542 tests, 3 skipped).
- Acceptance checks confirmed `HeadlessGenerationService` no longer passes the constructor catalog directly to validation/template generation/BoS writing after project load, and `Program.cs` still has no direct `File.WriteAllText`, `templates.ini`, or `morphs.ini` writer logic.

## TDD Gate Compliance

- RED gate commit present: `1d07a54a`.
- GREEN implementation commits present after RED: `d565d30f`, `469ce21a`.
- Refactor gate: not needed; the shared composer extraction and headless wiring passed targeted tests, build, and full test suite.

## Next Phase Readiness

AUTO-01 is now covered for embedded custom profiles in CLI/headless generation. Phase 7 can build on the same automation service path to replay saved deterministic assignment strategies before output generation.

## Self-Check: PASSED

- Created summary path verified: `.planning/phases/06-compose-custom-profiles-in-headless-generation/06-01-SUMMARY.md`.
- Key source/test files verified present.
- Commits verified in git history: `1d07a54a`, `d565d30f`, `469ce21a`.
- Verification commands passed as documented.

---
*Phase: 06-compose-custom-profiles-in-headless-generation*
*Completed: 2026-04-28*
