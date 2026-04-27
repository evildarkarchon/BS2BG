---
phase: 04-profile-extensibility-and-controlled-customization
reviewed: 2026-04-27T09:51:16Z
depth: standard
files_reviewed: 27
files_reviewed_list:
  - src/BS2BG.App/AppBootstrapper.cs
  - src/BS2BG.App/Services/IAppDialogService.cs
  - src/BS2BG.App/Services/NavigationService.cs
  - src/BS2BG.App/Services/ProfileManagementDialogService.cs
  - src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs
  - src/BS2BG.App/Services/TemplateProfileCatalogService.cs
  - src/BS2BG.App/Services/UserProfileStore.cs
  - src/BS2BG.App/Services/WindowAppDialogService.cs
  - src/BS2BG.App/ViewModels/DiagnosticFindingViewModel.cs
  - src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs
  - src/BS2BG.App/ViewModels/MainWindowViewModel.cs
  - src/BS2BG.App/ViewModels/MorphsViewModel.cs
  - src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs
  - src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs
  - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
  - src/BS2BG.App/Views/MainWindow.axaml
  - src/BS2BG.App/Views/MainWindow.axaml.cs
  - src/BS2BG.Core/Diagnostics/DiagnosticFinding.cs
  - src/BS2BG.Core/Diagnostics/ProfileDiagnosticsService.cs
  - src/BS2BG.Core/Diagnostics/ProfileRecoveryDiagnosticsService.cs
  - src/BS2BG.Core/Diagnostics/ProjectValidationService.cs
  - src/BS2BG.Core/Generation/ProfileDefinitionService.cs
  - src/BS2BG.Core/Generation/TemplateProfileCatalog.cs
  - src/BS2BG.Core/IsExternalInit.cs
  - src/BS2BG.Core/Models/CustomProfileDefinition.cs
  - src/BS2BG.Core/Models/ProjectModel.cs
  - src/BS2BG.Core/Serialization/ProjectFileService.cs
findings:
  critical: 6
  warning: 2
  info: 0
  total: 8
status: issues_found
---

# Phase 04: Code Review Report

**Reviewed:** 2026-04-27T09:51:16Z
**Depth:** standard
**Files Reviewed:** 27
**Status:** issues_found

## Summary

Reviewed Phase 04 profile extensibility source changes with emphasis on behavioral regressions, Avalonia bindings, ReactiveUI conventions, and project serialization compatibility. The implementation contains multiple shipping blockers around project sharing, conflict validation, profile metadata preservation, and the Profiles workspace UI.

## Critical Issues

### CR-01: GUI project saves never embed referenced local custom profiles

**Classification:** BLOCKER
**File:** `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:894`
**Issue:** The application save path calls `projectFileService.SaveToString(project)` without a `ProjectSaveContext`. The new serializer can embed referenced local custom profiles only when the context overload is used, so projects saved from the GUI can reference local custom profile names while omitting their definitions. This breaks EXT-03/EXT-05 sharing and silently loses the data needed on another machine.
**Fix:** Build a case-insensitive context from the runtime catalog/local custom profile snapshot and call the overload.
```csharp
var availableProfiles = profileCatalogService.LocalCustomProfiles
    .Concat(project.CustomProfiles)
    .Where(profile => profile.SourceKind != ProfileSourceKind.Bundled)
    .GroupBy(profile => profile.Name, StringComparer.OrdinalIgnoreCase)
    .ToDictionary(group => group.Key, group => group.First(), StringComparer.OrdinalIgnoreCase);
var snapshot = projectFileService.SaveToString(project, new ProjectSaveContext(availableProfiles));
```
Add an app-level save test proving a project preset referencing a local custom profile serializes `CustomProfiles`.

### CR-02: Rename conflict validation can approve names that collide with local profiles

**Classification:** BLOCKER
**File:** `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:676-692`
**Issue:** `ValidateRenameDecisions` stores bundled, local, and embedded names in a single case-insensitive set, then removes `conflict.Embedded.Name` before checking the requested rename. For an embedded/local conflict with the same display name, that removal also removes the local profile occupancy, allowing `Rename Project Copy` to keep or choose a name already owned by a local custom profile. This violates the explicit uniqueness requirement and can leave ambiguous catalog/project state.
**Fix:** Track occupied names by source/count, or validate against local names separately while excluding only the specific embedded profile being renamed.
```csharp
var localNames = localProfiles.Select(p => p.Name).ToHashSet(StringComparer.OrdinalIgnoreCase);
...
if (localNames.Contains(renamed))
    return $"Profile name '{renamed}' conflicts with an existing local custom profile.";
```
Also add tests for renaming to the original conflicted local name and to another local custom profile name.

### CR-03: Copy-as-custom discards the bundled profile being copied

**Classification:** BLOCKER
**File:** `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:319-328`
**Issue:** `CopyBundledProfile` sets `SelectedProfile = null` before reading the selected profile name and slider data. `SelectedProfileNameForCopy()` and `SelectedProfileSliderProfileForCopy()` then return empty/default values, so copying a bundled profile opens an empty editor rather than a copy of the bundled profile.
**Fix:** Capture the selected entry before clearing selection.
```csharp
var source = SelectedProfile;
if (source is null) return;
SelectedProfile = null;
Editor = ProfileEditorViewModel.FromProfile(
    source.Name,
    string.Empty,
    source.SliderProfile,
    ProfileSourceKind.LocalCustom,
    null,
    profileDefinitionService,
    catalogService.Current.ProfileNames);
Editor.Name = string.Empty;
```

### CR-04: Profiles workspace rows are not selectable in the UI

**Classification:** BLOCKER
**File:** `src/BS2BG.App/Views/MainWindow.axaml:1432-1514`
**Issue:** Profile groups are rendered with `ItemsControl`, not a selectable control, and no row command/binding updates `ProfileManagerViewModel.SelectedProfile`. Users cannot select custom, embedded, or missing rows from the Profiles tab, so edit/export/delete/copy commands operate only on whatever row the ViewModel auto-selected during refresh.
**Fix:** Use a `ListBox`/`TreeView` with `SelectedItem` bound to `SelectedProfile`, or add explicit row buttons/commands that call a selection command. Cover selecting a custom row in a headless UI test.

### CR-05: Profile manager drops `Game` metadata on edit/export

**Classification:** BLOCKER
**File:** `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs:103-104`; `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:499-500`
**Issue:** `ProfileManagerEntryViewModel` does not carry `Game`, `FromEntry` always passes `string.Empty`, and `ToCustomProfileDefinition` exports/saves entries with an empty game. Selecting or exporting an existing local/embedded Fallout4/custom profile can erase metadata even though EXT-02 requires supported metadata editing/preservation.
**Fix:** Add `Game` to catalog entries or resolve the full `CustomProfileDefinition` from `LocalCustomProfiles`/`ProjectProfiles` when building editor/export candidates. Preserve the existing `Game` value in `FromEntry` and `ToCustomProfileDefinition`.

### CR-06: Profile table authoring UI cannot add or remove slider rows

**Classification:** BLOCKER
**File:** `src/BS2BG.App/Views/MainWindow.axaml:1622-1652`
**Issue:** The Profiles editor UI displays existing Defaults, Multipliers, and Inverted rows, but exposes no controls to add or remove rows. A created blank profile can only edit Name/Game and cannot actually author slider tables through the UI, so EXT-02's supported profile table editing workflow is incomplete.
**Fix:** Add accessible add/remove controls for each editable table, bind them to ReactiveCommands on `ProfileEditorViewModel`, and test creating a blank profile with at least one default, multiplier, and inverted slider via the ViewModel/UI path.

## Warnings

### WR-01: Row value edits do not trigger validation or save canExecute updates

**Classification:** WARNING
**File:** `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs:66-70,163-167,331-372`
**Issue:** The editor validates on collection changes and Name/Game changes, but does not subscribe to row property changes. Editing `ValueSmall`, `ValueBig`, multiplier values, slider names, or inversion flags leaves `IsValid`, `ValidationRows`, and `SaveProfileCommand` gating stale until the user manually validates or collection membership changes.
**Fix:** Subscribe to each row's `Changed`/property-changed stream when rows are added and unsubscribe when removed, then call `ValidateProfile()` and `RefreshVisibleRows()` as appropriate.

### WR-02: Export Profile JSON reports success even when the write fails

**Classification:** WARNING
**File:** `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:362-366`
**Issue:** `ExportSelectedProfileAsync` writes directly with `File.WriteAllTextAsync` and has no local error handling or status update for unauthorized paths, missing directories, or I/O failures. Failures bubble through the command pipeline without the clear failure copy expected for risky export operations.
**Fix:** Route the write through an app/core file service with recoverable diagnostics or catch normal I/O exceptions and set a failure status message while keeping the selected profile/editor state intact.

---

_Reviewed: 2026-04-27T09:51:16Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
