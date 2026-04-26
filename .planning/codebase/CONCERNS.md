# Codebase Concerns

**Analysis Date:** 2026-04-26

## Tech Debt

**Large ViewModels concentrate UI workflow logic:**
- Issue: `MorphsViewModel` and `TemplatesViewModel` own command creation, filtering, import orchestration, undo snapshots, validation, status text, and selection synchronization in single classes.
- Files: `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`
- Impact: Small feature changes risk regressions across selection state, generated text, undo/redo, filters, and dirty-state behavior because unrelated concerns share the same mutable fields and reactive subscriptions.
- Fix approach: Extract focused services/helpers for NPC filtering, assignment undo snapshots, command registration, import status formatting, and generated-output orchestration. Keep ViewModels as binding surfaces that compose those units.

**Main window AXAML is a monolithic view:**
- Issue: The entire Templates workspace, Morphs workspace, command palette, NPC filter popup, SetSlider inspector, and BoS JSON viewer live in one AXAML file.
- Files: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs`
- Impact: Layout changes are hard to review, compiled-binding errors point into a large file, and reuse of subviews is blocked.
- Fix approach: Split `MainWindow.axaml` into user controls such as `TemplatesView.axaml`, `MorphsView.axaml`, `SetSliderInspectorView.axaml`, `CommandPaletteView.axaml`, and `NpcFilterView.axaml`. Keep `MainWindow` responsible only for shell-level layout and window events.

**Reactive background-work convention is not consistently implemented:**
- Issue: App-layer ViewModels use `Task.Run` directly for project load/save/export and XML/NPC imports instead of running work through ReactiveUI scheduler patterns described in the OpenSpec convention.
- Files: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `openspec/specs/reactive-mvvm-conventions/spec.md`
- Impact: Tests pin ReactiveUI schedulers, but `Task.Run` bypasses that contract; cancellation and scheduler behavior can differ between tests and the app, especially around command execution and state updates.
- Fix approach: Convert long-running command bodies to `ReactiveCommand.CreateFromObservable` or scheduler-aware abstractions that use `RxApp.TaskpoolScheduler` and marshal results through `RxApp.MainThreadScheduler`.

**Code-behind contains view-specific state synchronization:**
- Issue: `MainWindow.axaml.cs` manually mirrors tab selection, NPC multi-selection, race-filter selection, drag/drop, command-palette execution deferral, title updates, and focus handling.
- Files: `src/BS2BG.App/Views/MainWindow.axaml.cs`, `src/BS2BG.App/Views/MainWindow.axaml`
- Impact: Business logic is mostly in ViewModels, but selection/filter bridge logic remains event-driven and is easy to break when the AXAML is reorganized or controls are renamed.
- Fix approach: Move reusable behaviors into attached behaviors or small view services, bind selected workspace directly where Avalonia supports it, and isolate unavoidable control event glue in dedicated user controls.

**Profile loading still has legacy two-file assumptions:**
- Issue: `TemplateProfileCatalogFactory` creates the Fallout 4 CBBE profile by loading `settings.json`, the same file used for Skyrim CBBE.
- Files: `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs`, `src/BS2BG.App/BS2BG.App.csproj`, `settings.json`, `settings_UUNP.json`, `PRD.md`
- Impact: Fallout 4 profile output is defaults-only/experimental and can generate incorrect slider math for FO4 BodySlide XMLs because FO4 slider names do not match Skyrim CBBE slider names.
- Fix approach: Add a distinct `fallout4-cbbe.json` profile, ship it as content, and load it in `TemplateProfileCatalogFactory` instead of reusing `settings.json`.

**User preferences are incomplete relative to the app workflows:**
- Issue: The preferences service persists only `Theme`, while workflows use last-used project/preset/NPC/export folders and `OmitRedundantSliders` as user-facing preferences.
- Files: `src/BS2BG.App/Services/UserPreferencesService.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `PRD.md`
- Impact: Users lose folder context and omit-redundant state between sessions, increasing repetitive file-dialog navigation and risking unexpected generated-template differences after restart.
- Fix approach: Extend `UserPreferences` with last-used folder fields and `OmitRedundantSliders`; update file dialog services and `TemplatesViewModel` to load/save those values.

**NPC column filtering is implemented only for Race:**
- Issue: `NpcFilterColumn` defines multiple columns, but the UI exposes only the race popup/filter path.
- Files: `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/Views/MainWindow.axaml`, `PRD.md`
- Impact: The ControlsFX-style per-column filtering behavior is incomplete; users cannot filter by name, mod, editor ID, form ID, or assigned presets through column dropdowns.
- Fix approach: Generalize the race filter popup into a reusable column filter component and bind one instance per DataGrid column.

**Generated artifacts and legacy source increase repository noise:**
- Issue: The repository contains Java reference source, OpenJFX source snapshot, old Java assets, release artifacts, and C# source under the same root.
- Files: `src/com/asdasfa/jbs2bg/`, `src/jfx-8u60-b08/`, `assets/`, `bin/`, `artifacts/`, `src/BS2BG.Core/`, `src/BS2BG.App/`
- Impact: Broad searches and metrics include irrelevant legacy/generated code unless every command excludes those directories; contributors can accidentally edit reference or artifact files instead of the C# implementation.
- Fix approach: Keep explicit ignore/search guidance in contributor docs and prefer scoped tooling commands that target `src/BS2BG.*` and `tests/BS2BG.Tests` by default.

## Known Bugs

**Fallout 4 CBBE profile uses Skyrim CBBE settings:**
- Symptoms: FO4 BodySlide presets can produce missing/default-only or incorrect BodyGen/BoS values because the active `Fallout4Cbbe` profile loads the Skyrim CBBE defaults/multipliers/inverted list.
- Files: `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs`, `tests/fixtures/inputs/fallout4-cbbe/`, `PRD.md`
- Trigger: Import FO4 CBBE XML, select the Fallout 4 CBBE profile, then generate templates or BoS JSON.
- Workaround: Not detected in app code; users need a corrected profile implementation.

**NPC text charset fallback is heuristic rather than detected:**
- Symptoms: Non-UTF-8 NPC dumps without a BOM decode as the Windows ANSI code page or Windows-1252 fallback; other encodings can import with mojibake in names/races/editor IDs.
- Files: `src/BS2BG.Core/Import/NpcTextParser.cs`, `PRD.md`
- Trigger: Import an NPC text file encoded in a non-UTF-8, non-current-code-page encoding without a BOM.
- Workaround: Re-save the NPC dump as UTF-8 with or without BOM before importing.

**Save As reuses the current path as the dialog seed but always writes to the returned path:**
- Symptoms: The implementation is safe when the dialog returns a path, but Save As behavior depends entirely on `IFileDialogService.PickSaveProjectFileAsync` honoring the seed path and allowing a new target.
- Files: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/Services/IFileDialogService.cs`, `src/BS2BG.App/Services/WindowFileDialogService.cs`
- Trigger: Invoke Save As when a current project path is already set.
- Workaround: Choose an explicit new path in the save dialog.

## Security Considerations

**Project and import files are fully trusted local inputs:**
- Risk: Very large or malformed `.jbs2bg`, BodySlide XML, profile JSON, or NPC text files can consume memory/CPU because loaders read whole files and materialize full object graphs.
- Files: `src/BS2BG.Core/Serialization/ProjectFileService.cs`, `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.Core/Import/NpcTextParser.cs`, `src/BS2BG.Core/Generation/SliderProfileJsonService.cs`
- Current mitigation: Parse failures are caught for XML/NPC imports in `BodySlideXmlParser.ParseFile` and `NpcTextParser.ParseFile`; project open failures are reported by `MainWindowViewModel.TryOpenProjectPathAsync`.
- Recommendations: Add file-size limits or streaming parsers for user-selected files, surface clear validation errors, and include stress tests for large NPC/project/XML fixtures.

**Unsigned release packaging:**
- Risk: Release ZIPs include hashes but no Authenticode/code-signing step, so users cannot verify publisher identity through Windows trust UI.
- Files: `tools/release/package-release.ps1`, `docs/release/UNSIGNED-BUILD.md`
- Current mitigation: The packaging script writes `SHA256SUMS.txt` and a ZIP `.sha256` file.
- Recommendations: Add signing as a release step when a certificate is available and document verification instructions next to the generated checksums.

**Image lookup path traversal is guarded and should remain guarded:**
- Risk: NPC names/editor IDs come from imported text and are used to search for image files.
- Files: `src/BS2BG.App/Services/NpcImageLookupService.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`
- Current mitigation: `NpcImageLookupService` rejects rooted paths and path separators in candidate components and verifies candidates stay under the `images` directory.
- Recommendations: Preserve the containment checks when adding new image-file naming rules; add tests for rooted paths, `..`, separators, and drive-qualified names.

**No secret files detected in repository scan:**
- Risk: Not detected.
- Files: `.env`, `.env.*`, `*secret*`, `*credential*`
- Current mitigation: No matching secret/config credential files were found during this audit.
- Recommendations: Keep secret files ignored and never commit modder-specific paths or credentials.

## Performance Bottlenecks

**NPC filtering rebuilds observable collections on every relevant change:**
- Problem: Search/filter refresh clears and repopulates `VisibleNpcs` and `VisibleNpcDatabase`, and NPC property changes can trigger full visible-list rebuilds.
- Files: `src/BS2BG.App/ViewModels/MorphsViewModel.cs`
- Cause: `RefreshFilteredCollection` performs full collection scans and full target collection resets rather than incremental filtering or view-level collection views.
- Improvement path: Introduce an incremental filtered collection/view abstraction, debounce search text if needed, and benchmark large NPC imports representative of xEdit dumps.

**SetSlider inspector creates one row ViewModel per materialized slider:**
- Problem: Selecting presets with many sliders materializes `SetSliderInspectorRowViewModel` instances and binds all rows inside an `ItemsControl` in a `ScrollViewer`.
- Files: `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/SetSliderInspectorRowViewModel.cs`, `src/BS2BG.App/Views/MainWindow.axaml`
- Cause: The SetSlider inspector uses an `ItemsControl` rather than a virtualizing list/grid.
- Improvement path: Use a virtualizing control for SetSlider rows and measure selection latency for large presets/profiles.

**Import parsers load entire files into memory:**
- Problem: NPC files use `File.ReadAllBytes`, project files use `File.ReadAllText`, profile JSON uses `File.ReadAllText`, and XML uses `XDocument.Load`.
- Files: `src/BS2BG.Core/Import/NpcTextParser.cs`, `src/BS2BG.Core/Serialization/ProjectFileService.cs`, `src/BS2BG.Core/Generation/SliderProfileJsonService.cs`, `src/BS2BG.Core/Import/BodySlideXmlParser.cs`
- Cause: Current parsers favor simple whole-file processing over streaming.
- Improvement path: Keep whole-file parsing for normal modding-sized inputs, but add guardrails and tests for large files; stream NPC text line-by-line after charset detection if large imports become common.

**BoS export builds all JSON outputs before committing the batch:**
- Problem: `BosJsonExportWriter` accumulates every output path/content pair before writing.
- Files: `src/BS2BG.Core/Export/BosJsonExportWriter.cs`, `src/BS2BG.Core/IO/AtomicFileWriter.cs`
- Cause: Atomic batch writing requires all content to be available up front.
- Improvement path: Keep batch semantics for small/medium exports; add memory benchmarks and consider chunked generation with explicit recovery behavior for very large preset sets.

## Fragile Areas

**Byte-identical formatter/export path:**
- Files: `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs`, `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`, `tests/fixtures/expected/`
- Why fragile: Line endings, rounding, float formatting, missing-default injection, profile selection, and JSON newline behavior are load-bearing compatibility details.
- Safe modification: Change only with golden-file tests and the hand-traced math fixture; do not update `tests/fixtures/expected/**` to hide failures.
- Test coverage: Strong golden coverage exists, but any new profile or formatter behavior needs explicit fixture coverage.

**Undo/redo snapshots use live object references:**
- Files: `src/BS2BG.App/Services/UndoRedoService.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`
- Why fragile: Undo records often capture mutable `SliderPreset`, `Npc`, and `CustomMorphTarget` instances; later mutations can change what an older undo/redo operation restores.
- Safe modification: Snapshot value state for operations that can be followed by edits to the same objects, or document intentional live-reference semantics per command.
- Test coverage: ViewModel tests cover several undo/redo flows, but broad interleavings of rename, assignment, removal, import, and clear operations remain risky.

**Atomic batch export rollback spans multiple files but not a real transaction:**
- Files: `src/BS2BG.Core/IO/AtomicFileWriter.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`
- Why fragile: `WriteAtomicBatch` writes temp files, then commits files sequentially and attempts rollback on failure; external locks, permissions, antivirus, or cross-volume edge cases can leave partial state.
- Safe modification: Preserve backup/rollback tests and add failure-injection tests before changing the writer.
- Test coverage: Existing project save/export tests cover success and some failure paths, but rollback failure and locked-file scenarios are not comprehensively covered.

**Profile fallback silently defaults unknown profile names:**
- Files: `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs`, `src/BS2BG.Core/Serialization/ProjectFileService.cs`, `src/BS2BG.Core/Models/ProjectProfileMapping.cs`
- Why fragile: `GetProfile` returns `DefaultProfile` for missing/unknown names; a mistyped or removed profile can generate valid-looking output with the wrong math.
- Safe modification: Surface warnings for unknown profile names during project load/generation while keeping compatibility fallback behavior.
- Test coverage: Project load tests cover profile mapping, but not user-visible warnings for unknown profile names.

**MainWindow service attachment depends on constructor wiring:**
- Files: `src/BS2BG.App/AppBootstrapper.cs`, `src/BS2BG.App/Views/MainWindow.axaml.cs`, `src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs`, `src/BS2BG.App/Services/WindowNpcTextFilePicker.cs`, `src/BS2BG.App/Services/WindowClipboardService.cs`
- Why fragile: Window-scoped services are singletons that need `Attach(this)` from `MainWindow`; missing a constructor parameter or attach call can leave a service without an owner window.
- Safe modification: Centralize window service attachment behind one interface or registration list and test that every window-scoped service is attached by the shell.
- Test coverage: AppShell tests cover some shell wiring, but service attachment completeness is not enforced by a single contract test.

## Scaling Limits

**NPC list and filters:**
- Current capacity: Uses in-memory `ObservableCollection<Npc>` and full scans for filtering.
- Limit: Large xEdit dumps can cause visible UI churn during import, search, filter changes, and assignment updates.
- Scaling path: Add benchmarks with thousands of NPCs, virtualize the DataGrid/list controls, and move filtering to an incremental view abstraction.

**Preset and slider inspector:**
- Current capacity: Uses in-memory preset collections and creates inspector rows for all sliders on selection.
- Limit: Large preset corpora and profiles with many defaults increase selection and refresh cost.
- Scaling path: Virtualize inspector rows and avoid regenerating preview/BoS text until required or after debounced edits.

**Undo/redo history:**
- Current capacity: Undo history stores closures and snapshots without an explicit size limit.
- Limit: Long editing sessions with bulk NPC/preset operations can retain large object graphs and increase memory use.
- Scaling path: Add bounded history or memory-aware pruning in `UndoRedoService`, with status text when old entries are discarded.

## Dependencies at Risk

**Avalonia 12 / ReactiveUI 23 integration:**
- Risk: The project relies on Avalonia compiled bindings, ReactiveUI source generators, explicit test scheduler initialization, and plain `Window` inheritance. Minor package changes can affect binding generation or command scheduling.
- Impact: App startup, ViewModel tests, command `CanExecute`, and generated properties can break together.
- Migration plan: Upgrade Avalonia/ReactiveUI in a dedicated change with full UI/headless tests; keep `openspec/specs/reactive-mvvm-conventions/spec.md` synchronized with actual patterns.

**.NET 10 target:**
- Risk: App/tests target `net10.0`, while Core targets `netstandard2.1`; development requires current SDK availability.
- Impact: Contributors without the matching SDK cannot build or run tests.
- Migration plan: Keep SDK requirements explicit in setup docs and CI; evaluate LTS target changes only as a planned migration.

**Java 8 fixture regeneration toolchain:**
- Risk: Golden expected regeneration depends on JDK 8 with JavaFX plus legacy JARs.
- Impact: Rebuilding expected fixtures is operationally fragile and can block formatter/export parity investigations.
- Migration plan: Preserve `tests/tools/generate-expected.ps1`, archive required JAR/version provenance, and avoid expected-file regeneration unless the Java reference run is reproducible.

## Missing Critical Features

**Complete profile system:**
- Problem: The profile model exposes named profiles, but the shipped files and factory still rely on legacy `settings.json` and `settings_UUNP.json`; FO4 uses the Skyrim CBBE file.
- Blocks: Correct Fallout 4 CBBE output and user-added custom profiles.

**Full per-column NPC filtering:**
- Problem: The UI provides only a race filter despite multi-column filter model support.
- Blocks: Parity with the planned ControlsFX-style table filtering workflow for mod/name/editor/form/preset columns.

**Preference persistence for workflow state:**
- Problem: Last-used folders and `OmitRedundantSliders` are not persisted in `UserPreferences`.
- Blocks: Smooth repeated import/export workflows and consistent generation options across app sessions.

**Code signing in release pipeline:**
- Problem: Packaging emits ZIP/checksum artifacts but not signed executables.
- Blocks: Strong publisher verification for distributed Windows binaries.

## Test Coverage Gaps

**Large-input/stress behavior:**
- What's not tested: Importing very large NPC text files, very large `.jbs2bg` projects, large BodySlide XML sets, and large BoS export batches.
- Files: `src/BS2BG.Core/Import/NpcTextParser.cs`, `src/BS2BG.Core/Serialization/ProjectFileService.cs`, `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`
- Risk: Memory spikes, slow UI refresh, and cancellation delays can ship unnoticed.
- Priority: High

**Atomic rollback failure injection:**
- What's not tested: Partial commit failures, rollback failures, locked target files, backup restore failures, and antivirus-style interference.
- Files: `src/BS2BG.Core/IO/AtomicFileWriter.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`
- Risk: Export/save operations can leave confusing partial output in rare filesystem states.
- Priority: Medium

**Profile warnings and FO4 correctness:**
- What's not tested: Unknown profile warning behavior and a distinct FO4 CBBE settings profile.
- Files: `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs`, `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs`, `tests/fixtures/inputs/fallout4-cbbe/`
- Risk: Users generate wrong output with valid-looking status messages.
- Priority: High

**Window service attachment completeness:**
- What's not tested: Every `Window*` service registered in DI is attached to the shell window before use.
- Files: `src/BS2BG.App/AppBootstrapper.cs`, `src/BS2BG.App/Views/MainWindow.axaml.cs`, `src/BS2BG.App/Services/`
- Risk: A new window-scoped service can be registered and injected but fail at runtime because it has no owner window.
- Priority: Medium

**Full UI filter interactions:**
- What's not tested: User-level interactions for each NPC table filter dropdown, search text, checklist selection, clear action, and filtered bulk operations through the actual Avalonia controls.
- Files: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`
- Risk: ViewModel filtering can pass while AXAML event wiring or DataGrid selection behavior fails.
- Priority: Medium

---

*Concerns audit: 2026-04-26*
