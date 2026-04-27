---
phase: 04-profile-extensibility-and-controlled-customization
plan: 06
subsystem: project-open-profile-conflict-transaction
tags: [csharp, avalonia, profile-conflicts, project-open, transactional-overlays, tdd]

requires:
  - phase: 04-profile-extensibility-and-controlled-customization
    provides: AppData custom profile catalog/store, embedded project profile serialization, and recovery diagnostics from plans 04-02 through 04-04
provides:
  - Explicit App dialog contract for embedded/local custom-profile conflicts
  - Transactional project-open conflict handling with local snapshots, deferred store writes, cancellation, rollback, and project-scoped overlays
  - Focused ViewModel tests for missing profiles, all conflict choices, write failure rollback, bundled-name collision, and multi-conflict ordering
affects: [profile-extensibility, project-sharing, profile-recovery, runtime-catalog-overlays]

tech-stack:
  added: []
  patterns:
    - Pre-mutation project-open transaction over loaded ProjectLoadResult and local profile snapshots
    - Project-scoped embedded overlay precedence over same-name local custom profiles only after explicit user decision
    - Deferred local profile writes for Replace Local Profile with in-memory project/overlay rollback on failure

key-files:
  created:
    - tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs
  modified:
    - src/BS2BG.App/Services/IAppDialogService.cs
    - src/BS2BG.App/Services/WindowAppDialogService.cs
    - src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs
    - src/BS2BG.App/Services/TemplateProfileCatalogService.cs
    - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
    - src/BS2BG.App/ViewModels/MorphsViewModel.cs
    - tests/BS2BG.Tests/MainWindowViewModelTests.cs
    - tests/BS2BG.Tests/M6UxViewModelTests.cs
    - tests/BS2BG.Tests/MorphsViewModelTests.cs
    - tests/BS2BG.Tests/ProfileManagerViewModelTests.cs

key-decisions:
  - "Project-open conflict prompts collect all decisions before any local profile save, project replacement, or new project overlay mutation."
  - "Use Project Copy creates an active project-scoped overlay for the opened project; Keep Local and Replace Local remove the conflicting embedded overlay entry from active project profiles."
  - "Rename Project Copy marks the opened project dirty after MarkClean without adding an undo entry because project open clears prior undo history."

patterns-established:
  - "IAppDialogService is the ViewModel decision boundary for profile conflicts; tests fake decisions without Avalonia UI coupling."
  - "ITemplateProfileCatalogService exposes local/project profile snapshots and a save boundary so project-open orchestration does not depend on concrete AppData storage."

requirements-completed: [EXT-03, EXT-04, EXT-05]

duration: 6 min
completed: 2026-04-27
---

# Phase 04 Plan 06: Project-Open Profile Conflict Transaction Summary

**Transactional project-open conflict handling with explicit embedded/local profile decisions, deferred local writes, and project-scoped overlay isolation.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-27T08:59:31Z
- **Completed:** 2026-04-27T09:05:50Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- Added `ProfileConflictResolution`, `ProfileConflictRequest`, `ProfileConflictDecision`, and `PromptProfileConflictAsync` to the app dialog boundary, plus an Avalonia conflict prompt with the required non-silent copy and all four choices.
- Reworked project open to load with diagnostics into a detached project, snapshot local/project catalog state, prompt and validate all conflicts before mutation, defer `Replace Local Profile` writes, and restore previous overlay/current project on cancel or save failure.
- Implemented active-project overlay semantics: `Use Project Copy` can override a same-name local custom profile for the opened project only, while bundled-name collisions stay rejected by load diagnostics and cannot overlay bundled profiles.
- Added focused TDD coverage for missing custom profile fallback status, cancel, use-project-copy, replace-local, write-failure rollback, rename uniqueness/reference updates/dirty state, keep-local, bundled-name collisions, and multi-conflict decision ordering.

## Task Commits

Each task was committed atomically with TDD gates:

1. **Task 1 RED: Add profile conflict dialog contract tests** - `3f3ff6f8` (test)
2. **Task 1 GREEN: Add profile conflict dialog contract** - `f972d870` (feat)
3. **Task 2 RED: Add project-open conflict transaction tests** - `9928edd2` (test)
4. **Task 2 GREEN: Implement project-open conflict transaction** - `6036610a` (feat)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs` - New focused tests for fake dialog decisions and the project-open conflict transaction matrix.
- `src/BS2BG.App/Services/IAppDialogService.cs` - Adds profile conflict resolution contracts and the prompt method to the app dialog boundary.
- `src/BS2BG.App/Services/WindowAppDialogService.cs` - Implements the profile conflict prompt with required copy, destructive replace wording, rename input, and cancellation returning null.
- `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs` - Carries local profile snapshots in factory results and exposes the user-store save boundary.
- `src/BS2BG.App/Services/TemplateProfileCatalogService.cs` - Publishes local/project profile snapshots and local save delegation while preserving refresh/overlay serialization.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` - Orchestrates project-open profile conflict transactions, missing-profile status, overlay reset preservation, and rename dirty-state handling.
- `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `tests/BS2BG.Tests/MainWindowViewModelTests.cs`, `tests/BS2BG.Tests/M6UxViewModelTests.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs` - Updated existing `IAppDialogService` fakes/null services for the new prompt method.
- `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs` - Updated test catalog stub for the expanded catalog service contract.

## Decisions Made

- Project-open conflicts are resolved using an immutable local profile snapshot captured before dialogs, preventing later catalog state from changing conflict interpretation mid-open.
- Local profile replacement is staged and executed only after all prompts and rename validation succeed; failures leave the old in-memory project and old overlay active.
- Rename-on-open is treated as a loaded-project modification: open first marks clean, then marks dirty without adding undo history.

## Deviations from Plan

None - plan executed as written.

## Issues Encountered

- Running focused tests and solution build in parallel caused a transient `BS2BG.App.pdb` file lock. Re-running `dotnet test --filter FullyQualifiedName~MainWindowViewModelProfileRecoveryTests` after the build completed passed cleanly.

## Known Stubs

None. Empty collection initializers in tests and null dialog implementations are intentional test/design-time defaults, not user-facing placeholder data.

## Threat Flags

None - the embedded project profile to local profile store boundary, project overlay trust boundary, bundled-name shadowing prevention, and rename dirty-state repudiation concern were all covered by the plan threat model.

## Verification

- `dotnet test --filter FullyQualifiedName~MainWindowViewModelProfileRecoveryTests` — passed (10 tests).
- `dotnet build BS2BG.sln` — passed after the concurrent focused-test lock cleared (warnings only; existing analyzer warnings remain out of scope).
- Acceptance checks confirmed `PromptProfileConflictAsync`, required conflict dialog copy, `ClearProjectProfiles`, conflict tests for all four options plus cancel, missing profile open, overlay precedence, replace-local rollback, bundled collision, and multi-conflict ordering.

## TDD Gate Compliance

- RED gate commits: `3f3ff6f8`, `9928edd2`
- GREEN gate commits: `f972d870`, `6036610a`
- Refactor gate: not needed; focused tests and solution build passed without a separate cleanup commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Project-open embedded/local profile conflicts are now explicit and transactional, so the upcoming Profiles workspace UI can display and navigate profile states without inheriting silent trust-state mutations. Phase 04 plan 07 can consume the profile manager, dialog boundary, and overlay-clearing behavior already in place.

## Self-Check: PASSED

- Created/modified files verified: `04-06-SUMMARY.md`, `MainWindowViewModelProfileRecoveryTests.cs`, `IAppDialogService.cs`, `WindowAppDialogService.cs`, `TemplateProfileCatalogService.cs`, and `MainWindowViewModel.cs` exist.
- Commits verified: `3f3ff6f8`, `f972d870`, `9928edd2`, and `6036610a` exist in git history.
- Verification commands passed as documented.

---
*Phase: 04-profile-extensibility-and-controlled-customization*
*Completed: 2026-04-27*
