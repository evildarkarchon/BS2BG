---
phase: 04-profile-extensibility-and-controlled-customization
plan: 04
subsystem: profile-recovery-diagnostics
tags: [csharp, diagnostics, custom-profiles, tdd, recovery]

requires:
  - phase: 04-profile-extensibility-and-controlled-customization
    provides: source-tagged runtime catalog entries and embedded project custom profile definitions from plans 04-02 and 04-03
provides:
  - Read-only missing custom profile recovery diagnostics with explicit neutral fallback copy
  - Exact internal display-name import resolution helper ignoring file paths
  - Stable diagnostic code/category metadata for mechanical recovery deduplication
affects: [profile-extensibility, diagnostics-ui, project-recovery, custom-profile-import]

tech-stack:
  added: []
  patterns:
    - Core-only recovery diagnostics over ProjectModel plus TemplateProfileCatalog
    - DiagnosticFinding Code/Category metadata for exact deduplication
    - Exact StringComparison.OrdinalIgnoreCase profile identity matching

key-files:
  created:
    - src/BS2BG.Core/Diagnostics/ProfileRecoveryDiagnosticsService.cs
    - tests/BS2BG.Tests/ProfileRecoveryDiagnosticsServiceTests.cs
  modified:
    - src/BS2BG.Core/Diagnostics/DiagnosticFinding.cs
    - src/BS2BG.Core/Diagnostics/ProfileDiagnosticsService.cs
    - src/BS2BG.Core/Diagnostics/ProjectValidationService.cs
    - tests/BS2BG.Tests/ProfileDiagnosticsServiceTests.cs
    - tests/BS2BG.Tests/ProjectValidationServiceTests.cs

key-decisions:
  - "Missing custom profile references are represented by one recovery-coded informational finding instead of separate generic unbundled and fallback rows."
  - "Imported profile recovery identity uses only CustomProfileDefinition.Name with OrdinalIgnoreCase; FilePath remains source metadata only."
  - "ProjectValidationService no longer emits generic unbundled-profile rows; ProfileDiagnosticsService owns recovery-aware profile fallback findings."

patterns-established:
  - "Recovery diagnostics remain read-only and never mutate ProjectModel, catalog entries, or embedded project profile collections."
  - "Diagnostic deduplication uses stable Code and Category values rather than title/detail text matching."

requirements-completed: [EXT-04]

duration: 5 min
completed: 2026-04-27
---

# Phase 04 Plan 04: Profile Recovery Diagnostics Summary

**Read-only missing custom-profile recovery diagnostics with exact internal-name import resolution and code/category-based deduplication**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-27T08:43:36Z
- **Completed:** 2026-04-27T08:48:04Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments

- Added `ProfileRecoveryDiagnosticsService` and `ProfileRecoveryDiagnostic` records to report missing custom profile references as neutral, actionable, non-blocking diagnostics.
- Exposed recovery actions for import, remap, keep-unresolved, and project-embedded copy activation when an exact embedded definition is present.
- Added `CanResolveMissingReference` so import workflows can validate recovery matches by `CustomProfileDefinition.Name` only, ignoring filenames and paths.
- Added `DiagnosticFinding.Code` and `DiagnosticFinding.Category`, then routed missing-profile fallback findings through `MissingCustomProfile` / `ProfileRecovery` so Diagnostics surfaces do not show duplicate rows.
- Covered missing-reference behavior, embedded copy availability, bundled/local resolved cases, exact-match import resolution, filename mismatch rejection, and deduplication with focused xUnit/FluentAssertions tests.

## Task Commits

Each task was committed atomically with TDD gates:

1. **Task 1 RED: Add recovery diagnostics tests** - `17f38e5e` (test)
2. **Task 1 GREEN: Add profile recovery diagnostics service** - `26c5b95b` (feat)
3. **Task 2 RED: Add exact-match recovery tests** - `adb139fd` (test)
4. **Task 2 GREEN: Add exact-match recovery helper** - `daa64bf9` (feat)
5. **Task 3 RED: Add recovery dedupe tests** - `60104bd4` (test)
6. **Task 3 GREEN: Deduplicate profile recovery diagnostics** - `28836d9a` (feat)

**Plan metadata:** pending final docs commit

_Note: All three plan tasks used TDD RED/GREEN commits._

## Files Created/Modified

- `src/BS2BG.Core/Diagnostics/ProfileRecoveryDiagnosticsService.cs` - Core read-only recovery diagnostic service, action enum, immutable diagnostic record, and exact-name import resolution helper.
- `tests/BS2BG.Tests/ProfileRecoveryDiagnosticsServiceTests.cs` - Focused missing-profile, embedded-copy, local/bundled resolved, exact-match, filename mismatch, and dedupe coverage.
- `src/BS2BG.Core/Diagnostics/DiagnosticFinding.cs` - Adds optional `Code` and `Category` metadata for stable mechanical deduplication.
- `src/BS2BG.Core/Diagnostics/ProfileDiagnosticsService.cs` - Converts missing-profile fallback findings into recovery-coded diagnostics while retaining profile coverage summary/drilldown behavior.
- `src/BS2BG.Core/Diagnostics/ProjectValidationService.cs` - Removes the generic unbundled-profile finding path so combined diagnostics show one recovery row per missing profile.
- `tests/BS2BG.Tests/ProfileDiagnosticsServiceTests.cs` - Updates profile fallback assertions to use recovery code/category metadata.
- `tests/BS2BG.Tests/ProjectValidationServiceTests.cs` - Updates validation expectations after generic unbundled-profile findings were superseded by recovery diagnostics.

## Decisions Made

- Missing custom profiles now surface as `MissingCustomProfile` / `ProfileRecovery` findings from profile diagnostics only, preventing duplicate ProjectValidation/ProfileDiagnostics rows in the Diagnostics workspace.
- Recovery matching is intentionally display-name based: `CustomProfileDefinition.Name` compared with `StringComparison.OrdinalIgnoreCase`; `FilePath` is ignored even when it resembles the missing reference.
- Project-embedded definitions make `UseProjectEmbeddedCopy` available only when the active catalog does not already resolve the profile name.

## Deviations from Plan

None - plan executed as written.

## Issues Encountered

- Running final `dotnet test` and `dotnet build BS2BG.sln` concurrently caused a transient Avalonia/MSBuild file lock on `BS2BG.App.dll`. Re-running `dotnet build BS2BG.sln` after the focused test completed passed cleanly.

## Known Stubs

None.

## Threat Flags

None - the missing-profile diagnostics boundary, imported-profile spoofing mitigation, and read-only recovery behavior were covered by the plan threat model.

## Verification

- `dotnet test --filter FullyQualifiedName~ProfileRecoveryDiagnosticsServiceTests` — passed (7 tests).
- `dotnet test --filter "FullyQualifiedName~ProfileRecoveryDiagnosticsServiceTests|FullyQualifiedName~ProfileDiagnosticsServiceTests|FullyQualifiedName~ProjectValidationServiceTests"` — passed (13 tests).
- `dotnet build BS2BG.sln` — passed after re-run (0 warnings, 0 errors).
- Acceptance checks confirmed the recovery action enum values, neutral fallback wording, exact-match helper, filename mismatch test coverage, and code/category-based dedupe assertions.

## TDD Gate Compliance

- RED gate commits: `17f38e5e`, `adb139fd`, `60104bd4`
- GREEN gate commits: `26c5b95b`, `daa64bf9`, `28836d9a`
- Refactor gate: not needed; focused tests and build passed without a separate cleanup commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Recovery diagnostics are ready for later App profile-management commands to import matching profiles, activate embedded project copies, remap presets, or intentionally keep unresolved fallback visible without changing Core generation semantics.

## Self-Check: PASSED

- Created/modified files verified: `ProfileRecoveryDiagnosticsService.cs`, `ProfileRecoveryDiagnosticsServiceTests.cs`, `DiagnosticFinding.cs`, `ProfileDiagnosticsService.cs`, `ProjectValidationService.cs`, and `04-04-SUMMARY.md` exist.
- Commits verified: `17f38e5e`, `26c5b95b`, `adb139fd`, `daa64bf9`, `60104bd4`, and `28836d9a` exist in git history.
- Verification commands passed as documented.

---
*Phase: 04-profile-extensibility-and-controlled-customization*
*Completed: 2026-04-27*
