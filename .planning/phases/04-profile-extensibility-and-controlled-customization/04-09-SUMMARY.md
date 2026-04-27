---
phase: 04-profile-extensibility-and-controlled-customization
plan: 09
subsystem: ui-profile-management
tags: [avalonia, reactiveui, profile-manager, custom-profiles, tdd]

requires:
  - phase: 04-08
    provides: First-class Profiles workspace UI and profile manager command surfaces
provides:
  - Selection-safe Profiles workspace source rows bound to ProfileManagerViewModel.SelectedProfile
  - Copy-as-custom flow that captures the selected bundled row before clearing selection
  - Game metadata preservation for profile editor construction and selected custom/embedded JSON export
affects: [profile-extensibility, profile-management-ui, selected-profile-export]

tech-stack:
  added: []
  patterns:
    - Avalonia ListBox source groups with one-way-to-source SelectedProfile row targeting
    - Source-definition metadata enrichment for profile manager rows

key-files:
  created:
    - .planning/phases/04-profile-extensibility-and-controlled-customization/04-09-SUMMARY.md
  modified:
    - src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs
    - src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs
    - src/BS2BG.App/Views/MainWindow.axaml
    - tests/BS2BG.Tests/ProfileManagerViewModelTests.cs
    - tests/BS2BG.Tests/MainWindowHeadlessTests.cs

key-decisions:
  - "Profiles workspace source groups use selectable ListBox controls with one-way-to-source SelectedProfile binding to avoid cross-list selection clearing while still giving every row an explicit selection target."
  - "Profile manager rows enrich catalog entries from local/project source definitions so JSON export preserves Game metadata without changing Core slider math or bundled profile files."

patterns-established:
  - "Row-scoped profile actions read the selected row into a local before clearing or replacing selection/editor state."

requirements-completed: [EXT-01, EXT-02, EXT-05]

duration: 35min
completed: 2026-04-27
---

# Phase 04 Plan 09: Profile Row Selection and Metadata Gap Closure Summary

**Selectable Profiles workspace rows with selection-safe copy-as-custom and Game-preserving selected profile JSON export.**

## Performance

- **Duration:** 35 min
- **Started:** 2026-04-27T10:58:41Z
- **Completed:** 2026-04-27T11:35:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Converted the Bundled, Custom, Embedded Project, and Missing profile source groups from non-selectable `ItemsControl` displays into selectable `ListBox` row targets bound to `ProfileManagerViewModel.SelectedProfile`.
- Added regression coverage proving selectable profile row surfaces exist and selecting a non-initial profile changes the manager action target/editor.
- Fixed copy-as-custom to capture the selected bundled row before clearing selection, so the new custom editor is seeded from the selected source profile's slider tables.
- Preserved `Game` metadata from local custom and project-embedded source definitions through profile rows, editor construction, `ToCustomProfileDefinition`, and selected JSON export.

## Task Commits

Each task was committed atomically with TDD gates:

1. **Task 1 RED: Add failing profile row selection coverage** - `5390cdef` (test)
2. **Task 1 GREEN: Make profile rows selectable** - `311e56db` (feat)
3. **Task 2 RED: Add failing copy and metadata preservation tests** - `f33a361a` (test)
4. **Task 2 GREEN: Preserve profile copy and export metadata** - `c3e4ec3c` (feat)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` - Captures selected bundled rows before copy, enriches source rows with Game metadata, and exports Game-preserving profile definitions.
- `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs` - Builds editors from profile entries using the entry's Game metadata instead of dropping it.
- `src/BS2BG.App/Views/MainWindow.axaml` - Uses selectable `ListBox` source groups with automation names and `SelectedProfile` row targeting.
- `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs` - Covers non-initial row targeting, copy-as-custom source table preservation, and selected JSON export Game metadata.
- `tests/BS2BG.Tests/MainWindowHeadlessTests.cs` - Covers selectable profile source group controls in the Profiles workspace.

## Decisions Made

- Profiles source groups use `SelectedItem="{Binding SelectedProfile, Mode=OneWayToSource}"`; this makes row clicks update the action target without separate group lists fighting over visual selected state when the selected row belongs to another group.
- Local custom and embedded project Game metadata is resolved from `ITemplateProfileCatalogService.LocalCustomProfiles` and `ProjectProfiles` by internal profile name; filenames remain source metadata only.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Running focused tests and the full solution build in parallel caused a transient `BS2BG.App.dll` file lock from the compiler process. The full build completed successfully, and the focused tests were rerun afterward and passed.

## Known Stubs

None. PlaceholderText values found in `MainWindow.axaml` are active UI input hints, not unwired placeholder implementations.

## Authentication Gates

None.

## Threat Flags

None. The plan only adjusted existing local UI selection and selected-profile JSON export paths covered by the threat model; no new network, auth, file-access class, or schema trust boundary was introduced.

## Verification

- RED Task 1: `dotnet test --filter "FullyQualifiedName~MainWindowHeadlessTests"` failed as expected because `BundledProfilesList` did not exist.
- GREEN Task 1: `dotnet test --filter "FullyQualifiedName~ProfileManagerViewModelTests|FullyQualifiedName~MainWindowHeadlessTests"` passed (11 tests at that point).
- RED Task 2: `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` failed as expected for empty copied slider rows and dropped Game metadata.
- GREEN Task 2: `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` passed (10 tests at that point).
- Final focused regression: `dotnet test --filter "FullyQualifiedName~ProfileManagerViewModelTests|FullyQualifiedName~MainWindowHeadlessTests"` passed (13 tests).
- Final build: `dotnet build BS2BG.sln` passed with existing analyzer warnings and 0 errors.
- Acceptance checks confirmed `SelectedItem="{Binding SelectedProfile`, non-initial selection assertions, no `RelayCommand`/`AsyncRelayCommand` references in `ProfileManagerViewModel.cs`, selected-row capture before clearing selection, no `FromEntry(... string.Empty ...)` Game drop, and Game-preserving export assertions.

## TDD Gate Compliance

- RED gate commits: `5390cdef`, `f33a361a`
- GREEN gate commits: `311e56db`, `c3e4ec3c`
- Refactor gate: not needed; focused tests and build passed without a separate cleanup commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 04-10 can build on deterministic selected profile row targeting and metadata-preserving editor/export state. Remaining Phase 4 gap work can focus on editor add/remove controls and GUI project-save/conflict validation without revisiting row selection or selected JSON export metadata.

## Self-Check: PASSED

- Summary file exists at `.planning/phases/04-profile-extensibility-and-controlled-customization/04-09-SUMMARY.md`.
- Modified key files exist: `ProfileManagerViewModel.cs`, `ProfileEditorViewModel.cs`, `MainWindow.axaml`, `ProfileManagerViewModelTests.cs`, and `MainWindowHeadlessTests.cs`.
- Commits verified in git history: `5390cdef`, `311e56db`, `f33a361a`, and `c3e4ec3c`.
- Final focused tests and full solution build passed as documented.

---
*Phase: 04-profile-extensibility-and-controlled-customization*
*Completed: 2026-04-27*
