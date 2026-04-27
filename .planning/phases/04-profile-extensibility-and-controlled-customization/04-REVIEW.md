---
phase: 04-profile-extensibility-and-controlled-customization
reviewed: 2026-04-27T11:14:28Z
depth: standard
files_reviewed: 40
files_reviewed_list:
  - AGENTS.md
  - .planning/REQUIREMENTS.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-01-PLAN.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-02-PLAN.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-03-PLAN.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-04-PLAN.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-05-PLAN.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-06-PLAN.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-07-PLAN.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-08-PLAN.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-09-PLAN.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-10-PLAN.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-11-PLAN.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-01-SUMMARY.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-02-SUMMARY.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-03-SUMMARY.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-04-SUMMARY.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-05-SUMMARY.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-06-SUMMARY.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-07-SUMMARY.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-08-SUMMARY.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-09-SUMMARY.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-10-SUMMARY.md
  - .planning/phases/04-profile-extensibility-and-controlled-customization/04-11-SUMMARY.md
  - src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs
  - src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs
  - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
  - src/BS2BG.App/Views/MainWindow.axaml
  - src/BS2BG.App/Views/MainWindow.axaml.cs
  - src/BS2BG.App/Services/TemplateProfileCatalogService.cs
  - src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs
  - src/BS2BG.App/Services/UserProfileStore.cs
  - src/BS2BG.Core/Serialization/ProjectFileService.cs
  - src/BS2BG.Core/Models/ProjectModel.cs
  - src/BS2BG.Core/Models/CustomProfileDefinition.cs
  - tests/BS2BG.Tests/ProfileManagerViewModelTests.cs
  - tests/BS2BG.Tests/ProfileEditorViewModelTests.cs
  - tests/BS2BG.Tests/MainWindowViewModelTests.cs
  - tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs
  - tests/BS2BG.Tests/MainWindowHeadlessTests.cs
findings:
  critical: 3
  warning: 2
  info: 0
  total: 5
status: issues_found
---

# Phase 04: Code Review Report

**Reviewed:** 2026-04-27T11:14:28Z
**Depth:** standard
**Files Reviewed:** 40 files (source/test files plus Phase 04 plans/summaries)
**Status:** issues_found

## Summary

Reviewed Phase 04 source changes after gap-closure plans 04-09, 04-10, and 04-11, with emphasis on profile-management correctness, GUI save/share behavior, Avalonia bindings, and ReactiveUI conventions. The prior 04-09/04-10/04-11 gaps are partially closed, but the Profiles workspace still has shipping blockers around newly-created/copied profiles and unsaved-editor state loss.

## Critical Issues

### CR-01: Created and copied custom profiles cannot be saved from the Profiles UI

**Classification:** BLOCKER
**File:** `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:81,309-329`; `src/BS2BG.App/Views/MainWindow.axaml:1581-1585`
**Issue:** The only visible `Save Profile` button is bound to `ProfileManagerViewModel.SaveProfileCommand`, but that command can execute only when `SelectedProfile.SourceKind == LocalCustom`. Both `CreateBlankProfile()` and `CopyBundledProfile()` intentionally clear `SelectedProfile` before creating the editable local-custom candidate, so the user can author a blank/copy editor buffer but cannot save it through the UI. This breaks EXT-01/EXT-02 for the primary create/copy custom-profile workflows.
**Fix:** Gate manager save on the current editor candidate, not only the selected catalog row. For example, expose an editor `CanSaveCandidate` observable or combine `Editor.IsValid` with an explicit local-candidate state, then save with `SelectedProfile?.FilePath` only when editing an existing local custom profile.
```csharp
var canSaveEditor = this.WhenAnyValue(x => x.Editor.IsValid, x => x.Editor.HasUnsavedChanges,
    (isValid, hasChanges) => isValid && hasChanges);
SaveProfileCommand = ReactiveCommand.CreateFromTask(SaveSelectedProfileAsync, canSaveEditor);
```
Also add an app/headless test that clicks `Create Blank Profile` or `Copy as Custom Profile`, edits a valid name, and verifies `Save Profile` can execute and refreshes the catalog.

### CR-02: Declining the unsaved-edits prompt leaves `SelectedProfile` on the newly clicked row

**Classification:** BLOCKER
**File:** `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:97-102,138-153`
**Issue:** UI row selection writes directly to `SelectedProfile` through the ListBox binding before `TrySelectProfileAsync` runs. If the current editor has unsaved changes and the user declines the discard prompt, the method only raises `PropertyChanged` and returns false; it never restores the previous `SelectedProfile`. The editor remains for the old profile, but commands such as delete/export/save are now gated by and operate on the newly clicked row, creating a wrong-target operation/data-loss risk.
**Fix:** Track the committed selection separately and roll back the property when discard is declined, or route row selection through a command that asks before mutating `SelectedProfile`.
```csharp
if (Editor.HasUnsavedChanges && !await dialogService.ConfirmDiscardUnsavedEditsAsync(cancellationToken))
{
    selectingInternally = true;
    try { SelectedProfile = committedSelectedProfile; }
    finally { selectingInternally = false; }
    return false;
}
committedSelectedProfile = entry;
```
Add a test that simulates the actual property-set path (`vm.SelectedProfile = otherRow`) with a declined prompt and verifies both `SelectedProfile` and `Editor` still target the original row.

### CR-03: Catalog/search refresh silently replaces an unsaved profile editor

**Classification:** BLOCKER
**File:** `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:93-96,370-415`
**Issue:** `CatalogChanged` and every `SearchText` change call `RefreshProfileEntries()`, which clears/rebuilds rows, assigns `SelectedProfile`, and recreates `Editor` without checking `Editor.HasUnsavedChanges` or prompting. A user editing an unsaved custom profile can lose all unsaved row/metadata edits simply by typing in the Profiles search box or by any catalog refresh from import/save/delete/recovery actions.
**Fix:** Separate row projection refresh from editor replacement. Preserve the current editor when it has unsaved changes unless the user explicitly confirms discard; for search filtering, update visible collections without recreating `Editor`.
```csharp
var currentEditor = Editor;
var hasUnsaved = currentEditor.HasUnsavedChanges;
// rebuild row collections
if (!hasUnsaved)
{
    SelectedProfile = restoredSelection;
    Editor = restoredSelection is null ? EmptyEditor() : EditorFrom(restoredSelection);
}
```
Add regression tests for changing `SearchText` and receiving `CatalogChanged` while `Editor.HasUnsavedChanges` is true.

## Warnings

### WR-01: Profile editor search only filters Defaults rows

**Classification:** WARNING
**File:** `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs:463-468`; `src/BS2BG.App/Views/MainWindow.axaml:1672,1697`
**Issue:** `SearchText` populates only `VisibleDefaultRows`; Multipliers and Inverted rows are bound directly to `MultiplierRows` and `InvertedRows`. The UI label says `Filter profile sliders`, and Phase 04 requires large slider tables to be searchable, but two of the three editable tables ignore the filter.
**Fix:** Add `VisibleMultiplierRows` and `VisibleInvertedRows`, refresh them alongside defaults, and bind the AXAML tables to those visible collections. Add tests that filtering hides nonmatching multiplier and inverted rows without removing them from the saved candidate.

### WR-02: Profile import/export file I/O failures escape without user-facing recovery status

**Classification:** WARNING
**File:** `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:210,292,366`
**Issue:** Profile import reads selected files and export writes selected JSON paths directly without handling normal local I/O failures. Unlike project/export save paths, these commands do not catch `IOException`/`UnauthorizedAccessException` or set an actionable status message. A denied/missing file or unwritable export path can surface as a command exception and leave the Profiles workspace without clear failure copy.
**Fix:** Wrap user-selected file reads/writes in recoverable exception handling, preserve the current editor/selection, and set status text such as `Profile JSON could not be read` or `Profile JSON could not be exported` with the path/error details. Consider routing writes through the existing file-operation failure pattern if overwrite/partial-write reporting is needed.

---

_Reviewed: 2026-04-27T11:14:28Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
