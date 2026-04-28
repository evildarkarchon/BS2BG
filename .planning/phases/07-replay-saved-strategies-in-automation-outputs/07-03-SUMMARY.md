---
phase: 07-replay-saved-strategies-in-automation-outputs
plan: 03
subsystem: portable-bundle-automation
tags: [csharp, core, cli, avalonia, portable-bundles, bodygen, assignment-strategy, deterministic-replay, tdd]

requires:
  - phase: 07-replay-saved-strategies-in-automation-outputs
    provides: Core AssignmentStrategyReplayService and CLI replay reporting from Plans 01-02
provides:
  - Portable bundle BodyGen/all generation replaying saved assignment strategies from cloned request-scoped state
  - Source-state `project/project.jbs2bg` preservation while generated `morphs.ini` uses replayed assignments
  - Explicit replay report text on preview/result contracts plus `reports/replay.txt` manifest/checksum coverage on replayed bundles
affects: [portable-bundles, automation, cli-bundle, bodygen, AUTO-02, AUTO-03]

tech-stack:
  added: []
  patterns:
    - Constructor-injected AssignmentStrategyReplayService in PortableProjectBundleService
    - Replay-before-validation and replay-before-entry staging for bundle BodyGen output
    - Non-positional result/preview replay report contract with optional report artifact

key-files:
  created:
    - .planning/phases/07-replay-saved-strategies-in-automation-outputs/07-03-SUMMARY.md
  modified:
    - src/BS2BG.Core/Bundling/PortableProjectBundleService.cs
    - src/BS2BG.Core/Bundling/PortableProjectBundleContracts.cs
    - src/BS2BG.Cli/Program.cs
    - src/BS2BG.App/AppBootstrapper.cs
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - tests/BS2BG.Tests/PortableBundleServiceTests.cs

key-decisions:
  - "Portable bundle BodyGen replay uses AssignmentStrategyReplayService with cloneBeforeReplay:true so bundle generation cannot mutate caller project state or rewrite the bundled project entry."
  - "Portable bundle preview/result contracts expose ReplayReportText as non-positional init properties, keeping existing constructor call sites stable while making replay status explicit."
  - "Successful saved-strategy bundle replay writes reports/replay.txt and manifest/checksum entries; no-strategy and BoS-only paths report no replay without adding a bundle report artifact."

patterns-established:
  - "Bundle replay blockers return ValidationBlocked before staging output entries or creating a zip, matching CLI fail-before-write semantics."
  - "Bundle replay tests compare generated morph bytes with CLI output while asserting templates and BoS JSON remain byte-equivalent to source-state generation."

requirements-completed: [AUTO-02, AUTO-03]

duration: 10 min
completed: 2026-04-28
---

# Phase 07 Plan 03: Replay Saved Strategies in Portable Bundle Outputs Summary

**Portable bundles now replay saved assignment strategies for generated BodyGen morph output while preserving the bundled source project file unchanged.**

## Performance

- **Duration:** 10 min
- **Started:** 2026-04-28T09:13:30Z
- **Completed:** 2026-04-28T09:23:43Z
- **Tasks:** 2
- **Files modified:** 7 files including this summary

## Accomplishments

- Added RED bundle regressions for stale assignments, seeded repeatability, CLI/bundle morph byte parity, source project preservation, caller no-mutation, BoS-only/no-strategy behavior, replay blockers, preview reporting, and manifest/checksum coverage.
- Injected `AssignmentStrategyReplayService` into `PortableProjectBundleService` and all CLI/App/test composition paths.
- Reordered bundle planning so saved-strategy replay happens on a cloned working project before validation, generated output staging, manifest construction, and zip creation.
- Added explicit `ReplayReportText` init properties to bundle preview/result contracts and wrote `reports/replay.txt` only for successful saved-strategy replay.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Add bundle replay regressions** - `e7b4186d` (test)
2. **Task 2 GREEN: Replay strategies in portable bundles** - `266e20d8` (feat)

**Plan metadata:** pending final docs commit

_Note: This plan followed the TDD test → feat gate sequence._

## Files Created/Modified

- `tests/BS2BG.Tests/PortableBundleServiceTests.cs` - Added bundle replay fixtures and regressions covering output bytes, project entry preservation, caller mutation, replay reports, blockers, no-strategy behavior, and manifest checksums.
- `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` - Replays saved strategies through the Core replay seam, blocks bad replay before zip planning, emits explicit replay text, and generates bundle output from request-scoped state.
- `src/BS2BG.Core/Bundling/PortableProjectBundleContracts.cs` - Adds non-positional `ReplayReportText` init properties to preview and result records.
- `src/BS2BG.Cli/Program.cs` - Composes the replay-aware bundle service and prints replay report text on bundle command output.
- `src/BS2BG.App/AppBootstrapper.cs` - Registers `AssignmentStrategyReplayService` and supplies it to the bundle service singleton.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Updates fallback bundle service construction and GUI bundle summaries for replay report text.
- `.planning/phases/07-replay-saved-strategies-in-automation-outputs/07-03-SUMMARY.md` - Execution outcome documentation.

## Decisions Made

- Used `cloneBeforeReplay: true` for bundle generation so `project/project.jbs2bg` continues to serialize `request.Project` while output generation can safely consume `replayResult.Project`.
- Kept `reports/replay.txt` conditional on `replayResult.Replayed == true`; no-strategy and BoS-only paths expose the no-replay message via `ReplayReportText` but do not add a misleading report file.
- Printed replay report text from the CLI bundle command so automation users receive the same explicit replay status as GUI previews/results.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- RED tests failed for the intended reasons: bundle output used stale assignments, blocked replay still succeeded, `reports/replay.txt` was absent, and `ReplayReportText` did not exist yet.
- Targeted test runs continue to emit pre-existing analyzer warnings in unrelated files and test helper patterns; the portable bundle suite passed.

## User Setup Required

None - no external service configuration required.

## Verification

- `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter FullyQualifiedName~PortableBundleServiceTests` - passed (39 tests).
- Acceptance checks confirmed `PortableBundleServiceTests.cs` mentions `AssignmentStrategy`, `Seed`, `outputs/bodygen/morphs.ini`, `reports/replay.txt`, `project/project.jbs2bg`, `ValidationBlocked`, `ReplayReportText`, `ChangeVersion`, no saved assignment strategy behavior, and stale/source assignment preservation.
- Acceptance checks confirmed `PortableProjectBundleService.cs` contains `AssignmentStrategyReplayService replayService`, `PrepareForBodyGen(request.Project, request.Intent, cloneBeforeReplay: true)`, `SaveToString(request.Project, request.SaveContext)`, `reports/replay.txt`, and `PortableProjectBundleOutcome.ValidationBlocked` handling.

## TDD Gate Compliance

- RED gate commit present: `e7b4186d`.
- GREEN gate commit present after RED: `266e20d8`.
- Refactor gate: not needed; implementation passed the targeted portable bundle suite.

## Known Stubs

None - scanned changed source/test files for TODO/FIXME/placeholders and hardcoded empty UI data-source patterns; no goal-blocking stubs were introduced.

## Threat Flags

None - replay report/manifest sharing surface was already covered by the plan threat model and remains path-scrubbed.

## Next Phase Readiness

Phase 7 is complete. The reusable replay seam now covers direct Core, CLI/headless generation, and portable bundle generation, with blockers and reporting aligned across automation outputs.

## Self-Check: PASSED

- Created summary path verified: `.planning/phases/07-replay-saved-strategies-in-automation-outputs/07-03-SUMMARY.md`.
- Key source/test files verified present: `PortableProjectBundleService.cs`, `PortableProjectBundleContracts.cs`, `Program.cs`, `AppBootstrapper.cs`, `MainWindowViewModel.cs`, and `PortableBundleServiceTests.cs`.
- Commits verified in git history: `e7b4186d`, `266e20d8`.
- Verification command passed as documented.

---
*Phase: 07-replay-saved-strategies-in-automation-outputs*
*Completed: 2026-04-28*
