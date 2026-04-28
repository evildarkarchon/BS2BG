# Codebase Concerns

**Analysis Date:** 2026-04-28

## Tech Debt

**Avalonia shell and workflow ViewModels are large orchestration units:**
- Issue: Primary UI workflow state, command wiring, filtering, validation, undo/redo, and status text are concentrated in a small number of files. `src/BS2BG.App/ViewModels/MorphsViewModel.cs` is about 2,495 lines, `src/BS2BG.App/Views/MainWindow.axaml` is about 1,930 lines, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` is about 1,498 lines, and `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` is about 958 lines.
- Files: `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`
- Impact: Changes to NPC assignment, profile recovery, bundle creation, command palette behavior, or visual layout have broad blast radius and require larger regression passes.
- Fix approach: Extract narrowly owned coordinator/services for NPC filtering, assignment strategy editing, project-open recovery, and bundle command state while keeping public ViewModel contracts stable for compiled AXAML bindings.

**Design-time fallback constructors duplicate production composition:**
- Issue: `MainWindowViewModel` constructs a fallback `PortableProjectBundleService` and replay stack directly when DI does not provide one. This duplicates production wiring from `src/BS2BG.App/AppBootstrapper.cs` and increases constructor churn when Core service dependencies change.
- Files: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/AppBootstrapper.cs`, `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs`
- Impact: New bundle/replay dependencies must be updated in App DI, ViewModel fallback construction, CLI wiring, and tests, creating repeated integration-risk hotspots.
- Fix approach: Move fallback service construction behind an App-layer factory or test/design-time composition helper; keep ViewModels dependent on stable interfaces or prebuilt services.

**Manual column-filter UI is custom and tightly coupled:**
- Issue: The current Avalonia `DataGrid` filtering implementation uses hand-built filter popups, list selections, View code-behind clear handlers, and `MorphsViewModel` predicate state rather than a reusable filter behavior.
- Files: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/ViewModels/Workflow/NpcFilterState.cs`
- Impact: Adding new filterable tables or changing filter semantics requires coordinated AXAML, code-behind, ViewModel, and tests. The deferred Avalonia filtering concern remains explicit in `.planning/STATE.md`.
- Fix approach: Extract a reusable App-layer filter model/control contract for checklist/search filters and keep table-specific column definitions declarative.

**Local profile schema is strict version 1 only:**
- Issue: `ProfileDefinitionService` accepts only schema version `1`, rejects unknown root properties, and has no migration/compatibility abstraction for future custom profile fields.
- Files: `src/BS2BG.Core/Generation/ProfileDefinitionService.cs`, `src/BS2BG.App/Services/UserProfileStore.cs`, `.planning/STATE.md`
- Impact: Future profile metadata or calibration fields require a schema change that can break imports unless version negotiation and migrations are designed first.
- Fix approach: Add explicit versioned DTO/migration tests before expanding custom profile JSON; keep version 1 export deterministic for current sharing compatibility.

## Known Bugs

**No confirmed production bugs detected during this scan:**
- Symptoms: Not detected in source comments or planning state.
- Files: `src/BS2BG.Core/`, `src/BS2BG.App/`, `src/BS2BG.Cli/`, `tests/BS2BG.Tests/`
- Trigger: Not applicable.
- Workaround: Continue using golden-file and ViewModel regression tests before changes to parity-sensitive paths.

**Manual UAT coverage is acknowledged as incomplete for selected UI scenarios:**
- Symptoms: Planning state carries waived/partial manual UAT for fallback-panel visual placement/readability and filter/scope, large dataset, and restart persistence scenarios.
- Files: `.planning/STATE.md`, `.planning/PROJECT.md`, `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/Services/UserPreferencesService.cs`
- Trigger: Theme/DPI changes, filter behavior changes, persistence changes, or large NPC/preset datasets.
- Workaround: Run the deferred human UAT scenarios when touching UI layout, filtering/scope behavior, or preference persistence.

## Security Considerations

**Unsigned Windows release remains an accepted trust risk:**
- Risk: Windows SmartScreen and user trust friction remain for portable packages when no signing certificate is configured.
- Files: `docs/release/UNSIGNED-BUILD.md`, `docs/release/RELEASE-NOTES-v1.0.0.md`, `tools/release/package-release.ps1`
- Current mitigation: Release docs describe unsigned-build expectations and verification with SHA-256 sidecars.
- Recommendations: Add code signing when a certificate is available; keep `SIGNING-INFO.txt`, checksums, and release docs generated by packaging tests.

**Bundle privacy checks are heuristic text scanning:**
- Risk: Private path leakage detection checks manifest/report/replay text for path-like content, but generated report formats and future artifacts can bypass the heuristic if new fields are not included.
- Files: `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs`, `tests/BS2BG.Tests/PortableBundleServiceTests.cs`
- Current mitigation: `BundlePathScrubber` is applied to validation and replay reports, and tests assert common private-root redaction.
- Recommendations: Route every new bundle report/artifact through the same scrubber and add regression tests for each new field that can contain source, destination, or import paths.

**Profile file deletion trusts catalog source metadata:**
- Risk: `UserProfileStore.DeleteProfile` deletes `profile.FilePath` for selected local custom profile entries. The UI restricts deletion to `ProfileSourceKind.LocalCustom`, but the store itself does not verify that the path is under the configured profile directory.
- Files: `src/BS2BG.App/Services/UserProfileStore.cs`, `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs`, `tests/BS2BG.Tests/UserProfileStoreTests.cs`, `tests/BS2BG.Tests/ProfileManagerViewModelTests.cs`
- Current mitigation: Profile entries normally originate from `DiscoverProfiles()` scanning the configured local profile directory, and non-local profile rows cannot execute the delete command.
- Recommendations: Add a path containment check in `UserProfileStore.DeleteProfile` and tests for malicious or stale `FilePath` metadata.

## Performance Bottlenecks

**NPC import reads full files into memory:**
- Problem: `NpcTextParser.ParseFile` uses `File.ReadAllBytes(path)` before decoding and parsing every row.
- Files: `src/BS2BG.Core/Import/NpcTextParser.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `tests/BS2BG.Tests/NpcImportPreviewServiceTests.cs`
- Cause: Encoding detection and parsing operate over a full byte array/string snapshot.
- Improvement path: Keep current behavior for normal xEdit exports, but add file-size limits or streaming/chunked parsing before supporting very large NPC databases.

**Filtering and visible-row recomputation stay in the UI ViewModel:**
- Problem: `MorphsViewModel` maintains DynamicData row caches plus visible collections, selected row IDs, column value notifications, and search predicates.
- Files: `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/ViewModels/Workflow/NpcFilterState.cs`, `tests/BS2BG.Tests/NpcFilterStateTests.cs`, `tests/BS2BG.Tests/MorphsViewModelTests.cs`
- Cause: Filtering logic is feature-rich and table-specific, and manual UAT for large datasets remains a carried-forward item.
- Improvement path: Benchmark large NPC imports and filter toggles; isolate filtering into a reusable service with explicit complexity tests for distinct-value generation and selection pruning.

**Bundle generation stages outputs through disk before zipping:**
- Problem: `PortableProjectBundleService` creates a temp staging directory, writes INI/JSON outputs, reads them back into memory, then creates the zip.
- Files: `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`, `tests/BS2BG.Tests/PortableBundleServiceTests.cs`
- Cause: The bundle service reuses file-oriented exporters to preserve byte-identical behavior.
- Improvement path: Keep file-writer reuse until parity-safe in-memory writer seams exist; if bundles become large, add byte-producing exporter APIs with golden-file tests proving identical output.

## Fragile Areas

**Byte-identical output pipeline is intentionally brittle:**
- Files: `src/BS2BG.Core/Formatting/JavaFloatFormatting.cs`, `src/BS2BG.Core/Formatting/SliderMathFormatter.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`, `tests/fixtures/expected/**`, `tests/BS2BG.Tests/SliderMathFormatterTests.cs`, `tests/BS2BG.Tests/ExportWriterTests.cs`
- Why fragile: Line endings, trailing newlines, float formatting, half-up rounding, slider ordering, default injection, and profile-specific lookup tables are compatibility behavior rather than incidental formatting.
- Safe modification: Consult `src/com/asdasfa/jbs2bg/` via the `java-ref` skill before changes; run golden-file parity tests; do not regenerate `tests/fixtures/expected/**` to hide failures.
- Test coverage: Strong golden-file coverage exists, but failures require careful byte-level diagnosis.

**Profile fallback and custom-profile recovery are cross-cutting:**
- Files: `src/BS2BG.Core/Serialization/ProjectFileService.cs`, `src/BS2BG.Core/Generation/RequestScopedProfileCatalogComposer.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.Cli/Program.cs`, `tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs`, `tests/BS2BG.Tests/ProjectFileServiceCustomProfileTests.cs`
- Why fragile: Saved projects can reference bundled, local custom, embedded project, or missing profiles; UI flows must remain neutral and undoable while CLI/bundle flows block unresolved custom profile fallback.
- Safe modification: Preserve unresolved `ProfileName` values unless an explicit recovery action remaps them; update App, Core, CLI, and serialization tests together.
- Test coverage: Good regression coverage exists for profile recovery and project round-trip, but visual fallback UAT remains carried forward in `.planning/STATE.md`.

**Assignment strategy replay affects automation output correctness:**
- Files: `src/BS2BG.Core/Morphs/AssignmentStrategyReplayService.cs`, `src/BS2BG.Core/Morphs/AssignmentStrategyService.cs`, `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs`, `src/BS2BG.Cli/Program.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `tests/BS2BG.Tests/AssignmentStrategyReplayServiceTests.cs`, `tests/BS2BG.Tests/PortableBundleServiceTests.cs`, `tests/BS2BG.Tests/CliGenerationTests.cs`
- Why fragile: Headless and bundle generation must replay saved assignment strategies without mutating saved project state incorrectly, and blocked rows must prevent stale output.
- Safe modification: Keep replay before validation and output generation for BodyGen intents; preserve no-replay behavior for BoS-only intents; add tests for seeded determinism, stale assignments, blockers, and project save state.
- Test coverage: Strong targeted coverage exists, but changes touch shared assignment behavior used by both UI and automation.

**Open-project transaction ordering is delicate:**
- Files: `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs`, `src/BS2BG.App/Services/TemplateProfileCatalogService.cs`, `tests/BS2BG.Tests/MainWindowViewModelProfileRecoveryTests.cs`
- Why fragile: Project open resolves embedded/local profile conflicts, may save local profiles, swaps project state, clears undo history, marks dirty conditionally, and updates project-scoped catalog overlays.
- Safe modification: Preserve the no-caller-visible-mutation-before-conflict-resolution pattern and test cancellation/failure paths alongside success paths.
- Test coverage: Profile recovery tests exist, but failures can present as stale catalog, missing fallback, dirty-state, or undo-stack bugs.

## Scaling Limits

**Large NPC and preset datasets have no documented capacity target:**
- Current capacity: Not numerically specified; collections are in-memory `ObservableCollection`/DynamicData caches and generated text is built as full strings.
- Limit: Very large NPC imports or many preset/profile combinations can increase UI memory, distinct-filter recomputation, generated-text size, and bundle staging time.
- Scaling path: Define representative large fixtures, add non-golden performance smoke tests, and enforce UI responsiveness for import preview, filter toggles, assignment replay, and bundle generation.

**Release packaging is Windows-first:**
- Current capacity: Self-contained Windows portable release is the preferred distribution shape.
- Limit: Cross-platform runtime, packaging, file-dialog behavior, path handling, and manual QA are not established as release gates.
- Scaling path: Add Linux/macOS packaging and Avalonia UI smoke tests only after defining cross-platform support expectations in the next milestone.

## Dependencies at Risk

**Fallout 4 CBBE profile data lacks authoritative parity calibration:**
- Risk: FO4 output can be generated, but slider defaults/inverts/multipliers are documented as experimental until known-good calibration data exists.
- Impact: Fallout 4 users can receive plausible but unverified BodyGen/BoS output.
- Migration plan: Add FO4 calibration fixtures or an explicit calibration assistant before promoting FO4 support beyond experimental release-note status.

**Avalonia 12 / ReactiveUI 23 conventions are strict project contracts:**
- Risk: New UI code that omits compiled-binding `x:DataType`, reintroduces custom `ICommand` wrappers, or uses ViewModel `Dispatcher.UIThread` dispatch violates project conventions.
- Impact: Binding failures, asynchronous command cancellation bugs, and inconsistent test scheduler behavior.
- Migration plan: Follow `openspec/specs/reactive-mvvm-conventions/spec.md`; keep ViewModels on `[Reactive]`, `ReactiveCommand`, observable `canExecute`, and `ToProperty` derived state.

**Java reference toolchain remains required for expected-corpus regeneration:**
- Risk: Golden expected files depend on the legacy Java reference and JavaFX 8 harness.
- Impact: If the reference build becomes unavailable, intentional parity updates are harder to validate and regenerate.
- Migration plan: Preserve the existing Java reference tree and document/tool the reference harness environment before replacing any expected fixture workflow.

## Missing Critical Features

**FO4 calibration workflow:**
- Problem: Fallout 4 support has no authoritative parity source or calibration assistant.
- Blocks: Confident FO4 release claims and reliable FO4-specific regression fixtures.

**Advanced modding and ecosystem integrations are not implemented:**
- Problem: Preset diff, automatic game/mod folder discovery, richer assignment strategies, scrubbed support bundles, and cross-platform release parity are deferred.
- Blocks: Higher-level troubleshooting, automated setup guidance, and broader modding workflows beyond the v1.0 desktop utility scope.

**In-app developer diagnostics are absent:**
- Problem: PRD notes Avalonia diagnostic tooling availability is a deferred decision; source scan found no `Avalonia.Diagnostics`, `DiagnosticsSupport`, or `AttachDevTools` usage.
- Blocks: Fast visual-tree/binding inspection during UI debugging unless developers use external tooling or add a gated diagnostics package.

## Test Coverage Gaps

**Manual visual and large-dataset UAT remains carried forward:**
- What's not tested: Fallback panel placement/theme readability, filter/scope behavior under real usage, large dataset behavior, and restart persistence scenarios.
- Files: `.planning/STATE.md`, `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.App/Services/UserPreferencesService.cs`
- Risk: UI regressions can pass automated ViewModel/headless tests while still affecting real usability.
- Priority: High

**Performance ceilings are not asserted:**
- What's not tested: Maximum supported NPC text size, preset count, profile count, bundle size, and acceptable latency for import/filter/generate operations.
- Files: `src/BS2BG.Core/Import/NpcTextParser.cs`, `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs`, `tests/BS2BG.Tests/`
- Risk: Large real mod lists can expose memory or responsiveness issues that functional tests do not catch.
- Priority: Medium

**Security boundary tests for profile file paths are incomplete:**
- What's not tested: `UserProfileStore.DeleteProfile` rejecting local-profile metadata whose `FilePath` points outside the configured profile directory.
- Files: `src/BS2BG.App/Services/UserProfileStore.cs`, `tests/BS2BG.Tests/UserProfileStoreTests.cs`
- Risk: A future bug in catalog entry construction could turn stale or malicious metadata into deletion outside the intended profile store.
- Priority: Medium

**Future profile schema migration has no test harness:**
- What's not tested: Forward-compatible profile JSON versions, migration from older profile schema versions, or preservation of unknown future fields.
- Files: `src/BS2BG.Core/Generation/ProfileDefinitionService.cs`, `tests/BS2BG.Tests/ProfileDefinitionServiceTests.cs`, `tests/BS2BG.Tests/UserProfileStoreTests.cs`
- Risk: Profile extensibility work can break user-authored custom profiles or reject shareable profiles without a migration path.
- Priority: Medium

---

*Concerns audit: 2026-04-28*
