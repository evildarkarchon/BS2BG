---
phase: 04-profile-extensibility-and-controlled-customization
reviewed: 2026-04-27T12:51:05Z
depth: standard
files_reviewed: 47
files_reviewed_list:
  - openspec/specs/profile-extensibility/spec.md
  - openspec/specs/project-roundtrip/spec.md
  - openspec/specs/template-generation-flow/spec.md
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
  - tests/BS2BG.Tests/AppShellTests.cs
  - tests/BS2BG.Tests/DiagnosticsViewModelTests.cs
  - tests/BS2BG.Tests/M6UxViewModelTests.cs
  - tests/BS2BG.Tests/MainWindowHeadlessTests.cs
  - tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs
  - tests/BS2BG.Tests/MainWindowViewModelTests.cs
  - tests/BS2BG.Tests/MorphsViewModelTests.cs
  - tests/BS2BG.Tests/ProfileDefinitionServiceTests.cs
  - tests/BS2BG.Tests/ProfileDiagnosticsServiceTests.cs
  - tests/BS2BG.Tests/ProfileEditorViewModelTests.cs
  - tests/BS2BG.Tests/ProfileManagerViewModelTests.cs
  - tests/BS2BG.Tests/ProfileRecoveryDiagnosticsServiceTests.cs
  - tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs
  - tests/BS2BG.Tests/ProjectValidationServiceTests.cs
  - tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs
  - tests/BS2BG.Tests/TemplatesViewModelTests.cs
  - tests/BS2BG.Tests/UserProfileStoreTests.cs
findings:
  critical: 3
  warning: 2
  info: 0
  total: 5
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-04-27T12:51:05Z
**Depth:** standard
**Files Reviewed:** 47
**Status:** issues_found

## Summary

Reviewed profile extensibility specs, App/Core implementation, UI bindings, and tests. The implementation still has functional gaps that can break required recovery workflows, abort otherwise loadable projects, and silently overwrite imported profile data.

## Critical Issues

### CR-01: Advertised recovery remap action is not implemented

**File:** `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:272-284`

**Issue:** `ProfileRecoveryDiagnosticsService` advertises `RemapToInstalledProfile`, and Diagnostics creates an action row for it, but `ExecuteRecoveryActionAsync` falls through to `false`. Clicking **Remap to Installed Profile** does not remap presets or create the required undoable recovery operation.

**Fix:** Implement the remap action through a real installed-profile selection flow and call `TemplatesViewModel.RemapProfileReferences`, or stop advertising the action until that flow exists.

```csharp
ProfileRecoveryActionKind.RemapToInstalledProfile =>
    RemapMissingProfileToInstalledProfileAsync(missingProfileName, cancellationToken),
```

### CR-02: Malformed `CustomProfiles` section can abort whole project load

**File:** `src/BS2BG.Core/Serialization/ProjectFileService.cs:73`

**Issue:** `ProjectFileDto.CustomProfiles` is a `List<JsonElement>?`. If the optional `CustomProfiles` section has the wrong shape, for example an object or string instead of an array, `JsonSerializer.Deserialize<ProjectFileDto>` throws before legacy `SliderPresets`, `CustomMorphTargets`, or `MorphedNPCs` can be hydrated. The spec requires malformed embedded profile data to produce load diagnostics without blocking legacy project data.

**Fix:** Deserialize the root with `JsonDocument` or make `CustomProfiles` a `JsonElement?`, then validate that it is an array inside `LoadEmbeddedProfiles` and add a `ProjectLoadDiagnostic` instead of throwing.

```csharp
if (customProfilesElement.ValueKind != JsonValueKind.Array)
{
    diagnostics.Add(new ProjectLoadDiagnostic(
        "EmbeddedProfileSectionInvalid",
        "CustomProfiles must be an array; embedded profiles were ignored.",
        null));
    return profiles;
}
```

### CR-03: Multi-file profile import can overwrite an earlier imported duplicate

**File:** `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:292-330`

**Issue:** Each selected import file is validated against `catalogService.Current.ProfileNames`, but the catalog is refreshed only after the loop. Two selected JSON files with the same internal `Name` both pass validation in the same batch. `UserProfileStore.SaveProfile` may then choose the same sanitized target path and overwrite the first imported profile with the second, violating the duplicate-name rejection requirement and risking profile data loss.

**Fix:** Maintain a case-insensitive accepted-name set for the whole import batch and add each successfully saved profile name before validating the next file.

```csharp
var acceptedNames = catalogService.Current.ProfileNames.ToHashSet(StringComparer.OrdinalIgnoreCase);
...
var validation = ProfileDefinitionService.ValidateProfileJson(
    json,
    ProfileValidationContext.ForImport(acceptedNames, ProfileSourceKind.LocalCustom, file));
...
if (saved.Succeeded)
{
    acceptedNames.Add(validation.Profile.Name);
    importedAny = true;
}
```

## Warnings

### WR-01: Custom profile delete command is not exposed in the Profiles UI

**File:** `src/BS2BG.App/Views/MainWindow.axaml:1556-1585`

**Issue:** `ProfileManagerViewModel` implements `DeleteCustomProfileCommand` with referenced-profile confirmation, but the Profiles workspace only exposes Copy, Export, Validate, and Save buttons. Users cannot invoke the delete workflow from the first-class profile manager UI, leaving local custom-profile cleanup inaccessible.

**Fix:** Add a delete button bound to `DeleteCustomProfileCommand`, visible/enabled only when the command can execute.

```xml
<Button Content="Delete Custom Profile"
        automation:AutomationProperties.Name="Delete Custom Profile"
        Command="{Binding DeleteCustomProfileCommand}" />
```

### WR-02: Blank slider validation reports the wrong error

**File:** `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs:439-442`

**Issue:** A blank slider name emits `BlankSliderName` but the message says the table contains a duplicate slider. This misleads users fixing malformed profile rows and weakens validation diagnostics.

**Fix:** Use a blank-name-specific message.

```csharp
diagnostics.Add(Blocker(
    "BlankSliderName",
    $"{table} contains a blank slider name. Each slider name can appear once per table.",
    table,
    slider));
```

---

_Reviewed: 2026-04-27T12:51:05Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
