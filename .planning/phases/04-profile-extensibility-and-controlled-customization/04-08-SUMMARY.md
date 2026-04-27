---
phase: 04-profile-extensibility-and-controlled-customization
plan: 08
subsystem: ui
tags: [avalonia, reactiveui, compiled-bindings, profiles, accessibility]

requires:
  - phase: 04-05
    provides: Profile manager/editor workflows and commands
  - phase: 04-07
    provides: Diagnostics recovery actions and selected profile JSON export contracts
provides:
  - First-class Profiles workspace tab in the Avalonia shell
  - Templates-to-Profiles navigation through an explicit navigation service
  - Compiled-bound Profile Manager UI with source groups, neutral missing-profile copy, and accessible controls
affects: [profile-management-ui, shell-navigation, template-generation-flow, morph-assignment-flow]

tech-stack:
  added: []
  patterns:
    - Shell-owned INavigationService for cross-workspace navigation without ViewModel cycles
    - Avalonia compiled-binding DataTemplates with typed ProfileManager/ProfileEditor panes
    - Text-visible profile source and recovery states instead of color-only status

key-files:
  created:
    - src/BS2BG.App/Services/NavigationService.cs
  modified:
    - src/BS2BG.App/AppBootstrapper.cs
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs
    - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
    - src/BS2BG.App/Views/MainWindow.axaml
    - src/BS2BG.App/Views/MainWindow.axaml.cs
    - tests/BS2BG.Tests/AppShellTests.cs
    - tests/BS2BG.Tests/MainWindowHeadlessTests.cs

key-decisions:
  - "Profiles workspace navigation is shell-owned through INavigationService so Templates can request navigation without depending on MainWindowViewModel."
  - "Profiles UI uses explicit text badges and neutral recovery copy so source/editability/missing states are not color-only."

patterns-established:
  - "Workspace additions update AppWorkspace, MainWindow tab mapping, command palette, and global-search routing together."
  - "Profile management controls expose AutomationProperties.Name and typed compiled bindings for headless verification."

requirements-completed: [EXT-01, EXT-02, EXT-04, EXT-05]

duration: 30min
completed: 2026-04-27
---

# Phase 04 Plan 08: Profiles Workspace UI Summary

**First-class Avalonia Profiles workspace with shell navigation, compiled-bound manager UI, accessible controls, and human-verified Phase 4 copy.**

## Performance

- **Duration:** 30 min, including human verification checkpoint wait
- **Started:** 2026-04-27T09:18:16Z
- **Completed:** 2026-04-27T09:48:40Z
- **Tasks:** 3/3
- **Files modified:** 9

## Accomplishments

- Added a shell-owned Profiles workspace beside Templates, Morphs, and Diagnostics, including command palette entry, tab mapping, global search routing, aggregate busy state, and Templates `Manage Profiles` navigation.
- Added a compiled-bound Profiles tab UI with Profile Manager heading, source rails for Bundled/Custom/Embedded/Missing/Rejected groups, typed editor pane, exact Phase 4 copy, and automation names on new actionable controls.
- Recorded human approval of the visual checkpoint after focused headless tests passed; the user confirmed the Profiles workspace UI as approved.

## Task Commits

Each implementation task was committed atomically:

1. **Task 1: Add Profiles workspace shell bindings** - `706fc45d` (feat)
2. **Task 2: Add compiled-bound Profiles tab UI** - `a63043b4` (feat)
3. **Task 3: Human visual verification of Profiles workspace** - user approval recorded in this summary and final docs commit

## Files Created/Modified

- `src/BS2BG.App/Services/NavigationService.cs` - Adds `INavigationService` and shell navigation callback used by cross-workspace commands.
- `src/BS2BG.App/AppBootstrapper.cs` - Wires the navigation service through App ViewModel construction.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Exposes `Profiles`, adds Profiles workspace state, command palette entry, global search behavior, and busy aggregation.
- `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs` - Supports UI-visible source/recovery groups used by the Profiles workspace.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` - Adds `ManageProfilesCommand` using `INavigationService`.
- `src/BS2BG.App/Views/MainWindow.axaml` - Adds the compiled-bound Profiles tab, source badges, profile action controls, editor pane, and Templates `Manage Profiles` button.
- `src/BS2BG.App/Views/MainWindow.axaml.cs` - Maps the fourth tab to `AppWorkspace.Profiles`.
- `tests/BS2BG.Tests/AppShellTests.cs` - Covers Profiles navigation and global search shell contracts.
- `tests/BS2BG.Tests/MainWindowHeadlessTests.cs` - Covers Profiles tab, key controls, and source/recovery group presence.

## Verification

- `dotnet test --filter "FullyQualifiedName~MainWindowHeadlessTests|FullyQualifiedName~AppShellTests"` — passed, 32 tests.
- `dotnet build BS2BG.sln` — passed with 0 warnings and 0 errors.
- Human visual checkpoint — approved by user response: `approved`.

## Decisions Made

- Profiles workspace navigation is shell-owned through `INavigationService`; Templates requests navigation without owning or referencing `MainWindowViewModel`.
- Profiles UI represents source/editability/missing states with explicit text labels and neutral missing-profile copy, preserving Phase 4's no color-only state requirement.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Known Stubs

None found in the files created or modified for this plan. Existing unrelated placeholder-like text in other application areas was not introduced by this plan.

## Authentication Gates

None.

## Threat Flags

None. The plan added UI and ViewModel navigation surface only; profile import/save/export actions continue routing through existing validated ViewModel commands and service boundaries.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 4 profile extensibility UI is complete and ready for phase verification/transition. Phase 5 can build on the visible Profiles workspace, explicit recovery actions, and validated profile-management command surface.

## Self-Check: PASSED

- Found summary file: `.planning/phases/04-profile-extensibility-and-controlled-customization/04-08-SUMMARY.md`
- Found key created/modified files: `NavigationService.cs`, `MainWindow.axaml`, `AppShellTests.cs`, `MainWindowHeadlessTests.cs`
- Found task commits: `706fc45d`, `a63043b4`

---
*Phase: 04-profile-extensibility-and-controlled-customization*
*Completed: 2026-04-27*
