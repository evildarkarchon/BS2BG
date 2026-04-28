# Phase 06: Compose Custom Profiles in Headless Generation - Research

**Researched:** 2026-04-27  
**Domain:** .NET 10 CLI automation, Core generation orchestration, request-scoped custom profile catalogs  
**Confidence:** HIGH

## User Constraints

No phase `CONTEXT.md` exists for Phase 06, so there are no discuss-phase locked decisions, discretion notes, or deferred ideas to copy. [VERIFIED: file read `.planning/phases/06-compose-custom-profiles-in-headless-generation/CONTEXT.md` returned not found]

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTO-01 | User can run a headless CLI generation path that uses the same Core services and output semantics as the GUI. [VERIFIED: `.planning/REQUIREMENTS.md:44-47`] | The existing CLI `generate` path already uses `HeadlessGenerationService`, Core writers, and `System.CommandLine`; Phase 06 should only change how the per-request catalog is composed so CLI generation matches GUI/bundle custom-profile semantics. [VERIFIED: `src/BS2BG.Cli/Program.cs:95-107`, `src/BS2BG.Core/Automation/HeadlessGenerationService.cs:11-21`] |
</phase_requirements>

## Summary

Phase 06 is a targeted Core/CLI correctness fix, not a new-library phase. [VERIFIED: phase description; `src/BS2BG.Cli/Program.cs:179-190`] The current CLI `generate` command constructs `HeadlessGenerationService` with `new TemplateProfileCatalogFactory().Create()`, which is a bundled catalog at CLI startup; `HeadlessGenerationService.Run` validates and generates directly with that same injected catalog. [VERIFIED: `src/BS2BG.Cli/Program.cs:179-190`, `src/BS2BG.Core/Automation/HeadlessGenerationService.cs:61-100`] That architecture means embedded custom profiles loaded into `ProjectModel.CustomProfiles` cannot participate in CLI generation unless a request-scoped catalog is built after project load. [VERIFIED: `src/BS2BG.Core/Serialization/ProjectFileService.cs:97-100`, `src/BS2BG.Core/Automation/HeadlessGenerationService.cs:40-61`]

The established pattern already exists in `PortableProjectBundleService`: build a request-scoped catalog from bundled entries plus the specific project/save-context custom profiles, validate with that catalog, then use the same catalog for `templates.ini` and BoS JSON output. [VERIFIED: `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:183-210`, `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:288-300`] Phase 06 should extract or mirror that catalog-composition rule in Core and route CLI `generate` through it; do not mutate the global catalog or infer unknown profiles. [VERIFIED: `openspec/specs/profile-extensibility/spec.md:48-61`, `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs:20-24`]

**Primary recommendation:** Add a Core request-scoped catalog composer used by both `HeadlessGenerationService` and `PortableProjectBundleService`, then regression-test CLI generation against an embedded custom profile whose output differs from bundled fallback. [VERIFIED: `PortableProjectBundleService.cs:288-300`, `tests/BS2BG.Tests/PortableBundleServiceTests.cs:467-484`]

## Project Constraints (from AGENTS.md)

- Keep `BS2BG.Core` pure domain/I/O with no Avalonia or platform dependencies. [VERIFIED: `AGENTS.md:19-26`]
- CLI/App generation must reuse Core services rather than duplicating output logic. [VERIFIED: `AGENTS.md:19-26`, `src/BS2BG.Cli/Program.cs:176-190`]
- Do not edit sacred formatter/export files without asking: `JavaFloatFormatting.cs`, `SliderMathFormatter.cs`, `BodyGenIniExportWriter.cs`, `BosJsonExportWriter.cs`, or expected fixtures. [VERIFIED: `AGENTS.md:51-58`]
- Preserve byte-identical output semantics: BodyGen INI uses CRLF, BoS JSON uses LF with no trailing newline, Java-like/minimal-json float formatting stays context-dependent, and rounding is half-up. [VERIFIED: `AGENTS.md:60-70`]
- Use xUnit v3 plus FluentAssertions style for new tests. [VERIFIED: `AGENTS.md:47-49`, `Directory.Packages.props:11-23`]
- Use PowerShell on Windows and do not redirect to `nul`. [VERIFIED: `AGENTS.md:121-124`]
- New or substantially rewritten C# methods should have concise XML documentation when non-trivial. [VERIFIED: `C:\Users\evild\.config\kilo\AGENTS.md`]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| CLI option parsing and process exit codes | CLI / Frontend process | Core automation contracts | `System.CommandLine` owns parse/help behavior, while `AutomationExitCode` remains the stable Core-facing contract. [VERIFIED: `src/BS2BG.Cli/Program.cs:23-147`, `src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs:16-26`] |
| Project loading | Core serialization | CLI boundary | `ProjectFileService` loads `.jbs2bg` and hydrates embedded `CustomProfiles`; CLI should only map expected load failures. [VERIFIED: `src/BS2BG.Core/Serialization/ProjectFileService.cs:56-103`, `src/BS2BG.Cli/Program.cs:116-127`] |
| Request-scoped profile catalog composition | Core generation/automation | CLI and bundle callers | Catalog composition depends on project data and must be identical for validation, templates, and BoS output. [VERIFIED: `PortableProjectBundleService.cs:183-210`, `PortableProjectBundleService.cs:288-300`] |
| Template and BoS generation | Core generation/export | CLI writes result text | `HeadlessGenerationService` delegates to `TemplateGenerationService`, `MorphGenerationService`, `BodyGenIniExportWriter`, and `BosJsonExportWriter`. [VERIFIED: `HeadlessGenerationService.cs:86-100`] |
| Regression validation | Tests | CLI process harness | Existing tests already exercise service-level and in-process `Program.Main` CLI behavior. [VERIFIED: `tests/BS2BG.Tests/CliGenerationTests.cs:142-415`] |

## Standard Stack

### Core
| Library / Component | Version | Purpose | Why Standard |
|---------------------|---------|---------|--------------|
| .NET SDK / target framework | SDK 10.0.203; CLI/tests target `net10.0` | Build/run/test the CLI and test project. | Existing project target and installed SDK align. [VERIFIED: `dotnet --version`; `src/BS2BG.Cli/BS2BG.Cli.csproj:3-6`] |
| System.CommandLine | 2.0.7 stable | CLI commands, required options, help, parse/invoke, exit codes. | Microsoft docs state it parses command-line input and displays help; project already uses it. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/] [VERIFIED: NuGet flat-container stable version; `Directory.Packages.props:19`] |
| BS2BG.Core automation/generation services | in-repo | Project load, validation, template/morph generation, BodyGen/BoS export. | AUTO-01 requires same Core services and output semantics as GUI. [VERIFIED: `.planning/REQUIREMENTS.md:44-47`, `HeadlessGenerationService.cs:11-21`] |
| System.Text.Json | 10.0.7 stable | `.jbs2bg` and profile JSON serialization/deserialization. | Microsoft docs identify `System.Text.Json` as the .NET JSON serialization namespace; project already uses cached options in `ProjectFileService`. [CITED: https://learn.microsoft.com/dotnet/standard/serialization/system-text-json/overview] [VERIFIED: `ProjectFileService.cs:1-5`, `Directory.Packages.props:21`] |

### Supporting
| Library / Component | Version | Purpose | When to Use |
|---------------------|---------|---------|-------------|
| xUnit v3 | 3.2.2 stable | Automated unit/CLI tests. | Use for new regression tests in `tests/BS2BG.Tests`. [VERIFIED: NuGet flat-container stable version; `Directory.Packages.props:23`] |
| FluentAssertions | 8.9.0 stable | Assertion style. | Use `Should()` style for new tests. [VERIFIED: NuGet flat-container stable version; `AGENTS.md:47-49`] |
| Microsoft.NET.Test.Sdk | project pins 18.4.0; current stable 18.5.0 | Test infrastructure for `dotnet test`. | Keep central version unless intentionally updating all test dependencies. [VERIFIED: `Directory.Packages.props:15`; NuGet flat-container stable version] |
| NuGet Central Package Management | enabled | Central package versions in `Directory.Packages.props`. | Microsoft docs say `ManagePackageVersionsCentrally=true` with `PackageVersion` entries centralizes versions. [CITED: https://learn.microsoft.com/nuget/consume-packages/central-package-management] [VERIFIED: `Directory.Packages.props:1-24`] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Extracted Core request catalog composer | Duplicate `PortableProjectBundleService.BuildRequestProfileCatalog` logic in `HeadlessGenerationService` | Duplication is faster but risks future drift between CLI generate and bundle output. [VERIFIED: `PortableProjectBundleService.cs:288-300`, `HeadlessGenerationService.cs:61-100`] |
| Core-based generation | Direct file/string generation in `Program.cs` | Existing test explicitly forbids direct output writes in CLI program text; direct generation would violate AUTO-01. [VERIFIED: `tests/BS2BG.Tests/CliGenerationTests.cs:280-294`] |
| Global mutable catalog refresh | Per-request catalog | Global mutation risks cross-request leakage and conflicts with immutable catalog semantics. [VERIFIED: `TemplateProfileCatalog.cs:20-24`, `TemplateProfileCatalog.cs:41-52`] |

**Installation:** No new package installation is recommended. [VERIFIED: `Directory.Packages.props:1-24`]

**Version verification:** NuGet flat-container checks on 2026-04-27 reported stable versions: `System.CommandLine` 2.0.7, `System.Text.Json` 10.0.7, `xunit.v3` 3.2.2, `FluentAssertions` 8.9.0, `Microsoft.NET.Test.Sdk` 18.5.0. [VERIFIED: NuGet flat-container API]

## Architecture Patterns

### System Architecture Diagram

```text
CLI args
  |
  v
System.CommandLine RootCommand / generate command
  |
  v
HeadlessGenerationRequest
  |
  v
HeadlessGenerationService.Run
  |
  +--> ProjectFileService.Load(project path)
  |       |
  |       v
  |   ProjectModel + embedded CustomProfiles
  |
  +--> RequestScopedProfileCatalogComposer
  |       |
  |       +--> bundled catalog entries
  |       +--> referenced embedded/custom profile definitions
  |       v
  |   TemplateProfileCatalog for this request only
  |
  +--> ProjectValidationService.Validate(project, request catalog)
  |       |
  |       +--> blockers => AutomationExitCode.ValidationBlocked
  |       v
  +--> overwrite preflight
  |       |
  |       +--> existing targets + no overwrite => AutomationExitCode.OverwriteRefused
  |       v
  +--> TemplateGenerationService / MorphGenerationService / BodyGenIniExportWriter
  +--> BosJsonExportWriter / BosJsonExportPlanner
          |
          v
      HeadlessGenerationResult + stable CLI stdout/stderr
```

Diagram reflects current service flow plus the required request-scoped catalog insertion point. [VERIFIED: `HeadlessGenerationService.cs:36-120`, `PortableProjectBundleService.cs:183-210`]

### Recommended Project Structure

```text
src/
├── BS2BG.Core/
│   ├── Automation/              # HeadlessGenerationService and request/result contracts
│   ├── Bundling/                # PortableProjectBundleService should reuse request catalog composer
│   ├── Generation/              # TemplateProfileCatalog and new catalog-composition helper belong here
│   └── Serialization/           # ProjectFileService loads embedded CustomProfiles
├── BS2BG.Cli/
│   └── Program.cs               # Parse options and compose Core services only
tests/
└── BS2BG.Tests/
    ├── CliGenerationTests.cs    # New CLI generate embedded-profile regression tests
    └── PortableBundleServiceTests.cs # Existing request-scoped bundle parity references
```

Structure follows existing solution layout. [VERIFIED: `AGENTS.md:15-33`]

### Pattern 1: Request-scoped catalog composer
**What:** Build a new `TemplateProfileCatalog` per project generation request from bundled entries plus the non-bundled definitions referenced by the project. [VERIFIED: `PortableProjectBundleService.cs:288-300`, `PortableProjectBundleService.cs:315-336`]  
**When to use:** Use before validation and before any generation/export that reads `SliderPreset.ProfileName`. [VERIFIED: `PortableProjectBundleService.cs:183-210`, `HeadlessGenerationService.cs:61-100`]  
**Example:**
```csharp
// Source: existing bundle pattern in src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:288-300.
var entries = bundledCatalog.Entries
    .Where(entry => entry.SourceKind == ProfileSourceKind.Bundled)
    .Concat(project.CustomProfiles
        .Where(profile => !IsBundledProfileName(profile.Name))
        .Select(profile => new ProfileCatalogEntry(
            profile.Name,
            new TemplateProfile(profile.Name, profile.SliderProfile),
            profile.SourceKind,
            profile.FilePath,
            false)));

var requestCatalog = new TemplateProfileCatalog(entries);
```

### Pattern 2: Validate and generate with the same catalog instance
**What:** The catalog used for `ProjectValidationService.Validate` must be the same catalog used by `GenerateTemplates` and `BosJsonExportWriter.Write`. [VERIFIED: `PortableProjectBundleService.cs:183-210`, `PortableProjectBundleService.cs:254-276`]  
**When to use:** Every headless generation request. [VERIFIED: phase success criteria]  
**Example:**
```csharp
// Source: existing HeadlessGenerationService orchestration in src/BS2BG.Core/Automation/HeadlessGenerationService.cs:61-100.
var requestCatalog = catalogComposer.BuildForProject(project, saveContext: null);
var validationReport = ProjectValidationService.Validate(project, requestCatalog);
if (validationReport.BlockerCount > 0) return ValidationBlocked(validationReport);

var templates = templateGenerationService.GenerateTemplates(
    project.SliderPresets,
    requestCatalog,
    request.OmitRedundantSliders);

var bosResult = bosJsonExportWriter.Write(
    request.OutputDirectory,
    project.SliderPresets,
    requestCatalog);
```

### Pattern 3: Regression by output divergence, not just success
**What:** Use an embedded custom profile with defaults/multipliers/inversions that produce bytes different from bundled fallback, then assert CLI generate output equals the request-scoped expected output and differs from bundled-only output. [VERIFIED: `tests/BS2BG.Tests/PortableBundleServiceTests.cs:447-484`]  
**When to use:** Phase 06 regression tests for AUTO-01. [VERIFIED: phase success criteria]  
**Example:**
```csharp
// Source: bundle regression pattern in tests/BS2BG.Tests/PortableBundleServiceTests.cs:467-484.
var embedded = CreateProfile(
    "Embedded Body",
    ProfileSourceKind.EmbeddedProject,
    filePath: null,
    CreateEmbeddedSliderProfile());
var project = CreateProjectUsingProfile("Embedded Body");
project.CustomProfiles.Add(embedded);

var expectedCatalog = CreateRequestScopedCatalog(embedded);
var bundledOnly = new TemplateGenerationService().GenerateTemplates(
    project.SliderPresets,
    CreateBundledOnlyCatalog(),
    omitRedundantSliders: false);

// Run CLI/headless generation, then compare output bytes to expectedCatalog output and NotBe(bundledOnly).
```

### Anti-Patterns to Avoid
- **Using the constructor-injected bundled catalog for all projects:** This is the current gap and silently falls back unresolved custom profiles to the default bundled profile. [VERIFIED: `TemplateProfileCatalog.cs:68-74`, `HeadlessGenerationService.cs:61-100`]
- **Mutating `ProjectModel.CustomProfiles` during generation:** Project validation and generation should be read-only; existing diagnostics explicitly preserve project state. [VERIFIED: `ProjectValidationService.cs:18-24`, `HeadlessGenerationService.cs:36-120`]
- **Changing formatter/export implementations to make tests pass:** Formatter and writer files are sacred; the phase should change catalog selection only. [VERIFIED: `AGENTS.md:51-70`]
- **Resolving custom profile identity from filenames or paths:** Profile extensibility spec requires internal `Name` identity and forbids path/filename identity. [VERIFIED: `openspec/specs/profile-extensibility/spec.md:57-61`]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| CLI parsing/help/required options | Manual `args[]` parsing | `System.CommandLine` | It provides command/option parsing, help, and parse/invoke behavior. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/] |
| Profile JSON serialization | Ad hoc string JSON | `ProjectFileService`, `ProfileDefinitionService`, `System.Text.Json` | Existing services preserve schema, custom converters, ordering, and compatibility. [VERIFIED: `ProjectFileService.cs:43-55`, `ProjectFileService.cs:127-145`] |
| Request-scoped catalog rules | Separate one-off CLI lookup tables | Shared Core composer based on `TemplateProfileCatalog` and `CustomProfileDefinition` | Bundle already implements the target rule; sharing prevents drift. [VERIFIED: `PortableProjectBundleService.cs:288-300`] |
| Output bytes | New template/BoS writers | `TemplateGenerationService`, `MorphGenerationService`, `BodyGenIniExportWriter`, `BosJsonExportWriter` | Existing services own Java parity, line endings, and minimal-json behavior. [VERIFIED: `AGENTS.md:60-70`, `HeadlessGenerationService.cs:86-100`] |
| Validation severity/exit mapping | New CLI-only validation system | `ProjectValidationService` and `AutomationExitCode` | Existing automation contracts already define stable success/usage/validation/overwrite/I/O exit codes. [VERIFIED: `HeadlessGenerationContracts.cs:16-26`] |

**Key insight:** The hard part is not parsing CLI input; it is ensuring every request observes one coherent catalog for validation and output so custom profiles do not fall back silently. [VERIFIED: `PortableProjectBundleService.cs:183-210`, `TemplateProfileCatalog.cs:68-74`]

## Common Pitfalls

### Pitfall 1: Validating with bundled-only catalog, generating with request catalog, or vice versa
**What goes wrong:** The CLI can report success/diagnostics for one profile set while writing bytes from another profile set. [VERIFIED: `PortableProjectBundleService.cs:183-210`]  
**Why it happens:** Catalog composition happens too late or is duplicated between validation and generation. [VERIFIED: `HeadlessGenerationService.cs:61-100`]  
**How to avoid:** Build one request catalog immediately after project load and pass it to validation, templates, and BoS. [VERIFIED: `PortableProjectBundleService.cs:183-210`]  
**Warning signs:** Tests assert only exit code success and file existence, not content differences. [VERIFIED: `CliGenerationTests.cs:163-206`]

### Pitfall 2: Letting duplicate custom/bundled names enter a catalog
**What goes wrong:** `TemplateProfileCatalog` rejects duplicate display names ignoring case. [VERIFIED: `TemplateProfileCatalog.cs:86-106`]  
**Why it happens:** A composer appends project profiles without filtering bundled names or duplicate references. [VERIFIED: `PortableProjectBundleService.cs:315-347`]  
**How to avoid:** Keep bundled entries first; append only non-bundled, referenced custom definitions; dedupe by name with `StringComparer.OrdinalIgnoreCase`. [VERIFIED: `PortableProjectBundleService.cs:315-347`]  
**Warning signs:** `ArgumentException` from `TemplateProfileCatalog` construction. [VERIFIED: `TemplateProfileCatalog.cs:100-103`]

### Pitfall 3: Assuming `ProjectFileService.Load` exposes diagnostics
**What goes wrong:** CLI generation may ignore recoverable embedded-profile load diagnostics because `Load` returns only `ProjectModel`, while `LoadWithDiagnosticsFromString` returns diagnostics. [VERIFIED: `ProjectFileService.cs:56-68`, `ProjectFileService.cs:70-103`]  
**Why it happens:** Existing CLI `bundle` and `generate` paths call `Load`, not a diagnostics-returning file API. [VERIFIED: `Program.cs:116-122`, `HeadlessGenerationService.cs:40-47`]  
**How to avoid:** For Phase 06 scope, compose from successfully hydrated `project.CustomProfiles`; do not invent new load-diagnostic CLI behavior unless a requirement demands it. [VERIFIED: phase success criteria; `ProjectFileService.cs:97-100`]  
**Warning signs:** Plan expands into diagnostics UI/CLI reporting instead of catalog composition. [VERIFIED: `.planning/REQUIREMENTS.md:46-47`]

### Pitfall 4: Treating local custom profiles as available to standalone CLI generate
**What goes wrong:** A headless command on another machine may accidentally depend on user-local profile storage, contradicting portable embedded-project semantics. [VERIFIED: `openspec/specs/profile-extensibility/spec.md:30-37`]  
**Why it happens:** GUI has an active runtime catalog containing local profiles; CLI `generate` currently has only bundled assets and a project path. [VERIFIED: `AppBootstrapper.cs:47-60`, `Program.cs:179-190`]  
**How to avoid:** For Phase 06, support project-embedded custom profiles directly; only support external/local custom profiles if an explicit save-context or CLI option is designed later. [VERIFIED: phase success criteria mention embedded/custom project profiles; no CLI option exists in `Program.cs:25-61`]  
**Warning signs:** Planner adds App `IUserProfileStore` dependency to `BS2BG.Cli`. [VERIFIED: `BS2BG.Cli.csproj:12-18`]

## Code Examples

Verified patterns from existing code and official docs:

### System.CommandLine parse/invoke pattern
```csharp
// Source: https://learn.microsoft.com/dotnet/standard/commandline/get-started-tutorial#parse-the-arguments-and-invoke-the-parseresult
public static int Main(string[] args) => CreateRootCommand().Parse(args).Invoke();
```
This matches the current CLI entry point. [VERIFIED: `src/BS2BG.Cli/Program.cs:15-18`]

### Required typed options with accepted values
```csharp
// Source: existing BS2BG CLI in src/BS2BG.Cli/Program.cs:25-43.
var intentOption = new Option<string>("--intent")
{
    Description = "Output intent to generate: bodygen, bos, or all.",
    Required = true,
};
intentOption.AcceptOnlyFromAmong("bodygen", "bos", "all");
```

### Request-scoped catalog construction from bundle service
```csharp
// Source: existing bundle implementation in src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:288-300.
private TemplateProfileCatalog BuildRequestProfileCatalog(ProjectModel project, ProjectSaveContext? saveContext)
{
    var entries = profileCatalog.Entries
        .Where(entry => entry.SourceKind == ProfileSourceKind.Bundled)
        .Concat(ResolveBundleProfileSet(project, saveContext).Select(profile => new ProfileCatalogEntry(
            profile.Name,
            new TemplateProfile(profile.Name, profile.SliderProfile),
            profile.SourceKind,
            profile.FilePath,
            false)));

    return new TemplateProfileCatalog(entries);
}
```

### Service-level output path retains existing Core writers
```csharp
// Source: existing headless generation implementation in src/BS2BG.Core/Automation/HeadlessGenerationService.cs:86-100.
var templates = templateGenerationService.GenerateTemplates(project.SliderPresets, requestCatalog, request.OmitRedundantSliders);
var morphs = morphGenerationService.GenerateMorphs(project).Text;
var bodyGen = bodyGenIniExportWriter.Write(request.OutputDirectory, templates, morphs);
var bos = bosJsonExportWriter.Write(request.OutputDirectory, project.SliderPresets, requestCatalog);
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual CLI parsers | `System.CommandLine` commands/options/actions | Current Microsoft docs show `System.CommandLine` as the .NET command-line parsing/help package. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/] | Keep using existing CLI package; do not hand-roll parser. |
| Per-project package versions | NuGet Central Package Management | Project already uses `Directory.Packages.props`; NuGet docs define this as central package management. [CITED: https://learn.microsoft.com/nuget/consume-packages/central-package-management] [VERIFIED: `Directory.Packages.props:1-24`] | Do not add versions to `.csproj` `PackageReference` entries. |
| Bundled-only automation profile catalog | Request-scoped catalog composed from bundled + project/save-context custom profiles | Bundle path already uses this in current code. [VERIFIED: `PortableProjectBundleService.cs:183-210`] | CLI generate must adopt the bundle recipe. |
| Global mutable profile state | Immutable `TemplateProfileCatalog` values per composition | `TemplateProfileCatalog` stores entries privately and validates constructor input. [VERIFIED: `TemplateProfileCatalog.cs:20-52`] | Prefer new catalog instances over mutation. |

**Deprecated/outdated:**
- Duplicating output generation in CLI is outdated for this project because AUTO-01 requires the same Core services and existing tests assert `Program.cs` does not write output files directly. [VERIFIED: `.planning/REQUIREMENTS.md:44-47`, `tests/BS2BG.Tests/CliGenerationTests.cs:280-294`]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | CLI `generate` should not search user-local custom profile storage unless a future requirement adds an explicit source option. [ASSUMED] | Common Pitfalls | If wrong, Phase 06 may need to add a profile-store dependency or option instead of embedded-only request composition. |

## Open Questions

1. **Should `HeadlessGenerationService` return project-load embedded-profile diagnostics?**
   - What we know: `ProjectFileService.Load` discards recoverable diagnostics, while `LoadWithDiagnosticsFromString` can report them. [VERIFIED: `ProjectFileService.cs:56-75`]
   - What's unclear: Phase 06 success criteria only require composing embedded/custom definitions before validation/generation, not exposing load diagnostics. [VERIFIED: phase success criteria]
   - Recommendation: Do not add diagnostics output in Phase 06 unless implementation discovers invalid embedded profile handling blocks correct catalog composition. [ASSUMED]

2. **Should the shared composer include `ProjectSaveContext` for CLI `generate`?**
   - What we know: bundle uses `ProjectSaveContext` to include local custom profiles in bundled outputs; CLI `generate` has no current option or store context for external profiles. [VERIFIED: `PortableProjectBundleService.cs:315-336`, `Program.cs:25-61`]
   - What's unclear: The phrase “embedded or custom profiles” could include local custom profile references in a saved project that were not embedded. [ASSUMED]
   - Recommendation: Implement the composer with an optional `ProjectSaveContext` parameter for reuse, but CLI `generate` should pass `null` unless a local profile source is explicitly introduced. [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| .NET SDK | Build/test/CLI execution | ✓ | 10.0.203 | None needed. [VERIFIED: `dotnet --version`] |
| NuGet package index | Version verification only | ✓ | NuGet v3 flat-container reachable | Use pinned `Directory.Packages.props` if offline. [VERIFIED: NuGet flat-container API] |
| PowerShell | Windows command execution | ✓ | Tool shell is `pwsh` on win32 | None needed. [VERIFIED: environment/tool metadata] |

**Missing dependencies with no fallback:** None found. [VERIFIED: environment audit]

**Missing dependencies with fallback:** None found. [VERIFIED: environment audit]

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | xUnit v3 3.2.2 with FluentAssertions 8.9.0. [VERIFIED: `Directory.Packages.props:11-23`] |
| Config file | `tests/BS2BG.Tests/BS2BG.Tests.csproj`. [VERIFIED: file read] |
| Quick run command | `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter FullyQualifiedName~CliGenerationTests` [VERIFIED: `dotnet test --list-tests`] |
| Full suite command | `dotnet test` [VERIFIED: `AGENTS.md:105-109`] |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| AUTO-01 | CLI `generate` uses an embedded custom profile from the loaded project instead of bundled fallback. | unit/in-process CLI regression | `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter FullyQualifiedName~CliGenerationTests` | ✅ extend `tests/BS2BG.Tests/CliGenerationTests.cs` [VERIFIED: file read] |
| AUTO-01 | `HeadlessGenerationService` validates and generates with the same request-scoped catalog. | unit/service regression | `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter FullyQualifiedName~HeadlessGenerationService` | ✅ extend `tests/BS2BG.Tests/CliGenerationTests.cs` [VERIFIED: `dotnet test --list-tests`] |
| AUTO-01 | Shared composer remains aligned with portable bundle custom-profile output semantics. | unit/regression | `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter FullyQualifiedName~PortableBundleServiceTests` | ✅ existing bundle tests cover request-scoped output. [VERIFIED: `PortableBundleServiceTests.cs:447-484`] |

### Sampling Rate
- **Per task commit:** `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter FullyQualifiedName~CliGenerationTests` [VERIFIED: test infrastructure]
- **Per wave merge:** `dotnet test tests/BS2BG.Tests/BS2BG.Tests.csproj --filter "FullyQualifiedName~CliGenerationTests|FullyQualifiedName~PortableBundleServiceTests|FullyQualifiedName~TemplateProfileCatalogTests"` [VERIFIED: `dotnet test --list-tests`]
- **Phase gate:** `dotnet test` before `/gsd-verify-work`. [VERIFIED: `AGENTS.md:105-109`]

### Wave 0 Gaps
- [ ] Add one service-level regression in `tests/BS2BG.Tests/CliGenerationTests.cs` proving `HeadlessGenerationService` uses embedded custom profiles. [VERIFIED: current tests lack this exact listed test name]
- [ ] Add one `Program.Main generate` regression proving in-process CLI output differs from bundled-only fallback for an embedded profile. [VERIFIED: `CliGenerationTests.cs:297-415`]
- [ ] Extract/centralize test helper profile factories if needed to avoid copying too much from `PortableBundleServiceTests`. [ASSUMED]

## Security Domain

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no | Local offline CLI has no authentication surface. [VERIFIED: `.planning/REQUIREMENTS.md:72-77`] |
| V3 Session Management | no | No session state or hosted account model. [VERIFIED: `.planning/REQUIREMENTS.md:72-77`] |
| V4 Access Control | no | Local project files are selected by caller; OS file permissions apply. [ASSUMED] |
| V5 Input Validation | yes | Use `System.CommandLine` option constraints plus existing `ProjectFileService`, `TemplateProfileCatalog`, and validation services. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/syntax#options] [VERIFIED: `TemplateProfileCatalog.cs:86-106`] |
| V6 Cryptography | no | Phase 06 does not add signing, hashing, or encryption. [VERIFIED: phase description] |

### Known Threat Patterns for .NET local CLI generation
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Path disclosure in expected failure messages | Information Disclosure | Preserve existing boundary mapping for CLI messages; do not add stack traces or private profile paths. [VERIFIED: `Program.cs:243-268`, `CliGenerationTests.cs:417-495`] |
| Malformed project JSON | Tampering | Catch `JsonException`/I/O errors and return `UsageError`; do not continue with partial project state. [VERIFIED: `HeadlessGenerationService.cs:40-59`] |
| Duplicate profile names | Tampering | Let `TemplateProfileCatalog` reject ambiguous case-insensitive duplicates; filter bundled-name custom definitions. [VERIFIED: `TemplateProfileCatalog.cs:86-106`, `PortableProjectBundleService.cs:315-347`] |
| Silent profile fallback | Integrity | Compose request catalog from embedded custom definitions before validation/generation and assert output divergence from bundled fallback. [VERIFIED: `TemplateProfileCatalog.cs:68-74`, `PortableBundleServiceTests.cs:467-484`] |

## Sources

### Primary (HIGH confidence)
- `.planning/REQUIREMENTS.md` - AUTO-01 and Phase 06 mapping. [VERIFIED]
- `.planning/STATE.md` - prior decisions around request-scoped portable bundle profile resolution and Phase 04 custom-profile identity. [VERIFIED]
- `AGENTS.md` and global `AGENTS.md` - project constraints, sacred files, test style, environment rules. [VERIFIED]
- `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` - current headless validation/generation flow. [VERIFIED]
- `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` - existing request-scoped catalog recipe. [VERIFIED]
- `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs` - immutable catalog lookup/fallback and duplicate validation. [VERIFIED]
- `tests/BS2BG.Tests/CliGenerationTests.cs` and `PortableBundleServiceTests.cs` - existing regression patterns. [VERIFIED]
- Microsoft Learn `System.CommandLine` docs - CLI parsing/help/invocation. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/]
- Microsoft Learn `System.Text.Json` docs - JSON serialization behavior/options. [CITED: https://learn.microsoft.com/dotnet/standard/serialization/system-text-json/overview]
- Microsoft Learn NuGet CPM docs - central package management. [CITED: https://learn.microsoft.com/nuget/consume-packages/central-package-management]

### Secondary (MEDIUM confidence)
- NuGet flat-container API version checks for stable/latest package versions. [VERIFIED: API query]

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Existing packages and current stable NuGet versions were verified. [VERIFIED: `Directory.Packages.props`; NuGet flat-container API]
- Architecture: HIGH - The target pattern already exists in bundle code and the current gap is visible in headless generation code. [VERIFIED: `PortableProjectBundleService.cs:183-210`, `HeadlessGenerationService.cs:61-100`]
- Pitfalls: HIGH - Pitfalls map to existing tests/specs and concrete fallback/duplicate behavior. [VERIFIED: cited code/tests]

**Research date:** 2026-04-27  
**Valid until:** 2026-05-27 for in-repo architecture; re-check NuGet versions after 30 days. [ASSUMED]
