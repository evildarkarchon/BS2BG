---
phase: 03-validation-and-diagnostics
plan: 02
subsystem: import-diagnostics
tags: [npc-import, diagnostics, preview, tdd, core]

requires:
  - phase: 02-workflow-persistence-filtering-and-undo-hardening
    provides: stable NPC identity, filtering, and import workflow boundaries
  - phase: 03-validation-and-diagnostics
    provides: core project/profile diagnostics foundation from plan 03-01
provides:
  - read-only NPC import preview service for parser rows, diagnostics, duplicate classification, and encoding facts
  - parser diagnostics for within-file duplicate NPC rows skipped by direct import
  - TDD coverage for DIAG-03 preview/no-mutation behavior
affects: [validation-and-diagnostics, morph-assignment-flow, npc-import]

tech-stack:
  added: []
  patterns:
    - Core read-only preview DTOs over existing parser result objects
    - Case-insensitive Mod|EditorId duplicate classification without App-layer mutation

key-files:
  created:
    - src/BS2BG.Core/Import/NpcImportPreviewResult.cs
    - src/BS2BG.Core/Import/NpcImportPreviewService.cs
    - tests/BS2BG.Tests/NpcImportPreviewServiceTests.cs
  modified:
    - src/BS2BG.Core/Import/NpcTextParser.cs

key-decisions:
  - "Use existing NpcImportDiagnostic entries for within-file duplicate rows so direct import keeps skip behavior while preview can explain skipped rows."
  - "Keep existing database/project duplicate classification in NpcImportPreviewService rather than changing parser policy or App mutation paths."

patterns-established:
  - "NPC import previews are Core-only, read-only projections over NpcTextParser results; commit remains the caller's responsibility."
  - "Within-file duplicates are parser diagnostics; existing-state duplicates are preview-service classifications."

requirements-completed: [DIAG-03]

duration: 4 min
completed: 2026-04-27
---

# Phase 03 Plan 02: NPC Import Preview Facts Summary

**Read-only NPC import preview APIs with duplicate diagnostics, fallback encoding facts, and no-mutation classification.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-27T03:49:16Z
- **Completed:** 2026-04-27T03:52:44Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Added parser diagnostics for skipped within-file duplicate NPC rows while preserving direct import's first-row-kept policy.
- Added Core preview DTO/service APIs that report parsed rows, rows to add, existing duplicates, diagnostics, and fallback encoding facts without mutating caller collections.
- Added TDD coverage for duplicate parser diagnostics, no-mutation preview classification, invalid-line diagnostics, and fallback-encoded file preview.

## Task Commits

Each task was committed atomically with TDD gates:

1. **Task 1 RED: Preserve duplicate diagnostics in NPC parsing** - `fb8eaa26` (test)
2. **Task 1 GREEN: Preserve duplicate diagnostics in NPC parsing** - `9bd92127` (feat)
3. **Task 2 RED: Add no-mutation NPC import preview service** - `3bbb924c` (test)
4. **Task 2 GREEN: Add no-mutation NPC import preview service** - `02a6ba3e` (feat)

**Plan metadata:** final `docs(03-02)` commit containing this summary and state updates

## Files Created/Modified

- `src/BS2BG.Core/Import/NpcTextParser.cs` - Emits `Duplicate NPC row skipped` diagnostics with the canonical `Mod|EditorID` key when duplicate rows are skipped.
- `src/BS2BG.Core/Import/NpcImportPreviewResult.cs` - Immutable preview result for parsed rows, rows to add, existing duplicates, diagnostics, encoding facts, and summary counts.
- `src/BS2BG.Core/Import/NpcImportPreviewService.cs` - Read-only preview service wrapping `NpcTextParser.ParseFile`/`ParseText` and classifying existing duplicates by case-insensitive `(Mod, EditorId)`.
- `tests/BS2BG.Tests/NpcImportPreviewServiceTests.cs` - Focused DIAG-03 tests for parser duplicate diagnostics, preview classification/no-mutation, invalid rows, and fallback encoding.

## Decisions Made

- Used existing `NpcImportDiagnostic` entries for within-file duplicate rows instead of adding a parallel duplicate-detail type, because duplicate rows are parser diagnostics and direct imports already consume parser diagnostics.
- Classified existing database/project duplicates in `NpcImportPreviewService`, keeping `NpcTextParser` responsible only for source parsing and within-file duplicate policy.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used supported dotnet test invocation after planned `-x` flag failed**
- **Found during:** Task 1 verification
- **Issue:** `dotnet test --filter FullyQualifiedName~NpcImportPreviewServiceTests -x` passed `-x` through to MSBuild, which rejected it as an unknown switch in this environment.
- **Fix:** Ran the same focused test filters without `-x`; the focused and full test suites passed.
- **Files modified:** None
- **Verification:** `dotnet test --filter "FullyQualifiedName~NpcImportPreviewServiceTests"`, `dotnet test --filter "FullyQualifiedName~MorphsViewModelTests"`, and `dotnet test` passed.
- **Committed in:** N/A (command adjustment only)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Verification coverage was preserved; only the unsupported command flag was changed.

## Issues Encountered

- A concurrent pair of focused `dotnet test` runs briefly locked `BS2BG.App.dll`; rerunning the focused preview tests sequentially passed. No code changes were required.
- Existing analyzer warnings from prior diagnostics files/tests appeared during builds; they are unrelated to this plan and did not fail the suite.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None.

## Threat Flags

None - the plan's threat model already covered the local NPC text file parser boundary and read-only parser-result-to-preview classification.

## TDD Gate Compliance

- RED gate present for Task 1: `fb8eaa26`
- GREEN gate present for Task 1: `9bd92127`
- RED gate present for Task 2: `3bbb924c`
- GREEN gate present for Task 2: `02a6ba3e`
- REFACTOR gate: not needed; no behavior-neutral cleanup commit was required.

## Verification

- `dotnet test --filter "FullyQualifiedName~NpcImportPreviewServiceTests"` — Passed (3 tests).
- `dotnet test --filter "FullyQualifiedName~MorphsViewModelTests"` — Passed (37 tests).
- `dotnet test` — Passed (310 tests).
- Acceptance checks confirmed `Duplicate NPC row skipped`, `PreviewFile`, `PreviewText`, `RowsToAdd`, `ExistingDuplicates`, `UsedFallbackEncoding`, and `EncodingName` are present, and tests assert no mutation of the existing NPC collection.

## Self-Check: PASSED

- Verified key files exist: `NpcImportPreviewResult.cs`, `NpcImportPreviewService.cs`, `NpcImportPreviewServiceTests.cs`, `NpcTextParser.cs`, and this summary.
- Verified task commits exist: `fb8eaa26`, `9bd92127`, `3bbb924c`, and `02a6ba3e`.

## Next Phase Readiness

Ready for plan 03-03. The Core import preview seam exists for later App-layer preview/commit wiring without changing direct import behavior.

---
*Phase: 03-validation-and-diagnostics*
*Completed: 2026-04-27*
