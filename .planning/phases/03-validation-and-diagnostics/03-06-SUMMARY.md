---
phase: 03-validation-and-diagnostics
plan: 06
subsystem: export-diagnostics-ui
tags: [avalonia, reactiveui, export-preview, overwrite-confirmation, tdd]

requires:
  - phase: 03-validation-and-diagnostics
    provides: [Core ExportPreviewService and export preview DTOs from plan 03-03]
provides:
  - Shell export preview commands and binding-ready preview file rows
  - Export preview summaries with exact create/overwrite UI-SPEC copy
  - Overwrite-risk confirmation dialog contract and Avalonia dialog copy
  - Confirmation-gated BodyGen and BoS export flows that preserve writer output behavior
affects: [validation-and-diagnostics, export-workflow, diagnostics-ui]

tech-stack:
  added: []
  patterns:
    - ReactiveCommand export preview commands over Core read-only preview service
    - Risk-gated export writes using IAppDialogService confirmation

key-files:
  created:
    - src/BS2BG.App/ViewModels/Workflow/ExportPreviewViewModel.cs
  modified:
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - src/BS2BG.App/Services/IAppDialogService.cs
    - src/BS2BG.App/Services/WindowAppDialogService.cs
    - src/BS2BG.App/ViewModels/MorphsViewModel.cs
    - tests/BS2BG.Tests/MainWindowViewModelTests.cs
    - tests/BS2BG.Tests/M6UxViewModelTests.cs
    - tests/BS2BG.Tests/MorphsViewModelTests.cs

key-decisions:
  - "Keep export preview state in MainWindowViewModel as read-only App-layer presentation over Core ExportPreviewService."
  - "Require overwrite confirmation for existing target files while allowing routine create-new BodyGen exports to proceed without confirmation friction."

patterns-established:
  - "PreviewBodyGenExportCommand and PreviewBosJsonExportCommand populate ExportPreviewFiles, ExportPreviewSummary, and HasExportPreview without calling writer Write methods."
  - "Export commands compute the same preview before writing and call ConfirmExportOverwriteAsync only for overwrite targets."

requirements-completed: [DIAG-04]

duration: 4 min
completed: 2026-04-27
---

# Phase 03 Plan 06: Export Preview and Overwrite Confirmation Summary

**Shell export workflows now expose read-only target previews and gate existing-file overwrites with UI-SPEC confirmation while create-new exports stay frictionless.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-27T04:17:02Z
- **Completed:** 2026-04-27T04:21:27Z
- **Tasks:** 2 completed
- **Files modified:** 8

## Accomplishments

- Added `ExportPreviewViewModel` rows with target path, export kind, create/overwrite label, overwrite flag, and generated snippet lines.
- Wired `PreviewBodyGenExportCommand` and `PreviewBosJsonExportCommand` into `MainWindowViewModel` using `ExportPreviewService` without invoking export writers.
- Added shell preview state: `ExportPreviewFiles`, `ExportPreviewSummary`, and `HasExportPreview` with exact UI-SPEC create-new and overwrite copy.
- Added `ConfirmExportOverwriteAsync(ExportPreviewResult, CancellationToken)` to `IAppDialogService` and implemented Avalonia dialog copy: `Overwrite existing output files?`, `Export Anyway`, and `Keep Existing Files`.
- Updated BodyGen and BoS export commands to compute preview before writes, store it for UI binding, skip confirmation for create-new exports, and cancel safely before writing when overwrite confirmation is declined.

## Task Commits

Each task was committed atomically with TDD gates:

1. **Task 1 RED: Export preview shell tests** - `474b64c3` (test)
2. **Task 1 GREEN: Shell export preview state** - `1bedfff7` (feat)
3. **Task 2 RED: Export confirmation tests** - `8464ecb3` (test)
4. **Task 2 GREEN: Overwrite confirmation gating** - `2c3c3932` (feat)

**Plan metadata:** pending final docs commit.

## Files Created/Modified

- `src/BS2BG.App/ViewModels/Workflow/ExportPreviewViewModel.cs` - Binding-ready export preview row for target paths, effects, kind, and snippets.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Adds preview commands/state and confirmation-gated export execution.
- `src/BS2BG.App/Services/IAppDialogService.cs` - Adds the export overwrite confirmation contract.
- `src/BS2BG.App/Services/WindowAppDialogService.cs` - Implements the UI-SPEC overwrite confirmation dialog and file list.
- `src/BS2BG.App/ViewModels/MorphsViewModel.cs` - Updates design-time/null dialog implementation for the expanded dialog-service interface.
- `tests/BS2BG.Tests/MainWindowViewModelTests.cs` - Adds preview no-write, create-new no-confirmation, overwrite confirmation, and cancel-path coverage.
- `tests/BS2BG.Tests/M6UxViewModelTests.cs` - Updates test dialog fake for the expanded interface.
- `tests/BS2BG.Tests/MorphsViewModelTests.cs` - Updates test dialog fake for the expanded interface.

## Decisions Made

- Kept App-layer preview rows separate from Core preview DTOs so AXAML can bind stable `TargetPath`, `EffectLabel`, `Kind`, and `SnippetLines` properties.
- Used exact UI-SPEC create-new and overwrite summaries rather than deriving alternate copy from `ExportPreviewResult.HasBatchRisk`, because the plan's must-have truth requires routine create-new exports to avoid confirmation friction.
- Applied overwrite confirmation only when at least one target file already exists; create-new BodyGen exports do not call confirmation even though they produce two files.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated all dialog-service fakes after adding confirmation contract**
- **Found during:** Task 2 (Gate risky exports with confirmation only when needed)
- **Issue:** Adding `ConfirmExportOverwriteAsync` to `IAppDialogService` required existing null/test implementations to compile even where export confirmation is not used.
- **Fix:** Added default true-returning implementations in `MainWindowViewModel.NullAppDialogService`, `MorphsViewModel.NullAppDialogService`, and existing test dialog fakes.
- **Files modified:** `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `tests/BS2BG.Tests/M6UxViewModelTests.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`
- **Verification:** `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests"` passed.
- **Committed in:** `2c3c3932`

**2. [Rule 3 - Blocking] Used supported dotnet test invocation**
- **Found during:** Task verification
- **Issue:** Prior Phase 3 execution established that the planned `dotnet test ... -x` command is unsupported by this .NET/MSBuild environment.
- **Fix:** Ran the same focused filters without `-x`.
- **Files modified:** None
- **Verification:** Focused MainWindow and ExportPreviewService tests passed.
- **Committed in:** N/A (command adjustment only)

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both were required to compile and verify the intended confirmation contract; no export writer output semantics changed.

## Issues Encountered

- Running the two focused `dotnet test` commands in parallel briefly locked `BS2BG.App.dll`; rerunning `MainWindowViewModelTests` sequentially passed. No code changes were required.
- Existing analyzer warnings from earlier diagnostics files/tests still appear during focused builds; they are outside this plan's changes and do not fail the suite.

## Known Stubs

None. Stub scan matches were nullable optional dependencies, intentional null-selection assignments, or existing error text; no placeholder UI/data stubs were introduced.

## Threat Flags

None. The plan threat model already covered preview-to-filesystem write and dialog-service-to-user-decision boundaries; no new network, auth, or unplanned file-access surface was introduced.

## User Setup Required

None - no external service configuration required.

## TDD Gate Compliance

- RED commits present: `474b64c3`, `8464ecb3`
- GREEN commits present after RED: `1bedfff7`, `2c3c3932`
- Refactor commit: not needed

## Validation Performed

- `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests"` — passed (40 tests).
- `dotnet test --filter "FullyQualifiedName~ExportPreviewServiceTests"` — passed (2 tests).
- Acceptance checks confirmed `PreviewBodyGenExportCommand`, `PreviewBosJsonExportCommand`, preview no-write assertions for `templates.ini`, `morphs.ini`, and `.json`, `ConfirmExportOverwriteAsync`, confirmation counts 0/1, and `Overwrite existing output files?` dialog copy.

## Next Phase Readiness

Plan 03-07 can build save/export failure presentation on top of Core outcome ledgers and the shell's export preview/confirmation state. Plan 03-08 can bind export preview rows and summaries into the Diagnostics/shell UI without additional export workflow seams.

## Self-Check: PASSED

- Verified created file exists: `src/BS2BG.App/ViewModels/Workflow/ExportPreviewViewModel.cs`.
- Verified key modified files exist: `MainWindowViewModel.cs`, `IAppDialogService.cs`, `WindowAppDialogService.cs`, `MorphsViewModel.cs`, `MainWindowViewModelTests.cs`, `M6UxViewModelTests.cs`, and `MorphsViewModelTests.cs`.
- Verified task commit hashes exist in git history: `474b64c3`, `1bedfff7`, `8464ecb3`, and `2c3c3932`.

---
*Phase: 03-validation-and-diagnostics*
*Completed: 2026-04-27*
