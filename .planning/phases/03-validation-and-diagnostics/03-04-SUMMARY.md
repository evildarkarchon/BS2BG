---
phase: 03-validation-and-diagnostics
plan: 04
subsystem: diagnostics-ui
tags: [avalonia, reactiveui, diagnostics, clipboard, tdd, viewmodel]

requires:
  - phase: 03-validation-and-diagnostics
    provides: [Core project validation service, Core profile diagnostics service]
provides:
  - ReactiveUI Diagnostics ViewModel for read-only project/profile report refresh
  - Diagnostic finding row presentation with exact Blocker/Caution/Info labels and navigation intent
  - Grouped plain-text diagnostics report formatter and clipboard copy command
affects: [diagnostics-ui, app-shell, validation-and-diagnostics]

tech-stack:
  added: []
  patterns:
    - ReactiveCommand-based diagnostics refresh and clipboard copy
    - App-layer report formatting over Core diagnostic findings

key-files:
  created:
    - src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs
    - src/BS2BG.App/ViewModels/DiagnosticFindingViewModel.cs
    - src/BS2BG.App/Services/DiagnosticsReportFormatter.cs
    - tests/BS2BG.Tests/DiagnosticsViewModelTests.cs
  modified:
    - src/BS2BG.App/AppBootstrapper.cs

key-decisions:
  - "Keep diagnostics refresh and copy read-only in the App layer by consuming Core diagnostic services without mutating ProjectModel state."
  - "Format copied diagnostics as grouped plain text from display ViewModels, including profile fallback details, while excluding auto-fix actions."

patterns-established:
  - "DiagnosticsViewModel exposes ReactiveCommand refresh/copy surfaces, severity counts, area grouping, selected-detail text, and navigation intent for later AXAML wiring."
  - "DiagnosticsReportFormatter groups clipboard text under stable workflow headings such as ## Project and ## Profiles."

requirements-completed: [DIAG-01, DIAG-02]

duration: 5 min
completed: 2026-04-27
---

# Phase 03 Plan 04: Diagnostics ViewModel and Report Copy Summary

**ReactiveUI Diagnostics presentation state with read-only refresh, exact severity labels, profile fallback drilldown, and grouped clipboard reporting.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-27T04:02:11Z
- **Completed:** 2026-04-27T04:07:03Z
- **Tasks:** 2 completed
- **Files modified:** 5

## Accomplishments

- Added `DiagnosticsViewModel` with `RefreshDiagnosticsCommand`, severity counts, ordered workflow areas, profile drilldown rows, selected finding details, and navigation-intent properties.
- Added `DiagnosticFindingViewModel` to map Core severities to exact UI labels: `Blocker`, `Caution`, and `Info`.
- Added `DiagnosticsReportFormatter` and `CopyReportCommand` for grouped plain-text clipboard reports, including empty-state and neutral profile fallback details.
- Registered `ProjectValidationService`, `ProfileDiagnosticsService`, `DiagnosticsReportFormatter`, and `DiagnosticsViewModel` in `AppBootstrapper`.

## Task Commits

Each task was committed atomically with TDD gates:

1. **Task 1 RED: Diagnostics ViewModel refresh tests** - `0bbe3f77` (test)
2. **Task 1 GREEN: Diagnostics ViewModel refresh state** - `22270af5` (feat)
3. **Task 2 RED: Diagnostics report copy tests** - `6bb594b3` (test)
4. **Task 2 GREEN: Diagnostics report copy formatting** - `0dabfcac` (feat)
5. **Task 2 REFACTOR: Formatter cleanup** - `2afe183e` (refactor)
6. **Task 2 REFACTOR: Test cleanup** - `a3f31a82` (refactor)

**Plan metadata:** pending final docs commit.

## Files Created/Modified

- `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs` - ReactiveUI diagnostics refresh/copy state over read-only Core services.
- `src/BS2BG.App/ViewModels/DiagnosticFindingViewModel.cs` - Binding-ready finding row with severity label and navigation target metadata.
- `src/BS2BG.App/Services/DiagnosticsReportFormatter.cs` - Grouped plain-text report formatter for clipboard output.
- `src/BS2BG.App/AppBootstrapper.cs` - Registers diagnostics Core/App services and ViewModel.
- `tests/BS2BG.Tests/DiagnosticsViewModelTests.cs` - Focused ViewModel tests for read-only refresh, labels, DI registration, and report copy.

## Decisions Made

- Keep Diagnostics ViewModel refresh read-only by calling `ProjectValidationService.Validate` and `ProfileDiagnosticsService.Analyze` only, with tests asserting `ProjectModel.IsDirty` and `ChangeVersion` remain unchanged.
- Surface profile fallback copy as an informational Diagnostics finding using UI-SPEC wording so the report includes `Saved profile: ...; calculation fallback: ...` without adding ambient Templates warnings or auto-fix actions.
- Keep `DiagnosticsReportFormatter` as an injectable App service seam rather than a static helper so future UI/report variants can be tested through DI.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used supported dotnet test invocation**
- **Found during:** Task verification
- **Issue:** Phase 3 prior plans established that the planned `dotnet test ... -x` command is not accepted by this .NET/MSBuild environment.
- **Fix:** Ran the same focused test filter without `-x` and ran `dotnet build BS2BG.sln` for ReactiveUI/source-generator coverage.
- **Files modified:** None
- **Verification:** `dotnet test --filter "FullyQualifiedName~DiagnosticsViewModelTests"` passed; `dotnet build BS2BG.sln` passed.
- **Committed in:** N/A (command adjustment only)

---

**Total deviations:** 1 auto-fixed (1 blocking command adjustment)
**Impact on plan:** Verification coverage was preserved; implementation scope remained unchanged.

## Issues Encountered

- Existing analyzer warnings from previously added Core diagnostics tests/services appeared during focused `dotnet test`; they do not fail the build and were not caused by this plan's App-layer changes.

## Known Stubs

None. The `No diagnostics yet` text is intentional UI empty-state copy from the Phase 3 UI spec, not a placeholder stub.

## Threat Flags

None. The plan threat model already covered Core diagnostics-to-App presentation and clipboard report output; no new network, auth, file-write, or trust-boundary surface was added.

## TDD Gate Compliance

- RED commits present: `0bbe3f77`, `6bb594b3`
- GREEN commits present after RED: `22270af5`, `0dabfcac`
- REFACTOR commits present after GREEN: `2afe183e`, `a3f31a82`

## Validation Performed

- `dotnet test --filter "FullyQualifiedName~DiagnosticsViewModelTests"` — passed (5 tests).
- `dotnet build BS2BG.sln` — passed.
- Acceptance checks confirmed `RefreshDiagnosticsCommand`, `SeverityLabel`, `DiagnosticsViewModel` DI registration, `CopyReportCommand`, formatter headings `## Project` / `## Profiles`, `Blocker`, profile fallback report text, and copied status text.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 03-05 can proceed with App-layer NPC import preview wiring. Diagnostics refresh/copy state is available for the later Diagnostics AXAML tab without requiring additional Core changes.

## Self-Check: PASSED

- Verified key files exist: `DiagnosticsViewModel.cs`, `DiagnosticFindingViewModel.cs`, `DiagnosticsReportFormatter.cs`, `DiagnosticsViewModelTests.cs`, and `AppBootstrapper.cs`.
- Verified task commits exist: `0bbe3f77`, `22270af5`, `6bb594b3`, `0dabfcac`, `2afe183e`, and `a3f31a82`.

---
*Phase: 03-validation-and-diagnostics*
*Completed: 2026-04-27*
