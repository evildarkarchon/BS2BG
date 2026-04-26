<!-- refreshed: 2026-04-26 -->
# Architecture

**Analysis Date:** 2026-04-26

## System Overview

```text
┌─────────────────────────────────────────────────────────────┐
│                 Avalonia Desktop Shell                       │
│  `src/BS2BG.App/Program.cs` → `App.axaml.cs` → `Views/`      │
├──────────────────┬──────────────────┬───────────────────────┤
│   Templates UI   │    Morphs UI     │   Shell Commands       │
│ `Views/Main...`  │ `Views/Main...`  │ `MainWindowViewModel`  │
└────────┬─────────┴────────┬─────────┴──────────┬────────────┘
         │                  │                     │
         ▼                  ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│              ReactiveUI ViewModel Orchestration              │
│ `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`             │
│ `src/BS2BG.App/ViewModels/MorphsViewModel.cs`                │
│ `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`            │
└────────┬──────────────────┬─────────────────────┬────────────┘
         │                  │                     │
         ▼                  ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│             Core Domain, Import, Generation, Export          │
│ `src/BS2BG.Core/Models/` `Import/` `Generation/` `Export/`   │
└────────┬──────────────────┬─────────────────────┬────────────┘
         │                  │                     │
         ▼                  ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│       Filesystem Artifacts and Reference Data                │
│ `settings.json`, `settings_UUNP.json`, `.jbs2bg`, INI, JSON  │
└─────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| Avalonia startup | Creates the desktop app, registers ReactiveUI/Microsoft DI, and starts classic desktop lifetime. | `src/BS2BG.App/Program.cs` |
| Application lifetime | Resolves the main window from the service provider. | `src/BS2BG.App/App.axaml.cs` |
| Dependency composition | Registers shared project model, Core services, App services, ViewModels, and `MainWindow`. | `src/BS2BG.App/AppBootstrapper.cs` |
| Main window view | Hosts menus, search, command palette, Templates tab, Morphs tab, drag/drop, and small UI event bridges. | `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs` |
| Shell ViewModel | Owns project file lifecycle, export commands, busy aggregation, title/status, theme preference, undo/redo, global search, and command palette. | `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` |
| Templates ViewModel | Owns BodySlide XML import, preset collection editing, profile selection, slider inspector rows, template preview/generation, and BoS JSON preview copy. | `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` |
| Morphs ViewModel | Owns NPC import, NPC database filtering, custom morph targets, preset assignments, random fill/clear operations, image lookup/viewing, and morph text generation. | `src/BS2BG.App/ViewModels/MorphsViewModel.cs` |
| Project model | Central mutable aggregate for slider presets, custom morph targets, morphed NPCs, dirty state, and change version. | `src/BS2BG.Core/Models/ProjectModel.cs` |
| Import services | Convert BodySlide XML and NPC text into Core models plus diagnostics. | `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.Core/Import/NpcTextParser.cs` |
| Generation services | Convert model aggregates into `templates.ini`, BoS JSON preview text, and `morphs.ini` text. | `src/BS2BG.Core/Generation/TemplateGenerationService.cs`, `src/BS2BG.Core/Generation/MorphGenerationService.cs` |
| Formatting pipeline | Applies defaults, inversion, multipliers, rounding, float formatting, and BoS JSON layout. | `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`, `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs` |
| Export writers | Persist BodyGen INI files and BoS JSON files using atomic writes. | `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`, `src/BS2BG.Core/IO/AtomicFileWriter.cs` |
| Project serialization | Loads/saves `.jbs2bg` project JSON and resolves saved preset references. | `src/BS2BG.Core/Serialization/ProjectFileService.cs` |
| Tests | Verify Core parity, App ViewModels, services, Avalonia shell behavior, and release packaging. | `tests/BS2BG.Tests/` |

## Pattern Overview

**Overall:** MVVM desktop shell over a pure Core domain/service layer, with explicit dependency injection and service adapters for UI/platform boundaries.

**Key Characteristics:**
- Keep portable conversion logic in `src/BS2BG.Core/`; do not reference Avalonia, ReactiveUI, or App services from Core.
- Use `ProjectModel` as the in-memory aggregate shared by `MainWindowViewModel`, `TemplatesViewModel`, and `MorphsViewModel`.
- Use ReactiveUI `ReactiveObject`, `[Reactive]`, `[ObservableAsProperty]`, `ReactiveCommand`, and observable `canExecute` gates in ViewModels.
- Keep filesystem/UI interactions behind App service interfaces such as `IFileDialogService`, `IBodySlideXmlFilePicker`, `INpcTextFilePicker`, `IClipboardService`, `IImageViewService`, and `IAppDialogService` in `src/BS2BG.App/Services/`.
- Write outputs through Core writers and `AtomicFileWriter`; do not write export files directly from ViewModels.

## Layers

**Presentation Layer:**
- Purpose: Define Avalonia UI, compiled bindings, keyboard shortcuts, tab layout, and minimal UI event forwarding.
- Location: `src/BS2BG.App/Views/`, `src/BS2BG.App/App.axaml`, `src/BS2BG.App/Themes/`
- Contains: AXAML views, `MainWindow` code-behind, theme resources.
- Depends on: `BS2BG.App.ViewModels`, Avalonia controls, service adapter attach hooks.
- Used by: Avalonia lifetime in `src/BS2BG.App/App.axaml.cs`.

**Application/ViewModel Layer:**
- Purpose: Coordinate user workflows, command gating, UI state, validation messages, undo/redo recording, and calls into Core services.
- Location: `src/BS2BG.App/ViewModels/`
- Contains: `MainWindowViewModel`, `TemplatesViewModel`, `MorphsViewModel`, inspector row ViewModels, observable helpers.
- Depends on: Core models/services, App service interfaces, ReactiveUI.
- Used by: `src/BS2BG.App/Views/MainWindow.axaml` compiled bindings and code-behind.

**Platform Adapter Layer:**
- Purpose: Isolate Avalonia storage provider, clipboard, dialog, image viewing, notification, profile catalog creation, and user preferences.
- Location: `src/BS2BG.App/Services/`
- Contains: `Window*` concrete services plus `I*` interfaces and null/empty test/design-time implementations in the same files.
- Depends on: Avalonia for window-bound implementations; Core for profile/data concepts.
- Used by: ViewModels via interfaces and `MainWindow` via `Attach(TopLevel)` for window-owned services.

**Core Domain Layer:**
- Purpose: Represent project state and domain concepts independently of UI/platform runtime.
- Location: `src/BS2BG.Core/Models/`
- Contains: `ProjectModel`, `SliderPreset`, `SetSlider`, `MorphTargetBase`, `CustomMorphTarget`, `Npc`, profile mapping.
- Depends on: .NET base class libraries only.
- Used by: Core services, App ViewModels, tests.

**Core Import/Generation/Export Layer:**
- Purpose: Convert between external file formats and domain models/output text.
- Location: `src/BS2BG.Core/Import/`, `src/BS2BG.Core/Generation/`, `src/BS2BG.Core/Formatting/`, `src/BS2BG.Core/Export/`, `src/BS2BG.Core/Serialization/`, `src/BS2BG.Core/IO/`
- Contains: parsers, generation services, formatter functions, project JSON service, atomic writers.
- Depends on: Core models, `System.Text.Json`, `XDocument`, filesystem APIs.
- Used by: ViewModels and export workflows.

**Test Layer:**
- Purpose: Validate byte-identical Java parity, ViewModel workflows, service adapters, UI shell behavior, and release scripts.
- Location: `tests/BS2BG.Tests/`, `tests/fixtures/`
- Contains: xUnit v3 tests, Avalonia headless test bootstrapping, golden expected outputs.
- Depends on: `BS2BG.App`, `BS2BG.Core`, FluentAssertions, Avalonia Headless.
- Used by: `dotnet test` and release confidence checks.

## Data Flow

### Primary Request Path

1. Application starts at `Program.Main` and `BuildAvaloniaApp` (`src/BS2BG.App/Program.cs:9`, `src/BS2BG.App/Program.cs:12`).
2. ReactiveUI/Microsoft DI are registered with `AppBootstrapper.ConfigureServices` (`src/BS2BG.App/Program.cs:18`, `src/BS2BG.App/AppBootstrapper.cs:27`).
3. `App.OnFrameworkInitializationCompleted` resolves `MainWindow` from DI (`src/BS2BG.App/App.axaml.cs:13`, `src/BS2BG.App/App.axaml.cs:16`).
4. `MainWindow` sets `DataContext`, title, dimensions, and attaches window-backed services (`src/BS2BG.App/Views/MainWindow.axaml.cs:29`, `src/BS2BG.App/Views/MainWindow.axaml.cs:52`).
5. `MainWindow.axaml` binds menus, keyboard shortcuts, tabs, buttons, lists, and status fields to `MainWindowViewModel` and child ViewModels (`src/BS2BG.App/Views/MainWindow.axaml:13`, `src/BS2BG.App/Views/MainWindow.axaml:198`).

### BodySlide Template Import and Generation Flow

1. User invokes import via button, drag/drop, or command; the command is bound to `Templates.ImportPresetsCommand` (`src/BS2BG.App/Views/MainWindow.axaml:212`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs:115`).
2. `TemplatesViewModel.ImportPresetsAsync` requests XML paths through `IBodySlideXmlFilePicker` (`src/BS2BG.App/ViewModels/TemplatesViewModel.cs:227`).
3. `BodySlideXmlParser.ParseFiles` loads each XML file and parses `SliderPresets`/`Preset`/`SetSlider` elements into `SliderPreset`/`SetSlider` models (`src/BS2BG.Core/Import/BodySlideXmlParser.cs:29`, `src/BS2BG.Core/Import/BodySlideXmlParser.cs:62`).
4. `TemplatesViewModel` adds/edits presets in the shared `ProjectModel.SliderPresets` collection and records undo/redo operations around mutations (`src/BS2BG.App/ViewModels/TemplatesViewModel.cs:176`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs:255`).
5. `TemplateGenerationService.GenerateTemplates` sorts presets and formats each preset through `SliderMathFormatter.FormatTemplateLine` (`src/BS2BG.Core/Generation/TemplateGenerationService.cs:26`, `src/BS2BG.Core/Formatting/SliderMathFormatter.cs:8`).
6. `SliderMathFormatter` injects missing default sliders, applies inversion/multipliers/rounding, and returns `templates.ini` lines (`src/BS2BG.Core/Formatting/SliderMathFormatter.cs:96`, `src/BS2BG.Core/Formatting/SliderMathFormatter.cs:44`).

### Morph Assignment and Export Flow

1. User imports NPC rows through `MorphsViewModel.ImportNpcsCommand` or manages custom targets from the Morphs tab (`src/BS2BG.App/ViewModels/MorphsViewModel.cs:191`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs:194`).
2. `NpcTextParser.ParseFile` reads bytes, detects BOM/UTF-8/fallback encoding, and parses `Mod|Name|EditorID|Race|FormID` rows into `Npc` models (`src/BS2BG.Core/Import/NpcTextParser.cs:18`, `src/BS2BG.Core/Import/NpcTextParser.cs:60`).
3. `MorphAssignmentService` validates custom target names, adds targets/NPCs, and assigns random presets through `IRandomAssignmentProvider` (`src/BS2BG.Core/Morphs/MorphAssignmentService.cs:14`, `src/BS2BG.Core/Morphs/MorphAssignmentService.cs:163`).
4. `MorphGenerationService.GenerateMorphs` converts custom targets and NPCs to BodyGen morph lines (`src/BS2BG.Core/Generation/MorphGenerationService.cs:12`, `src/BS2BG.Core/Generation/MorphGenerationService.cs:30`).
5. `BodyGenIniExportWriter.Write` writes `templates.ini` and `morphs.ini` as an atomic pair with CRLF normalization (`src/BS2BG.Core/Export/BodyGenIniExportWriter.cs:13`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs:21`).

### Project Save/Load Flow

1. `MainWindowViewModel` owns `OpenProjectCommand`, `SaveProjectCommand`, and `SaveProjectAsCommand` (`src/BS2BG.App/ViewModels/MainWindowViewModel.cs:155`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:157`).
2. `IFileDialogService` selects `.jbs2bg` files/folders through a window-attached storage provider (`src/BS2BG.App/Services/WindowFileDialogService.cs:11`, `src/BS2BG.App/Services/WindowFileDialogService.cs:25`).
3. `ProjectFileService.Load` deserializes project JSON DTOs into a fresh `ProjectModel` and resolves saved preset references (`src/BS2BG.Core/Serialization/ProjectFileService.cs:28`, `src/BS2BG.Core/Serialization/ProjectFileService.cs:127`).
4. `ProjectModel.ReplaceWith` clones loaded state into the singleton project and marks it clean (`src/BS2BG.Core/Models/ProjectModel.cs:47`, `src/BS2BG.Core/Models/ProjectModel.cs:80`).
5. `ProjectFileService.Save` serializes the project and writes atomically (`src/BS2BG.Core/Serialization/ProjectFileService.cs:60`, `src/BS2BG.Core/Serialization/ProjectFileService.cs:69`).

**State Management:**
- Primary app state is a singleton `ProjectModel` registered in DI (`src/BS2BG.App/AppBootstrapper.cs:29`).
- Dirty state is event-driven: collections and child `ProjectModelNode` changes call `ProjectModel.MarkDirty` (`src/BS2BG.Core/Models/ProjectModel.cs:127`, `src/BS2BG.Core/Models/ProjectModel.cs:139`).
- ViewModel state is ReactiveUI property-driven; busy and derived shell title use `Observable.CombineLatest` and `ToProperty` (`src/BS2BG.App/ViewModels/MainWindowViewModel.cs:177`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:218`).
- Undo/redo stores closures for each ViewModel mutation and guards replay with `UndoRedoService.IsReplaying` (`src/BS2BG.App/Services/UndoRedoService.cs:16`, `src/BS2BG.App/Services/UndoRedoService.cs:56`).

## Key Abstractions

**Project aggregate:**
- Purpose: Shared mutable root for all current project data and dirty/change events.
- Examples: `src/BS2BG.Core/Models/ProjectModel.cs`, `src/BS2BG.Core/Models/ProjectModelNode.cs`
- Pattern: Observable aggregate root with child subscriptions.

**Domain model nodes:**
- Purpose: Represent presets, sliders, morph targets, custom targets, and NPCs with change notification.
- Examples: `src/BS2BG.Core/Models/SliderPreset.cs`, `src/BS2BG.Core/Models/SetSlider.cs`, `src/BS2BG.Core/Models/MorphTargetBase.cs`, `src/BS2BG.Core/Models/Npc.cs`
- Pattern: Mutable domain objects owned by `ProjectModel` collections.

**Profile catalog:**
- Purpose: Map project profile names to slider defaults, multipliers, and inverted sliders.
- Examples: `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs`, `src/BS2BG.Core/Generation/TemplateProfile.cs`, `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs`
- Pattern: Immutable catalog loaded from `settings.json` and `settings_UUNP.json` during DI composition.

**Formatter service boundary:**
- Purpose: Keep UI-facing model types separate from formatting-specific records used by parity logic.
- Examples: `src/BS2BG.Core/Generation/TemplateGenerationService.cs`, `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`
- Pattern: Generation service maps domain models to formatting models, then delegates pure formatting.

**Platform service interfaces:**
- Purpose: Make dialogs, clipboard, file picking, image display, and notifications replaceable in tests and design-time constructors.
- Examples: `src/BS2BG.App/Services/IFileDialogService.cs`, `src/BS2BG.App/Services/IBodySlideXmlFilePicker.cs`, `src/BS2BG.App/Services/IClipboardService.cs`, `src/BS2BG.App/Services/IImageViewService.cs`
- Pattern: Interface + `Window*` Avalonia implementation + null/empty implementation for tests/design-time.

**Reactive commands:**
- Purpose: Expose user actions with cancellation, busy state, thrown exception streams, and observable can-execute logic.
- Examples: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`
- Pattern: `ReactiveCommand.Create*` with `WhenAnyValue`/`CombineLatest` gates.

## Entry Points

**Desktop executable:**
- Location: `src/BS2BG.App/Program.cs`
- Triggers: OS process startup.
- Responsibilities: Configure Avalonia platform, Inter font, ReactiveUI view registration, Microsoft DI resolver, trace logging, and desktop lifetime.

**Application lifetime:**
- Location: `src/BS2BG.App/App.axaml.cs`
- Triggers: Avalonia framework initialization.
- Responsibilities: Resolve and show `MainWindow`.

**Main UI:**
- Location: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs`
- Triggers: DI-resolved window construction and user input.
- Responsibilities: Bind ViewModel commands/properties, forward selection/drag/drop events, attach window services.

**Core import entry points:**
- Location: `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.Core/Import/NpcTextParser.cs`
- Triggers: ViewModel import commands and tests.
- Responsibilities: Parse external input into models and diagnostics.

**Core export entry points:**
- Location: `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`
- Triggers: shell export commands and tests.
- Responsibilities: Create output directories, normalize file content, sanitize JSON filenames, and write atomically.

**Project serialization entry point:**
- Location: `src/BS2BG.Core/Serialization/ProjectFileService.cs`
- Triggers: open/save shell commands and tests.
- Responsibilities: Translate project models to/from `.jbs2bg` JSON.

## Architectural Constraints

- **Threading:** UI startup uses `[STAThread]` and classic Avalonia desktop lifetime (`src/BS2BG.App/Program.cs:9`). ViewModel command state is ReactiveUI observable-driven; avoid direct dispatcher calls in ViewModels. Code-behind uses `Dispatcher.UIThread.Post` only for the command palette selection deferral (`src/BS2BG.App/Views/MainWindow.axaml.cs:101`).
- **Global state:** `AppBootstrapper` holds a static `IServiceProvider` cache (`src/BS2BG.App/AppBootstrapper.cs:16`). `ProjectModel` is registered as a singleton and is intentionally shared across ViewModels (`src/BS2BG.App/AppBootstrapper.cs:29`). `NpcTextParser` registers code pages in a static constructor (`src/BS2BG.Core/Import/NpcTextParser.cs:16`).
- **Circular imports:** No C# project circular dependency exists: `BS2BG.App` references `BS2BG.Core`; `BS2BG.Core` has no project reference back to App (`src/BS2BG.App/BS2BG.App.csproj:21`, `src/BS2BG.Core/BS2BG.Core.csproj`).
- **UI boundary:** `src/BS2BG.Core/` must stay platform-independent. Add Avalonia integrations only under `src/BS2BG.App/Services/` or `src/BS2BG.App/Views/`.
- **Compiled bindings:** `src/BS2BG.App/BS2BG.App.csproj` enables compiled bindings by default; every new AXAML root/DataTemplate must declare `x:DataType` following `src/BS2BG.App/Views/MainWindow.axaml:11` and `src/BS2BG.App/Views/MainWindow.axaml:183`.
- **Golden output parity:** `SliderMathFormatter`, `JavaFloatFormatting`, and export writers are byte-sensitive. Preserve line endings and float formatting in `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`, `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, and `src/BS2BG.Core/Export/BosJsonExportWriter.cs`.

## Anti-Patterns

### Putting UI dependencies in Core

**What happens:** Core parsers, formatters, model classes, or export writers reference Avalonia, ReactiveUI, dialogs, clipboard, or windows.
**Why it's wrong:** `src/BS2BG.Core/BS2BG.Core.csproj` targets `netstandard2.1` and is the portable parity layer; UI references would break portability and test isolation.
**Do this instead:** Add UI/platform code in `src/BS2BG.App/Services/` behind an interface and inject it through `src/BS2BG.App/AppBootstrapper.cs`.

### Writing files directly from ViewModels

**What happens:** A ViewModel calls `File.WriteAllText`, creates export files, or serializes JSON directly.
**Why it's wrong:** Atomic write semantics and format normalization live in Core writers; bypassing them risks partial writes and byte parity failures.
**Do this instead:** Use `ProjectFileService` for `.jbs2bg`, `BodyGenIniExportWriter` for INI pairs, and `BosJsonExportWriter` for BoS JSON (`src/BS2BG.Core/Serialization/ProjectFileService.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`).

### Mutating project data without undo/dirty awareness

**What happens:** UI workflow code directly changes `ProjectModel` child collections/properties without recording undo/redo or considering replay.
**Why it's wrong:** User-facing edits need undo/redo and dirty state consistency across `MainWindowViewModel`, `TemplatesViewModel`, and `MorphsViewModel`.
**Do this instead:** Follow existing mutation methods that call domain services and `UndoRedoService.Record`, such as preset rename in `src/BS2BG.App/ViewModels/TemplatesViewModel.cs:246` and assignment operations in `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.

### Reintroducing non-ReactiveUI command infrastructure

**What happens:** New ViewModels use custom relay commands, manual `INotifyPropertyChanged` setters, or `Func<bool>` can-execute callbacks.
**Why it's wrong:** Current architecture centralizes ViewModel state in ReactiveUI commands/properties and tests initialize ReactiveUI scheduler services.
**Do this instead:** Use `ReactiveObject`, `[Reactive]`, `[ObservableAsProperty]`, and `ReactiveCommand.Create*` as in `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:27` and `src/BS2BG.App/ViewModels/TemplatesViewModel.cs:115`.

## Error Handling

**Strategy:** Core import services return result objects with diagnostics for recoverable input problems; ViewModels subscribe to `ReactiveCommand.ThrownExceptions` for unexpected command failures; export/serialization services throw for filesystem and programming errors.

**Patterns:**
- Catch expected XML/file read failures in `BodySlideXmlParser.ParseFile` and return `BodySlideXmlImportDiagnostic` (`src/BS2BG.Core/Import/BodySlideXmlParser.cs:17`).
- Catch expected NPC read failures and return `NpcImportDiagnostic` (`src/BS2BG.Core/Import/NpcTextParser.cs:22`).
- Subscribe command exception streams and convert them to status/dialog messages in ViewModels (`src/BS2BG.App/ViewModels/MainWindowViewModel.cs:203`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs:148`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs:253`).
- Throw `ArgumentNullException`/`ArgumentException` for invalid service inputs (`src/BS2BG.Core/IO/AtomicFileWriter.cs:7`, `src/BS2BG.Core/Serialization/ProjectFileService.cs:28`).
- Roll back partially committed atomic batches or raise an aggregate rollback error (`src/BS2BG.Core/IO/AtomicFileWriter.cs:94`, `src/BS2BG.Core/IO/AtomicFileWriter.cs:133`).

## Cross-Cutting Concerns

**Logging:** Avalonia startup logs to trace via `.LogToTrace()` in `src/BS2BG.App/Program.cs:21`. No application-wide structured logging framework is present.

**Validation:** Model/service validation is local and explicit: preset names in `src/BS2BG.Core/Models/SliderPreset.cs:205`, custom target names in `src/BS2BG.Core/Morphs/MorphAssignmentService.cs:181`, parser diagnostics in `src/BS2BG.Core/Import/`, and ViewModel validation messages in `src/BS2BG.App/ViewModels/`.

**Authentication:** Not applicable. The app is an offline desktop utility and no authentication provider is present.

---

*Architecture analysis: 2026-04-26*
