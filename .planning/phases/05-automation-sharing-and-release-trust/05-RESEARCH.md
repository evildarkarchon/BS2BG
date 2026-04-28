# Phase 5: Automation, Sharing, and Release Trust - Research

**Researched:** 2026-04-27
**Domain:** .NET headless CLI, portable bundle generation, deterministic assignment strategies, Windows release trust
**Confidence:** HIGH

## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Headless CLI Contract
- **D-01:** Add a dedicated CLI project/executable, not a headless mode flag inside `BS2BG.App.exe`.
- **D-02:** CLI generation must reuse the same Core project loading, profile catalog, generation, validation, and export services used by the GUI. It must not introduce a second formatter or output writer path.
- **D-03:** CLI output selection is explicit. Commands require output intent such as BodyGen INIs, BoS JSON, or all outputs rather than generating every artifact by default.
- **D-04:** CLI validation runs before writing. `Blocker` diagnostics fail the command with a nonzero exit code; `Caution` and `Info` diagnostics are reported without blocking.
- **D-05:** Existing target files are not overwritten unless an explicit overwrite flag is supplied. This applies to BodyGen INIs and BoS JSON outputs.

#### Portable Bundle Shape
- **D-06:** The portable project bundle is a single shareable zip artifact, not only an unpacked folder.
- **D-07:** The zip uses a structured internal layout with predictable folders such as `project/`, `outputs/bodygen/`, `outputs/bos/`, `profiles/`, and `reports/`.
- **D-08:** Bundle profile copies include only referenced non-bundled custom profiles. Do not include unrelated local custom profiles or duplicate bundled profile data.
- **D-09:** Bundle manifests and reports must not include absolute local paths, drive roots, user names, original import directories, or original export directories. Use relative bundle paths and source filenames only.
- **D-10:** Bundle generation should include the `.jbs2bg` project, generated outputs requested by the workflow, referenced custom profile JSON copies, and a validation/report artifact suitable for support review.

#### Assignment Strategy Scope
- **D-11:** Deterministic assignment means seed replay: the same project, eligible rows, preset set/order, strategy configuration, and seed produce the same assignments.
- **D-12:** Phase 5 includes the full assignment strategy menu: seeded random, round-robin, weights, race filters, and group/bucket rules.
- **D-13:** Assignment strategy configuration is saved in the `.jbs2bg` project so GUI, CLI, bundle generation, undo/redo, and collaborator machines can reproduce behavior.
- **D-14:** Race-aware rules match against the imported `Npc.Race` field case-insensitively. Do not add game-data lookup, plugin parsing, or implicit race resolution.
- **D-15:** If strategy rules leave an NPC with no eligible presets, assignment is blocked for that NPC and a diagnostic/report finding identifies the rule gap. Do not silently fall back to all presets.
- **D-16:** Strategy implementation must preserve the existing random-provider abstraction rather than bypassing it. Extend or wrap the provider seam as needed so deterministic behavior remains unit-testable.

#### Release Trust And Support Docs
- **D-17:** Release automation supports both signed and unsigned paths. Use signing when configured/available; unsigned release artifacts remain valid when checksum sidecars and unsigned-warning verification docs are present.
- **D-18:** BodyGen, BodySlide, BoS, and common output-location troubleshooting guidance lives in packaged docs only for Phase 5. Do not add an in-app wizard or new Help-menu UI solely for this guidance.
- **D-19:** Package assertions verify manifest contents and smoke-level release trust: required files, checksums, profile assets, docs, absence of absolute paths in generated manifests/reports, and clean extraction launch when available.
- **D-20:** Release docs must keep the existing no-plugin-editing boundary clear: BS2BG helps users generate files and place outputs correctly, but does not edit external game plugins.

### the agent's Discretion
- Exact CLI command and flag names are flexible if they preserve explicit output selection, validation-first behavior, overwrite safety, and script-friendly nonzero failures.
- Exact zip manifest schema and report formatting are flexible if they are deterministic, path-scrubbed, and easy to test.
- Exact assignment strategy UI layout is flexible, but it must be accessible, undoable, persisted in the project, and compatible with CLI/bundle reproduction.
- Exact signing configuration mechanism is flexible as long as unsigned checksum verification remains supported.

### Deferred Ideas (OUT OF SCOPE)
- In-app setup wizard for BodyGen/BodySlide/BoS remains out of scope for Phase 5.
- Game-data/plugin lookup for race resolution remains out of scope; race filters use imported NPC text only.
- Cloud sharing, accounts, telemetry, and automatic mod-manager/game-folder discovery remain out of scope.
- Cross-platform release parity remains out of scope unless a later roadmap explicitly expands the Windows-first release target.

## Summary

Phase 5 should add a fourth solution project, `BS2BG.Cli`, plus Core-only automation services for command orchestration, bundle creation, assignment strategies, and release/package verification. The CLI must be a thin composition layer over `ProjectFileService`, `TemplateProfileCatalog`, `TemplateGenerationService`, `MorphGenerationService`, `ProjectValidationService`, `BodyGenIniExportWriter`, and `BosJsonExportWriter`; those services already own load, validation, generation, output ordering, line endings, JSON formatting, and atomic writes. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] [VERIFIED: src/BS2BG.Core/Serialization/ProjectFileService.cs] [VERIFIED: src/BS2BG.Core/Export/BodyGenIniExportWriter.cs] [VERIFIED: src/BS2BG.Core/Export/BosJsonExportWriter.cs]

Use `System.CommandLine` for the dedicated CLI parser rather than custom argument parsing. Microsoft documents it as providing common CLI functions such as parsing, help text, typed options, tab completion, and response files, and current NuGet search shows stable `System.CommandLine` `2.0.7` with preview `3.0.0-preview.3.26207.106`; pin the stable package unless the planner explicitly accepts preview API churn. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/] [VERIFIED: NuGet search 2026-04-27]

**Primary recommendation:** Build Phase 5 as Core automation services plus a small `BS2BG.Cli` executable using `System.CommandLine` `2.0.7`, `System.IO.Compression`, `System.Security.Cryptography.SHA256`, existing xUnit/FluentAssertions tests, and the existing PowerShell release script; never fork generation, formatting, export, assignment randomness, checksum, or signing logic into ad hoc paths. [VERIFIED: NuGet search 2026-04-27] [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive?view=net-10.0] [CITED: https://learn.microsoft.com/dotnet/api/system.security.cryptography.sha256?view=net-10.0]

## Project Constraints (from AGENTS.md)

- `BS2BG.Core` is `netstandard2.1`/C# 13 and must remain pure domain/I/O without Avalonia or platform dependencies. [VERIFIED: AGENTS.md]
- `BS2BG.App` is `net10.0`/C# 14 with Avalonia 12; every AXAML root and `DataTemplate` must declare `x:DataType`. [VERIFIED: AGENTS.md]
- ViewModels use ReactiveUI 23.x patterns: `ReactiveObject`, source-generated `[Reactive]`, `ReactiveCommand`, observable `canExecute`, `ToProperty`, and `RxApp.TaskpoolScheduler`; do not reintroduce custom RelayCommand types or dispatcher calls in ViewModels. [VERIFIED: AGENTS.md]
- Tests use xUnit v3 and FluentAssertions; new assertions should use `Should()` style. [VERIFIED: AGENTS.md]
- Sacred files require explicit caution before edits: `tests/fixtures/expected/**`, `JavaFloatFormatting.cs`, `SliderMathFormatter.cs`, `BodyGenIniExportWriter.cs`, and `BosJsonExportWriter.cs`. [VERIFIED: AGENTS.md]
- Byte-identical output is load-bearing: BodyGen INI uses `\r\n`; BoS JSON uses `\n` with no trailing newline; half-up rounding and context-specific float formatting must not change. [VERIFIED: AGENTS.md]
- Primary dev platform is Windows; use PowerShell, never Bash, and do not redirect output to `nul`. [VERIFIED: AGENTS.md]
- For .NET/MSBuild documentation use Microsoft Learn; for Avalonia questions use the Avalonia docs server. [VERIFIED: AGENTS.md]

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTO-01 | User can run a headless CLI generation path that uses the same Core services and output semantics as the GUI. | Use `BS2BG.Cli` + `System.CommandLine` + existing Core load/validate/generate/export services. [VERIFIED: .planning/REQUIREMENTS.md] [CITED: https://learn.microsoft.com/dotnet/standard/commandline/] |
| AUTO-02 | User can create a portable project bundle containing the `.jbs2bg` file, generated outputs, profile JSON copies, and a validation report without private local paths. | Use a Core bundle service with `ZipArchive`, relative entry names, manifest path scrubbing, and referenced-only profile export semantics from Phase 4. [VERIFIED: .planning/REQUIREMENTS.md] [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive?view=net-10.0] |
| AUTO-03 | User can apply deterministic assignment strategy presets through seams that remain testable and do not bypass existing random-provider abstractions. | Extend/wrap `IRandomAssignmentProvider` and `MorphAssignmentService`; persist strategy config in `.jbs2bg` without replacing the provider seam. [VERIFIED: src/BS2BG.Core/Morphs/IRandomAssignmentProvider.cs] [VERIFIED: src/BS2BG.Core/Morphs/MorphAssignmentService.cs] |
| AUTO-04 | User can verify downloaded release artifacts through checksums, signing information when available, and release-package assertions. | Extend `tools/release/package-release.ps1`, `SHA256SUMS.txt`, zip sidecars, optional SignTool verification, and tests. [VERIFIED: tools/release/package-release.ps1] [CITED: https://learn.microsoft.com/windows/win32/seccrypto/signtool#verify-command-options] |
| AUTO-05 | User can access setup and troubleshooting guidance for BodyGen, BodySlide, BoS, and common output-location mistakes without BS2BG editing external game plugins. | Add packaged docs only; keep no-plugin-editing boundary explicit. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] [VERIFIED: docs/release/README.md] |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Headless command parsing and exit codes | CLI executable | Core services | `BS2BG.Cli` owns arguments, stdout/stderr, and exit codes; Core owns project semantics. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/] [VERIFIED: .planning/codebase/ARCHITECTURE.md] |
| Project loading, validation, generation, export | Core Import/Generation/Export | CLI/App orchestration | Existing Core services already own parity-sensitive file semantics and are UI-free. [VERIFIED: .planning/codebase/ARCHITECTURE.md] |
| Portable project bundle | Core automation service | CLI/App invocation | Bundle structure, manifest, checksums, and path scrubbing are testable without UI; GUI can call the same service later. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] |
| Assignment strategy model and execution | Core Morphs/Models | App ViewModel UI | Strategy state affects `.jbs2bg`, CLI, bundle reproduction, and morph assignment; UI only edits configuration. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] |
| Strategy UI | Avalonia App/ViewModels | Core strategy service | GUI must be accessible, undoable, dirty-tracked, and ReactiveUI-compliant. [VERIFIED: AGENTS.md] |
| Release packaging and verification | PowerShell release tooling | Tests/docs | Existing packaging is PowerShell-based and Windows-first; tests assert package behavior and docs describe verification. [VERIFIED: tools/release/package-release.ps1] [VERIFIED: docs/release/UNSIGNED-BUILD.md] |
| Setup/troubleshooting guidance | Packaged docs | Release package tests | Context forbids in-app wizard/help UI for Phase 5; docs must ship in the package. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| .NET SDK / TFM | SDK `10.0.203`; CLI/App `net10.0`; Core `netstandard2.1` | Build new `BS2BG.Cli`, App UI, tests, and portable Core services. | Matches repository target stack and installed SDK. [VERIFIED: environment audit 2026-04-27] [VERIFIED: AGENTS.md] |
| System.CommandLine | `2.0.7` stable | CLI commands, options, typed parsing, help, parse errors, response files. | Microsoft documents parsing/help/typed options and consistent POSIX/Windows parsing; do not hand-roll parser behavior. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/] [VERIFIED: NuGet search 2026-04-27] |
| Microsoft.Extensions.DependencyInjection | `10.0.7` | Shared composition of Core services for App and CLI. | Already used in `AppBootstrapper`; stable NuGet version matches central package file. [VERIFIED: src/BS2BG.App/AppBootstrapper.cs] [VERIFIED: Directory.Packages.props] |
| System.Text.Json | `10.0.7` | Project, profile, manifest, and validation-report JSON. | Existing project/profile serialization stack; avoids adding a second JSON library. [VERIFIED: Directory.Packages.props] [VERIFIED: src/BS2BG.Core/Serialization/ProjectFileService.cs] |
| System.IO.Compression.ZipArchive / ZipFile | BCL in .NET 10 | Create portable project bundle zip and inspect release zips in tests. | Official BCL zip API supports relative entries, create/read modes, and archive assertions. [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive?view=net-10.0] |
| System.Security.Cryptography.SHA256 | BCL in .NET 10 | Checksums for manifests, sidecars, package assertions, and bundle contents. | Official BCL SHA-256 API; Microsoft notes small content changes alter hashes and SHA-256 hash size is 256 bits. [CITED: https://learn.microsoft.com/dotnet/api/system.security.cryptography.sha256?view=net-10.0] |
| PowerShell Get-FileHash | PowerShell 7.6.1 available; Windows PowerShell 5.1 available | Release script checksums and user verification instructions. | `Get-FileHash` defaults to SHA256 and is already used in release script/docs. [CITED: https://learn.microsoft.com/powershell/module/microsoft.powershell.utility/get-filehash?view=powershell-7.6] [VERIFIED: tools/release/package-release.ps1] |
| SignTool | Optional; not installed locally | Optional Authenticode signing and verification metadata. | Microsoft documents `signtool verify /pa` and exit codes 0/1/2; unsigned path remains valid. [CITED: https://learn.microsoft.com/windows/win32/seccrypto/signtool#verify-command-options] [VERIFIED: environment audit 2026-04-27] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| xUnit v3 | `3.2.2` | Unit, integration, release-package assertions. | Existing test framework; use for CLI parse/exit-code tests, bundle manifest tests, strategy tests, package tests. [VERIFIED: Directory.Packages.props] |
| FluentAssertions | `8.9.0` | Expressive assertions. | Required style for new tests. [VERIFIED: AGENTS.md] [VERIFIED: Directory.Packages.props] |
| Avalonia.Headless.XUnit | `12.0.1` | Strategy UI and accessibility tests. | Use only for UI plans that add assignment strategy controls. [VERIFIED: Directory.Packages.props] |
| ReactiveUI.Avalonia / SourceGenerators | `12.0.1` / `2.6.1` | App ViewModel strategy UI. | Use for App-side commands/properties; not for Core or CLI. [VERIFIED: AGENTS.md] [VERIFIED: Directory.Packages.props] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| System.CommandLine | Manual `args[]` parsing | Reject: custom parser would hand-roll option arity, help, parse errors, quoting, and response-file behavior. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/] |
| System.CommandLine | Spectre.Console.Cli | Not needed: richer console UI adds dependency and styling concerns, while Phase 5 needs script-friendly automation. [ASSUMED] |
| ZipArchive | Shelling out to `Compress-Archive` from C# | Reject for app/CLI bundle creation: external process behavior is harder to unit test and less portable inside Core. [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive?view=net-10.0] |
| SHA256 / Get-FileHash | MD5/SHA1 | Reject: Microsoft notes MD5/SHA1 are no longer secure against attack in `Get-FileHash` docs. [CITED: https://learn.microsoft.com/powershell/module/microsoft.powershell.utility/get-filehash?view=powershell-7.6] |

**Installation:**
```powershell
# Add only the parser package; most bundle/hash APIs are BCL.
dotnet add src/BS2BG.Cli/BS2BG.Cli.csproj package System.CommandLine --version 2.0.7
```

**Version verification:** Stable versions were checked against NuGet search on 2026-04-27: `System.CommandLine` `2.0.7`, `Microsoft.Extensions.DependencyInjection` `10.0.7`, `System.Text.Json` `10.0.7`, `xunit.v3` `3.2.2`, `FluentAssertions` `8.9.0`, `Avalonia.Headless.XUnit` `12.0.1`. [VERIFIED: NuGet search 2026-04-27]

## Architecture Patterns

### System Architecture Diagram

```text
CLI args / App command
        |
        v
System.CommandLine (CLI only) -----> explicit output intent + overwrite flag
        |                                      |
        v                                      v
Automation Orchestrator (Core-facing, no Avalonia references)
        |
        +--> ProjectFileService.Load(.jbs2bg)
        +--> TemplateProfileCatalog composition
        +--> ProjectValidationService.Validate
        |        |-- Blocker -> nonzero CLI exit / no writes
        |        `-- Caution/Info -> report and continue
        +--> AssignmentStrategyService (optional)
        |        `-- IRandomAssignmentProvider-compatible seeded/random seam
        +--> TemplateGenerationService + MorphGenerationService
        +--> BodyGenIniExportWriter / BosJsonExportWriter
        +--> BundleService
                 |-- project/project.jbs2bg
                 |-- outputs/bodygen/* and outputs/bos/*
                 |-- profiles/*.json (referenced custom only)
                 |-- reports/validation.*
                 `-- manifest.json + checksums, relative paths only
```

### Recommended Project Structure

```text
src/
├── BS2BG.Core/
│   ├── Automation/          # CLI/bundle orchestration contracts, output intent, exit/result models
│   ├── Bundling/            # PortableProjectBundleService, manifest, path scrubber, zip writer
│   ├── Morphs/              # Strategy model/executor/provider extensions
│   └── Serialization/       # Backward-compatible project strategy persistence
├── BS2BG.Cli/               # net10.0 console executable, System.CommandLine root/subcommands only
├── BS2BG.App/
│   ├── ViewModels/          # ReactiveUI strategy UI and bundle command surfaces
│   └── Services/            # file-picker adapters only; no generation forks
└── tests/BS2BG.Tests/
    ├── Cli*Tests.cs
    ├── PortableBundle*Tests.cs
    ├── AssignmentStrategy*Tests.cs
    └── ReleaseTrust*Tests.cs
```

### Pattern 1: Thin CLI Composition Over Core

**What:** `BS2BG.Cli` owns command syntax, parse errors, stdout/stderr, and process exit codes; Core owns project loading, validation, generation, export, and bundle creation. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/] [VERIFIED: .planning/codebase/ARCHITECTURE.md]

**When to use:** Every headless workflow (`generate`, `bundle`, `validate`, `verify-release`) should delegate to Core service methods with typed request/response records. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]

**Example:**
```csharp
// Source: Microsoft Learn System.CommandLine overview + existing Core service pattern.
var projectOption = new Option<FileInfo>("--project") { Required = true };
var outputOption = new Option<DirectoryInfo>("--output") { Required = true };
var overwriteOption = new Option<bool>("--overwrite");

var generate = new Command("generate", "Generate BS2BG outputs from a project file")
{
    projectOption,
    outputOption,
    overwriteOption,
};

generate.SetAction(parseResult =>
{
    var request = new HeadlessGenerationRequest(
        parseResult.GetValue(projectOption)!.FullName,
        parseResult.GetValue(outputOption)!.FullName,
        OutputIntent.All,
        parseResult.GetValue(overwriteOption));

    return services.GetRequiredService<HeadlessGenerationService>().Run(request).ExitCode;
});

return rootCommand.Parse(args).Invoke();
```

### Pattern 2: Validation-First Write Gate

**What:** Run `ProjectValidationService.Validate` before export/bundle writes; any `DiagnosticSeverity.Blocker` produces a nonzero CLI exit and no output mutations. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] [VERIFIED: src/BS2BG.Core/Diagnostics/ProjectValidationService.cs]

**When to use:** Before BodyGen INI writes, BoS JSON writes, portable bundle generation, and release verification reports. [VERIFIED: .planning/REQUIREMENTS.md]

**Example:**
```csharp
// Source: src/BS2BG.Core/Diagnostics/ProjectValidationService.cs
var report = ProjectValidationService.Validate(project, profileCatalog);
if (report.Findings.Any(finding => finding.Severity == DiagnosticSeverity.Blocker))
{
    return HeadlessGenerationResult.Blocked(report, exitCode: 2);
}
```

### Pattern 3: Deterministic Strategy as Data + Provider-Compatible Execution

**What:** Persist strategy configuration as project data and execute it through an assignment service that still uses or wraps `IRandomAssignmentProvider` for seeded random selection. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] [VERIFIED: src/BS2BG.Core/Morphs/IRandomAssignmentProvider.cs]

**When to use:** Seeded random, round-robin, weighted selection, race filters, and group/bucket rules all need repeatability across GUI, CLI, bundles, undo/redo, and collaborator machines. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]

**Example:**
```csharp
// Source: existing IRandomAssignmentProvider seam in src/BS2BG.Core/Morphs/IRandomAssignmentProvider.cs
public sealed class SeededRandomAssignmentProvider(int seed) : IRandomAssignmentProvider
{
    private readonly Random random = new(seed);

    public int NextIndex(int exclusiveMax) => exclusiveMax <= 0 ? 0 : random.Next(exclusiveMax);
}
```

### Pattern 4: Relative-Path Bundle Manifest

**What:** Manifest entries store bundle-relative paths and source filenames only; generated reports must reject drive roots, absolute paths, and usernames. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]

**When to use:** `manifest.json`, validation report, checksum list, and support-review report. [VERIFIED: .planning/REQUIREMENTS.md]

**Example:**
```csharp
// Source: ZipArchive docs require relative entry names for archive contents.
using var zipStream = File.Create(bundlePath);
using var archive = new ZipArchive(zipStream, ZipArchiveMode.Create);

var entry = archive.CreateEntry("reports/validation.txt", CompressionLevel.Optimal);
await using var entryStream = entry.Open();
await using var writer = new StreamWriter(entryStream, new UTF8Encoding(false));
await writer.WriteAsync(validationReportTextWithoutPrivatePaths);
```

### Anti-Patterns to Avoid

- **Forking generation for CLI:** Bypasses `SliderMathFormatter`, `TemplateGenerationService`, and writer semantics; this risks breaking line endings, rounding, ordering, and BoS JSON layout. [VERIFIED: AGENTS.md]
- **Writing export files directly from CLI/App:** Bypasses atomic writes and normalization. Use `BodyGenIniExportWriter` and `BosJsonExportWriter`. [VERIFIED: src/BS2BG.Core/Export/BodyGenIniExportWriter.cs] [VERIFIED: src/BS2BG.Core/Export/BosJsonExportWriter.cs]
- **Absolute paths in manifests/reports:** Violates bundle privacy and sharing constraints. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]
- **Silent assignment fallback:** If filters/rules leave no eligible preset, emit a diagnostic and block that NPC; do not assign from all presets. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]
- **Game-plugin or race lookup:** Race filters must use imported `Npc.Race` only; no xEdit/plugin parsing. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]
- **Compressing first and validating later:** `ZipFile.CreateFromDirectory` can leave an incomplete invalid archive if a file cannot be added; stage and validate before final zip write. [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.zipfile.createfromdirectory?view=net-10.0]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| CLI parsing/help/errors | Manual `args[]` parser | `System.CommandLine` | Handles parsing, help, typed options, parse errors, response files, and consistent command syntax. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/] |
| Template/BoS formatting | New CLI formatter | `TemplateGenerationService`, `SliderMathFormatter`, `BosJsonExportWriter` | Output parity is load-bearing and protected by existing services/tests. [VERIFIED: AGENTS.md] |
| BodyGen file writes | `File.WriteAllText` from CLI/App | `BodyGenIniExportWriter` | Existing writer normalizes CRLF and writes atomically. [VERIFIED: src/BS2BG.Core/Export/BodyGenIniExportWriter.cs] |
| BoS JSON file writes | Custom JSON writer or direct serialization | `BosJsonExportWriter` | Existing writer sanitizes filenames, orders presets, and delegates preview JSON to generation service. [VERIFIED: src/BS2BG.Core/Export/BosJsonExportWriter.cs] |
| Project custom profile copies | Directory copy of all local profiles | `ProjectFileService` / `ProfileDefinitionService.ExportProfileJson` semantics | Phase 4 requires referenced non-bundled profiles only. [VERIFIED: src/BS2BG.Core/Serialization/ProjectFileService.cs] [VERIFIED: openspec/specs/profile-extensibility/spec.md] |
| Zip archive implementation | Custom zip format or shell process from Core | `System.IO.Compression.ZipArchive` | Official BCL API supports create/read/update and relative entries. [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive?view=net-10.0] |
| Checksums | Custom hash algorithm, MD5/SHA1 | SHA-256 via `SHA256` / `Get-FileHash` | Microsoft docs identify MD5/SHA1 as no longer secure against attack; SHA256 is the existing release practice. [CITED: https://learn.microsoft.com/powershell/module/microsoft.powershell.utility/get-filehash?view=powershell-7.6] |
| Signing verification | Parse PE signatures manually | SignTool when available; checksum docs when not | SignTool verifies Authenticode policy and returns documented exit codes; unsigned path is locked as valid. [CITED: https://learn.microsoft.com/windows/win32/seccrypto/signtool#verify-command-options] |
| Strategy randomness | `new Random()` inside strategy code | Existing `IRandomAssignmentProvider` seam or deterministic wrapper | Existing tests already use provider injection; D-16 forbids bypass. [VERIFIED: src/BS2BG.Core/Morphs/IRandomAssignmentProvider.cs] [VERIFIED: tests/BS2BG.Tests/MorphCoreTests.cs] |

**Key insight:** The hard part is not creating files; it is preserving the same trusted Core semantics across GUI, CLI, bundle, and release verification paths. Every custom shortcut creates a second truth source and undermines Phase 5's trust goal. [VERIFIED: .planning/PROJECT.md]

## Common Pitfalls

### Pitfall 1: CLI Drift From GUI Semantics
**What goes wrong:** CLI output differs from GUI export because it serializes directly or uses a new formatter. [VERIFIED: AGENTS.md]
**Why it happens:** CLI projects often reimplement simple-looking generation to avoid UI dependencies. [ASSUMED]
**How to avoid:** Put orchestration in CLI, keep all semantics in Core; add golden fixture tests comparing CLI-generated files to existing Core writer output. [VERIFIED: .planning/codebase/ARCHITECTURE.md]
**Warning signs:** New code references `File.WriteAllText` for `templates.ini`, `morphs.ini`, or BoS JSON outside existing writers. [VERIFIED: .planning/codebase/ARCHITECTURE.md]

### Pitfall 2: Bundle Privacy Leaks
**What goes wrong:** Manifest/report leaks `C:\Users\...`, drive letters, original XML import folders, or export folders. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]
**Why it happens:** Using `FullName`, exception messages, or direct diagnostic strings in support artifacts. [ASSUMED]
**How to avoid:** Centralize path scrubbing; manifest schema stores only relative bundle paths and source file names. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]
**Warning signs:** Tests do not scan `manifest.json` and reports for `Path.IsPathRooted`, drive patterns, backslashes, or user profile path fragments. [ASSUMED]

### Pitfall 3: Zip Entry Names and Determinism
**What goes wrong:** Archives contain duplicate entries, absolute-looking names, platform-specific separators, or unstable timestamps. [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive.createentry?view=net-10.0]
**Why it happens:** `ZipArchive.CreateEntry` allows duplicate entry names and sets new entry `LastWriteTime` to current time unless controlled. [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive.createentry?view=net-10.0]
**How to avoid:** Sort entries ordinally, convert `\` to `/`, reject rooted paths, track used entry names, and set a fixed `LastWriteTime` if byte-stable zips become required. [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive.createentry?view=net-10.0]
**Warning signs:** Bundle tests inspect extracted files but not `ZipArchive.Entries` names and duplicate counts. [ASSUMED]

### Pitfall 4: Weighted Strategy Non-Determinism
**What goes wrong:** Same seed produces different assignments on another machine or after sorting changes. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]
**Why it happens:** Iteration over dictionaries or filtered UI collections without stable ordering. [ASSUMED]
**How to avoid:** Define eligibility as ordered by stable preset/NPC identity using explicit `StringComparer.OrdinalIgnoreCase` where matching is case-insensitive; persist strategy config and seed. [VERIFIED: src/BS2BG.Core/Serialization/ProjectFileService.cs] [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]
**Warning signs:** Tests assert count only, not exact assignment sequence for seed + strategy + NPC set. [ASSUMED]

### Pitfall 5: SignTool as a Hard Dependency
**What goes wrong:** Local package verification fails because Windows SDK SignTool is not installed. [VERIFIED: environment audit 2026-04-27]
**Why it happens:** Signing tools ship with SDK/WDK installs and may not be on `PATH`. [ASSUMED]
**How to avoid:** Treat SignTool as optional: if available, verify/sign and record metadata; if absent, generate checksum sidecars and unsigned-build docs. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]
**Warning signs:** Release tests require `signtool` unconditionally. [ASSUMED]

## Code Examples

Verified patterns from official and project sources:

### System.CommandLine Parse and Invoke
```csharp
// Source: https://learn.microsoft.com/dotnet/standard/commandline/get-started-tutorial#parse-the-arguments-and-invoke-the-parseresult
ParseResult parseResult = rootCommand.Parse(args);
return parseResult.Invoke();
```

### Zip Entry With Relative Path
```csharp
// Source: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive.createentry?view=net-10.0
using var archive = new ZipArchive(zipStream, ZipArchiveMode.Create);
ZipArchiveEntry entry = archive.CreateEntry("reports/validation.txt", CompressionLevel.Optimal);
using StreamWriter writer = new(entry.Open(), new UTF8Encoding(false));
writer.Write(reportText);
```

### SHA-256 File Hash in C#
```csharp
// Source: https://learn.microsoft.com/dotnet/api/system.security.cryptography.sha256.hashdata?view=net-10.0
await using var stream = File.OpenRead(path);
byte[] hash = SHA256.HashData(stream);
string hex = Convert.ToHexString(hash).ToLowerInvariant();
```

### PowerShell Checksum Verification
```powershell
# Source: https://learn.microsoft.com/powershell/module/microsoft.powershell.utility/get-filehash?view=powershell-7.6
$expected = (Get-Content .\BS2BG-v1.0.0-win-x64.zip.sha256).Split(' ')[0]
$actual = (Get-FileHash -Algorithm SHA256 -LiteralPath .\BS2BG-v1.0.0-win-x64.zip).Hash.ToLowerInvariant()
$actual -eq $expected
```

### SignTool Verification When Available
```powershell
# Source: https://learn.microsoft.com/windows/win32/seccrypto/using-signtool-to-verify-a-file-signature
signtool verify /pa /v .\BS2BG.App.exe
```

### Existing Random Provider Seam
```csharp
// Source: src/BS2BG.Core/Morphs/IRandomAssignmentProvider.cs
public interface IRandomAssignmentProvider
{
    int NextIndex(int exclusiveMax);
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual console `args[]` parsing | `System.CommandLine` parser with typed options, help, parse errors, tab completion, response files | Current Microsoft docs and NuGet stable `2.0.7` as of 2026-04-27 | Avoid custom parser edge cases and keep CLI script-friendly. [CITED: https://learn.microsoft.com/dotnet/standard/commandline/] [VERIFIED: NuGet search 2026-04-27] |
| Unsigned-only release trust | Signed when configured plus checksum sidecars and unsigned-warning docs when unsigned | Locked Phase 5 D-17 | Release can improve trust without blocking on certificate availability. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] |
| GUI-only generation | Dedicated CLI executable over same Core services | Locked Phase 5 D-01/D-02 | Automation does not require Avalonia startup and does not fork output semantics. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] |
| Simple random fill | Persisted deterministic strategies: seed replay, round-robin, weights, race filters, group/buckets | Phase 5 user scope override | Strategy output becomes reproducible across GUI, CLI, bundles, and collaborators. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] |

**Deprecated/outdated:**
- MD5/SHA1 for release integrity: Microsoft PowerShell docs say MD5 and SHA1 are no longer considered secure against attack; use SHA-256. [CITED: https://learn.microsoft.com/powershell/module/microsoft.powershell.utility/get-filehash?view=powershell-7.6]
- `System.CommandLine` preview APIs unless deliberately accepted: NuGet search shows stable `2.0.7` and preview `3.0.0-preview.3.26207.106`; pin stable for phase implementation. [VERIFIED: NuGet search 2026-04-27]
- In-app setup wizard for this phase: explicitly deferred by context. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spectre.Console.Cli is not needed because Phase 5 needs script-friendly automation more than rich console UI. | Standard Stack / Alternatives | If users expect rich interactive console flows, CLI UX may feel sparse. |
| A2 | CLI drift commonly happens because developers reimplement simple-looking generation to avoid UI dependencies. | Common Pitfalls | If false, still harmless; the prevention remains required by locked decisions. |
| A3 | Bundle privacy leaks commonly come from `FullName`, exception messages, or direct diagnostics. | Common Pitfalls | Path-scrubber tests might miss other privacy vectors if this list is incomplete. |
| A4 | Bundle tests should scan for rooted paths, drive patterns, backslashes, and user profile fragments. | Common Pitfalls | Overly strict tests may need platform-aware normalization. |
| A5 | Weighted strategy non-determinism commonly comes from dictionary/UI collection ordering. | Common Pitfalls | Strategy test design could miss other entropy sources. |
| A6 | SignTool may be absent because SDK/WDK tools are not necessarily on PATH. | Common Pitfalls | Release automation might under-document installation if signing becomes mandatory later. |

## Open Questions

1. **Exact CLI command names and exit-code map**
   - What we know: CLI must use explicit output intent, validation-first behavior, overwrite safety, and script-friendly nonzero failures. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]
   - What's unclear: Whether exit codes should distinguish parse error, validation blocker, overwrite refusal, and filesystem failure.
   - Recommendation: Define `0=success`, `1=parse/usage`, `2=validation blocked`, `3=overwrite refused`, `4=I/O failure` in the first CLI plan. [ASSUMED]

2. **Signing configuration source**
   - What we know: Signed and unsigned paths must both be supported; SignTool is not installed locally. [VERIFIED: environment audit 2026-04-27] [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]
   - What's unclear: Whether a certificate/PFX, Windows cert store subject, or external CI secret will be used later.
   - Recommendation: Keep signing opt-in through PowerShell parameters/env vars and keep unsigned checksum path as default. [ASSUMED]

3. **Bundle zip byte determinism**
   - What we know: Context requires deterministic manifest/report formatting and path scrubbing, but does not require byte-identical zip files. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]
   - What's unclear: Whether future release/support workflows need repeatable zip hashes for identical inputs.
   - Recommendation: Sort entries now; set fixed entry timestamps only if deterministic zip bytes become a locked requirement. [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive.createentry?view=net-10.0]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| .NET SDK | Build/test/CLI project | ✓ | `10.0.203` | None needed. [VERIFIED: environment audit 2026-04-27] |
| PowerShell 7 | Release script and verification docs | ✓ | `7.6.1` | Windows PowerShell 5.1 also available. [VERIFIED: environment audit 2026-04-27] |
| Windows PowerShell | Compatibility for release script users | ✓ | `5.1.26100.8115` | PowerShell 7. [VERIFIED: environment audit 2026-04-27] |
| SignTool | Optional signing/verification metadata | ✗ | — | Generate SHA-256 sidecars and unsigned docs; skip signing assertions unless tool is present. [VERIFIED: environment audit 2026-04-27] |
| Java/Javac | Only fixture regeneration, not Phase 5 implementation | ✓ | `26.0.1` | Do not regenerate golden fixtures for Phase 5 unless explicitly required. [VERIFIED: environment audit 2026-04-27] |

**Missing dependencies with no fallback:**
- None for Phase 5 planning; signing is explicitly optional. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md]

**Missing dependencies with fallback:**
- SignTool: use checksum sidecars and unsigned-build docs when signing is not configured/available. [VERIFIED: environment audit 2026-04-27] [VERIFIED: docs/release/UNSIGNED-BUILD.md]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | xUnit v3 `3.2.2` + FluentAssertions `8.9.0`; Avalonia Headless `12.0.1` for UI tests. [VERIFIED: Directory.Packages.props] |
| Config file | `Directory.Packages.props`, `Directory.Build.props`; no separate xUnit config found in required reads. [VERIFIED: Directory.Packages.props] |
| Quick run command | `dotnet test BS2BG.sln --filter "FullyQualifiedName~Cli|FullyQualifiedName~Bundle|FullyQualifiedName~Strategy|FullyQualifiedName~Release"` [ASSUMED] |
| Full suite command | `dotnet test BS2BG.sln` [VERIFIED: AGENTS.md] |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| AUTO-01 | CLI loads project, validates first, writes explicit selected outputs through Core writers, respects overwrite flag, returns nonzero on blockers. | unit/integration | `dotnet test BS2BG.sln --filter FullyQualifiedName~CliGeneration` | ❌ Wave 0 |
| AUTO-02 | Bundle zip contains `project/`, `outputs/bodygen/`, `outputs/bos/`, `profiles/`, `reports/`, manifest, checksums, and no private paths. | unit/integration | `dotnet test BS2BG.sln --filter FullyQualifiedName~PortableBundle` | ❌ Wave 0 |
| AUTO-03 | Seeded random, round-robin, weights, race filters, groups/buckets are deterministic and use provider-compatible seams. | unit | `dotnet test BS2BG.sln --filter FullyQualifiedName~AssignmentStrategy` | ❌ Wave 0 |
| AUTO-04 | Package assertions verify required files, checksums, optional signing metadata, unsigned docs, and clean extraction launch when available. | unit/integration/script | `dotnet test BS2BG.sln --filter FullyQualifiedName~ReleaseTrust` | ❌ Wave 0 partial existing release tests |
| AUTO-05 | Packaged docs include BodyGen/BodySlide/BoS setup/troubleshooting and no-plugin-editing boundary. | unit/docs assertion | `dotnet test BS2BG.sln --filter FullyQualifiedName~ReleaseDocs` | ❌ Wave 0 partial existing docs |

### Sampling Rate
- **Per task commit:** targeted filter for touched area plus `dotnet build BS2BG.sln`. [ASSUMED]
- **Per wave merge:** `dotnet test BS2BG.sln`. [VERIFIED: AGENTS.md]
- **Phase gate:** full suite green plus release script/package assertion smoke. [VERIFIED: tools/release/package-release.ps1] [VERIFIED: docs/release/QA-CHECKLIST.md]

### Wave 0 Gaps
- [ ] `tests/BS2BG.Tests/CliGenerationTests.cs` — covers AUTO-01.
- [ ] `tests/BS2BG.Tests/PortableBundleServiceTests.cs` — covers AUTO-02.
- [ ] `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs` — covers AUTO-03.
- [ ] `tests/BS2BG.Tests/ReleaseTrustTests.cs` — extends AUTO-04 beyond existing `ReleasePackagingScriptTests.cs`.
- [ ] `tests/BS2BG.Tests/ReleaseDocsTests.cs` — covers AUTO-05 packaged docs.
- [ ] `src/BS2BG.Cli/BS2BG.Cli.csproj` — CLI project needed before CLI tests compile.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no | Offline desktop/CLI utility; no accounts, OAuth, cookies, or auth provider. [VERIFIED: .planning/codebase/INTEGRATIONS.md] |
| V3 Session Management | no | No sessions or network service. [VERIFIED: .planning/codebase/INTEGRATIONS.md] |
| V4 Access Control | limited | Filesystem safety: no overwrite without explicit flag; no operations outside selected output/bundle paths. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] |
| V5 Input Validation | yes | Validate project JSON, profile JSON, strategy config, CLI options, zip entry names, and output paths before writes. [VERIFIED: src/BS2BG.Core/Serialization/ProjectFileService.cs] [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive.createentry?view=net-10.0] |
| V6 Cryptography | yes | Use SHA-256 via BCL/PowerShell; optional Authenticode verification through SignTool; never custom crypto. [CITED: https://learn.microsoft.com/dotnet/api/system.security.cryptography.sha256?view=net-10.0] [CITED: https://learn.microsoft.com/windows/win32/seccrypto/signtool#verify-command-options] |

### Known Threat Patterns for BS2BG Phase 5

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Bundle path traversal / unsafe zip entries | Tampering | Store only normalized relative entry names; reject rooted paths and `..` segments; inspect `ZipArchive.Entries` in tests. [CITED: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive?view=net-10.0] |
| Private path disclosure in support bundles | Information Disclosure | Central path scrubber; tests scan manifest/report/docs generated from bundle service. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] |
| Accidental overwrite of user exports | Tampering | Preflight existing target files; require explicit overwrite flag. [VERIFIED: .planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md] |
| Malicious/corrupt profile JSON in shared project | Tampering | Reuse Phase 4 profile validation and embedded-profile diagnostics; reject malformed optional data without corrupting legacy project fields. [VERIFIED: src/BS2BG.Core/Serialization/ProjectFileService.cs] |
| Release artifact substitution | Spoofing/Tampering | SHA-256 sidecars, packaged `SHA256SUMS.txt`, optional SignTool Authenticode verification. [VERIFIED: tools/release/package-release.ps1] [CITED: https://learn.microsoft.com/powershell/module/microsoft.powershell.utility/get-filehash?view=powershell-7.6] |

## Sources

### Primary (HIGH confidence)
- `AGENTS.md` — project stack, sacred files, byte parity, ReactiveUI/Avalonia conventions, testing rules.
- `.planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md` — locked Phase 5 decisions and scope.
- `.planning/REQUIREMENTS.md` — AUTO-01 through AUTO-05.
- `.planning/PROJECT.md`, `.planning/ROADMAP.md`, `.planning/STATE.md` — project constraints, roadmap, blockers.
- `.planning/codebase/STACK.md`, `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/INTEGRATIONS.md` — current codebase boundaries and dependencies.
- `src/BS2BG.Core/*` service files — current project serialization, validation, export, and morph assignment seams.
- `tools/release/package-release.ps1`, `docs/release/*.md` — current release packaging/checksum docs.
- Microsoft Learn System.CommandLine overview: https://learn.microsoft.com/dotnet/standard/commandline/
- Microsoft Learn ZipArchive docs: https://learn.microsoft.com/dotnet/api/system.io.compression.ziparchive?view=net-10.0
- Microsoft Learn Get-FileHash docs: https://learn.microsoft.com/powershell/module/microsoft.powershell.utility/get-filehash?view=powershell-7.6
- Microsoft Learn SignTool docs: https://learn.microsoft.com/windows/win32/seccrypto/signtool#verify-command-options
- Microsoft Learn SHA256 docs: https://learn.microsoft.com/dotnet/api/system.security.cryptography.sha256?view=net-10.0
- NuGet search on 2026-04-27 — stable package versions.

### Secondary (MEDIUM confidence)
- None needed; critical findings were verified from project files, NuGet, or Microsoft documentation.

### Tertiary (LOW confidence)
- Assumptions listed in the Assumptions Log.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — current project stack, NuGet versions, and Microsoft docs verified during this session.
- Architecture: HIGH — based on locked Phase 5 context plus existing codebase/service boundaries.
- Pitfalls: MEDIUM — most are grounded in project constraints and docs; some risk patterns are experience-based and tagged `[ASSUMED]`.

**Research date:** 2026-04-27
**Valid until:** 2026-05-04 for System.CommandLine/NuGet version choices; 2026-05-27 for project architecture and locked decisions.
