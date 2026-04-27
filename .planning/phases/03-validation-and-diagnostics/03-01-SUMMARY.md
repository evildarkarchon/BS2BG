---
phase: 03-validation-and-diagnostics
plan: 01
subsystem: diagnostics
tags: [core, diagnostics, validation, profiles, xunit, fluentassertions]

requires:
  - phase: 01-profile-correctness-and-trust
    provides: [profile catalog fallback semantics, neutral unbundled-profile behavior]
  - phase: 02-workflow-persistence-filtering-and-undo-hardening
    provides: [stable project model mutation tracking]
provides:
  - Core diagnostic severity and finding contracts
  - Read-only project validation report service
  - Read-only profile diagnostics summary and slider drilldown facts
affects: [diagnostics-ui, import-preview, export-preview, profile-extensibility]

tech-stack:
  added: []
  patterns: [immutable Core diagnostics DTOs, read-only validation services, TDD red-green commits]

key-files:
  created:
    - src/BS2BG.Core/Diagnostics/DiagnosticSeverity.cs
    - src/BS2BG.Core/Diagnostics/DiagnosticFinding.cs
    - src/BS2BG.Core/Diagnostics/ProjectValidationReport.cs
    - src/BS2BG.Core/Diagnostics/ProjectValidationService.cs
    - src/BS2BG.Core/Diagnostics/ProfileDiagnosticsService.cs
    - tests/BS2BG.Tests/ProjectValidationServiceTests.cs
    - tests/BS2BG.Tests/ProfileDiagnosticsServiceTests.cs
  modified:
    - src/BS2BG.Core/Formatting/SliderProfile.cs

key-decisions:
  - "Keep project/profile diagnostics read-only and Core-only so App diagnostics presentation can reuse them without mutating ProjectModel state."
  - "Expose profile multiplier and inversion tables from SliderProfile for diagnostics rather than re-parsing profile JSON or duplicating formatter logic."

patterns-established:
  - "Diagnostics services return immutable report DTOs with severity-counted summaries."
  - "Profile fallback diagnostics use neutral Info findings and explicitly avoid mismatch, scoring, heuristic, or experimental language."

requirements-completed: [DIAG-01, DIAG-02]

duration: 6 min
completed: 2026-04-27
---

# Phase 03 Plan 01: Core Project/Profile Diagnostics Summary

**Read-only Core diagnostics services for project health, profile coverage, injected defaults, multipliers, inversions, and neutral fallback facts.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-27T03:41:49Z
- **Completed:** 2026-04-27T03:47:37Z
- **Tasks:** 2 completed
- **Files modified:** 8

## Accomplishments

- Added Core diagnostic contracts: `DiagnosticSeverity`, `DiagnosticFinding`, and `ProjectValidationReport`.
- Implemented `ProjectValidationService.Validate(ProjectModel project, TemplateProfileCatalog profileCatalog)` with grouped, read-only findings for Project, Profiles, Templates, Morphs/NPCs, and Export readiness.
- Implemented `ProfileDiagnosticsService.Analyze(ProjectModel project, TemplateProfileCatalog catalog, string? selectedPresetName = null)` with whole-project/selected-preset summaries, slider drilldown rows, and neutral fallback Info findings.
- Added focused xUnit/FluentAssertions coverage for DIAG-01 and DIAG-02, including no-mutation assertions and negative mismatch/scoring/experimental wording checks.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Project validation tests** - `b742950b` (test)
2. **Task 1 GREEN: Project validation diagnostics** - `fbeab5f7` (feat)
3. **Task 2 RED: Profile diagnostics tests** - `3a5d50e6` (test)
4. **Task 2 GREEN: Profile diagnostics facts** - `65fefacc` (feat)

**Plan metadata:** pending final docs commit.

## Files Created/Modified

- `src/BS2BG.Core/Diagnostics/DiagnosticSeverity.cs` - Defines Blocker, Caution, and Info severity values.
- `src/BS2BG.Core/Diagnostics/DiagnosticFinding.cs` - Immutable diagnostic finding DTO with optional target/action hint.
- `src/BS2BG.Core/Diagnostics/ProjectValidationReport.cs` - Immutable report with severity counts.
- `src/BS2BG.Core/Diagnostics/ProjectValidationService.cs` - Read-only project validation grouped by workflow area.
- `src/BS2BG.Core/Diagnostics/ProfileDiagnosticsService.cs` - Profile diagnostics report, summary, and slider drilldown implementation.
- `src/BS2BG.Core/Formatting/SliderProfile.cs` - Exposes multiplier and inversion table entries for read-only diagnostics.
- `tests/BS2BG.Tests/ProjectValidationServiceTests.cs` - DIAG-01 tests for grouped read-only validation.
- `tests/BS2BG.Tests/ProfileDiagnosticsServiceTests.cs` - DIAG-02 tests for profile diagnostics and neutral fallback wording.

## Decisions Made

- Keep diagnostics as Core read-only services with immutable DTOs; App navigation/copy/report UI remains for later plans.
- Expose `SliderProfile.Multipliers` and `SliderProfile.InvertedNames` so diagnostics inspect the same loaded profile tables used by generation without duplicating profile-loading logic.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Exposed profile table entries for diagnostics**
- **Found during:** Task 2 (Add profile diagnostic summary and drilldown facts)
- **Issue:** `SliderProfile` only exposed defaults plus lookup methods, which was insufficient to count multiplier and inversion table entries from the active profile without duplicating profile JSON parsing.
- **Fix:** Added read-only `Multipliers` and `InvertedNames` properties while preserving existing formatter lookup methods and behavior.
- **Files modified:** `src/BS2BG.Core/Formatting/SliderProfile.cs`
- **Verification:** `dotnet test --filter "FullyQualifiedName~ProjectValidationServiceTests|FullyQualifiedName~ProfileDiagnosticsServiceTests"` passed.
- **Committed in:** `65fefacc`

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Required for correctness of DIAG-02 profile behavior reporting; no output formatting behavior changed.

## Issues Encountered

- The plan-provided `dotnet test ... -x` command is not accepted by the installed .NET/MSBuild toolchain (`MSB1001: Unknown switch`). Verification was run with the same filters without `-x`, and all focused tests passed.

## Known Stubs

None.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## TDD Gate Compliance

- RED commits present: `b742950b`, `3a5d50e6`
- GREEN commits present after RED: `fbeab5f7`, `65fefacc`
- REFACTOR commits: none needed

## Verification

- `dotnet test --filter "FullyQualifiedName~ProjectValidationServiceTests"` — passed (3 tests).
- `dotnet test --filter "FullyQualifiedName~ProfileDiagnosticsServiceTests"` — passed (3 tests).
- `dotnet test --filter "FullyQualifiedName~ProjectValidationServiceTests|FullyQualifiedName~ProfileDiagnosticsServiceTests"` — passed (6 tests).
- Acceptance checks confirmed `Validate(ProjectModel project, TemplateProfileCatalog profileCatalog)`, severity enum values, read-only `project.IsDirty.Should().BeFalse()` assertions, `Analyze(ProjectModel project, TemplateProfileCatalog catalog`, and required profile diagnostic wording assertions.

## Next Phase Readiness

Plan 03-02 can build import preview diagnostics on the new diagnostic DTO patterns. Later App diagnostics plans can consume these Core reports without introducing UI dependencies or project mutation.

## Self-Check: PASSED

- Found all created Core diagnostics and test files.
- Verified task commits exist: `b742950b`, `fbeab5f7`, `3a5d50e6`, `65fefacc`.

---
*Phase: 03-validation-and-diagnostics*
*Completed: 2026-04-27*
