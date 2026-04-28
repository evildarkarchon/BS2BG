---
phase: 05-automation-sharing-and-release-trust
plan: 03
subsystem: assignment-strategy-persistence
tags: [csharp, system-text-json, project-roundtrip, assignment-strategies, tdd]

requires:
  - phase: 04-profile-extensibility-and-controlled-customization
    provides: Legacy-compatible optional project JSON section patterns and ProjectLoadResult diagnostics
provides:
  - Versioned Core assignment strategy configuration contracts
  - Persisted optional AssignmentStrategy project JSON section
  - Imported-race-only ordinal-ignore-case strategy rule matching
  - Recoverable project-load diagnostics for invalid strategy configuration
affects: [automation-sharing, morph-assignment-flow, cli-generation, portable-bundles, project-roundtrip]

tech-stack:
  added: []
  patterns:
    - Optional System.Text.Json root section appended after legacy project fields
    - Schema-versioned strategy data contract with nullable-safe hydration defaults
    - Result-style project load diagnostics for recoverable optional strategy failures

key-files:
  created:
    - src/BS2BG.Core/Morphs/AssignmentStrategyContracts.cs
    - tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs
  modified:
    - src/BS2BG.Core/Models/ProjectModel.cs
    - src/BS2BG.Core/Serialization/ProjectFileService.cs

key-decisions:
  - "AssignmentStrategy is optional project data; null values are omitted to preserve legacy .jbs2bg shape."
  - "Strategy rule race matching uses only imported Npc.Race text with StringComparer.OrdinalIgnoreCase and no plugin/game lookup."
  - "Invalid persisted strategies are dropped with ProjectLoadResult diagnostics while legacy project fields still hydrate."

patterns-established:
  - "Versioned strategy config uses schemaVersion 1 and rejects unsupported future schema versions recoverably."
  - "Composable strategy rules carry preset names, race filters, weights, and bucket names in one data shape."

requirements-completed: [AUTO-03]

duration: 13 min
completed: 2026-04-28
---

# Phase 05 Plan 03: Assignment Strategy Project Serialization Summary

**Versioned assignment strategy configuration now round-trips through `.jbs2bg` with imported-race-only matching and recoverable diagnostics for invalid shared strategy data**

## Performance

- **Duration:** 13 min
- **Started:** 2026-04-28T02:32:52Z
- **Completed:** 2026-04-28T02:45:51Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Added Core-only assignment strategy contracts for seeded random, round-robin, weighted, race-filter, and group/bucket strategy configuration.
- Added composable strategy rules that can carry preset names, race filters, weights, and bucket names together for future algorithm/UI consumption.
- Added imported `Npc.Race` matching through `StringComparer.OrdinalIgnoreCase` without game-data, plugin, ESP/ESM, or xEdit lookup paths.
- Extended `ProjectModel` with dirty-tracked, clone-preserved optional `AssignmentStrategy` data.
- Extended `ProjectFileService` with optional `AssignmentStrategy` JSON after existing legacy fields and recoverable diagnostics for unsupported schema versions or invalid rules.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Add assignment strategy contract tests** - `15791c8e` (test)
2. **Task 1 GREEN: Implement assignment strategy contracts** - `d3b61296` (feat)
3. **Task 2 RED: Add strategy persistence tests** - `e2974894` (test)
4. **Task 2 GREEN: Persist strategies in project files** - `b4a7a8d3` (feat)

**Plan metadata:** pending final docs commit

_Note: Both plan tasks used TDD RED/GREEN commits._

## Files Created/Modified

- `src/BS2BG.Core/Morphs/AssignmentStrategyContracts.cs` - Defines the versioned strategy definition, full strategy kind menu, composable rules, and imported-race matching helper.
- `src/BS2BG.Core/Models/ProjectModel.cs` - Adds dirty-tracked optional `AssignmentStrategy` data and preserves it through `ReplaceWith` clone flows.
- `src/BS2BG.Core/Serialization/ProjectFileService.cs` - Saves and loads optional `AssignmentStrategy` JSON with `schemaVersion`, nullable-safe defaults, rule validation, and recoverable diagnostics.
- `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs` - Covers strategy contracts, race matching, legacy omission, JSON round-trip, dirty tracking, and invalid strategy diagnostics.

## Decisions Made

- `AssignmentStrategy` remains nullable and is omitted from saved JSON when absent so legacy project files do not churn.
- The new root `AssignmentStrategy` field uses `JsonPropertyOrder(4)`, after existing `CustomProfiles` order `3`, to avoid colliding with Phase 4 serialization order.
- Unsupported future `schemaVersion` values and invalid strategy rule shapes produce `ProjectLoadResult.Diagnostics` and set `ProjectModel.AssignmentStrategy` to null while preserving legacy project fields.
- Groups/buckets rules allow empty `RaceFilters` to match any imported race; RaceFilters strategy rules require explicit race filters.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added minimal ProjectModel strategy property during Task 1 GREEN**
- **Found during:** Task 1 (Create strategy configuration contracts)
- **Issue:** The Task 1 behavior included default disabled/null strategy compatibility, which requires a project-level storage seam even before full serialization.
- **Fix:** Added the initial nullable `ProjectModel.AssignmentStrategy` property in Task 1, then completed dirty tracking and clone preservation in Task 2.
- **Files modified:** `src/BS2BG.Core/Models/ProjectModel.cs`
- **Verification:** `dotnet test BS2BG.sln --filter FullyQualifiedName~AssignmentStrategy` passed after Task 1 GREEN and final verification.
- **Committed in:** `d3b61296` and completed in `b4a7a8d3`

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** The adjustment was required to satisfy the planned default/null strategy behavior and did not expand beyond the planned persistence scope.

## Issues Encountered

None.

## Known Stubs

None.

## Threat Flags

None - the shared `.jbs2bg` strategy trust boundary, nullable-safe hydration, future schema rejection, and imported-race-only information disclosure control were covered by the plan threat model.

## Verification

- `dotnet test BS2BG.sln --filter FullyQualifiedName~AssignmentStrategy` — passed after Task 1 GREEN (5 tests), after Task 2 GREEN (15 tests), and during final verification (15 tests).
- `dotnet build BS2BG.sln` — passed during final verification with 0 warnings and 0 errors.

## TDD Gate Compliance

- RED gate commits: `15791c8e`, `e2974894`
- GREEN gate commits: `d3b61296`, `b4a7a8d3`
- Refactor gate: not needed; implementation passed focused tests and build without a separate cleanup commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

The strategy data boundary is ready for downstream Phase 5 plans to implement deterministic assignment execution, GUI editing, CLI use, and bundle reproduction using the persisted configuration without adding game/plugin lookup paths.

## Self-Check: PASSED

- Created/modified files verified: `AssignmentStrategyContracts.cs`, `AssignmentStrategyServiceTests.cs`, `ProjectModel.cs`, `ProjectFileService.cs`, and `05-03-SUMMARY.md` exist.
- Commits verified: `15791c8e`, `d3b61296`, `e2974894`, and `b4a7a8d3` exist in git history.
- Verification commands passed as documented.

---
*Phase: 05-automation-sharing-and-release-trust*
*Completed: 2026-04-28*
