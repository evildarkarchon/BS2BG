---
phase: 03-validation-and-diagnostics
plan: 07
subsystem: diagnostics
tags: [atomic-writes, save-export-failures, reactiveui, viewmodel, tdd]

requires:
  - phase: 03-validation-and-diagnostics
    provides: [AtomicWriteException and outcome ledger from plan 03-03, export preview confirmation state from plan 03-06]
provides:
  - Project save failures routed through atomic batch ledger exceptions
  - Binding-ready file operation ledger rows for App failure presentation
  - Save/export failure status copy with written/restored/skipped/left-untouched outcome details
affects: [validation-and-diagnostics, export-workflow, project-roundtrip, diagnostics-ui]

tech-stack:
  added: []
  patterns:
    - Atomic write ledgers are surfaced through App ViewModel status and ObservableCollection rows
    - Save/export success flows remain frictionless while failure paths expose outcome diagnostics

key-files:
  created:
    - src/BS2BG.App/ViewModels/Workflow/FileOperationLedgerViewModel.cs
  modified:
    - src/BS2BG.Core/Serialization/ProjectFileService.cs
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - tests/BS2BG.Tests/ExportWriterTests.cs
    - tests/BS2BG.Tests/MainWindowViewModelTests.cs

key-decisions:
  - "Route project saves through AtomicFileWriter.WriteAtomicBatch so save commit failures expose the same ledger shape as pair/batch exports without adding save preview friction."
  - "Keep file operation ledger presentation in the App ViewModel layer using binding-ready rows while Core continues to own atomic write outcomes."

patterns-established:
  - "ReportFileOperationFailure detects AtomicWriteException directly or through aggregate/inner exceptions and formats UI-SPEC failure copy."
  - "FileOperationLedgerViewModel maps Core outcomes to exact labels: Written, Restored, Skipped, Left untouched, and Incomplete/unknown."

requirements-completed: [DIAG-05]

duration: 4 min
completed: 2026-04-27
---

# Phase 03 Plan 07: Atomic Save/Export Failure Ledger Summary

**Save and export failures now surface atomic write outcome ledgers with actionable per-file status while preserving normal save/export output behavior.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-27T04:35:55Z
- **Completed:** 2026-04-27T04:39:14Z
- **Tasks:** 2 completed
- **Files modified:** 5

## Accomplishments

- Added TDD coverage proving export writer failures preserve observable `AtomicWriteException` ledger entries and project save failures can expose ledger details.
- Routed project file saves through `AtomicFileWriter.WriteAtomicBatch` so single-file save commit failures produce `AtomicWriteException` entries without changing successful serialization content.
- Added `FileOperationLedgerViewModel` rows with exact UI labels for `Written`, `Restored`, `Skipped`, `Left untouched`, and `Incomplete/unknown`.
- Updated `MainWindowViewModel` save, BodyGen export, and BoS JSON export catch paths to show `File operation incomplete` UI-SPEC copy, original exception text, and binding-ready ledger rows.
- Preserved save flow without preview/confirmation and did not modify golden expected fixtures or byte-sensitive writer formatting.

## Task Commits

Each task was committed atomically with TDD gates:

1. **Task 1 RED: Ledger propagation coverage** - `189110b9` (test)
2. **Task 1 GREEN: Project save ledger propagation** - `69ae1cbb` (feat)
3. **Task 2 RED: Shell ledger status coverage** - `007ba5db` (test)
4. **Task 2 GREEN: Shell ledger reporting rows/status** - `29d44a0d` (feat)

**Plan metadata:** pending final docs commit.

## Files Created/Modified

- `src/BS2BG.App/ViewModels/Workflow/FileOperationLedgerViewModel.cs` - Binding-ready file operation outcome row with exact UI labels and detail text.
- `src/BS2BG.Core/Serialization/ProjectFileService.cs` - Saves through atomic batch writing so commit failures can carry ledger entries.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Exposes `LastFileOperationLedger`/`HasFileOperationLedger` and formats atomic save/export failures with UI-SPEC copy.
- `tests/BS2BG.Tests/ExportWriterTests.cs` - Adds ledger propagation tests for BodyGen export and project save failure paths.
- `tests/BS2BG.Tests/MainWindowViewModelTests.cs` - Adds ViewModel tests for save/export failure status, ledger rows, and no export confirmation during save.

## Decisions Made

- Project saves now use the existing batch atomic writer for single-file writes because `WriteAtomicBatch` is the ledger-producing path; this preserves no-preview save UX and successful `.jbs2bg` serialization output.
- Ledger row formatting stays in the App workflow ViewModel layer rather than Core so UI labels can follow the Phase 3 UI-SPEC without changing Core outcome enums.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Cleared stale ledger state before new save/export attempts**
- **Found during:** Task 2 (Format save/export failure ledgers in MainWindow)
- **Issue:** A successful operation after a prior failure could otherwise leave stale ledger rows visible, overclaiming the latest operation state.
- **Fix:** Added `ClearFileOperationLedger()` and call it before save/export attempts that proceed to filesystem writes.
- **Files modified:** `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`
- **Verification:** `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests"` passed.
- **Committed in:** `29d44a0d`

**2. [Rule 3 - Blocking] Used supported dotnet test invocation**
- **Found during:** Task verification
- **Issue:** Prior Phase 3 plans established that the planned `dotnet test ... -x` command is unsupported by this .NET/MSBuild environment.
- **Fix:** Ran the same focused filters without `-x`.
- **Files modified:** None
- **Verification:** Focused ExportWriter and MainWindowViewModel test runs passed.
- **Committed in:** N/A (command adjustment only)

---

**Total deviations:** 2 auto-fixed (1 missing critical state-clearing fix, 1 blocking command adjustment)
**Impact on plan:** Both preserve correctness and verification coverage without scope creep or output-format changes.

## Issues Encountered

- Existing analyzer warnings from earlier Phase 3 diagnostics files/tests still appear during focused builds; they are outside this plan's changes and do not fail the suite.

## Known Stubs

None. Stub scan matches in `MainWindowViewModel.cs` were optional nullable constructor parameters and intentional selection-clearing assignments, not UI/data stubs.

## Threat Flags

None. The plan threat model already covered the filesystem exception-to-App status boundary, save/export catch behavior, and byte-sensitive writer preservation.

## TDD Gate Compliance

- RED commits present: `189110b9`, `007ba5db`
- GREEN commits present after RED: `69ae1cbb`, `29d44a0d`
- Refactor commit: not needed

## Validation Performed

- `dotnet test --filter "FullyQualifiedName~ExportWriterTests"` — passed (16 tests).
- `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests"` — passed (42 tests).
- `dotnet test --filter "FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~ExportWriterTests"` — passed (58 tests).
- `git diff -- tests/fixtures/expected` — no changes.
- Acceptance checks confirmed `NormalizeCrLf` remains in `BodyGenIniExportWriter.cs`, `PreviewBosJson` remains in `BosJsonExportWriter.cs`, `LastFileOperationLedger` and `File operation incomplete` are present in `MainWindowViewModel.cs`, and `Incomplete/unknown` is present in `FileOperationLedgerViewModel.cs`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 03-08 can bind `LastFileOperationLedger` and `HasFileOperationLedger` into the Diagnostics/shell UI alongside preview rows, with Core writer success formatting and save/export command behavior preserved.

## Self-Check: PASSED

- Verified key files exist: `ProjectFileService.cs`, `FileOperationLedgerViewModel.cs`, `MainWindowViewModel.cs`, `ExportWriterTests.cs`, and `MainWindowViewModelTests.cs`.
- Verified task commits exist in git history: `189110b9`, `69ae1cbb`, `007ba5db`, and `29d44a0d`.

---
*Phase: 03-validation-and-diagnostics*
*Completed: 2026-04-27*
