---
phase: 05-automation-sharing-and-release-trust
plan: 02
subsystem: cli
tags: [dotnet, cli, automation, headless-generation, export-safety]

requires:
  - phase: 05-automation-sharing-and-release-trust
    provides: CLI executable shell and typed headless generation contracts from plan 05-01
provides:
  - Validation-first Core headless generation orchestration over existing project load, validation, generation, and export writer services
  - CLI generate command wired to Core service outcomes with script-friendly stdout, stderr, and exit codes
  - Writer-owned BoS JSON output planning reused by overwrite preflight
  - Install-relative Core bundled profile catalog factory for CLI composition
affects: [automation, cli-generation, bundle-generation, release-packaging]

tech-stack:
  added: []
  patterns: [Core-only headless orchestration, validation-before-write gate, overwrite preflight using writer-owned planning, in-process CLI tests with serialized console capture]

key-files:
  created:
    - src/BS2BG.Core/Automation/HeadlessGenerationService.cs
    - src/BS2BG.Core/Export/BosJsonExportPlanner.cs
    - src/BS2BG.Core/Generation/TemplateProfileCatalogFactory.cs
  modified:
    - src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs
    - src/BS2BG.Core/Export/BosJsonExportWriter.cs
    - src/BS2BG.Cli/Program.cs
    - src/BS2BG.App/AppBootstrapper.cs
    - tests/BS2BG.Tests/BS2BG.Tests.csproj
    - tests/BS2BG.Tests/CliGenerationTests.cs
    - tests/BS2BG.Tests/TemplateGenerationServiceTests.cs
    - tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs

key-decisions:
  - "Keep CLI generation as a thin Core service composition layer so command parsing never forks formatter or writer semantics."
  - "Move install-relative bundled catalog loading into Core for CLI use while leaving App custom-profile catalog composition as an App service."
  - "Extract BoS JSON path planning beside the writer and delegate writer path generation to it so overwrite preflight and writes cannot drift."

patterns-established:
  - "HeadlessGenerationService validates with ProjectValidationService before any export writer call and blocks on diagnostic blockers."
  - "OutputIntent.All preflights all selected BodyGen and BoS targets before writing when overwrite is disabled."
  - "CLI tests call Program.Main in process under a non-parallel console-capture collection."

requirements-completed: [AUTO-01]

duration: 28 min
completed: 2026-04-28
---

# Phase 05 Plan 02: Headless Generation Service and CLI Wiring Summary

**Validation-first CLI generation over the existing Core project load, validation, template/morph generation, and BodyGen/BoS export writer paths.**

## Performance

- **Duration:** 28 min
- **Started:** 2026-04-28T02:28:00Z
- **Completed:** 2026-04-28T02:56:34Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- Added `HeadlessGenerationService` to load `.jbs2bg` projects, validate before writes, refuse existing targets without overwrite, generate through existing Core services, and return stable automation exit codes `0` through `4`.
- Added `BosJsonExportPlanner` and mechanically updated `BosJsonExportWriter` to use the same planned sanitized/deduplicated filenames for both preflight and writes.
- Wired `BS2BG.Cli generate` to the Core service with install-relative bundled profile loading, stdout for successful written paths, and stderr for validation/overwrite/I/O failures.
- Expanded CLI tests to cover validation blockers, BodyGen and BoS writes, overwrite refusal, malformed/missing projects, I/O failure, partial write ledgers, in-process `Program.Main`, install-relative profile lookup, and omit-redundant forwarding.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: headless generation service behavior tests** - `2d7126e0` (test)
2. **Task 1 GREEN: validation-first headless generation service** - `689d9598` (feat)
3. **Task 2 RED: in-process generate CLI behavior tests** - `59f23529` (test)
4. **Task 2 GREEN: CLI wiring and install-relative Core catalog** - `eabc8c5d` (feat)

**Plan metadata:** pending final docs commit

_Note: Both tasks used RED then GREEN commits._

## Files Created/Modified

- `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` - Orchestrates project loading, validation, overwrite preflight, generation, writer calls, and outcome ledgers.
- `src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs` - Adds optional per-file write ledger details to generation results.
- `src/BS2BG.Core/Export/BosJsonExportPlanner.cs` - Plans BoS JSON output paths with writer-equivalent filename sanitization and duplicate handling.
- `src/BS2BG.Core/Export/BosJsonExportWriter.cs` - Mechanically delegates filename planning to `BosJsonExportPlanner`; JSON content semantics unchanged.
- `src/BS2BG.Core/Generation/TemplateProfileCatalogFactory.cs` - Loads bundled profiles from `AppContext.BaseDirectory` for CLI/Core automation.
- `src/BS2BG.Cli/Program.cs` - Composes Core services and maps generation results to stdout/stderr and process exit codes.
- `src/BS2BG.App/AppBootstrapper.cs` - Qualifies the App catalog factory after the Core factory introduced the same type name.
- `tests/BS2BG.Tests/BS2BG.Tests.csproj` - References `BS2BG.Cli` for in-process CLI tests.
- `tests/BS2BG.Tests/CliGenerationTests.cs` - Adds Core service and Program.Main integration coverage under serialized console capture.
- `tests/BS2BG.Tests/TemplateGenerationServiceTests.cs` - Qualifies the App catalog factory alias to avoid ambiguity.
- `tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs` - Qualifies the App catalog factory alias to avoid ambiguity.

## Decisions Made

- CLI generation is composed directly from Core services in `Program.cs` with no `BS2BG.App` or Avalonia dependency.
- Core bundled profile lookup for CLI uses only `AppContext.BaseDirectory` by default, plus an explicit constructor override for tests/custom hosts.
- `OutputIntent.All` does not roll back successful BodyGen writes after a later BoS write failure; it reports `IoFailure` and preserves a ledger stating that BodyGen artifacts remain present.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Resolved App/test catalog factory ambiguity**
- **Found during:** Task 2 (Wire generate command to service and script-friendly output)
- **Issue:** Adding `BS2BG.Core.Generation.TemplateProfileCatalogFactory` made existing App and test references to `TemplateProfileCatalogFactory` ambiguous where both App and Core namespaces were imported.
- **Fix:** Qualified the App bootstrapper registration and added test aliases where existing tests intentionally target the App catalog factory.
- **Files modified:** `src/BS2BG.App/AppBootstrapper.cs`, `tests/BS2BG.Tests/TemplateGenerationServiceTests.cs`, `tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs`
- **Verification:** `dotnet build BS2BG.sln` and `dotnet test BS2BG.sln --filter FullyQualifiedName~CliGeneration` passed.
- **Committed in:** `eabc8c5d`

---

**Total deviations:** 1 auto-fixed (1 blocking).
**Impact on plan:** The fix was required by the Core factory move and did not alter App runtime behavior.

## Issues Encountered

- Parallel verification commands caused transient App build file locks in `obj\Debug\net10.0`; rerunning the targeted tests sequentially passed. No code changes were required.

## User Setup Required

None - no external service configuration required.

## TDD Gate Compliance

- RED commits present: `2d7126e0`, `59f23529`
- GREEN commits present: `689d9598`, `eabc8c5d`
- REFACTOR commits: none needed

## Known Stubs

None.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: cli-output-filesystem | `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` | CLI-controlled project and output paths now drive filesystem writes; mitigated with validation-first behavior, overwrite preflight, and writer-owned atomic exports. |

## Verification

- `dotnet build BS2BG.sln` - passed.
- `dotnet test BS2BG.sln --filter FullyQualifiedName~CliGeneration` - passed (19 tests).
- `dotnet test BS2BG.sln --filter FullyQualifiedName~ExportWriterTests` - passed (16 tests).
- Acceptance checks verified `ProjectValidationService.Validate` precedes writer calls in `HeadlessGenerationService`, writer calls use `BodyGenIniExportWriter`/`BosJsonExportWriter`, overwrite refusal is present, no direct output `File.WriteAllText` exists in the service, Program.cs has no `BS2BG.App` or Avalonia using, and CLI tests exercise in-process console capture.

## Next Phase Readiness

Plan 03 can build portable bundle automation on top of the same headless generation service, overwrite semantics, validation report, and writer-owned BoS output planning.

## Self-Check: PASSED

- Verified created files exist on disk: `HeadlessGenerationService.cs`, `BosJsonExportPlanner.cs`, `TemplateProfileCatalogFactory.cs`, and this summary.
- Verified task commits `2d7126e0`, `689d9598`, `59f23529`, and `eabc8c5d` exist in git history.

---
*Phase: 05-automation-sharing-and-release-trust*
*Completed: 2026-04-28*
