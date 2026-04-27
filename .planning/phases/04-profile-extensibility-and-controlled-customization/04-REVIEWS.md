---
phase: 4
reviewers: [gemini, claude, codex]
reviewed_at: 2026-04-27T01:11:38.0504832-07:00
plans_reviewed:
  - 04-01-PLAN.md
  - 04-02-PLAN.md
  - 04-03-PLAN.md
  - 04-04-PLAN.md
  - 04-05-PLAN.md
  - 04-06-PLAN.md
  - 04-07-PLAN.md
---

# Cross-AI Plan Review - Phase 4

## Gemini Review

# Phase 4 Plan Review: Profile Extensibility and Controlled Customization

## Summary
The implementation plans for Phase 4 provide a comprehensive and architecturally sound roadmap for introducing custom profile management. The strategy effectively balances the need for user flexibility with the strict requirement of preserving bundled profile integrity and backward compatibility. By splitting the work into Core validation/serialization and App orchestration, the plans ensure that profile identity remains stable (internal name-based) and that project sharing is robust (embedding referenced definitions). The inclusion of explicit conflict handling during project load and the non-blocking approach to missing profiles align perfectly with the project's "trust and parity" philosophy.

## Strengths
- **Identity Integrity**: The decision to use internal `Name` as the sole identity, explicitly rejecting filenames or fuzzy matching for resolution, prevents a common class of data-corruption bugs in modding tools.
- **Trust Domain Separation**: Clearly tagging catalog entries as `Bundled`, `LocalCustom`, or `EmbeddedProject` allows the UI and generation logic to enforce read-only constraints on bundled data while enabling "Use Project Copy" overlays without forced local installation.
- **Backward Compatibility**: The use of `[JsonPropertyOrder]` and optional top-level sections in `ProjectFileService` (Plan 03) ensures that older versions of BS2BG can still read Phase 4 projects without failing or losing legacy math fields.
- **Validation-Gated Workflows**: Gating both `Save` and `Import` commands behind Core-level strict validation ensures the runtime catalog never contains malformed data that could break generation math.
- **User Safety**: The sequential conflict dialog (Plan 06) and the byte-identical baseline test for project saves (Plan 03) show a high degree of defensive engineering suited for a parity-sensitive tool.

## Concerns
- **MEDIUM - Project-Scoped Lookup Persistence**: While Plan 06 mentions using `WithProjectProfiles` for an overlay, it is not explicitly clear how this project-scoped state is cleared when the project is closed or a new one is created. A subsequent project might accidentally see embedded profiles from the previous session if the singleton `ITemplateProfileCatalogService` is not reset during the `NewProjectAsync` flow.
- **LOW - Sanitized Filename Collisions**: Plan 02 uses an 8-character hash for collisions. While deterministic, if a user renames a profile in the editor to something that would collide with another existing profile's file, the logic needs to ensure it does not overwrite the wrong file during the `SaveProfile` call.
- **LOW - UI/UX for Large Slider Tables**: Profile definitions can have hundreds of sliders. A dense editable row approach in Avalonia without virtualization or a search/filter inside the editor might become sluggish or difficult to navigate for very large custom profiles.

## Suggestions
- **Explicit Catalog Reset**: In Plan 06, ensure `MainWindowViewModel.NewProjectAsync` explicitly calls `catalogService.Refresh()` or a reset method to clear any `EmbeddedProject` entries from the runtime catalog.
- **Conflict Equality Edge Case**: In Plan 01, ensure `ProfileDefinitionEquality` treats `null` or missing tables as equivalent to empty objects/arrays to avoid false conflicts when sharing projects with older/newer versions of the schema.
- **Editor Search**: Consider adding a simple `Filter Sliders` text box inside the `ProfileEditorViewModel` (Plan 05) to help users find specific sliders in a large profile definition.
- **Bulk-Conflict Handling**: While sequential dialogs are safer, if a project contains 20 custom profiles, the user might find 20 dialogs tedious. A future `Apply to all remaining conflicts` checkbox or summary list would be a good v2 addition.

## Risk Assessment: LOW
The overall risk is low. The plans are highly surgical, adding new collections and sections rather than refactoring existing sacred slider math. The dependency chain is logical, and the Wave 1-5 structure ensures that UI components are not built before their underlying Core services are validated. The primary success factor - byte-identical output - is protected by specific tasks in Plans 01 and 03.

The plans are approved for execution.

---

## the agent Review

# Cross-AI Plan Review - Phase 4: Profile Extensibility and Controlled Customization

## Overall Summary

This is a strong, well-decomposed plan set for adding custom profile extensibility to BS2BG. The seven plans honor the locked CONTEXT.md decisions (D-01 through D-17), correctly stage Core contracts before App workflows, preserve byte-parity sacred files, and explicitly address review concerns inline (visible in the `truths` notes). Wave dependencies are coherent and the critical path (Plan 01 -> 02/03 -> 04 -> 05/06 -> 07) reflects real data flow rather than arbitrary ordering. Test coverage is mapped to each requirement and the threat models are scoped appropriately for a local-only desktop app. The main risks are integration-heavy (Plan 06's project-open conflict choreography) and subtle compatibility risk (Plan 03 byte-equality guarantee), with a few specification gaps around concurrent collection access, refresh ordering, and the Templates VM's catalog binding.

**Overall Risk: MEDIUM** - Plans 1, 2, 4, 5, 7 are LOW-MEDIUM; Plans 3 and 6 carry the bulk of the risk.

---

## Plan 04-01: Core validation contracts

**Summary**: Foundation plan. Cleanly separates the immutable contract types (`CustomProfileDefinition`, `ProfileValidationResult`, `ProfileDefinitionEquality`) from the strict parser/exporter. TDD-first with focused acceptance tests that lock D-03/D-06/D-07/D-08/D-12 semantics.

**Strengths**:
- `DefinitionallyEquals` is defined here in Plan 01 even though it is consumed in Plan 06 - exactly the right place to lock conflict-equality semantics.
- Schema versioning (`Version: 1`) introduced proactively as a forward-compatibility seam.
- Explicit NaN/Infinity rejection rules close a subtle gap that broad-finite-float acceptance could create.
- `ProfileSourceKind.EmbeddedProject` is declared in Plan 01 even though it is not used until Plan 06 - avoids a ripple-edit later.

**Concerns**:
- **MEDIUM**: The `ExportProfileJson` round-trip stability acceptance is asserted in tests but the plan does not specify the exact ordering rule for keys within `Defaults`/`Multipliers` objects. If the export uses dictionary-iteration order, two semantically equal profiles can serialize differently across OS/runtime. Recommend specifying `OrdinalIgnoreCase` or original-source order if preserved deterministic key ordering.
- **LOW**: `Game` is optional metadata normalized to `string.Empty` when absent but Plan 02 Task 3 wants to seed bundled `Game = "Skyrim"` / `"Fallout4"`. Consider whether `Game` should be a normalized enum or a constrained string set for consistency, or document that it is free-form.
- **LOW**: `ProfileValidationContext.ForImport` takes an `IEnumerable<string>` of existing names but the threading/lifetime model is not stated. If this is enumerated lazily and the underlying catalog mutates during validation, duplicate checks could be inconsistent. Materializing into a snapshot HashSet inside the constructor would be safer.

**Suggestions**:
- Add an explicit acceptance criterion for deterministic JSON key ordering inside Defaults/Multipliers.
- Document `Game` as a string with a recommended-values list rather than free-form.
- Add a test asserting that two `CustomProfileDefinition` instances built from the same JSON produce identical `ExportProfileJson` strings, byte-for-byte.

**Risk: LOW**

---

## Plan 04-02: AppData store + source-tagged catalog

**Summary**: Introduces user-local profile storage, tags catalog entries with source metadata, and creates a refresh-aware catalog service. Also migrates `TemplateProfileCatalogFactory` from static to injectable while preserving the existing static shim - a meaningful but well-scoped refactor.

**Strengths**:
- Single `ITemplateProfileCatalogService.Refresh()` ownership for runtime catalog updates is precisely the right shape for the cross-cutting refresh story across Plans 5/6/7.
- `WithProjectProfiles` overlay keeps embedded project profiles out of the local store unless explicit - correctly enforces D-13/D-15/D-16.
- Filename sanitization with deterministic 8-char hash collision suffix is specified, eliminating cross-OS variance.
- Discovery failures degrade gracefully to bundled-only catalog, matching `UserPreferencesService` non-blocking convention.

**Concerns**:
- **HIGH**: `TemplateProfileCatalog`'s existing constructor and lookup is hot-path code consumed by `TemplatesViewModel`, `MainWindowViewModel`, `DiagnosticsViewModel`, `BosJsonExportWriter`, `ProjectValidationService`, and others. Plan 02 says existing consumers can keep using the constructor that wraps inputs as `Bundled`. But the catalog is currently registered as a singleton instance. After refresh, the catalog instance changes, so anyone who captured the old reference will not see updates. The plan acknowledges existing consumers can receive `TemplateProfileCatalog` from `catalogService.Current` until later plans migrate, but Plan 06 then asks Templates/Diagnostics ViewModels to react to recovery state. Recommend either making the existing catalog mutable in place or explicitly migrating affected ViewModels in this plan. The current ambiguity means generation can produce stale results after a profile import until app restart.
- **MEDIUM**: Concurrent access to the `UserProfileStore` during background discovery vs. foreground import is not specified. If `Refresh()` is invoked from a Plan 05 import command while another `Refresh()` is in-flight, the catalog can race. Specify a lock or serialize through `RxApp.TaskpoolScheduler`.
- **MEDIUM**: `LastDiscoveryDiagnostics` exposes rejected-file diagnostics on `ITemplateProfileCatalogService`, but no plan in the set actually surfaces those diagnostics in UI. Plan 07 builds the Profiles tab but does not specify a rejected files group. Either add presentation in Plan 07 or drop this property.
- **LOW**: The plan mentions `SourceKind = "Skyrim"` / `"Fallout4"` for bundled `Game`, but `ProjectProfileMapping` constants are not game-tagged today. Confirm these strings are not load-bearing for any existing test or fixture.

**Suggestions**:
- Add explicit `MainWindowViewModel`, `TemplatesViewModel`, and `DiagnosticsViewModel` migration to `ITemplateProfileCatalogService.Current` inside Task 3, with a focused acceptance criterion that stale `TemplateProfileCatalog` singleton injection is removed.
- Document refresh thread-safety, such as serializing all `Refresh()` and `WithProjectProfiles()` calls via a `SemaphoreSlim`.
- Add a test that proves Templates preview math reflects a newly-imported custom profile within the same session, not via app restart.

**Risk: MEDIUM** due to the singleton-vs-refresh mismatch.

---

## Plan 04-03: Embedded CustomProfiles section

**Summary**: Adds the optional `CustomProfiles` top-level array to `.jbs2bg` while preserving legacy fields. Smartly insists on byte-equality for projects with no custom profiles.

**Strengths**:
- The byte-identical to v1 format when no custom profiles acceptance criterion is the correct guardrail for project-roundtrip compatibility.
- D-15 enforcement is concretely specified: `OrdinalIgnoreCase` match against `SliderPreset.ProfileName`, excluding `Bundled`.
- Deterministic ordering by `Name` with `OrdinalIgnoreCase` makes saved files diffable.
- ProjectModel collection wiring follows the existing `AttachCollection` dirty-tracking pattern.

**Concerns**:
- **HIGH**: The byte-equality test is described as capturing current serializer output before implementation and asserting it remains byte-identical after implementation. This requires either a fixture file under `tests/fixtures/expected/**` (sacred) or a runtime-baseline comparison. The plan does not say which. Specify the mechanism.
- **MEDIUM**: `CustomProfiles` ordering at position `JsonPropertyOrder(3)` is defined, but future root-level optional sections will need to know whether `CustomProfiles` should stay last or migrate. Add a comment in `ProjectFileService.cs` documenting the order policy.
- **MEDIUM**: Malformed embedded profile load behavior is Core-side diagnostics/invalid embedded-profile result data while project open remains possible. But `ProjectFileService.LoadFromString` currently has no diagnostics return channel; it returns a `ProjectModel`. The plan does not specify the new return shape. Either change the signature or attach diagnostics to `ProjectModel`; Plan 06 then needs to consume them during `TryOpenProjectPathAsync`.
- **LOW**: `EmbeddedProfileDto` shape is defined as matching standalone profile JSON from Plan 01. If Plan 01's standalone JSON changes later, embedded compatibility needs separate consideration. Document the relationship.

**Suggestions**:
- Add an explicit acceptance criterion specifying the byte-equality test mechanism, such as a string literal in the test file containing canonical v1 JSON output.
- Define the `LoadFromString` diagnostics return contract, such as `LoadProjectResult` with `ProjectModel` plus `IReadOnlyList<ProjectLoadDiagnostic>`, or transient `ProjectModel.LoadDiagnostics`.
- Add a test that asserts a project with mixed referenced and unreferenced custom profiles in `ProjectModel.CustomProfiles` only serializes the referenced ones.

**Risk: MEDIUM** because the byte-equality and diagnostics-channel ambiguities are real.

---

## Plan 04-04: Recovery diagnostics

**Summary**: Adds a Core-only read-only recovery diagnostics service with exact-match resolution helpers. Correctly depends on Plan 03 so it can detect embedded copies. Task 3 deduplicates with Phase 3 findings.

**Strengths**:
- Embedded-profile awareness tightly couples recovery UX to project file content.
- Phase 3 deduplication is critical and easy to miss.
- Read-only contract preserves Phase 3 diagnostics boundary.
- Explicit `CanResolveMissingReference` test for filename-vs-internal-name mismatch nails the D-12 anti-pattern.

**Concerns**:
- **MEDIUM**: Task 3's deduplication filters by exact missing profile display name and diagnostic category/code, but `ProfileDiagnosticsService` currently emits findings keyed by free-form `Title`/`Detail` strings, not codes. To filter by code reliably, add a `Code` field to `DiagnosticFinding` or change `ProfileDiagnosticsService` to mark fallback-detail findings distinctly.
- **MEDIUM**: The recovery service inspects `ProjectModel.SliderPresets` and `ProjectModel.CustomProfiles`. If Plan 06 adds a project-scoped overlay through `WithProjectProfiles`, recovery diagnostics need to know whether overlay-active state means resolved or still missing for save/share semantics. Plan 04 should specify which state diagnostics observe.
- **LOW**: `ProfileRecoveryActionKind.UseProjectEmbeddedCopy` is enum'd here but the command-side behavior is in Plan 06 Task 3. Cross-reference would help future maintainers.

**Suggestions**:
- Add a `Code` or `Category` field to `DiagnosticFinding` so deduplication is mechanical, not text-search.
- Specify whether recovery diagnostics treat embedded copy active via overlay as resolved or still using fallback semantically.
- Add a test that asserts the recovery service does not produce a diagnostic when a preset's profile name resolves to a bundled profile via existing case-insensitive lookup.

**Risk: LOW**

---

## Plan 04-05: Manager + Editor ViewModels

**Summary**: Creates `ProfileManagerViewModel`, `ProfileEditorViewModel`, and `IProfileManagementDialogService`. Validation-gated save, source-aware command availability, single editor instance with discard confirmation on selection change.

**Strengths**:
- Single editor instance with `ConfirmDiscardUnsavedEditsAsync` on selection change is a thoughtful UX detail captured up front.
- Save-failure preserves unsaved edits, a common forgotten edge case explicitly tested.
- Bundled rows have explicitly disabled edit/delete commands rather than hidden, matching UI-SPEC `Bundled - read-only` discoverability.
- Validation transitions are tested as the buffer changes, not only on save attempt.

**Concerns**:
- **MEDIUM**: The editor row models are instructed to convert to JSON/definition candidate and validate with `ProfileDefinitionService` before save. For hundreds of slider rows, validating on every keystroke through `JsonDocument.Parse` is wasteful. Specify whether validation is debounced/triggered explicitly, or whether the editor builds a `CustomProfileDefinition` directly and uses a non-JSON validation path.
- **MEDIUM**: `Refresh()` is called after import/save/delete success, but if `Refresh()` rebuilds the catalog, the manager VM's bound `ProfileEntries` collection will need to be reloaded. Specify the binding strategy: observe `Current` via property change notification or re-query in command result handler.
- **MEDIUM**: Singleton lifetime for `ProfileManagerViewModel` assumes a single-shell app. This is probably fine for current code, but document the assumption.
- **LOW**: `ProfileEditorViewModel` is described as a child but no DI registration is specified. Specify whether it is transient or manager-owned.
- **LOW**: `ConfirmDiscardUnsavedEditsAsync` duplicates existing app-level discard patterns. This is acceptable if copy differs, but note the distinction.

**Suggestions**:
- Specify validation cadence, such as editor builds a `CustomProfileDefinition` candidate from row state without round-tripping through JSON; only export uses `ExportProfileJson`.
- Add an acceptance criterion that the manager's `ProfileEntries` reflects `Refresh()`-induced catalog changes within the same session.
- Document single-shell assumption for `ProfileManagerViewModel` lifetime.

**Risk: LOW-MEDIUM**

---

## Plan 04-06: Project-open conflict + recovery actions

**Summary**: The most complex plan in the phase. Implements project-open conflict choreography, `Use Project Copy` overlay activation, undo-recorded preset remap, and Diagnostics recovery actions.

**Strengths**:
- The 8-step `TryOpenProjectPathAsync` sequence is laid out explicitly with cancel semantics.
- Cancel-leaves-existing-project-unchanged is correctly placed before `project.ReplaceWith(loadedProject)`.
- Undo-recorded remap with reappearing recovery diagnostic on undo is a strong correctness invariant.
- Multi-conflict handling is explicitly sequential with no apply-to-all ambiguity.
- `ProfileDefinitionEquality.DefinitionallyEquals` is correctly leveraged from Plan 01.

**Concerns**:
- **HIGH**: Conflict comparison happens before `project.ReplaceWith(loadedProject)`, but local custom profiles are read from `IUserProfileStore` / `ITemplateProfileCatalogService.Current` at the moment of comparison. Between the dialog `await` and actual store mutation/`ReplaceWith`, another import could mutate the local store. Specify a TOCTOU mitigation, such as capturing an `IReadOnlyList<CustomProfileDefinition>` snapshot once at the start of `TryOpenProjectPathAsync` and operating on that snapshot throughout.
- **HIGH**: `Replace Local Profile` saves embedded definition through `UserProfileStore`, refreshes the catalog, and local profile becomes active. If this happens before `project.ReplaceWith(loadedProject)` and a later conflict dialog or write fails, the local store is changed but the project has not opened. Specify rollback, or defer destructive store operations until after all dialogs resolve.
- **MEDIUM**: `Rename Project Copy` requires a unique display name, but the uniqueness scope should be specified against the post-decision projected catalog, including all prior rename decisions in this open.
- **MEDIUM**: Recording undo during project open for `Rename Project Copy` may fight normal open-project behavior that clears undo history. Confirm whether rename-on-open should create an undo entry or simply mark dirty after load.
- **MEDIUM**: Diagnostics recovery actions add mutating commands to a previously read-only diagnostics area. Phase 4 context permits explicit recovery actions, but the relevant OpenSpec capability specs should be updated to reflect this.
- **LOW**: The plan modifies `TemplatesViewModel` to add a remap command but does not specify how recovery action invocation flows from `DiagnosticsViewModel` to `TemplatesViewModel`; cross-VM coordination should be specified.

**Suggestions**:
- Add an explicit snapshot-local-profiles step before the conflict loop.
- Defer destructive `UserProfileStore.SaveProfile` writes for `Replace Local Profile` to a single transaction phase after all conflicts are resolved.
- Specify uniqueness scope for `Rename Project Copy` as unique against the post-decision projected catalog.
- Add an acceptance test for partial-failure rollback.
- Document cross-VM coordination for Diagnostics recovery actions, such as routing through `MainWindowViewModel` or a recovery coordinator service.

**Risk: HIGH** due to project-open choreography and partial-failure surface area.

---

## Plan 04-07: Profiles workspace UI

**Summary**: Adds the Profiles tab to `MainWindow`, wires `INavigationService` for cross-VM navigation, and includes a human visual verification checkpoint.

**Strengths**:
- `INavigationService` cleanly decouples `TemplatesViewModel` from `MainWindowViewModel` for the `Manage Profiles` button.
- Profiles search behavior is specified.
- Headless test asserts the Missing references group is present even when empty.
- Human verification checkpoint is well-scripted.
- Spacing tokens are explicitly enumerated.

**Concerns**:
- **MEDIUM**: `Profiles.WhenAnyValue(x => x.IsBusy)` is added to the busy aggregate. Plan 05 specifies `IsBusy`, but Plan 06 conflict handling happens in `MainWindowViewModel`, not directly in `ProfileManagerViewModel`. `Profiles.IsBusy` may not capture project-open conflict resolution time. Either route conflict-resolution through a `ProfileManagerViewModel` command or add a separate observable to the aggregate.
- **LOW**: The Profiles tab likely needs a typed `DataTemplate` for the editor pane with `x:DataType="vm:ProfileEditorViewModel"`. The plan mentions typed DataTemplates for profile rows/editor rows but not the editor pane itself.
- **LOW**: `ManageProfilesCommand` on `TemplatesViewModel` injects `INavigationService`; confirm the constructor is updated in this plan.
- **LOW**: The human verification checkpoint references a missing custom profile project fixture/test path if available. Make this concrete with deterministic reproduction steps or a fixture from Plan 06.

**Suggestions**:
- Route project-open conflict resolution through a busy source included in the aggregate.
- Add explicit `x:DataType="vm:ProfileEditorViewModel"` acceptance criterion for the editor pane.
- Update `TemplatesViewModel` constructor signature in the action text to include `INavigationService`.
- Make the human checkpoint missing-profile test path concrete.

**Risk: LOW**

---

## Cross-Plan Issues

### Phase Goal Coverage

The five Phase 4 success criteria map cleanly:
1. **Import/copy/export/validate** - Plans 01, 02, 05
2. **Strict edit validation** - Plans 01, 05
3. **Save with legacy fields** - Plan 03
4. **Resolve missing references with diagnostics** - Plans 04, 06
5. **Bundle/share project profiles** - Plans 03, 06

Coverage is complete.

### Dependency Ordering

The wave structure is correct:
- Wave 1 (01) -> 2 (02, 03) -> 3 (04, 05) -> 4 (06) -> 5 (07)
- Plan 04 correctly depends on 03 for embedded profile awareness.
- Plan 05 correctly depends on 02 and 01.
- Plan 06 correctly depends on 02, 03, 04.

One latent concern: Plan 05 starts before Plan 06, meaning the manager VM is built before conflict-handling integration is specified. If Plan 06 reveals API needs on `ProfileManagerViewModel`, those need to go into Plan 06 or a Plan 05 follow-up.

### Test File Strategy

The plans add several new test classes. With xUnit v3, module initializer, and ReactiveUI immediate scheduling, this should work, but the full suite runtime will grow. Consider whether any tests should be tagged for selective CI runs. Not blocking.

### Sacred Files

All plans correctly avoid `JavaFloatFormatting.cs`, `SliderMathFormatter.cs`, `BodyGenIniExportWriter.cs`, `BosJsonExportWriter.cs`, and `tests/fixtures/expected/**`. Plan 03 comes closest by extending `ProjectFileService.cs`, but the byte-equality acceptance is the right guard.

### OpenSpec Updates

None of the plans mention updating OpenSpec capability specs (`project-roundtrip`, `template-generation-flow`, diagnostics workflow if present, plus a new `profile-extensibility` capability). Phase 4 introduces enough behavior to warrant a new capability spec or significant deltas to existing ones. This is a **MEDIUM** concern; recommend adding an explicit OpenSpec spec update task to Plan 07 or as Plan 08.

### Threading Model

No plan specifies the threading contract for `IUserProfileStore` (sync vs. async; if sync, called only from Taskpool) or `ITemplateProfileCatalogService` (thread-safe vs. single-threaded). Plan 06 project-open, Plan 05 import, and Plan 07 UI navigation all touch the same state. Add a one-line threading contract somewhere.

---

## Risk Heatmap

| Plan | Risk | Primary Concern |
|------|------|-----------------|
| 04-01 | LOW | Key-ordering determinism in export |
| 04-02 | MEDIUM | Catalog singleton vs. refresh contract |
| 04-03 | MEDIUM | Byte-equality test mechanism + load diagnostics channel |
| 04-04 | LOW | Phase 3 dedup mechanism specificity |
| 04-05 | LOW-MEDIUM | Validation cadence + refresh propagation to bound list |
| 04-06 | HIGH | TOCTOU + partial-failure rollback in conflict sequence |
| 04-07 | LOW | Busy aggregation for project-open path |

**Overall Phase 4 Risk: MEDIUM** - Plans 02, 03, and especially 06 should be re-reviewed after revisions; 01, 04, 05, 07 are largely ready.

## Top Recommendations (Prioritized)

1. **Plan 02**: Migrate `TemplateProfileCatalog` consumers to `ITemplateProfileCatalogService.Current` in this plan, not later. Otherwise refresh is silently broken.
2. **Plan 06**: Snapshot local profiles at the start of `TryOpenProjectPathAsync` and defer destructive store writes until all conflicts resolve. Document partial-failure semantics.
3. **Plan 03**: Specify the byte-equality test mechanism explicitly.
4. **Plan 03**: Define the diagnostics return channel for `LoadFromString` malformed embedded profile data.
5. **Cross-cutting**: Add an OpenSpec capability spec update task.
6. **Plan 04**: Add a `Code`/`Category` field to `DiagnosticFinding` so Phase 3/Phase 4 dedup is mechanical.

---

## Codex Review

**Overall**

The phase is well decomposed around Core-first validation, App-local storage, project embedding, recovery diagnostics, then UI. The plans mostly achieve EXT-01 through EXT-05 and respect the key trust boundary: bundled, custom, embedded, and unresolved profiles must stay distinguishable. The main risks are not concept quality, but scope density in 04-06, a missing serialization/profile-resolver contract in 04-03, and catalog overlay semantics that could collide with the duplicate-name rule introduced in 04-02.

## 04-01 Core Validation

**Summary:** Strong foundation plan. It defines identity, validation, export, and equality before later plans depend on those semantics.

**Strengths**
- Keeps validation in Core and out of UI.
- Explicitly allows blank profiles and broad finite floats.
- Defines normalized equality before embedded/local conflict handling.

**Concerns**
- **MEDIUM:** Validation rejects duplicate slider names case-insensitively, but equality compares slider names case-sensitively. That can be valid, but the distinction should be documented in the equality XML comments.
- **LOW:** `NaN`/`Infinity` tests may be artificial unless JSON parsing options allow named floating-point literals; malformed JSON may already reject them.
- **LOW:** Export stability should specify indentation/newline behavior so snapshot tests are deterministic.

**Suggestions**
- Add tests for duplicate JSON object properties found through `JsonElement.EnumerateObject`, not just duplicate DTO rows.
- Document that filenames and paths are provenance only, never identity.
- Use one helper for normalized profile-name and table-key comparison.

**Risk Assessment:** **LOW-MEDIUM**. Scope is contained and mostly Core-only.

## 04-02 AppData Store And Catalog

**Summary:** Good next layer, but catalog refresh and duplicate handling need sharper contracts.

**Strengths**
- Keeps bundled profiles read-only and loaded first.
- Uses AppData storage and atomic writes.
- Introduces a refresh-owning catalog service, which later UI workflows need.

**Concerns**
- **MEDIUM:** Discovery order for multiple custom files is not specified. If two custom files have the same display name, first-wins behavior could become filesystem-order dependent.
- **MEDIUM:** `WithProjectProfiles` may conflict with the catalog constructor's duplicate-name rejection once project overlays need to temporarily prefer embedded copies.
- **LOW:** stable hash must not use `string.GetHashCode`, which is process-randomized in modern .NET.

**Suggestions**
- Sort discovered files by full path before validation and make duplicate handling deterministic.
- Specify SHA-256 or another stable hash for filename suffixes.
- Decide whether `TemplateProfileCatalogService` raises change notifications or whether every consumer must explicitly refresh from `Current`.

**Risk Assessment:** **MEDIUM**. The design is sound, but runtime refresh and overlay semantics affect later plans.

## 04-03 Project Embedding

**Summary:** Directionally correct, but this plan has the biggest missing contract before 04-06.

**Strengths**
- Preserves legacy root fields and adds an optional `CustomProfiles` section.
- Filters embedded profiles to referenced custom profiles only.
- Requires byte-identical output when no custom profiles exist.

**Concerns**
- **HIGH:** Save embeds only `ProjectModel.CustomProfiles`. If a project references a local custom profile from the AppData catalog, this plan does not say how that definition gets into `ProjectModel.CustomProfiles` before save.
- **HIGH:** Malformed embedded entries produce diagnostics, which requires a load-result contract, but the plan does not define whether `ProjectFileService.LoadFromString` returns `ProjectLoadResult`, stores diagnostics on `ProjectModel`, or uses another channel.
- **MEDIUM:** `CustomProfileDefinition` is immutable, but `SliderProfile` contents may not be. Dirty tracking and snapshot semantics need cloning/replacement rules.

**Suggestions**
- Add an explicit profile-definition resolver for save, or require profile selection/remap workflows to copy referenced custom definitions into `ProjectModel.CustomProfiles`.
- Define a `ProjectLoadResult` or equivalent with `ProjectModel` plus embedded-profile diagnostics.
- Add tests for local custom profile referenced by a preset but absent from `ProjectModel.CustomProfiles`.

**Risk Assessment:** **HIGH** until the save-time profile source and load-diagnostics contracts are specified.

## 04-04 Recovery Diagnostics

**Summary:** Good read-only diagnostics plan with the right non-blocking tone, but it should depend on 04-02 as well.

**Strengths**
- Preserves Phase 1 neutral fallback behavior.
- Enforces exact internal display-name matching.
- Accounts for embedded project copies.

**Concerns**
- **MEDIUM:** `depends_on` omits 04-02, but tests and behavior mention locally available custom profiles in the catalog.
- **MEDIUM:** Dedupe with Phase 3 diagnostics could become brittle if implemented as filtering rather than a shared diagnostic code/model.
- **LOW:** Exact message text tests may create unnecessary churn.

**Suggestions**
- Add `04-02` to dependencies.
- Prefer diagnostic codes/action kinds in tests, with only one or two copy smoke assertions.
- Make fallback calculation source reuse existing profile fallback logic rather than duplicate it.

**Risk Assessment:** **MEDIUM**. Behavior is clear, integration with existing diagnostics is the main risk.

## 04-05 Profile Manager And Editor ViewModels

**Summary:** Solid App-layer plan, though it tries to build a lot before the shell UI exists.

**Strengths**
- Uses ReactiveUI commands and service boundaries.
- Keeps file I/O out of ViewModels.
- Correctly gates save on Core validation and keeps bundled profiles read-only.

**Concerns**
- **MEDIUM:** Building validation by converting structured editor rows back into JSON may add avoidable complexity.
- **MEDIUM:** Delete behavior for a custom profile currently referenced by the open project is not specified.
- **LOW:** The generic save-failure message conflates validation blockers with I/O failures.

**Suggestions**
- Add a Core validation overload for an in-memory profile candidate, then have JSON import call into the same rules.
- Add delete tests for profile is referenced by current project with explicit confirmation and resulting recovery diagnostics.
- Separate validation failures from store/write failures in user-facing status.

**Risk Assessment:** **MEDIUM**. Mostly manageable, but the editor should not grow its own parallel validation model.

## 04-06 Conflict Handling And Recovery Actions

**Summary:** This is the most important and highest-risk plan. It covers the right behaviors, but it is too dense for one execution unit and has transactional hazards.

**Strengths**
- Correctly refuses silent embedded/local conflict resolution.
- Defines cancel-before-mutation semantics.
- Includes undo-aware remap and project-scoped embedded copy behavior.

**Concerns**
- **HIGH:** Multiple-conflict flow says cancellation leaves no app state mutation, but store writes could occur after earlier decisions unless all decisions are collected before any write.
- **HIGH:** `Use Project Copy` can conflict with duplicate-name rejection if local and embedded profiles share a name but differ. Overlay precedence must be explicitly supported.
- **HIGH:** Embedded profiles that use bundled display names are not addressed. They should not be allowed to shadow bundled profiles.
- **MEDIUM:** Recording undo during project open for `Rename Project Copy` may fight normal open project clears undo stack behavior.
- **MEDIUM:** `Replace Local Profile` needs rollback or all-or-nothing behavior if a later profile write fails.

**Suggestions**
- Split into two plans: project-open conflict transaction, then diagnostics/recovery commands.
- Collect all conflict decisions first, validate all renamed names, then apply local-store writes and project mutation.
- Define project overlay semantics as project-scoped profile overrides local for this project without entering the global duplicate set, or choose another explicit rule.
- Add tests for embedded profile name colliding with bundled profile names.
- Decide whether rename-on-open should mark dirty without adding undo, or add undo after clearing the normal open stack.

**Risk Assessment:** **HIGH**. The requirements are right, but transaction ordering and overlay identity need tightening before implementation.

## 04-07 Profiles Workspace UI

**Summary:** Good final integration plan with a useful human checkpoint. Most risk depends on whether 04-05/04-06 expose clean bindings.

**Strengths**
- Requires compiled bindings and automation names.
- Adds source labels as text, not color-only state.
- Includes Templates-to-Profiles navigation and headless UI coverage.

**Concerns**
- **MEDIUM:** `INavigationService` can create a circular dependency if implemented by directly depending on `MainWindowViewModel`.
- **LOW:** Global search behavior is more detailed than the core phase goal and could distract if the shell is already complex.
- **LOW:** MainWindow AXAML may become too large; a dedicated Profiles view could reduce binding complexity.

**Suggestions**
- Implement navigation as a small shell-owned service or observable request, not a hard VM cycle.
- Consider extracting a `ProfileManagerView` once the Profiles tab grows beyond simple layout.
- Keep the human checkpoint, but run full `dotnet test` after any UI fixes from that checkpoint.

**Risk Assessment:** **MEDIUM**. UI integration is broad, but the plan has good verification gates.

## Phase-Level Recommendations

- Add an explicit save/load profile-definition contract before starting 04-03.
- Add `04-02` as a dependency for 04-04.
- Split 04-06 or at least add a pre-mutation decision-collection step and rollback policy.
- Define project-scoped overlay behavior so it can coexist with duplicate-name rejection.
- Add tests for bundled-name collision, referenced local custom profile embedding, duplicate custom files during discovery, and delete-currently-referenced custom profile.

Overall phase risk: **MEDIUM-HIGH**. The architecture is pointed in the right direction and should achieve Phase 4, but 04-03 and 04-06 need tightening before execution to avoid subtle profile trust and project-sharing regressions.

---

## Consensus Summary

All three reviewers agree that the Phase 4 plan set is directionally sound, well decomposed, and aligned with the locked custom-profile decisions. They consistently praised the Core-first sequencing, strict validation before catalog inclusion, source-tagged trust domains (`Bundled`, `LocalCustom`, `EmbeddedProject`), non-blocking missing-profile recovery, and preservation of byte-sensitive generation/export behavior.

The consensus risk is concentrated in integration boundaries rather than core concept quality. Plan 04-06 is the dominant concern across reviewers because project-open conflict handling, project-scoped overlays, local-store writes, remap/rename behavior, and undo/dirty semantics interact in ways that can silently mutate profile trust state if ordering is wrong. Plan 04-03 is the second major concern because embedded profile save/load behavior needs a precise contract for referenced local custom definitions and load diagnostics. Plan 04-02 also needs a sharper runtime catalog refresh contract so custom profile imports/edits take effect in existing ViewModels without requiring an app restart.

### Agreed Strengths

- Core validation and export are staged before App storage, project embedding, and UI workflows.
- Profile identity is correctly based on internal display name, not filename or fuzzy matching.
- Bundled profile trust is protected through read-only source tagging and duplicate-name rejection.
- Project embedding is optional and intended to preserve legacy `.jbs2bg` fields and compatibility.
- Recovery is explicit, visible, and non-blocking rather than a silent fallback or warning-heavy workflow.
- The plans avoid sacred formatter/export/golden-file surfaces.

### Agreed Concerns

- **HIGH - Tighten Plan 04-06 conflict transaction semantics.** The agent and Codex both flagged project-open conflict handling as high risk. Required clarifications include collecting all conflict decisions before any local-store writes, snapshotting local profiles, rollback or all-or-nothing behavior for `Replace Local Profile`, uniqueness rules for renamed project copies, and whether rename-on-open participates in undo after the open stack is cleared.
- **HIGH - Define Plan 04-03 save/load profile contracts.** The agent and Codex both called out missing details around how malformed embedded profiles surface diagnostics from `ProjectFileService.LoadFromString`, and Codex additionally flagged that saving only `ProjectModel.CustomProfiles` may omit referenced local custom profiles from AppData unless a resolver/copy-in contract is defined.
- **MEDIUM/HIGH - Resolve runtime catalog refresh and overlay semantics.** Gemini, the agent, and Codex all raised catalog lifecycle concerns: clearing project-scoped overlays on new/closed projects, avoiding stale singleton `TemplateProfileCatalog` references, deciding notification vs. explicit refresh, and ensuring `WithProjectProfiles` can coexist with duplicate-name rejection for project-scoped overrides.
- **MEDIUM - Strengthen deterministic serialization and validation details.** Reviewers asked for deterministic key ordering/newline behavior in `ExportProfileJson`, a clear byte-equality test mechanism for no-custom project saves, stable hash implementation details, deterministic custom file discovery order, and duplicate JSON-property tests.
- **MEDIUM - Specify diagnostics and recovery integration more mechanically.** The agent and Codex both warned that Phase 3/Phase 4 diagnostic dedup should use diagnostic codes/action kinds rather than brittle text filtering. Codex also noted Plan 04-04 should depend on 04-02 because it tests local custom profiles through the catalog.
- **MEDIUM - Clarify Profile Manager/editor refresh and validation cadence.** The agent and Codex both noted that Plan 04-05 needs a clear strategy for manager list updates after catalog refresh and should avoid repeatedly converting structured editor rows to JSON for validation on every keystroke.

### Divergent Views

- Gemini rated the overall plan risk **LOW** and approved execution, while the agent rated it **MEDIUM** and Codex rated it **MEDIUM-HIGH**. The difference is mainly how strongly they weigh Plan 04-06's transaction hazards and Plan 04-03's missing save/load contracts.
- Gemini uniquely emphasized clearing project-scoped overlays on new project creation and editor filtering for large slider tables.
- Codex uniquely recommended splitting Plan 04-06 into two plans: one for project-open conflict transactions and one for diagnostics/recovery commands.
- The agent uniquely recommended adding an OpenSpec capability spec update task and a `Code`/`Category` field to `DiagnosticFinding` for mechanical deduplication.
