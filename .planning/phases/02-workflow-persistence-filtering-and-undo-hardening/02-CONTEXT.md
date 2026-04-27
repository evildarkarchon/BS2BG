# Phase 2: Workflow Persistence, Filtering, and Undo Hardening - Context

**Gathered:** 2026-04-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 2 delivers safer daily workflow behavior for BS2BG users working across restarts and large NPC/preset datasets. The phase covers local workflow preference persistence, full NPC filtering with stable row identity, explicit bulk-operation scopes that protect hidden filtered rows, and hardened undo/redo for high-risk workflow mutations. It must not change slider math, Java-compatible formatting, BodyGen/BoS output semantics, golden expected fixtures, or Phase 1's locked neutral profile-fallback behavior.

</domain>

<decisions>
## Implementation Decisions

### Preference Persistence
- **D-01:** Persist `OmitRedundantSliders` as a local app preference, not as `.jbs2bg` project state. This restores the user's workflow across restarts without changing project serialization semantics or shared-project behavior.
- **D-02:** Remember separate workflow folders for project open/save, BodySlide XML import, NPC text import, BodyGen INI export, and BoS JSON export. Do not collapse these into one global folder.
- **D-03:** Preference load/save failures should silently fall back to defaults or best-effort behavior. Preferences are convenience state and must not block startup, import, generation, save, or export workflows.
- **D-04:** NPC search/filter state remains session-only. Do not restore filters across app restarts because stale filters can make rows appear missing.

### NPC Identity And Filtering
- **D-05:** Use a generated stable NPC row ID for UI identity, filtering, selection preservation, and undo hardening. Existing field-based values such as `Mod`, `EditorId`, and `FormId` remain domain/display/export data, not the sole UI identity key.
- **D-06:** Implement per-column NPC filtering as checklist filters with in-popup search for long value lists. Extend the existing race-filter/checklist direction to mod, name, editor ID, form ID, race, assignment state, and preset-related values.
- **D-07:** Keep hidden selected NPCs selected when filters change. Stable row IDs should preserve selection across filter changes, while explicit bulk-operation scopes protect users from accidental hidden-row mutation.
- **D-08:** Use debounced free-text filtering for large NPC datasets. Checklist changes may apply immediately, but text search should avoid rebuilding visible rows on every keystroke.

### Bulk Operation Scopes
- **D-09:** Expose bulk-operation scope through an explicit scope selector with `All`, `Visible`, `Selected`, and `Visible Empty` style choices rather than multiplying buttons or asking scope after every command.
- **D-10:** Default bulk operations to the visible row scope when filters are active. This aligns actions with the user's current filtered view and minimizes accidental hidden-row changes.
- **D-11:** Make `Visible Empty` the primary scope for random fill operations. Hidden NPCs and already-assigned visible NPCs should stay untouched unless the user explicitly chooses a broader scope.
- **D-12:** Require confirmation only for destructive all-scope operations, such as clearing/removing across all rows. Routine visible/selected edits should rely on explicit scope labels and undo/redo instead of frequent prompts.

### Undo Hardening
- **D-13:** Harden undo/redo with targeted value snapshots for risky operations rather than full project snapshots or serialized snapshots. Preserve the lightweight undo service shape while eliminating unsafe live-reference captures.
- **D-14:** Phase 2 must cover all high-risk operations named by WORK-04: preset operations, target operations, NPC assignment operations, import operations, clear operations, and profile operations.
- **D-15:** Bound undo/redo history for large datasets and surface a non-blocking status message when old entries are pruned. Avoid unbounded memory growth during long bulk-edit sessions.
- **D-16:** Record each bulk operation as one undoable user action, even when it changes many rows. Do not create one undo entry per affected row.

### the agent's Discretion
- Exact preference JSON property names and migration shape, as long as existing theme persistence remains compatible and D-01 through D-04 are honored.
- Exact generated row ID storage location, as long as it preserves stable UI identity without changing BodyGen/BoS output semantics.
- Exact debounce interval and filtering helper structure, as long as large-dataset responsiveness is tested.
- Exact undo history limit, as long as the limit prevents unbounded growth and pruning is communicated non-blockingly.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope And Prior Decisions
- `.planning/PROJECT.md` — Project value, parity constraints, sacred files, architecture constraints, and current validated requirements.
- `.planning/REQUIREMENTS.md` — Phase 2 requirement IDs `WORK-01` through `WORK-05` and out-of-scope boundaries.
- `.planning/ROADMAP.md` — Phase 2 goal, success criteria, dependency on Phase 1, and UI hint.
- `.planning/STATE.md` — Current phase state, recent Phase 1 decisions, and Phase 2 concern about Avalonia per-column filtering/performance research.
- `.planning/phases/01-profile-correctness-and-trust/01-CONTEXT.md` — Locked Phase 1 profile behavior; Phase 2 must not reintroduce warning, mismatch, inference, or experimental-label behavior.

### Product And Historical Workflow Context
- `PRD.md` §4.6 — User preference keys from the Java tool and desired local JSON preference direction.
- `PRD.md` — Broader parity checklist, workflow expectations, and compatibility risks for `.jbs2bg`, BodyGen INI, BoS JSON, and BodySlide XML.
- `openspec/changes/archive/2026-04-24-m6-ux-upgrades/design.md` — Prior decisions for command metadata, ViewModel-level undo/redo, column predicate filtering, and theme preference persistence.
- `openspec/changes/archive/2026-04-24-m6-ux-upgrades/tasks.md` — Historical M6 filter/theme/task coverage and existing test direction.

### Current Capability Specs
- `openspec/specs/reactive-mvvm-conventions/spec.md` — Required ReactiveUI/Avalonia ViewModel conventions for new App-layer work.
- `openspec/specs/template-generation-flow/spec.md` — Existing template import/profile/omit/preview/generation behavior constraints.
- `openspec/specs/morph-assignment-flow/spec.md` — Existing NPC/custom-target/assignment/morph generation behavior constraints.

### Codebase Maps And Research
- `.planning/codebase/ARCHITECTURE.md` — Layer responsibilities, state management, undo/redo service notes, and App/Core boundaries.
- `.planning/codebase/CONVENTIONS.md` — C# style, ReactiveUI patterns, comments/docstrings, tests, and error-handling conventions.
- `.planning/codebase/STRUCTURE.md` — Where new Core, App, service, ViewModel, AXAML, and test code belongs.
- `.planning/codebase/CONCERNS.md` — Known Phase 2 concerns: incomplete preferences, partial NPC column filtering, full-collection filter rebuilds, live-reference undo snapshots, and large-input risks.
- `.planning/research/SUMMARY.md` — Project-level research summary supporting the current roadmap.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/BS2BG.App/Services/UserPreferencesService.cs`: Existing local preference service; currently persists theme only and silently falls back on failures. Extend this path for D-01 through D-03.
- `src/BS2BG.App/Services/WindowFileDialogService.cs`: Project/export folder picker integration point for remembered project, INI export, and JSON export folders.
- `src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs`: BodySlide XML import picker integration point for remembered preset folder.
- `src/BS2BG.App/Services/WindowNpcTextFilePicker.cs`: NPC text import picker integration point for remembered NPC folder.
- `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`: Owns `OmitRedundantSliders`, preset/profile operations, imports, preview refresh, and several undo paths.
- `src/BS2BG.App/ViewModels/MorphsViewModel.cs`: Owns NPC import, search/filter state, visible NPC collections, selected NPCs, assignment commands, and many bulk undo paths.
- `src/BS2BG.App/Services/UndoRedoService.cs`: Existing closure-based undo/redo stack and replay guard; hardening should improve snapshots without replacing the whole pattern unless research proves necessary.
- `src/BS2BG.Core/Models/Npc.cs`: NPC model fields used by filters and morph generation. Add UI identity carefully so output semantics remain unchanged.
- `src/BS2BG.Core/Morphs/MorphAssignmentService.cs`: Existing assignment and de-dupe operations; planner should preserve Core/UI separation when adding explicit scopes.

### Established Patterns
- App ViewModels use ReactiveUI `[Reactive]`, `[ObservableAsProperty]`, `ReactiveCommand`, observable `canExecute`, and scheduler-aware derived state. Do not reintroduce relay commands or direct dispatcher calls in ViewModels.
- `BS2BG.Core` remains UI-free and portable. Filtering/scope helpers may be pure and testable, but Avalonia-specific state belongs in `BS2BG.App`.
- The shared `ProjectModel` is mutable and dirty-tracked; ViewModel mutations must keep dirty state and undo/redo in sync.
- Existing preferences are local machine state, not project state. Continue that split for `OmitRedundantSliders` per D-01.
- Existing NPC filtering rebuilds visible observable collections; Phase 2 should improve responsiveness and test large dataset behavior without changing generated output.
- Existing undo records frequently capture closures and live model instances; Phase 2 should replace risky captures with value snapshots for high-risk operations.

### Integration Points
- Preference hydration/save: `MainWindowViewModel`, `TemplatesViewModel`, `UserPreferencesService`, and picker services.
- Filtering UI and control wiring: `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Views/MainWindow.axaml.cs`, and `MorphsViewModel`.
- Scope-aware bulk operations: `MorphsViewModel` command definitions, selected/visible NPC collections, `MorphAssignmentService`, and undo recording.
- Undo hardening tests: `tests/BS2BG.Tests/MorphsViewModelTests.cs`, `tests/BS2BG.Tests/TemplatesViewModelTests.cs`, `tests/BS2BG.Tests/M6UxViewModelTests.cs`, `tests/BS2BG.Tests/MainWindowViewModelTests.cs`, `tests/BS2BG.Tests/UserPreferencesServiceTests.cs`, and `tests/BS2BG.Tests/ModelSubscriptionTests.cs`.

</code_context>

<specifics>
## Specific Ideas

- User chose local app preference persistence for `OmitRedundantSliders`; downstream agents should not plan a `.jbs2bg` schema change for that setting in Phase 2.
- User chose separate remembered folders for project, BodySlide XML import, NPC import, BodyGen INI export, and BoS JSON export.
- User chose generated stable NPC row IDs, checklist-plus-search per-column filters, hidden selection preservation, and debounced text search.
- User chose a scope selector with visible rows as the filtered default, visible-empty as the primary random-fill scope, and confirmations only for destructive all-scope operations.
- User chose targeted value snapshots, high-risk operation coverage, bounded undo history with status, and one undo entry per bulk action.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within Phase 2 workflow persistence, filtering, bulk-scope, and undo-hardening scope.

</deferred>

---

*Phase: 02-workflow-persistence-filtering-and-undo-hardening*
*Context gathered: 2026-04-26*
