# Phase 3: Validation and Diagnostics - Research

**Researched:** 2026-04-27  
**Domain:** C#/.NET desktop validation, diagnostics, import/export previews, Avalonia/ReactiveUI MVVM  
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
## Implementation Decisions

### Health Report Shape
- **D-01:** Add an explicit Diagnostics panel/tab in the existing app shell for the project health report. Avoid a modal-only or text-only implementation because users need to inspect findings while navigating Templates and Morphs data.
- **D-02:** Model finding severity as `Blocker`, `Caution`, and `Info`. Use risk/action language rather than generic `Error`/`Warning` labels where possible.
- **D-03:** Organize findings by workflow area, such as Project, Profiles, Templates, Morphs/NPCs, Import, and Export. Each finding still carries severity.
- **D-04:** Keep findings read-only, but allow navigation/selection to affected presets, targets, NPCs, or output areas where practical, plus a copyable report. Do not implement auto-fix actions in Phase 3.

### Profile Diagnostics Tone
- **D-05:** Show profile diagnostics only in the explicit Diagnostics panel/report. Do not add ambient warning banners or normal-workflow warning copy to template generation.
- **D-06:** Do not implement slider-name mismatch heuristics or likely profile mismatch scoring in Phase 3. This is an intentional override/narrowing of `DIAG-02`; downstream agents should satisfy the requirement through concrete diagnostics for coverage, unknown sliders, injected defaults, multipliers, inversions, and fallback state.
- **D-07:** Provide profile diagnostics as summary plus drilldown: coverage counts, unknown slider counts, injected defaults, multipliers, inversions, neutral fallback state, and expandable slider-level details.
- **D-08:** Represent unbundled saved profiles as neutral fallback details: saved profile name, calculation fallback profile, affected presets, and current fallback behavior. Do not mark this as a warning/error or block generation/export.
- **D-09:** Run profile diagnostics at whole-project scope by default, with selected-preset drilldown/filtering rather than selected-preset-only diagnostics.

### NPC Import Preview
- **D-10:** Add an optional preview path for NPC text import rather than forcing all imports through preview-first. The current direct import workflow may remain available, but preview mode must parse into a temporary result and avoid mutating the NPC database/project until the user commits.
- **D-11:** NPC import preview should show both a summary and a row-level table for parsed rows, invalid lines, duplicates, fallback-decoded files/rows, and rows that would be added.
- **D-12:** Preserve the current duplicate policy by default: skip duplicate NPCs and explain them. Preview should identify whether duplicates occur within the file or against existing database/project rows when that can be determined.
- **D-13:** Keep import effects and assignment effects distinct. File import preview should state that importing adds to the NPC database only; assignment-changing commands should get their own before/after counts when they commit rows to morphs.
- **D-14:** Show fallback charset decoding as a per-file caution in preview/status, including the encoding name from parser results so users can review possible mojibake before commit.

### Export Risk Preview
- **D-15:** Export preview should show exact target paths, whether each file will be created or overwritten, and a generated-output effect summary/snippet before disk writes. Full content preview is optional at the planner's discretion but not required as the default.
- **D-16:** Require confirmation only for overwrite/risk cases, such as existing target files or multi-file batch situations where partial-output risk should be acknowledged. Routine create-new exports should avoid unnecessary confirmation friction.
- **D-17:** Save/export failure reporting should use an outcome ledger that identifies which files were written, restored, skipped, or left untouched, and includes the original exception plus rollback/incomplete state where known.
- **D-18:** Preserve existing atomic pair/batch write semantics in Phase 3 and expose them better through preview/result diagnostics. Do not redesign export transactions or change byte-sensitive writer output behavior unless planning proves a minimal non-formatting result API is necessary.
- **D-19:** Project save should not gain export-style preview friction. Improve save failure diagnostics to report target/outcome details where known; keep normal save flow smooth.

### the agent's Discretion
- Exact Diagnostics panel placement, styling, and navigation affordances, as long as it stays inside the existing shell and follows Avalonia compiled-binding/ReactiveUI conventions.
- Exact report DTO names and service boundaries, as long as reusable validation logic remains UI-free where practical and App code owns presentation/navigation.
- Exact preview snippet/summary format for generated output, as long as target paths and create/overwrite effects are visible before risky writes.

### Deferred Ideas (OUT OF SCOPE)
## Deferred Ideas

None — discussion stayed within Phase 3 validation and diagnostics scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DIAG-01 | User can run a read-only project validation report that identifies profile, preset, target, NPC assignment, reference, and export-readiness issues. | Implement a Core-first `ProjectValidationService` returning immutable findings grouped by workflow area and surfaced in the App Diagnostics panel. [VERIFIED: `.planning/REQUIREMENTS.md`; `03-CONTEXT.md`; `.planning/codebase/STRUCTURE.md`] |
| DIAG-02 | User can inspect profile diagnostics showing slider coverage, unknown sliders, injected defaults, multipliers, inversions, and likely profile mismatch indicators. | Treat likely mismatch indicators as out of scope per D-06; implement concrete profile drilldowns using catalog/default/multiplier/inversion tables and existing missing-default rows. [VERIFIED: `03-CONTEXT.md`; `TemplatesViewModel.cs`; `TemplateProfileCatalog.cs`] |
| DIAG-03 | User can preview NPC import results, including parsed rows, invalid lines, duplicates, charset fallback, and assignment effects before committing import changes. | Build preview from `NpcTextParser`/`NpcImportResult`, extend result detail for duplicate classification, and keep assignment previews separate from import previews. [VERIFIED: `NpcTextParser.cs`; `NpcImportResult.cs`; `03-CONTEXT.md`] |
| DIAG-04 | User can preview export destinations and exact output effects before writing files when the workflow involves risk of overwriting or partial output. | Add preview DTOs around existing generation and writer path rules; do not alter output writers' byte-sensitive content semantics. [VERIFIED: `BodyGenIniExportWriter.cs`; `BosJsonExportWriter.cs`; `03-CONTEXT.md`] |
| DIAG-05 | User receives actionable save/export failure messages that identify which files were written, restored, skipped, or left untouched. | Extend atomic write/report surfaces with an outcome ledger while preserving `File.Replace`/temp-file rollback semantics. [VERIFIED: `AtomicFileWriter.cs`; Microsoft File.Replace docs; `03-CONTEXT.md`] |
</phase_requirements>

## Summary

Phase 3 should be implemented as read-only diagnostic/result services plus App-layer presentation and command wiring. Core owns project validation, profile diagnostic facts, NPC import preview facts, export preview/result DTOs, and atomic outcome ledgers; App owns the Diagnostics tab/panel, navigation to selected rows/presets, copyable report formatting, and risk confirmation. [VERIFIED: `03-CONTEXT.md`; `.planning/codebase/STRUCTURE.md`; `AGENTS.md`]

Do not introduce new ecosystem libraries for validation, diffing, logging, JSON, XML, or MVVM. The project already standardizes on .NET 10/C# 14 App and tests, netstandard2.1/C# 13 Core, Avalonia 12 compiled bindings, ReactiveUI commands/properties, System.Text.Json, XDocument/LINQ to XML, xUnit v3, and FluentAssertions. [VERIFIED: `Directory.Packages.props`; `*.csproj`; `AGENTS.md`; NuGet API]

**Primary recommendation:** Add `BS2BG.Core/Diagnostics` result services and `BS2BG.App/ViewModels/DiagnosticsViewModel`, wired as a third shell workspace/tab, with no mutation until explicit import/export/save commit commands run. [VERIFIED: `03-CONTEXT.md`; `MainWindowViewModel.cs`; `TemplatesViewModel.cs`; `MorphsViewModel.cs`]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Project validation report | Core / Domain | App / ViewModel | Finding generation is project-state logic and must stay UI-free; App renders, filters, copies, and navigates. [VERIFIED: `AGENTS.md`; `.planning/codebase/STRUCTURE.md`] |
| Profile diagnostics | Core / Domain | App / ViewModel | Slider coverage/default/multiplier/inversion facts derive from `TemplateProfileCatalog` and model data; App provides summary/drilldown UI. [VERIFIED: `TemplateProfileCatalog.cs`; `TemplatesViewModel.cs`; `03-CONTEXT.md`] |
| NPC import preview | Core / Import | App / ViewModel | Parsing, invalid lines, encoding fallback, and duplicate facts belong next to `NpcTextParser`; App previews rows and commits via existing Morphs workflow. [VERIFIED: `NpcTextParser.cs`; `MorphsViewModel.cs`] |
| Assignment effect preview | App / ViewModel | Core / Morphs | Effects depend on current visible/selected scope and row identity from App workflow; actual assignment remains Core service behavior. [VERIFIED: `NpcBulkScopeResolver` grep results; `MorphsViewModel.cs`; Phase 2 decisions in `STATE.md`] |
| Export preview | Core / Export/Generation | App / ViewModel | Path/content summaries derive from existing writers/generation services; App owns confirmation dialogs and presentation. [VERIFIED: `BodyGenIniExportWriter.cs`; `BosJsonExportWriter.cs`; `MainWindowViewModel.cs`] |
| Save/export outcome ledger | Core / IO | App / ViewModel | `AtomicFileWriter` knows write/rollback state; App formats user messages. [VERIFIED: `AtomicFileWriter.cs`; Microsoft File.Replace docs] |
| Diagnostics panel navigation/copy | App / ViewModel/View | Core DTOs | Avalonia UI and clipboard services are App-only. [VERIFIED: `AGENTS.md`; `IClipboardService` references in `.planning/codebase/TESTING.md`] |

## Project Constraints (from AGENTS.md)

- Keep `BS2BG.Core` UI-free and keep Avalonia/platform code in `BS2BG.App`. [VERIFIED: `AGENTS.md`]
- Use Avalonia 12 compiled bindings; every AXAML root and `DataTemplate` needs `x:DataType`. [VERIFIED: `AGENTS.md`; Avalonia compiled bindings docs]
- Use ReactiveUI patterns already restored in the project: `ReactiveObject`, `[Reactive]`, `[ObservableAsProperty]`, `ReactiveCommand`, observable `canExecute`, and no RelayCommand/AsyncRelayCommand reintroduction. [VERIFIED: `AGENTS.md`; `openspec/specs/reactive-mvvm-conventions/spec.md`; ReactiveUI docs]
- Keep `MainWindow` as plain `Avalonia.Controls.Window`, not `ReactiveWindow`. [VERIFIED: `AGENTS.md`; `openspec/specs/reactive-mvvm-conventions/spec.md`]
- Do not edit sacred files without explicit caution: golden expected fixtures, `JavaFloatFormatting.cs`, `SliderMathFormatter.cs`, `BodyGenIniExportWriter.cs`, and `BosJsonExportWriter.cs`. [VERIFIED: `AGENTS.md`]
- Preserve byte-identical output semantics: INI CRLF, BoS JSON LF with no trailing newline, Java-like/minimal-json float formatting, half-up rounding, missing-default injection, and profile-specific tables. [VERIFIED: `AGENTS.md`; `TemplateGenerationService.cs`; export writer files]
- New tests use xUnit v3 and FluentAssertions; do not regenerate expected fixtures to silence failures. [VERIFIED: `AGENTS.md`; `.planning/codebase/TESTING.md`]
- Use PowerShell on Windows and never redirect to `nul`. [VERIFIED: `AGENTS.md`]
- Add XML doc comments on new or substantially rewritten methods, especially public APIs and non-trivial helpers. [VERIFIED: global `AGENTS.md`; `.planning/codebase/CONVENTIONS.md`]

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| .NET SDK / Target Framework | SDK 10.0.203 available; App/Tests `net10.0`; Core `netstandard2.1` | Build/runtime targets | Matches project target split and installed SDK. [VERIFIED: `dotnet --version`; `*.csproj`; `AGENTS.md`] |
| C# | App/Tests LangVersion 14; Core LangVersion 13 | Implementation language | Matches existing project files and portability boundary. [VERIFIED: `*.csproj`; `AGENTS.md`] |
| System.Text.Json | 10.0.7; published 2026-04-21 | Project/profile JSON serialization | Official .NET JSON library; high-performance, low-allocation design with UTF-8 support. [VERIFIED: NuGet API; Microsoft System.Text.Json overview] |
| System.Text.Encoding.CodePages | 10.0.7; published 2026-04-21 | NPC charset fallback support | Existing parser registers code pages for fallback decoding. [VERIFIED: NuGet API; `NpcTextParser.cs`] |
| XDocument / LINQ to XML | BCL | BodySlide XML parsing | Existing parser uses LINQ to XML; Microsoft documents `XElement.Load`/LINQ-to-XML file loading. [VERIFIED: `AGENTS.md`; Microsoft LINQ to XML docs] |
| AtomicFileWriter + File.Replace | Internal + BCL | Atomic save/export write/rollback semantics | Existing writes use temp files, `File.Replace`, backups, and batch rollback; Microsoft documents `File.Replace` as replacing a file and creating a backup. [VERIFIED: `AtomicFileWriter.cs`; Microsoft File.Replace docs] |

### App/UI
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Avalonia | 12.0.1; published 2026-04-13 | Desktop UI framework | Add Diagnostics workspace/tab, AXAML bindings, panels, DataTemplates. [VERIFIED: NuGet API; `Directory.Packages.props`; Avalonia docs] |
| Avalonia.Desktop / Themes.Fluent / Fonts.Inter | 12.0.1; published 2026-04-13 | Desktop shell/theme/font support | Continue existing shell styling; no new UI framework. [VERIFIED: NuGet API; `Directory.Packages.props`] |
| ReactiveUI.Avalonia | 12.0.1; published 2026-04-20 | ReactiveUI integration for Avalonia | Existing ViewModels use `ReactiveObject`, `ReactiveCommand`, and ReactiveUI source generation conventions. [VERIFIED: NuGet API; `Directory.Packages.props`; ReactiveUI docs; project OpenSpec] |
| ReactiveUI.SourceGenerators | 2.6.1 in project; published 2026-01-12 | `[Reactive]` and `[ObservableAsProperty]` source generation | Required by project convention; do not switch to Fody or CommunityToolkit. [VERIFIED: NuGet API; `AGENTS.md`; OpenSpec] |
| DynamicData | 9.4.31; published 2026-03-08 | Reactive collection support if needed | Already referenced; use only if a diagnostics collection needs incremental/reactive list transforms. [VERIFIED: NuGet API; `Directory.Packages.props`] |
| Microsoft.Extensions.DependencyInjection | 10.0.7; published 2026-04-21 | App service registration | Register diagnostics services/ViewModels in existing bootstrapper pattern. [VERIFIED: NuGet API; `AppBootstrapper.cs` in structure map] |

### Testing
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| xunit.v3 | 3.2.2; published 2026-01-14 | Unit/integration tests | Add Core service and ViewModel tests. [VERIFIED: NuGet API; `.planning/codebase/TESTING.md`] |
| Avalonia.Headless.XUnit | 12.0.1; published 2026-04-13 | Headless UI tests | Add shell exposure/accessibility tests for Diagnostics panel. [VERIFIED: NuGet API; `.planning/codebase/TESTING.md`] |
| FluentAssertions | 8.9.0; published 2026-03-16 | Assertions | Use `.Should()` style assertions. [VERIFIED: NuGet API; `.planning/codebase/TESTING.md`] |
| Microsoft.NET.Test.Sdk | 18.4.0; published 2026-04-07 | Test runner integration | Existing test project uses it. [VERIFIED: NuGet API; `BS2BG.Tests.csproj`] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Internal diagnostics DTOs | FluentValidation or validation framework | Not recommended: phase needs domain-specific readiness/report objects, not form validation; adding a library would not cover profile/export/import semantics. [VERIFIED: `03-CONTEXT.md`; `.planning/codebase/CONVENTIONS.md`] |
| Existing `AtomicFileWriter` | Transactional filesystem or custom transaction manager | Not recommended: context locks preserving atomic pair/batch semantics; Microsoft positions replace-with-temp as a common document-like atomic update approach while multi-file transactions remain hard. [VERIFIED: `03-CONTEXT.md`; Microsoft File.Replace docs; Microsoft TxF alternatives search result] |
| Existing ReactiveUI conventions | CommunityToolkit.Mvvm | Not allowed for this project despite generic Avalonia guidance; project explicitly standardizes on ReactiveUI. [VERIFIED: `AGENTS.md`; OpenSpec; Avalonia expert rules conflict noted] |
| Existing parser result pattern | Throwing exceptions for invalid user rows | Not recommended: existing parsers return diagnostics for recoverable input issues. [VERIFIED: `BodySlideXmlParser`/`NpcTextParser` patterns in codebase docs] |

**Installation:** No new packages are recommended. [VERIFIED: `Directory.Packages.props`; phase scope]

```powershell
# No package install needed. Verify current package graph instead:
dotnet restore BS2BG.sln
dotnet test --list-tests --no-restore
```

## Architecture Patterns

### System Architecture Diagram

```text
User opens Diagnostics tab / Preview command
        |
        v
App ViewModel command (ReactiveCommand; canExecute from observable state)
        |
        +--> ProjectValidationService (Core, read-only ProjectModel snapshot)
        |       |
        |       +--> Profile diagnostics: catalog coverage, unknown sliders, injected defaults, multipliers, inversions, fallback state
        |       +--> Reference diagnostics: missing/stale preset references, empty targets/NPC assignments, export readiness
        |
        +--> NpcImportPreviewService (Core parser + App existing DB comparison)
        |       |
        |       +--> parsed rows / invalid lines / file duplicates / existing duplicates / fallback encoding
        |       +--> Commit path calls existing Morphs import/database mutation only after user action
        |
        +--> ExportPreviewService (Core generation + writer path rules)
        |       |
        |       +--> target paths / create-vs-overwrite / generated snippets / batch-risk flags
        |       +--> Commit path calls existing writers; outcome ledger records written/restored/skipped/untouched
        |
        v
DiagnosticsViewModel exposes grouped findings, summaries, row details, copyable text
        |
        v
Avalonia Diagnostics workspace/tab with compiled bindings and optional navigation selection
```

This flow keeps diagnostics read-only until an explicit commit command calls existing mutation/export services. [VERIFIED: `03-CONTEXT.md`; `MainWindowViewModel.cs`; `MorphsViewModel.cs`; `TemplatesViewModel.cs`]

### Recommended Project Structure

```text
src/
├── BS2BG.Core/
│   ├── Diagnostics/                 # Project/profile validation report services and DTOs
│   │   ├── ProjectValidationService.cs
│   │   ├── DiagnosticFinding.cs
│   │   ├── DiagnosticSeverity.cs     # Blocker, Caution, Info
│   │   ├── ProfileDiagnosticsService.cs
│   │   └── ExportPreviewService.cs
│   ├── Import/
│   │   ├── NpcImportPreviewService.cs # Reuses NpcTextParser; no project mutation
│   │   └── NpcImportPreviewResult.cs
│   └── IO/
│       └── WriteOutcomeLedger.cs     # If AtomicFileWriter needs non-formatting result API
├── BS2BG.App/
│   ├── ViewModels/
│   │   └── DiagnosticsViewModel.cs    # Presentation state, copy/nav commands, preview summaries
│   ├── Services/
│   │   └── DiagnosticsReportFormatter.cs # App copyable report formatting if UI-specific
│   └── Views/
│       └── MainWindow.axaml           # Third workspace/tab or included panel
└── BS2BG.Tests/
    ├── ProjectValidationServiceTests.cs
    ├── ProfileDiagnosticsServiceTests.cs
    ├── NpcImportPreviewServiceTests.cs
    ├── ExportPreviewServiceTests.cs
    ├── AtomicFileWriterOutcomeTests.cs
    └── DiagnosticsViewModelTests.cs
```

This structure follows the existing Core/App/Test split and flat test layout. [VERIFIED: `.planning/codebase/STRUCTURE.md`; `.planning/codebase/TESTING.md`]

### Pattern 1: Immutable diagnostic findings in Core
**What:** Return structured findings with area, severity, title, detail, affected entity key, and optional action hint. [VERIFIED: `03-CONTEXT.md`]  
**When to use:** Every validation/check path that can produce user-facing report rows. [VERIFIED: `03-CONTEXT.md`]

```csharp
// Source: project result-object pattern in BodySlideXmlImportResult/NpcImportResult; severity names from 03-CONTEXT.md.
namespace BS2BG.Core.Diagnostics;

public enum DiagnosticSeverity
{
    Blocker,
    Caution,
    Info
}

public sealed class DiagnosticFinding(
    DiagnosticSeverity severity,
    string area,
    string title,
    string detail,
    string? targetKey = null)
{
    public DiagnosticSeverity Severity { get; } = severity;
    public string Area { get; } = area ?? throw new ArgumentNullException(nameof(area));
    public string Title { get; } = title ?? throw new ArgumentNullException(nameof(title));
    public string Detail { get; } = detail ?? throw new ArgumentNullException(nameof(detail));
    public string? TargetKey { get; } = targetKey;
}
```

### Pattern 2: Preview services never mutate project state
**What:** Preview services clone or materialize inputs, compute summaries, and return result DTOs; commit commands call existing mutation paths later. [VERIFIED: `03-CONTEXT.md`; `ExportBosJsonAsync` snapshot pattern in `MainWindowViewModel.cs`]  
**When to use:** NPC import preview, export preview, assignment effect preview, and validation report refresh. [VERIFIED: `03-CONTEXT.md`]

```csharp
// Source: MainWindowViewModel snapshots presets before background BoS write; NpcTextParser already returns parsed result DTOs.
public sealed class NpcImportPreviewService(NpcTextParser parser)
{
    public NpcImportPreviewResult PreviewFile(string path, IReadOnlyCollection<Npc> existingNpcs)
    {
        var parsed = parser.ParseFile(path);
        return NpcImportPreviewResult.FromParsedResult(path, parsed, existingNpcs);
    }
}
```

### Pattern 3: ReactiveUI command and derived-state wiring
**What:** Use `ReactiveCommand.Create`/`CreateFromTask`, observable `canExecute`, `ThrownExceptions` subscriptions for recoverable UI failures, and `ToProperty` for derived state. [CITED: https://www.reactiveui.net/docs/handbook/commands/; https://www.reactiveui.net/docs/handbook/when-any; VERIFIED: OpenSpec]  
**When to use:** Diagnostics refresh, copy report, preview import, commit import preview, preview export, confirm risky export. [VERIFIED: `03-CONTEXT.md`]

```csharp
// Source: ReactiveUI commands/WhenAny docs and existing MainWindowViewModel pattern.
var canRefresh = this.WhenAnyValue(x => x.IsBusy).Select(isBusy => !isBusy);
RefreshDiagnosticsCommand = ReactiveCommand.CreateFromTask(RefreshDiagnosticsAsync, canRefresh);
RefreshDiagnosticsCommand.ThrownExceptions
    .Subscribe(ex => StatusMessage = "Refreshing diagnostics failed: " + FormatExceptionMessage(ex))
    .DisposeWith(disposables);

_hasBlockersHelper = this.WhenAnyValue(x => x.Findings)
    .Select(findings => findings.Any(f => f.Severity == DiagnosticSeverity.Blocker))
    .ToProperty(this, x => x.HasBlockers);
```

### Pattern 4: Export preview wraps generation; writers remain byte-sensitive
**What:** Use `TemplateGenerationService.GenerateTemplates`, `MorphGenerationService.GenerateMorphs`, and `PreviewBosJson` to produce snippets/summaries before writing; do not change `SliderMathFormatter` or writer formatting. [VERIFIED: `TemplateGenerationService.cs`; `BodyGenIniExportWriter.cs`; `BosJsonExportWriter.cs`; `AGENTS.md`]  
**When to use:** DIAG-04 and risky overwrite confirmation. [VERIFIED: `03-CONTEXT.md`]

```csharp
// Source: TemplateGenerationService and writer path rules.
var templatesPath = Path.Combine(directoryPath, "templates.ini");
var morphsPath = Path.Combine(directoryPath, "morphs.ini");
var templatesText = templateGenerationService.GenerateTemplates(project.SliderPresets, catalog, omitRedundant);
var morphs = morphGenerationService.GenerateMorphs(project);
var preview = new BodyGenExportPreview(
    templatesPath,
    File.Exists(templatesPath),
    morphsPath,
    File.Exists(morphsPath),
    templatesText.Split("\r\n").Take(3).ToArray(),
    morphs.Text.Split("\r\n").Take(3).ToArray());
```

### Anti-Patterns to Avoid
- **Ambient profile warnings outside Diagnostics:** Phase 3 explicitly forbids normal-workflow warning banners and mismatch scoring. [VERIFIED: `03-CONTEXT.md`]
- **Mutating live NPC database during preview:** Preview must parse into temporary result and avoid project mutation until commit. [VERIFIED: `03-CONTEXT.md`]
- **Changing writer output to make previews easier:** Export preview must wrap existing generation/writer rules, not alter byte-sensitive output. [VERIFIED: `AGENTS.md`; `03-CONTEXT.md`]
- **Using WPF assumptions in Avalonia:** Avalonia uses `.axaml`, compiled bindings with `x:DataType`, `IsVisible` instead of WPF `Visibility`, and style selectors/pseudo-classes. [CITED: Avalonia expert rules; Avalonia docs]
- **Reintroducing RelayCommand/AsyncRelayCommand:** Project OpenSpec forbids custom App command implementations. [VERIFIED: OpenSpec; `AGENTS.md`]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| MVVM command infrastructure | Custom `RelayCommand` or manual `ICommand` | `ReactiveCommand.Create*` | Existing project contract and ReactiveUI docs cover async, `IsExecuting`, `ThrownExceptions`, and observable `canExecute`. [VERIFIED: OpenSpec; CITED: ReactiveUI docs] |
| Derived ViewModel state | Manual event subscriptions assigning backing fields | `WhenAnyValue`/`Observable.CombineLatest(...).ToProperty(...)` | ReactiveUI docs identify `ToProperty` as idiomatic for calculated read-only properties. [CITED: ReactiveUI WhenAny docs] |
| JSON parsing/serialization | Custom JSON writer/parser | `System.Text.Json` | Official .NET library provides JSON serialization/deserialization and UTF-8 support; project already uses it. [CITED: Microsoft System.Text.Json overview; VERIFIED: `BS2BG.Core.csproj`] |
| BodySlide XML parsing | Custom string splitting XML parser | XDocument/LINQ to XML | Existing parser uses XML APIs and Microsoft documents LINQ-to-XML loading. [VERIFIED: `AGENTS.md`; CITED: Microsoft LINQ to XML docs] |
| NPC charset fallback from scratch | New encoding detector or lossy guesses | Existing `NpcTextParser` + `System.Text.Encoding.CodePages` | Parser already exposes `UsedFallbackEncoding` and `EncodingName`; context only needs clearer preview/status. [VERIFIED: `NpcTextParser.cs`; `NpcImportResult.cs`; `03-CONTEXT.md`] |
| Atomic file replacement | Ad hoc write-over-target or delete/copy | Existing `AtomicFileWriter` + `File.Replace` | Microsoft documents replace-with-backup semantics; existing tests cover rollback cases. [CITED: Microsoft File.Replace docs; VERIFIED: `AtomicFileWriter.cs`; test discovery] |
| Export content preview by reformatting | Separate preview formatter | Existing `TemplateGenerationService`/`MorphGenerationService`/`PreviewBosJson` | Separate formatting risks Java parity drift. [VERIFIED: `TemplateGenerationService.cs`; `AGENTS.md`] |
| Mocking parity-critical services | Mocks for formatter/export writers | Real Core services and temp files | Project testing rules say not to mock `SliderMathFormatter`, `JavaFloatFormatting`, or export writers. [VERIFIED: `.planning/codebase/TESTING.md`] |

**Key insight:** Phase 3 is mostly an observability layer over existing Core behavior; custom replacement logic would create a second implementation of import/generation/export semantics and weaken the parity contract. [VERIFIED: `03-CONTEXT.md`; `AGENTS.md`]

## Common Pitfalls

### Pitfall 1: Diagnostics accidentally become policy changes
**What goes wrong:** Findings block generation/export or introduce warnings in normal template UI. [VERIFIED: `03-CONTEXT.md`]  
**Why it happens:** `DIAG-02` wording mentions likely mismatch indicators, but context D-06 intentionally narrows that scope. [VERIFIED: `03-CONTEXT.md`; `.planning/REQUIREMENTS.md`]  
**How to avoid:** Keep all profile diagnostics inside Diagnostics panel and mark unbundled fallback as neutral detail, not `Blocker`/`Caution`. [VERIFIED: `03-CONTEXT.md`]  
**Warning signs:** New strings containing "mismatch", "experimental", or normal-workflow warnings in Templates UI. [VERIFIED: Phase 1 tests listed by test discovery]

### Pitfall 2: Preview mutates live collections
**What goes wrong:** NPC preview adds rows, changes assignments, alters dirty state, or records undo entries before commit. [VERIFIED: `03-CONTEXT.md`; `MorphsViewModel.cs`]  
**Why it happens:** Existing direct import path immediately calls `AddNpcsToDatabase`. [VERIFIED: `MorphsViewModel.cs` lines 1038-1075]  
**How to avoid:** Implement separate preview result path and only call existing mutation methods in commit. [VERIFIED: `03-CONTEXT.md`]  
**Warning signs:** Preview command changes `NpcDatabase.Count`, `MorphedNpcs`, `project.IsDirty`, or undo history. [VERIFIED: Phase 2 undo/dirty constraints in `STATE.md`]

### Pitfall 3: Duplicate NPC diagnostics lose source information
**What goes wrong:** Preview cannot distinguish duplicate rows within the file from duplicates against existing database/project rows. [VERIFIED: `03-CONTEXT.md`; `NpcTextParser.cs`]  
**Why it happens:** Current parser silently skips duplicate `(mod, editorId)` rows within a file. [VERIFIED: `NpcTextParser.cs` lines 94-96]  
**How to avoid:** Extend parser/preview result to preserve skipped duplicate diagnostics with line/file/key; classify existing duplicates in preview service. [VERIFIED: `03-CONTEXT.md`; `NpcTextParser.cs`]  
**Warning signs:** Preview summary has skipped counts but no row-level duplicate explanation. [VERIFIED: `03-CONTEXT.md`]

### Pitfall 4: Export preview changes output formatting
**What goes wrong:** Preview path uses different newline, ordering, filename, float, or JSON formatting than commit path. [VERIFIED: `AGENTS.md`; export writer files]  
**Why it happens:** A separate preview formatter is tempting for snippets. [ASSUMED]  
**How to avoid:** Generate snippets from exact existing generation services and writer path/name rules. [VERIFIED: `TemplateGenerationService.cs`; `BosJsonExportWriter.cs`; `BodyGenIniExportWriter.cs`]  
**Warning signs:** Preview test expected strings differ from export writer output or golden fixtures. [VERIFIED: `.planning/codebase/TESTING.md`]

### Pitfall 5: Multi-file outcome reporting overpromises atomicity
**What goes wrong:** UI says all files rolled back even when rollback/incomplete state is unknown. [VERIFIED: `03-CONTEXT.md`; `AtomicFileWriter.cs`]  
**Why it happens:** `WriteAtomicBatch` attempts rollback but can throw `AggregateException` if rollback is incomplete. [VERIFIED: `AtomicFileWriter.cs` lines 112-139]  
**How to avoid:** Outcome ledger must include `Written`, `Restored`, `Skipped`, `LeftUntouched`, and `Unknown/Incomplete` states plus original and rollback exceptions. [VERIFIED: `03-CONTEXT.md`; `AtomicFileWriter.cs`]  
**Warning signs:** Catch block formats only `exception.Message` without file state details. [VERIFIED: `MainWindowViewModel.cs` lines 480-512 and 559-562]

### Pitfall 6: Avalonia compiled binding breakage in a large AXAML file
**What goes wrong:** Diagnostics UI build fails or silently opts out of compiled binding. [CITED: Avalonia compiled binding docs]  
**Why it happens:** Project-wide compiled binding requires `x:DataType` in binding scopes and DataTemplates. [CITED: Avalonia XAML compilation docs; VERIFIED: `BS2BG.App.csproj`]  
**How to avoid:** Add `x:DataType` to Diagnostics panel root/templates and use `{ReflectionBinding}` only for justified dynamic scenarios. [CITED: Avalonia docs]  
**Warning signs:** AVLN compile errors about missing DataType or unresolved properties. [CITED: Avalonia XAML compilation docs]

## Code Examples

### Project validation service shape
```csharp
// Source: parser/result-object pattern in current Core; D-02 severity names from 03-CONTEXT.md.
public sealed class ProjectValidationService(TemplateProfileCatalog profileCatalog)
{
    public ProjectValidationReport Validate(ProjectModel project)
    {
        ArgumentNullException.ThrowIfNull(project);

        var findings = new List<DiagnosticFinding>();
        AddProfileFindings(project, findings);
        AddReferenceFindings(project, findings);
        AddExportReadinessFindings(project, findings);
        return new ProjectValidationReport(findings);
    }
}
```

### NPC import preview classification
```csharp
// Source: NpcTextParser exposes Npcs, Diagnostics, UsedFallbackEncoding, EncodingName.
var parsed = npcTextParser.ParseFile(path);
var existingKeys = existingNpcs.ToHashSet(NpcIdentityComparer.ModEditorIdIgnoreCase);
var rowsToAdd = parsed.Npcs.Where(npc => !existingKeys.Contains(npc)).ToArray();
var existingDuplicates = parsed.Npcs.Where(existingKeys.Contains).ToArray();
var fallbackFinding = parsed.UsedFallbackEncoding
    ? new DiagnosticFinding(DiagnosticSeverity.Caution, "Import", "Fallback decoding used", parsed.EncodingName)
    : null;
```

### Export outcome ledger states
```csharp
// Source: AtomicFileWriter currently tracks committed entries and rollback exceptions.
public enum FileWriteOutcome
{
    Planned,
    Written,
    Restored,
    Skipped,
    LeftUntouched,
    Incomplete
}

public sealed class FileWriteLedgerEntry(string path, FileWriteOutcome outcome, string? detail = null)
{
    public string Path { get; } = path ?? throw new ArgumentNullException(nameof(path));
    public FileWriteOutcome Outcome { get; } = outcome;
    public string? Detail { get; } = detail;
}
```

### Avalonia DataTemplate compiled binding
```xml
<!-- Source: Avalonia x:DataType/DataTemplate compiled binding docs; project compiled binding rule. -->
<DataTemplate x:DataType="diag:DiagnosticFindingViewModel">
  <Grid ColumnDefinitions="Auto,*,Auto">
    <TextBlock Text="{Binding SeverityLabel}" />
    <TextBlock Grid.Column="1" Text="{Binding Title}" />
    <Button Grid.Column="2"
            Content="Go"
            IsVisible="{Binding CanNavigate}"
            Command="{Binding NavigateCommand}" />
  </Grid>
</DataTemplate>
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| RelayCommand/AsyncRelayCommand and manual property setters | ReactiveUI `ReactiveCommand`, `[Reactive]`, `[ObservableAsProperty]`, observable `canExecute` | Locked by `restore-reactiveui-patterns` before Phase 3 | New diagnostics ViewModels must follow ReactiveUI source-generator conventions. [VERIFIED: `AGENTS.md`; OpenSpec] |
| Runtime/reflection-heavy Avalonia binding | Compiled bindings with `x:DataType` | Project-wide in `BS2BG.App.csproj` | Diagnostics AXAML binding errors should be compile-time failures. [VERIFIED: `BS2BG.App.csproj`; CITED: Avalonia docs] |
| Generic Error/Warning labels | `Blocker`, `Caution`, `Info` risk/action language | Phase 3 D-02 | Diagnostics UX should not use generic severity names where avoidable. [VERIFIED: `03-CONTEXT.md`] |
| Direct import only | Optional no-mutation preview plus existing direct import allowed | Phase 3 D-10 | Planner should add preview command without forcing every import through preview. [VERIFIED: `03-CONTEXT.md`] |
| Simple exception status for save/export | Outcome ledger with file states and exceptions | Phase 3 D-17 | Save/export failures need per-file state details. [VERIFIED: `03-CONTEXT.md`; `MainWindowViewModel.cs`] |

**Deprecated/outdated:**
- `Avalonia.Diagnostics` package: Avalonia expert rules say it is deprecated and recommend `AvaloniaUI.DiagnosticsSupport` + Developer Tools; this phase does not need to add DevTools packages for user diagnostics. [CITED: Avalonia expert rules]
- WPF `Visibility`, `DataTrigger`, `DependencyProperty`, and `.xaml` assumptions: not valid Avalonia patterns. [CITED: Avalonia expert rules]
- Profile mismatch scoring in Phase 3: explicitly out of scope despite roadmap wording. [VERIFIED: `03-CONTEXT.md`]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | A separate preview formatter is tempting for snippets. | Common Pitfalls | Low; this is a planning caution, not an implementation fact. |

## Open Questions

1. **Should `AtomicFileWriter` return a ledger directly or throw a custom exception carrying a ledger?**
   - What we know: D-17 requires per-file written/restored/skipped/untouched detail, and current writer throws raw/aggregate exceptions. [VERIFIED: `03-CONTEXT.md`; `AtomicFileWriter.cs`]
   - What's unclear: Minimal API shape that preserves existing tests while exposing ledger on failure. [ASSUMED]
   - Recommendation: Use a custom `AtomicWriteException : IOException` with `IReadOnlyList<FileWriteLedgerEntry>` if changing success return types is too disruptive; otherwise add result-returning overloads and keep existing void methods as wrappers. [ASSUMED]

2. **How much generated content should export preview show by default?**
   - What we know: D-15 requires paths, create/overwrite state, and summary/snippet; full content preview is optional. [VERIFIED: `03-CONTEXT.md`]
   - What's unclear: Exact UI size/placement. [VERIFIED: `03-CONTEXT.md` discretion]
   - Recommendation: Show first 3 non-empty lines per file/category plus counts and a copy/full-view affordance only if cheap. [ASSUMED]

3. **Should duplicate classification live inside `NpcTextParser` or a higher-level preview service?**
   - What we know: Parser currently de-dupes within parsed file silently and App `AddNpcsToDatabase` skips existing duplicates. [VERIFIED: `NpcTextParser.cs`; `MorphsViewModel.cs`]
   - What's unclear: Whether parser should expose within-file duplicate diagnostics for direct imports too. [ASSUMED]
   - Recommendation: Add parser diagnostics for within-file duplicates and preview-service classification for existing database/project duplicates. [VERIFIED: `03-CONTEXT.md`; ASSUMED API placement]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| .NET SDK | Build/test/implementation | ✓ | 10.0.203 | None needed. [VERIFIED: `dotnet --version`] |
| NuGet package feed | Version verification/restore | ✓ | NuGet V3 API reachable | Use checked-in central package versions if offline. [VERIFIED: NuGet API calls; `Directory.Packages.props`] |
| Avalonia Headless test support | UI shell tests | ✓ | 12.0.1 package referenced | Use ViewModel tests if shell test not needed. [VERIFIED: `BS2BG.Tests.csproj`; NuGet API] |
| PowerShell | Project command environment | ✓ | Platform shell via pwsh | None; project requires PowerShell on Windows. [VERIFIED: environment; `AGENTS.md`] |

**Missing dependencies with no fallback:** None found. [VERIFIED: environment audit]

**Missing dependencies with fallback:** None found. [VERIFIED: environment audit]

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | xUnit v3 3.2.2 + FluentAssertions 8.9.0 + Avalonia.Headless.XUnit 12.0.1. [VERIFIED: NuGet API; `.planning/codebase/TESTING.md`] |
| Config file | `tests/BS2BG.Tests/BS2BG.Tests.csproj`; no separate test config detected. [VERIFIED: `.planning/codebase/TESTING.md`; test discovery] |
| Quick run command | `dotnet test --filter FullyQualifiedName~Diagnostics -x` [ASSUMED new test name pattern] |
| Full suite command | `dotnet test` [VERIFIED: `AGENTS.md`; `.planning/codebase/TESTING.md`] |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| DIAG-01 | Read-only validation report groups profile/preset/target/NPC/reference/export findings. | unit + ViewModel | `dotnet test --filter FullyQualifiedName~ProjectValidationServiceTests -x` | ❌ Wave 0 [VERIFIED: test discovery] |
| DIAG-02 | Profile diagnostics show coverage, unknown sliders, injected defaults, multipliers, inversions, fallback details, no mismatch scoring. | unit + ViewModel | `dotnet test --filter FullyQualifiedName~ProfileDiagnosticsServiceTests -x` | ❌ Wave 0 [VERIFIED: test discovery] |
| DIAG-03 | NPC import preview reports rows, invalid lines, duplicate types, fallback encoding, and no mutation before commit. | unit + ViewModel | `dotnet test --filter FullyQualifiedName~NpcImportPreviewServiceTests -x` | ❌ Wave 0 [VERIFIED: test discovery] |
| DIAG-04 | Export preview reports paths, create/overwrite state, snippets, and risky confirmation only for overwrite/batch risk. | unit + ViewModel | `dotnet test --filter FullyQualifiedName~ExportPreviewServiceTests -x` | ❌ Wave 0 [VERIFIED: test discovery] |
| DIAG-05 | Save/export failure messages include file outcome ledger states. | unit + ViewModel | `dotnet test --filter FullyQualifiedName~AtomicFileWriterOutcomeTests -x` | ❌ Wave 0 [VERIFIED: test discovery] |

### Sampling Rate
- **Per task commit:** `dotnet test --filter FullyQualifiedName~{ChangedSubjectTests} -x` for focused test class. [VERIFIED: `.planning/codebase/TESTING.md`]
- **Per wave merge:** `dotnet test`. [VERIFIED: `AGENTS.md`]
- **Phase gate:** Full suite green before `/gsd-verify-work`. [VERIFIED: GSD workflow]

### Wave 0 Gaps
- [ ] `tests/BS2BG.Tests/ProjectValidationServiceTests.cs` — covers DIAG-01. [VERIFIED: test discovery]
- [ ] `tests/BS2BG.Tests/ProfileDiagnosticsServiceTests.cs` — covers DIAG-02. [VERIFIED: test discovery]
- [ ] `tests/BS2BG.Tests/NpcImportPreviewServiceTests.cs` — covers DIAG-03. [VERIFIED: test discovery]
- [ ] `tests/BS2BG.Tests/ExportPreviewServiceTests.cs` — covers DIAG-04. [VERIFIED: test discovery]
- [ ] `tests/BS2BG.Tests/AtomicFileWriterOutcomeTests.cs` — covers DIAG-05. [VERIFIED: test discovery]
- [ ] `tests/BS2BG.Tests/DiagnosticsViewModelTests.cs` and one `AppShellTests` case — covers Diagnostics panel exposure, copy/report, and compiled binding. [VERIFIED: existing test patterns]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no | No accounts/authentication in local offline desktop workflow. [VERIFIED: `.planning/PROJECT.md`; `.planning/REQUIREMENTS.md` out-of-scope] |
| V3 Session Management | no | No sessions. [VERIFIED: `.planning/PROJECT.md`] |
| V4 Access Control | no | No multi-user authorization boundary; OS filesystem permissions apply. [VERIFIED: local desktop architecture in `PROJECT.md`] |
| V5 Input Validation | yes | Parser/result diagnostics for XML/NPC/project/profile input; validate paths and reserved filenames through existing writer rules. [VERIFIED: `BodySlideXmlParser`/`NpcTextParser` patterns; `BosJsonExportWriter.cs`] |
| V6 Cryptography | no | Phase does not add signing/hashing/encryption; do not hand-roll crypto. [VERIFIED: Phase 3 scope; `.planning/REQUIREMENTS.md`] |
| V8 Data Protection | yes | Avoid writing during preview; copyable reports should not add private local paths beyond user-selected paths already in workflow. [VERIFIED: `03-CONTEXT.md`; `.planning/REQUIREMENTS.md` privacy out-of-scope] |
| V12 File and Resources | yes | Preserve atomic write/rollback semantics; report exact paths and outcome states; guard against invalid/reserved export names. [VERIFIED: `AtomicFileWriter.cs`; `BosJsonExportWriter.cs`; Microsoft File.Replace docs] |

### Known Threat Patterns for local file diagnostics

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed or huge local input causes CPU/memory pressure | Denial of Service | Keep parse diagnostics recoverable; add size/stress tests where practical; avoid UI-thread parsing. [VERIFIED: `.planning/codebase/CONCERNS.md`; `NpcTextParser.cs`] |
| Preview writes files accidentally | Tampering | Enforce preview services as read-only and test no project/file mutation before commit. [VERIFIED: `03-CONTEXT.md`] |
| Path confusion during export/save | Tampering / Information Disclosure | Display exact target paths before risky overwrites and ledger paths after failures. [VERIFIED: `03-CONTEXT.md`] |
| Partial multi-file output after lock/permission failure | Tampering | Use existing atomic batch writer and ledger incomplete rollback states. [VERIFIED: `AtomicFileWriter.cs`; Microsoft File.Replace docs] |
| Mojibake from fallback encoding | Integrity | Surface fallback encoding name as caution before commit. [VERIFIED: `NpcImportResult.cs`; `03-CONTEXT.md`] |

## Sources

### Primary (HIGH confidence)
- `J:\jBS2BG\AGENTS.md` — project stack, sacred files, byte-identical output constraints, ReactiveUI/Avalonia conventions, test rules. [VERIFIED]
- `.planning/phases/03-validation-and-diagnostics/03-CONTEXT.md` — locked Phase 3 decisions D-01 through D-19 and discretion. [VERIFIED]
- `.planning/REQUIREMENTS.md` — DIAG-01 through DIAG-05. [VERIFIED]
- `.planning/codebase/{STRUCTURE,CONVENTIONS,TESTING,CONCERNS}.md` — project structure, patterns, tests, risks. [VERIFIED]
- `src/BS2BG.Core/Import/NpcTextParser.cs`, `NpcImportResult.cs` — parser result/fallback/duplicate behavior. [VERIFIED]
- `src/BS2BG.Core/IO/AtomicFileWriter.cs` — atomic single/pair/batch behavior and rollback exception shape. [VERIFIED]
- `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `BosJsonExportWriter.cs` — path/write/format constraints. [VERIFIED]
- `src/BS2BG.Core/Generation/TemplateGenerationService.cs`, `TemplateProfileCatalog.cs` — preview/generation/fallback behavior. [VERIFIED]
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `TemplatesViewModel.cs`, `MorphsViewModel.cs` — current commands, status messages, import/export flows, snapshots. [VERIFIED]
- NuGet V3 API — current package versions and publish timestamps for listed packages. [VERIFIED]
- Avalonia docs — compiled bindings, `x:DataType`, DataTemplate, common Avalonia pitfalls. [CITED: https://docs.avaloniaui.net/docs/xaml/compilation; https://docs.avaloniaui.net/docs/xaml/directives; https://docs.avaloniaui.net/docs/migration/wpf/data-templates]
- ReactiveUI docs — `ReactiveCommand`, `ThrownExceptions`, `IsExecuting`, observable `canExecute`, `WhenAnyValue`, `ToProperty`. [CITED: https://www.reactiveui.net/docs/handbook/commands/; https://www.reactiveui.net/docs/handbook/when-any]
- Microsoft Learn — `File.Replace`, System.Text.Json overview, LINQ to XML file loading. [CITED: https://learn.microsoft.com/dotnet/api/system.io.file.replace?view=net-10.0; https://learn.microsoft.com/dotnet/standard/serialization/system-text-json/overview; https://learn.microsoft.com/dotnet/standard/linq/load-xml-file]

### Secondary (MEDIUM confidence)
- Avalonia expert rules tool — broad Avalonia pitfalls and DevTools note; project-specific ReactiveUI rules override generic CommunityToolkit recommendation. [CITED: avalonia-docs expert rules; VERIFIED override in `AGENTS.md`]
- Microsoft TxF alternatives search result — confirms replace-with-temp is a common document-like atomic update pattern and multi-file transactional updates are harder. [CITED: Microsoft Learn search result]

### Tertiary (LOW confidence)
- Assumed API placement recommendations for outcome ledger and preview snippets. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified from project files, NuGet API, and official docs. [VERIFIED]
- Architecture: HIGH — constrained by phase context and existing Core/App split. [VERIFIED]
- Pitfalls: HIGH for project-specific pitfalls, MEDIUM for implementation-shape caveats that depend on future API choices. [VERIFIED; ASSUMED where noted]

**Research date:** 2026-04-27  
**Valid until:** 2026-05-27 for project-internal architecture; 2026-05-04 for package/version freshness. [ASSUMED]
