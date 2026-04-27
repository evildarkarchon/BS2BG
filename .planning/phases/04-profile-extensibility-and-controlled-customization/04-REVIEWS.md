---
phase: 4
reviewers: [gemini, claude, codex]
reviewed_at: 2026-04-27T00:37:17-07:00
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

Gemini was invoked but did not produce a plan review.

```text
Warning: Skipping extension in C:\Users\evild\.gemini\extensions\logs: Configuration file not found at C:\Users\evild\.gemini\extensions\logs\gemini-extension.json
When using Gemini API, you must specify the GEMINI_API_KEY environment variable.
Update your environment and try again (no reload needed if using .env)!
```

---

## the agent Review

# Phase 4 Plan Review: Profile Extensibility and Controlled Customization

## Overall Assessment

The seven-plan structure is well-decomposed and faithfully maps the locked CONTEXT.md decisions (D-01 through D-17) onto code. Wave parallelization is sensible (04-01 -> {04-02, 04-03, 04-04} -> {04-05, 04-06} -> 04-07). The Core/App split is preserved, sacred files are untouched, and TDD-first plans (04-01 through 04-04) provide solid scaffolding. The main risks are concentrated in **04-06** (project open conflict ordering) and a few specification gaps around DTO shapes, normalization rules, and cross-plan integration that will likely surface during execution.

**Overall risk: MEDIUM** - plans are competent and goal-aligned but contain a few HIGH-impact ambiguities that could cause rework if not clarified before execution.

---

## Plan 04-01: Core validation and JSON export

### Summary

Solid TDD foundation. Defines `CustomProfileDefinition`, `ProfileValidationResult`, and `ProfileDefinitionService` with strict-but-permissive validation aligned to D-05/06/07/08.

### Strengths

- Clean Core-only contract; no App leakage
- Explicit RED tests for blank profiles, broad finite numerics, duplicate names/sliders
- Reuses existing `SliderProfile`/`SliderDefault`/`SliderMultiplier` for downstream generation compatibility
- Acceptance criteria are grep-checkable

### Concerns

- **MEDIUM** - Coexistence with `SliderProfileJsonService` is unspecified. The legacy service is still used by `TemplateProfileCatalogFactory.LoadRequiredProfile` for bundled JSON. Plan doesn't say whether `ProfileDefinitionService` replaces it, wraps it, or runs alongside. Risk of two parsers diverging on bundled vs custom JSON.
- **MEDIUM** - `Game` metadata is introduced for custom profiles but bundled profiles (e.g., `settings.json`) have no `Game` field today. How is `CustomProfileDefinition.Game` populated for bundled profiles when they're wrapped into `ProfileCatalogEntry` in 04-02? Defaults to empty? "Skyrim"? Unclear.
- **LOW** - Round-trip test (export -> re-validate -> equivalent definition) is mentioned in behavior but not in acceptance criteria. Add a `ExportProfileJson_RoundTrips` test.
- **LOW** - NaN/Infinity rejection is in `<action>` text but not in acceptance criteria. Add an explicit test.
- **LOW** - No spec on how `Defaults` table emits when blank - empty object `{}` or omitted? Round-trip with `SliderProfileJsonService.LoadFromString` will diverge if shapes differ.

### Suggestions

- Document the migration plan for `SliderProfileJsonService`: either deprecate it in 04-02 once the factory uses `ProfileDefinitionService.ValidateProfileJson` for required bundled files, or keep it for bundled-only and route custom-only through the new service. State which.
- Add an `ExportProfileJson_RoundTripIsStable` test asserting `Validate(Export(profile)).Profile == profile`.
- Specify `Game` field default for bundled profiles (recommend: `"Skyrim"` for Skyrim CBBE/UUNP, `"Fallout4"` for FO4 CBBE) and document in 04-02 factory wiring.

### Risk: **LOW**

---

## Plan 04-02: AppData storage + catalog composition

### Summary

Adds `IUserProfileStore` over `%APPDATA%/jBS2BG/profiles/`, source-tags catalog entries, and composes bundled + custom catalog at startup.

### Strengths

- Reuses `AtomicFileWriter` and `Environment.SpecialFolder.ApplicationData` patterns from `UserPreferencesService`
- Preserves existing `TemplateProfileCatalog` constructor signature for backward compatibility
- Resilient startup - bundled-only fallback when local profile folder is unreadable
- DI registration explicit

### Concerns

- **HIGH** - `TemplateProfileCatalogFactory` is currently a `static class` (`public static class TemplateProfileCatalogFactory`). The plan needs it to consume `IUserProfileStore` - that requires either making it non-static (breaks the existing `services.AddSingleton(_ => TemplateProfileCatalogFactory.CreateDefault())` registration in `AppBootstrapper.cs:46`) or passing the store as a parameter. Plan doesn't choose. **This is a blocking design question.**
- **MEDIUM** - Filename sanitization rules unspecified. What characters are stripped? Unicode? Length limits? Two custom profiles with names that sanitize to the same filename - what then? Recommend: hash-disambiguating suffix or rejection at save time.
- **MEDIUM** - Discovery surfaces "rejected-file diagnostics through the store result" but no consumer exists in this plan. Where do they appear? UI? Status message? If 04-05's manager is the consumer, the plan should declare that integration as an explicit downstream contract.
- **MEDIUM** - Catalog refresh after `SaveProfile`/`DeleteProfile` is not addressed. Catalog is currently a singleton built once at startup. Adding/removing custom profiles at runtime requires either rebuilding it or making it mutable. Plan needs a strategy.
- **LOW** - `ProfileCatalogEntry.IsEditable = SourceKind == LocalCustom` excludes `EmbeddedProject` entries from editability - but those are introduced in 04-03/04-06 for project-scope profiles. Confirm whether embedded entries surface in `Entries` here or only in `ProjectModel.CustomProfiles`.

### Suggestions

- Decide static vs instance factory before implementation. Instance class with constructor injection of `IUserProfileStore` and bundled `searchDirectories` is cleaner; update `AppBootstrapper` registration accordingly. Add a static `CreateDefault()` shim if existing tests need it.
- Add Task 4 (or extend Task 3): catalog refresh contract. Recommend: `TemplateProfileCatalog` becomes mutable behind `RefreshFromStore()` or expose `ICatalogService` over it that `ProfileManagerViewModel` calls after save/delete.
- Specify filename sanitization: `Path.GetInvalidFileNameChars()` stripped, lowercase, max 64 chars, `.json` suffix, collision-resolved with `-{hash8}`.

### Risk: **MEDIUM-HIGH** - the static-factory question is structurally important.

---

## Plan 04-03: Embedded custom profiles in `.jbs2bg`

### Summary

Adds optional `CustomProfiles` top-level array (order 3) to project file with referenced-only filtering on save.

### Strengths

- Correct `[JsonPropertyOrder(3)]` placement after legacy `MorphedNPCs`
- Preserves all legacy field names/order
- D-15 referenced-only filter is testable
- Acceptance criteria explicitly checks legacy fields are still present

### Concerns

- **HIGH** - Backward compatibility byte-stability test missing. The most important assertion is: *project without custom profiles produces byte-identical output before and after this change*. Plan says "preserves `isUUNP` and `Profile` fields" but doesn't require byte-equality with existing `v1-stale-project.expected.jbs2bg` golden, if one exists.
- **MEDIUM** - `EmbeddedProfileDto` shape is implied but not specified. Should the embedded JSON shape match the standalone profile JSON (uppercase `Defaults`/`Multipliers`/`Inverted`, lowercase `valueSmall`/`valueBig`) for symmetry, or use C#-flavored property naming? This affects whether users can copy-paste between standalone and embedded.
- **MEDIUM** - Validation of embedded profiles on load is unspecified. If embedded JSON is malformed, does load fail or proceed with diagnostics? Per D-09 should be non-blocking, but 04-03 doesn't address it (defers entirely to 04-06). State this explicitly.
- **MEDIUM** - `ProjectModel.CustomProfiles` mutation semantics unclear. "Observable or read-only-mutable" - but dirty tracking is critical. Editing an embedded profile through the editor: does it mark project dirty? What about `ChangeVersion`? Recommend: same `ObservableCollection<CustomProfileDefinition>` pattern as `SliderPresets` with `AttachCollection` wiring.
- **LOW** - Saved order of `CustomProfiles` array unspecified. Recommend ordering by `Name` (case-insensitive) for deterministic output, matching existing `SliderPresets.OrderBy(...)`.
- **LOW** - Legacy reader behavior with unknown `CustomProfiles` field - `System.Text.Json` ignores unknown properties by default, so old readers will simply drop the section. Worth a one-line note in tests.

### Suggestions

- Add explicit test: `Save_NoCustomProfiles_ProducesByteIdenticalOutputToV1Format()` using an existing golden fixture if available, or capture current output as the baseline.
- Specify `EmbeddedProfileDto` shape exactly. Recommend matching standalone profile JSON case for `Name`/`Game`/`Defaults`/`Multipliers`/`Inverted` so users can extract embedded -> save as standalone trivially.
- Add validation behavior on load to acceptance criteria: malformed embedded entries are dropped with a Core-side diagnostic, project still loads.

### Risk: **MEDIUM** - backward-compat test gap is the main concern.

---

## Plan 04-04: Recovery diagnostics service

### Summary

Read-only Core service producing `ProfileRecoveryDiagnostic` findings with three explicit action kinds.

### Strengths

- Read-only by design, preserving Phase 3 baseline
- Exact-match resolution helper enforces D-12 cleanly
- Action enum matches UI-SPEC verbatim

### Concerns

- **MEDIUM** - Overlaps with existing `ProfileDiagnosticsService.Analyze` and `ProjectValidationService.AddProfileFindings`. Both already emit "unbundled saved profile" findings for the same condition (preset profile name not in catalog). Without integration, the Diagnostics tab will show duplicate findings for the same missing profile. Plan should either (a) replace those existing findings, (b) deduplicate, or (c) explicitly distinguish "missing custom profile reference (recoverable)" from "unbundled saved profile (legacy/neutral)".
- **MEDIUM** - Should `depends_on` include `04-03`? Recovery semantics differ when embedded profile data is present in `ProjectModel.CustomProfiles`. If embedded profile matches the missing reference, the diagnostic message and available actions should change (e.g., "import from project copy" becomes one of the recovery options). Plan doesn't address embedded-aware recovery.
- **LOW** - Test for "no recovery diagnostic when profile is bundled" is implicit in Task 1 (`A project with no missing profile names produces no recovery diagnostics`) but should also cover "no diagnostic when locally available custom profile matches".
- **LOW** - How does this service interact with the legacy `isUUNP=true` projects whose profile name resolves through `ProjectProfileMapping.Resolve`? If the resolved name is in the catalog, no diagnostic. Worth an explicit test.

### Suggestions

- Add Task 3: integrate or supersede the existing "Unbundled saved profile" finding in `ProjectValidationService` to prevent duplicate findings. Either delete the old finding when this service runs, or filter it out.
- Add `depends_on: [04-01, 04-03]` and include an embedded-profile-aware code path: if `ProjectModel.CustomProfiles` contains an exact-match definition for a missing reference, the recovery diagnostic should call that out and offer "Use project-embedded copy" as an additional action.
- Acceptance criterion: existing `ProfileDiagnosticsServiceTests` and `ProjectValidationServiceTests` still pass - confirms no regression in Phase 3 behavior.

### Risk: **MEDIUM** - duplicate-finding risk and the missing 04-03 dependency are real.

---

## Plan 04-05: Profile manager + editor ViewModels

### Summary

ReactiveUI ViewModels for the Profile Manager workspace with strict validation gating and a `IProfileManagementDialogService` boundary.

### Strengths

- Faithful to ReactiveUI conventions (compiled, openspec-backed)
- Validation-gated `Save` via observable canExecute
- Bundled-vs-custom command availability enforced
- DI registration explicit

### Concerns

- **MEDIUM** - `IProfileManagementDialogService` interface members aren't specified. "import/conflict/save prompts" is listed but no methods are declared. Suggested minimum: `Task<string?> PickProfileImportFileAsync(CancellationToken)`, `Task<string?> PickProfileExportPathAsync(string suggestedFileName, CancellationToken)`, `Task<bool> ConfirmDeleteProfileAsync(string profileName, CancellationToken)`. Without this, the Window implementation in Task 1 is undefined.
- **MEDIUM** - `ProfileManagerViewModel` lifetime/instance and editor binding unclear. Is one `ProfileEditorViewModel` instance reused (rebound per selection) or transient per-edit? What happens with unsaved edits + selection change? Plan should specify (recommend: single editor with "Discard Unsaved Edits?" confirmation per UI-SPEC).
- **MEDIUM** - Catalog refresh after save/delete (link to 04-02 concern): if the catalog is a singleton, the manager needs a way to trigger refresh. Plan doesn't reference this.
- **MEDIUM** - Test coverage gaps: acceptance criteria check command names and copy strings but not behavior. Recommend tests for:
  - Save fails with IO error -> editor remains open with unsaved changes intact (per UI-SPEC)
  - Validation result transitions Valid -> Blocker -> Valid as user edits
  - Concurrent rapid edits don't lose validation state
- **LOW** - `Validate Profile` command remains enabled when there are unsaved edits - good. But spec doesn't say what `IsValid` reflects: last-saved or current-buffer state. Should be current-buffer.

### Suggestions

- Specify `IProfileManagementDialogService` interface explicitly in the `<interfaces>` block with method signatures.
- Add lifetime decision: `ProfileEditorViewModel` is owned by `ProfileManagerViewModel` and rebound on selection change with discard-confirmation guard.
- Add catalog-refresh contract: `ProfileManagerViewModel` calls a `RefreshCatalog()` method (added to 04-02 scope) after save/delete/import.
- Strengthen Task 2 acceptance with behavioral tests beyond grep checks.

### Risk: **MEDIUM** - solid skeleton but several integration details are punted.

---

## Plan 04-06: Conflict + recovery integration

### Summary

Wires conflict dialog into project open, exposes recovery actions in Diagnostics, and adds undo-aware preset profile remap.

### Strengths

- Explicit four-option conflict resolution matching UI-SPEC
- Undo integration for remap operations
- Non-blocking project open preserved
- Recovery actions match exact UI-SPEC labels

### Concerns

- **HIGH** - **Project open ordering is fragile.** Current `TryOpenProjectPathAsync` (`MainWindowViewModel.cs:414-426`) does `projectFileService.Load -> project.ReplaceWith -> SelectedPreset/Npc resets -> MarkClean`. Plan says "After ProjectFileService.Load... inspect... before writing to IUserProfileStore or changing local catalog state" - but `ReplaceWith` is itself a project-state mutation visible to all workspace ViewModels. The conflict dialog needs to fire **before** `ReplaceWith` if we want the user to opt out without a flash of "now you have the project loaded but not the profiles". State the exact sequence: `Load -> DetectConflicts -> if conflicts: prompt -> apply ReplaceWith -> optionally save embedded to UserProfileStore -> reset selections -> MarkClean`. If the user cancels mid-conflict-dialog, what state is the app in?
- **HIGH** - **"Compare normalized profile definitions, not raw JSON text" is not defined.** Float comparison tolerance? Order-insensitive `Inverted` comparison? Case-sensitive vs case-insensitive slider names within tables? This is the equality contract for D-16 conflict detection - a critical spec gap. Recommend: define `CustomProfileDefinition.IsDefinitionallyEqual(other)` in 04-01 with explicit semantics, or here with a public `ProfileDefinitionEquality` comparer.
- **MEDIUM** - `Use Project Copy` ("keeps embedded definition in project scope without overwriting local") needs a concrete implementation. The catalog is currently global. If the project temporarily exposes a profile that's not in the global catalog, generation needs to find it via project-scope lookup. Plan implies this works but doesn't trace through how `TemplateGenerationService` / `TemplateProfileCatalog.GetProfile` find the project-scoped entry. Either: (a) the catalog gets project-scoped overlay support, or (b) `ProjectModel.CustomProfiles` is queried first by generation. Both are non-trivial - needs a Task or design decision.
- **MEDIUM** - `Rename Project Copy` mutates both embedded data AND referenced project preset names. This is a project-state change. Plan doesn't say if it marks dirty or is undoable. Should be both.
- **MEDIUM** - Recovery action "Remap to Installed Profile" undo behavior: undoing should restore the previous (missing/unresolved) reference. The undo action then triggers the recovery diagnostic to reappear. Plan should test this round-trip.
- **MEDIUM** - Conflict dialog method signature in `IAppDialogService` not specified. Suggested: `Task<ProfileConflictDecision> PromptProfileConflictAsync(ProfileConflictRequest request, CancellationToken ct)` where `ProfileConflictDecision` carries resolution + optional renamed name.
- **LOW** - Multiple conflicts in one project - does the dialog show one at a time or batched? UI-SPEC implies one dialog per conflict.

### Suggestions

- Specify the exact project-open sequence as pseudocode in the plan (this is the highest-leverage clarification).
- Move `ProfileDefinitionEquality` definition into 04-01 (or a small Task here) with explicit rules: case-sensitive slider names, case-insensitive profile name (already enforced by identity), exact float bit-equality on `valueSmall/valueBig/multiplier`, set-equality on `Inverted`.
- Add an explicit task for project-scoped catalog lookup: either extend `TemplateProfileCatalog` with overlay or have generation services consult `ProjectModel.CustomProfiles` first.
- Add `Rename Project Copy` to the undo records list.
- Add multi-conflict handling spec (sequential dialogs with "Apply to all matching" optional).

### Risk: **HIGH** - this is the most architecturally consequential plan and has the most under-specified behavior.

---

## Plan 04-07: Shell UI integration

### Summary

First-class Profiles workspace tab with compiled bindings, accessibility, and a human-verify checkpoint.

### Strengths

- Adds `Profiles` to `AppWorkspace` enum cleanly
- Headless smoke tests for tab presence
- Human verification checkpoint protects UI quality
- Faithful UI-SPEC copy enforcement

### Concerns

- **MEDIUM** - Templates "Manage Profiles" navigation: `TemplatesViewModel` doesn't currently know about `MainWindowViewModel.ActiveWorkspace`. Plumbing the navigation requires either an event/observable on Templates that Main subscribes to, or injecting a lightweight `INavigationService` interface. Plan says "Add a navigation command or method" but doesn't specify the wiring. Recommend: introduce `INavigationService` with `NavigateTo(AppWorkspace workspace)`, register in DI, inject into `TemplatesViewModel`.
- **MEDIUM** - Global search behavior in Profiles workspace: the existing `MainWindowViewModel.ApplyGlobalSearchText` has a switch on `ActiveWorkspace`. Plan adds the case for Profiles "clears other workspace search text" but Profiles itself probably wants a search filter for profile names. Either implement it or explicitly defer.
- **MEDIUM** - Aggregate `IsAnyBusy` integration: plan says "include `Profiles.WhenAnyValue(x => x.IsBusy) in aggregate busy if the manager exposes busy state". But ProfileManagerViewModel busy state isn't defined in 04-05. Either add `[ObservableAsProperty] _isBusy` to ProfileManagerViewModel in 04-05 or skip this hookup in 04-07.
- **LOW** - Missing recovery state visualization in the tab - UI-SPEC mentions "Missing references" group at the bottom of the source rail. Headless test should assert that group binding exists, not just `Import Profile` button.
- **LOW** - Spacing token enforcement is non-testable; gate via human checkpoint is appropriate.

### Suggestions

- Define `INavigationService` (or equivalent) and wire into 04-07 Task 1 explicitly.
- Specify global-search behavior for Profiles workspace (recommend: filter local profile list by name).
- Confirm `IsBusy` exposure on `ProfileManagerViewModel` is in scope of 04-05 acceptance criteria.

### Risk: **LOW-MEDIUM**

---

## Cross-Plan Concerns

1. **Catalog mutability/refresh contract is missing across 04-02, 04-05, 04-06.** Custom profile add/delete/import/conflict-resolve all imply runtime catalog updates, but no plan owns this. Add a single explicit task in 04-02 or split into a new mini-plan.

2. **Profile-definition equality semantics for D-16 conflict detection are undefined.** This is the single highest-impact spec gap. Define in 04-01 or 04-06 before implementation.

3. **Project-scoped vs global catalog precedence** for "Use Project Copy" conflict resolution is undefined. Without it, 04-06 can't be implemented correctly.

4. **Schema versioning is absent.** Custom profile JSON has no `Version` field. Future profile-shape changes will break older readers. Recommend adding optional `"Version": 1` field now, validated as `1` if present, defaulted to `1` if absent. Cheap to add today, expensive to retrofit.

5. **`ProfileDiagnosticsService` / `ProjectValidationService` integration with new recovery service.** Risk of duplicate findings in Diagnostics workspace. Address explicitly in 04-04.

6. **Test data fixtures.** Plans reference test files like `ProjectFileServiceCustomProfileTests.cs` but don't enumerate JSON test fixtures. Recommend adding a `tests/fixtures/profiles/` directory in 04-01 with: `valid-named.json`, `valid-blank.json`, `malformed.json`, `duplicate-slider.json`, `nan-value.json`, `large-broad-numerics.json`.

---

## Summary Table

| Plan | Risk | Top concern |
|------|------|-------------|
| 04-01 | LOW | Coexistence with `SliderProfileJsonService` |
| 04-02 | MEDIUM-HIGH | Static factory -> instance migration |
| 04-03 | MEDIUM | Missing byte-identical backward-compat test |
| 04-04 | MEDIUM | Duplicate findings vs Phase 3 services; missing 04-03 dep |
| 04-05 | MEDIUM | Dialog interface unspecified; editor lifecycle |
| 04-06 | **HIGH** | Project-open ordering + equality semantics undefined |
| 04-07 | LOW-MEDIUM | Cross-VM navigation plumbing |

## Recommendation

**Proceed with revisions.** Address the HIGH concerns in 04-02 (static factory), 04-03 (backward-compat test), and 04-06 (open ordering + equality contract) before kicking off Wave 2. The MEDIUM concerns can be resolved during execution if the executor is briefed on them, but pre-resolving in plans will prevent rework.

Specifically, before executing Wave 2:

1. Pin the equality semantics for `CustomProfileDefinition` (suggest: add to 04-01).
2. Decide static-vs-instance for `TemplateProfileCatalogFactory` (suggest: instance, with `CreateDefault()` shim).
3. Add catalog refresh contract to 04-02.
4. Add `depends_on: [04-01, 04-03]` to 04-04 and document embedded-aware recovery.
5. Add backward-compat byte-identical test to 04-03.

---

## Codex Review

Codex was invoked but did not produce a plan review.

```text
Error loading config.toml: invalid type: sequence, expected struct AgentsToml
in `agents`
```

---

## Consensus Summary

Only the agent CLI returned a substantive review. Gemini and Codex were invoked but failed before reviewing, so there is no true multi-reviewer consensus to synthesize. The following summary captures the substantive external review plus the operational failures that prevented broader consensus.

### Agreed Strengths

No multi-reviewer agreement is available because only one external reviewer completed. The completed review found the seven-plan wave structure strong, the Core/App separation well preserved, and the TDD-first Core plans well aligned with Phase 4 locked decisions.

### Agreed Concerns

No concern was independently raised by 2+ successful reviewers. The highest-priority concerns from the completed review are:

- 04-06 needs a precise project-open conflict ordering sequence before implementation.
- 04-06 needs explicit normalized profile equality semantics for embedded/local conflict detection.
- 04-02 needs a catalog/factory refresh strategy because custom profiles are added, deleted, imported, and conflict-resolved at runtime.
- 04-03 should add a byte-identical backward-compatibility test for project saves without custom profiles.
- 04-04 should address duplicate findings between the new recovery diagnostics and existing Phase 3 profile diagnostics.

### Divergent Views

No divergent reviewer views are available. The failed reviewers should be rerun after configuring Gemini credentials and fixing Codex config if independent consensus is required before replanning.
