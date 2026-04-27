# Phase 4: Profile Extensibility and Controlled Customization - Context

**Gathered:** 2026-04-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 4 delivers custom local profile management for BS2BG users: importing, creating, validating, editing, exporting, embedding, and recovering profile definitions without corrupting bundled profiles, silently falling back, or breaking legacy `.jbs2bg` compatibility. It must not change slider math, byte-sensitive export formatting, Phase 1 neutral fallback tone, or Phase 3 diagnostics' read-only default behavior except where explicit profile-management actions resolve custom-profile state.

</domain>

<decisions>
## Implementation Decisions

### Profile Storage And Identity
- **D-01:** Store editable custom profile JSON files in a user profiles folder, not beside bundled install files by default. This keeps release/package files read-only and protects bundled profile trust.
- **D-02:** Treat bundled profiles as read-only. Users must copy a bundled profile into a custom profile before editing metadata or slider tables.
- **D-03:** Reject duplicate profile display names case-insensitively across bundled and custom profiles. Do not allow custom profiles to shadow bundled profile names.
- **D-04:** Discover custom profiles automatically from the local user profiles folder and also provide explicit import/copy actions. Local folder discovery is the primary runtime source for reusable custom profiles.

### Authoring Rules
- **D-05:** Custom profile editing covers metadata and all current slider tables: profile name/game-style metadata plus `Defaults`, `Multipliers`, and `Inverted` entries.
- **D-06:** Validation is strict before import/save/catalog inclusion. Reject malformed schema, blank or duplicate slider names, nonnumeric values, ambiguous profile names, and otherwise invalid profile data rather than partially accepting a broken profile.
- **D-07:** Permit broad numeric float values instead of clamping to normal 0-1 defaults or positive-only multipliers. Validation should reject nonnumeric/malformed values, but researcher/planner should preserve modder flexibility for unusual body mods and only add diagnostics for extreme values if useful.
- **D-08:** Allow users to create a blank custom profile. This is explicitly accepted despite the risk of missing-default-heavy output; downstream agents should make validation and UI state clear rather than forbidding empty starting points.

### Missing Custom Profile Recovery
- **D-09:** If a project references a custom profile that is not installed, open the project and show a resolvable diagnostic with actions. Do not block project open.
- **D-10:** Preserve Phase 1 non-blocking behavior for unresolved profiles: profile-dependent preview/generation/export may continue with visible fallback calculation rules, but the unresolved state must not be silent.
- **D-11:** Recovery actions should include importing a matching profile JSON, remapping/adopting an installed or bundled profile, or intentionally keeping the unresolved reference with visible fallback until later.
- **D-12:** Imported profiles resolve unresolved project references only by exact profile display-name match, case-insensitively. Do not use filename stems or fuzzy matching as identity.

### Sharing And Project Embedding
- **D-13:** Embed referenced custom profile data into `.jbs2bg` project files for sharing rather than relying only on sidecar JSON files.
- **D-14:** Preserve older-reader compatibility by adding an optional custom-profiles section while keeping the existing `SliderPresets`, `CustomMorphTargets`, `MorphedNPCs`, `isUUNP`, and `Profile` fields intact.
- **D-15:** Include only custom profiles referenced by project presets when saving/sharing embedded profile data. Do not embed unrelated local custom profiles.
- **D-16:** When loading a project with embedded custom profile data that conflicts with an existing local profile of the same name, prompt the user to import/replace/rename or keep the local profile. Do not silently let either project or local data win.
- **D-17:** Include a separate export-profile action for selected custom profiles as JSON. This complements embedded project sharing without expanding into Phase 5's full portable project bundle.

### the agent's Discretion
- Exact user profiles folder path and filename convention, as long as editable profiles are local user data and bundled profiles remain protected.
- Exact UI placement for profile management, as long as missing-profile recovery is visible through diagnostics/profile UI and normal profile fallback copy stays neutral.
- Exact optional `.jbs2bg` custom-profiles section name and DTO shape, as long as legacy fields remain unchanged and older consumers can ignore the new section.
- Exact warning/diagnostic wording for strict validation and extreme numeric values, as long as malformed/ambiguous profile data is rejected before catalog inclusion.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope And Prior Decisions
- `.planning/PROJECT.md` — Project value, parity constraints, sacred files, architecture constraints, and active decisions.
- `.planning/REQUIREMENTS.md` — Phase 4 requirement IDs `EXT-01` through `EXT-05`; preserve compatibility and custom-profile recovery requirements.
- `.planning/ROADMAP.md` — Phase 4 goal, success criteria, dependency on Phase 3, and UI hint.
- `.planning/STATE.md` — Current state and Phase 4 concern that custom profile schema/version rules need focused design.
- `.planning/phases/01-profile-correctness-and-trust/01-CONTEXT.md` — Locked neutral fallback behavior, no profile inference, no ambient mismatch warnings, and deferred custom profile management.
- `.planning/phases/02-workflow-persistence-filtering-and-undo-hardening/02-CONTEXT.md` — Local preference patterns, undo expectations, and non-blocking convenience-state failure handling.
- `.planning/phases/03-validation-and-diagnostics/03-CONTEXT.md` — Diagnostics panel/read-only baseline, profile diagnostics tone, and neutral fallback drilldown decisions.

### Product And Capability Specs
- `PRD.md` §4.5 — Profile JSON shape, N-profile direction, profile folder concept, `Name`/`Game` metadata, and `.jbs2bg` `Profile`/`isUUNP` compatibility rules.
- `PRD.md` §4.6 — Local preference storage context relevant to choosing user-local profile storage.
- `PRD.md` §7.7 — FO4 profile seed constraints and rationale for profile customization without claiming authoritative calibration.
- `openspec/specs/reactive-mvvm-conventions/spec.md` — Required ReactiveUI/Avalonia ViewModel conventions for new App-layer work.
- `openspec/specs/template-generation-flow/spec.md` — Existing profile selection, template preview, and generation behavior constraints.
- `openspec/specs/project-roundtrip/spec.md` — Existing `.jbs2bg` serialization compatibility and legacy field requirements.
- `openspec/specs/morph-assignment-flow/spec.md` — Existing morph/NPC assignment references that should keep working when profiles are embedded or recovered.

### Codebase Maps And Source Touchpoints
- `.planning/codebase/ARCHITECTURE.md` — Core/App separation, profile catalog, project serialization, and ViewModel orchestration boundaries.
- `.planning/codebase/INTEGRATIONS.md` — Local filesystem-only storage, profile JSON loading, project files, and user preference storage.
- `.planning/codebase/CONVENTIONS.md` — ReactiveUI patterns, parser/result validation conventions, comments/docstrings, and byte-parity constraints.
- `src/BS2BG.Core/Generation/SliderProfileJsonService.cs` — Current strict-ish JSON reader for `Defaults`, `Multipliers`, and `Inverted`; likely starting point for validation/import/export services.
- `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs` — Current name-based catalog lookup, case-insensitive matching, and default fallback behavior.
- `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs` — Current bundled profile composition and required profile loading from `AppContext.BaseDirectory`.
- `src/BS2BG.Core/Serialization/ProjectFileService.cs` — `.jbs2bg` DTOs, `Profile`/`isUUNP` round-trip behavior, property order, and atomic save path.
- `src/BS2BG.Core/Models/ProjectProfileMapping.cs` — Bundled profile constants and legacy `isUUNP` mapping.
- `src/BS2BG.Core/Diagnostics/ProfileDiagnosticsService.cs` — Existing profile coverage/fallback diagnostics and neutral informational fallback wording.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` — Profile selector behavior, neutral fallback information, missing-default refresh, and preview recalculation paths.
- `tests/BS2BG.Tests/TemplateProfileCatalogFactoryTests.cs` — Existing bundled profile tests and FO4 seed expectations.
- `tests/BS2BG.Tests/ProjectFileServiceTests.cs` — Existing project round-trip patterns that should be extended for optional embedded custom profile data.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SliderProfileJsonService` already reads the core profile JSON shape. Phase 4 should likely extend or wrap this with validation result types and export support rather than duplicating parsing rules.
- `TemplateProfileCatalog` already provides case-insensitive profile lookup, profile-name listing, default fallback, and `ContainsProfile`; custom profiles should integrate through this catalog without changing formatter semantics.
- `TemplateProfileCatalogFactory` is the current composition point for bundled profiles. It will need a user-profile source while preserving required bundled profile loading.
- `ProjectFileService` already emits both `isUUNP` and `Profile` for each preset. Optional embedded custom-profile data should extend this serializer without changing existing fields.
- `ProfileDiagnosticsService` already reports neutral fallback information and can be extended or complemented for unresolved custom-profile recovery diagnostics.
- `TemplatesViewModel` already separates saved unbundled profile names from fallback calculation profile adoption; profile-management UI should preserve that separation.

### Established Patterns
- Core services return result/diagnostic objects for recoverable user-data issues; use that pattern for profile validation and import diagnostics.
- `BS2BG.Core` remains UI-free. Profile schema validation, catalog model changes, and project serialization belong in Core; file pickers, dialogs, conflict prompts, and profile manager presentation belong in App.
- Existing fallback behavior is intentionally neutral and non-blocking. Phase 4 may add explicit recovery actions, but must not reintroduce noisy profile-inference or mismatch-warning behavior.
- Project serialization uses `System.Text.Json`, explicit DTOs, property order attributes, UTF-8 without BOM, and atomic writes. Embedded profile data should follow this style.
- New App-layer work must use ReactiveUI source-generator properties, `ReactiveCommand`, observable command gates, and compiled AXAML bindings.

### Integration Points
- Profile catalog composition: `TemplateProfileCatalogFactory`, DI registration in `AppBootstrapper`, and any new user-profile service.
- Profile authoring/import/export: new Core validation/import/export service plus App file-dialog/profile-manager commands.
- Missing profile recovery: `ProfileDiagnosticsService`, Diagnostics tab ViewModel/UI, `TemplatesViewModel.RefreshProfileFallbackInformation`, and project-open flow in `MainWindowViewModel`.
- Project embedding: `ProjectFileService` DTOs, save/load tests, and conflict resolution before embedded profiles become local custom profiles.
- Tests: extend Core profile JSON/validation tests, project round-trip tests, Template/Profile ViewModel tests, and Avalonia shell tests; do not edit sacred golden expected output fixtures.

</code_context>

<specifics>
## Specific Ideas

- The user intentionally chose embedded referenced custom profiles in `.jbs2bg` despite the sidecar recommendation. Downstream agents should design this as an optional, older-reader-safe section and keep the existing legacy fields intact.
- Blank custom profiles are allowed. Validation should distinguish an intentionally empty but well-formed profile from malformed/ambiguous profile data.
- Broad numeric values are allowed for modder flexibility. Do not clamp defaults or multipliers solely because common bundled values are 0-1 or 1.0.
- Conflict handling for embedded profiles must be explicit. Loading a shared project must not silently replace a local profile or silently ignore embedded profile data when the same profile name differs.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within Phase 4 profile extensibility scope. Full portable project bundles, CLI automation, release artifact verification, and broader share packages remain Phase 5.

</deferred>

---

*Phase: 04-profile-extensibility-and-controlled-customization*
*Context gathered: 2026-04-26*
