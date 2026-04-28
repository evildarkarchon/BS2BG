# Coding Conventions

**Analysis Date:** 2026-04-28

## Naming Patterns

**Files:**
- Use one public type per C# file with PascalCase matching the type name: `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/Services/WindowFileDialogService.cs`.
- Use interface files prefixed with `I` and named for the service abstraction: `src/BS2BG.App/Services/IFileDialogService.cs`, `src/BS2BG.App/Services/IClipboardService.cs`, `src/BS2BG.Core/Morphs/IRandomAssignmentProvider.cs`.
- Use `*ViewModel.cs` for reactive presentation models: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs`.
- Use `*Service.cs`, `*Parser.cs`, `*Writer.cs`, and `*Formatter.cs` suffixes to communicate responsibility: `src/BS2BG.Core/Generation/TemplateGenerationService.cs`, `src/BS2BG.Core/Import/NpcTextParser.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`.
- Use `.axaml` plus `.axaml.cs` pairs for Avalonia views: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs`, `src/BS2BG.App/App.axaml`, `src/BS2BG.App/App.axaml.cs`.
- Do not add new C# implementation under legacy Java directories such as `src/com/asdasfa/jbs2bg/` or `src/jfx-8u60-b08/`; active C# code belongs under `src/BS2BG.Core/`, `src/BS2BG.App/`, or `src/BS2BG.Cli/`.

**Functions:**
- Use PascalCase for methods and commands: `ParseFile`, `ParseFiles`, `ParseString` in `src/BS2BG.Core/Import/BodySlideXmlParser.cs`; `ImportPresetsAsync`, `GenerateTemplates`, `LinkExternalBusy` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`.
- Use `Try*` methods for validation or partial success paths with `out` parameters and no exceptions for expected invalid input: `TryAddCustomTarget` and `TryValidateCustomTargetName` in `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`.
- Use `*Async` suffix for `Task`-returning methods: `ImportPresetsAsync` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `PreviewNpcImportAsync` in `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, and test methods such as `SavingStrategyConfigurationUpdatesProjectAndMarksDirty` in `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`.
- Use helper methods with narrow names inside tests: `CreateViewModel`, `CreateProjectWithPresetsAndNpcs`, and `FindRepositoryRoot` in `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`; `LoadProfile`, `LoadPresets`, and `AssertFixtureText` in `tests/BS2BG.Tests/SliderMathFormatterTests.cs`.

**Variables:**
- Use camelCase for locals and private readonly fields: `presets`, `diagnostics`, and `sliderElement` in `src/BS2BG.Core/Import/BodySlideXmlParser.cs`; `assignmentService`, `project`, and `disposables` in `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- Use ReactiveUI source-generator backing fields with a leading underscore and `[Reactive]` or `[ObservableAsProperty]`: `_generatedTemplateText`, `_isBusy`, `_selectedPreset` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`; `_generatedMorphsText`, `_strategyValidationMessage`, `_selectedAssignmentStrategyKind` in `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- Use descriptive domain names instead of abbreviations for user-facing state: `ProfileNames`, `GeneratedTemplateText`, `StrategySummaryText`, `ValidationMessage`, `StatusMessage` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` and `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- Use `cancellationToken` as the parameter name for cancellable async flows, including `ImportPresetsAsync` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` and test doubles in `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`.

**Types:**
- Use PascalCase for classes, records, enums, and structs: `ProjectModel` in `src/BS2BG.Core/Models/ProjectModel.cs`, `NpcKey` nested in `src/BS2BG.Core/Import/NpcTextParser.cs`, `PresetCountWarningState` in `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- Use immutable record types for small value outputs where appropriate: `ExpectedOutputPaths` in `tests/BS2BG.Tests/TestProfiles.cs`.
- Use nested private helper classes in tests for fakes and fixtures: `SequenceRandomAssignmentProvider`, `EmptyClipboardService`, and `EmptyNpcTextFilePicker` in `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`.
- Use domain-specific result/diagnostic types for recoverable operations: `BodySlideXmlImportResult` and `BodySlideXmlImportDiagnostic` used by `src/BS2BG.Core/Import/BodySlideXmlParser.cs`; `NpcImportResult` and `NpcImportDiagnostic` used by `src/BS2BG.Core/Import/NpcTextParser.cs`.

## Code Style

**Formatting:**
- Follow `.editorconfig`: UTF-8, final newline, trim trailing whitespace, spaces for indentation, 4-space indentation for `*.cs`, and 2-space indentation for `*.axaml`, `*.csproj`, `*.props`, `*.json`, and YAML files.
- C# braces go on new lines for namespaces, types, methods, and blocks per `.editorconfig`; examples appear throughout `src/BS2BG.Core/Import/BodySlideXmlParser.cs` and `src/BS2BG.App/AppBootstrapper.cs`.
- Use file-scoped namespaces: `namespace BS2BG.Core.Import;` in `src/BS2BG.Core/Import/NpcTextParser.cs`, `namespace BS2BG.Tests;` in `tests/BS2BG.Tests/SliderMathFormatterTests.cs`.
- Prefer collection expressions for new arrays/lists when target language permits: `[]` in `src/BS2BG.Core/Import/NpcTextParser.cs`, `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs`, and `tests/BS2BG.Tests/TestProfiles.cs`.
- Use target-typed `new()` where the target type is explicit: `CompositeDisposable disposables = new();` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` and `Dictionary<Npc, int> npcPropertySubscriptions = new();` in `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- Keep PowerShell scripts CRLF per `.editorconfig`; examples and tooling live under `tests/tools/generate-expected.ps1` and `tools/release/`.

**Linting:**
- Central analyzer configuration is in `Directory.Build.props`: nullable reference types are enabled, implicit usings are enabled, .NET analyzers are enabled, `AnalysisLevel` is `latest`, and `AnalysisMode` is `Recommended`.
- Nullable warnings CS8600-CS8603 are elevated to warning in `.editorconfig`; new code should use explicit null checks and nullable annotations instead of suppressing flow analysis.
- Use `ArgumentNullException.ThrowIfNull(...)` for direct null guard calls where no custom control flow is needed, as in `LinkExternalBusy` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`.
- Use classic `if (value is null) throw new ArgumentNullException(nameof(value));` when the surrounding method already uses explicit branching or must support netstandard patterns, as in `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.Core/Import/NpcTextParser.cs`, and `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`.
- Use targeted `SuppressMessage` attributes with justification when analyzer guidance conflicts with injectable service surfaces: `BodySlideXmlParser` in `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `NpcTextParser` in `src/BS2BG.Core/Import/NpcTextParser.cs`, and `MorphAssignmentService` in `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`.

## Import Organization

**Order:**
1. System namespaces first, sorted: `System.Globalization`, `System.Text.Json`, `System.Xml.Linq` in `tests/BS2BG.Tests/SliderMathFormatterTests.cs`; `System.CommandLine`, `System.Text.Json` in `src/BS2BG.Cli/Program.cs`.
2. Framework and third-party namespaces next: `Avalonia.*`, `Microsoft.Extensions.DependencyInjection`, `ReactiveUI`, `DynamicData` in `src/BS2BG.App/ViewModels/MorphsViewModel.cs` and `tests/BS2BG.Tests/MainWindowHeadlessTests.cs`.
3. Project namespaces next: `BS2BG.App.*`, `BS2BG.Core.*`, and then aliases where needed, as in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` and `tests/BS2BG.Tests/ExportWriterTests.cs`.

**Path Aliases:**
- C# uses project references and namespaces rather than source-level path aliases; `tests/BS2BG.Tests/BS2BG.Tests.csproj` references `src/BS2BG.App/BS2BG.App.csproj`, `src/BS2BG.Cli/BS2BG.Cli.csproj`, and `src/BS2BG.Core/BS2BG.Core.csproj`.
- Use namespace aliases to disambiguate duplicate model/formatting names: `using SetSlider = BS2BG.Core.Models.SetSlider;` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`; `using ModelSetSlider = BS2BG.Core.Models.SetSlider;` in `tests/BS2BG.Tests/ExportWriterTests.cs` and `tests/BS2BG.Tests/TestProfiles.cs`.
- Tests rely on a global FluentAssertions import in `tests/BS2BG.Tests/FluentAssertionsSetup.cs`; new tests normally do not need per-file `using FluentAssertions;`.

## Error Handling

**Patterns:**
- Throw `ArgumentNullException` for programmer errors and invalid required dependencies: constructors in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, and `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`.
- Return result objects with diagnostics for recoverable import failures instead of throwing into UI flows: `BodySlideXmlParser.ParseFile` catches `IOException`, `UnauthorizedAccessException`, and `XmlException` and returns `BodySlideXmlImportDiagnostic` in `src/BS2BG.Core/Import/BodySlideXmlParser.cs`; `NpcTextParser.ParseFile` catches file I/O exceptions and returns `NpcImportDiagnostic` in `src/BS2BG.Core/Import/NpcTextParser.cs`.
- Catch only expected exception families with exception filters, as in `src/BS2BG.Cli/Program.cs` where project load errors are converted to `AutomationExitCode.UsageError` and bundle I/O failures are converted to `AutomationExitCode.IoFailure`.
- Use boolean results for idempotent user operations: `AddPresetToTarget`, `RemovePresetFromTarget`, `RemoveNpc`, and `TryValidateCustomTargetName` in `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`.
- In ReactiveUI ViewModels, subscribe command `ThrownExceptions` and route failures to visible status/validation text: `ImportPresetsCommand.ThrownExceptions` and copy-command handlers in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`.
- For atomic file operations, tests should assert both exception type and rollback state; examples are `AtomicFileWriterWriteAtomicPairLeavesTargetsUntouchedOnPhase1Failure` and related tests in `tests/BS2BG.Tests/ExportWriterTests.cs`.

## Logging

**Framework:** console / user-visible status text; no structured logging package detected.

**Patterns:**
- CLI output is emitted through explicit writer methods in `src/BS2BG.Cli/Program.cs`; preserve stable automation-friendly messages and exit codes.
- UI feedback uses ViewModel properties such as `StatusMessage`, `ValidationMessage`, `StrategyValidationMessage`, and `InvalidLoadedStrategyMessage` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` and `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- Import/export diagnostics should flow through diagnostic result types and report formatters, such as `src/BS2BG.App/Services/DiagnosticsReportFormatter.cs` and `src/BS2BG.Core/Diagnostics/DiagnosticReportTextFormatter.cs`, rather than ad-hoc console output.

## Comments

**When to Comment:**
- Keep comments that explain non-obvious why, parity constraints, lifecycle ownership, scheduler behavior, or stale-state hazards. Examples include the singleton catalog comment in `src/BS2BG.App/AppBootstrapper.cs` and the deterministic strategy comment in `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`.
- Do not delete accurate comments as cleanup. Comments are project policy and should only be removed when the described code is removed or substantially changed.
- Add a short comment for hidden constraints in new code, especially Java parity behavior, atomic rollback behavior, cancellation/async ordering, ReactiveUI scheduler choices, and empty `catch`/`finally` blocks.
- Use XML documentation on methods added or substantially rewritten unless they are trivial private helpers. Existing examples include `Program.Main`, `Program.CreateRootCommand`, and `Program.MapBundleOutcome` in `src/BS2BG.Cli/Program.cs`, plus `ApplyStrategy` in `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`.

**JSDoc/TSDoc:**
- Not applicable; this is a C#/.NET codebase. Use XML doc comments (`///`) for C# public/internal APIs and explanatory XML comments in AXAML only when needed.

## Function Design

**Size:**
- Keep Core methods small and single-purpose: parsing helpers in `src/BS2BG.Core/Import/BodySlideXmlParser.cs` and `src/BS2BG.Core/Import/NpcTextParser.cs` are focused and testable.
- ViewModels can contain larger orchestration methods, but new behavior should be decomposed behind services or private helpers rather than adding monolithic UI logic. `src/BS2BG.App/ViewModels/MorphsViewModel.cs` is already large; add reusable domain logic to `src/BS2BG.Core/` services where possible.
- CLI command setup is centralized in `src/BS2BG.Cli/Program.cs`; add new command actions with small parsing helpers and service-composition helpers rather than inlining all behavior into `CreateRootCommand`.

**Parameters:**
- Pass dependencies through constructors and validate them immediately: `TemplatesViewModel` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `MorphsViewModel` in `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, and `MorphAssignmentService` in `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`.
- Use optional parameters only for test seams or optional collaborators with safe defaults: `UndoRedoService?`, `IUserPreferencesService?`, and `INavigationService?` in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`; `IScheduler?`, `IAppDialogService?`, and `AssignmentStrategyService?` in `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- Use `IEnumerable<T>` for input sequences that are iterated and `IReadOnlyList<T>` for indexed/count-aware inputs: `AddNpcsToMorphs`, `FillEmptyNpcs`, and `ApplyStrategy` in `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`.

**Return Values:**
- Return immutable or result-like objects for operations that carry diagnostics or paths: `BodySlideXmlImportResult`, `NpcImportResult`, export result objects in `src/BS2BG.Core/Export/`, and `ExpectedOutputPaths` in `tests/BS2BG.Tests/TestProfiles.cs`.
- Return `bool` or counts for idempotent collection mutations so callers can update status and undo state: `ClearAssignments`, `FillEmptyNpcs`, `AddAllPresetsToTarget`, and `RemoveNpc` in `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`.
- Return command observables/tasks through `ReactiveCommand<Unit, Unit>` properties in ViewModels rather than exposing `ICommand` fields directly, as in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` and `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.

## Module Design

**Exports:**
- Keep `src/BS2BG.Core/` pure domain and I/O with no Avalonia dependencies. Parser, generation, formatting, export, serialization, and morph logic belongs in `src/BS2BG.Core/`.
- Keep Avalonia, ReactiveUI, windows, dialogs, clipboard, preferences, and ViewModels in `src/BS2BG.App/`.
- Keep automation-only command-line composition in `src/BS2BG.Cli/Program.cs`, referencing Core services without Avalonia/App UI dependencies.
- Use dependency injection registration in `src/BS2BG.App/AppBootstrapper.cs`; add new application services there and expose abstractions from `src/BS2BG.App/Services/`.
- Preserve ReactiveUI conventions from `AGENTS.md`: ViewModels inherit `ReactiveObject`, notifying properties use `[Reactive]`, commands use `ReactiveCommand.Create*`, derived state uses `WhenAnyValue`/`ToProperty`, and ViewModels do not call `Dispatcher.UIThread.InvokeAsync` directly.
- Preserve Avalonia compiled binding conventions: root AXAML and every `DataTemplate` must declare `x:DataType`, as in `src/BS2BG.App/Views/MainWindow.axaml`.

**Barrel Files:**
- Not used. There are no C# barrel files; reference concrete namespaces directly from source files and project references.
- Shared test helpers are ordinary internal classes/files such as `tests/BS2BG.Tests/TestProfiles.cs`, `tests/BS2BG.Tests/FluentAssertionsSetup.cs`, and `tests/BS2BG.Tests/TestModuleInitializer.cs`.

---

*Convention analysis: 2026-04-28*
