---
phase: 03-validation-and-diagnostics
plan: 03
subsystem: diagnostics
tags: [core, export-preview, atomic-writes, diagnostics, tdd]

requires:
  - phase: 03-validation-and-diagnostics
    provides: Core diagnostics patterns from plans 03-01 and 03-02
provides:
  - Read-only BodyGen and BoS export preview DTOs and service
  - Atomic write outcome ledger and AtomicWriteException for commit/rollback failures
  - Focused xUnit coverage for DIAG-04 and DIAG-05
affects: [validation-and-diagnostics, export-workflow, save-failure-reporting]

tech-stack:
  added: []
  patterns:
    - Core read-only preview service wrapping existing generation/path rules
    - Immutable file outcome entries carried by IO exceptions

key-files:
  created:
    - src/BS2BG.Core/Diagnostics/ExportPreviewService.cs
    - src/BS2BG.Core/Diagnostics/ExportPreviewResult.cs
    - src/BS2BG.Core/IO/WriteOutcomeLedger.cs
    - src/BS2BG.Core/IO/AtomicWriteException.cs
    - src/BS2BG.Core/Properties/AssemblyInfo.cs
    - tests/BS2BG.Tests/ExportPreviewServiceTests.cs
    - tests/BS2BG.Tests/AtomicFileWriterOutcomeTests.cs
  modified:
    - src/BS2BG.Core/IO/AtomicFileWriter.cs

key-decisions:
  - "Keep export preview read-only by duplicating writer filename rules instead of calling writer Write methods."
  - "Preserve existing setup/temp-write exception compatibility while adding outcome ledgers for commit and rollback failures."

patterns-established:
  - "Preview services return immutable DTOs with exact paths, create/overwrite state, snippets, and batch-risk flags."
  - "Atomic write failures that occur during commit/rollback throw AtomicWriteException with per-file outcome snapshots."

requirements-completed: [DIAG-04, DIAG-05]

duration: 5min
completed: 2026-04-27
---

# Phase 03 Plan 03: Export Preview and Atomic Outcome Ledger Summary

**Read-only export previews with writer-equivalent target paths plus atomic write ledgers that explain restored, skipped, untouched, and incomplete file states.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-27T03:55:22Z
- **Completed:** 2026-04-27T04:00:26Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Added `ExportPreviewService` and DTOs for BodyGen INI and BoS JSON previews without calling write APIs or changing sacred export writers.
- Added `WriteOutcomeLedger`, `FileWriteOutcome`, `FileWriteLedgerEntry`, and `AtomicWriteException` for actionable atomic write failure reporting.
- Extended `AtomicFileWriter.WriteAtomicBatch` to emit ledger states for commit failures and rollback failures while preserving existing public write method signatures.
- Added focused tests for export preview no-write behavior, overwrite/create state, BoS sanitized unique naming, and atomic outcome states.

## Task Commits

Each task was committed atomically:

1. **Task 1 RED: Export preview tests** - `16245f9b` (test)
2. **Task 1 GREEN: Export preview service** - `cfcc7cce` (feat)
3. **Task 2 RED: Atomic outcome tests** - `d6361975` (test)
4. **Task 2 GREEN: Atomic outcome ledger** - `df16a85c` (feat)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `src/BS2BG.Core/Diagnostics/ExportPreviewService.cs` - Read-only preview service for BodyGen and BoS exports.
- `src/BS2BG.Core/Diagnostics/ExportPreviewResult.cs` - Immutable preview result and per-file preview DTOs.
- `src/BS2BG.Core/IO/WriteOutcomeLedger.cs` - Outcome enum, immutable ledger entries, and ledger builder.
- `src/BS2BG.Core/IO/AtomicWriteException.cs` - IOException subtype carrying ledger entries and optional rollback exception.
- `src/BS2BG.Core/IO/AtomicFileWriter.cs` - Ledger tracking for commit/rollback failures, with cleanup comments added to empty catch blocks.
- `src/BS2BG.Core/Properties/AssemblyInfo.cs` - Test assembly visibility for the rollback-failure injection seam.
- `tests/BS2BG.Tests/ExportPreviewServiceTests.cs` - Export preview behavior tests.
- `tests/BS2BG.Tests/AtomicFileWriterOutcomeTests.cs` - Atomic outcome ledger tests.

## Decisions Made

- Keep export preview read-only by duplicating `BosJsonExportWriter` filename sanitization/uniqueness rules instead of invoking writer `Write` methods, because preview must not create directories or output files.
- Preserve raw setup/temp-write exceptions for compatibility with existing `ExportWriterTests`; `AtomicWriteException` is used for commit and rollback phases where the target-state ledger is most actionable.
- Use an internal rollback-failure injector visible only to tests to validate incomplete rollback ledger states without changing production APIs.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Compatibility] Preserved setup/temp-write exception compatibility**
- **Found during:** Task 2 (Add atomic write outcome ledger for failures)
- **Issue:** Wrapping pre-commit temp-write failures in `AtomicWriteException` broke existing tests that intentionally expect the original `DirectoryNotFoundException` before any target is touched.
- **Fix:** Kept setup/temp-write failures as raw exceptions and limited `AtomicWriteException` ledger reporting to commit/rollback failures; skipped state remains represented for later entries not attempted after a commit failure.
- **Files modified:** `src/BS2BG.Core/IO/AtomicFileWriter.cs`
- **Verification:** `dotnet test --filter FullyQualifiedName~ExportWriterTests` passed.
- **Committed in:** `df16a85c`

---

**Total deviations:** 1 auto-fixed (1 compatibility bug)
**Impact on plan:** The DIAG-05 ledger is available for actionable commit/rollback failure reporting while existing pre-commit failure behavior remains backward-compatible.

## Issues Encountered

- The plan's `dotnet test ... -x` commands are not accepted by this .NET/MSBuild environment (`MSB1001: Unknown switch`). Verification was rerun with the same filters and without `-x`.
- Two focused `dotnet test` runs were initially started in parallel and briefly contended for the App build output. The writer regression test was rerun sequentially and passed.

## Validation Performed

- `dotnet test --filter FullyQualifiedName~ExportPreviewServiceTests` — passed (2 tests).
- `dotnet test --filter FullyQualifiedName~AtomicFileWriterOutcomeTests` — passed (2 tests).
- `dotnet test --filter "FullyQualifiedName~ExportPreviewServiceTests|FullyQualifiedName~AtomicFileWriterOutcomeTests"` — passed (4 tests).
- `dotnet test --filter FullyQualifiedName~ExportWriterTests` — passed (14 tests).
- Acceptance criteria checked with `Select-String` for preview methods, ledger enum states, `AtomicWriteException.Entries`, and `WriteAtomicPair` compatibility.

## Known Stubs

None. Optional `null` defaults in constructors were reviewed as intentional API defaults, not UI stubs.

## Threat Flags

None. The plan already covered preview no-write behavior and filesystem atomic outcome reporting in the threat model.

## TDD Gate Compliance

- RED commits present: `16245f9b`, `d6361975`
- GREEN commits present after RED: `cfcc7cce`, `df16a85c`
- Refactor commit: not needed

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

DIAG-04 and DIAG-05 Core contracts are ready for App-layer preview confirmation and failure-message presentation in subsequent Phase 3 plans.

## Self-Check: PASSED

- Verified all key created/modified files exist.
- Verified task commit hashes `16245f9b`, `cfcc7cce`, `d6361975`, and `df16a85c` exist in git history.

---
*Phase: 03-validation-and-diagnostics*
*Completed: 2026-04-27*
