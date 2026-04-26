# Phase 1: Profile Correctness and Trust - Context

**Gathered:** 2026-04-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 1 delivers explicit bundled profile semantics for Skyrim CBBE, Skyrim UUNP, and Fallout 4 CBBE while preserving legacy `.jbs2bg` project compatibility. The phase fixes the current FO4 profile reuse problem, keeps profile-dependent generation/export behavior testable, and avoids expanding into custom profile management, body-mod calibration tooling, or broader workflow persistence.

</domain>

<decisions>
## Implementation Decisions

### FO4 Profile Shape
- **D-01:** Add a separate bundled root-level Fallout 4 CBBE JSON profile file alongside the existing `settings.json` and `settings_UUNP.json`; do not move profiles into a `profiles/` folder in Phase 1.
- **D-02:** Seed the FO4 profile from known FO4 CBBE slider names documented in `PRD.md`, with `valueSmall`/`valueBig` defaults at `1.0`, multipliers at `1.0`, and no inverted sliders.
- **D-03:** Keep the existing Skyrim profile files exactly where they are during Phase 1. Broader profile-folder migration/custom profile layout belongs to Phase 4.
- **D-04:** Make Fallout 4 CBBE always available in the profile selector; do not hide it behind an experimental toggle.

### Warning And Confidence Behavior
- **D-05:** Do not add general warnings for unprofiled or custom body mods. BodyGen files can encompass many body mods that BS2BG does not profile, so slider-name mismatch warnings would create false pressure and noise.
- **D-06:** Do not label Fallout 4 CBBE as experimental in the main workflow, selector, or warning UI. The user explicitly chose not to surface FO4 calibration confidence in-app for Phase 1.
- **D-07:** If a saved project references a profile name that is not currently bundled, preserve the original profile name for round-trip compatibility and use neutral informational fallback text only when generation would otherwise silently use bundled fallback math.
- **D-08:** Treat the roadmap wording around warnings/experimental FO4 status as constrained by these decisions. Downstream agents should not implement modal warnings, warning banners, mismatch heuristics, or FO4 experimental labels unless a later spec explicitly reverses this context.

### Profile Inference
- **D-09:** Imported BodySlide XML presets use the profile currently selected by the user. Do not infer profile from file path, game folder, or slider-name overlap.
- **D-10:** Do not implement likely-mismatch detection from slider names in Phase 1. Custom and unprofiled body mods may intentionally use unknown or overlapping sliders.
- **D-11:** Legacy projects without a `Profile` field continue to map through `isUUNP`: `true` means Skyrim UUNP, `false` means Skyrim CBBE, with no prompt on open.
- **D-12:** If a project contains an unbundled profile name, preserve the name for save/load and visibly use the fallback calculation profile until the user changes it. The fallback text should be neutral, not a warning.

### Trust Evidence
- **D-13:** The minimum Phase 1 proof for FO4 is distinct-table test coverage: tests must prove FO4 loads from its own JSON and never shares Skyrim CBBE or Skyrim UUNP defaults, multipliers, or inverted-slider tables.
- **D-14:** Do not alter or regenerate existing Java-reference golden expected files under `tests/fixtures/expected/**` for Phase 1. Add focused C# tests/fixtures instead of rebasing sacred golden outputs.
- **D-15:** ViewModel/UI coverage should include profile selection updates, imports using the selected profile, unresolved saved profile preservation, and visible neutral fallback information.
- **D-16:** Add tests asserting custom/unprofiled body-mod slider names do not produce mismatch warnings or block generation. Only unresolved profile fallback should produce neutral info.

### the agent's Discretion
- Exact FO4 profile file name, as long as it is a distinct root-level bundled JSON file and clearly maps to `ProjectProfileMapping.Fallout4Cbbe`.
- Exact neutral fallback message wording and placement, as long as it is not framed as a warning and does not block generation/export.
- Exact test class names and fixture helper structure, following existing xUnit v3 and FluentAssertions patterns.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope And Requirements
- `.planning/PROJECT.md` — Project value, constraints, sacred files, and existing decisions around parity and compatibility.
- `.planning/REQUIREMENTS.md` — Phase 1 requirement IDs `PROF-01` through `PROF-05`; apply the warning-related decisions in this context when interpreting `PROF-03` and `PROF-05`.
- `.planning/ROADMAP.md` — Phase 1 goal, success criteria, and dependency ordering.

### Profile And Fixture Requirements
- `PRD.md` §4.5 — Legacy two-profile behavior, named profile selector direction, and `.jbs2bg` `isUUNP`/`Profile` compatibility rules.
- `PRD.md` §7.7 — FO4 slider namespace seed guidance; use known FO4 CBBE sliders, defaults `1.0`, multipliers `1.0`, inverted empty.
- `PRD.md` §9a — Real-world BodySlide input observations, especially FO4/SSE slider-name overlap and Mod Organizer path constraints.
- `tests/fixtures/README.md` — Fixture corpus policy and golden-file regeneration constraints.
- `tests/fixtures/inputs/minimal/MATH-WALKTHROUGH.md` — Source of truth for formatter math expectations if Phase 1 touches slider math tests.

### Current Capability Specs
- `openspec/specs/template-generation-flow/spec.md` — Existing import/profile selection/template preview/generation requirements.
- `openspec/specs/project-roundtrip/spec.md` — Existing `.jbs2bg` `isUUNP` and `Profile` compatibility requirements.
- `openspec/specs/reactive-mvvm-conventions/spec.md` — App-layer ReactiveUI and Avalonia ViewModel conventions for any UI work.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/BS2BG.Core/Models/ProjectProfileMapping.cs`: Central constants for `Skyrim CBBE`, `Skyrim UUNP`, and `Fallout 4 CBBE`, plus legacy `isUUNP` mapping.
- `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs`: Existing catalog lookup and default fallback behavior. Planner should account for the current silent fallback and make unresolved fallback visible but neutral.
- `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs`: Current factory registers FO4 CBBE by reusing `settings.json`; Phase 1 must replace that with a distinct bundled FO4 JSON load.
- `src/BS2BG.Core/Serialization/ProjectFileService.cs`: Already loads `Profile` before falling back to `isUUNP` and emits both fields on save.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`: Imports assign `SelectedProfileName` to imported presets, profile changes refresh missing defaults, preview, BoS JSON, and inspector rows.
- `tests/BS2BG.Tests/TemplatesViewModelTests.cs` and `tests/BS2BG.Tests/TemplateGenerationServiceTests.cs`: Existing patterns for profile selection, missing-default refresh, fixture generation, and profile catalog tests.

### Established Patterns
- Core profile/generation logic stays in `BS2BG.Core`; Avalonia UI and user-facing messages stay in `BS2BG.App` ViewModels/services.
- Profile selection is per preset, and generated template text is cleared/recomputed when profile-dependent state changes.
- Tests use xUnit v3, FluentAssertions, hand-written fakes, and real Core services where deterministic.
- Golden Java-reference expected files are sacred; do not modify them to make FO4 profile changes pass.

### Integration Points
- Startup/profile catalog composition: `TemplateProfileCatalogFactory.CreateDefault` and app package content entries need the new FO4 JSON included.
- Template generation and BoS preview: `TemplateGenerationService` consumes profile catalogs by preset `ProfileName`.
- Project round-trip: `ProjectFileService` and `SliderPreset.ProfileName` preserve named profiles and legacy `isUUNP` behavior.
- Templates UI: profile selector bindings, status text, and preview refresh paths in `TemplatesViewModel` are the likely place for neutral fallback visibility.

</code_context>

<specifics>
## Specific Ideas

- User correction: BodyGen files encompass many body mods, not all of which are profiled, so Phase 1 should not add warnings for unknown/custom body-mod slider data.
- Fallout 4 CBBE should be available as a normal selectable profile without an experimental label in the main workflow.
- Fallback for a missing bundled profile should be visible and neutral, preserving the saved profile name rather than silently rewriting it.

</specifics>

<deferred>
## Deferred Ideas

- `profiles/` folder migration and custom profile management — Phase 4: Profile Extensibility and Controlled Customization.
- Authoritative Fallout 4 calibration assistant or known-good community calibration workflow — v2 advanced modding requirement unless explicitly pulled forward.
- Any heuristic profile detection from file paths or slider names — deferred unless a later phase explicitly revisits it with false-positive handling.

</deferred>

---

*Phase: 01-profile-correctness-and-trust*
*Context gathered: 2026-04-26*
