---
phase: 05-automation-sharing-and-release-trust
plan: 04
subsystem: deterministic-assignment-strategies
tags: [csharp, core, morph-assignment, deterministic-random, diagnostics, tdd]

requires:
  - phase: 05-automation-sharing-and-release-trust
    provides: Versioned assignment strategy persistence and imported-race-only strategy contracts
provides:
  - Deterministic seeded random assignment replay using a pinned provider-compatible PRNG
  - Shared non-mutating strategy eligibility surface for application and diagnostics
  - Round-robin, weighted, race-filter, and group/bucket assignment algorithms
  - Non-export-blocking diagnostics for NPCs with no eligible preset after strategy rules
affects: [automation-sharing, morph-assignment-flow, cli-generation, portable-bundles, diagnostics]

tech-stack:
  added: []
  patterns:
    - Core-only strategy execution service over ProjectModel and IRandomAssignmentProvider
    - Pinned Mulberry32 deterministic provider for persisted seed replay
    - Shared ComputeEligibility source reused by diagnostics and Apply

key-files:
  created:
    - src/BS2BG.Core/Morphs/AssignmentStrategyService.cs
    - src/BS2BG.Core/Morphs/DeterministicAssignmentRandomProvider.cs
  modified:
    - src/BS2BG.Core/Morphs/MorphAssignmentService.cs
    - src/BS2BG.Core/Diagnostics/ProjectValidationService.cs
    - tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs

key-decisions:
  - "Persisted seed replay uses a pinned Mulberry32 implementation instead of System.Random(seed) so shared projects replay identically across runtimes."
  - "ComputeEligibility is the single non-mutating source for Apply and diagnostics, preventing divergent no-eligible behavior."
  - "No-eligible strategy diagnostics are Caution severity in ordinary validation so saved but unapplied strategies do not block unrelated exports."

patterns-established:
  - "Strategy algorithms operate on explicit eligible NPC scopes and stable NPC/preset ordering."
  - "Weighted rules use fixed two-decimal half-up integer units; values quantizing to zero do not participate."

requirements-completed: [AUTO-03]

duration: 45 min
completed: 2026-04-28
---

# Phase 05 Plan 04: Deterministic Assignment Strategy Execution Summary

**Provider-compatible deterministic assignment strategies now replay seeded, weighted, race-filtered, and group/bucket NPC assignments with shared no-eligible diagnostics**

## Performance

- **Duration:** 45 min
- **Started:** 2026-04-28T02:49:56Z
- **Completed:** 2026-04-28T03:35:00Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- Added `AssignmentStrategyService` with `ComputeEligibility` and `Apply` over explicit NPC scopes, stable NPC ordering, and stable project preset ordering.
- Added `DeterministicAssignmentRandomProvider` using a documented pinned Mulberry32 PRNG behind `IRandomAssignmentProvider` with reference-vector coverage.
- Implemented seeded random, round-robin, weighted fixed-unit selection, imported-race filters, and group/bucket rule eligibility without fallback to all presets.
- Extended `MorphAssignmentService` with `ApplyStrategy` while preserving existing random fill methods and provider injection.
- Extended `ProjectValidationService` with Caution-level no-eligible strategy findings that reuse `ComputeEligibility` and match `Apply` blocked rows.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Add deterministic strategy tests** - `585ac991` (test)
2. **Task 1 GREEN: Implement deterministic strategy foundation** - `18a4de69` (feat)
3. **Task 2 RED: Add rich algorithm tests** - `88a0c1dc` (test)
4. **Task 2 GREEN: Implement weighted/race/group algorithms** - `a368cd43` (feat)
5. **Task 3 RED: Add no-eligible diagnostics tests** - `8f6f21b6` (test)
6. **Task 3 GREEN: Diagnose no-eligible strategy rows** - `b5888ec5` (feat)

**Plan metadata:** pending final docs commit

_Note: All three plan tasks used TDD RED/GREEN commits._

## Files Created/Modified

- `src/BS2BG.Core/Morphs/AssignmentStrategyService.cs` - Executes persisted strategies, computes shared eligibility, returns blocked NPCs, and implements seeded random, round-robin, weighted, race-filter, and group/bucket logic.
- `src/BS2BG.Core/Morphs/DeterministicAssignmentRandomProvider.cs` - Provides pinned Mulberry32 deterministic draws through `IRandomAssignmentProvider` for seed replay.
- `src/BS2BG.Core/Morphs/MorphAssignmentService.cs` - Adds `ApplyStrategy` delegation while preserving existing assignment methods.
- `src/BS2BG.Core/Diagnostics/ProjectValidationService.cs` - Adds non-blocking no-eligible strategy findings using shared eligibility.
- `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs` - Covers exact replay sequences, provider seam preservation, PRNG vectors, weighted/race/group rules, no fallback, and diagnostics/apply parity.

## Decisions Made

- Persisted deterministic replay does not use `System.Random(seed)`; Mulberry32 is pinned and covered by seed `0`, `1`, and `123` reference vectors.
- Weighted strategy rules are sorted by rule name ordinal-ignore-case plus original order and convert weights with `Math.Round(weight * 100, MidpointRounding.AwayFromZero)`.
- Strategy diagnostics are Caution severity during ordinary project validation because D-15 blocks assignment application for affected NPCs, not unrelated export of already assigned morphs.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected initial exact-sequence test expectations to match the planned stable NPC ordering**
- **Found during:** Task 1 and Task 2 GREEN
- **Issue:** Initial RED expectations assumed name ordering in a few exact-sequence assertions, but the plan required default strategy execution order by Mod, EditorId, FormId, Name, then original index.
- **Fix:** Updated expected sequences to assert the planned stable execution order while keeping snapshots readable by NPC name.
- **Files modified:** `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs`
- **Verification:** `dotnet test BS2BG.sln --filter FullyQualifiedName~AssignmentStrategy` passed with 31 tests.
- **Committed in:** `18a4de69` and `a368cd43`

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** The correction aligned tests with the formal algorithm spec and did not expand scope.

## Issues Encountered

None beyond the TDD expectation correction documented as an auto-fixed issue.

## Known Stubs

None.

## Threat Flags

None - the strategy config to NPC assignment mutation boundary and no-eligible fallback/tampering risks were covered by the plan threat model and mitigated with stable-order exact-sequence tests plus shared blocked diagnostics.

## Verification

- `dotnet test BS2BG.sln --filter FullyQualifiedName~AssignmentStrategy` — passed (31 tests).
- `dotnet build BS2BG.sln` — passed with 0 warnings and 0 errors.
- Acceptance checks confirmed `AssignmentStrategyService.cs` contains `ComputeEligibility`, `Apply`, `StringComparer.OrdinalIgnoreCase`, `IRandomAssignmentProvider`, weighted/race/group algorithm handling, and no game/plugin lookup references.
- Acceptance checks confirmed `DeterministicAssignmentRandomProvider.cs` contains the pinned PRNG and no `System.Random` usage.
- Acceptance checks confirmed `ProjectValidationService.cs` contains `No eligible preset after strategy rules` and calls `AssignmentStrategyService.ComputeEligibility`.

## TDD Gate Compliance

- RED gate commits: `585ac991`, `88a0c1dc`, `8f6f21b6`
- GREEN gate commits: `18a4de69`, `a368cd43`, `b5888ec5`
- Refactor gate: not needed; focused tests and final build passed without separate cleanup commits.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

The Core strategy execution boundary is ready for Phase 5 GUI, CLI, and bundle plans to invoke deterministic assignment strategies from persisted project configuration while preserving reproducibility and no-fallback diagnostics.

## Self-Check: PASSED

- Created/modified files verified: `AssignmentStrategyService.cs`, `DeterministicAssignmentRandomProvider.cs`, `MorphAssignmentService.cs`, `ProjectValidationService.cs`, `AssignmentStrategyServiceTests.cs`, and `05-04-SUMMARY.md` exist.
- Commits verified: `585ac991`, `18a4de69`, `88a0c1dc`, `a368cd43`, `8f6f21b6`, and `b5888ec5` exist in git history.
- Verification commands passed as documented.

---
*Phase: 05-automation-sharing-and-release-trust*
*Completed: 2026-04-28*
