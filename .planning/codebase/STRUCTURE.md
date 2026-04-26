# Codebase Structure

**Analysis Date:** 2026-04-26

## Directory Layout

```text
jBS2BG/
├── BS2BG.sln                         # .NET solution containing Core, App, and Tests projects
├── Directory.Build.props             # Shared nullable, implicit usings, analyzer settings
├── Directory.Packages.props          # Central NuGet package versions
├── AGENTS.md                         # Project-specific agent instructions and architecture notes
├── PRD.md                            # Product requirements and parity/milestone context
├── settings.json                     # Skyrim CBBE / Fallout 4 CBBE slider profile data copied to App output
├── settings_UUNP.json                # Skyrim UUNP slider profile data copied to App output
├── src/
│   ├── BS2BG.Core/                   # Portable domain, import, generation, export, serialization logic
│   │   ├── Export/                   # BodyGen INI and BoS JSON output writers
│   │   ├── Formatting/               # Byte-sensitive slider math and Java-compatible float formatting
│   │   ├── Generation/               # Template/morph generation services and profile catalog types
│   │   ├── Import/                   # BodySlide XML and NPC text parsers
│   │   ├── IO/                       # Atomic file write helpers
│   │   ├── Models/                   # Project aggregate and domain model classes
│   │   ├── Morphs/                   # Morph assignment and random provider abstractions
│   │   └── Serialization/            # `.jbs2bg` project file load/save service
│   ├── BS2BG.App/                    # Avalonia desktop app, ReactiveUI ViewModels, platform services
│   │   ├── Services/                 # Dialog, picker, clipboard, preferences, image, undo/redo services
│   │   ├── Themes/                   # Avalonia resource dictionaries
│   │   ├── ViewModels/               # Main, Templates, Morphs, and inspector ViewModels
│   │   └── Views/                    # MainWindow AXAML and code-behind
│   └── com/                          # Legacy Java reference source, not part of C# solution
├── tests/
│   ├── BS2BG.Tests/                  # xUnit v3 + FluentAssertions + Avalonia Headless tests
│   ├── fixtures/                     # Input/expected golden fixture corpus
│   └── tools/                        # Fixture generation tooling
├── openspec/                         # Current and archived OpenSpec change/spec files
├── tools/release/                    # Release packaging scripts
├── docs/release/                     # Release process documentation
├── assets/                           # Legacy Java/JavaFX assets
├── bin/                              # Legacy Java build outputs, not used by C# projects
└── artifacts/                        # Build/test/release outputs; generated
```

## Directory Purposes

**Solution root:**
- Purpose: Owns build-wide configuration, profile JSON files, product/spec context, and the solution file.
- Contains: `BS2BG.sln`, `Directory.Build.props`, `Directory.Packages.props`, `settings.json`, `settings_UUNP.json`, `PRD.md`, `AGENTS.md`.
- Key files: `BS2BG.sln`, `Directory.Build.props`, `Directory.Packages.props`, `settings.json`, `settings_UUNP.json`.

**`src/BS2BG.Core/`:**
- Purpose: Portable conversion engine and domain model; keep it free of Avalonia/ReactiveUI/platform UI dependencies.
- Contains: `netstandard2.1` Core project and subdirectories for import, formatting, generation, export, models, morph assignment, serialization, and IO.
- Key files: `src/BS2BG.Core/BS2BG.Core.csproj`, `src/BS2BG.Core/Models/ProjectModel.cs`, `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Serialization/ProjectFileService.cs`.

**`src/BS2BG.Core/Models/`:**
- Purpose: Define mutable domain objects used by import, generation, serialization, and ViewModels.
- Contains: Project aggregate, profile mapping, slider presets, set sliders, morph target base class, custom targets, NPCs.
- Key files: `src/BS2BG.Core/Models/ProjectModel.cs`, `src/BS2BG.Core/Models/ProjectModelNode.cs`, `src/BS2BG.Core/Models/SliderPreset.cs`, `src/BS2BG.Core/Models/SetSlider.cs`, `src/BS2BG.Core/Models/MorphTargetBase.cs`, `src/BS2BG.Core/Models/CustomMorphTarget.cs`, `src/BS2BG.Core/Models/Npc.cs`.

**`src/BS2BG.Core/Import/`:**
- Purpose: Parse external user inputs into domain models with diagnostics.
- Contains: BodySlide XML parser/result/diagnostic and NPC text parser/result/diagnostic.
- Key files: `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.Core/Import/NpcTextParser.cs`, `src/BS2BG.Core/Import/BodySlideXmlImportResult.cs`, `src/BS2BG.Core/Import/NpcImportResult.cs`.

**`src/BS2BG.Core/Formatting/`:**
- Purpose: Perform byte-sensitive slider math, Java-compatible float formatting, and BoS JSON text layout.
- Contains: Formatting-only `SliderPreset`, `SetSlider`, profile/default/multiplier types, and formatter classes.
- Key files: `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`, `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs`, `src/BS2BG.Core/Formatting/SliderProfile.cs`.

**`src/BS2BG.Core/Generation/`:**
- Purpose: Adapt domain models to formatting models and produce template/morph strings.
- Contains: `TemplateGenerationService`, `MorphGenerationService`, profile catalog, profile JSON service, result types.
- Key files: `src/BS2BG.Core/Generation/TemplateGenerationService.cs`, `src/BS2BG.Core/Generation/MorphGenerationService.cs`, `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs`, `src/BS2BG.Core/Generation/SliderProfileJsonService.cs`.

**`src/BS2BG.Core/Morphs/`:**
- Purpose: Encapsulate morph target/NPC assignment operations and random preset selection.
- Contains: Assignment service, random assignment interface, default random provider.
- Key files: `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`, `src/BS2BG.Core/Morphs/IRandomAssignmentProvider.cs`, `src/BS2BG.Core/Morphs/RandomAssignmentProvider.cs`.

**`src/BS2BG.Core/Export/`:**
- Purpose: Persist generated outputs using the correct filenames, encodings, line endings, and atomic write semantics.
- Contains: BodyGen INI export writer, BoS JSON export writer, export result records/classes.
- Key files: `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`.

**`src/BS2BG.Core/Serialization/`:**
- Purpose: Load/save `.jbs2bg` project JSON and map DTOs to domain models.
- Contains: `ProjectFileService` and private DTO/converter types.
- Key files: `src/BS2BG.Core/Serialization/ProjectFileService.cs`.

**`src/BS2BG.Core/IO/`:**
- Purpose: Centralize safe atomic writes and batch rollback behavior.
- Contains: `AtomicFileWriter`.
- Key files: `src/BS2BG.Core/IO/AtomicFileWriter.cs`.

**`src/BS2BG.App/`:**
- Purpose: Desktop executable using Avalonia 12, ReactiveUI, and Microsoft DI.
- Contains: App startup, DI bootstrapper, shell constants, AXAML resources, services, ViewModels, and views.
- Key files: `src/BS2BG.App/BS2BG.App.csproj`, `src/BS2BG.App/Program.cs`, `src/BS2BG.App/App.axaml`, `src/BS2BG.App/App.axaml.cs`, `src/BS2BG.App/AppBootstrapper.cs`, `src/BS2BG.App/AppShell.cs`.

**`src/BS2BG.App/ViewModels/`:**
- Purpose: Hold UI state, commands, validation/status messages, filtering, and workflow orchestration.
- Contains: Main shell ViewModel, Templates ViewModel, Morphs ViewModel, slider inspector row ViewModel, collection observable helper.
- Key files: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/ViewModels/SetSliderInspectorRowViewModel.cs`, `src/BS2BG.App/ViewModels/CollectionChangedObservable.cs`.

**`src/BS2BG.App/Services/`:**
- Purpose: Provide App-layer infrastructure and platform adapters behind interfaces.
- Contains: Window-backed file/dialog/clipboard/image/NPC picker services, profile catalog factory, user preferences, undo/redo, command palette descriptors, null/empty test/design-time implementations.
- Key files: `src/BS2BG.App/Services/WindowFileDialogService.cs`, `src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs`, `src/BS2BG.App/Services/WindowNpcTextFilePicker.cs`, `src/BS2BG.App/Services/WindowClipboardService.cs`, `src/BS2BG.App/Services/UndoRedoService.cs`, `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs`, `src/BS2BG.App/Services/UserPreferencesService.cs`.

**`src/BS2BG.App/Views/`:**
- Purpose: Define the visible Avalonia shell and bridge UI-only events not expressible as simple bindings.
- Contains: `MainWindow.axaml` and `MainWindow.axaml.cs`.
- Key files: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs`.

**`src/BS2BG.App/Themes/`:**
- Purpose: Store application-level Avalonia styles/resources.
- Contains: Theme resource dictionary.
- Key files: `src/BS2BG.App/Themes/ThemeResources.axaml`.

**`tests/BS2BG.Tests/`:**
- Purpose: Test Core, App services, ViewModels, Avalonia shell behavior, packaging scripts, and golden parity.
- Contains: xUnit v3 test classes at project root, `AvaloniaTestApp`, and `TestModuleInitializer`.
- Key files: `tests/BS2BG.Tests/BS2BG.Tests.csproj`, `tests/BS2BG.Tests/TestModuleInitializer.cs`, `tests/BS2BG.Tests/AvaloniaTestApp.cs`, `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, `tests/BS2BG.Tests/ExportWriterTests.cs`, `tests/BS2BG.Tests/MainWindowViewModelTests.cs`.

**`tests/fixtures/`:**
- Purpose: Store fixture input/expected files used by golden-file and parser tests.
- Contains: Input fixtures, expected output corpus, fixture README, minimal math walkthrough.
- Key files: `tests/fixtures/README.md`, `tests/fixtures/inputs/minimal/MATH-WALKTHROUGH.md`, `tests/fixtures/expected/`.

**`src/com/`:**
- Purpose: Legacy Java reference implementation for parity checks and historical porting context.
- Contains: Java source under `src/com/asdasfa/jbs2bg/`.
- Key files: `src/com/asdasfa/jbs2bg/MainController.java`, `src/com/asdasfa/jbs2bg/data/Settings.java`, `src/com/asdasfa/jbs2bg/data/NPC.java`.

**`openspec/`:**
- Purpose: Track current and archived OpenSpec capabilities/changes.
- Contains: `openspec/specs/`, `openspec/changes/`, `openspec/changes/archive/`.
- Key files: `openspec/specs/reactive-mvvm-conventions/spec.md`, `openspec/specs/template-generation-flow/spec.md`, `openspec/specs/morph-assignment-flow/spec.md`.

**Generated/build directories:**
- Purpose: Store local build, IDE, test, and release outputs.
- Contains: `src/**/bin/`, `src/**/obj/`, `tests/**/bin/`, `tests/**/obj/`, `artifacts/`, `.vs/`, IDE/tool cache directories.
- Key files: Not source locations; do not add new source code here.

## Key File Locations

**Entry Points:**
- `src/BS2BG.App/Program.cs`: Desktop process entry point and Avalonia/ReactiveUI/DI setup.
- `src/BS2BG.App/App.axaml.cs`: Avalonia application lifetime hook that resolves `MainWindow`.
- `src/BS2BG.App/Views/MainWindow.axaml`: Main UI definition, key bindings, menu, command palette, Templates tab, Morphs tab.
- `src/BS2BG.App/Views/MainWindow.axaml.cs`: Code-behind for view-only events, window dimensions, service attachment, drag/drop dispatch.

**Configuration:**
- `BS2BG.sln`: Solution file.
- `Directory.Build.props`: Nullable, implicit usings, analyzers.
- `Directory.Packages.props`: Central package versions.
- `src/BS2BG.Core/BS2BG.Core.csproj`: Core target/framework/package references.
- `src/BS2BG.App/BS2BG.App.csproj`: App target/framework/Avalonia compiled binding setting/content copies.
- `tests/BS2BG.Tests/BS2BG.Tests.csproj`: Test dependencies and fixture copying.
- `settings.json`: Slider defaults/multipliers/inverted data used for Skyrim CBBE and Fallout 4 CBBE profiles.
- `settings_UUNP.json`: Slider defaults/multipliers/inverted data used for Skyrim UUNP profile.

**Core Logic:**
- `src/BS2BG.Core/Models/ProjectModel.cs`: Shared project aggregate and dirty tracking.
- `src/BS2BG.Core/Import/BodySlideXmlParser.cs`: BodySlide XML parser.
- `src/BS2BG.Core/Import/NpcTextParser.cs`: NPC text parser and encoding fallback.
- `src/BS2BG.Core/Generation/TemplateGenerationService.cs`: Domain-to-formatting adapter for templates and BoS JSON previews.
- `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`: Slider math and output formatting pipeline.
- `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs`: Java/minimal-json float formatting parity.
- `src/BS2BG.Core/Generation/MorphGenerationService.cs`: Morph lines generation.
- `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`: Custom target/NPC preset assignment operations.
- `src/BS2BG.Core/Serialization/ProjectFileService.cs`: `.jbs2bg` project load/save.
- `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`: `templates.ini` and `morphs.ini` writer.
- `src/BS2BG.Core/Export/BosJsonExportWriter.cs`: BoS JSON writer.
- `src/BS2BG.Core/IO/AtomicFileWriter.cs`: Atomic file/batch writer.

**Application Logic:**
- `src/BS2BG.App/AppBootstrapper.cs`: Service registration and object graph composition.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`: Shell commands, project lifecycle, export workflows, busy/title/theme/command palette state.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`: Preset import/edit/generation workflow.
- `src/BS2BG.App/ViewModels/MorphsViewModel.cs`: NPC/custom target/assignment/morph generation workflow.
- `src/BS2BG.App/Services/UndoRedoService.cs`: Undo/redo stacks and replay guard.
- `src/BS2BG.App/Services/WindowFileDialogService.cs`: Project/export folder pickers.
- `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs`: Runtime profile catalog loading.

**Testing:**
- `tests/BS2BG.Tests/SliderMathFormatterTests.cs`: Slider math/formatting parity tests.
- `tests/BS2BG.Tests/ExportWriterTests.cs`: Export writer tests.
- `tests/BS2BG.Tests/BodySlideXmlParserTests.cs`: XML parser tests.
- `tests/BS2BG.Tests/ProjectFileServiceTests.cs`: Project serialization tests.
- `tests/BS2BG.Tests/TemplatesViewModelTests.cs`: Templates ViewModel workflow tests.
- `tests/BS2BG.Tests/MorphsViewModelTests.cs`: Morphs ViewModel workflow tests.
- `tests/BS2BG.Tests/MainWindowViewModelTests.cs`: Shell workflow tests.
- `tests/BS2BG.Tests/AppShellTests.cs`: Avalonia shell tests.
- `tests/BS2BG.Tests/TestModuleInitializer.cs`: ReactiveUI scheduler/test initialization.
- `tests/fixtures/expected/`: Golden expected output corpus; do not edit casually.

## Naming Conventions

**Files:**
- One primary C# type per file using PascalCase: `ProjectModel.cs`, `TemplateGenerationService.cs`, `MorphsViewModel.cs`.
- Interfaces use `I` prefix and live near their implementations: `IFileDialogService.cs`, `IClipboardService.cs`, `IRandomAssignmentProvider.cs`.
- Avalonia views use `.axaml` plus `.axaml.cs`: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs`.
- Tests use `{Subject}Tests.cs`: `ProjectFileServiceTests.cs`, `WindowImageViewServiceTests.cs`, `TemplateGenerationServiceTests.cs`.
- Result/diagnostic transport types use `{Feature}Result.cs` and `{Feature}Diagnostic.cs`: `BodySlideXmlImportResult.cs`, `NpcImportDiagnostic.cs`, `MorphGenerationResult.cs`.

**Directories:**
- Project directories match root namespace: `src/BS2BG.Core/`, `src/BS2BG.App/`, `tests/BS2BG.Tests/`.
- Core feature directories use singular/plural domain names by responsibility: `Models`, `Import`, `Generation`, `Formatting`, `Export`, `Serialization`, `Morphs`, `IO`.
- App feature directories separate MVVM roles: `Views`, `ViewModels`, `Services`, `Themes`.
- Tests are currently flat under `tests/BS2BG.Tests/`; add new test files there unless a broader test folder refactor is intentionally planned.

## Where to Add New Code

**New Core import format:**
- Primary code: `src/BS2BG.Core/Import/`
- Models/results/diagnostics: `src/BS2BG.Core/Import/` for parser-specific DTOs; `src/BS2BG.Core/Models/` only for persistent domain concepts.
- Tests: `tests/BS2BG.Tests/{ParserName}Tests.cs`

**New template or morph generation behavior:**
- Primary code: `src/BS2BG.Core/Generation/` for orchestration/service APIs.
- Formatting/math code: `src/BS2BG.Core/Formatting/` only when output math or byte formatting changes.
- Tests: `tests/BS2BG.Tests/TemplateGenerationServiceTests.cs`, `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, or `tests/BS2BG.Tests/MorphCoreTests.cs` depending on scope.

**New export type:**
- Primary code: `src/BS2BG.Core/Export/`
- Shared write utilities: `src/BS2BG.Core/IO/AtomicFileWriter.cs` only for reusable atomic write behavior.
- Tests: `tests/BS2BG.Tests/ExportWriterTests.cs`

**New project state:**
- Domain model: `src/BS2BG.Core/Models/`
- Project aggregate wiring: `src/BS2BG.Core/Models/ProjectModel.cs`
- Serialization: `src/BS2BG.Core/Serialization/ProjectFileService.cs`
- ViewModel exposure: `src/BS2BG.App/ViewModels/`
- Tests: `tests/BS2BG.Tests/ProjectFileServiceTests.cs` and matching ViewModel tests.

**New UI feature in existing shell:**
- AXAML: `src/BS2BG.App/Views/MainWindow.axaml`
- View-only event glue: `src/BS2BG.App/Views/MainWindow.axaml.cs`
- State/commands: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, or `src/BS2BG.App/ViewModels/MorphsViewModel.cs`
- Platform service: `src/BS2BG.App/Services/`
- Tests: `tests/BS2BG.Tests/{ViewModelOrService}Tests.cs` and Avalonia headless shell tests when UI behavior is involved.

**New window-backed platform operation:**
- Interface: `src/BS2BG.App/Services/I{Operation}Service.cs`
- Window implementation: `src/BS2BG.App/Services/Window{Operation}Service.cs`
- Null/empty test implementation: colocate with the interface or implementation in `src/BS2BG.App/Services/` following existing patterns.
- DI registration: `src/BS2BG.App/AppBootstrapper.cs`
- Window attachment: `src/BS2BG.App/Views/MainWindow.axaml.cs` if a `TopLevel` owner is required.
- Tests: `tests/BS2BG.Tests/{Operation}ServiceTests.cs`

**New ViewModel-only helper:**
- Implementation: `src/BS2BG.App/ViewModels/`
- Use `ReactiveObject`/ReactiveUI only when it owns observable UI state; use static/internal helper for pure transformations.
- Tests: `tests/BS2BG.Tests/{HelperOrViewModel}Tests.cs`

**New command palette item:**
- Registration: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`
- Descriptor model: `src/BS2BG.App/Services/CommandDescriptor.cs`
- UI binding already exists in `src/BS2BG.App/Views/MainWindow.axaml`.
- Tests: `tests/BS2BG.Tests/MainWindowViewModelTests.cs`

**New slider profile data handling:**
- Profile data files: root `settings.json` or `settings_UUNP.json` only when changing shipped profile data intentionally.
- Loader/catalog code: `src/BS2BG.Core/Generation/SliderProfileJsonService.cs`, `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs`, `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs`
- Tests: `tests/BS2BG.Tests/SliderProfileTests.cs`, `tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs`

**Utilities:**
- Core utilities reusable by parser/export/generation code: `src/BS2BG.Core/IO/` or the relevant Core feature directory.
- App-only utilities: `src/BS2BG.App/Services/` for infrastructure or `src/BS2BG.App/ViewModels/` for UI state helpers.
- Test helpers: `tests/BS2BG.Tests/` unless the fixture tooling belongs under `tests/tools/`.

## Special Directories

**`src/com/`:**
- Purpose: Java reference implementation for parity research.
- Generated: No.
- Committed: Yes.

**`src/jfx-8u60-b08/`:**
- Purpose: Embedded legacy OpenJFX source snapshot referenced by historical Java project context.
- Generated: No.
- Committed: Yes.

**`assets/`:**
- Purpose: Legacy Java/JavaFX assets.
- Generated: No.
- Committed: Yes.

**`bin/`:**
- Purpose: Legacy Java build/output directory at repository root.
- Generated: Yes/legacy.
- Committed: Present in repository context; do not add C# source here.

**`src/**/bin/` and `src/**/obj/`:**
- Purpose: .NET build outputs and intermediate files.
- Generated: Yes.
- Committed: No for new outputs; do not edit or add source here.

**`tests/**/bin/` and `tests/**/obj/`:**
- Purpose: Test build outputs, copied fixtures, and intermediate files.
- Generated: Yes.
- Committed: No for new outputs; do not edit or add source here.

**`tests/fixtures/expected/`:**
- Purpose: Golden expected output corpus used for byte-identical parity tests.
- Generated: Regenerated only through fixture tooling when intentionally updating parity baselines.
- Committed: Yes; treat as sacred and avoid manual edits.

**`tests/fixtures/inputs/`:**
- Purpose: Source fixture inputs for parser/generation/export tests.
- Generated: No.
- Committed: Yes.

**`tests/tools/`:**
- Purpose: Test fixture generation scripts and related tooling.
- Generated: No.
- Committed: Yes.

**`tools/release/`:**
- Purpose: Release packaging automation.
- Generated: No.
- Committed: Yes.

**`docs/release/`:**
- Purpose: Release process and packaging documentation.
- Generated: No.
- Committed: Yes.

**`artifacts/`:**
- Purpose: Build/test/release artifacts such as `artifacts/codex-build`, `artifacts/release`, and `artifacts/test-out`.
- Generated: Yes.
- Committed: No for new outputs.

**`openspec/`:**
- Purpose: OpenSpec capability specs, open changes, and archived change proposals.
- Generated: No.
- Committed: Yes.

**`.planning/codebase/`:**
- Purpose: Generated codebase maps consumed by GSD planning/execution commands.
- Generated: Yes.
- Committed: Intended planning artifact location for mapper output.

---

*Structure analysis: 2026-04-26*
