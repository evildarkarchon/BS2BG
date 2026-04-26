# Coding Conventions

**Analysis Date:** 2026-04-26

## Naming Patterns

**Files:**
- Use PascalCase for C# types and match the primary public type name: `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `tests/BS2BG.Tests/SliderMathFormatterTests.cs`.
- Prefix interfaces with `I`: `src/BS2BG.App/Services/IClipboardService.cs`, `src/BS2BG.Core/Morphs/IRandomAssignmentProvider.cs`.
- Avalonia views use `.axaml` plus `.axaml.cs` code-behind: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs`.
- Keep test classes under `tests/BS2BG.Tests/` with a `Tests` suffix matching the unit under test: `tests/BS2BG.Tests/BodySlideXmlParserTests.cs`, `tests/BS2BG.Tests/AppShellTests.cs`.

**Functions:**
- Public methods use PascalCase and describe behavior with verb phrases: `ParseString` in `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `GenerateTemplates` in `src/BS2BG.Core/Generation/TemplateGenerationService.cs`, `ImportPresetsAsync` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`.
- Async methods end in `Async`: `PickOpenProjectFileAsync` in `src/BS2BG.App/Services/WindowFileDialogService.cs`, `SaveProjectAsync` in `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`.
- Boolean-returning methods use `Try`, `Can`, `Has`, or action-result names: `TryRenameSelectedPreset` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `TryValidateName` in `src/BS2BG.Core/Models/SliderPreset.cs`.
- Private helpers use PascalCase like public members: `AddSetSlider` in `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `CreateTempPath` in `src/BS2BG.Core/IO/AtomicFileWriter.cs`.

**Variables:**
- Private fields use camelCase without `_` except ReactiveUI source-generator backing fields, which use `_camelCase`: `project`, `templateGenerationService`, and `_selectedPreset` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`.
- Prefer `var` for locals, including built-in types and apparent types, per `.editorconfig`.
- Use descriptive local names for domain values: `presetElement`, `sliderElement`, `diagnostics` in `src/BS2BG.Core/Import/BodySlideXmlParser.cs`; `directoryPath`, `snapshot` in `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`.
- Use `cancellationToken` as the parameter name for cancellable async APIs: `src/BS2BG.App/Services/WindowFileDialogService.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.

**Types:**
- Classes, records, enums, and enum members use PascalCase: `SliderPreset`, `ProjectProfileMapping`, `NpcFilterColumn.Name` in `src/BS2BG.Core/Models/SliderPreset.cs` and `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- Service classes end in `Service`, parser classes end in `Parser`, writer classes end in `Writer`, and ViewModels end in `ViewModel`: `src/BS2BG.Core/Serialization/ProjectFileService.cs`, `src/BS2BG.Core/Import/NpcTextParser.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`.
- Domain model classes live in `src/BS2BG.Core/Models/` and should stay UI-independent: `src/BS2BG.Core/Models/ProjectModel.cs`, `src/BS2BG.Core/Models/Npc.cs`.

## Code Style

**Formatting:**
- `.editorconfig` is the source of truth: UTF-8, LF by default, final newline, spaces, 4-space C# indentation, 2-space XML/AXAML/JSON/MSBuild indentation, CRLF for PowerShell and `.sln` files.
- Use Allman braces for C# (`csharp_new_line_before_open_brace = all`) as shown in `src/BS2BG.Core/Import/BodySlideXmlParser.cs`.
- Use file-scoped namespaces: `namespace BS2BG.Core.Import;` in `src/BS2BG.Core/Import/BodySlideXmlParser.cs` and `namespace BS2BG.App.ViewModels;` in `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`.
- Keep nullable enabled and satisfy nullable warnings; `Directory.Build.props` sets `<Nullable>enable</Nullable>` and `.editorconfig` elevates key CS8600-CS8603 diagnostics to warnings.
- Use target-typed `new()` where it improves readability: `private readonly CompositeDisposable disposables = new();` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`.
- Preserve byte-identical formatting contracts in output code: INI writers use CRLF in `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`; BoS JSON uses LF/no trailing newline in `src/BS2BG.Core/Export/BosJsonExportWriter.cs` and `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`.

**Linting:**
- .NET analyzers are enabled globally in `Directory.Build.props` with `<EnableNETAnalyzers>true</EnableNETAnalyzers>`, `<AnalysisLevel>latest</AnalysisLevel>`, and `<AnalysisMode>Recommended</AnalysisMode>`.
- `Microsoft.CodeAnalysis.NetAnalyzers` is centrally versioned in `Directory.Packages.props` and injected for `.csproj` projects by `Directory.Build.props`.
- Suppress analyzer rules narrowly with `[SuppressMessage]` and an explicit `Justification`: `src/BS2BG.Core/Import/BodySlideXmlParser.cs` suppresses CA1822 for injectable services; `tests/BS2BG.Tests/TemplatesViewModelTests.cs` suppresses CA1861 for readable expected arrays.

## Import Organization

**Order:**
1. `System.*` namespaces first, sorted alphabetically: `System.Collections.ObjectModel`, `System.Reactive`, `System.Windows.Input` in `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`.
2. Third-party namespaces next: `Avalonia.*`, `Microsoft.Extensions.DependencyInjection`, `ReactiveUI.*` in `src/BS2BG.App/AppBootstrapper.cs` and `src/BS2BG.App/Services/WindowImageViewService.cs`.
3. Project namespaces last: `BS2BG.App.*`, `BS2BG.Core.*` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` and `tests/BS2BG.Tests/AppShellTests.cs`.
4. Alias directives come after normal usings when needed to resolve naming collisions: `using SetSlider = BS2BG.Core.Models.SetSlider;` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` and `using ModelSliderPreset = BS2BG.Core.Models.SliderPreset;` in `tests/BS2BG.Tests/TemplatesViewModelTests.cs`.

**Path Aliases:**
- No C# path aliases are configured. Use project/namespace references rooted at `BS2BG.Core`, `BS2BG.App`, and `BS2BG.Tests`.
- AXAML uses XML namespace aliases for view models, models, and services: `xmlns:vm="using:BS2BG.App.ViewModels"`, `xmlns:models="using:BS2BG.Core.Models"`, and `xmlns:services="using:BS2BG.App.Services"` in `src/BS2BG.App/Views/MainWindow.axaml`.

## Error Handling

**Patterns:**
- Validate public method arguments at entry and throw `ArgumentNullException` or `ArgumentException`: `src/BS2BG.Core/Generation/TemplateGenerationService.cs`, `src/BS2BG.Core/Models/SliderPreset.cs`, `src/BS2BG.Core/IO/AtomicFileWriter.cs`.
- Parser services return result objects with diagnostics for recoverable input issues instead of throwing: `BodySlideXmlImportResult` from `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `NpcImportResult` from `src/BS2BG.Core/Import/NpcTextParser.cs`.
- UI ViewModels catch operational failures and update `StatusMessage` rather than surfacing exceptions to the dispatcher: `TryOpenProjectPathAsync`, `HandleDroppedFilesAsync`, `ExportBodyGenInisAsync`, and `ExportBosJsonAsync` in `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`.
- Reactive commands subscribe to `ThrownExceptions` and route failures through status messages: `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`.
- File-system rollback code preserves original exceptions and uses `AggregateException` only when rollback also fails: `src/BS2BG.Core/IO/AtomicFileWriter.cs`.
- Silent fallbacks are used only for non-critical UX state such as preferences and image previews: `src/BS2BG.App/Services/UserPreferencesService.cs`, `src/BS2BG.App/Services/WindowImageViewService.cs`.

## Logging

**Framework:** console/status messages; no `ILogger` logging framework is detected in `src/`.

**Patterns:**
- Core library code should not write to console or UI; return values, diagnostics, or exceptions instead: `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.Core/Generation/MorphGenerationService.cs`.
- App-level user-visible status belongs in ViewModel `StatusMessage` properties: `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`.
- Use specific success/failure text for UI workflows, e.g. export/save/open status in `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` and copy/import status in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`.

## Comments

**When to Comment:**
- Keep comments that document parity-sensitive or non-obvious behavior; never remove accurate comments as cleanup.
- Add comments for hidden constraints, rollback/cancellation behavior, ownership/disposal, and byte-for-byte Java parity rules when adding logic near `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`, `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, or `src/BS2BG.Core/Export/BosJsonExportWriter.cs`.
- Prefer short analyzer suppression justifications over broad comments: `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `tests/BS2BG.Tests/BodySlideXmlParserTests.cs`.

**JSDoc/TSDoc:**
- Not applicable. This is a C# solution.
- Add XML doc comments (`///`) on new or substantially rewritten methods, especially public APIs and non-trivial helpers, following project owner guidance in `AGENTS.md`.

## Function Design

**Size:**
- Keep Core services small and focused: `src/BS2BG.Core/Generation/TemplateGenerationService.cs` is a thin formatter adapter; `src/BS2BG.Core/Import/BodySlideXmlParser.cs` is a parser plus private helpers.
- ViewModels are larger orchestration units. Add feature-specific helpers inside the relevant ViewModel rather than introducing UI logic into Core: `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- Prefer single-purpose private helpers for normalization, validation, formatting, and collection updates: `ValidateAndNormalize` in `src/BS2BG.Core/Models/SliderPreset.cs`, `EnsureProjectExtension` in `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`.

**Parameters:**
- Inject dependencies through constructors and guard them immediately: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/AppBootstrapper.cs`.
- Accept `IEnumerable<T>`/`IReadOnlyList<T>` at service boundaries where mutation is not required: `GenerateTemplates` in `src/BS2BG.Core/Generation/TemplateGenerationService.cs`, `WriteAtomicBatch` in `src/BS2BG.Core/IO/AtomicFileWriter.cs`.
- Pass `CancellationToken` through async file/dialog operations: `src/BS2BG.App/Services/WindowFileDialogService.cs`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`.

**Return Values:**
- Return immutable/result DTOs for parser and generation outcomes: `src/BS2BG.Core/Import/BodySlideXmlImportResult.cs`, `src/BS2BG.Core/Generation/MorphGenerationResult.cs`.
- Return `bool` for command-like operations that can be rejected by validation: `TryRenameSelectedPreset` and `RemoveSelectedPreset` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `AddCustomTarget` in `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- Return generated text from Core services; ViewModels store generated text in reactive properties: `src/BS2BG.Core/Generation/TemplateGenerationService.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`.

## Module Design

**Exports:**
- Use public sealed classes for most services/models unless inheritance is required: `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.App/Services/WindowImageViewService.cs`.
- Keep Core (`src/BS2BG.Core/`) free of Avalonia and UI dependencies; UI services and ViewModels belong in `src/BS2BG.App/`.
- Register concrete services and their interfaces in `src/BS2BG.App/AppBootstrapper.cs`; prefer singleton application services and transient windows following existing registrations.
- Reactive ViewModels inherit `ReactiveObject`, use `[Reactive]` and `[ObservableAsProperty]`, and expose `ReactiveCommand` instances: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- AXAML must use compiled binding patterns with `x:DataType` on roots and `DataTemplate`s: `src/BS2BG.App/Views/MainWindow.axaml`.

**Barrel Files:**
- No barrel files or aggregate namespace export files are used. Reference concrete namespaces directly.
- Global usings are limited to test assertion setup: `tests/BS2BG.Tests/FluentAssertionsSetup.cs` defines `global using FluentAssertions;`.

---

*Convention analysis: 2026-04-26*
