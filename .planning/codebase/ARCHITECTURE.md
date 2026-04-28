<!-- refreshed: 2026-04-28 -->
# Architecture

**Analysis Date:** 2026-04-28

## System Overview

```text
┌─────────────────────────────────────────────────────────────┐
│                    Presentation / Entry Points               │
├──────────────────────┬──────────────────┬───────────────────┤
│   Avalonia Desktop   │  Automation CLI  │   Tests / Specs    │
│ `src/BS2BG.App/`     │ `src/BS2BG.Cli/` │ `tests/BS2BG.Tests`│
└──────────┬───────────┴────────┬─────────┴─────────┬─────────┘
           │                    │                   │
           ▼                    ▼                   ▼
┌─────────────────────────────────────────────────────────────┐
│                 Application Orchestration Layer              │
│ `src/BS2BG.App/ViewModels/` + `src/BS2BG.App/Services/`      │
│ `src/BS2BG.Core/Automation/` + `src/BS2BG.Core/Bundling/`    │
└───────────────────────────────┬─────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                         Core Domain                          │
│ `src/BS2BG.Core/Models/`, `Generation/`, `Morphs/`,          │
│ `Import/`, `Export/`, `Diagnostics/`, `Serialization/`       │
└───────────────────────────────┬─────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                  Files, Bundled Data, External Artifacts      │
│ `settings*.json`, `.jbs2bg`, BodySlide XML, NPC text,        │
│ `templates.ini`, `morphs.ini`, BoS JSON, portable bundle zip │
└─────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| Desktop app host | Creates the Avalonia app, detects platform support, wires ReactiveUI and DI, and starts the classic desktop lifetime. | `src/BS2BG.App/Program.cs` |
| App bootstrapper | Registers the single-window object graph and keeps Core services injectable behind App interfaces. | `src/BS2BG.App/AppBootstrapper.cs` |
| Main window view | Defines the shell, tabs, menus, command palette, bindings, and view-only event forwarding. | `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs` |
| Root ViewModel | Coordinates project file commands, global busy state, export previews, bundle creation, workspace navigation, and child ViewModels. | `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` |
| Templates workspace | Imports BodySlide XML presets, edits slider/profile state, previews templates and BoS JSON, and exposes preset commands. | `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` |
| Morphs workspace | Imports NPC text, manages custom targets and NPC preset assignment, applies assignment strategies, and generates `morphs.ini` previews. | `src/BS2BG.App/ViewModels/MorphsViewModel.cs` |
| Diagnostics workspace | Presents validation and recovery findings from Core diagnostics services. | `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs` |
| Profile management workspace | Manages bundled, local, and project custom profile definitions. | `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs`, `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs` |
| Shared project state | Owns mutable presets, custom morph targets, NPCs, custom profiles, assignment strategy, dirty state, and change version. | `src/BS2BG.Core/Models/ProjectModel.cs` |
| XML import | Parses BodySlide XML files/strings into domain presets with diagnostics. | `src/BS2BG.Core/Import/BodySlideXmlParser.cs` |
| NPC import | Parses pipe-delimited NPC text into `Npc` rows and diagnostics. | `src/BS2BG.Core/Import/NpcTextParser.cs` |
| Template generation | Converts model presets to formatter presets and produces Java-parity `templates.ini` / BoS JSON text. | `src/BS2BG.Core/Generation/TemplateGenerationService.cs` |
| Morph generation | Produces morph assignment lines from custom targets and NPC rows. | `src/BS2BG.Core/Generation/MorphGenerationService.cs` |
| Slider formatting | Performs Java-compatible slider math, missing-default injection, profile lookup application, and float formatting. | `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`, `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs` |
| Export writers | Write BodyGen INIs and BoS JSON through atomic file operations and fixed line-ending rules. | `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs` |
| Project serialization | Loads/saves `.jbs2bg` JSON and embeds project-scoped custom profile/assignment strategy state. | `src/BS2BG.Core/Serialization/ProjectFileService.cs` |
| Validation | Produces non-mutating findings for UI, CLI, and bundle reports. | `src/BS2BG.Core/Diagnostics/ProjectValidationService.cs` |
| Headless automation | Runs project load, strategy replay, validation, generation, overwrite preflight, and export for CLI callers. | `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` |
| Portable bundling | Builds path-scrubbed bundle previews and zip contents from saved project, validation reports, generated outputs, and manifests. | `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` |
| CLI host | Parses `generate` and `bundle` commands and composes Core-only services without depending on Avalonia. | `src/BS2BG.Cli/Program.cs` |
| Test suite | Verifies Core, App ViewModels, CLI automation, release packaging, and Avalonia headless behavior. | `tests/BS2BG.Tests/` |

## Pattern Overview

**Overall:** Layered desktop architecture with a pure Core domain, MVVM presentation, DI composition, and Core-owned headless automation.

**Key Characteristics:**
- Keep `src/BS2BG.Core/` independent from Avalonia, ReactiveUI, and platform UI services; App and CLI both consume Core through project references.
- Keep mutable workflow state in a shared `ProjectModel` singleton registered in `src/BS2BG.App/AppBootstrapper.cs`; ViewModels mutate the project through Core services and command handlers.
- Use ReactiveUI SourceGenerators in ViewModels (`[Reactive]`, `[ObservableAsProperty]`, `ReactiveCommand`) and compiled AXAML bindings (`x:DataType`) in `src/BS2BG.App/Views/MainWindow.axaml`.
- Use injectable service seams for file dialogs, clipboard, image viewing, preferences, profile stores, random assignment, serialization, generation, export, and bundle creation.
- Preserve Java reference output parity in `src/BS2BG.Core/Formatting/`, `src/BS2BG.Core/Generation/`, and `src/BS2BG.Core/Export/`; golden-file tests in `tests/BS2BG.Tests/` are the safety net.

## Layers

**Desktop Presentation:**
- Purpose: Render the single-window Avalonia UI and convert view-only events into ViewModel calls.
- Location: `src/BS2BG.App/Views/`, `src/BS2BG.App/Themes/`, `src/BS2BG.App/App.axaml`, `src/BS2BG.App/Program.cs`
- Contains: AXAML, window code-behind, theme resources, desktop lifetime setup.
- Depends on: `src/BS2BG.App/ViewModels/`, Avalonia packages, ReactiveUI Avalonia setup.
- Used by: End users running `src/BS2BG.App/BS2BG.App.csproj`.

**App MVVM Orchestration:**
- Purpose: Own UI commands, busy/can-execute state, child workspace coordination, undo/redo, preferences, file dialog choices, and Core service calls.
- Location: `src/BS2BG.App/ViewModels/`, `src/BS2BG.App/ViewModels/Workflow/`
- Contains: `MainWindowViewModel`, workspace ViewModels, row/preview ViewModels, filter state, undo snapshots.
- Depends on: `src/BS2BG.App/Services/`, `src/BS2BG.Core/Models/`, `src/BS2BG.Core/Generation/`, `src/BS2BG.Core/Morphs/`, `src/BS2BG.Core/Diagnostics/`, `ReactiveUI`.
- Used by: `src/BS2BG.App/Views/MainWindow.axaml` through compiled bindings and code-behind event glue.

**App Platform Services:**
- Purpose: Isolate Avalonia/platform dependencies behind interfaces so ViewModels remain testable.
- Location: `src/BS2BG.App/Services/`
- Contains: file pickers, dialogs, clipboard, image lookup/viewing, navigation, profile catalog service, user preferences, display converters.
- Depends on: Avalonia where platform-bound (`WindowFileDialogService`, `WindowClipboardService`), Core profile/serialization models where domain-bound.
- Used by: `src/BS2BG.App/AppBootstrapper.cs`, `src/BS2BG.App/ViewModels/*.cs`, tests using null/empty service implementations.

**Core Domain Models:**
- Purpose: Represent BS2BG project state and emit dirty/change notifications without UI dependencies.
- Location: `src/BS2BG.Core/Models/`
- Contains: `ProjectModel`, `SliderPreset`, `SetSlider`, `Npc`, `CustomMorphTarget`, `MorphTargetBase`, custom profile definitions.
- Depends on: `System.Collections.ObjectModel`, Core morph contracts.
- Used by: all Core services, App ViewModels, CLI automation, and tests.

**Core Import / Serialization / Persistence:**
- Purpose: Convert external BodySlide XML, NPC text, and project JSON into domain objects and back.
- Location: `src/BS2BG.Core/Import/`, `src/BS2BG.Core/Serialization/`, `src/BS2BG.Core/IO/`
- Contains: `BodySlideXmlParser`, `NpcTextParser`, `ProjectFileService`, `AtomicFileWriter`, ledger/result contracts.
- Depends on: `System.Xml.Linq`, `System.Text.Json`, filesystem APIs.
- Used by: Templates/Morphs import commands, project save/open commands, CLI generation, portable bundle creation.

**Core Generation / Formatting / Export:**
- Purpose: Produce Java-parity output text and write it safely to disk.
- Location: `src/BS2BG.Core/Generation/`, `src/BS2BG.Core/Formatting/`, `src/BS2BG.Core/Export/`
- Contains: `TemplateGenerationService`, `MorphGenerationService`, profile catalog/factory services, `SliderMathFormatter`, `JavaFloatFormatting`, INI/JSON export writers.
- Depends on: Core models and profile data loaded from `settings.json`, `settings_UUNP.json`, `settings_FO4_CBBE.json`.
- Used by: App previews/exports, CLI generation, portable bundles, golden-file tests.

**Core Morph Assignment:**
- Purpose: Manage preset assignment to custom targets and NPCs, including deterministic strategy replay for automation.
- Location: `src/BS2BG.Core/Morphs/`
- Contains: `MorphAssignmentService`, `AssignmentStrategyService`, replay contracts/service, random provider abstractions.
- Depends on: Core models.
- Used by: `MorphsViewModel`, `HeadlessGenerationService`, `PortableProjectBundleService`, assignment strategy tests.

**Core Diagnostics / Automation / Bundling:**
- Purpose: Provide non-UI validation, preview, CLI generation, replay reports, portable bundle manifests, and privacy-scrubbed reports.
- Location: `src/BS2BG.Core/Diagnostics/`, `src/BS2BG.Core/Automation/`, `src/BS2BG.Core/Bundling/`
- Contains: `ProjectValidationService`, `ExportPreviewService`, `HeadlessGenerationService`, `PortableProjectBundleService`, bundle contracts/scrubber.
- Depends on: Core generation/export/serialization/morph services.
- Used by: App diagnostics/export previews/bundles and `src/BS2BG.Cli/Program.cs`.

**CLI Entry Layer:**
- Purpose: Expose automation surfaces for scripts without Avalonia.
- Location: `src/BS2BG.Cli/`
- Contains: `Program.cs`, `BS2BG.Cli.csproj`.
- Depends on: `System.CommandLine` and `src/BS2BG.Core/BS2BG.Core.csproj` only.
- Used by: release automation, CLI tests, headless workflows.

**Reference and Planning Assets:**
- Purpose: Preserve Java behavior and process context without participating in the C# build.
- Location: `src/com/asdasfa/jbs2bg/`, `src/jfx-8u60-b08/`, `openspec/`, `.planning/`
- Contains: Java jBS2BG reference, OpenJFX source snapshot, OpenSpec specs/changes, GSD planning docs.
- Depends on: Not part of `BS2BG.sln` except as human/reference context.
- Used by: implementation planning, parity checks, and porting cross-checks.

## Data Flow

### Primary Desktop Import → Template Preview Path

1. User chooses BodySlide XML through `Templates.ImportPresetsCommand` (`src/BS2BG.App/ViewModels/TemplatesViewModel.cs:153`).
2. `TemplatesViewModel` uses `IBodySlideXmlFilePicker` and `BodySlideXmlParser.ParseFiles` to parse XML into `SliderPreset` objects (`src/BS2BG.Core/Import/BodySlideXmlParser.cs:29`).
3. Parsed presets are added to `ProjectModel.SliderPresets`, which marks dirty and updates change version (`src/BS2BG.Core/Models/ProjectModel.cs:17`, `src/BS2BG.Core/Models/ProjectModel.cs:163`).
4. `TemplatesViewModel` refreshes visible preset rows, inspector rows, profile selection, and template/BoS previews from the current `TemplateProfileCatalog` (`src/BS2BG.App/ViewModels/TemplatesViewModel.cs:198`).
5. `TemplateGenerationService.PreviewTemplate` maps model presets into formatter presets and calls `SliderMathFormatter.FormatTemplateLine` (`src/BS2BG.Core/Generation/TemplateGenerationService.cs:14`).

### Desktop Export BodyGen INIs Path

1. User invokes `ExportBodyGenInisCommand` from menu/key binding in `MainWindow.axaml` (`src/BS2BG.App/Views/MainWindow.axaml:82`).
2. `MainWindowViewModel` gates the command on project content and global busy state (`src/BS2BG.App/ViewModels/MainWindowViewModel.cs:236`).
3. Template text is generated by `TemplateGenerationService.GenerateTemplates` with profiles from the current catalog (`src/BS2BG.Core/Generation/TemplateGenerationService.cs:26`).
4. Morph text is generated by `MorphGenerationService.GenerateMorphs(ProjectModel)` (`src/BS2BG.Core/Generation/MorphGenerationService.cs:12`).
5. `BodyGenIniExportWriter.Write` creates the output directory, normalizes CRLF, and writes `templates.ini` plus `morphs.ini` atomically (`src/BS2BG.Core/Export/BodyGenIniExportWriter.cs:13`).
6. `AtomicFileWriter.WriteAtomicPair` delegates to the batch writer with rollback ledger support (`src/BS2BG.Core/IO/AtomicFileWriter.cs:32`).

### Desktop NPC Import → Morph Assignment Path

1. User previews/imports NPC text through `MorphsViewModel` commands and the `INpcTextFilePicker` service (`src/BS2BG.App/ViewModels/MorphsViewModel.cs:55`, `src/BS2BG.App/Services/WindowNpcTextFilePicker.cs`).
2. `NpcImportPreviewService` and `NpcTextParser` convert pipe-delimited rows into `Npc` objects plus diagnostics (`src/BS2BG.Core/Import/NpcImportPreviewService.cs`, `src/BS2BG.Core/Import/NpcTextParser.cs`).
3. `MorphsViewModel` adds selected NPCs to `ProjectModel.MorphedNpcs` through `MorphAssignmentService.AddNpcToMorphs` (`src/BS2BG.Core/Morphs/MorphAssignmentService.cs:89`).
4. Preset assignment commands call `MorphAssignmentService` or `AssignmentStrategyService`, mutating `MorphTargetBase.SliderPresets` collections (`src/BS2BG.Core/Morphs/MorphAssignmentService.cs:52`).
5. `MorphGenerationService.GenerateMorphs` reads `CustomMorphTargets` and `MorphedNpcs` and returns output text plus targets without presets (`src/BS2BG.Core/Generation/MorphGenerationService.cs:19`).

### Project Save/Open Path

1. `MainWindowViewModel` exposes new/open/save/save-as commands (`src/BS2BG.App/ViewModels/MainWindowViewModel.cs:242`).
2. File locations come from `IFileDialogService` and persisted folders in `UserPreferencesService` (`src/BS2BG.App/Services/WindowFileDialogService.cs`, `src/BS2BG.App/Services/UserPreferencesService.cs:15`).
3. Save calls `ProjectFileService.SaveToString` / `Save` to serialize presets, custom targets, NPCs, embedded profiles, and assignment strategy (`src/BS2BG.Core/Serialization/ProjectFileService.cs:106`).
4. Load calls `ProjectFileService.LoadWithDiagnosticsFromString` then `ProjectModel.ReplaceWith` so the singleton project instance stays stable for existing ViewModel bindings (`src/BS2BG.Core/Serialization/ProjectFileService.cs:75`, `src/BS2BG.Core/Models/ProjectModel.cs:65`).
5. Recoverable load diagnostics flow to profile recovery/diagnostics UI (`src/BS2BG.Core/Serialization/ProjectFileService.cs:18`).

### CLI Generate Path

1. CLI starts at `Program.Main` and parses `generate --project --output --intent` (`src/BS2BG.Cli/Program.cs:19`, `src/BS2BG.Cli/Program.cs:55`).
2. `Program.CreateGenerationService` composes Core services only: project file, template/morph generation, INI/JSON writers, planner, replay, and bundled profile catalog (`src/BS2BG.Cli/Program.cs:180`).
3. `HeadlessGenerationService.Run` loads the project file and builds a request-scoped profile catalog (`src/BS2BG.Core/Automation/HeadlessGenerationService.cs:39`).
4. Assignment strategy replay occurs before BodyGen output when requested (`src/BS2BG.Core/Automation/HeadlessGenerationService.cs:64`).
5. `ProjectValidationService.Validate` blocks invalid projects before writing (`src/BS2BG.Core/Automation/HeadlessGenerationService.cs:74`).
6. Writers emit `templates.ini`, `morphs.ini`, and/or BoS JSON with overwrite preflight and atomic write ledger reporting (`src/BS2BG.Core/Automation/HeadlessGenerationService.cs:91`).

### Portable Bundle Path

1. Desktop and CLI callers create `PortableProjectBundleRequest` with project, destination path, output intent, overwrite flag, save context, and privacy roots (`src/BS2BG.Core/Bundling/PortableProjectBundleContracts.cs`).
2. `PortableProjectBundleService.Preview` calls `BuildPlan` without creating a zip (`src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:95`).
3. `BuildPlan` composes request-scoped profiles, replays assignment strategy, validates, embeds project JSON, reports, profiles, generated output, manifest, and checksums (`src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:203`).
4. `Create` writes a temp zip then commits it to the final bundle path only after all entries are created (`src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:116`).

**State Management:**
- `ProjectModel` is the canonical mutable state container (`src/BS2BG.Core/Models/ProjectModel.cs`).
- App registers `ProjectModel` as a singleton and shares it across `MainWindowViewModel`, `TemplatesViewModel`, `MorphsViewModel`, diagnostics, and profiles (`src/BS2BG.App/AppBootstrapper.cs:32`).
- ViewModels expose derived state through ReactiveUI properties and commands; collection changes are bridged via `CollectionChangedObservable` (`src/BS2BG.App/ViewModels/CollectionChangedObservable.cs`).
- Undo/redo snapshots live in App, not Core, through `UndoRedoService` and `ViewModels/Workflow/UndoSnapshots.cs` (`src/BS2BG.App/Services/UndoRedoService.cs`, `src/BS2BG.App/ViewModels/Workflow/UndoSnapshots.cs`).

## Key Abstractions

**ProjectModel:**
- Purpose: Root aggregate for every project workflow and dirty-state tracking.
- Examples: `src/BS2BG.Core/Models/ProjectModel.cs`, `tests/BS2BG.Tests/ProjectFileServiceTests.cs`
- Pattern: Observable aggregate root with child subscription tracking and explicit `ReplaceWith` for stable singleton identity.

**ProjectModelNode:**
- Purpose: Base change-notification node for models whose mutations must mark the project dirty.
- Examples: `src/BS2BG.Core/Models/ProjectModelNode.cs`, `src/BS2BG.Core/Models/SliderPreset.cs`, `src/BS2BG.Core/Models/Npc.cs`
- Pattern: Parent aggregate listens to child `Changed` events rather than ViewModels manually marking dirty for every field.

**TemplateProfileCatalog / TemplateProfile:**
- Purpose: Resolve bundled, local, and project profile slider defaults/multipliers/inversions for generation.
- Examples: `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs`, `src/BS2BG.Core/Generation/TemplateProfileCatalogFactory.cs`, `src/BS2BG.App/Services/TemplateProfileCatalogService.cs`
- Pattern: Catalog service owns the current profile set; request-scoped composer adds embedded project profiles for automation and bundling.

**Reactive ViewModels:**
- Purpose: Keep UI state, commands, and derived properties testable without Avalonia controls.
- Examples: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`
- Pattern: `ReactiveObject` + SourceGenerator attributes + `ReactiveCommand.Create*` / `CreateFromTask` with observable `canExecute`.

**Platform Service Interfaces:**
- Purpose: Keep file dialogs, clipboard, image viewing, dialogs, preferences, and navigation replaceable in tests.
- Examples: `src/BS2BG.App/Services/IFileDialogService.cs`, `src/BS2BG.App/Services/IClipboardService.cs`, `src/BS2BG.App/Services/NavigationService.cs`
- Pattern: App-specific adapters are registered in DI, while tests pass null/empty implementations.

**Import Result and Diagnostic Records:**
- Purpose: Return partial successes with human-readable diagnostics instead of throwing for recoverable user input errors.
- Examples: `src/BS2BG.Core/Import/BodySlideXmlImportResult.cs`, `src/BS2BG.Core/Import/NpcImportResult.cs`, `src/BS2BG.Core/Serialization/ProjectFileService.cs`
- Pattern: Immutable result records containing parsed objects plus diagnostic collections.

**AtomicFileWriter:**
- Purpose: Make project save/export/batch writes safer and report partial outcomes.
- Examples: `src/BS2BG.Core/IO/AtomicFileWriter.cs`, `src/BS2BG.Core/IO/AtomicWriteException.cs`, `src/BS2BG.Core/IO/WriteOutcomeLedger.cs`
- Pattern: temp-file commit with rollback and ledger snapshots for UI/CLI reporting.

**Assignment Strategy Contracts:**
- Purpose: Persist and replay NPC assignment strategies deterministically for UI, CLI, and bundle workflows.
- Examples: `src/BS2BG.Core/Morphs/AssignmentStrategyContracts.cs`, `src/BS2BG.Core/Automation/AssignmentStrategyReplayService.cs`
- Pattern: contract records plus replay service that can clone project state before applying assignments.

## Entry Points

**Avalonia desktop app:**
- Location: `src/BS2BG.App/Program.cs`
- Triggers: `dotnet run --project src/BS2BG.App/BS2BG.App.csproj`, published desktop executable.
- Responsibilities: Build `AppBuilder`, register ReactiveUI/DI, and start desktop lifetime.

**Avalonia application lifetime:**
- Location: `src/BS2BG.App/App.axaml.cs`
- Triggers: Avalonia framework initialization.
- Responsibilities: Load AXAML and resolve `MainWindow` from `AppBootstrapper.Services`.

**Main window shell:**
- Location: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs`
- Triggers: DI transient construction and user events.
- Responsibilities: Bind menus/tabs/forms to `MainWindowViewModel`, attach window-bound services, forward view-only multi-selection, drag/drop, tab, and command-palette events.

**CLI:**
- Location: `src/BS2BG.Cli/Program.cs`
- Triggers: `bs2bg generate ...` and `bs2bg bundle ...` invocations.
- Responsibilities: Parse arguments, compose Core services, call automation/bundle services, map outcomes to stable exit codes.

**Core service tests:**
- Location: `tests/BS2BG.Tests/`
- Triggers: `dotnet test`.
- Responsibilities: Assert Java parity, ViewModel behavior, CLI behavior, diagnostics, serialization, bundling, and release packaging rules.

**Java reference context:**
- Location: `src/com/asdasfa/jbs2bg/`
- Triggers: Human/agent inspection and fixture regeneration tooling.
- Responsibilities: Authoritative behavior source for porting; not compiled by `BS2BG.sln`.

## Architectural Constraints

- **Threading:** Avalonia desktop uses a single UI thread (`[STAThread]` in `src/BS2BG.App/Program.cs:9`). ViewModels use ReactiveUI observables/commands for busy state; platform theme application is one of the few places that touches `Dispatcher.UIThread` (`src/BS2BG.App/Services/UserPreferencesService.cs:114`).
- **Global state:** `AppBootstrapper` holds a static `IServiceProvider` (`src/BS2BG.App/AppBootstrapper.cs:19`) and registers singleton `ProjectModel`, ViewModels, services, and Core services (`src/BS2BG.App/AppBootstrapper.cs:32`). Use this only for app composition; tests should construct services explicitly where possible.
- **Core dependency boundary:** `src/BS2BG.Core/BS2BG.Core.csproj` targets `netstandard2.1` and must not reference Avalonia, ReactiveUI, App services, or CLI code. App and CLI reference Core (`src/BS2BG.App/BS2BG.App.csproj:23`, `src/BS2BG.Cli/BS2BG.Cli.csproj:17`).
- **CLI boundary:** `src/BS2BG.Cli/Program.cs` composes Core-only services and must not reference `BS2BG.App`; this keeps headless automation viable.
- **Output parity:** `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs`, `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, and `src/BS2BG.Core/Export/BosJsonExportWriter.cs` encode byte-sensitive Java parity rules. Treat these as sacred implementation points.
- **Compiled bindings:** Root AXAML and every data template must declare `x:DataType`; `src/BS2BG.App/BS2BG.App.csproj:7` enables compiled bindings by default and `src/BS2BG.App/Views/MainWindow.axaml:15` demonstrates the root pattern.
- **ReactiveUI conventions:** ViewModels inherit `ReactiveObject`, use `[Reactive]` and `[ObservableAsProperty]`, and expose `ReactiveCommand` instances; do not reintroduce retired custom relay commands.
- **Circular imports:** No project-level circular references are present: App → Core, CLI → Core, Tests → App/CLI/Core. Within App, code-behind can call ViewModel methods for view-only glue, but ViewModels should not know Avalonia controls.
- **Generated directories:** `bin/` and `obj/` under `src/BS2BG.*` and `tests/BS2BG.Tests/` are build output directories and are not architecture sources.

## Anti-Patterns

### Putting Domain Logic in Avalonia Code-Behind

**What happens:** Code-behind in `src/BS2BG.App/Views/MainWindow.axaml.cs` exists for control-specific glue such as multi-selection filter forwarding and drag/drop forwarding.
**Why it's wrong:** Adding import, generation, serialization, or assignment rules here bypasses ViewModel tests and couples Core behavior to Avalonia controls.
**Do this instead:** Put workflow logic in `src/BS2BG.App/ViewModels/*.cs` and domain logic in `src/BS2BG.Core/*`; keep code-behind like `ApplyNpcColumnFilterSelection` (`src/BS2BG.App/Views/MainWindow.axaml.cs:109`) limited to view-owned control state.

### Letting Core Depend on App or Avalonia

**What happens:** Core services are consumed by both App and CLI; adding UI interfaces or Avalonia types to Core would force CLI and tests to load UI dependencies.
**Why it's wrong:** It breaks the clean automation path in `src/BS2BG.Cli/Program.cs` and violates the `netstandard2.1` portability boundary in `src/BS2BG.Core/BS2BG.Core.csproj`.
**Do this instead:** Add Core abstractions only for domain/I/O behavior under `src/BS2BG.Core/`; add Avalonia implementations behind App service interfaces under `src/BS2BG.App/Services/`.

### Recreating Profile Catalogs Without Request Context

**What happens:** Generation and bundle output can silently miss project/local custom profiles if code uses only the bundled catalog.
**Why it's wrong:** Saved projects can reference custom profiles embedded in `.jbs2bg`; automation must resolve them for correct output.
**Do this instead:** Use `RequestScopedProfileCatalogComposer.BuildForProject` in automation/bundle flows (`src/BS2BG.Core/Automation/HeadlessGenerationService.cs:64`, `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:206`) and `ITemplateProfileCatalogService.Current` in App workflows (`src/BS2BG.App/Services/TemplateProfileCatalogService.cs`).

### Direct File Writes for Multi-Artifact Outputs

**What happens:** Direct `File.WriteAllText` calls for project, INI, JSON, or bundle output bypass rollback and ledger reporting.
**Why it's wrong:** BodyGen export writes multiple artifacts; partial writes must be reported and, when possible, rolled back.
**Do this instead:** Use `ProjectFileService.WriteAtomic` (`src/BS2BG.Core/Serialization/ProjectFileService.cs:118`), `BodyGenIniExportWriter.Write` (`src/BS2BG.Core/Export/BodyGenIniExportWriter.cs:13`), `BosJsonExportWriter.Write` (`src/BS2BG.Core/Export/BosJsonExportWriter.cs`), or `PortableProjectBundleService.Create` (`src/BS2BG.Core/Bundling/PortableProjectBundleService.cs:116`).

## Error Handling

**Strategy:** Expected user/input/I/O errors return typed results, diagnostics, validation reports, booleans, or CLI exit codes; unexpected programmer errors still use argument validation and exceptions.

**Patterns:**
- Parser services return result objects with diagnostics for recoverable malformed input (`src/BS2BG.Core/Import/BodySlideXmlParser.cs:21`, `src/BS2BG.Core/Import/NpcTextParser.cs`).
- Validation is non-mutating and severity-coded (`src/BS2BG.Core/Diagnostics/ProjectValidationService.cs:24`).
- CLI generation maps usage, validation, overwrite, and I/O outcomes to stable `AutomationExitCode` values (`src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs`, `src/BS2BG.Cli/Program.cs:156`).
- Atomic write failures carry `FileWriteLedgerEntry` snapshots through `AtomicWriteException` (`src/BS2BG.Core/IO/AtomicWriteException.cs`).
- App ViewModels subscribe to `ReactiveCommand.ThrownExceptions` and set status messages rather than crashing the shell (`src/BS2BG.App/ViewModels/TemplatesViewModel.cs:189`).
- Preferences tolerate unreadable/corrupt files by returning defaults (`src/BS2BG.App/Services/UserPreferencesService.cs:54`).

## Cross-Cutting Concerns

**Logging:** No central logging abstraction is present. Avalonia startup calls `.LogToTrace()` in `src/BS2BG.App/Program.cs:21`; CLI writes user-facing success/failure output to stdout/stderr in `src/BS2BG.Cli/Program.cs:296`.
**Validation:** Use `ProjectValidationService.Validate` for project health and export readiness (`src/BS2BG.Core/Diagnostics/ProjectValidationService.cs`); use parser/import diagnostics for malformed external files (`src/BS2BG.Core/Import/`).
**Authentication:** Not applicable; BS2BG is a local desktop/CLI utility with no detected network authentication surface.
**Preferences:** Persist user preferences under `%APPDATA%\jBS2BG\user-preferences.json` through `UserPreferencesService` (`src/BS2BG.App/Services/UserPreferencesService.cs:46`).
**Profiles:** Bundled profile JSON files at repo root are linked into App/CLI output (`src/BS2BG.App/BS2BG.App.csproj:31`, `src/BS2BG.Cli/BS2BG.Cli.csproj:21`); custom profiles flow through `src/BS2BG.App/Services/UserProfileStore.cs` and project save context.
**Release and packaging:** Release scripts and docs live under `tools/release/` and `docs/release/`; release trust tests live in `tests/BS2BG.Tests/ReleaseTrustTests.cs` and `tests/BS2BG.Tests/ReleasePackagingScriptTests.cs`.
**Porting reference:** Use the project `java-ref` skill and files under `src/com/asdasfa/jbs2bg/` before changing Java-parity behavior.

---

*Architecture analysis: 2026-04-28*
