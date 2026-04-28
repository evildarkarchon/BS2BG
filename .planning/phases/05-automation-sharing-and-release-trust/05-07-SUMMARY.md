---
phase: 05-automation-sharing-and-release-trust
plan: 07
subsystem: automation-sharing
tags: [cli, avalonia, portable-bundle, preview, release-trust]

requires:
  - phase: 05-06
    provides: PortableProjectBundleService and bundle manifest/scrubbing behavior
provides:
  - CLI bundle command with explicit output intent and overwrite safety
  - GUI portable bundle preview and create workflow using Core bundling service
  - Human-approved visual verification evidence for bundle preview layout, privacy copy, and overwrite affordance
affects: [automation-sharing-and-release-trust, cli-automation, avalonia-shell]

tech-stack:
  added: []
  patterns:
    - System.CommandLine automation command delegates to Core services without App dependencies
    - Avalonia shell preview-first workflow backed by ReactiveCommand and file dialog abstraction

key-files:
  created:
    - .planning/phases/05-automation-sharing-and-release-trust/05-07-VISUAL-VERIFICATION.md
  modified:
    - src/BS2BG.Cli/Program.cs
    - src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs
    - src/BS2BG.Core/Automation/HeadlessGenerationService.cs
    - src/BS2BG.App/AppBootstrapper.cs
    - src/BS2BG.App/Services/IFileDialogService.cs
    - src/BS2BG.App/Services/WindowFileDialogService.cs
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - src/BS2BG.App/Views/MainWindow.axaml
    - tests/BS2BG.Tests/PortableBundleServiceTests.cs
    - tests/BS2BG.Tests/CliGenerationTests.cs
    - tests/BS2BG.Tests/M6UxViewModelTests.cs
    - tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs
    - tests/BS2BG.Tests/MainWindowViewModelTests.cs

key-decisions:
  - "Use one AutomationExitCode surface for generate and bundle CLI outcomes so automation callers get consistent success, validation, overwrite, and I/O mappings."
  - "Use Core PortableProjectBundleService.Preview for GUI previews instead of creating temporary zips, preserving preview-before-write semantics."

patterns-established:
  - "Bundle target overwrite is explicit in both CLI (--overwrite) and GUI (BundleOverwriteAllowed)."
  - "Bundle preview copy exposes deterministic layout entries, referenced custom profile scope, and path privacy status before writing."

requirements-completed: [AUTO-01, AUTO-02]

duration: 1 min
completed: 2026-04-28
---

# Phase 05 Plan 07: Portable Bundle Automation and Preview Summary

**CLI and Avalonia shell portable bundle workflows now share the Core bundle service with previewed layout, privacy status, explicit intent, and overwrite safety.**

## Performance

- **Duration:** 1 min for checkpoint continuation; prior implementation completed in the recorded task commits.
- **Started:** 2026-04-28T04:50:03Z
- **Completed:** 2026-04-28T04:51:17Z
- **Tasks:** 3/3 completed
- **Files modified:** 14 source/test files plus 1 planning evidence file

## Accomplishments

- Added a `bundle` CLI command that loads `.jbs2bg` projects, previews bundle content, writes zip bundles, and maps Core bundle outcomes to stable automation exit codes.
- Added GUI preview/create state and commands for portable bundles, including zip save picking, deterministic layout preview, referenced-custom-profile copy, privacy status text, and explicit overwrite choice.
- Recorded approved human visual verification for the bundle preview workflow after the checkpoint was accepted.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Add CLI bundle command tests** - `b63ca8c7` (test)
2. **Task 1 GREEN: Add CLI bundle command** - `ccb10539` (feat)
3. **Task 2 RED: Add GUI bundle workflow tests** - `413972f1` (test)
4. **Task 2 GREEN: Add GUI bundle preview and create commands** - `652daf3a` (feat)
5. **Task 3: Visual verify bundle preview workflow** - `f4462ed3` (docs)

**Plan metadata:** pending final docs commit

_Note: TDD tasks produced RED and GREEN commits for the CLI and GUI workflow slices._

## Files Created/Modified

- `.planning/phases/05-automation-sharing-and-release-trust/05-07-VISUAL-VERIFICATION.md` - Records the approved human verification checkpoint evidence.
- `src/BS2BG.Cli/Program.cs` - Adds the portable bundle command, option validation, project loading, preview/create calls, and outcome-to-exit-code mapping.
- `src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs` - Renames/shared automation exit contracts so generate and bundle use one exit-code surface.
- `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` - Updates headless generation code to the shared automation exit contract.
- `src/BS2BG.App/AppBootstrapper.cs` - Registers `PortableProjectBundleService` for App ViewModel composition.
- `src/BS2BG.App/Services/IFileDialogService.cs` - Adds zip bundle save-picker abstraction.
- `src/BS2BG.App/Services/WindowFileDialogService.cs` - Implements the `.zip` save picker.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Adds bundle target, intent, overwrite, preview summary/entries, and preview/create commands.
- `src/BS2BG.App/Views/MainWindow.axaml` - Adds accessible portable bundle preview UI and Create Portable Bundle action.
- `tests/BS2BG.Tests/PortableBundleServiceTests.cs` - Covers CLI and GUI bundle behavior.
- `tests/BS2BG.Tests/CliGenerationTests.cs` - Updates existing CLI tests for the shared automation exit code naming.
- `tests/BS2BG.Tests/M6UxViewModelTests.cs` - Updates ViewModel construction for the new dependency.
- `tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs` - Updates test fakes/construction for the new dependency.
- `tests/BS2BG.Tests/MainWindowViewModelTests.cs` - Updates MainWindowViewModel test setup for the new dependency.

## Decisions Made

- Use one `AutomationExitCode` surface for generate and bundle CLI outcomes so automation callers get consistent success, validation, overwrite, and I/O mappings.
- Use Core `PortableProjectBundleService.Preview` for GUI previews instead of creating temporary zips, preserving preview-before-write semantics.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- `dotnet build BS2BG.sln` succeeded with pre-existing analyzer warnings in unrelated files and newly touched test callsites for CA1861. These warnings do not block this plan and were not expanded beyond the planned bundle work.

## User Setup Required

None - no external service configuration required.

## Verification

- `dotnet test BS2BG.sln --filter FullyQualifiedName~PortableBundle` — Passed: 27/27.
- `dotnet build BS2BG.sln` — Succeeded with warnings, 0 errors.
- Human visual checkpoint — Approved by user response: `approved`.

## Known Stubs

None found in the plan-created or plan-modified bundle workflow surface that prevent the plan goal.

## Threat Flags

None. The filesystem bundle target and privacy-preview surfaces are covered by the plan threat model (`T-05-07-01`, `T-05-07-02`).

## TDD Gate Compliance

- RED commit present for CLI bundle command: `b63ca8c7`.
- GREEN commit present after CLI RED: `ccb10539`.
- RED commit present for GUI bundle workflow: `413972f1`.
- GREEN commit present after GUI RED: `652daf3a`.

## Next Phase Readiness

- Portable bundle creation is available from CLI automation and the Avalonia shell.
- Bundle preview/write behavior is ready for phase-level verification and release-trust documentation checks.

## Self-Check: PASSED

- Verified summary and visual verification evidence files exist.
- Verified key implementation files exist.
- Verified task commits exist: `b63ca8c7`, `ccb10539`, `413972f1`, `652daf3a`, `f4462ed3`.

---
*Phase: 05-automation-sharing-and-release-trust*
*Completed: 2026-04-28*
