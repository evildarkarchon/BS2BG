# Phase 3: Validation and Diagnostics - Context

**Gathered:** 2026-04-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 3 delivers read-only diagnostics and previews that help users understand project readiness, profile behavior, NPC import effects, and export/save risk before committing risky changes to disk. It must not change slider math, Java-compatible output formatting, project serialization compatibility, bundled profile semantics, or Phase 1's neutral fallback rules.

</domain>

<decisions>
## Implementation Decisions

### Health Report Shape
- **D-01:** Add an explicit Diagnostics panel/tab in the existing app shell for the project health report. Avoid a modal-only or text-only implementation because users need to inspect findings while navigating Templates and Morphs data.
- **D-02:** Model finding severity as `Blocker`, `Caution`, and `Info`. Use risk/action language rather than generic `Error`/`Warning` labels where possible.
- **D-03:** Organize findings by workflow area, such as Project, Profiles, Templates, Morphs/NPCs, Import, and Export. Each finding still carries severity.
- **D-04:** Keep findings read-only, but allow navigation/selection to affected presets, targets, NPCs, or output areas where practical, plus a copyable report. Do not implement auto-fix actions in Phase 3.

### Profile Diagnostics Tone
- **D-05:** Show profile diagnostics only in the explicit Diagnostics panel/report. Do not add ambient warning banners or normal-workflow warning copy to template generation.
- **D-06:** Do not implement slider-name mismatch heuristics or likely profile mismatch scoring in Phase 3. This is an intentional override/narrowing of `DIAG-02`; downstream agents should satisfy the requirement through concrete diagnostics for coverage, unknown sliders, injected defaults, multipliers, inversions, and fallback state.
- **D-07:** Provide profile diagnostics as summary plus drilldown: coverage counts, unknown slider counts, injected defaults, multipliers, inversions, neutral fallback state, and expandable slider-level details.
- **D-08:** Represent unbundled saved profiles as neutral fallback details: saved profile name, calculation fallback profile, affected presets, and current fallback behavior. Do not mark this as a warning/error or block generation/export.
- **D-09:** Run profile diagnostics at whole-project scope by default, with selected-preset drilldown/filtering rather than selected-preset-only diagnostics.

### NPC Import Preview
- **D-10:** Add an optional preview path for NPC text import rather than forcing all imports through preview-first. The current direct import workflow may remain available, but preview mode must parse into a temporary result and avoid mutating the NPC database/project until the user commits.
- **D-11:** NPC import preview should show both a summary and a row-level table for parsed rows, invalid lines, duplicates, fallback-decoded files/rows, and rows that would be added.
- **D-12:** Preserve the current duplicate policy by default: skip duplicate NPCs and explain them. Preview should identify whether duplicates occur within the file or against existing database/project rows when that can be determined.
- **D-13:** Keep import effects and assignment effects distinct. File import preview should state that importing adds to the NPC database only; assignment-changing commands should get their own before/after counts when they commit rows to morphs.
- **D-14:** Show fallback charset decoding as a per-file caution in preview/status, including the encoding name from parser results so users can review possible mojibake before commit.

### Export Risk Preview
- **D-15:** Export preview should show exact target paths, whether each file will be created or overwritten, and a generated-output effect summary/snippet before disk writes. Full content preview is optional at the planner's discretion but not required as the default.
- **D-16:** Require confirmation only for overwrite/risk cases, such as existing target files or multi-file batch situations where partial-output risk should be acknowledged. Routine create-new exports should avoid unnecessary confirmation friction.
- **D-17:** Save/export failure reporting should use an outcome ledger that identifies which files were written, restored, skipped, or left untouched, and includes the original exception plus rollback/incomplete state where known.
- **D-18:** Preserve existing atomic pair/batch write semantics in Phase 3 and expose them better through preview/result diagnostics. Do not redesign export transactions or change byte-sensitive writer output behavior unless planning proves a minimal non-formatting result API is necessary.
- **D-19:** Project save should not gain export-style preview friction. Improve save failure diagnostics to report target/outcome details where known; keep normal save flow smooth.

### the agent's Discretion
- Exact Diagnostics panel placement, styling, and navigation affordances, as long as it stays inside the existing shell and follows Avalonia compiled-binding/ReactiveUI conventions.
- Exact report DTO names and service boundaries, as long as reusable validation logic remains UI-free where practical and App code owns presentation/navigation.
- Exact preview snippet/summary format for generated output, as long as target paths and create/overwrite effects are visible before risky writes.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope And Prior Decisions
- `.planning/PROJECT.md` — Project value, compatibility constraints, sacred files, architecture split, and active decisions.
- `.planning/REQUIREMENTS.md` — Phase 3 requirement IDs `DIAG-01` through `DIAG-05`; interpret `DIAG-02` through D-06 in this context.
- `.planning/ROADMAP.md` — Phase 3 goal, success criteria, dependency on Phase 2, and UI hint.
- `.planning/STATE.md` — Current phase state and accumulated decisions/concerns.
- `.planning/phases/01-profile-correctness-and-trust/01-CONTEXT.md` — Locked no ambient mismatch warnings, no profile inference, no FO4 experimental labels, and neutral fallback behavior.
- `.planning/phases/02-workflow-persistence-filtering-and-undo-hardening/02-CONTEXT.md` — Stable NPC row identity, filtering, explicit bulk scopes, and undo hardening decisions that diagnostics should build on.

### Product And Capability Specs
- `PRD.md` — Product parity expectations, Java-reference constraints, workflow context, and compatibility risks.
- `openspec/specs/reactive-mvvm-conventions/spec.md` — Required ReactiveUI/Avalonia ViewModel conventions for new App-layer work.
- `openspec/specs/template-generation-flow/spec.md` — Existing template import/profile/omit/preview/generation behavior constraints.
- `openspec/specs/morph-assignment-flow/spec.md` — Existing NPC/custom-target/assignment/morph generation behavior constraints.
- `openspec/specs/project-roundtrip/spec.md` — `.jbs2bg` compatibility constraints relevant to save diagnostics.

### Codebase Maps And Source Touchpoints
- `.planning/codebase/TESTING.md` — Test patterns, fixture rules, and golden-file constraints.
- `.planning/codebase/CONVENTIONS.md` — C# style, ReactiveUI patterns, comments/docstrings, and error-handling conventions.
- `.planning/codebase/STRUCTURE.md` — Where Core, App, service, ViewModel, AXAML, and test code belongs.
- `.planning/codebase/CONCERNS.md` — Known risks around profile fallback, import parsing, atomic rollback, large inputs, and sacred formatter/export paths.
- `src/BS2BG.Core/Import/NpcTextParser.cs` — Current NPC parser, duplicate skip behavior, diagnostics, BOM/UTF-8/fallback decoding, and `EncodingName` source.
- `src/BS2BG.Core/Import/NpcImportResult.cs` — Existing parsed NPC, diagnostic, fallback, and encoding result shape.
- `src/BS2BG.Core/Import/BodySlideXmlParser.cs` — BodySlide XML parser/result pattern for recoverable diagnostics.
- `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs` — Profile lookup/fallback semantics and `ContainsProfile` detection.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs` — Neutral fallback UI state, selected calculation profile, missing default refresh, and slider inspector rebuild paths.
- `src/BS2BG.App/ViewModels/SetSliderInspectorRowViewModel.cs` — Existing per-slider preview rows that profile diagnostics can reuse conceptually.
- `src/BS2BG.App/ViewModels/MorphsViewModel.cs` — NPC import, status formatting, row/filter state, assignment effects, and bulk-scope workflows.
- `src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs` — Stable App-layer NPC row identity and filter-facing accessors.
- `src/BS2BG.App/ViewModels/Workflow/NpcFilterState.cs` — Session-only filter predicates and checklist value extraction.
- `src/BS2BG.App/ViewModels/Workflow/NpcBulkScopeResolver.cs` — Scope-to-row-ID snapshot behavior for assignment-effect previews.
- `src/BS2BG.App/ViewModels/MainWindowViewModel.cs` — Save/export command flows and current status-message failure handling.
- `src/BS2BG.Core/IO/AtomicFileWriter.cs` — Current atomic single/pair/batch write and rollback behavior to expose, not broadly redesign.
- `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs` — BodyGen INI export paths and CRLF output constraints; sacred output behavior.
- `src/BS2BG.Core/Export/BosJsonExportWriter.cs` — BoS JSON file naming, batching, LF/no-trailing-newline output constraints; sacred output behavior.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `NpcImportResult` already exposes parsed NPCs, diagnostics, `UsedFallbackEncoding`, and `EncodingName`, giving preview code a parser result foundation.
- `NpcTextParser` already detects invalid rows, skips duplicates by mod/editor ID, and detects BOM/strict UTF-8/fallback encoding. It does not currently surface duplicate diagnostics, so preview planning should account for that gap.
- `TemplateProfileCatalog.ContainsProfile` and `TemplatesViewModel.RefreshProfileFallbackInformation` already distinguish bundled profiles from neutral fallback behavior without changing saved profile names.
- `SetSliderInspectorRowViewModel.PreviewText` and `TemplateGenerationService.PreviewSetSlider` provide an existing per-slider preview concept for profile diagnostic drilldown.
- `NpcRowViewModel`, `NpcFilterState`, and `NpcBulkScopeResolver` provide stable row identity, filter predicates, and scope snapshots that diagnostics can use when pointing to affected NPC rows or assignment effects.
- `AtomicFileWriter`, `BodyGenIniExportWriter`, and `BosJsonExportWriter` already centralize export paths and atomic write behavior; diagnostics should wrap/preview/report around these paths without changing output text semantics.

### Established Patterns
- Core parser/import services return result objects with diagnostics for recoverable input issues instead of throwing for normal bad input.
- App ViewModels surface operational failures through user-visible `StatusMessage` and command `ThrownExceptions` subscriptions.
- New App-layer work must use ReactiveUI `[Reactive]`, `[ObservableAsProperty]`, and `ReactiveCommand` patterns; avoid reintroducing relay commands or direct dispatcher calls.
- `BS2BG.Core` remains UI-free. Validation/report DTOs can live in Core if they describe project state, but Avalonia navigation, panel state, and copy/report commands belong in App.
- Byte-identical export/formatting behavior is load-bearing. Do not change line endings, float formatting, ordering, filenames, or JSON trailing-newline behavior while adding diagnostics.

### Integration Points
- Diagnostics panel wiring: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, and likely a focused diagnostics ViewModel/service under `src/BS2BG.App/ViewModels/` or `src/BS2BG.App/Services/`.
- Project health validation: `ProjectModel`, `TemplateProfileCatalog`, `TemplatesViewModel`, `MorphsViewModel`, and Core generation/export readiness services.
- NPC import preview: `NpcTextParser`, `NpcImportResult`, `MorphsViewModel.ImportNpcFilesCoreAsync`, `AddNpcsToDatabase`, and duplicate comparison via `IsSameNpc`.
- Export preview/outcome reporting: `MainWindowViewModel.ExportBodyGenInisAsync`, `MainWindowViewModel.ExportBosJsonAsync`, `SaveProjectInternalAsync`, `BodyGenIniExportWriter`, `BosJsonExportWriter`, and `AtomicFileWriter`.
- Tests: add focused Core result/validator tests plus ViewModel tests in `tests/BS2BG.Tests/`; use FluentAssertions and avoid editing golden expected fixtures.

</code_context>

<specifics>
## Specific Ideas

- The user explicitly chose no likely-profile-mismatch checks for Phase 3, despite `DIAG-02` wording. Treat this as a locked context override and avoid slider-name mismatch scoring.
- NPC import preview is optional, not mandatory for every import. Direct import may remain, but the preview command must be no-mutation until commit.
- Import and assignment effects should be represented separately so diagnostics do not imply file import itself assigns presets.
- Export preview should be practical rather than full-file-by-default: paths, create/overwrite effects, and concise generated-output summaries/snippets are enough unless planning identifies a low-friction full-view affordance.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within Phase 3 validation and diagnostics scope.

</deferred>

---

*Phase: 03-validation-and-diagnostics*
*Context gathered: 2026-04-26*
