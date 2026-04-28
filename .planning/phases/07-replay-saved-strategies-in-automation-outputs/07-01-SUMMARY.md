---
phase: 07-replay-saved-strategies-in-automation-outputs
plan: 01
subsystem: automation-strategy-replay
tags: [csharp, core, automation, morphs, assignment-strategy, deterministic-replay, tdd]

requires:
  - phase: 05-automation-sharing-and-release-trust
    provides: persisted assignment strategy definitions and deterministic provider-compatible strategy execution
  - phase: 06-compose-custom-profiles-in-headless-generation
    provides: request-scoped automation state pattern for headless and bundle generation
provides:
  - Core AssignmentStrategyReplayService for BodyGen/all saved-strategy replay
  - AssignmentStrategyReplayResult blocker and summary contract for downstream automation callers
  - Seeded MorphAssignmentService.ApplyStrategy dispatch that preserves eligibleRows scope
affects: [automation, cli-generation, portable-bundles, morph-assignment-flow, AUTO-03]

tech-stack:
  added: []
  patterns:
    - Clone-before-replay request-scoped ProjectModel working state
    - Fatal IsBlocked replay result contract for partial strategy replay
    - Seeded deterministic provider branch preserving injected provider behavior for unseeded calls

key-files:
  created:
    - src/BS2BG.Core/Automation/AssignmentStrategyReplayService.cs
    - src/BS2BG.Core/Automation/AssignmentStrategyReplayContracts.cs
    - tests/BS2BG.Tests/AssignmentStrategyReplayServiceTests.cs
  modified:
    - src/BS2BG.Core/Morphs/MorphAssignmentService.cs

key-decisions:
  - "Saved BodyGen/all assignment strategy replay is centralized in Core through AssignmentStrategyReplayService instead of being duplicated in CLI or bundle code."
  - "Blocked replay results expose IsBlocked and partial working-project semantics so later automation wiring can fail before generating stale morph output."
  - "Seeded strategy replay branches on the random provider while still calling service.Apply(project, strategy, eligibleRows), preserving scoped GUI/bulk semantics."

patterns-established:
  - "Direct helper tests lock replay/no-replay, clone/no-clone, blockers, empty input, and seeded determinism before integration wiring."
  - "Replay callers receive both concise counts and actionable blocked NPC identities without touching output writers."

requirements-completed: [AUTO-03]

duration: 4 min
completed: 2026-04-28
---

# Phase 07 Plan 01: Core Saved-Strategy Replay Seam Summary

**Core replay seam for deterministic saved assignment strategies with clone-safe working state and fatal blocked-NPC reporting.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-28T09:06:17Z
- **Completed:** 2026-04-28T09:10:03Z
- **Tasks:** 2
- **Files modified:** 4 source/test files

## Accomplishments

- Added `AssignmentStrategyReplayService.PrepareForBodyGen` to return a request-scoped project clone or source project depending on `cloneBeforeReplay`, replaying only for `OutputIntent.BodyGen` and `OutputIntent.All`.
- Added `AssignmentStrategyReplayResult` with `Replayed`, `StrategyKind`, `AssignedCount`, `BlockedNpcs`, and `IsBlocked`; XML docs state blocked projects may be partially replayed and must not be generated from.
- Updated `MorphAssignmentService.ApplyStrategy` so saved seeded strategies use `DeterministicAssignmentRandomProvider` while unseeded calls keep the injected provider and both paths preserve `eligibleRows`.
- Added TDD coverage for BodyGen/all replay, BoS no-replay, no-strategy clone isolation, blocked NPC identity, clone/no-clone mutation contracts, seeded replay determinism, empty NPC no-op replay, and seeded eligible-row scoping.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Specify replay helper behavior** - `b4822f79` (test)
2. **Task 2 GREEN: Implement Core replay seam** - `f6b43453` (feat)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `src/BS2BG.Core/Automation/AssignmentStrategyReplayService.cs` - Core-only helper that prepares a BodyGen-safe project by replaying saved assignment strategies when applicable.
- `src/BS2BG.Core/Automation/AssignmentStrategyReplayContracts.cs` - Replay result contract with fatal blocker semantics and concise replay summary fields.
- `src/BS2BG.Core/Morphs/MorphAssignmentService.cs` - Uses deterministic provider replay for seeded strategies without dropping scoped eligible rows.
- `tests/BS2BG.Tests/AssignmentStrategyReplayServiceTests.cs` - Direct replay seam and seeded eligible-row regression coverage.

## Decisions Made

- Core replay uses `ProjectModel.ReplaceWith` for clone isolation instead of adding a new clone API, matching the plan and existing project round-trip clone behavior.
- BoS-only intent returns a no-op replay result even when a project has a saved strategy, preserving D-01 scope that only BodyGen morph output needs replay.
- The blocked replay contract intentionally returns the partially mutated working project for diagnostics/state inspection, but `IsBlocked` is the fatal guard for downstream generation.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- The RED run failed on missing `AssignmentStrategyReplayService` and `AssignmentStrategyReplayResult`, as intended.
- The seeded replay fixture was adjusted during GREEN to use an existing valid preset as the stale assignment so deterministic seeded replay could pin the existing three-preset reference sequence without letting a stale test-only preset participate in assignment eligibility.
- Targeted test runs emit pre-existing analyzer warnings in unrelated files; the final targeted suite passed.

## User Setup Required

None - no external service configuration required.

## Verification

- `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter "FullyQualifiedName~AssignmentStrategyReplayServiceTests|FullyQualifiedName~AssignmentStrategyServiceTests|FullyQualifiedName~MorphsViewModelStrategyTests"` - passed (50 tests).
- Acceptance checks confirmed replay service tokens, replay result contract, deterministic provider branch, `eligibleRows` preservation, and absence of a new two-argument `AssignmentStrategyService.Apply(project, strategy)` call in `MorphAssignmentService.ApplyStrategy`.

## TDD Gate Compliance

- RED gate commit present: `b4822f79`.
- GREEN gate commit present after RED: `f6b43453`.
- Refactor gate: not needed; implementation passed the targeted replay/shared strategy suite.

## Known Stubs

None - scanned changed files; null/default patterns found were existing validation/test setup, not UI or data-source stubs.

## Next Phase Readiness

Plan 02 can now wire CLI/headless generation to `AssignmentStrategyReplayService` and rely on a stable `IsBlocked` contract plus seeded deterministic replay that preserves GUI/scoped eligible-row semantics.

## Self-Check: PASSED

- Created summary path verified: `.planning/phases/07-replay-saved-strategies-in-automation-outputs/07-01-SUMMARY.md`.
- Key source/test files verified present: `AssignmentStrategyReplayService.cs`, `AssignmentStrategyReplayContracts.cs`, `AssignmentStrategyReplayServiceTests.cs`, and `MorphAssignmentService.cs`.
- Commits verified in git history: `b4822f79`, `f6b43453`.
- Verification command passed as documented.

---
*Phase: 07-replay-saved-strategies-in-automation-outputs*
*Completed: 2026-04-28*
