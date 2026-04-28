# Codebase Structure

**Analysis Date:** 2026-04-28

## Directory Layout

```
J:/jBS2BG/
├── src/                         # C# solution projects plus legacy Java reference
│   ├── BS2BG.Core/              # Pure domain/import/generation/export/automation library
│   │   ├── Automation/          # Headless generation and assignment replay contracts/services
│   │   ├── Bundling/            # Portable project bundle preview/create services and contracts
│   │   ├── Diagnostics/         # Validation, recovery, preview, and report formatting
│   │   ├── Export/              # BodyGen INI and BoS JSON file writers/planners
│   │   ├── Formatting/          # Java-parity slider math and float formatting
│   │   ├── Generation/          # Template/morph generation and profile catalog loading
│   │   ├── Import/              # BodySlide XML and NPC text import parsers/previews
│   │   ├── IO/                  # Atomic file writer, ledger, and write exceptions
│   │   ├── Models/              # Project, preset, slider, NPC, morph, and profile models
│   │   ├── Morphs/              # Morph assignment and assignment strategy services
│   │   ├── Serialization/       # .jbs2bg project JSON load/save service
│   │   └── BS2BG.Core.csproj    # netstandard2.1 Core project
│   ├── BS2BG.App/               # Avalonia desktop application
│   │   ├── Services/            # Platform adapters and App service interfaces
│   │   ├── Themes/              # Avalonia theme resources
│   │   ├── ViewModels/          # ReactiveUI root/workspace/row ViewModels
│   │   │   └── Workflow/        # Workflow-specific row/filter/preview/snapshot helpers
│   │   ├── Views/               # MainWindow AXAML and code-behind
│   │   ├── App.axaml(.cs)       # Avalonia Application
│   │   ├── AppBootstrapper.cs   # DI composition root
│   │   ├── AppShell.cs          # Shell constants
│   │   ├── Program.cs           # Desktop entry point
│   │   └── BS2BG.App.csproj     # net10.0 Avalonia project
│   ├── BS2BG.Cli/               # System.CommandLine automation CLI
│   │   ├── Program.cs           # generate/bundle commands and Core composition
│   │   └── BS2BG.Cli.csproj     # net10.0 single-file CLI project
│   └── com/asdasfa/jbs2bg/      # Java jBS2BG reference, not part of C# build
├── tests/
│   ├── BS2BG.Tests/             # xUnit v3 tests for Core, App, CLI, release, and UI headless flows
│   ├── fixtures/                # Input/expected fixture corpus and math walkthrough docs
│   └── tools/                   # Test fixture regeneration tooling
├── openspec/                    # OpenSpec changes, archive, specs, and workflow metadata
├── .planning/                   # GSD project planning state and generated codebase maps
├── .claude/skills/              # Project skills: java-ref, parity-check, OpenSpec commands
├── tools/release/               # Release packaging scripts
├── docs/release/                # Release documentation
├── artifacts/                   # Build/test/release output artifacts
├── assets/                      # Legacy Java project assets
├── bin/                         # Legacy Java packaged binaries
├── settings.json                # Bundled Skyrim CBBE profile data
├── settings_UUNP.json           # Bundled Skyrim UUNP profile data
├── settings_FO4_CBBE.json       # Bundled Fallout 4 CBBE profile data
├── BS2BG.sln                    # Visual Studio solution
├── Directory.Build.props        # Shared nullable/analyzer/implicit-using settings
├── Directory.Packages.props     # Central NuGet package versions
├── PRD.md                       # Product spec and parity guidance
└── AGENTS.md                    # Project instructions and sacred-file guidance
```

## Directory Purposes

**`src/BS2BG.Core/`:**
- Purpose: Pure BS2BG domain and I/O logic shared by desktop, CLI, and tests.
- Contains: Domain models, import parsers, profile catalogs, Java-parity formatting, generation, export writers, serialization, validation, automation, bundling.
- Key files: `src/BS2BG.Core/BS2BG.Core.csproj`, `src/BS2BG.Core/Models/ProjectModel.cs`, `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`, `src/BS2BG.Core/Generation/TemplateGenerationService.cs`, `src/BS2BG.Core/Automation/HeadlessGenerationService.cs`.

**`src/BS2BG.Core/Automation/`:**
- Purpose: Headless generation and assignment strategy replay for CLI/bundle workflows.
- Contains: `HeadlessGenerationService`, automation request/result/exit-code contracts, strategy replay contracts and service.
- Key files: `src/BS2BG.Core/Automation/HeadlessGenerationService.cs`, `src/BS2BG.Core/Automation/HeadlessGenerationContracts.cs`, `src/BS2BG.Core/Automation/AssignmentStrategyReplayService.cs`.

**`src/BS2BG.Core/Bundling/`:**
- Purpose: Create portable project bundles that include project JSON, generated output, profile data, reports, manifest, and checksums.
- Contains: Bundle service, contracts, manifest serializer, path scrubber.
- Key files: `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs`, `src/BS2BG.Core/Bundling/PortableProjectBundleContracts.cs`, `src/BS2BG.Core/Bundling/BundlePathScrubber.cs`.

**`src/BS2BG.Core/Diagnostics/`:**
- Purpose: Project validation, profile diagnostics, recovery findings, export previews, and report text formatting.
- Contains: Validation services, diagnostic records, severity enum, export preview result/service.
- Key files: `src/BS2BG.Core/Diagnostics/ProjectValidationService.cs`, `src/BS2BG.Core/Diagnostics/ProjectValidationReport.cs`, `src/BS2BG.Core/Diagnostics/DiagnosticReportTextFormatter.cs`.

**`src/BS2BG.Core/Export/`:**
- Purpose: File writers and planners for `templates.ini`, `morphs.ini`, and BoS JSON output.
- Contains: `BodyGenIniExportWriter`, `BosJsonExportWriter`, `BosJsonExportPlanner`.
- Key files: `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`.

**`src/BS2BG.Core/Formatting/`:**
- Purpose: Byte-sensitive Java-compatible slider formatting and float formatting.
- Contains: `SliderMathFormatter`, `JavaFloatFormatting`, formatting-layer preset/slider model classes.
- Key files: `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`, `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs`.

**`src/BS2BG.Core/Generation/`:**
- Purpose: Generate template/morph text and load/compose slider profile catalogs.
- Contains: `TemplateGenerationService`, `MorphGenerationService`, profile factory/catalog/service classes, profile JSON service.
- Key files: `src/BS2BG.Core/Generation/TemplateGenerationService.cs`, `src/BS2BG.Core/Generation/MorphGenerationService.cs`, `src/BS2BG.Core/Generation/TemplateProfileCatalogFactory.cs`, `src/BS2BG.Core/Generation/RequestScopedProfileCatalogComposer.cs`.

**`src/BS2BG.Core/Import/`:**
- Purpose: Parse external input files and provide import diagnostics/previews.
- Contains: BodySlide XML parser, NPC text parser, preview service, result and diagnostic records.
- Key files: `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.Core/Import/NpcTextParser.cs`, `src/BS2BG.Core/Import/NpcImportPreviewService.cs`.

**`src/BS2BG.Core/IO/`:**
- Purpose: Reusable atomic write primitive with rollback ledger.
- Contains: `AtomicFileWriter`, `AtomicWriteException`, `WriteOutcomeLedger`.
- Key files: `src/BS2BG.Core/IO/AtomicFileWriter.cs`, `src/BS2BG.Core/IO/AtomicWriteException.cs`.

**`src/BS2BG.Core/Models/`:**
- Purpose: Shared domain model graph and project aggregate.
- Contains: `ProjectModel`, `ProjectModelNode`, `SliderPreset`, `SetSlider`, `Npc`, `CustomMorphTarget`, `MorphTargetBase`, profile definition/mapping types.
- Key files: `src/BS2BG.Core/Models/ProjectModel.cs`, `src/BS2BG.Core/Models/SliderPreset.cs`, `src/BS2BG.Core/Models/Npc.cs`, `src/BS2BG.Core/Models/CustomProfileDefinition.cs`.

**`src/BS2BG.Core/Morphs/`:**
- Purpose: Manage morph target preset assignment and persisted assignment strategies.
- Contains: Assignment services, random-provider abstractions, deterministic provider, strategy contracts.
- Key files: `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`, `src/BS2BG.Core/Morphs/AssignmentStrategyService.cs`, `src/BS2BG.Core/Morphs/AssignmentStrategyContracts.cs`.

**`src/BS2BG.Core/Serialization/`:**
- Purpose: Serialize and deserialize `.jbs2bg` project JSON with diagnostics and embedded profile data.
- Contains: `ProjectFileService` and nested DTO/converter implementation.
- Key files: `src/BS2BG.Core/Serialization/ProjectFileService.cs`.

**`src/BS2BG.App/`:**
- Purpose: Avalonia desktop shell and app-specific service composition.
- Contains: Application bootstrap, DI, shell constants, ViewModels, views, services, themes.
- Key files: `src/BS2BG.App/Program.cs`, `src/BS2BG.App/AppBootstrapper.cs`, `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`.

**`src/BS2BG.App/Services/`:**
- Purpose: UI/platform adapters plus testable service interfaces for ViewModels.
- Contains: File pickers, dialogs, clipboard, image lookup/view, navigation, profile catalog/store, preferences, undo/redo, converters.
- Key files: `src/BS2BG.App/Services/WindowFileDialogService.cs`, `src/BS2BG.App/Services/IFileDialogService.cs`, `src/BS2BG.App/Services/UserPreferencesService.cs`, `src/BS2BG.App/Services/UndoRedoService.cs`, `src/BS2BG.App/Services/NavigationService.cs`.

**`src/BS2BG.App/ViewModels/`:**
- Purpose: ReactiveUI MVVM state and commands for shell, Templates, Morphs, Diagnostics, and Profiles workspaces.
- Contains: Root/workspace ViewModels, row ViewModels, observable helpers, workflow helper subdirectory.
- Key files: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs`.

**`src/BS2BG.App/ViewModels/Workflow/`:**
- Purpose: ViewModel helper records/classes tied to workflow UI state, not Core domain behavior.
- Contains: NPC row/filter state, export preview models, import preview models, file operation ledger ViewModels, undo snapshots.
- Key files: `src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs`, `src/BS2BG.App/ViewModels/Workflow/NpcFilterState.cs`, `src/BS2BG.App/ViewModels/Workflow/UndoSnapshots.cs`.

**`src/BS2BG.App/Views/`:**
- Purpose: Single-window AXAML and view-only code-behind glue.
- Contains: `MainWindow.axaml`, `MainWindow.axaml.cs`.
- Key files: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs`.

**`src/BS2BG.Cli/`:**
- Purpose: Headless command-line automation entry point.
- Contains: `System.CommandLine` command tree and Core service composition.
- Key files: `src/BS2BG.Cli/Program.cs`, `src/BS2BG.Cli/BS2BG.Cli.csproj`.

**`src/com/asdasfa/jbs2bg/`:**
- Purpose: Authoritative Java implementation used as a reference for parity work.
- Contains: JavaFX controller, settings/profile classes, data models, ControlsFX table filter, test harness.
- Key files: `src/com/asdasfa/jbs2bg/MainController.java`, `src/com/asdasfa/jbs2bg/data/Settings.java`, `src/com/asdasfa/jbs2bg/testharness/FixtureDriver.java`.

**`tests/BS2BG.Tests/`:**
- Purpose: Main xUnit v3 test project for Core/App/CLI behavior.
- Contains: Golden-file tests, ViewModel tests, parser tests, serialization tests, CLI tests, release tests, headless Avalonia tests.
- Key files: `tests/BS2BG.Tests/BS2BG.Tests.csproj`, `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, `tests/BS2BG.Tests/MainWindowViewModelTests.cs`, `tests/BS2BG.Tests/CliGenerationTests.cs`, `tests/BS2BG.Tests/TestModuleInitializer.cs`.

**`tests/fixtures/`:**
- Purpose: Fixture corpus for golden parity tests and math documentation.
- Contains: Input fixtures, expected output snapshots, fixture README, hand-traced math walkthrough.
- Key files: `tests/fixtures/README.md`, `tests/fixtures/inputs/minimal/MATH-WALKTHROUGH.md`, `tests/fixtures/expected/`.

**`openspec/`:**
- Purpose: Current and archived OpenSpec change artifacts and capability specs.
- Contains: `openspec/specs/`, `openspec/changes/`, `openspec/changes/archive/`.
- Key files: `openspec/specs/reactive-mvvm-conventions/spec.md`, `openspec/specs/template-generation-flow/spec.md`, `openspec/specs/morph-assignment-flow/spec.md`.

**`.planning/`:**
- Purpose: GSD project state and generated codebase maps consumed by future planning/execution commands.
- Contains: Project/requirements/roadmap/state docs, research, codebase maps.
- Key files: `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/STATE.md`, `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/STRUCTURE.md`.

**`.claude/skills/`:**
- Purpose: Project-specific agent skills that capture architecture constraints and workflows.
- Contains: `java-ref`, `parity-check`, and OpenSpec workflow skills.
- Key files: `.claude/skills/java-ref/SKILL.md`, `.claude/skills/parity-check/SKILL.md`, `.claude/skills/openspec-apply-change/SKILL.md`.

## Key File Locations

**Entry Points:**
- `src/BS2BG.App/Program.cs`: Desktop app process entry point and Avalonia/ReactiveUI setup.
- `src/BS2BG.App/App.axaml.cs`: Resolves `MainWindow` during Avalonia framework initialization.
- `src/BS2BG.App/Views/MainWindow.axaml`: Main shell UI, menus, tabs, command palette, forms, bindings.
- `src/BS2BG.Cli/Program.cs`: CLI command entry point for `generate` and `bundle`.
- `tests/BS2BG.Tests/TestModuleInitializer.cs`: ReactiveUI test bootstrap and scheduler pinning.

**Configuration:**
- `BS2BG.sln`: Solution containing `BS2BG.Core`, `BS2BG.App`, `BS2BG.Cli`, and `BS2BG.Tests`.
- `Directory.Build.props`: Shared nullable, implicit using, analyzer settings.
- `Directory.Packages.props`: Central NuGet package versions.
- `src/BS2BG.Core/BS2BG.Core.csproj`: Core target framework and package references.
- `src/BS2BG.App/BS2BG.App.csproj`: Avalonia app target framework, compiled binding setting, and bundled profile JSON links.
- `src/BS2BG.Cli/BS2BG.Cli.csproj`: CLI target framework, single-file publish flags, and bundled profile JSON links.
- `tests/BS2BG.Tests/BS2BG.Tests.csproj`: xUnit/Avalonia headless test configuration and expected fixture copy rule.
- `settings.json`, `settings_UUNP.json`, `settings_FO4_CBBE.json`: Bundled profile data consumed by profile factories.
- `AGENTS.md`: Project instructions, sacred files, build/test commands, and architecture notes.
- `PRD.md`: Product requirements, parity checklist, risks, and milestone history.

**Core Logic:**
- `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`: Java-parity slider math and line formatting.
- `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs`: Java-like and minimal-json-like float formatting.
- `src/BS2BG.Core/Generation/TemplateGenerationService.cs`: Template/BoS preview and full template generation service.
- `src/BS2BG.Core/Generation/MorphGenerationService.cs`: Morph text generation service.
- `src/BS2BG.Core/Generation/TemplateProfileCatalogFactory.cs`: Bundled profile catalog factory.
- `src/BS2BG.Core/Generation/RequestScopedProfileCatalogComposer.cs`: Adds project/local custom profiles for automation and bundles.
- `src/BS2BG.Core/Import/BodySlideXmlParser.cs`: BodySlide XML parser.
- `src/BS2BG.Core/Import/NpcTextParser.cs`: NPC text parser.
- `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`: Morph target and NPC assignment mutations.
- `src/BS2BG.Core/Morphs/AssignmentStrategyService.cs`: Strategy-based assignment implementation.
- `src/BS2BG.Core/Serialization/ProjectFileService.cs`: Project load/save implementation.
- `src/BS2BG.Core/Diagnostics/ProjectValidationService.cs`: Export/project validation rules.
- `src/BS2BG.Core/Automation/HeadlessGenerationService.cs`: Core automation generation flow.
- `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs`: Portable bundle planning and zip creation.

**UI Logic:**
- `src/BS2BG.App/AppBootstrapper.cs`: DI composition root.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`: Shell commands, export, project file, bundle, and navigation orchestration.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`: Templates workspace state and commands.
- `src/BS2BG.App/ViewModels/MorphsViewModel.cs`: Morphs/NPC workspace state, filters, and commands.
- `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs`: Diagnostics workspace state.
- `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs`: Profile management state.
- `src/BS2BG.App/Services/*.cs`: Platform adapters, preferences, undo/redo, catalog services, converters.

**Testing:**
- `tests/BS2BG.Tests/SliderMathFormatterTests.cs`: Slider math and formatting parity tests.
- `tests/BS2BG.Tests/ExportWriterTests.cs`: INI/JSON export writer behavior.
- `tests/BS2BG.Tests/BodySlideXmlParserTests.cs`: BodySlide XML import behavior.
- `tests/BS2BG.Tests/ProjectFileServiceTests.cs`: Project round-trip serialization.
- `tests/BS2BG.Tests/MainWindowViewModelTests.cs`: Shell workflow behavior.
- `tests/BS2BG.Tests/TemplatesViewModelTests.cs`: Templates workspace behavior.
- `tests/BS2BG.Tests/MorphsViewModelTests.cs`: Morphs workspace behavior.
- `tests/BS2BG.Tests/CliGenerationTests.cs`: CLI generation path behavior.
- `tests/BS2BG.Tests/PortableBundleServiceTests.cs`: Portable bundle service behavior.
- `tests/BS2BG.Tests/MainWindowHeadlessTests.cs`: Avalonia headless UI behavior.

**Reference / Specs:**
- `src/com/asdasfa/jbs2bg/MainController.java`: Java reference for slider math, exports, and controller behavior.
- `src/com/asdasfa/jbs2bg/data/Settings.java`: Java reference for settings/profile defaults.
- `src/com/asdasfa/jbs2bg/data/NPC.java`: Java reference for NPC model/import format.
- `tests/fixtures/inputs/minimal/MATH-WALKTHROUGH.md`: Hand-traced expected values for slider math.
- `tests/fixtures/README.md`: Fixture layout and regeneration workflow.
- `openspec/specs/reactive-mvvm-conventions/spec.md`: Current ReactiveUI/MVVM convention contract.

## Naming Conventions

**Files:**
- C# types use PascalCase file names matching the primary type: `src/BS2BG.Core/Models/ProjectModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`.
- Interfaces use `I` prefix and live next to related App service implementations: `src/BS2BG.App/Services/IFileDialogService.cs`, `src/BS2BG.App/Services/IClipboardService.cs`.
- Result/diagnostic/contract records use suffixes such as `Result`, `Diagnostic`, `Contracts`, `Report`, `Preview`: `src/BS2BG.Core/Import/NpcImportResult.cs`, `src/BS2BG.Core/Diagnostics/ProjectValidationReport.cs`.
- ViewModels use `ViewModel` suffix; row/helper ViewModels use descriptive prefixes: `src/BS2BG.App/ViewModels/SetSliderInspectorRowViewModel.cs`, `src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs`.
- Tests use `*Tests.cs` suffix and usually mirror the subject type name: `tests/BS2BG.Tests/TemplateGenerationServiceTests.cs`, `tests/BS2BG.Tests/ProjectValidationServiceTests.cs`.
- Avalonia views use `.axaml` plus `.axaml.cs` code-behind: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs`.

**Directories:**
- Project directories follow solution project names: `src/BS2BG.Core/`, `src/BS2BG.App/`, `src/BS2BG.Cli/`, `tests/BS2BG.Tests/`.
- Core subdirectories are capability/layer names in PascalCase: `Import/`, `Generation/`, `Serialization/`, `Diagnostics/`, `Bundling/`.
- App subdirectories follow MVVM roles: `Views/`, `ViewModels/`, `Services/`, `Themes/`.
- Planning/spec directories are lowercase workflow names: `openspec/specs/template-generation-flow/`, `.planning/codebase/`.

## Where to Add New Code

**New Core parser or importer:**
- Primary code: `src/BS2BG.Core/Import/`
- Models/results: `src/BS2BG.Core/Import/` for importer-specific records, or `src/BS2BG.Core/Models/` for reusable domain types.
- Tests: `tests/BS2BG.Tests/*ParserTests.cs` or `tests/BS2BG.Tests/*Import*Tests.cs`.

**New template/profile/generation behavior:**
- Primary code: `src/BS2BG.Core/Generation/` and, for byte-sensitive formatting, `src/BS2BG.Core/Formatting/`.
- Tests: `tests/BS2BG.Tests/TemplateGenerationServiceTests.cs`, `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, or a focused new `*Tests.cs` file.
- Reference first: `src/com/asdasfa/jbs2bg/MainController.java`, `src/com/asdasfa/jbs2bg/data/Settings.java`, and the `java-ref` skill.

**New morph assignment behavior:**
- Primary code: `src/BS2BG.Core/Morphs/`.
- App UI integration: `src/BS2BG.App/ViewModels/MorphsViewModel.cs` and relevant view bindings in `src/BS2BG.App/Views/MainWindow.axaml`.
- Tests: `tests/BS2BG.Tests/MorphCoreTests.cs`, `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`.

**New project serialization field:**
- Primary code: `src/BS2BG.Core/Models/ProjectModel.cs` or a model file under `src/BS2BG.Core/Models/`, plus DTO/converter logic in `src/BS2BG.Core/Serialization/ProjectFileService.cs`.
- Tests: `tests/BS2BG.Tests/ProjectFileServiceTests.cs` and, for profile fields, `tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs`.
- Use `ProjectModel.ReplaceWith` for open/replace flows instead of swapping the singleton project instance.

**New App workspace or panel:**
- ViewModel: `src/BS2BG.App/ViewModels/`.
- View markup: add a tab/section to `src/BS2BG.App/Views/MainWindow.axaml` unless the UI is split into new views intentionally.
- Services: `src/BS2BG.App/Services/` for platform adapters or App-only abstractions.
- DI: register dependencies in `src/BS2BG.App/AppBootstrapper.cs`.
- Tests: `tests/BS2BG.Tests/*ViewModelTests.cs` and `tests/BS2BG.Tests/MainWindowHeadlessTests.cs` for binding/shell coverage.

**New platform service:**
- Interface and implementation: `src/BS2BG.App/Services/`.
- Null/empty test implementation: same file or adjacent file in `src/BS2BG.App/Services/` when it is part of app test seams.
- Registration: `src/BS2BG.App/AppBootstrapper.cs`.
- Tests: `tests/BS2BG.Tests/*ServiceTests.cs`.

**New CLI command:**
- Command parsing and result printing: `src/BS2BG.Cli/Program.cs`.
- Domain behavior: add a Core service under `src/BS2BG.Core/Automation/`, `src/BS2BG.Core/Bundling/`, or another appropriate Core capability directory.
- Tests: `tests/BS2BG.Tests/CliGenerationTests.cs` or a new CLI-focused test file.
- Do not reference `src/BS2BG.App/` from CLI.

**New diagnostics or validation rule:**
- Primary code: `src/BS2BG.Core/Diagnostics/ProjectValidationService.cs` or a new service in `src/BS2BG.Core/Diagnostics/`.
- UI integration: `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs` and diagnostics section in `src/BS2BG.App/Views/MainWindow.axaml`.
- Tests: `tests/BS2BG.Tests/ProjectValidationServiceTests.cs`, `tests/BS2BG.Tests/DiagnosticsViewModelTests.cs`.

**New portable bundle content:**
- Primary code: `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` and contracts in `src/BS2BG.Core/Bundling/PortableProjectBundleContracts.cs`.
- Tests: `tests/BS2BG.Tests/PortableBundleServiceTests.cs`.
- Scrub privacy-sensitive paths with `src/BS2BG.Core/Bundling/BundlePathScrubber.cs` when report/manifest text may include local paths.

**New release tooling:**
- Scripts: `tools/release/`.
- Docs: `docs/release/`.
- Tests: `tests/BS2BG.Tests/ReleasePackagingScriptTests.cs`, `tests/BS2BG.Tests/ReleaseDocsTests.cs`, `tests/BS2BG.Tests/ReleaseTrustTests.cs`.

**New project planning or spec artifact:**
- GSD planning docs: `.planning/`.
- OpenSpec capability specs and changes: `openspec/specs/`, `openspec/changes/`.
- Use OpenSpec skills for non-trivial feature/change workflow rather than ad hoc spec files.

## Special Directories

**`src/com/asdasfa/jbs2bg/`:**
- Purpose: Java jBS2BG reference for porting and parity checks.
- Generated: No.
- Committed: Yes.
- Guidance: Read this before porting behavior; use `.claude/skills/java-ref/SKILL.md` to map topics to files.

**`src/jfx-8u60-b08/`:**
- Purpose: Embedded OpenJFX 8 source snapshot from the legacy Java project.
- Generated: No.
- Committed: Yes.
- Guidance: Ignore unless explicitly working on Java reference internals.

**`tests/fixtures/expected/`:**
- Purpose: Sacred golden-file expected output corpus.
- Generated: Yes, only via Java reference tooling.
- Committed: Yes.
- Guidance: Do not edit to silence test failures; regenerate only deliberately through `tests/tools/generate-expected.ps1`.

**`tests/fixtures/inputs/`:**
- Purpose: Input data for parser/generation/golden tests.
- Generated: No.
- Committed: Yes.
- Guidance: Use `tests/fixtures/inputs/minimal/MATH-WALKTHROUGH.md` as source of truth for slider math expectations.

**`openspec/changes/archive/`:**
- Purpose: Completed OpenSpec proposals and implementation rationale.
- Generated: Workflow-managed.
- Committed: Yes.
- Guidance: Read archived changes for historical decisions instead of re-litigating existing architecture.

**`openspec/specs/`:**
- Purpose: Current capability specs that describe expected behavior.
- Generated: Workflow-managed.
- Committed: Yes.
- Guidance: Update relevant specs through OpenSpec workflow when adding capability behavior.

**`.planning/codebase/`:**
- Purpose: Generated codebase map consumed by GSD planning/execution.
- Generated: Yes.
- Committed: Yes.
- Guidance: Mapper agents write files here; implementation agents should read relevant maps before planning work.

**`.claude/skills/`:**
- Purpose: Project-local agent skills.
- Generated: No/workflow-managed.
- Committed: Yes.
- Guidance: `java-ref` maps porting topics; `parity-check` describes golden test validation.

**`tools/release/`:**
- Purpose: Release packaging scripts.
- Generated: No.
- Committed: Yes.
- Guidance: Keep script behavior covered by release packaging tests under `tests/BS2BG.Tests/`.

**`docs/release/`:**
- Purpose: Release process and user documentation.
- Generated: No.
- Committed: Yes.
- Guidance: Keep docs synchronized with release tests.

**`artifacts/`:**
- Purpose: Build, release, and test output artifacts.
- Generated: Yes.
- Committed: Generally no for generated outputs.
- Guidance: Do not place source files here.

**`bin/` and `obj/` under project directories:**
- Purpose: .NET build outputs and intermediates.
- Generated: Yes.
- Committed: No.
- Guidance: Ignore for architecture and do not add source code here.

**Root `assets/`, `bin/`, `build.fxbuild`, `.classpath`, `.project`, `.settings/`:**
- Purpose: Legacy Java/JavaFX project leftovers.
- Generated: Mixed legacy artifacts.
- Committed: Yes.
- Guidance: Ignore unless explicitly working with the Java reference or legacy packaging.

---

*Structure analysis: 2026-04-28*
