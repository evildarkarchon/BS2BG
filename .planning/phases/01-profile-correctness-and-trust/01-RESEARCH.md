# Phase 01: Profile Correctness and Trust - Research

**Researched:** 2026-04-26  
**Domain:** C#/.NET profile catalog correctness, legacy JSON round-trip compatibility, Avalonia ReactiveUI MVVM trust messaging  
**Confidence:** HIGH for implementation architecture and tests; MEDIUM for FO4 profile calibration data

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### FO4 Profile Shape
- **D-01:** Add a separate bundled root-level Fallout 4 CBBE JSON profile file alongside the existing `settings.json` and `settings_UUNP.json`; do not move profiles into a `profiles/` folder in Phase 1.
- **D-02:** Seed the FO4 profile from known FO4 CBBE slider names documented in `PRD.md`, with `valueSmall`/`valueBig` defaults at `1.0`, multipliers at `1.0`, and no inverted sliders.
- **D-03:** Keep the existing Skyrim profile files exactly where they are during Phase 1. Broader profile-folder migration/custom profile layout belongs to Phase 4.
- **D-04:** Make Fallout 4 CBBE always available in the profile selector; do not hide it behind an experimental toggle.

#### Warning And Confidence Behavior
- **D-05:** Do not add general warnings for unprofiled or custom body mods. BodyGen files can encompass many body mods that BS2BG does not profile, so slider-name mismatch warnings would create false pressure and noise.
- **D-06:** Do not label Fallout 4 CBBE as experimental in the main workflow, selector, or warning UI. The user explicitly chose not to surface FO4 calibration confidence in-app for Phase 1.
- **D-07:** If a saved project references a profile name that is not currently bundled, preserve the original profile name for round-trip compatibility and use neutral informational fallback text only when generation would otherwise silently use bundled fallback math.
- **D-08:** Treat the roadmap wording around warnings/experimental FO4 status as constrained by these decisions. Downstream agents should not implement modal warnings, warning banners, mismatch heuristics, or FO4 experimental labels unless a later spec explicitly reverses this context.

#### Profile Inference
- **D-09:** Imported BodySlide XML presets use the profile currently selected by the user. Do not infer profile from file path, game folder, or slider-name overlap.
- **D-10:** Do not implement likely-mismatch detection from slider names in Phase 1. Custom and unprofiled body mods may intentionally use unknown or overlapping sliders.
- **D-11:** Legacy projects without a `Profile` field continue to map through `isUUNP`: `true` means Skyrim UUNP, `false` means Skyrim CBBE, with no prompt on open.
- **D-12:** If a project contains an unbundled profile name, preserve the name for save/load and visibly use the fallback calculation profile until the user changes it. The fallback text should be neutral, not a warning.

#### Trust Evidence
- **D-13:** The minimum Phase 1 proof for FO4 is distinct-table test coverage: tests must prove FO4 loads from its own JSON and never shares Skyrim CBBE or Skyrim UUNP defaults, multipliers, or inverted-slider tables.
- **D-14:** Do not alter or regenerate existing Java-reference golden expected files under `tests/fixtures/expected/**` for Phase 1. Add focused C# tests/fixtures instead of rebasing sacred golden outputs.
- **D-15:** ViewModel/UI coverage should include profile selection updates, imports using the selected profile, unresolved saved profile preservation, and visible neutral fallback information.
- **D-16:** Add tests asserting custom/unprofiled body-mod slider names do not produce mismatch warnings or block generation. Only unresolved profile fallback should produce neutral info.

### the agent's Discretion
- Exact FO4 profile file name, as long as it is a distinct root-level bundled JSON file and clearly maps to `ProjectProfileMapping.Fallout4Cbbe`.
- Exact neutral fallback message wording and placement, as long as it is not framed as a warning and does not block generation/export.
- Exact test class names and fixture helper structure, following existing xUnit v3 and FluentAssertions patterns.

### Deferred Ideas (OUT OF SCOPE)
- `profiles/` folder migration and custom profile management — Phase 4: Profile Extensibility and Controlled Customization.
- Authoritative Fallout 4 calibration assistant or known-good community calibration workflow — v2 advanced modding requirement unless explicitly pulled forward.
- Any heuristic profile detection from file paths or slider names — deferred unless a later phase explicitly revisits it with false-positive handling.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PROF-01 | User can select a distinct Fallout 4 CBBE profile that does not reuse Skyrim CBBE or Skyrim UUNP defaults, multipliers, or inverted-slider behavior. | Add a root-level FO4 profile JSON and register it in `TemplateProfileCatalogFactory`; test FO4 defaults/multipliers/inverts are distinct. [VERIFIED: AGENTS.md; VERIFIED: codebase read `TemplateProfileCatalogFactory.cs`; CITED: `.planning/REQUIREMENTS.md`] |
| PROF-02 | User can load existing `.jbs2bg` projects with legacy `isUUNP` values and preserve compatible profile semantics on save. | Keep `ProjectFileService` load order of `Profile` first and `isUUNP` fallback; add focused round-trip tests for absent `Profile` and unbundled `Profile`. [VERIFIED: codebase read `ProjectFileService.cs`; CITED: `openspec/specs/project-roundtrip/spec.md`] |
| PROF-03 | User can see a clear warning when a preset, project, or export path uses an unknown, missing, inferred, or likely mismatched profile. | Context narrows this to neutral unresolved-profile fallback info only; do not implement mismatch/inference warnings. [CITED: `01-CONTEXT.md` D-05 through D-12] |
| PROF-04 | User can generate templates, morphs, and BoS JSON with profile-specific behavior covered by tests for each bundled profile. | Use existing `TemplateGenerationService`, `SliderMathFormatter`, export writers, and test fixtures; add bundled-profile-specific tests without editing golden expected files. [VERIFIED: codebase read `TemplateGenerationService.cs`; VERIFIED: `dotnet test --list-tests`; CITED: AGENTS.md] |
| PROF-05 | User can understand that Fallout 4 profile support is experimental unless authoritative calibration data has been validated. | Context explicitly says not to surface FO4 experimental labels in the main workflow; keep calibration caveat outside Phase 1 main UI and document assumptions. [CITED: `01-CONTEXT.md` D-06/D-08; CITED: `.planning/STATE.md`] |
</phase_requirements>

## Project Constraints (from AGENTS.md)

- `BS2BG.Core` remains UI-free and portable; Avalonia/App-layer work belongs in `BS2BG.App`. [CITED: AGENTS.md]
- Use .NET 10/C# 14 for App and Tests, netstandard2.1/C# 13 for Core. [CITED: AGENTS.md]
- Avalonia compiled bindings are enabled by default; every AXAML root and `DataTemplate` needs `x:DataType`. [CITED: AGENTS.md; CITED: https://docs.avaloniaui.net/docs/xaml/compilation]
- Follow project ReactiveUI conventions: `ReactiveObject`, `[Reactive]`, `ReactiveCommand`, observable `canExecute`, `ToProperty`, and no custom `RelayCommand`/`AsyncRelayCommand`. [CITED: AGENTS.md; VERIFIED: Context7 `/reactiveui/reactiveui`; VERIFIED: Context7 `/reactiveui/reactiveui.sourcegenerators`]
- Keep `MainWindow` as a plain `Avalonia.Controls.Window`, not `ReactiveWindow`. [CITED: AGENTS.md]
- New tests use xUnit v3 and FluentAssertions style, not bare `Assert.*`. [CITED: AGENTS.md]
- Do not edit sacred files without explicit caution: `tests/fixtures/expected/**`, `JavaFloatFormatting.cs`, `SliderMathFormatter.cs`, `BodyGenIniExportWriter.cs`, `BosJsonExportWriter.cs`. [CITED: AGENTS.md]
- Preserve byte-identical output semantics: INI CRLF, BoS JSON LF/no trailing newline, half-up rounding, dual float formatters, and profile-specific defaults/multipliers/inverts. [CITED: AGENTS.md]
- Use PowerShell on Windows; do not use Bash shell patterns or redirect to `nul`. [CITED: AGENTS.md]
- Never delete or rewrite comments as cleanup; add XML doc comments for methods added or substantially rewritten unless trivial. [CITED: global AGENTS.md]

## Summary

Phase 1 is not a new-library problem; it is a bounded correctness and trust-hardening change inside the existing C#/.NET 10 + Avalonia 12 + ReactiveUI architecture. [VERIFIED: AGENTS.md; VERIFIED: codebase read `Directory.Packages.props`] The highest-risk issue is that `TemplateProfileCatalogFactory` currently registers `Fallout 4 CBBE` by loading `settings.json`, which means FO4 uses Skyrim CBBE calculation tables. [VERIFIED: codebase read `TemplateProfileCatalogFactory.cs`] The first implementation task should therefore add a distinct root-level FO4 JSON profile, copy it to app output/publish output, and prove via tests that its defaults, multipliers, and inverted list are not shared with either Skyrim profile. [CITED: `01-CONTEXT.md` D-01/D-02/D-13]

The second risk is silent fallback. `TemplateProfileCatalog.GetProfile` currently returns `DefaultProfile` for unknown profile names, which protects generation from crashing but hides that saved project semantics are unresolved. [VERIFIED: codebase read `TemplateProfileCatalog.cs`] Preserve this fallback for generation compatibility, but add explicit detection so the App layer can show neutral fallback information while preserving the original saved profile name for round-trip save/load. [CITED: `01-CONTEXT.md` D-07/D-12]

The third risk is overcorrecting with warnings or heuristics. Phase context explicitly forbids FO4 experimental labels, slider-name mismatch warnings, path inference, and modal warning banners for this phase. [CITED: `01-CONTEXT.md` D-05 through D-10] Tests must therefore prove both positive behavior (distinct bundled profiles, selected-profile import, unresolved-profile info) and negative behavior (no mismatch warning or generation block for custom/unprofiled slider names). [CITED: `01-CONTEXT.md` D-15/D-16]

**Primary recommendation:** Implement profile trust as a Core catalog/data correctness change plus App-layer neutral fallback visibility; do not add profile inference, custom profile management, or FO4 experimental warning UX in Phase 1. [CITED: `01-CONTEXT.md`]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Bundled profile data | App packaging/content | Core profile loader | Root-level JSON files are copied by `BS2BG.App.csproj`, while parsing remains in Core `SliderProfileJsonService`. [VERIFIED: codebase read `BS2BG.App.csproj`; VERIFIED: codebase read `SliderProfileJsonService.cs`] |
| Profile constants and legacy mapping | Core domain model | App ViewModel | `ProjectProfileMapping` owns canonical display names and `isUUNP` compatibility; ViewModels bind those names. [VERIFIED: codebase read `ProjectProfileMapping.cs`; VERIFIED: codebase read `TemplatesViewModel.cs`] |
| Calculation fallback | Core generation/catalog | App ViewModel | `TemplateProfileCatalog.GetProfile` performs generation fallback; App needs detection to make fallback visible. [VERIFIED: codebase read `TemplateProfileCatalog.cs`] |
| Neutral fallback information | App ViewModel/UI | Core catalog helper | User-facing text belongs in `TemplatesViewModel`/AXAML; Core should expose only lookup facts. [VERIFIED: codebase read `TemplatesViewModel.cs`; CITED: AGENTS.md architecture constraint] |
| Legacy `.jbs2bg` round-trip | Core serialization | Tests | `ProjectFileService` already serializes/deserializes `Profile` and `isUUNP`; tests protect compatibility. [VERIFIED: codebase read `ProjectFileService.cs`] |
| Profile-specific output tests | Tests | Core generation/export | Golden fixtures stay sacred; focused C# tests prove bundled-profile behavior without rebaselining expected files. [CITED: AGENTS.md; CITED: `01-CONTEXT.md` D-14] |

## Standard Stack

### Core
| Library / Component | Version | Purpose | Why Standard |
|---------------------|---------|---------|--------------|
| .NET SDK | 10.0.203 installed | Build/test runtime for App and Tests | Repository targets net10.0 for App/Tests; installed SDK was verified. [VERIFIED: `dotnet --version`] |
| `BS2BG.Core` | in-repo netstandard2.1 | Profile models, generation, serialization, formatter pipeline | Keeps calculation and file semantics UI-free and testable. [CITED: AGENTS.md; VERIFIED: codebase read] |
| `System.Text.Json` | pinned 10.0.7; latest stable 10.0.7 | Project/profile JSON parsing and writing | Existing code uses it; Microsoft docs support `WriteIndented`, property-name/order attributes, and .NET 9+ indentation controls. [VERIFIED: Directory.Packages.props + NuGet API; CITED: https://learn.microsoft.com/dotnet/standard/serialization/system-text-json/customize-properties] |
| `System.Xml.Linq.XDocument` | BCL | BodySlide XML parsing | Existing parser uses .NET XML DOM; no new XML library is needed. [CITED: AGENTS.md] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Avalonia | pinned 12.0.1; latest stable 12.0.1 | Desktop UI and AXAML compilation | Use for profile selector/fallback panel only; keep business logic out of views. [VERIFIED: Directory.Packages.props + NuGet API; CITED: https://docs.avaloniaui.net/docs/xaml/compilation] |
| ReactiveUI.Avalonia | pinned 12.0.1; latest stable 14.7.1 | Project-standard App MVVM integration | Keep the pinned project version for Phase 1; do not combine profile correctness with framework upgrade. [VERIFIED: Directory.Packages.props + NuGet API; CITED: AGENTS.md] |
| ReactiveUI.SourceGenerators | pinned 2.6.1; latest stable 2.6.1 | `[Reactive]` and `[ObservableAsProperty]` generation | Use for new `TemplatesViewModel` state, such as fallback-info text/visibility. [VERIFIED: Directory.Packages.props + NuGet API; VERIFIED: Context7 `/reactiveui/reactiveui.sourcegenerators`] |
| FluentAssertions | pinned 8.9.0; latest stable 8.9.0 | Fluent test assertions | Required style for new tests in this repository. [VERIFIED: Directory.Packages.props + NuGet API; CITED: AGENTS.md] |
| xUnit v3 | pinned 3.2.2; latest stable 3.2.2 | Unit/headless UI tests | Existing test project and Avalonia.Headless.XUnit use xUnit v3. [VERIFIED: Directory.Packages.props + NuGet API; CITED: AGENTS.md] |
| Avalonia.Headless.XUnit | pinned 12.0.1; latest stable 12.0.1 | Headless UI tests | Use for AXAML/profile-selector/fallback-panel smoke coverage. [VERIFIED: Directory.Packages.props + NuGet API; CITED: AGENTS.md] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Root-level `settings_FO4_CBBE.json` | `profiles/fallout4-cbbe.json` | Locked out of scope by D-01/D-03; defer folder migration to Phase 4. [CITED: `01-CONTEXT.md`] |
| Neutral fallback info | Modal warnings/banners | Locked out by D-05/D-08 because mismatch and experimental warning UX would create false pressure. [CITED: `01-CONTEXT.md`] |
| Explicit profile selection | Path or slider-name inference | Locked out by D-09/D-10 because real body mods can overlap or be intentionally unprofiled. [CITED: `01-CONTEXT.md`; CITED: `PRD.md` §9a] |
| Focused C# tests | Regenerating golden expected fixtures | Golden expected files are sacred; rebaselining them would hide regressions. [CITED: AGENTS.md; CITED: `01-CONTEXT.md` D-14] |

**Installation / package changes:** No new packages should be installed for Phase 1. [VERIFIED: codebase read; VERIFIED: NuGet version audit]

**Version verification:** NuGet flat-container API checks on 2026-04-26 verified the pinned stable versions above; `ReactiveUI.Avalonia` has a newer stable release, but this phase should not upgrade it because project guidance pins ReactiveUI/Avalonia behavior and the change is unrelated to profile correctness. [VERIFIED: NuGet API; CITED: AGENTS.md]

## Architecture Patterns

### System Architecture Diagram

```text
User selects profile / opens project / imports XML
        |
        v
TemplatesViewModel (App)
  - binds selector to catalog.ProfileNames
  - assigns selected profile to imported presets
  - detects unresolved saved profile for neutral info
        |
        +-----------------------------+
        |                             |
        v                             v
ProjectFileService (Core)       TemplateProfileCatalog (Core)
  - Profile first                 - exact bundled profile lookup
  - isUUNP fallback               - existing GetProfile fallback math
  - save both fields              - new detection helper for App
        |                             |
        v                             v
SliderPreset.ProfileName ----> TemplateGenerationService ----> SliderMathFormatter / Export Writers
        |                             |
        v                             v
.jbs2bg round-trip              templates.ini / morphs.ini / BoS JSON
```

### Recommended Project Structure

```text
root/
├── settings.json                 # existing Skyrim CBBE bundled profile; leave in place [CITED: 01-CONTEXT.md D-03]
├── settings_UUNP.json            # existing Skyrim UUNP bundled profile; leave in place [CITED: 01-CONTEXT.md D-03]
├── settings_FO4_CBBE.json        # recommended FO4 root-level bundled profile name [CITED: 01-CONTEXT.md D-01]
├── src/BS2BG.Core/
│   ├── Generation/TemplateProfileCatalog.cs     # add detection helper; keep fallback generation behavior [VERIFIED: codebase read]
│   ├── Models/ProjectProfileMapping.cs          # canonical profile names / legacy mapping [VERIFIED: codebase read]
│   └── Serialization/ProjectFileService.cs      # legacy and named-profile round-trip [VERIFIED: codebase read]
├── src/BS2BG.App/
│   ├── BS2BG.App.csproj                         # add FO4 content copy entry [VERIFIED: codebase read]
│   ├── Services/TemplateProfileCatalogFactory.cs # load FO4 profile from distinct JSON [VERIFIED: codebase read]
│   ├── ViewModels/TemplatesViewModel.cs          # selected profile + neutral fallback info [VERIFIED: codebase read]
│   └── Views/MainWindow.axaml                    # compiled-binding fallback panel [VERIFIED: codebase grep]
└── tests/BS2BG.Tests/
    ├── TemplateProfileCatalogFactoryTests.cs
    ├── TemplateProfileCatalogTests.cs
    ├── ProjectFileServiceTests.cs
    ├── TemplatesViewModelTests.cs
    └── AppShellTests.cs
```

### Pattern 1: Add lookup detection without changing fallback generation
**What:** Keep `GetProfile` returning `DefaultProfile`, but add an exact-match predicate/try-get for user-facing fallback detection. [VERIFIED: codebase read `TemplateProfileCatalog.cs`]  
**When to use:** Use `GetProfile` in generation paths; use `ContainsProfile`/`TryGetProfile` in App/UI diagnostics. [CITED: `01-CONTEXT.md` D-07/D-12]

```csharp
// Source: in-repo pattern from TemplateProfileCatalog.GetProfile + Phase 1 context.
public bool ContainsProfile(string? name) =>
    !string.IsNullOrWhiteSpace(name)
    && profiles.Any(profile => string.Equals(profile.Name, name, StringComparison.OrdinalIgnoreCase));

public TemplateProfile GetProfile(string? name)
{
    if (!string.IsNullOrWhiteSpace(name))
    {
        var match = profiles.FirstOrDefault(profile => string.Equals(
            profile.Name,
            name,
            StringComparison.OrdinalIgnoreCase));
        if (match is not null) return match;
    }

    return DefaultProfile;
}
```

### Pattern 2: Preserve serialized profile name separately from selected bundled option
**What:** A loaded preset may have `SliderPreset.ProfileName == "Community CBBE"`; generation can use default fallback math while the saved profile name remains unchanged until the user explicitly chooses a bundled profile. [CITED: `01-CONTEXT.md` D-07/D-12]  
**When to use:** Use when selected preset's saved profile is not in `profileCatalog.ProfileNames`. [VERIFIED: codebase read `TemplatesViewModel.cs`]

```csharp
// Source: in-repo TemplatesViewModel binding pattern + Context7 ReactiveUI source-generator docs.
[Reactive(SetModifier = AccessModifier.Private)]
private string _profileFallbackMessage = string.Empty;

[ObservableAsProperty]
private bool _isProfileFallbackVisible;

private void RefreshProfileFallbackInfo()
{
    var savedName = SelectedPreset?.ProfileName;
    if (string.IsNullOrWhiteSpace(savedName) || profileCatalog.ContainsProfile(savedName))
    {
        ProfileFallbackMessage = string.Empty;
        return;
    }

    ProfileFallbackMessage = "Saved profile \"" + savedName + "\" is not bundled. BS2BG is using "
                             + profileCatalog.GetProfile(savedName).Name
                             + " calculation rules for preview and generation until you choose a bundled profile.";
}
```

### Pattern 3: Keep Avalonia bindings compiled
**What:** Add fallback UI with `x:DataType` and standard bindings; do not introduce reflection bindings unless unavoidable. [CITED: https://docs.avaloniaui.net/docs/xaml/compilation]  
**When to use:** Any AXAML changes in Phase 1. [CITED: AGENTS.md]

```xml
<!-- Source: Avalonia compiled bindings docs + in-repo MainWindow.axaml binding pattern. -->
<Border IsVisible="{Binding Templates.IsProfileFallbackVisible}"
        Classes="profileInfoPanel">
  <TextBlock Text="{Binding Templates.ProfileFallbackMessage}"
             TextWrapping="Wrap" />
</Border>
```

### Anti-Patterns to Avoid
- **Replacing `GetProfile` fallback with throwing behavior:** generation/export paths currently rely on fallback; throwing would break legacy/unbundled project loads. [VERIFIED: codebase read `TemplateProfileCatalog.cs`; CITED: `01-CONTEXT.md` D-07/D-12]
- **Normalizing unknown profile names to Skyrim CBBE on save:** this loses round-trip compatibility. [CITED: `01-CONTEXT.md` D-07/D-12]
- **Inferring profile from path or slider overlap:** explicitly forbidden and high-noise for custom body mods. [CITED: `01-CONTEXT.md` D-09/D-10]
- **Labeling FO4 as experimental in selector/status/banner:** explicitly forbidden for Phase 1 main workflow. [CITED: `01-CONTEXT.md` D-06/D-08]
- **Editing golden expected files:** explicitly forbidden unless rebaseline is separately approved. [CITED: AGENTS.md; CITED: `01-CONTEXT.md` D-14]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON parsing/writing | Custom JSON parser/string concatenation | `System.Text.Json` + existing DTOs/converters | Existing project serialization uses it and Microsoft docs support property names/order/indentation. [VERIFIED: codebase read `ProjectFileService.cs`; CITED: Microsoft Learn System.Text.Json docs] |
| Reactive state notifications | Manual `INotifyPropertyChanged` boilerplate or custom command types | `ReactiveObject`, `[Reactive]`, `[ObservableAsProperty]`, `ReactiveCommand` | Project conventions explicitly retired custom commands and source generators are already referenced. [CITED: AGENTS.md; VERIFIED: Context7 ReactiveUI docs] |
| UI binding resolution | Reflection-binding default | Avalonia compiled bindings with `x:DataType` | Project and Avalonia docs require/use compiled binding validation. [CITED: AGENTS.md; CITED: Avalonia docs] |
| Profile matching heuristics | Path/game-folder/slider-overlap detector | Explicit user-selected `ProfileName` + neutral unresolved-profile detection | Heuristics are locked out and produce false positives for custom body mods. [CITED: `01-CONTEXT.md` D-05/D-09/D-10] |
| FO4 calibration | Invented multiplier/inversion math | Seed profile defaults/multipliers at `1.0`, empty `Inverted` | Locked decision D-02; authoritative calibration is deferred. [CITED: `01-CONTEXT.md`; CITED: `.planning/STATE.md`] |

**Key insight:** Phase 1 trust comes from making profile semantics explicit and testable, not from making the app smarter about guessing profiles. [CITED: `01-CONTEXT.md`]

## Common Pitfalls

### Pitfall 1: Accidentally preserving the current FO4-Skyrim table reuse
**What goes wrong:** FO4 remains listed as selectable but uses `settings.json`. [VERIFIED: codebase read `TemplateProfileCatalogFactory.cs`]  
**Why it happens:** Factory registration labels FO4 separately but loads Skyrim CBBE data. [VERIFIED: codebase read]  
**How to avoid:** Add `settings_FO4_CBBE.json`, wire it in `BS2BG.App.csproj`, and assert FO4-only slider defaults such as `BreastCenterBig`, `ButtNew`, `ShoulderTweak`, `HipBack`, and `ChubbyWaist`. [CITED: `PRD.md` §7.7; CITED: `01-CONTEXT.md` D-13]  
**Warning signs:** Tests pass despite deleting/renaming FO4 JSON, or FO4 profile default list equals Skyrim CBBE. [ASSUMED]

### Pitfall 2: Losing unbundled profile names during fallback
**What goes wrong:** A project saved with `Profile: "Community CBBE"` reloads/saves as `Skyrim CBBE`. [CITED: `01-CONTEXT.md` D-07/D-12]  
**Why it happens:** Dropdown selection is treated as the source of truth even when no bundled item matches. [VERIFIED: codebase read `TemplatesViewModel.cs`]  
**How to avoid:** Keep `SliderPreset.ProfileName` unchanged until user chooses a bundled profile; display neutral fallback info separately. [CITED: `01-CONTEXT.md` D-07/D-12]  
**Warning signs:** `ProjectFileService.SaveToString` loses the original `Profile` value in a no-edit round trip. [VERIFIED: codebase read `ProjectFileService.cs`]

### Pitfall 3: Adding noisy mismatch warnings against user decisions
**What goes wrong:** Custom/unprofiled body mods trigger warnings or generation blocks because slider names are unknown. [CITED: `01-CONTEXT.md` D-05/D-16]  
**Why it happens:** Slider coverage is tempting to interpret as profile correctness, but BodyGen files can encompass many body mods. [CITED: `01-CONTEXT.md` D-05]  
**How to avoid:** Only unresolved saved profile fallback produces neutral info in Phase 1. [CITED: `01-CONTEXT.md` D-16]  
**Warning signs:** Tests or UI strings contain "mismatch", "experimental", or "warning" for FO4/custom slider names. [CITED: `01-CONTEXT.md` D-08]

### Pitfall 4: Breaking Java parity while adding tests
**What goes wrong:** Formatting/export changes alter CRLF/LF, rounding, default injection, or float formatting. [CITED: AGENTS.md]  
**Why it happens:** Profile work touches generation paths near sacred formatter/export code. [VERIFIED: codebase read `TemplateGenerationService.cs`]  
**How to avoid:** Prefer tests around catalog data and ViewModel behavior; avoid sacred formatter/export edits unless a test proves they are necessary. [CITED: AGENTS.md]  
**Warning signs:** Golden-file snapshot failures under `tests/fixtures/expected/**`. [VERIFIED: `dotnet test --list-tests`]

## Code Examples

### FO4 factory registration
```csharp
// Source: TemplateProfileCatalogFactory.cs current pattern; target file name chosen under D-01 discretion.
return new TemplateProfileCatalog(new[]
{
    new TemplateProfile(ProjectProfileMapping.SkyrimCbbe,
        LoadRequiredProfile("settings.json", directories)),
    new TemplateProfile(ProjectProfileMapping.SkyrimUunp,
        LoadRequiredProfile("settings_UUNP.json", directories)),
    new TemplateProfile(ProjectProfileMapping.Fallout4Cbbe,
        LoadRequiredProfile("settings_FO4_CBBE.json", directories))
});
```

### Content-copy registration
```xml
<!-- Source: BS2BG.App.csproj current Content pattern. -->
<Content Include="..\..\settings_FO4_CBBE.json"
         Link="settings_FO4_CBBE.json"
         CopyToOutputDirectory="PreserveNewest"
         CopyToPublishDirectory="PreserveNewest"/>
```

### Legacy profile round-trip test shape
```csharp
// Source: ProjectFileService.cs behavior + FluentAssertions project style.
var json = """
{
  "SliderPresets": {
    "Alpha": { "isUUNP": false, "Profile": "Community CBBE", "SetSliders": [] }
  },
  "CustomMorphTargets": {},
  "MorphedNPCs": {}
}
""";

var service = new ProjectFileService();
var project = service.LoadFromString(json);

project.SliderPresets.Single().ProfileName.Should().Be("Community CBBE");
service.SaveToString(project).Should().Contain("\"Profile\": \"Community CBBE\"");
```

### Focused validation command
```powershell
# Source: Microsoft Learn dotnet test filtering docs.
dotnet test --filter "FullyQualifiedName~TemplateProfileCatalogFactoryTests|FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~ProjectFileServiceTests"
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| ReactiveUI Fody / manual setters | ReactiveUI.SourceGenerators `[Reactive]` and `[ObservableAsProperty]` | Captured by repository `reactive-mvvm-conventions` spec before this phase. [CITED: `openspec/specs/reactive-mvvm-conventions/spec.md`] | New ViewModel state should use source generators, not retired command/setter patterns. [CITED: AGENTS.md] |
| Reflection bindings / runtime XAML binding errors | Avalonia compiled bindings with `x:DataType` | Avalonia docs describe project-wide compiled binding support. [CITED: https://docs.avaloniaui.net/docs/xaml/compilation] | AXAML changes should be type-checked at build time. [CITED: Avalonia docs] |
| Two-profile `isUUNP` toggle | Named profile selector with legacy `isUUNP` compatibility | Existing code already has `Profile` field support and profile selector binding. [VERIFIED: codebase read `ProjectFileService.cs`; VERIFIED: grep `MainWindow.axaml`] | Phase 1 should harden named profiles without removing legacy fields. [CITED: `PRD.md` §4.5] |
| FO4 CBBE reuses Skyrim CBBE tables | Distinct FO4 bundled profile JSON | Phase 1 target state. [CITED: `01-CONTEXT.md` D-01/D-02] | Trust proof is distinct-table tests, not authoritative FO4 calibration. [CITED: `01-CONTEXT.md` D-13] |

**Deprecated/outdated:**
- `profiles/` folder migration from PRD v2 extension is deferred for Phase 4 in current context. [CITED: `01-CONTEXT.md` D-03]
- FO4 experimental selector labels from older roadmap/PRD wording are superseded by D-06/D-08 for Phase 1 main workflow. [CITED: `01-CONTEXT.md`]
- Avalonia `Avalonia.Diagnostics` is not used; expert docs warn against it, and Phase 1 does not need devtools changes. [CITED: Avalonia expert rules]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Deleting/renaming FO4 JSON should fail tests if content-copy and factory wiring are complete. | Common Pitfalls | Test design may need to explicitly load from a temporary directory and app output to catch packaging omissions. |

## Open Questions

1. **Exact FO4 JSON file name**
   - What we know: Context leaves the exact name to agent discretion if it is root-level and maps to `ProjectProfileMapping.Fallout4Cbbe`. [CITED: `01-CONTEXT.md`]
   - What's unclear: No locked filename exists. [CITED: `01-CONTEXT.md`]
   - Recommendation: Use `settings_FO4_CBBE.json` because it stays alongside `settings.json` / `settings_UUNP.json` and reads as a profile variant. [ASSUMED]
2. **Where to document FO4 calibration caveat outside main workflow**
   - What we know: Main workflow must not label FO4 experimental. [CITED: `01-CONTEXT.md` D-06]
   - What's unclear: Phase 1 does not specify release-note/help placement. [CITED: `01-CONTEXT.md`]
   - Recommendation: Do not plan in-app caveat unless UI-SPEC already requires it; leave release/help documentation to a later explicit doc task. [CITED: `01-CONTEXT.md` D-08]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| .NET SDK | Build/test/validation | ✓ | 10.0.203 | None needed. [VERIFIED: `dotnet --version`] |
| NuGet package restore | Build/test | ✓ | Restore succeeded during `dotnet test --list-tests` | None needed. [VERIFIED: `dotnet test --list-tests`] |
| GSD graphify | Optional graph context | ✗ | disabled | Continue with codebase/file research. [VERIFIED: `gsd-tools graphify status`] |

**Missing dependencies with no fallback:** None for Phase 1 implementation. [VERIFIED: environment audit]

**Missing dependencies with fallback:** GSD graphify is disabled; codebase reads/grep supplied architecture context. [VERIFIED: environment audit]

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | xUnit v3 3.2.2 + FluentAssertions 8.9.0 + Avalonia.Headless.XUnit 12.0.1 [VERIFIED: Directory.Packages.props + NuGet API] |
| Config file | `tests/BS2BG.Tests/BS2BG.Tests.csproj` and centralized `Directory.Packages.props` [VERIFIED: codebase read] |
| Quick run command | `dotnet test --filter "FullyQualifiedName~TemplateProfileCatalogFactoryTests|FullyQualifiedName~TemplateProfileCatalogTests|FullyQualifiedName~ProjectFileServiceTests|FullyQualifiedName~TemplatesViewModelTests"` [CITED: Microsoft Learn dotnet test filter docs] |
| Full suite command | `dotnet test` [CITED: AGENTS.md] |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| PROF-01 | FO4 CBBE loads from distinct JSON and has FO4-only defaults/multipliers/empty inverted list | unit/integration | `dotnet test --filter FullyQualifiedName~TemplateProfileCatalogFactoryTests` | ✅ existing file; add cases [VERIFIED: codebase read] |
| PROF-02 | Legacy no-`Profile` maps by `isUUNP`; unbundled `Profile` is preserved on save | unit | `dotnet test --filter FullyQualifiedName~ProjectFileServiceTests` | ✅ existing file; add cases [VERIFIED: `dotnet test --list-tests`] |
| PROF-03 | Unresolved saved profile shows neutral fallback info only | unit/headless UI | `dotnet test --filter "FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~AppShellTests"` | ✅ existing files; add cases [VERIFIED: `dotnet test --list-tests`] |
| PROF-04 | Bundled profiles generate templates/BoS through profile-specific behavior | unit/snapshot-adjacent | `dotnet test --filter "FullyQualifiedName~TemplateGenerationServiceTests|FullyQualifiedName~SliderMathFormatterTests"` | ✅ existing files; add focused non-golden cases [VERIFIED: `dotnet test --list-tests`] |
| PROF-05 | FO4 is not labeled experimental in main workflow | headless UI | `dotnet test --filter FullyQualifiedName~AppShellTests` | ✅ existing file; add selector/status text cases [VERIFIED: `dotnet test --list-tests`] |

### Sampling Rate
- **Per task commit:** Run the narrow command for touched files; for profile catalog work use `dotnet test --filter FullyQualifiedName~TemplateProfileCatalogFactoryTests`. [CITED: Microsoft Learn dotnet test filter docs]
- **Per wave merge:** `dotnet test`. [CITED: AGENTS.md]
- **Phase gate:** Full suite green before `/gsd-verify-work`. [CITED: GSD workflow]

### Wave 0 Gaps
- [ ] `tests/BS2BG.Tests/TemplateProfileCatalogTests.cs` — covers exact-match detection vs fallback behavior for unresolved profile names. [VERIFIED: no existing class in `dotnet test --list-tests`]
- [ ] New/extended `ProjectFileServiceTests` cases — covers unbundled `Profile` preservation and no-`Profile` `isUUNP` mapping. [VERIFIED: existing test file]
- [ ] New/extended `TemplatesViewModelTests` cases — covers unresolved profile info, selected-profile import, and no mismatch warnings. [VERIFIED: existing test file]
- [ ] New/extended `AppShellTests` cases — covers selector options and absence of FO4 experimental labels. [VERIFIED: existing test list]

## Security Domain

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no | Local offline desktop app; no authentication boundary introduced. [VERIFIED: project docs] |
| V3 Session Management | no | No sessions/cookies/tokens. [VERIFIED: project docs] |
| V4 Access Control | no | No multi-user authorization boundary. [VERIFIED: project docs] |
| V5 Input Validation | yes | Treat JSON/XML/project profile names as data; preserve names, reject only existing forbidden preset characters, and do not execute content. [VERIFIED: codebase read `SliderPreset.cs`; VERIFIED: codebase read `SliderProfileJsonService.cs`] |
| V6 Cryptography | no | No cryptography introduced. [VERIFIED: phase scope] |

### Known Threat Patterns for local profile/project files
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Tampered/missing bundled FO4 JSON causes silent wrong output | Tampering | Required profile loading fails when file is missing; tests assert distinct FO4 data and packaging copy. [VERIFIED: codebase read `TemplateProfileCatalogFactory.cs`] |
| Malicious/unexpected project profile name triggers code behavior | Tampering | Treat profile name as inert string; use catalog exact-match/fallback only, no reflection or dynamic type lookup. [VERIFIED: codebase read `ProjectProfileMapping.cs`; VERIFIED: codebase read `TemplateProfileCatalog.cs`] |
| Noisy false-positive profile warnings erode trust | Repudiation/Information quality | Show only neutral unresolved-profile info per locked context; avoid heuristic mismatch warnings. [CITED: `01-CONTEXT.md` D-05/D-16] |

## Sources

### Primary (HIGH confidence)
- `AGENTS.md` — project architecture, sacred files, target stack, ReactiveUI conventions, testing rules. [CITED: AGENTS.md]
- `.planning/phases/01-profile-correctness-and-trust/01-CONTEXT.md` — locked Phase 1 decisions D-01 through D-16. [CITED]
- Codebase reads: `ProjectProfileMapping.cs`, `TemplateProfileCatalog.cs`, `TemplateProfileCatalogFactory.cs`, `ProjectFileService.cs`, `TemplatesViewModel.cs`, `BS2BG.App.csproj`, `Directory.Packages.props`. [VERIFIED: codebase read]
- `dotnet --version`, `dotnet test --list-tests`, NuGet flat-container version audit. [VERIFIED: CLI]
- Avalonia docs — compiled bindings and `x:DataType`: https://docs.avaloniaui.net/docs/xaml/compilation and https://docs.avaloniaui.net/docs/xaml/directives. [CITED]
- Context7 `/reactiveui/reactiveui` and `/reactiveui/reactiveui.sourcegenerators` — `ReactiveCommand.CreateFromTask`, `canExecute`, `[ObservableAsProperty]`. [VERIFIED: Context7 CLI]
- Microsoft Learn System.Text.Json property customization/order and dotnet test filtering. [CITED: https://learn.microsoft.com/dotnet/standard/serialization/system-text-json/customize-properties; CITED: https://learn.microsoft.com/dotnet/core/testing/selective-unit-tests]

### Secondary (MEDIUM confidence)
- `PRD.md` §4.5, §7.7, §9a — profile schema and FO4 slider seed observations; profile folder parts are superseded by Phase 1 context. [CITED: PRD.md]
- `.planning/STATE.md` — FO4 calibration remains medium-confidence. [CITED]

### Tertiary (LOW confidence)
- None relied on for implementation decisions. [VERIFIED: source audit]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — package versions verified from repository and NuGet API; no new dependency recommended. [VERIFIED]
- Architecture: HIGH — core/app/test boundaries verified in source and AGENTS.md. [VERIFIED]
- Pitfalls: HIGH for silent fallback/FO4 reuse, MEDIUM for exact FO4 seed completeness because calibration is intentionally best-effort. [VERIFIED: codebase; CITED: STATE.md]

**Research date:** 2026-04-26  
**Valid until:** 2026-05-26 for repository architecture; 2026-05-03 for package currency/version assumptions.
