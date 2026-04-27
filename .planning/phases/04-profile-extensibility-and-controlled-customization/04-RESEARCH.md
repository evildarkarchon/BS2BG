# Phase 4: Profile Extensibility and Controlled Customization - Research

**Researched:** 2026-04-27  
**Domain:** C#/.NET desktop local profile authoring, strict JSON validation, Avalonia/ReactiveUI MVVM, backward-compatible project serialization  
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
## Implementation Decisions

### Profile Storage And Identity
- **D-01:** Store editable custom profile JSON files in a user profiles folder, not beside bundled install files by default. This keeps release/package files read-only and protects bundled profile trust.
- **D-02:** Treat bundled profiles as read-only. Users must copy a bundled profile into a custom profile before editing metadata or slider tables.
- **D-03:** Reject duplicate profile display names case-insensitively across bundled and custom profiles. Do not allow custom profiles to shadow bundled profile names.
- **D-04:** Discover custom profiles automatically from the local user profiles folder and also provide explicit import/copy actions. Local folder discovery is the primary runtime source for reusable custom profiles.

### Authoring Rules
- **D-05:** Custom profile editing covers metadata and all current slider tables: profile name/game-style metadata plus `Defaults`, `Multipliers`, and `Inverted` entries.
- **D-06:** Validation is strict before import/save/catalog inclusion. Reject malformed schema, blank or duplicate slider names, nonnumeric values, ambiguous profile names, and otherwise invalid profile data rather than partially accepting a broken profile.
- **D-07:** Permit broad numeric float values instead of clamping to normal 0-1 defaults or positive-only multipliers. Validation should reject nonnumeric/malformed values, but researcher/planner should preserve modder flexibility for unusual body mods and only add diagnostics for extreme values if useful.
- **D-08:** Allow users to create a blank custom profile. This is explicitly accepted despite the risk of missing-default-heavy output; downstream agents should make validation and UI state clear rather than forbidding empty starting points.

### Missing Custom Profile Recovery
- **D-09:** If a project references a custom profile that is not installed, open the project and show a resolvable diagnostic with actions. Do not block project open.
- **D-10:** Preserve Phase 1 non-blocking behavior for unresolved profiles: profile-dependent preview/generation/export may continue with visible fallback calculation rules, but the unresolved state must not be silent.
- **D-11:** Recovery actions should include importing a matching profile JSON, remapping/adopting an installed or bundled profile, or intentionally keeping the unresolved reference with visible fallback until later.
- **D-12:** Imported profiles resolve unresolved project references only by exact profile display-name match, case-insensitively. Do not use filename stems or fuzzy matching as identity.

### Sharing And Project Embedding
- **D-13:** Embed referenced custom profile data into `.jbs2bg` project files for sharing rather than relying only on sidecar JSON files.
- **D-14:** Preserve older-reader compatibility by adding an optional custom-profiles section while keeping the existing `SliderPresets`, `CustomMorphTargets`, `MorphedNPCs`, `isUUNP`, and `Profile` fields intact.
- **D-15:** Include only custom profiles referenced by project presets when saving/sharing embedded profile data. Do not embed unrelated local custom profiles.
- **D-16:** When loading a project with embedded custom profile data that conflicts with an existing local profile of the same name, prompt the user to import/replace/rename or keep the local profile. Do not silently let either project or local data win.
- **D-17:** Include a separate export-profile action for selected custom profiles as JSON. This complements embedded project sharing without expanding into Phase 5's full portable project bundle.

### the agent's Discretion
- Exact user profiles folder path and filename convention, as long as editable profiles are local user data and bundled profiles remain protected.
- Exact UI placement for profile management, as long as missing-profile recovery is visible through diagnostics/profile UI and normal profile fallback copy stays neutral.
- Exact optional `.jbs2bg` custom-profiles section name and DTO shape, as long as legacy fields remain unchanged and older consumers can ignore the new section.
- Exact warning/diagnostic wording for strict validation and extreme numeric values, as long as malformed/ambiguous profile data is rejected before catalog inclusion.

### Deferred Ideas (OUT OF SCOPE)
## Deferred Ideas

None — discussion stayed within Phase 4 profile extensibility scope. Full portable project bundles, CLI automation, release artifact verification, and broader share packages remain Phase 5.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EXT-01 | User can import, copy, export, and validate local JSON profile files without corrupting bundled profiles or existing projects. | Use a Core `ProfileDefinitionService`/validator plus App profile-store adapter under `%APPDATA%/jBS2BG/profiles`, atomic writes, case-insensitive catalog identity checks, and read-only bundled catalog entries. [VERIFIED: CONTEXT.md D-01-D-06, D-17; codebase UserPreferencesService.cs; AtomicFileWriter.cs] |
| EXT-02 | User can edit supported profile metadata and slider tables through validated workflows that reject malformed or ambiguous profile data. | Validate typed profile DTOs strictly before catalog inclusion; accept blank well-formed profiles and broad finite floats; reject blank/duplicate slider names, nonnumeric JSON tokens, duplicate profile names, and duplicate table names. [VERIFIED: CONTEXT.md D-05-D-08; SliderProfileJsonService.cs current shape] |
| EXT-03 | User can save projects that reference custom profiles while preserving legacy compatibility fields for older `.jbs2bg` consumers. | Extend `ProjectFileService` with an optional top-level custom profile section ordered after legacy root fields while preserving `SliderPresets`, `CustomMorphTargets`, `MorphedNPCs`, `isUUNP`, and `Profile`. [VERIFIED: CONTEXT.md D-13-D-15; ProjectFileService.cs; Microsoft System.Text.Json property order docs] |
| EXT-04 | User can resolve missing custom profile references through clear diagnostics rather than silent fallback. | Extend profile diagnostics/recovery over existing neutral fallback detection; project open remains non-blocking, generation may continue with visible fallback, and imported profiles resolve only exact display-name matches. [VERIFIED: CONTEXT.md D-09-D-12; ProfileDiagnosticsService.cs] |
| EXT-05 | User can bundle or copy project-specific profiles when sharing a project with another machine. | Save referenced custom profile definitions into `.jbs2bg`, provide selected custom-profile JSON export, and handle embedded/local conflicts explicitly at load. [VERIFIED: CONTEXT.md D-13-D-17] |
</phase_requirements>

## Summary

Phase 4 should be implemented as a local-profile management layer over the existing Core/App split: Core owns profile DTO parsing, strict validation, immutable catalog composition, project embedding, and recovery diagnostics; App owns user-profile folder location, file pickers, conflict prompts, editable ViewModels, and compiled-bound UI. [VERIFIED: AGENTS.md; ARCHITECTURE.md; CONTEXT.md code_context]

The primary architectural risk is not library choice; it is silently mixing trust domains: bundled read-only profiles, local editable custom profiles, embedded project profiles, and unresolved saved profile names must stay distinguishable until the user takes an explicit action. [VERIFIED: CONTEXT.md D-01-D-04, D-09-D-16] The current code already has the right seams (`SliderProfileJsonService`, `TemplateProfileCatalog`, `TemplateProfileCatalogFactory`, `ProjectFileService`, `ProfileDiagnosticsService`), but they need richer profile identity/source metadata and validation result objects rather than more ad hoc `JsonDocument` calls. [VERIFIED: codebase read: SliderProfileJsonService.cs, TemplateProfileCatalog.cs, TemplateProfileCatalogFactory.cs, ProjectFileService.cs, ProfileDiagnosticsService.cs]

**Primary recommendation:** Add a Core-first custom profile pipeline (`ProfileDefinitionDto` → `ProfileValidationResult` → `ProfileDefinition`/`TemplateProfile` → `TemplateProfileCatalog`) and App adapters for user-local storage/import/export/conflict UI; do not mutate bundled profile files, do not infer profile identity from filenames, and do not change formatter/export semantics. [VERIFIED: CONTEXT.md; AGENTS.md sacred files]

## Project Constraints (from AGENTS.md)

- `BS2BG.Core` targets `netstandard2.1`, stays pure domain/I/O, and must not reference Avalonia or platform services. [VERIFIED: AGENTS.md lines 19-26; ARCHITECTURE.md]
- `BS2BG.App` targets `net10.0` and uses Avalonia 12 with compiled bindings enabled by default; every AXAML root and `DataTemplate` must declare `x:DataType`. [VERIFIED: AGENTS.md lines 27-49; Avalonia docs]
- App ViewModels must use ReactiveUI patterns: `ReactiveObject`, `[Reactive]`, `ReactiveCommand.Create*`, observable `canExecute`, `ToProperty`, and `RxApp.TaskpoolScheduler`; do not reintroduce custom relay commands or direct dispatcher calls from ViewModels. [VERIFIED: AGENTS.md lines 45-47; openspec reactive-mvvm-conventions]
- `MainWindow` and new windows remain plain `Avalonia.Controls.Window` unless a specific need is proven. [VERIFIED: AGENTS.md line 46; openspec reactive-mvvm-conventions]
- Use `System.Text.Json` for JSON and `XDocument` for BodySlide XML; new tests use xUnit v3 and FluentAssertions style. [VERIFIED: AGENTS.md lines 48-49; dotnet list package]
- Sacred files require explicit caution before edits: `tests/fixtures/expected/**`, `JavaFloatFormatting.cs`, `SliderMathFormatter.cs`, `BodyGenIniExportWriter.cs`, `BosJsonExportWriter.cs`. [VERIFIED: AGENTS.md lines 51-58]
- Preserve byte-identical output behavior: INI CRLF, BoS JSON LF/no trailing newline, half-up rounding, context-specific float formatting, missing-default injection, and profile-specific lookup tables. [VERIFIED: AGENTS.md lines 60-70]
- Primary development platform is Windows; use PowerShell semantics and do not redirect to `nul`. [VERIFIED: AGENTS.md lines 121-125]
- New or substantially rewritten C# methods should have XML doc comments; do not remove accurate comments as cleanup. [VERIFIED: global AGENTS.md]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Custom profile JSON validation | Core Domain/Services | App ViewModels for presentation | Validation is user-data domain logic and must be UI-free/reusable by project load, import, edit, and tests. [VERIFIED: ARCHITECTURE.md; CONTEXT.md D-06] |
| User-local custom profile folder | Platform Adapter (`BS2BG.App.Services`) | Core service for profile file content validation | Folder location is OS/app data concern; file contents are Core concern. [VERIFIED: UserPreferencesService.cs; Environment.SpecialFolder docs] |
| Bundled vs custom catalog composition | App composition + Core catalog | — | Factory currently loads bundled files from `AppContext.BaseDirectory`; Phase 4 should add user source while preserving required bundled loading. [VERIFIED: TemplateProfileCatalogFactory.cs] |
| Profile editor UI | App ViewModels/Views | Core validator | Editing and conflict prompts are presentation workflows; saved definitions are accepted only after Core validation. [VERIFIED: CONTEXT.md code_context; Avalonia docs] |
| Project embedded custom profiles | Core Serialization | App conflict prompts before local import | `.jbs2bg` DTO shape and compatibility belong in Core; conflict choice is user-facing App workflow. [VERIFIED: ProjectFileService.cs; CONTEXT.md D-13-D-16] |
| Missing-profile recovery diagnostics | Core Diagnostics | App diagnostics/profile UI actions | Existing diagnostics are Core read-only reports; App should expose explicit actions without making diagnostics mutate by default. [VERIFIED: ProfileDiagnosticsService.cs; Phase 3 CONTEXT.md] |
| Export selected profile JSON | Core serializer/writer helper | App file picker | JSON content and validation are Core; picker destination is App storage provider. [VERIFIED: Avalonia StorageProvider docs; SliderProfileJsonService.cs] |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| .NET SDK / runtime | 10.0.203 SDK available locally | Build/runtime for App and tests | Project targets net10.0 for App/Tests and netstandard2.1 for Core. [VERIFIED: dotnet --version; AGENTS.md] |
| System.Text.Json | 10.0.7 (latest exact NuGet search returned 10.0.7) | Profile JSON and `.jbs2bg` serialization | Existing project standard; supports property names/orders, required properties, extension data, `JsonDocument`, and strict token inspection without adding Newtonsoft. [VERIFIED: Directory.Packages.props; dotnet package search; Microsoft docs] |
| System.IO + `AtomicFileWriter` | BCL + existing Core helper | Atomic writes for custom profile files and project saves | Existing writers use temp files plus `File.Replace`/`File.Move` and ledger exceptions; reuse to avoid partial profile/project corruption. [VERIFIED: AtomicFileWriter.cs] |
| Avalonia | 12.0.1 (latest exact NuGet search returned 12.0.1) | Desktop UI, dialogs, storage provider, compiled AXAML | Existing UI framework; StorageProvider is current file/folder picker API and compiled bindings require `x:DataType`. [VERIFIED: Directory.Packages.props; dotnet package search; Avalonia docs] |
| ReactiveUI.Avalonia | 12.0.1 (latest exact NuGet search returned 12.0.1) | Reactive commands and App ViewModel integration | Existing OpenSpec-mandated App convention; command availability comes from observables. [VERIFIED: Directory.Packages.props; openspec reactive-mvvm-conventions; Avalonia ReactiveCommand docs] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| ReactiveUI.SourceGenerators | 2.6.1 (installed) | `[Reactive]` notifying properties | Use for new editable profile manager ViewModels and dialog state. [VERIFIED: Directory.Packages.props; AGENTS.md] |
| Microsoft.Extensions.DependencyInjection | 10.0.7 (installed) | Service registration for new profile store/validator/dialog adapters | Register Core services and App adapters in `AppBootstrapper`. [VERIFIED: Directory.Packages.props; AppBootstrapper usage in ARCHITECTURE.md] |
| Avalonia.Headless.XUnit | 12.0.1 (installed) | Headless UI smoke/compiled-binding tests | Use for profile manager UI smoke tests if new AXAML is added. [VERIFIED: Directory.Packages.props; TESTING.md] |
| xunit.v3 | 3.2.2 (latest exact NuGet search returned 3.2.2) | Unit/integration tests | Required test framework; use `[Fact]`, `[Theory]`, and `[AvaloniaFact]` where needed. [VERIFIED: dotnet package search; TESTING.md] |
| FluentAssertions | 8.9.0 (latest exact NuGet search returned 8.9.0) | Assertions | Project standard; avoid bare `Assert.*` in new tests. [VERIFIED: dotnet package search; TESTING.md] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom domain validator over `System.Text.Json` | JSON Schema package such as NJsonSchema | Do not add: schema shape is small, validation is domain-specific (case-insensitive identity, duplicate slider names, bundled-shadowing, broad numeric policy), and no schema package exists in project dependencies. [VERIFIED: Directory.Packages.props; CONTEXT.md D-03,D-06,D-07] |
| Existing `SliderProfileJsonService` only | Ad hoc `JsonDocument` parsing in each workflow | Do not do this: current service reads tables but lacks validation/result diagnostics, metadata, export support, and conflict identity. Wrap/extend centrally instead. [VERIFIED: SliderProfileJsonService.cs] |
| Sidecar-only custom profiles | Embedded project copies only | Use both: user explicitly locked local reusable profiles plus embedded referenced profiles for sharing. [VERIFIED: CONTEXT.md D-04,D-13,D-17] |
| Filename-based identity | Display name exact match | Do not use filename identity; imported profiles resolve unresolved references only by exact display-name match case-insensitively. [VERIFIED: CONTEXT.md D-12] |

**Installation:** no new packages are recommended. [VERIFIED: Directory.Packages.props; dotnet list package]

```powershell
# Keep existing centrally-managed package set.
dotnet restore BS2BG.sln
```

**Version verification:** `dotnet list BS2BG.sln package` confirmed installed packages, and `dotnet package search --exact-match --format json` confirmed latest exact versions for Avalonia 12.0.1, System.Text.Json 10.0.7, ReactiveUI.Avalonia 12.0.1, xunit.v3 3.2.2, and FluentAssertions 8.9.0. [VERIFIED: local command output]

## Architecture Patterns

### System Architecture Diagram

```text
Profile JSON import/copy/edit/export action
        |
        v
App service boundary (StorageProvider / AppData path / conflict dialog)
        |
        v
Core ProfileDefinitionService
  ├─ Parse DTO with System.Text.Json token checks
  ├─ Validate metadata + Defaults + Multipliers + Inverted
  ├─ Normalize display-name identity case-insensitively
  └─ Emit ProfileValidationResult diagnostics
        |
        +--> REJECT: App shows strict validation diagnostics; catalog unchanged
        |
        v
UserProfileStore (AppData custom JSON files; atomic writes)
        |
        v
Catalog composition (bundled read-only + custom validated profiles)
        |
        v
Templates/Profile Diagnostics/Generation
  ├─ Known profile: use exact profile tables
  └─ Missing profile: preserve saved name + visible neutral fallback
        |
        v
Project save/share
  ├─ Keep legacy SliderPresets/CustomMorphTargets/MorphedNPCs/isUUNP/Profile fields
  └─ Add optional referenced-custom-profiles section
        |
        v
Project open on another machine
  ├─ Embedded profile exact-name match resolves missing reference
  ├─ Conflict with local profile => App prompt import/replace/rename/keep local
  └─ No resolution => open project with diagnostic + fallback visible
```

[VERIFIED: CONTEXT.md D-01-D-17; ProjectFileService.cs; ProfileDiagnosticsService.cs]

### Recommended Project Structure

```text
src/
├── BS2BG.Core/
│   ├── Generation/
│   │   ├── SliderProfileJsonService.cs        # extend/wrap for export-compatible profile JSON
│   │   ├── TemplateProfileCatalog.cs          # add source/type metadata or companion lookup
│   │   └── ProfileDefinitionService.cs        # new parse/validate/normalize service
│   ├── Diagnostics/
│   │   └── ProfileRecoveryDiagnosticsService.cs # missing/conflict/recovery findings
│   ├── Models/
│   │   └── CustomProfileDefinition.cs         # immutable profile identity + SliderProfile + source
│   └── Serialization/
│       └── ProjectFileService.cs              # optional embedded profiles section
├── BS2BG.App/
│   ├── Services/
│   │   ├── UserProfileStore.cs                # AppData profile folder, atomic writes
│   │   ├── TemplateProfileCatalogFactory.cs   # bundled + custom composition
│   │   └── ProfileManagementDialogService.cs  # import/conflict/save prompts
│   ├── ViewModels/
│   │   ├── ProfileManagerViewModel.cs         # list/import/copy/export/delete/edit commands
│   │   └── ProfileEditorViewModel.cs          # metadata/table editing with validation result display
│   └── Views/
│       └── ProfileManagerDialog.axaml         # or integrated profile-management panel
└── tests/
    └── BS2BG.Tests/
        ├── ProfileDefinitionServiceTests.cs
        ├── UserProfileStoreTests.cs
        ├── ProjectFileServiceCustomProfileTests.cs
        └── ProfileManagerViewModelTests.cs
```

[VERIFIED: ARCHITECTURE.md project layers; CONTEXT.md integration points]

### Pattern 1: Result-first profile validation
**What:** A Core validator returns an immutable result with either a normalized profile definition or diagnostics; normal malformed user JSON should not throw past the workflow boundary. [VERIFIED: CONVENTIONS.md error handling; BodySlideXmlParser/NpcTextParser patterns]

**When to use:** Importing, editing, saving, exporting, discovering local files, and loading embedded profiles. [VERIFIED: CONTEXT.md EXT scope]

**Example:**
```csharp
// Source: existing parser/result pattern in CONVENTIONS.md and System.Text.Json required-properties docs.
public sealed class ProfileValidationResult
{
    public ProfileValidationResult(CustomProfileDefinition? profile, IEnumerable<ProfileValidationDiagnostic> diagnostics)
    {
        Profile = profile;
        Diagnostics = (diagnostics ?? throw new ArgumentNullException(nameof(diagnostics))).ToArray();
    }

    public CustomProfileDefinition? Profile { get; }
    public IReadOnlyList<ProfileValidationDiagnostic> Diagnostics { get; }
    public bool IsValid => Profile is not null && Diagnostics.All(d => d.Severity != DiagnosticSeverity.Blocker);
}
```

### Pattern 2: Trust-domain-tagged catalog entries
**What:** Keep bundled and custom profile identities in one catalog for lookup, but track source (`Bundled`, `LocalCustom`, `EmbeddedProject`) and editability outside formatter logic. [VERIFIED: CONTEXT.md D-01-D-04,D-13-D-16; TemplateProfileCatalog.cs]

**When to use:** Profile selector display, copy/edit availability, duplicate-name rejection, and project embedding selection. [VERIFIED: CONTEXT.md D-02,D-03,D-15]

**Example:**
```csharp
// Source: TemplateProfileCatalog currently performs case-insensitive name matching.
public enum ProfileSourceKind
{
    Bundled,
    LocalCustom,
    EmbeddedProject
}

public sealed record ProfileCatalogEntry(
    string Name,
    SliderProfile SliderProfile,
    ProfileSourceKind SourceKind,
    string? FilePath)
{
    public bool IsEditable => SourceKind == ProfileSourceKind.LocalCustom;
}
```

### Pattern 3: Optional top-level project extension section
**What:** Add a nullable top-level DTO property after legacy fields, with `[JsonPropertyName]` and `[JsonPropertyOrder]`; older readers that ignore unknown fields can still read legacy fields, while current readers can hydrate embedded custom profiles. [VERIFIED: CONTEXT.md D-14; Microsoft System.Text.Json property-name/order docs; ProjectFileService.cs]

**When to use:** Saving/sharing projects that reference custom profiles. [VERIFIED: EXT-03, EXT-05]

**Example:**
```csharp
// Source: System.Text.Json property customization/order docs and ProjectFileService.cs DTO pattern.
private sealed class ProjectFileDto
{
    [JsonPropertyName("SliderPresets")]
    [JsonPropertyOrder(0)]
    public Dictionary<string, SliderPresetDto>? SliderPresets { get; set; }

    [JsonPropertyName("CustomMorphTargets")]
    [JsonPropertyOrder(1)]
    public Dictionary<string, MorphTargetDto>? CustomMorphTargets { get; set; }

    [JsonPropertyName("MorphedNPCs")]
    [JsonPropertyOrder(2)]
    public NamedNpcObjectList? MorphedNpCs { get; set; }

    [JsonPropertyName("CustomProfiles")]
    [JsonPropertyOrder(3)]
    public List<EmbeddedProfileDto>? CustomProfiles { get; set; }
}
```

### Pattern 4: AppData profile store with explicit import/export actions
**What:** Use a user profiles folder under `Environment.SpecialFolder.ApplicationData` following the existing preference path style, and write profile JSON through atomic file replacement. [VERIFIED: UserPreferencesService.cs; Environment.SpecialFolder docs; AtomicFileWriter.cs]

**When to use:** Local custom profile discovery, copy-bundled-as-custom, import, save edits, and selected-profile export. [VERIFIED: CONTEXT.md D-01,D-04,D-17]

**Example:**
```csharp
// Source: existing UserPreferencesService path pattern and Environment.SpecialFolder.ApplicationData docs.
var root = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
var profilesDirectory = Path.Combine(root, "jBS2BG", "profiles");
Directory.CreateDirectory(profilesDirectory);
```

### Pattern 5: Reactive edit commands gated by validation state
**What:** Use `[Reactive]` properties for editable fields, compute validation summaries through observable derived state, and expose save/import/export via `ReactiveCommand` with observable `canExecute`. [VERIFIED: openspec reactive-mvvm-conventions; Avalonia ReactiveCommand docs]

**When to use:** Profile manager/editor ViewModels and conflict dialogs. [VERIFIED: AGENTS.md ReactiveUI rules]

**Example:**
```csharp
// Source: ReactiveUI/Avalonia ReactiveCommand docs and project reactive-mvvm-conventions.
var canSave = this.WhenAnyValue(x => x.ValidationResult, result => result?.IsValid == true);
SaveCommand = ReactiveCommand.CreateFromTask(
    (CancellationToken cancellationToken) => SaveAsync(cancellationToken),
    canSave);
```

### Anti-Patterns to Avoid
- **Shadowing bundled profiles with custom files:** reject case-insensitive duplicate display names across all sources instead. [VERIFIED: CONTEXT.md D-03]
- **Using filename stems as profile identity:** only exact display-name matches resolve missing references. [VERIFIED: CONTEXT.md D-12]
- **Partial acceptance of broken JSON:** reject before save/import/catalog inclusion; do not drop malformed tables and continue. [VERIFIED: CONTEXT.md D-06]
- **Clamping floats to “normal” body ranges:** broad numeric values are allowed; only malformed/nonnumeric values are invalid. [VERIFIED: CONTEXT.md D-07]
- **Blocking project open for missing custom profiles:** open with diagnostics and visible fallback. [VERIFIED: CONTEXT.md D-09,D-10]
- **Mutating diagnostics by default:** diagnostics remain read-only unless the user invokes explicit recovery/import/remap actions. [VERIFIED: Phase 3 CONTEXT.md D-04; Phase 4 CONTEXT.md D-11]
- **Writing profile files directly from ViewModels:** use App service + Core validator + atomic writer. [VERIFIED: ARCHITECTURE.md anti-pattern]
- **Editing sacred formatter/export files to support profile extensibility:** profile management should feed existing `SliderProfile` tables, not alter slider math or output formatting. [VERIFIED: AGENTS.md sacred files]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON parsing/token validation | String splitting or regex JSON parsing | `System.Text.Json` `JsonDocument`/DTOs plus domain validator | JSON has escaped strings, duplicate-looking names, number token rules, comments/trailing comma options, and property order concerns. [VERIFIED: Microsoft System.Text.Json docs; SliderProfileJsonService.cs] |
| Backward-compatible JSON property names/order | Manual string concatenation | `[JsonPropertyName]`, `[JsonPropertyOrder]`, existing `JsonSerializerOptions` | Project serialization already uses explicit DTOs and order attributes; manual JSON risks compatibility drift. [VERIFIED: ProjectFileService.cs; Microsoft docs] |
| Overflow/unknown JSON preservation if ever needed | Custom catch-all parser | `[JsonExtensionData]` on DTOs | System.Text.Json supports extension data round-trip without emitting the extension property name. [CITED: https://learn.microsoft.com/dotnet/standard/serialization/system-text-json/handle-overflow] |
| File picker/import/export dialogs | Old `OpenFileDialog`/`SaveFileDialog` APIs or ViewModel UI calls | Avalonia `StorageProvider` behind App service interfaces | Avalonia 12 StorageProvider is the current file/folder picker surface; existing project uses App service boundaries. [CITED: https://docs.avaloniaui.net/docs/services/storage/storage-provider; VERIFIED: ARCHITECTURE.md] |
| Atomic profile/project writes | Direct `File.WriteAllText` from ViewModels | `AtomicFileWriter` or a Core wrapper over it | Direct writes can corrupt local profiles/projects on failure; existing helper handles temp files and rollback ledgers. [VERIFIED: AtomicFileWriter.cs] |
| Duplicate profile identity logic | Per-screen string comparisons | Central case-insensitive identity service/catalog validation | Duplicate-name rules must be consistent across bundled, local, embedded, import, and recovery flows. [VERIFIED: CONTEXT.md D-03,D-12; TemplateProfileCatalog.cs] |
| Reactive command infrastructure | New RelayCommand/AsyncRelayCommand | `ReactiveCommand.Create*` / `CreateFromTask` | Project forbids custom command types and tests initialize ReactiveUI. [VERIFIED: AGENTS.md; openspec reactive-mvvm-conventions] |
| JSON schema package or generated schema | External schema-first validator | Domain-specific validator over `System.Text.Json` | Validation depends on BS2BG semantics (broad numeric floats, blank profile allowed, name conflicts) that generic schema cannot decide alone. [VERIFIED: CONTEXT.md D-06-D-08] |

**Key insight:** The hard part is preserving identity/trust boundaries and compatibility semantics, not parsing JSON or drawing an editor. A generic custom implementation can easily corrupt bundled trust, silently change fallback behavior, or embed incompatible project JSON. [VERIFIED: CONTEXT.md; AGENTS.md]

## Common Pitfalls

### Pitfall 1: Treating profile filename as identity
**What goes wrong:** A file named `Community-CBBE.json` resolves a project reference to `Community CBBE` even if the internal `Name` is different. [VERIFIED: CONTEXT.md D-12]  
**Why it happens:** File import workflows often default identity from path for convenience. [ASSUMED]  
**How to avoid:** Use internal display-name metadata as identity and reject missing/ambiguous display names; filenames are storage convention only. [VERIFIED: CONTEXT.md D-12]  
**Warning signs:** Tests pass for imported file path but fail when internal profile `Name` differs from filename. [ASSUMED]

### Pitfall 2: Letting custom profiles shadow bundled names
**What goes wrong:** A local `Skyrim CBBE` custom file silently changes generation math for old projects. [VERIFIED: CONTEXT.md D-02,D-03]  
**Why it happens:** Catalog lookup is already case-insensitive and returns the first matching profile. [VERIFIED: TemplateProfileCatalog.cs]  
**How to avoid:** Validate the combined bundled+custom catalog before publishing it and reject duplicate display names case-insensitively. [VERIFIED: CONTEXT.md D-03]  
**Warning signs:** Catalog composition order determines output math. [VERIFIED: TemplateProfileCatalog.cs]

### Pitfall 3: Strict validation accidentally forbids accepted modder flexibility
**What goes wrong:** Blank custom profiles or unusual negative/large multiplier/default values are rejected despite locked decisions. [VERIFIED: CONTEXT.md D-07,D-08]  
**Why it happens:** Developers conflate “strict schema” with “clamp to common CBBE ranges.” [ASSUMED]  
**How to avoid:** Strictly validate shape, names, numeric token type, and finite numeric values; do not range-clamp ordinary finite floats. [VERIFIED: CONTEXT.md D-06,D-07]  
**Warning signs:** Tests assert 0-1-only defaults or positive-only multipliers. [ASSUMED]

### Pitfall 4: Optional embedded project section breaks old readers
**What goes wrong:** Reordering or renaming legacy fields causes older `.jbs2bg` consumers to fail or lose data. [VERIFIED: CONTEXT.md D-14; ProjectFileService.cs]  
**Why it happens:** Serializer DTO refactors can accidentally rename `MorphedNPCs`, `isUUNP`, or `Profile`. [VERIFIED: ProjectFileService.cs]  
**How to avoid:** Add only an optional top-level section after existing root fields and extend existing round-trip tests. [VERIFIED: Microsoft JsonPropertyOrder docs; ProjectFileServiceTests.cs]  
**Warning signs:** Existing `v1-stale-project.expected.jbs2bg` compatibility test changes without a Phase 4-specific expected reason. [VERIFIED: ProjectFileServiceTests.cs]

### Pitfall 5: Making unresolved profiles noisy or blocking
**What goes wrong:** Phase 4 recovery turns the Phase 1 neutral fallback into a warning/blocker during normal generation/export. [VERIFIED: Phase 1 CONTEXT.md D-05-D-12; Phase 4 CONTEXT.md D-10]  
**Why it happens:** Missing custom profiles feel like validation errors, but the project has a locked non-blocking fallback contract. [VERIFIED: CONTEXT.md D-09,D-10]  
**How to avoid:** Present recovery diagnostics and actions in diagnostics/profile UI; keep generation/export fallback visible and neutral until explicitly resolved. [VERIFIED: ProfileDiagnosticsService.cs; CONTEXT.md D-10,D-11]  
**Warning signs:** UI text uses “mismatch”, “error”, “experimental”, or blocks export for unresolved profile only. [VERIFIED: ProfileDiagnosticsServiceTests.cs]

### Pitfall 6: Importing embedded profiles silently over local profiles
**What goes wrong:** Opening a shared project changes a user’s installed custom profile or ignores a project-specific profile without notice. [VERIFIED: CONTEXT.md D-16]  
**Why it happens:** Automatic “last write wins” simplifies load code. [ASSUMED]  
**How to avoid:** Detect same-name different-definition conflicts and ask import/replace/rename/keep-local before installing embedded data. [VERIFIED: CONTEXT.md D-16]  
**Warning signs:** Load tests show same-name embedded/local conflict resolves with no recorded user choice. [ASSUMED]

### Pitfall 7: Editing ViewModels bypass undo/dirty expectations
**What goes wrong:** Profile changes alter project state without dirty state or undo coverage for profile operations. [VERIFIED: Phase 2 CONTEXT.md D-14; ARCHITECTURE.md anti-pattern]  
**Why it happens:** Profile metadata feels like settings instead of project-affecting workflow state. [ASSUMED]  
**How to avoid:** For project preset profile remap/adoption actions, record undo snapshots; for local profile file edits, surface save/discard state separately. [VERIFIED: Phase 2 CONTEXT.md D-13-D-16]

## Code Examples

Verified patterns from official/project sources:

### Strict profile parse entry point
```csharp
// Source: System.Text.Json JsonDocument pattern in SliderProfileJsonService.cs plus CONTEXT strict-validation rules.
public ProfileValidationResult ValidateProfileJson(string json, ProfileValidationContext context)
{
    if (json is null) throw new ArgumentNullException(nameof(json));
    if (context is null) throw new ArgumentNullException(nameof(context));

    try
    {
        using var document = JsonDocument.Parse(json);
        return ValidateRoot(document.RootElement, context);
    }
    catch (JsonException exception)
    {
        return ProfileValidationResult.Invalid(ProfileValidationDiagnostic.Blocker(
            "Profile JSON is malformed: " + exception.Message));
    }
}
```

### Case-insensitive duplicate identity check
```csharp
// Source: TemplateProfileCatalog.cs uses StringComparison.OrdinalIgnoreCase for profile matching.
var knownNames = new HashSet<string>(
    bundledProfiles.Select(profile => profile.Name),
    StringComparer.OrdinalIgnoreCase);

if (!knownNames.Add(candidateName))
{
    diagnostics.Add(ProfileValidationDiagnostic.Blocker(
        "Profile name conflicts with an existing bundled or custom profile."));
}
```

### Preserve legacy fields and append optional custom profiles
```csharp
// Source: ProjectFileService.cs and System.Text.Json JsonPropertyOrder docs.
[JsonPropertyName("CustomProfiles")]
[JsonPropertyOrder(3)]
public List<EmbeddedProfileDto>? CustomProfiles { get; set; }
```

### Use StorageProvider through an App service, not a ViewModel
```csharp
// Source: Avalonia StorageProvider docs; existing WindowFileDialogService pattern.
var files = await topLevel.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
{
    Title = "Import BS2BG profile JSON",
    AllowMultiple = true,
    FileTypeFilter = new[]
    {
        new FilePickerFileType("BS2BG profile JSON") { Patterns = new[] { "*.json" } }
    }
});
```

### User profile folder convention
```csharp
// Source: UserPreferencesService.cs and Environment.SpecialFolder.ApplicationData docs.
public static string GetDefaultProfileDirectory()
{
    var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
    return Path.Combine(appData, "jBS2BG", "profiles");
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Avalonia `OpenFileDialog`/`SaveFileDialog` muscle memory | `TopLevel.StorageProvider.OpenFilePickerAsync` / `SaveFilePickerAsync` / `OpenFolderPickerAsync` | Avalonia 11/12 era; current docs expose StorageProvider | Profile import/export must use existing App storage services over StorageProvider. [CITED: https://docs.avaloniaui.net/docs/services/storage/storage-provider] |
| Reflection bindings accepted by default | Project-wide compiled bindings with `x:DataType` required | Current project sets `AvaloniaUseCompiledBindingsByDefault`; Avalonia docs document project-wide setting | New profile manager AXAML must include `x:DataType` on root and data templates. [VERIFIED: AGENTS.md; CITED: https://docs.avaloniaui.net/docs/xaml/compilation] |
| `System.Text.Json` treated as minimal serializer only | .NET 10-era `System.Text.Json` supports required properties, property ordering, extension data, and DOM/token validation | Required properties documented for .NET 7+; constructor required option .NET 9+ | No JSON schema/Newtonsoft dependency is needed for this phase’s simple but domain-strict shape. [CITED: Microsoft System.Text.Json docs] |
| Two-profile `isUUNP` model | Named profile selector plus compatibility `isUUNP` field | Implemented before Phase 4 per current code/specs | Custom profiles should extend named-profile behavior, not resurrect UUNP booleans. [VERIFIED: ProjectFileService.cs; openspec project-roundtrip] |
| Modal-only diagnostic/fix flows | First-class Diagnostics tab with read-only baseline and explicit actions | Phase 3 decisions and implementation | Missing custom-profile recovery should appear as diagnostics/profile actions, not hidden fallback or auto-fix. [VERIFIED: Phase 3 CONTEXT.md; ProfileDiagnosticsService.cs] |

**Deprecated/outdated:**
- `Avalonia.Diagnostics`: do not add; project docs indicate Avalonia Plus diagnostics support if needed, but Phase 4 does not require devtools work. [VERIFIED: AGENTS.md; Avalonia expert rules]
- `RelayCommand`/`AsyncRelayCommand`: retired and forbidden for new App work. [VERIFIED: AGENTS.md; openspec reactive-mvvm-conventions]
- WPF XAML concepts like `DataTrigger`, `DependencyProperty`, and `Visibility` enum: avoid in Avalonia AXAML. [CITED: Avalonia expert rules]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Developers often default identity from filenames for import convenience. | Common Pitfalls | Low; mitigation is locked by D-12 regardless. |
| A2 | Tests should flag filename/internal-name mismatches and silent conflict resolution. | Common Pitfalls | Low; if planner omits these tests, regressions become easier. |
| A3 | Developers may conflate strict schema validation with range clamping. | Common Pitfalls | Medium; wrong implementation would violate D-07. |
| A4 | Last-write-wins conflict handling is a common simplification. | Common Pitfalls | Medium; wrong implementation would violate D-16 and damage local profiles. |
| A5 | Profile metadata can be mistaken for non-project settings. | Common Pitfalls | Medium; wrong implementation can bypass undo/dirty contracts. |

## Open Questions

1. **Exact `.jbs2bg` embedded section name and DTO shape**
   - What we know: Optional custom-profiles section is locked, legacy fields must remain intact, and exact name/shape is at agent discretion. [VERIFIED: CONTEXT.md D-13,D-14]
   - What's unclear: Whether to call it `CustomProfiles`, `EmbeddedProfiles`, or a versioned object like `ProfileDefinitions`. [VERIFIED: CONTEXT.md discretion]
   - Recommendation: Use `CustomProfiles` as a simple top-level array ordered after `MorphedNPCs`; include `Name`, `Game`, `Defaults`, `Multipliers`, and `Inverted` with no unrelated local profiles. [VERIFIED: PRD profile shape; CONTEXT.md D-15]

2. **Exact user profiles folder path**
   - What we know: Editable profiles must be local user data; existing preferences use `%APPDATA%/jBS2BG/user-preferences.json`. [VERIFIED: CONTEXT.md D-01; UserPreferencesService.cs]
   - What's unclear: Whether folder name should be `profiles`, `custom-profiles`, or include profile version. [VERIFIED: CONTEXT.md discretion]
   - Recommendation: Use `%APPDATA%/jBS2BG/profiles/` for consistency with existing preference storage and keep filenames sanitized from display names plus disambiguating suffix if needed. [VERIFIED: UserPreferencesService.cs; Environment.SpecialFolder docs]

3. **Profile conflict equality definition**
   - What we know: Same-name local/embedded conflicts must prompt; neither side wins silently. [VERIFIED: CONTEXT.md D-16]
   - What's unclear: Whether textual JSON differences such as property order count as different. [ASSUMED]
   - Recommendation: Compare normalized validated profile definitions (metadata + table entries case-sensitive slider names? preserve names exactly but duplicate checks case-insensitive) rather than raw JSON text. [VERIFIED: CONTEXT.md D-06,D-16]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| .NET SDK | Build/test/package Phase 4 | ✓ | 10.0.203 | Blocking if absent. [VERIFIED: dotnet --version] |
| NuGet package restore | Existing dependencies | ✓ | via `dotnet restore` / nuget.org search | Use existing lock/central versions; no new packages recommended. [VERIFIED: dotnet list package; dotnet package search] |
| Avalonia StorageProvider | Profile import/export UI | ✓ via Avalonia package | 12.0.1 | App service can be faked in tests. [VERIFIED: Directory.Packages.props; Avalonia docs] |
| Local filesystem/AppData | Custom profile store | ✓ | Windows current environment | If unavailable/unauthorized, report profile-store diagnostics and keep bundled catalog usable. [VERIFIED: UserPreferencesService.cs failure pattern] |

**Missing dependencies with no fallback:** none detected. [VERIFIED: local command output]

**Missing dependencies with fallback:** local custom profile folder write failures should fall back to bundled-only catalog plus user-visible import/save failure diagnostics. [VERIFIED: UserPreferencesService.cs; CONTEXT.md D-01-D-04]

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | xUnit v3 3.2.2 + FluentAssertions 8.9.0 + Avalonia.Headless.XUnit 12.0.1 [VERIFIED: dotnet list package; TESTING.md] |
| Config file | `tests/BS2BG.Tests/BS2BG.Tests.csproj`; no separate runsettings detected. [VERIFIED: BS2BG.Tests.csproj; TESTING.md] |
| Quick run command | `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests` [VERIFIED: TESTING.md command pattern] |
| Full suite command | `dotnet test` [VERIFIED: AGENTS.md; TESTING.md] |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EXT-01 | Import/copy/export/validate local JSON profiles without changing bundled profiles | unit + App service | `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests` and `~UserProfileStoreTests` | ❌ Wave 0 |
| EXT-02 | Edit metadata/defaults/multipliers/inverted with strict validation and allowed blank/broad numeric profiles | unit + ViewModel | `dotnet test --filter FullyQualifiedName~ProfileEditorViewModelTests` | ❌ Wave 0 |
| EXT-03 | Save project with referenced custom profiles while keeping `isUUNP` and `Profile` compatibility | unit/integration | `dotnet test --filter FullyQualifiedName~ProjectFileServiceCustomProfileTests` | ❌ Wave 0 (extend existing `ProjectFileServiceTests.cs`) |
| EXT-04 | Missing custom profile opens with diagnostics/recovery actions and no silent fallback | unit + ViewModel | `dotnet test --filter FullyQualifiedName~ProfileRecoveryDiagnosticsServiceTests` | ❌ Wave 0 (extend existing diagnostics tests) |
| EXT-05 | Embedded/shared profile copies load on another machine and conflicts prompt explicitly | unit + ViewModel | `dotnet test --filter FullyQualifiedName~CustomProfileSharingTests` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `dotnet test --filter FullyQualifiedName~ProfileDefinitionServiceTests` or the touched focused test class. [VERIFIED: TESTING.md]
- **Per wave merge:** `dotnet test --filter "FullyQualifiedName~Profile|FullyQualifiedName~ProjectFileService|FullyQualifiedName~TemplatesViewModel|FullyQualifiedName~DiagnosticsViewModel"` [VERIFIED: existing test file names]
- **Phase gate:** `dotnet test` full suite green before `/gsd-verify-work`. [VERIFIED: AGENTS.md]

### Wave 0 Gaps
- [ ] `tests/BS2BG.Tests/ProfileDefinitionServiceTests.cs` — covers EXT-01/EXT-02 parse/validate/export rules. [VERIFIED: test glob absent]
- [ ] `tests/BS2BG.Tests/UserProfileStoreTests.cs` — covers AppData store, atomic write, discovery, duplicate filename conventions. [VERIFIED: test glob absent]
- [ ] Extend `tests/BS2BG.Tests/ProjectFileServiceTests.cs` or add `ProjectFileServiceCustomProfileTests.cs` — covers EXT-03/EXT-05 embedded section and legacy fields. [VERIFIED: existing ProjectFileServiceTests.cs]
- [ ] Extend `tests/BS2BG.Tests/ProfileDiagnosticsServiceTests.cs` or add `ProfileRecoveryDiagnosticsServiceTests.cs` — covers EXT-04 exact-match recovery and neutral unresolved state. [VERIFIED: existing ProfileDiagnosticsServiceTests.cs]
- [ ] Add `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs` / `ProfileEditorViewModelTests.cs` if new ViewModels are created. [VERIFIED: current glob absent]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no | Offline desktop app; no auth provider or accounts detected. [VERIFIED: INTEGRATIONS.md] |
| V3 Session Management | no | No sessions/cookies/tokens detected. [VERIFIED: INTEGRATIONS.md] |
| V4 Access Control | yes (local trust boundary) | Treat bundled install profiles as read-only and custom profiles as user data; never let custom files shadow bundled names. [VERIFIED: CONTEXT.md D-01-D-03] |
| V5 Input Validation | yes | Core `System.Text.Json` parser + strict profile validation result; reject malformed/ambiguous profile data before catalog inclusion. [VERIFIED: CONTEXT.md D-06; Microsoft docs] |
| V6 Cryptography | no | No cryptographic feature in Phase 4; do not hand-roll signing/hashing for profile trust. [VERIFIED: Phase 5 AUTO-04 deferred; REQUIREMENTS.md] |
| V8 Data Protection | yes (local files) | Atomic writes, user-local storage, no telemetry/cloud sync. [VERIFIED: AtomicFileWriter.cs; REQUIREMENTS.md out of scope] |
| V12 File and Resources | yes | Sanitize filenames, use explicit file pickers, avoid overwriting bundled files, and handle unauthorized/IO failures with diagnostics. [VERIFIED: Avalonia StorageProvider docs; UserPreferencesService.cs; CONTEXT.md D-01,D-02] |

### Known Threat Patterns for local profile management

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed or adversarial JSON causing partial catalog inclusion | Tampering | Parse/validate entirely before adding to catalog; invalid profiles are rejected with diagnostics. [VERIFIED: CONTEXT.md D-06] |
| Custom profile shadows bundled `Skyrim CBBE` | Spoofing/Tampering | Case-insensitive duplicate-name rejection across bundled and custom sources. [VERIFIED: CONTEXT.md D-03] |
| Path traversal or invalid filenames on profile export | Tampering | Use StorageProvider-selected target path and sanitized suggested filenames; profile identity remains internal `Name`. [CITED: Avalonia StorageProvider docs; VERIFIED: CONTEXT.md D-12] |
| Project embedded profile overwrites local profile silently | Tampering | Detect conflict and prompt import/replace/rename/keep-local. [VERIFIED: CONTEXT.md D-16] |
| Partial file write corrupts custom profile or project | Tampering/DoS | Use `AtomicFileWriter`/batch semantics and report IO failures. [VERIFIED: AtomicFileWriter.cs] |
| Silent fallback hides unresolved profile | Information disclosure/Integrity (user trust) | Existing neutral fallback diagnostics plus explicit recovery actions. [VERIFIED: ProfileDiagnosticsService.cs; CONTEXT.md D-09-D-11] |

## Sources

### Primary (HIGH confidence)
- `J:\jBS2BG\AGENTS.md` — project stack, sacred files, ReactiveUI/Avalonia conventions, tests, environment. [VERIFIED: file read]
- `.planning/phases/04-profile-extensibility-and-controlled-customization/04-CONTEXT.md` — locked Phase 4 decisions D-01 through D-17 and discretion scope. [VERIFIED: file read]
- `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/STATE.md`, `.planning/PROJECT.md` — phase requirements and accumulated decisions. [VERIFIED: file read]
- Current code: `SliderProfileJsonService.cs`, `TemplateProfileCatalog.cs`, `TemplateProfileCatalogFactory.cs`, `ProjectFileService.cs`, `ProfileDiagnosticsService.cs`, `UserPreferencesService.cs`, `AtomicFileWriter.cs`. [VERIFIED: file read]
- OpenSpec specs: `reactive-mvvm-conventions`, `project-roundtrip`, `template-generation-flow`. [VERIFIED: file read]
- Microsoft Learn: System.Text.Json required properties, property names/order, extension data; Environment.SpecialFolder. [CITED: learn.microsoft.com]
- Avalonia docs: StorageProvider, file dialogs, compiled bindings/x:DataType, ReactiveCommand. [CITED: docs.avaloniaui.net; GitHub avalonia-docs]
- Local package verification: `dotnet list BS2BG.sln package`, `dotnet package search --exact-match --format json`. [VERIFIED: local command output]

### Secondary (MEDIUM confidence)
- Avalonia expert rules output — useful current-Avalonia pitfalls, but project-specific ReactiveUI decisions override its CommunityToolkit recommendation. [VERIFIED: avalonia-docs tool; CONFLICT RESOLVED by AGENTS.md]

### Tertiary (LOW confidence)
- Assumptions log A1-A5 — implementation-behavior cautions derived from common engineering patterns, not externally verified in this session. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — project dependencies and latest exact NuGet versions verified locally. [VERIFIED: dotnet list; dotnet package search]
- Architecture: HIGH — existing code seams and locked CONTEXT.md decisions directly define boundaries. [VERIFIED: code reads; CONTEXT.md]
- Pitfalls: MEDIUM-HIGH — critical pitfalls are locked by context/code; some behavioral warnings are assumptions and logged. [VERIFIED/ASSUMED as tagged]
- Validation: HIGH — test framework and existing tests verified; Phase 4-specific test files are identified as Wave 0 gaps. [VERIFIED: TESTING.md; glob]

**Research date:** 2026-04-27  
**Valid until:** 2026-05-27 for project-specific architecture; re-check NuGet/Avalonia docs after 30 days. [ASSUMED]
