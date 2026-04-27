---
phase: 03-validation-and-diagnostics
plan: 08
subsystem: diagnostics-ui
tags: [avalonia, compiled-bindings, diagnostics, app-shell, tdd]

requires:
  - phase: 03-validation-and-diagnostics
    provides: [Diagnostics ViewModel from plan 03-04, NPC preview workflow from plan 03-05, export preview workflow from plan 03-06, file operation ledger rows from plan 03-07]
provides:
  - First-class Diagnostics workspace in the main app shell
  - Compiled-bound Diagnostics tab with report, detail, NPC preview, export preview, and ledger surfaces
  - Headless shell coverage for Diagnostics tab visibility, automation names, and ViewModel wiring
affects: [diagnostics-ui, app-shell, validation-and-diagnostics, phase-4-readiness]

tech-stack:
  added: []
  patterns:
    - Avalonia compiled bindings with x:DataType on Diagnostics DataTemplates
    - AppWorkspace enum routing for Templates, Morphs, and Diagnostics workspaces
    - Human visual verification checkpoint after automated build and focused tests

key-files:
  created: []
  modified:
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - src/BS2BG.App/Views/MainWindow.axaml
    - src/BS2BG.App/Views/MainWindow.axaml.cs
    - tests/BS2BG.Tests/AppShellTests.cs
    - tests/BS2BG.Tests/MainWindowViewModelTests.cs

key-decisions:
  - "Treat Diagnostics as a first-class workspace beside Templates and Morphs rather than a modal or text-only report."
  - "Keep preview, import, export, and ledger actions visibly distinct in the Diagnostics tab so read-only report surfaces do not imply automatic mutation."
  - "Record the human visual verification checkpoint as approved after automated tests and build passed."

patterns-established:
  - "Diagnostics tab binds through MainWindowViewModel.Diagnostics and existing Morphs/export/ledger shell surfaces with compiled DataTemplates."
  - "Shell tests verify Diagnostics tab automation names and ViewModel resolution before human visual approval."

requirements-completed: [DIAG-01, DIAG-02, DIAG-03, DIAG-04, DIAG-05]

duration: 11 min
completed: 2026-04-27
---

# Phase 03 Plan 08: Diagnostics Shell Tab Summary

**First-class Avalonia Diagnostics workspace with compiled-bound report, preview, export, and ledger surfaces exposed beside Templates and Morphs.**

## Performance

- **Duration:** 11 min
- **Started:** 2026-04-27T04:42:25Z
- **Completed:** 2026-04-27T04:53:00Z
- **Tasks:** 3 completed
- **Files modified:** 5

## Accomplishments

- Added `AppWorkspace.Diagnostics` routing and exposed `DiagnosticsViewModel Diagnostics` from `MainWindowViewModel` without regressing Templates or Morphs search behavior.
- Added a top-level `Diagnostics` tab to `MainWindow.axaml` with `Run Diagnostics`, `Copy Report`, grouped findings, selected detail text, NPC import preview controls, export preview controls, and file operation ledger presentation.
- Preserved Avalonia compiled-binding requirements with `x:DataType` on new Diagnostics data templates and automation names on actionable controls.
- Added headless shell tests for Diagnostics ViewModel resolution, active workspace selection, tab presence, and action automation names.
- Completed the blocking human visual verification checkpoint; user response was `approved`.

## Task Commits

Each implementation task was committed atomically with TDD gates:

1. **Task 1 RED: Diagnostics workspace shell tests** - `a86dc36f` (test)
2. **Task 1 GREEN: Diagnostics workspace ViewModel wiring** - `5ff987b9` (feat)
3. **Task 2 RED: Diagnostics tab shell tests** - `e25c54de` (test)
4. **Task 2 GREEN: Diagnostics tab UI** - `7c885093` (feat)
5. **Task 3: Human visual verification of Diagnostics tab** - approved by user; no code commit required.

**Plan metadata:** pending final docs commit.

## Files Created/Modified

- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Adds Diagnostics workspace routing and exposes the injected `DiagnosticsViewModel` to the shell.
- `src/BS2BG.App/Views/MainWindow.axaml` - Adds the compiled-bound Diagnostics tab with report, detail, preview, export, and ledger UI surfaces.
- `src/BS2BG.App/Views/MainWindow.axaml.cs` - Keeps shell code-behind compatible with the expanded tab set.
- `tests/BS2BG.Tests/AppShellTests.cs` - Adds headless shell assertions for the Diagnostics tab and automation names.
- `tests/BS2BG.Tests/MainWindowViewModelTests.cs` - Adds workspace/ViewModel routing coverage for Diagnostics without breaking existing workspace behavior.

## Decisions Made

- Treat Diagnostics as a top-level workspace tab to satisfy Phase 3's user-visible validation goal and the UI-SPEC requirement that diagnostics not be modal-only or text-only.
- Reuse the tested ViewModel seams from plans 03-04 through 03-07 for report, preview, export, and ledger data instead of duplicating state in the view.
- Keep preview and committing actions visually distinct through exact labels such as `Preview NPC Import`, `Import Previewed NPCs`, and `Preview Export`.
- Record the human verification checkpoint as approved after the user confirmed the visual/interaction check.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used supported dotnet test invocation**
- **Found during:** Final plan verification
- **Issue:** Prior Phase 3 execution established that the planned `dotnet test ... -x` command is unsupported by this .NET/MSBuild environment.
- **Fix:** Ran the same focused test filter without `-x` and ran `dotnet build BS2BG.sln`.
- **Files modified:** None
- **Verification:** Focused tests passed (115 tests), and solution build succeeded.
- **Committed in:** N/A (command adjustment only)

---

**Total deviations:** 1 auto-fixed (1 blocking command adjustment)
**Impact on plan:** Verification coverage was preserved; implementation scope remained unchanged.

## Issues Encountered

- Existing analyzer warnings from prior Phase 3 diagnostics files/tests and new nullable warnings in `AppShellTests.cs` appeared during focused tests/build; they did not fail the suite and were not blocking.

## Human Verification

- **Checkpoint:** Task 3 — Human visual verification of Diagnostics tab
- **Response:** approved
- **Outcome:** Diagnostics tab layout/readability and interaction affordances accepted; no follow-up UI fixes requested.

## Known Stubs

None. Stub scan matches were existing test helper parameter names or existing error text (`NPC row identity was not available for undo snapshot`), not placeholder UI/data stubs. The `No diagnostics yet` text is intentional UI-SPEC empty-state copy.

## Threat Flags

None. The plan threat model already covered compiled-binding/report visibility, severity text labels/automation names, and distinct preview-vs-commit controls; no new network, auth, file-access, or schema trust boundary was introduced.

## TDD Gate Compliance

- RED commits present: `a86dc36f`, `e25c54de`
- GREEN commits present after RED: `5ff987b9`, `7c885093`
- Refactor commit: not needed

## Validation Performed

- Verified previous task commits exist in git history: `a86dc36f`, `5ff987b9`, `e25c54de`, and `7c885093`.
- `dotnet test --filter "FullyQualifiedName~AppShellTests|FullyQualifiedName~DiagnosticsViewModelTests|FullyQualifiedName~MainWindowViewModelTests|FullyQualifiedName~MorphsViewModelTests"` — passed (115 tests).
- `dotnet build BS2BG.sln` — succeeded.
- Acceptance checks confirmed `Header="Diagnostics"`, `Run Diagnostics`, `Copy Report`, `Preview NPC Import`, `Preview Export`, `File operation incomplete`, `x:DataType="vm:DiagnosticFindingViewModel"`, `AppWorkspace.Diagnostics`, and `public DiagnosticsViewModel Diagnostics`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 3's Diagnostics UI is now visible in the shell and ready for end-of-phase verification. Phase 4 can build profile extensibility diagnostics on a first-class report workspace rather than introducing a separate validation surface.

## Self-Check: PASSED

- Verified key modified files exist: `MainWindowViewModel.cs`, `MainWindow.axaml`, `MainWindow.axaml.cs`, `AppShellTests.cs`, and `MainWindowViewModelTests.cs`.
- Verified task commits exist in git history: `a86dc36f`, `5ff987b9`, `e25c54de`, and `7c885093`.
- Verified summary file created at `.planning/phases/03-validation-and-diagnostics/03-08-SUMMARY.md`.

---
*Phase: 03-validation-and-diagnostics*
*Completed: 2026-04-27*
