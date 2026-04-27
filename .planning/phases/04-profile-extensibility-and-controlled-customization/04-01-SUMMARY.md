---
phase: 04-profile-extensibility-and-controlled-customization
plan: 01
subsystem: core-profile-validation
tags: [csharp, system-text-json, profile-validation, tdd]

requires:
  - phase: 03-validation-and-diagnostics
    provides: read-only diagnostics and validation-result conventions used for strict profile intake
provides:
  - Core custom profile identity/source contracts
  - Strict profile JSON validation with diagnostics for malformed schema, duplicates, versions, and non-finite values
  - Deterministic standalone profile JSON export with LF newlines and stable table ordering
affects: [profile-extensibility, custom-profile-storage, project-embedded-profiles, profile-recovery]

tech-stack:
  added: []
  patterns:
    - Result-first Core validation over System.Text.Json JsonDocument token inspection
    - Source-tagged immutable profile definitions for bundled, local custom, and embedded project trust domains
    - Normalized definition equality for same-name embedded/local conflict detection

key-files:
  created:
    - src/BS2BG.Core/Models/CustomProfileDefinition.cs
    - src/BS2BG.Core/Generation/ProfileDefinitionService.cs
    - src/BS2BG.Core/IsExternalInit.cs
    - tests/BS2BG.Tests/ProfileDefinitionServiceTests.cs
  modified: []

key-decisions:
  - "Custom profile identity is the internal Name field; file paths are retained only as source metadata."
  - "Definition equality normalizes profile display names and table order while preserving exact game metadata, slider-name casing, and float values."
  - "Profile JSON export normalizes indented System.Text.Json output to LF to make custom profile sharing byte-stable on Windows."

patterns-established:
  - "Strict custom profile import returns ProfileValidationResult diagnostics instead of throwing for ordinary malformed user JSON."
  - "Version 1 profile JSON is the first explicit schema; missing Version defaults to 1 and unsupported versions are blockers."

requirements-completed: [EXT-01, EXT-02]

duration: 4 min
completed: 2026-04-27
---

# Phase 04 Plan 01: Core Custom Profile Validation and JSON Export Summary

**Strict Core custom-profile contracts with source-tagged identity, diagnostic JSON validation, normalized conflict equality, and deterministic LF-only profile export**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-27T08:19:13Z
- **Completed:** 2026-04-27T08:23:31Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Added immutable Core contracts for custom profile source domains, validation diagnostics/results, validation contexts, and normalized definition equality.
- Implemented `ProfileDefinitionService` to validate untrusted custom profile JSON strictly before catalog inclusion while accepting blank profiles and broad finite numeric values.
- Added deterministic standalone profile JSON export with schema `Version: 1`, stable key ordering, LF newlines, and no trailing newline.
- Covered validation and export behavior with focused xUnit/FluentAssertions tests, including duplicate profile/slider names, malformed JSON, duplicate JSON properties, non-finite values, version handling, filename-independent identity, and export round-trip stability.

## Task Commits

Each task was committed atomically:

1. **Task 1: Define custom profile validation contracts** - `8ed6ffdd` (test)
2. **Task 2 RED: Add strict profile parser/export tests** - `a6d69a64` (test)
3. **Task 2 GREEN: Implement strict profile parser and exporter** - `49db4aa9` (feat)

**Plan metadata:** pending final docs commit

_Note: TDD tasks used separate RED and GREEN commits._

## Files Created/Modified

- `src/BS2BG.Core/Models/CustomProfileDefinition.cs` - Defines custom profile source kinds, immutable definitions, validation diagnostics/results, import context snapshots, and normalized profile equality semantics.
- `src/BS2BG.Core/Generation/ProfileDefinitionService.cs` - Parses, validates, and exports custom profile JSON using explicit `System.Text.Json` token checks and deterministic writer output.
- `src/BS2BG.Core/IsExternalInit.cs` - Provides the small netstandard2.1 compatibility shim needed for public record contracts in Core.
- `tests/BS2BG.Tests/ProfileDefinitionServiceTests.cs` - Focused validation/export coverage for Phase 4 D-03/D-05/D-06/D-07/D-08/D-12/D-16 prerequisites.

## Decisions Made

- Custom profile identity uses only the internal `Name` property; file path is preserved as source metadata and never becomes display identity.
- Normalized conflict equality compares profile display names case-insensitively, table order insensitively, slider names case-sensitively within tables, game metadata ordinally, and floats with exact `float.Equals` semantics.
- Exported custom profile JSON is normalized to LF after `Utf8JsonWriter` indentation so Windows execution still produces byte-stable shareable profile files.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added netstandard2.1 record compatibility shim**
- **Found during:** Task 1 (Define custom profile validation contracts)
- **Issue:** `BS2BG.Core` targets `netstandard2.1`, which does not provide `System.Runtime.CompilerServices.IsExternalInit`; the required public record contracts could not compile without it.
- **Fix:** Added an internal `IsExternalInit` shim in Core.
- **Files modified:** `src/BS2BG.Core/IsExternalInit.cs`
- **Verification:** `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests` compiled Core after the shim and later passed all focused tests.
- **Committed in:** `8ed6ffdd`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** The shim is compatibility-only and preserves the exact record contract requested by the plan without changing generation/export behavior.

## Issues Encountered

- `Utf8JsonWriter` emitted CRLF-indented JSON on Windows; export now normalizes CRLF to LF to satisfy the plan's byte-stable custom profile export requirement.

## Known Stubs

None.

## Threat Flags

None - the untrusted profile JSON boundary and catalog-inclusion validation surface were covered by the plan threat model.

## Verification

- `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests` — passed (15 tests).
- `dotnet build BS2BG.sln` — passed (0 warnings, 0 errors).
- Task acceptance criteria were verified with focused `Select-String` checks for required contracts, service methods, version handling, case-insensitive duplicate checks, and named test coverage.

## TDD Gate Compliance

- RED gate commits: `8ed6ffdd`, `a6d69a64`
- GREEN gate commit: `49db4aa9`
- Refactor gate: not needed; implementation passed focused tests and build without a separate cleanup commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for 04-02 AppData custom profile storage and source-tagged catalog composition. The Core validation/export contract is in place for local discovery, imports, editor saves, embedded project profiles, and later same-name conflict handling.

## Self-Check: PASSED

- Created files verified: `CustomProfileDefinition.cs`, `ProfileDefinitionService.cs`, `IsExternalInit.cs`, and `ProfileDefinitionServiceTests.cs` exist.
- Commits verified: `8ed6ffdd`, `a6d69a64`, and `49db4aa9` exist in git history.
- Verification commands passed as documented.

---
*Phase: 04-profile-extensibility-and-controlled-customization*
*Completed: 2026-04-27*
