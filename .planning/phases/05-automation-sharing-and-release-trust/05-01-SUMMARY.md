---
phase: 05-automation-sharing-and-release-trust
plan: 01
subsystem: cli
tags: [dotnet, system-commandline, automation, headless-generation]

requires:
  - phase: 04-profile-extensibility-and-controlled-customization
    provides: custom profile/project trust domains reused by future headless generation
provides:
  - Dedicated BS2BG.Cli executable project registered in the solution
  - System.CommandLine generate contract with explicit output intent and safety options
  - Core headless generation request/result and exit-code contracts
affects: [automation, release-packaging, portable-bundles, cli-generation]

tech-stack:
  added: [System.CommandLine 2.0.7]
  patterns: [thin CLI parser over Core automation contracts, explicit output-intent enum]

key-files:
  created:
    - src/BS2BG.Cli/BS2BG.Cli.csproj
    - src/BS2BG.Cli/Program.cs
    - src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs
    - tests/BS2BG.Tests/CliGenerationTests.cs
  modified:
    - Directory.Packages.props
    - BS2BG.sln

key-decisions:
  - "Use a dedicated BS2BG.Cli project that references Core only, preserving D-01 and avoiding Avalonia/App startup for automation."
  - "Represent output intent and CLI outcomes as Core enums so later services consume typed requests instead of parser strings."

patterns-established:
  - "CLI commands parse with System.CommandLine and immediately map user input into BS2BG.Core.Automation contracts."
  - "Bundled profile JSON files are copied to both CLI build and publish outputs beside the CLI assembly/apphost."

requirements-completed: [AUTO-01]

duration: 15 min
completed: 2026-04-28
---

# Phase 05 Plan 01: CLI Foundation and Headless Contracts Summary

**Dedicated BS2BG.Cli executable with System.CommandLine generate parsing and typed Core automation contracts for explicit headless output selection.**

## Performance

- **Duration:** 15 min
- **Started:** 2026-04-28T02:32:51Z
- **Completed:** 2026-04-28T02:47:59Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Added a separate `BS2BG.Cli` `net10.0` executable project with a Core-only reference, central `System.CommandLine` pin, solution registration, and build/publish profile asset copying.
- Implemented the `generate` command contract with required `--project`, `--output`, `--intent`, plus `--overwrite` and `--omit-redundant-sliders` options.
- Added Core automation contracts for output intent, stable exit codes, generation requests/results, and tests that protect the CLI contract.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: CLI project contract tests** - `8f2649ec` (test)
2. **Task 1 GREEN: dedicated CLI executable shell** - `6e1cc72d` (feat)
3. **Task 2 RED: headless contract tests** - `ac69092e` (test)
4. **Task 2 GREEN: headless generation contracts** - `6684af08` (feat)

**Plan metadata:** pending final docs commit

_Note: TDD tasks produced test then feature commits._

## Files Created/Modified

- `Directory.Packages.props` - Pins `System.CommandLine` 2.0.7 centrally.
- `BS2BG.sln` - Registers `BS2BG.Cli` under the `src` solution folder.
- `src/BS2BG.Cli/BS2BG.Cli.csproj` - Defines the dedicated CLI executable, publish properties, Core reference, and bundled profile asset copy rules.
- `src/BS2BG.Cli/Program.cs` - Builds the System.CommandLine root/generate parser and maps intent text into `HeadlessGenerationRequest`.
- `src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs` - Adds typed request/result, output intent, and stable exit-code contracts.
- `tests/BS2BG.Tests/CliGenerationTests.cs` - Adds executable CLI contract tests for solution registration, parser behavior, profile assets, and Core contracts.

## Decisions Made

- Kept CLI composition separate from `BS2BG.App` and Avalonia to satisfy D-01 and preserve Core/App boundaries.
- Used typed Core contracts (`OutputIntent`, `HeadlessGenerationExitCode`, `HeadlessGenerationRequest`) so Plan 02 can add generation services without passing parser strings through Core.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- A parallel final verification attempt caused an Avalonia build file-lock during `dotnet test`; rerunning the test command sequentially passed. No code changes were required.

## User Setup Required

None - no external service configuration required.

## TDD Gate Compliance

- RED commits present: `8f2649ec`, `ac69092e`
- GREEN commits present: `6e1cc72d`, `6684af08`
- REFACTOR commits: none needed

## Known Stubs

None.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: cli-args-to-filesystem | `src/BS2BG.Cli/Program.cs` | New CLI arguments collect project/output paths for future filesystem writes; current mitigation uses typed System.CommandLine options and Core request mapping. |

## Verification

- `dotnet build BS2BG.sln` - passed.
- `dotnet test BS2BG.sln --filter FullyQualifiedName~CliGeneration` - passed (8 tests).

## Next Phase Readiness

Plan 02 can now implement the headless generation service behind `HeadlessGenerationRequest` without adding parser churn or duplicating output writers.

## Self-Check: PASSED

- Verified created files exist on disk.
- Verified task commits `8f2649ec`, `6e1cc72d`, `ac69092e`, and `6684af08` exist in git history.

---
*Phase: 05-automation-sharing-and-release-trust*
*Completed: 2026-04-28*
