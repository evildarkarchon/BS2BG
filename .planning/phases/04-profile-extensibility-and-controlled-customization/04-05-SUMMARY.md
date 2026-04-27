---
phase: 04-profile-extensibility-and-controlled-customization
plan: 05
subsystem: app-profile-management-workflows
tags: [csharp, reactiveui, avalonia, profile-management, tdd]

requires:
  - phase: 04-profile-extensibility-and-controlled-customization
    provides: Core custom profile validation/export and runtime local catalog/store refreshes from plans 04-01 and 04-02
provides:
  - ReactiveUI profile manager ViewModel with source-aware profile row commands
  - Profile editor ViewModel for metadata/defaults/multipliers/inverted rows with validation-gated save
  - Profile management dialog/file-picker service boundary and DI wiring
affects: [profile-extensibility, profile-management-ui, local-custom-profiles, runtime-catalog-refresh]

tech-stack:
  added: []
  patterns:
    - ReactiveCommand-based App profile manager/editor orchestration
    - In-memory profile candidate validation from editor row state before store writes
    - Window-bound profile dialog service exposing path/confirmation results only

key-files:
  created:
    - src/BS2BG.App/Services/ProfileManagementDialogService.cs
    - src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs
    - src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs
    - tests/BS2BG.Tests/ProfileManagerViewModelTests.cs
    - tests/BS2BG.Tests/ProfileEditorViewModelTests.cs
  modified:
    - src/BS2BG.App/AppBootstrapper.cs
    - src/BS2BG.App/Views/MainWindow.axaml.cs

key-decisions:
  - "Profile manager owns one editor instance per single-shell workspace session and prompts before discarding unsaved editor buffers."
  - "Profile editor validation is performed from in-memory row state, not JSON serialization round-trips, while import/export keep using Core JSON validation/export."
  - "Deleting a referenced local custom profile preserves project preset profile names so recovery diagnostics remain visible instead of silently remapping."

patterns-established:
  - "Source labels distinguish bundled read-only, local custom editable, embedded project, and missing fallback rows."
  - "Local profile saves/deletes refresh ITemplateProfileCatalogService so existing profile-aware ViewModels can observe runtime catalog changes."

requirements-completed: [EXT-01, EXT-02]

duration: 6 min
completed: 2026-04-27
---

# Phase 04 Plan 05: Profile Manager and Editor App Workflows Summary

**ReactiveUI profile manager/editor workflows now provide validated local custom profile authoring over the Core profile validator and user profile store.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-27T08:50:16Z
- **Completed:** 2026-04-27T08:56:00Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments

- Added `IProfileManagementDialogService`/`ProfileManagementDialogService` for profile JSON import/export pickers and destructive/discard confirmations.
- Added `ProfileManagerViewModel` with ReactiveUI commands for import, create blank, copy bundled, validate, save, export, and delete workflows.
- Added `ProfileEditorViewModel` with editable defaults, multipliers, inverted rows, search-filtered visible rows, validation/status rows, and validation-gated `SaveProfileCommand`.
- Covered bundled read-only command gating, unsaved selection discard confirmation, referenced custom profile deletion, missing fallback labels, save gating, live validation transitions, row search, and save failure behavior with focused tests.
- Registered profile manager/dialog services in App DI and attached the window-bound profile dialog service from `MainWindow`.

## Task Commits

Each task was committed atomically with TDD gates:

1. **Task 1 RED: Add profile manager workflow tests** - `b3125ee8` (test)
2. **Task 1 GREEN: Add profile manager workflows** - `21d38db7` (feat)
3. **Task 2 RED: Add profile editor validation tests** - `5bbdc0cc` (test)
4. **Task 2 GREEN: Add profile editor validation workflow** - `98e2c4ed` (feat)
5. **Task 3: Wire profile manager services into app DI** - `17efe054` (feat)
6. **Post-task fix: Label unresolved profile manager rows** - `3c3b2c6d` (fix)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `src/BS2BG.App/Services/ProfileManagementDialogService.cs` - Adds the profile-management dialog service interface, Avalonia storage-provider JSON import/export pickers, and profile delete/discard confirmations.
- `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` - Adds source-aware catalog row projection, manager commands, single-editor ownership, unsaved discard handling, delete/reference checks, and catalog refresh orchestration.
- `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs` - Adds metadata/table row editing, in-memory candidate validation, row search projection, validation/status rows, and validation-gated save behavior.
- `src/BS2BG.App/AppBootstrapper.cs` - Registers `ProfileManagementDialogService`, `IProfileManagementDialogService`, and singleton `ProfileManagerViewModel`.
- `src/BS2BG.App/Views/MainWindow.axaml.cs` - Attaches the profile management dialog service to the shell window when DI provides it.
- `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs` - Covers manager source labels/commands, unsaved selection confirmation, referenced deletion, and missing fallback rows.
- `tests/BS2BG.Tests/ProfileEditorViewModelTests.cs` - Covers editor save gating, live validation transitions, row search, and save failure preservation.

## Decisions Made

- The App manager keeps a single editor instance for the single-shell Profiles workspace; selection changes that would discard unsaved edits route through `ConfirmDiscardUnsavedEditsAsync`.
- The editor builds and validates an in-memory `CustomProfileDefinition` candidate directly from row state, avoiding JSON serialization on normal edit cadence.
- Referenced custom profile deletion deliberately leaves preset `ProfileName` values untouched so Phase 4 recovery diagnostics can reappear.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] Attached the profile management dialog service to the shell window**
- **Found during:** Task 3 (Wire profile manager into dependency injection)
- **Issue:** Registering a window-bound `ProfileManagementDialogService` without attaching the `MainWindow` would make picker and confirmation methods return default/no-owner behavior at runtime.
- **Fix:** Added an optional `ProfileManagementDialogService` constructor parameter to `MainWindow` and called `Attach(this)` alongside the existing window services.
- **Files modified:** `src/BS2BG.App/Views/MainWindow.axaml.cs`
- **Verification:** `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` and `dotnet build BS2BG.sln` passed.
- **Committed in:** `17efe054`

**2. [Rule 1 - Bug] Corrected unresolved profile row source labels**
- **Found during:** Final acceptance check after Task 3
- **Issue:** Missing project profile rows reused `ProfileSourceKind.EmbeddedProject`, causing the source label to display `Embedded in project` instead of the required `Missing — using fallback` copy.
- **Fix:** Added an explicit missing-row flag to `ProfileManagerEntryViewModel` and covered the neutral fallback source label in tests.
- **Files modified:** `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs`, `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs`
- **Verification:** `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` passed.
- **Committed in:** `3c3b2c6d`

---

**Total deviations:** 2 auto-fixed (1 missing critical functionality, 1 bug)
**Impact on plan:** Both fixes tighten planned runtime correctness and UI copy without changing the Core profile model or expanding scope beyond App profile management.

## Issues Encountered

- Running `dotnet test` and `dotnet build BS2BG.sln` concurrently once caused a transient PDB file lock in `BS2BG.App`. Re-running the focused tests after the build completed passed cleanly.

## Known Stubs

None. Empty collection initializers in the new ViewModels are intentional mutable UI collections populated by catalog state, editor rows, validation, and test/user actions.

## Threat Flags

None - the user-selected JSON boundary, App-to-profile-store write boundary, and bundled read-only trust domain were covered by the plan threat model.

## Verification

- `dotnet test --filter FullyQualifiedName~ProfileManagerViewModelTests` — passed (4 tests after the missing-label fix).
- `dotnet test --filter FullyQualifiedName~ProfileEditorViewModelTests` — passed (4 tests).
- `dotnet test --filter "FullyQualifiedName~ProfileManagerViewModelTests|FullyQualifiedName~ProfileEditorViewModelTests"` — passed (8 tests).
- `dotnet build BS2BG.sln` — passed (warnings only; existing analyzer warnings remain out of scope).
- Acceptance source checks confirmed `ReactiveCommand`, `IsBusy`, required dialog interface methods, `SaveProfileCommand`, `ValidateProfileCommand`, blank-profile copy, `AddSingleton<ProfileManagerViewModel>`, `IProfileManagementDialogService` registration, and no `RelayCommand` occurrence in `ProfileManagerViewModel.cs`.

## TDD Gate Compliance

- RED gate commits: `b3125ee8`, `5bbdc0cc`
- GREEN gate commits: `21d38db7`, `98e2c4ed`
- Refactor/fix gate: `3c3b2c6d` corrected a final acceptance-label bug after the task commits.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Profile manager/editor services are available for the forthcoming Profiles workspace UI and conflict/recovery workflows. Later UI plans can bind to the manager/editor ViewModels and use the existing service boundary without adding direct file I/O to ViewModels.

## Self-Check: PASSED

- Created files verified: `ProfileManagementDialogService.cs`, `ProfileManagerViewModel.cs`, `ProfileEditorViewModel.cs`, `ProfileManagerViewModelTests.cs`, `ProfileEditorViewModelTests.cs`, and `04-05-SUMMARY.md` exist.
- Commits verified: `b3125ee8`, `21d38db7`, `5bbdc0cc`, `98e2c4ed`, `17efe054`, and `3c3b2c6d` exist in git history.
- Verification commands passed as documented.

---
*Phase: 04-profile-extensibility-and-controlled-customization*
*Completed: 2026-04-27*
