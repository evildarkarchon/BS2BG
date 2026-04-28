# Phase 7: Replay Saved Strategies in Automation Outputs - Context

**Gathered:** 2026-04-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 7 closes the automation gap where CLI generation and portable bundle generation currently produce `morphs.ini` from whatever NPC assignments are already stored in memory instead of first replaying the saved deterministic `ProjectModel.AssignmentStrategy`. This phase is limited to Core/CLI/bundle automation behavior for BodyGen morph output. It must not add new assignment strategy types, change GUI strategy authoring semantics, alter template/BoS JSON slider math, modify byte-sensitive writers, or introduce App/Avalonia dependencies into Core or CLI code.

</domain>

<decisions>
## Implementation Decisions

### Replay Trigger And Scope
- **D-01:** Replay a saved assignment strategy automatically whenever an automation request includes BodyGen morph output and `ProjectModel.AssignmentStrategy` is present. CLI `generate --intent bodygen`, CLI `generate --intent all`, bundle BodyGen output, and bundle `all` output should replay; BoS-only automation should not replay because it does not generate `morphs.ini`.
- **D-02:** Do not require a new opt-in flag for saved strategy replay. Reproducibility must come from saved project data, so users who saved a strategy should not need to remember an extra CLI or bundle switch.
- **D-03:** If no saved assignment strategy exists, keep current automation behavior: generate morph output from the project's existing NPC/custom-target assignments.

### Bundle Project State
- **D-04:** Keep `project/project.jbs2bg` inside portable bundles as the original saved/source project state, with the saved strategy configuration intact. Do not rewrite that bundle project file to contain replayed NPC assignments just because outputs were generated.
- **D-05:** Replayed assignments used for generated `morphs.ini` should be request-scoped working state. Bundle generation must avoid mutating the caller's project model or serializing replay side effects back into the bundled project entry.

### Blocked NPC Policy
- **D-06:** If saved strategy replay leaves any NPC with no eligible preset, CLI and bundle BodyGen generation must fail before writing output files or zip entries. Treat these as automation blockers, not warnings, because partial or stale `morphs.ini` output would undermine reproducibility.
- **D-07:** Blocked strategy replay must not silently fall back to all presets and must not leave stale prior assignments in generated output. Existing Phase 5 strategy eligibility rules and random-provider abstractions remain authoritative.

### Replay Visibility
- **D-08:** Successful strategy replay should be visible as concise summary counts: strategy kind, assigned NPC count, and zero blocked rows. Do not print or bundle per-NPC assignment listings on successful paths by default.
- **D-09:** Failure paths must include actionable blocked-NPC details in CLI output and bundle preview/report text so users can identify which rule, race filter, weight, or bucket configuration needs repair before rerunning automation.

### the agent's Discretion
- Exact helper/seam names are flexible, but keep the orchestration Core-only and reusable between `HeadlessGenerationService` and `PortableProjectBundleService`.
- Exact wording of success summaries and failure details is flexible as long as scripts still receive stable nonzero exit codes on blocked replay and users can identify blocked NPC rows.
- Exact test fixture construction is flexible, but tests must prove output reproducibility from saved strategy data rather than pre-mutated assignments.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope And Requirements
- `.planning/PROJECT.md` — project value, parity constraints, Core/App separation, sacred output files, and completed automation/custom-profile state.
- `.planning/REQUIREMENTS.md` — `AUTO-02` and `AUTO-03` remain pending and map to Phase 7; automation must support portable bundles and deterministic saved strategy replay.
- `.planning/ROADMAP.md` — Phase 7 goal, dependency order, gap closure statement, and success criteria.
- `.planning/STATE.md` — current focus, accumulated Phase 5/6 decisions, and continuity notes.

### Prior Automation Decisions
- `.planning/phases/05-automation-sharing-and-release-trust/05-CONTEXT.md` — CLI contract, bundle shape, full assignment strategy menu, saved strategy persistence, race-filter semantics, no-eligible diagnostic requirement, and random-provider seam preservation.
- `.planning/phases/06-compose-custom-profiles-in-headless-generation/06-01-SUMMARY.md` — request-scoped catalog composition pattern and Phase 6 readiness for Phase 7.
- `.planning/phases/06-compose-custom-profiles-in-headless-generation/06-VERIFICATION.md` — verified headless/bundle catalog data flow and output-byte regression expectations.
- `.planning/phases/06-compose-custom-profiles-in-headless-generation/06-LEARNINGS.md` — patterns for one request-scoped object feeding validation and writers, direct edge-case tests before integration tests, and automation-specific blocking.

### Product And Capability Specs
- `PRD.md` — byte-sensitive output semantics, Core portability, `.jbs2bg` compatibility, and local/offline modder trust constraints.
- `openspec/specs/morph-assignment-flow/spec.md` — existing NPC/custom-target assignment behavior and morph generation constraints.
- `openspec/specs/project-roundtrip/spec.md` — `.jbs2bg` serialization compatibility relevant to keeping bundle project state as saved source data.
- `openspec/specs/template-generation-flow/spec.md` — template/BoS generation constraints that Phase 7 must not change.
- `openspec/specs/release-polish/spec.md` — portable bundle/release trust constraints and no private path leakage.

### Codebase Maps And Source Touchpoints
- `.planning/codebase/STACK.md` — .NET/C# stack, CLI/App/Core project boundaries, and test framework conventions.
- `.planning/codebase/ARCHITECTURE.md` — Core generation/export flows, project model ownership, writer boundaries, and anti-patterns.
- `.planning/codebase/CONVENTIONS.md` — C# style, diagnostics/result patterns, ReactiveUI constraints where App code is touched, and comment/docstring expectations.
- `src/BS2BG.Core/Automation/HeadlessGenerationService.cs` — CLI/headless orchestration currently validates and writes `morphs.ini` from existing assignments.
- `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` — bundle planning currently saves project data and generates BodyGen output from existing assignments.
- `src/BS2BG.Core/Morphs/AssignmentStrategyService.cs` — existing deterministic strategy execution, eligibility computation, stable NPC ordering, seed provider usage, and blocked-NPC result model.
- `src/BS2BG.Core/Morphs/MorphAssignmentService.cs` — existing provider-compatible strategy application seam.
- `src/BS2BG.Core/Diagnostics/ProjectValidationService.cs` — current read-only strategy eligibility finding surface; Phase 7 may need automation-blocking orchestration around it.
- `src/BS2BG.Cli/Program.cs` — CLI composition point that must remain App/Avalonia-free.
- `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs` — deterministic replay and provider-seam coverage.
- `tests/BS2BG.Tests/MorphsViewModelStrategyTests.cs` — GUI strategy behavior, full-project scope, and saved strategy UI expectations.
- `tests/BS2BG.Tests/CliGenerationTests.cs` — CLI/headless integration and output-byte regression patterns.
- `tests/BS2BG.Tests/PortableBundleServiceTests.cs` — bundle output byte, profile, privacy, and overwrite-safety regression patterns.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AssignmentStrategyService.Apply` and `ComputeEligibility` already execute saved strategies in stable NPC order, return blocked NPC rows, and use `DeterministicAssignmentRandomProvider` when a seed is present.
- `MorphAssignmentService.ApplyStrategy` preserves the Phase 5 random-provider seam and can inform any orchestration helper that applies saved strategies before morph generation.
- `ProjectValidationService.AddAssignmentStrategyFindings` already computes no-eligible strategy rows without mutating the project, useful for preflight and failure details.
- `HeadlessGenerationService.Run` already centralizes CLI project load, request-scoped profile catalog composition, validation, overwrite preflight, generation, writer calls, ledgers, and exit-code-oriented results.
- `PortableProjectBundleService.BuildPlan` already centralizes bundle validation, project serialization, profile entries, generated outputs, path scrubbing, manifests, checksums, and zip creation.
- `DiagnosticReportTextFormatter` already formats deterministic plain-text validation reports for CLI/bundle support artifacts without App dependencies.

### Established Patterns
- Automation paths should build request-scoped Core state once and pass that same state through validation and writers, matching the Phase 6 `RequestScopedProfileCatalogComposer` pattern.
- Core remains UI-free. Any replay helper should live in `BS2BG.Core` and be composed by CLI/bundle services without referencing `BS2BG.App`.
- Output writers remain authoritative for bytes. Phase 7 should feed replayed project state into existing `MorphGenerationService`/writers, not create alternate `morphs.ini` formatting.
- Automation blocks when silent wrong output would be produced. Phase 6 already blocked unresolved custom profiles before output writes; Phase 7 should apply the same trust posture to blocked strategy replay.
- In-process `Program.Main` tests must stay in the shared console-capture collection to avoid stdout/stderr races.

### Integration Points
- Insert saved-strategy replay before `MorphGenerationService.GenerateMorphs(project)` in `HeadlessGenerationService` for BodyGen/all intents.
- Insert saved-strategy replay before bundle BodyGen generation in `PortableProjectBundleService`, using a working copy or equivalent request-scoped model so `project/project.jbs2bg` remains original.
- Extend CLI/bundle results or reports with replay summary counts and blocked-NPC details while preserving existing exit-code mapping.
- Add regression tests where saved project assignments are intentionally stale or empty, then prove CLI and bundle `morphs.ini` bytes come from replayed strategy data.
- Add blocked-rule tests proving CLI and bundle refuse BodyGen output before any writes/zip entries when strategy rules leave NPCs ineligible.

</code_context>

<specifics>
## Specific Ideas

- The user selected automatic replay for BodyGen automation, not a required flag and not an opt-in workflow.
- The user selected original bundle project state: bundle outputs may reflect replayed working state, but `project/project.jbs2bg` remains the saved source project with its strategy configuration.
- The user selected fail-before-writing behavior for blocked NPCs, elevating strategy replay gaps from cautions to automation blockers for Phase 7 BodyGen paths.
- The user selected summary-count visibility on success and detailed blocked-NPC information only on failure paths.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within Phase 7 automation replay scope.

</deferred>

---

*Phase: 07-replay-saved-strategies-in-automation-outputs*
*Context gathered: 2026-04-28*
