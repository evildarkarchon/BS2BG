---
phase: 01-profile-correctness-and-trust
reviewed: 2026-04-26T13:35:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - settings_FO4_CBBE.json
  - src/BS2BG.App/BS2BG.App.csproj
  - src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs
  - src/BS2BG.Core/Generation/TemplateProfileCatalog.cs
  - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
  - src/BS2BG.App/Themes/ThemeResources.axaml
  - src/BS2BG.App/Views/MainWindow.axaml
  - docs/release/README.md
  - tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs
  - tests/BS2BG.Tests/TemplateGenerationServiceTests.cs
  - tests/BS2BG.Tests/TemplateProfileCatalogTests.cs
  - tests/BS2BG.Tests/ProjectFileServiceTests.cs
  - tests/BS2BG.Tests/TemplatesViewModelTests.cs
  - tests/BS2BG.Tests/AppShellTests.cs
findings:
  critical: 0
  warning: 1
  info: 0
  total: 1
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-04-26T13:35:00Z  
**Depth:** standard  
**Files Reviewed:** 14  
**Status:** issues_found

## Summary

Reviewed the Phase 01 plan/summary context and the source, test, and release-documentation files changed for profile correctness and trust. The bundled FO4 profile wiring, catalog detection helper, neutral fallback copy, AXAML binding, and focused tests are generally coherent, but the unresolved-profile UX has a behavioral trap: when the fallback calculation profile is already selected, the user cannot explicitly adopt that same bundled profile through the profile selector.

## Warnings

### WR-01: Fallback-selected default profile cannot be explicitly adopted from the selector

**File:** `src/BS2BG.App/ViewModels/TemplatesViewModel.cs:621-633`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs:577-587`

**Issue:** When a preset is loaded with an unbundled saved profile such as `Community CBBE`, `SetSelectedProfileNameFromPreset` immediately sets `SelectedProfileName` to `profileCatalog.GetProfile(profileName).Name`, usually `Skyrim CBBE`. The only code path that overwrites `SelectedPreset.ProfileName` with a bundled profile is `OnSelectedProfileNameChangedReactive`, which only runs when `SelectedProfileName` changes. If the user wants to accept the displayed fallback profile (`Skyrim CBBE`), selecting the already-selected ComboBox item does not change the bound value, so `SelectedPreset.ProfileName` remains `Community CBBE` and the fallback panel stays visible. This violates the intended “until you choose a bundled profile” behavior for the default fallback profile.

**Fix:** Add an explicit adoption path for the displayed fallback profile, or avoid preselecting the fallback item as the actual selector value. For example, keep calculation fallback separate from the selector value and only write the preset profile when the user selects an actual bundled value:

```csharp
private string EffectiveProfileName => profileCatalog.GetProfile(SelectedPreset?.ProfileName ?? SelectedProfileName).Name;

private void SetSelectedProfileNameFromPreset(string? profileName)
{
    syncingProfileFromPreset = true;
    try
    {
        SelectedProfileName = profileCatalog.ContainsProfile(profileName)
            ? profileCatalog.GetProfile(profileName).Name
            : string.Empty; // no bundled selection has been explicitly chosen yet
    }
    finally
    {
        syncingProfileFromPreset = false;
    }

    RefreshProfileFallbackInformation();
}
```

Then use `EffectiveProfileName` for preview/generation fallback, or add a visible fallback-panel action such as “Use Skyrim CBBE” that sets `SelectedPreset.ProfileName = profileCatalog.GetProfile(SelectedPreset.ProfileName).Name` and refreshes fallback state.

---

_Reviewed: 2026-04-26T13:35:00Z_  
_Reviewer: the agent (gsd-code-reviewer)_  
_Depth: standard_
