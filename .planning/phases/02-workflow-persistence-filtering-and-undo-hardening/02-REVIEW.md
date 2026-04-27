---
phase: 02-workflow-persistence-filtering-and-undo-hardening
reviewed: 2026-04-27T02:08:58Z
depth: standard
files_reviewed: 29
files_reviewed_list:
  - Directory.Packages.props
  - src/BS2BG.App/AppBootstrapper.cs
  - src/BS2BG.App/BS2BG.App.csproj
  - src/BS2BG.App/Services/IAppDialogService.cs
  - src/BS2BG.App/Services/NpcBulkScopeDisplayConverter.cs
  - src/BS2BG.App/Services/UndoRedoService.cs
  - src/BS2BG.App/Services/UserPreferencesService.cs
  - src/BS2BG.App/Services/WindowAppDialogService.cs
  - src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs
  - src/BS2BG.App/Services/WindowFileDialogService.cs
  - src/BS2BG.App/Services/WindowNpcTextFilePicker.cs
  - src/BS2BG.App/Themes/ThemeResources.axaml
  - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
  - src/BS2BG.App/ViewModels/MorphsViewModel.cs
  - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
  - src/BS2BG.App/ViewModels/Workflow/NpcBulkScopeResolver.cs
  - src/BS2BG.App/ViewModels/Workflow/NpcFilterState.cs
  - src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs
  - src/BS2BG.App/ViewModels/Workflow/UndoSnapshots.cs
  - src/BS2BG.App/Views/MainWindow.axaml
  - src/BS2BG.App/Views/MainWindow.axaml.cs
  - tests/BS2BG.Tests/M6UxAppShellTests.cs
  - tests/BS2BG.Tests/M6UxViewModelTests.cs
  - tests/BS2BG.Tests/M7ReleasePolishTests.cs
  - tests/BS2BG.Tests/MainWindowViewModelTests.cs
  - tests/BS2BG.Tests/MorphsViewModelTests.cs
  - tests/BS2BG.Tests/NpcFilterStateTests.cs
  - tests/BS2BG.Tests/TemplatesViewModelTests.cs
  - tests/BS2BG.Tests/UserPreferencesServiceTests.cs
findings:
  critical: 3
  warning: 1
  info: 0
  total: 4
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-04-27T02:08:58Z  
**Depth:** standard  
**Files Reviewed:** 29  
**Status:** issues_found

## Summary

Reviewed the Phase 02 workflow-persistence, NPC filtering/scope, and undo-hardening implementation against the phase PLAN/SUMMARY artifacts and UI contract. The implementation contains several shipping blockers: one preference save path can erase folder channels, free-text filtering does not switch routine bulk operations away from `All`, and scoped row clearing can be disabled for valid non-visible scopes. One UI labeling issue also contradicts the hidden-row safety contract.

## Critical Issues

### CR-01: BLOCKER — Omit preference saves can wipe remembered folder channels

**File:** `src/BS2BG.App/ViewModels/TemplatesViewModel.cs:491-502`  
**Issue:** `SaveOmitRedundantSlidersPreference` rebuilds `UserPreferences` from the `currentPreferences` object loaded when the ViewModel was constructed. If a file picker later saves `ProjectFolder`, `BodySlideXmlFolder`, `NpcTextFolder`, `BodyGenExportFolder`, or `BosJsonExportFolder`, toggling `OmitRedundantSliders` writes the stale in-memory values back and erases those newer channels. This violates the Phase 02 requirement that workflow preferences preserve each other and remain reliable across restarts.

**Fix:** Load the latest preferences before preserving unrelated fields, matching the theme save path.

```csharp
private void SaveOmitRedundantSlidersPreference()
{
    var latestPreferences = preferencesService.Load();
    currentPreferences = new UserPreferences
    {
        Theme = latestPreferences.Theme,
        OmitRedundantSliders = OmitRedundantSliders,
        ProjectFolder = latestPreferences.ProjectFolder,
        BodySlideXmlFolder = latestPreferences.BodySlideXmlFolder,
        NpcTextFolder = latestPreferences.NpcTextFolder,
        BodyGenExportFolder = latestPreferences.BodyGenExportFolder,
        BosJsonExportFolder = latestPreferences.BosJsonExportFolder
    };

    if (!preferencesService.Save(currentPreferences))
        StatusMessage = "This workflow preference could not be saved. BS2BG will continue using defaults for this session.";
}
```

### CR-02: BLOCKER — Free-text filters leave routine bulk operations defaulting to All

**File:** `src/BS2BG.App/ViewModels/MorphsViewModel.cs:315-319` and `src/BS2BG.App/ViewModels/MorphsViewModel.cs:900-914`  
**Issue:** Checklist filters switch `SelectedNpcBulkScope` from `All` to `Visible`, but debounced free-text search does not. A user can type into the NPC search box, see only matching rows, and still have routine bulk actions default to `All`, which can mutate hidden rows contrary to D-10 and the UI spec's hidden-row protection requirement.

**Fix:** After applying pending global search text, if any filter is active and the selected scope is `All`, switch to `Visible` before scoped commands run.

```csharp
private void ApplyPendingNpcSearchText()
{
    npcFilterState.ApplyPendingGlobalSearchText();
    if (npcFilterState.HasAnyFilter() && SelectedNpcBulkScope == NpcBulkScope.All)
        SelectedNpcBulkScope = NpcBulkScope.Visible;

    RefreshVisibleNpcs();
}
```

### CR-03: BLOCKER — Scoped NPC clearing is disabled when the selected scope has targets but visible rows are empty

**File:** `src/BS2BG.App/ViewModels/MorphsViewModel.cs:186` and `src/BS2BG.App/ViewModels/MorphsViewModel.cs:246-248`  
**Issue:** `ClearVisibleNpcsCommand` now clears the selected scope, but its `canExecute` still only checks `VisibleNpcs`. If filters hide all rows while the user chooses `All` or has hidden selected rows and chooses `Selected`, the command remains disabled even though `ClearNpcsForScope` would have valid targets. This breaks the explicit `All`/`Selected` scope semantics added in Plan 07.

**Fix:** Gate the command from the resolved selected scope rather than visible count alone.

```csharp
var canClearNpcsForSelectedScope = Gate(npcScopeChanged.CombineLatest(
    visibleNpcsChanged,
    selectedNpcsChanged,
    npcsChanged,
    (scope, _, _, _) => ResolveNpcTargets(scope).Length > 0));

ClearVisibleNpcsCommand = ReactiveCommand.CreateFromTask(
    ClearNpcsForSelectedScopeAsync,
    canClearNpcsForSelectedScope);
```

## Warnings

### WR-01: WARNING — Scoped clear button still advertises visible-only behavior

**File:** `src/BS2BG.App/Views/MainWindow.axaml:720-725`  
**Issue:** The `Clear` button is bound to a selected-scope command that can clear `All`, `Selected`, or `Visible Empty`, but its automation name is still `Clear visible NPCs`. The UI contract says undo/redo UI must not imply hidden filtered rows were untouched unless the selected scope actually excluded them; this label is misleading for assistive technology and automated UI consumers when the scope is `All` or `Selected`.

**Fix:** Use scope-neutral copy or bind the label/automation name to scope-aware text, e.g. `Clear scoped NPCs`, while preserving the visible scope selector next to the command.

---

_Reviewed: 2026-04-27T02:08:58Z_  
_Reviewer: the agent (gsd-code-reviewer)_  
_Depth: standard_
