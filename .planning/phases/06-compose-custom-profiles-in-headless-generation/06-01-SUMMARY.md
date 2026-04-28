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
  - Regression coverage for composer edge cases, embedded custom-profile CLI output bytes, unresolved-profile blocking, and bundle profile filename collision handling
affects: [automation-sharing-and-release-trust, profile-extensibility, cli-automation, portable-bundles, AUTO-01]

tech-stack:
  added: []
  patterns:
    - Request-scoped catalog composition immediately after project load
    - Project-embedded custom profile precedence over save-context definitions
    - Output-byte regression comparison against request-scoped expected generation and bundled fallback divergence
    - Validation-blocking unresolved custom profiles before fallback generation can occur

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

duration: 14 min
completed: 2026-04-28
---

# Phase 06 Plan 01: Compose Custom Profiles in Headless Generation Summary

**CLI and portable bundle automation now generate from embedded custom profile definitions through one shared request-scoped catalog path.**

## Performance

- **Duration:** 14 min
- **Started:** 2026-04-28T06:57:26Z
- **Completed:** 2026-04-28T07:10:00Z
- **Tasks:** 3
- **Files modified:** 8 source/test/planning/review files

## Accomplishments

- Added `RequestScopedProfileCatalogComposer` as a Core-only helper that keeps bundled profiles first, resolves only referenced custom profile names, filters bundled/blank/unreferenced definitions, deduplicates case-insensitively, and gives project-embedded profiles precedence over save-context profiles.
- Refactored `PortableProjectBundleService` so validation, generated outputs, profile zip entries, and missing-profile checks consume the shared composer semantics instead of private duplicated catalog-building logic.
- Wired `HeadlessGenerationService` to build one request-scoped catalog immediately after project load and use it for validation, `templates.ini`, and BoS JSON generation.
- Added headless validation blocking for unresolved non-bundled custom profile references so CLI output cannot silently fall back to bundled profile math.
- Added deterministic bundle `profiles/` filename de-duplication for distinct custom profile names that sanitize to the same JSON archive path.
- Added direct composer tests plus service-level and `Program.Main generate` regressions proving embedded custom-profile output bytes match request-scoped expected output and differ from bundled fallback.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Add composer and headless custom-profile regressions** - `1d07a54a` (test)
2. **Task 2 GREEN: Extract shared request-scoped profile catalog composition** - `d565d30f` (feat)
3. **Task 3: Wire headless generation to the request-scoped catalog** - `469ce21a` (fix)
4. **Code review fix: Block unresolved headless custom profiles** - `3d3310c5` (fix)
5. **Code review fix: Deduplicate bundle profile entry names** - `c831c3ef` (fix)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `src/BS2BG.Core/Generation/RequestScopedProfileCatalogComposer.cs` - Shared Core composer for request-scoped bundled plus referenced custom profile catalogs.
- `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` - Uses the composed request catalog for validation, BodyGen templates, and BoS JSON output.
- `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` - Delegates bundle catalog/profile resolution to the shared composer and assigns unique sanitized profile entry names.
- `tests/BS2BG.Tests/RequestScopedProfileCatalogComposerTests.cs` - Covers first-seen ordering, bundled/blank/unreferenced filtering, project-over-save-context precedence, and save-context fallback.
- `tests/BS2BG.Tests/TestProfiles.cs` - Provides shared custom profile, catalog, project, and expected-output test helpers.
- `tests/BS2BG.Tests/CliGenerationTests.cs` - Covers service-level and in-process CLI embedded custom-profile output bytes plus unresolved-profile blocking.
- `tests/BS2BG.Tests/PortableBundleServiceTests.cs` - Covers referenced/deduplicated profile entries, sanitized filename collisions, and serializes console capture for CLI bundle tests.
- `.planning/phases/06-compose-custom-profiles-in-headless-generation/06-REVIEW.md` - Records clean final advisory code review after the blocker fixes.

## Decisions Made

- Standalone CLI generation remains portable-project scoped: it composes bundled profiles with embedded project custom profiles only, without `%APPDATA%`, `IUserProfileStore`, environment variable, or `--profiles-dir` lookup.
- The composer returns source profile definitions without cloning because catalog construction is read-only and does not mutate project or save-context profile data.
- Missing portable-bundle custom profile detection now checks unresolved preset profile names against the composed request catalog, keeping the missing-profile decision aligned with validation and output generation.
- Headless generation treats unresolved non-bundled profile names as validation blockers rather than preserving neutral GUI fallback semantics, because automation output must not silently use wrong profile math.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Serialized CLI console-capture tests across CLI and bundle suites**
- **Found during:** Task 3 (Wire headless generation to the request-scoped catalog)
- **Issue:** Running the expanded filtered suite exposed stdout/stderr capture interference between `CliGenerationTests` and `PortableBundleServiceTests` because both call `Program.Main` in-process.
- **Fix:** Added `PortableBundleServiceTests` to the existing `ConsoleCaptureCollection` so `Program.Main` output redirection is serialized across both classes.
- **Files modified:** `tests/BS2BG.Tests/PortableBundleServiceTests.cs`
- **Verification:** Re-ran the targeted suite successfully: 61 passed.
- **Committed in:** `469ce21a`

**2. [Rule 1 - Bug] Blocked unresolved custom profiles in headless generation**
- **Found during:** Code review gate after Task 3
- **Issue:** A saved project referencing a non-bundled custom profile that was not embedded could still validate and generate with bundled fallback profile math.
- **Fix:** Added a headless missing-profile preflight after request catalog composition and before overwrite/output writes, plus service-level and `Program.Main` regressions.
- **Files modified:** `src/BS2BG.Core/Automation/HeadlessGenerationService.cs`, `tests/BS2BG.Tests/CliGenerationTests.cs`
- **Verification:** Re-ran targeted suite successfully: 63 passed.
- **Committed in:** `3d3310c5`

**3. [Rule 1 - Bug] Deduplicated sanitized portable-bundle profile entry filenames**
- **Found during:** Code review gate after unresolved-profile fix
- **Issue:** Distinct valid custom profile names could sanitize to the same `profiles/*.json` archive path and turn an otherwise valid bundle into `IoFailure`.
- **Fix:** Added deterministic suffixing for duplicate sanitized profile filenames and a regression with colliding names.
- **Files modified:** `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs`, `tests/BS2BG.Tests/PortableBundleServiceTests.cs`
- **Verification:** Re-ran targeted suite successfully: 64 passed.
- **Committed in:** `c831c3ef`

---

**Total deviations:** 3 auto-fixed (3 bugs)
**Impact on plan:** All fixes preserve the Phase 6 trust boundary by preventing silent wrong-profile output or avoidable bundle failures without adding external profile lookup or changing formatter/export semantics.

## Issues Encountered

- The initial RED test run failed on the absent `RequestScopedProfileCatalogComposer`, as intended. A test-helper namespace ambiguity was corrected before the RED commit so the remaining failure represented the planned missing production code.
- Advisory code review initially found two blocker-class issues; both were fixed and the final review status is clean.
- Targeted test runs emitted existing analyzer warnings in source/test files; the final `dotnet build BS2BG.sln` completed with 0 warnings and 0 errors.

## User Setup Required

None - no external service configuration required.

## Verification

- `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter "FullyQualifiedName~RequestScopedProfileCatalogComposerTests|FullyQualifiedName~CliGenerationTests|FullyQualifiedName~PortableBundleServiceTests"` - passed (64 tests after review fixes).
- `dotnet build BS2BG.sln` - passed with 0 warnings and 0 errors.
- `dotnet test` - passed (545 tests, 3 skipped).
- Code review gate - final status clean in `06-REVIEW.md`.
- Acceptance checks confirmed `HeadlessGenerationService` no longer passes the constructor catalog directly to validation/template generation/BoS writing after project load, and `Program.cs` still has no direct `File.WriteAllText`, `templates.ini`, or `morphs.ini` writer logic.

## TDD Gate Compliance

- RED gate commit present: `1d07a54a`.
- GREEN implementation commits present after RED: `d565d30f`, `469ce21a`, `3d3310c5`, `c831c3ef`.
- Refactor gate: not needed; the shared composer extraction and headless wiring passed targeted tests, build, and full test suite.

## Next Phase Readiness

AUTO-01 is now covered for embedded custom profiles in CLI/headless generation. Phase 7 can build on the same automation service path to replay saved deterministic assignment strategies before output generation.

## Self-Check: PASSED

- Created summary path verified: `.planning/phases/06-compose-custom-profiles-in-headless-generation/06-01-SUMMARY.md`.
- Key source/test files verified present.
- Commits verified in git history: `1d07a54a`, `d565d30f`, `469ce21a`, `3d3310c5`, `c831c3ef`.
- Verification commands passed as documented.

---
*Phase: 06-compose-custom-profiles-in-headless-generation*
*Completed: 2026-04-28*
