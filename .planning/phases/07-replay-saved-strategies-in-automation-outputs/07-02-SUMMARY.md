---
phase: 07-replay-saved-strategies-in-automation-outputs
plan: 02
subsystem: cli-automation
tags: [csharp, core, cli, automation, bodygen, assignment-strategy, deterministic-replay, tdd]

requires:
  - phase: 07-replay-saved-strategies-in-automation-outputs
    provides: Core AssignmentStrategyReplayService and replay result contract from Plan 01
provides:
  - CLI/headless BodyGen and all-intent generation replay saved assignment strategies before validation, overwrite checks, and writes
  - ValidationBlocked replay blocker with actionable NPC identity fields and no private project/output path leakage
  - Concise CLI stdout replay summary for successful saved-strategy generation
affects: [automation, cli-generation, bodygen, morph-assignment-flow, AUTO-03]

tech-stack:
  added: []
  patterns:
    - Constructor-injected Core replay seam in HeadlessGenerationService
    - Replay-before-validation and replay-before-overwrite ordering for assignment-dependent output
    - Path-scrubbed blocked replay messages with concise success summary text

key-files:
  created:
    - .planning/phases/07-replay-saved-strategies-in-automation-outputs/07-02-SUMMARY.md
  modified:
    - src/BS2BG.Core/Automation/HeadlessGenerationService.cs
    - src/BS2BG.Cli/Program.cs
    - tests/BS2BG.Tests/CliGenerationTests.cs

key-decisions:
  - "Headless generation replays saved strategies on a cloned working project before assignment-dependent validation and output preflight."
  - "Blocked replay returns ValidationBlocked before target planning or writer calls so all-intent requests leave BodyGen and BoS files absent."
  - "Successful replay messages are concise stdout text; blocked messages include NPC identity fields and strategy reason without project or output-directory paths."

patterns-established:
  - "CLI generation composes AssignmentStrategyReplayService explicitly with MorphAssignmentService and RandomAssignmentProvider, keeping replay logic in Core."
  - "Templates and BoS JSON continue to use existing writer/catalog paths; tests assert byte parity against source-state generation."

requirements-completed: [AUTO-03]

duration: 4 min
completed: 2026-04-28
---

# Phase 07 Plan 02: Replay Saved Strategies in CLI Outputs Summary

**CLI/headless BodyGen generation now replays saved assignment strategies before validation, overwrite preflight, and output writes.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-28T09:13:17Z
- **Completed:** 2026-04-28T09:17:20Z
- **Tasks:** 2
- **Files modified:** 4 files including this summary

## Accomplishments

- Added failing CLI/headless regression coverage for stale saved assignments, seeded deterministic replay, all-intent template/BoS byte parity, BoS-only no-replay behavior, no-strategy fallback, blocked replay no-write behavior, replay-before-validation, replay-before-overwrite, and CLI stdout/stderr surfaces.
- Injected `AssignmentStrategyReplayService` into `HeadlessGenerationService` and composed it in `BS2BG.Cli` without adding App/Avalonia dependencies.
- Reordered headless generation so BodyGen/all replay happens before validation, missing-profile checks, overwrite preflight, target planning, directory creation, and writer calls.
- Added success replay summaries and blocked NPC detail messages that include Mod, Name, EditorId, Race, FormId, and Reason while avoiding private project/output paths.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Add CLI replay regressions** - `d2961fff` (test)
2. **Task 2 GREEN: Wire replay before CLI output writes** - `e8b37d76` (feat)

**Plan metadata:** pending final docs commit

_Note: This plan followed the TDD test -> feat gate sequence._

## Files Created/Modified

- `tests/BS2BG.Tests/CliGenerationTests.cs` - Added replay regression fixtures and service/CLI assertions covering BodyGen, all, BoS-only, blocked, validation-order, overwrite-order, and stdout behavior.
- `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` - Added replay service dependency, replay ordering, blocked replay handling, and replay success/failure message formatting.
- `src/BS2BG.Cli/Program.cs` - Composes the replay service with Core-only morph assignment dependencies for CLI generation.
- `.planning/phases/07-replay-saved-strategies-in-automation-outputs/07-02-SUMMARY.md` - Execution outcome documentation.

## Decisions Made

- Headless generation always uses `cloneBeforeReplay: true` for saved strategy replay, preserving source loaded project state and matching the request-scoped automation pattern from prior phases.
- Replay blockers are checked before normal validation and overwrite preflight because blocked replay is a fatal correctness condition for generated morph output.
- BoS-only requests still call the replay seam but receive a no-op result from the Core helper, preserving D-01 while keeping one orchestration path.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- RED tests failed for the intended reason: headless generation was still producing `morphs.ini` from stale assignments and success output lacked replay summary text.
- During GREEN, two RED expectations were corrected to match established domain behavior: `Npc.FormId` stores normalized IDs (`2` rather than `000002`), and the seeded deterministic sequence follows stable automation ordering (`Codsworth`, `Aela`, `Danica`).
- Targeted test runs continue to emit pre-existing analyzer warnings in unrelated files; the targeted CLI suite passed.

## User Setup Required

None - no external service configuration required.

## Verification

- `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter FullyQualifiedName~CliGenerationTests` - passed (36 tests).
- Acceptance checks confirmed `AssignmentStrategy`, `Seed`, all three `OutputIntent` values, `ValidationBlocked`, `OverwriteRefused`, `templates.ini`, BoS JSON output, `ProjectPath`, output-directory path assertions, validation-after-replay coverage, and stale-assignment fixture markers are present in `CliGenerationTests.cs`.
- Acceptance checks confirmed `HeadlessGenerationService` contains `AssignmentStrategyReplayService replayService`, calls `PrepareForBodyGen(project, request.Intent, cloneBeforeReplay: true)`, and formats replay success/failure messages.
- Acceptance checks confirmed `Program.cs` composes `new AssignmentStrategyReplayService(new MorphAssignmentService(new RandomAssignmentProvider()))`.

## TDD Gate Compliance

- RED gate commit present: `d2961fff`.
- GREEN gate commit present after RED: `e8b37d76`.
- Refactor gate: not needed; implementation passed the targeted CLI suite.

## Known Stubs

None - scanned changed source/test files for TODO/FIXME/placeholders and hardcoded empty UI data-source patterns; no goal-blocking stubs were introduced.

## Next Phase Readiness

Plan 03 can now wire portable bundle generation to the same replay seam, reusing the CLI-proven blocker, summary, and request-scoped working-state behavior while preserving bundle source project state.

## Self-Check: PASSED

- Created summary path verified: `.planning/phases/07-replay-saved-strategies-in-automation-outputs/07-02-SUMMARY.md`.
- Key source/test files verified present: `HeadlessGenerationService.cs`, `Program.cs`, and `CliGenerationTests.cs`.
- Commits verified in git history: `d2961fff`, `e8b37d76`.
- Verification command passed as documented.

---
*Phase: 07-replay-saved-strategies-in-automation-outputs*
*Completed: 2026-04-28*
