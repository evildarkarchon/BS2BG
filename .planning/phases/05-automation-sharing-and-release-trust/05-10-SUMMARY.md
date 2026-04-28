---
phase: 05-automation-sharing-and-release-trust
plan: 10
subsystem: portable-bundles
tags: [csharp, core, cli, avalonia, portable-bundle, custom-profiles, atomic-write]

requires:
  - phase: 05-06
    provides: Core PortableProjectBundleService zip layout, manifest, profile-copy, validation, and output writer integration
  - phase: 05-07
    provides: CLI and Avalonia bundle command surfaces using the Core bundle service
provides:
  - Atomic portable bundle replacement that preserves existing bundles on failed final commits
  - Request-scoped bundle validation and generation catalogs using referenced embedded/local custom profiles
  - Stable CLI bundle expected-failure mapping for missing/malformed projects, missing assets, and filesystem failures
affects: [automation-sharing-and-release-trust, cli-automation, support-bundles, profile-extensibility, release-trust]

tech-stack:
  added: []
  patterns:
    - Same-directory temp zip staging with injectable final-commit seam for deterministic overwrite-failure tests
    - Request-scoped catalog construction from bundled entries plus referenced CustomProfileDefinition data
    - CLI expected-failure wrappers that print concise automation-safe stderr without stack traces

key-files:
  created:
    - .planning/phases/05-automation-sharing-and-release-trust/05-10-SUMMARY.md
  modified:
    - src/BS2BG.Core/Bundling/PortableProjectBundleService.cs
    - src/BS2BG.Cli/Program.cs
    - src/BS2BG.App/AppBootstrapper.cs
    - tests/BS2BG.Tests/PortableBundleServiceTests.cs
    - tests/BS2BG.Tests/CliGenerationTests.cs

key-decisions:
  - "Portable bundle overwrite commits use File.Replace/File.Move only after a temp zip is fully closed, preserving prior bundles on final-commit failure."
  - "Portable bundle generation resolves custom profiles once per request and reuses that profile set for missing-profile checks, zip profile entries, validation, templates.ini, and BoS JSON."
  - "CLI bundle expected failures are mapped at command boundaries so automation receives stable exit codes and no implementation stack traces."

patterns-established:
  - "Core bundle tests inject final commit failure through an internal seam instead of relying on nondeterministic filesystem races."
  - "Avalonia bundle composition keeps the service singleton while supplying local/project profiles through BuildProjectSaveContext per request."

requirements-completed: [AUTO-02, AUTO-01]

duration: 5 min
completed: 2026-04-28
---

# Phase 05 Plan 10: Portable Bundle Trust Gap Closure Summary

**Portable bundle creation now preserves existing zips on failed overwrite commits, generates bundle outputs from copied custom profile data, and maps expected CLI bundle failures to stable automation exit codes.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-28T05:26:25Z
- **Completed:** 2026-04-28T05:31:11Z
- **Tasks:** 3
- **Files modified:** 5 source/test files plus this summary

## Accomplishments

- Added regression coverage for the verification gaps: deterministic final-commit failure, request-scoped local/embedded profile output bytes, concise CLI bundle failure handling, and GUI save-context profile composition.
- Reworked `PortableProjectBundleService.Create` to write archives to a same-directory temp path and commit with `File.Replace`/`File.Move` only after the zip closes successfully.
- Centralized bundle profile resolution through `ResolveBundleProfileSet` so missing-profile checks, profile zip entries, validation, `templates.ini`, and BoS JSON use the same referenced custom profile data.
- Wrapped CLI bundle command setup/create failures into `AutomationExitCode.UsageError` or `AutomationExitCode.IoFailure` with concise stderr messages that avoid stack traces and exception type names.
- Documented the App singleton registration strategy: the singleton keeps the bundled/base catalog while `MainWindowViewModel.BuildProjectSaveContext()` supplies local/project custom profiles per request.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Regression tests for bundle trust gaps** - `c278a559` (test)
2. **Task 2: Atomic bundle replacement and request-scoped custom profiles** - `214b7976` (feat)
3. **Task 3: CLI/App bundle hardening** - `ddb51a22` (fix)

**Plan metadata:** pending final docs commit

_Note: Task 1 used a TDD RED commit; Tasks 2 and 3 supplied the GREEN implementation and app/CLI hardening._

## Files Created/Modified

- `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` - Adds same-directory temp zip staging, final commit seam, request-scoped catalog creation, and single-source custom profile resolution.
- `src/BS2BG.Cli/Program.cs` - Handles expected bundle project-load and filesystem/profile-asset failures with stable exit codes and concise stderr.
- `src/BS2BG.App/AppBootstrapper.cs` - Documents why the singleton bundle service receives only the base catalog and per-request save context supplies custom profiles.
- `tests/BS2BG.Tests/PortableBundleServiceTests.cs` - Covers overwrite preservation, local/embedded custom profile output bytes, and GUI bundle generation through `BuildProjectSaveContext`.
- `tests/BS2BG.Tests/CliGenerationTests.cs` - Covers missing/malformed project, missing bundled profile assets, and directory target CLI bundle failures without stack traces.

## Decisions Made

- Portable bundle overwrite safety is enforced at the final commit step instead of relying on direct final-path writes or pre-delete behavior.
- Bundle custom profile resolution is single-sourced from the project plus save context and then projected into both generation catalogs and zip profile entries.
- CLI bundle failure reporting favors stable automation messages over detailed exception output to avoid leaking implementation paths or private install locations.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Targeted test runs emitted existing analyzer warnings in unrelated test/source files and new CA1861/CA1822 suggestions in test code and CLI helper code; the final `dotnet build BS2BG.sln` completed with 0 warnings and 0 errors.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None.

## Threat Flags

None. The filesystem overwrite, custom-profile generation catalog, and CLI failure-message trust boundaries were identified in the plan threat model and mitigated in this plan.

## Verification

- `dotnet test BS2BG.sln --filter "FullyQualifiedName~PortableBundle|FullyQualifiedName~CliGeneration"` - passed (54 tests).
- `dotnet build BS2BG.sln` - passed with 0 warnings and 0 errors.
- Acceptance checks confirmed `File.Replace`/`File.Move`, no final-path deletion in catch paths, `ResolveBundleProfileSet`, request-scoped catalog usage, CLI `AutomationExitCode.UsageError`/`IoFailure` handling, and AppBootstrapper `BuildProjectSaveContext` singleton-scope comment.

## TDD Gate Compliance

- RED commit present: `c278a559`.
- GREEN implementation commits present after RED: `214b7976`, `ddb51a22`.
- Refactor gate: not needed; final targeted tests and full build passed.

## Next Phase Readiness

AUTO-02 is no longer blocked by the phase verification gaps: portable bundles preserve previous zips on failed overwrite replacement, generated bundle outputs use the same custom profile definitions copied into `profiles/`, and CLI bundle automation returns stable expected-failure exit codes.

## Self-Check: PASSED

- Created summary path verified: `.planning/phases/05-automation-sharing-and-release-trust/05-10-SUMMARY.md`.
- Key source/test files verified present.
- Commits verified in git history: `c278a559`, `214b7976`, `ddb51a22`.
- Verification commands passed as documented.

---
*Phase: 05-automation-sharing-and-release-trust*
*Completed: 2026-04-28*
