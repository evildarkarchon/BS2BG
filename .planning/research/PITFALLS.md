# Domain Pitfalls

**Domain:** BS2BG parity-sensitive desktop modding utility
**Researched:** 2026-04-26
**Focus:** Future work after initial Java parity has shipped
**Overall confidence:** HIGH for codebase-specific risks from project planning and audit documents; MEDIUM for general modding-utility workflow patterns inferred from BS2BG's domain constraints.

## Critical Pitfalls

Mistakes that can silently corrupt generated output, break existing projects, or erode modder trust.

### Pitfall 1: Treating Generated Output as "Just Text"

**What goes wrong:** A refactor or feature cleanup changes line endings, trailing newlines, numeric formatting, slider ordering, missing-default injection, profile lookup, or JSON number rendering. The app still builds and exports plausible files, but BodyGen/BoS behavior differs from the Java reference or existing BS2BG projects.

**Why it happens:** Developers normalize output through convenient .NET APIs (`System.Text.Json`, platform newline defaults, default `Math.Round`, `float.ToString`) without preserving the Java/minimal-json quirks that are part of the compatibility contract.

**Warning signs:**
- Changes touch `JavaFloatFormatting`, `SliderMathFormatter`, `BodyGenIniExportWriter`, `BosJsonExportWriter`, profile selection, or project serialization.
- A diff shows `.0` added/removed, CRLF/LF changes, reordered sliders/properties, or a new trailing newline.
- Review language says "equivalent", "cleaner", "standard JSON", or "formatting-only" around export code.
- Golden expected files are updated in the same change as production formatter/export code.

**Consequences:** Existing modder projects generate different in-game bodies; downstream tools fail on subtle formatting assumptions; parity failures are discovered only after release.

**Prevention strategy:**
- Keep formatter/export code isolated in Core and treat sacred files as compatibility surfaces.
- Require a parity note in every OpenSpec proposal that touches import/generation/export/serialization: either "no output impact" with test evidence or "intentional output change" with explicit migration rationale.
- Add new fixture coverage before changing behavior for new profiles or file types; do not widen behavior by editing expected fixtures first.
- Preserve the two formatter paths: text output uses Java-like float strings; BoS JSON uses minimal-json-like number strings.

**Future phase to address:** Any phase touching profiles, export, import, project serialization, formatter cleanup, CLI generation, or bulk automation.

**Test/review guardrail:**
- Run `dotnet test` with golden-file tests before merge.
- Review must explicitly inspect byte-level diffs for `templates.ini`, `morphs.ini`, BoS JSON, and `.jbs2bg` round trips.
- Block changes that modify `tests/fixtures/expected/**` unless the Java reference regeneration procedure and rationale are included.

### Pitfall 2: Shipping Valid-Looking Output With the Wrong Profile

**What goes wrong:** A project or imported XML uses the wrong slider profile, especially Fallout 4 CBBE using Skyrim CBBE defaults/multipliers/inverts. Exported files look syntactically valid, but values are semantically wrong for the user's game/body.

**Why it happens:** Legacy jBS2BG modeled profile selection as a CBBE/UUNP boolean, while future work needs named profiles, backward-compatible `isUUNP`, experimental FO4 support, and possible user-added profiles.

**Warning signs:**
- Unknown profile names silently fall back to the default profile with no visible warning.
- UI shows profile names but project save/load still reasons mainly in `isUUNP`.
- FO4 support is described as complete without a distinct `fallout4-cbbe.json` and fixture coverage.
- Profile selection state is cached globally or shared across presets.

**Consequences:** Modders spend time debugging BodyGen/LooksMenu output that appears to be their load order's fault; BS2BG loses trust because it generated "valid" but wrong output.

**Prevention strategy:**
- Add a distinct Fallout 4 CBBE profile file and load it through `TemplateProfileCatalogFactory`; stop reusing Skyrim CBBE settings for FO4.
- Keep compatibility fallback behavior for old projects, but surface load/generation warnings when a profile is missing or inferred.
- Make profile choice visible at the preset level and persist both `Profile` and legacy `isUUNP`.
- Treat FO4 calibration as experimental until validated with real fixture outputs and user feedback.

**Future phase to address:** Profile-system completion / Fallout 4 correctness phase.

**Test/review guardrail:**
- Add tests for unknown-profile warnings, `Profile` + `isUUNP` round trips, and FO4-specific fixtures.
- Review profile changes against PRD §4.5 and `.planning/codebase/CONCERNS.md` profile bug notes.

### Pitfall 3: Reintroducing UI Framework Drift During Avalonia/ReactiveUI Work

**What goes wrong:** New UI work compiles weakly or behaves differently at runtime because it bypasses Avalonia 12 compiled bindings, uses outdated APIs, reintroduces retired command types, calls `Dispatcher.UIThread` directly in ViewModels, or attaches window services inconsistently.

**Why it happens:** Avalonia tutorials and older code samples often target older APIs; BS2BG also recently shifted to ReactiveUI 23 conventions and source generators, so muscle-memory patterns are risky.

**Warning signs:**
- New AXAML lacks `x:DataType` on roots or `DataTemplate`s.
- `{ReflectionBinding ...}` appears without a documented reason.
- New ViewModel code uses `RelayCommand`, `AsyncRelayCommand`, `RaiseAndSetIfChanged`, direct `Task.Run`, or `Dispatcher.UIThread.InvokeAsync`.
- New services require a `Window` but are not covered by shell attachment tests.
- App works manually, but headless UI tests or `CanExecute` tests become flaky.

**Consequences:** Binding regressions ship silently, commands enable/disable at wrong times, busy state/cancellation diverges between tests and production, and UI changes become hard to review.

**Prevention strategy:**
- Follow `openspec/specs/reactive-mvvm-conventions/spec.md` for every new ViewModel and command.
- Keep Views plain Avalonia `Window`/controls unless a specific ReactiveWindow lifecycle need is documented.
- Route long-running work through ReactiveUI scheduler-aware commands/services instead of ad hoc `Task.Run`.
- Centralize window-scoped service attachment behind a testable shell contract.

**Future phase to address:** UI refactor, workflow improvement, package upgrade, or any phase adding views/dialogs/services.

**Test/review guardrail:**
- Build must fail on compiled-binding errors; add headless tests for new views/dialogs.
- Add/maintain AppShell tests that assert all window-scoped services are attached.
- Review new ViewModels for forbidden retired patterns before behavior review.

### Pitfall 4: Letting Monolithic ViewModels Accumulate More Workflow State

**What goes wrong:** Future features get appended to `TemplatesViewModel`, `MorphsViewModel`, `MainWindowViewModel`, and `MainWindow.axaml`, increasing cross-coupling between filtering, selection, undo, import/export, dirty state, generated text, and status messages.

**Why it happens:** The current implementation is functional and easier to extend locally than to decompose first. Small feature requests can look cheap until they interact with existing reactive subscriptions and mutable collections.

**Warning signs:**
- New feature requires editing many unrelated sections of a large ViewModel or `MainWindow.axaml`.
- A change to selection/filtering breaks generated output, undo/redo, or dirty-state tests.
- Code-behind gains more event handlers for business or workflow decisions.
- Reviewers cannot tell which component owns a state transition.

**Consequences:** Regression risk rises with every UI improvement; roadmap phases slow down because small workflow changes need broad retesting.

**Prevention strategy:**
- Extract focused services/helpers before adding adjacent behavior: filtering engine, assignment operations, undo snapshot builders, generated-output coordinator, command palette registry, import status formatter.
- Split the main AXAML into user controls (`TemplatesView`, `MorphsView`, `SetSliderInspectorView`, `CommandPaletteView`, `NpcFilterView`) during the next substantial UI phase.
- Keep ViewModels as binding surfaces that compose services, not as all-purpose workflow engines.

**Future phase to address:** UI maintainability/refactor phase before adding substantial workflow features such as full column filters, richer inspector, or multi-step import tools.

**Test/review guardrail:**
- Require regression tests around the extracted service before moving behavior.
- Review PR size and boundaries: if one feature touches both template and morph workflow state, require an explicit state-ownership note.

### Pitfall 5: Undo/Redo Captures Mutable Live State

**What goes wrong:** Undo records close over live `SliderPreset`, `Npc`, or `CustomMorphTarget` objects. Later edits mutate those objects, so an older undo/redo operation restores the wrong state or reintroduces stale references.

**Why it happens:** Capturing objects/closures is convenient for UI commands, especially during bulk operations, but BS2BG workflows involve rename, removal, assignment, import, clear, and generated-output side effects that can interleave.

**Warning signs:**
- Undo entries capture model instances instead of value snapshots.
- Tests cover single-step undo but not interleavings such as rename -> assign -> remove -> undo.
- Bulk import/clear operations push one coarse undo action without preserving before/after assignment lists.
- Undo after profile changes, preset deletion, or NPC filtering behaves differently from manual reversal.

**Consequences:** Users cannot trust undo during long modding sessions; assignments or preset edits can be lost or resurrected unexpectedly.

**Prevention strategy:**
- Define per-command undo semantics before implementation: value snapshot, identity lookup, or intentional live reference.
- Snapshot serializable/value state for operations that can be followed by edits to the same object.
- Bound undo history for large sessions or document memory behavior.

**Future phase to address:** Undo/redo hardening phase, especially before adding multi-select bulk operations or richer inspector edits.

**Test/review guardrail:**
- Add interleaving tests for rename, assignment, deletion, import, clear, profile switch, and generated-text invalidation.
- Review every new undo command for snapshot ownership and memory retention.

### Pitfall 6: Filtering UI Changes Break Bulk Morph Workflows

**What goes wrong:** Column filters, search, multi-select, and visible-only operations disagree about which NPCs are visible/selected. Bulk actions such as Fill Empty or Clear Assignments affect hidden rows, miss visible rows, or operate on stale selections.

**Why it happens:** The original ControlsFX TableFilter behavior is richer than Avalonia DataGrid's built-in surface. Current filtering is partial, race-focused, and implemented through collection rebuilds and code-behind synchronization.

**Warning signs:**
- A filter feature is implemented only in ViewModel tests, with no headless UI interaction test through the actual DataGrid/popup.
- Selection is preserved by index rather than stable NPC identity.
- Visible collections are cleared/repopulated during every property change.
- Bulk operations don't state whether they target all NPCs, visible NPCs, selected NPCs, or empty visible NPCs.

**Consequences:** Modders assign presets to the wrong NPC set, producing hard-to-debug `morphs.ini` changes after lengthy manual filtering sessions.

**Prevention strategy:**
- Define a single filtering abstraction with stable identity and explicit operation scopes: all, visible, selected, visible-empty.
- Generalize column filters as reusable components instead of one-off race-filter code.
- Preserve selection by NPC key (`Mod`, `EditorId`, `FormId`) instead of row index where possible.
- Keep user-visible status text after bulk actions: number affected and scope.

**Future phase to address:** Full NPC table filtering / bulk assignment phase.

**Test/review guardrail:**
- Add headless UI tests for each filter column, checklist/search/clear, selection preservation, and filtered bulk operations.
- Add ViewModel tests that assert hidden NPCs are not modified by visible-only commands.

## Moderate Pitfalls

### Pitfall 7: Ignoring Large Real-World Modding Inputs Until Users Hit Them

**What goes wrong:** Large BodySlide XML collections, xEdit NPC dumps, huge saved projects, or large BoS export batches cause UI stalls, memory spikes, slow filter churn, or cancellation delays.

**Warning signs:**
- Parsers continue to read whole files without size checks while new workflows encourage bulk import.
- Filtering/search rebuilds observable collections on every keystroke or NPC property change.
- Inspector rows are fully materialized in non-virtualized controls for every selected preset.
- Export writers accumulate all outputs before writing without memory benchmarks.

**Prevention strategy:**
- Add realistic stress fixtures/benchmarks before advertising bulk workflow improvements.
- Debounce search/filter text; use incremental filtered views or cached result sets.
- Virtualize SetSlider inspector rows and NPC tables where practical.
- Add file-size warnings or clear validation errors for unusually large local inputs.

**Future phase to address:** Performance/stress hardening phase before advanced filtering, drag-and-drop bulk import, or CLI generation.

**Test/review guardrail:**
- Add tests/benchmarks for thousands of NPCs, many presets, large `.jbs2bg`, and BoS batch exports.
- Review UI changes for virtualization and collection reset behavior.

### Pitfall 8: Weak Filesystem Failure Handling Around Save/Export

**What goes wrong:** Antivirus, locked files, permissions, cross-volume temp paths, or partial rollback failures leave users with incomplete `templates.ini`/`morphs.ini`/BoS outputs or confusing backup files.

**Warning signs:**
- Export code changes atomic write ordering or temp/backup paths without failure-injection tests.
- Save/export status reports success after only part of a batch completes.
- New export surfaces write directly with `File.WriteAllText` instead of the atomic writer.
- Rollback failure behavior is undocumented.

**Prevention strategy:**
- Keep atomic writes centralized and do not bypass `AtomicFileWriter` for new output modes.
- Add clear, actionable error messages that list which files were written/restored/left untouched.
- Include recovery notes in release docs for partial export failures.

**Future phase to address:** Export reliability phase, especially before CLI or batch export enhancements.

**Test/review guardrail:**
- Add failure-injection tests for locked files, commit failure, rollback failure, and backup restore failure.
- Review every new file-writing path for atomicity and encoding/line-ending parity.

### Pitfall 9: Preference Persistence Drift Changes User Output Unexpectedly

**What goes wrong:** Workflow preferences such as `OmitRedundantSliders`, last-used folders, theme, or default profile are not persisted consistently, so the same project can generate different preview/export output after restart or force repetitive navigation.

**Warning signs:**
- UI exposes a setting but it only lives in memory.
- `OmitRedundantSliders` defaults differently across startup, tests, and project workflows.
- File dialogs always start in generic locations despite prior successful imports/exports.
- Preference save failures are silent.

**Prevention strategy:**
- Extend `UserPreferences` to include PRD-required folders and generation-affecting settings.
- Separate user preferences from project data: project-compatible output state belongs in `.jbs2bg`; workflow conveniences belong in `%APPDATA%` preferences.
- On preference load failure, keep safe defaults and surface a non-blocking warning.

**Future phase to address:** Preference/workflow polish phase.

**Test/review guardrail:**
- Add service tests for preference round trips, missing/malformed preference files, and `OmitRedundantSliders` persistence.
- Review whether a setting affects generated output; if yes, require explicit persistence and test coverage.

### Pitfall 10: Charset and Path Assumptions Leak Into Modder Data

**What goes wrong:** NPC dumps with non-UTF-8 encodings import with mojibake, image lookup names accidentally allow traversal or fail on legitimate names, or export filenames mishandle Windows-reserved characters in preset names.

**Warning signs:**
- New import/export/image code normalizes names through filesystem paths without containment checks.
- NPC parser changes fallback encoding behavior without tests for BOM/UTF-8/fallback.
- BoS JSON export writes preset names directly as filenames without sanitization review.
- Code assumes Steam `Data/` paths rather than Mod Organizer's per-mod layout and user-selected files.

**Prevention strategy:**
- Preserve `NpcImageLookupService` rooted/path-separator containment checks.
- Keep NPC import diagnostics explicit about fallback decoding and malformed lines.
- Sanitize filenames only at export boundaries; keep in-memory preset/project names verbatim for round trips.
- Continue user-picked file dialogs rather than hardcoding BodySlide or MO2 paths.

**Future phase to address:** Import/export UX and image workflow phases.

**Test/review guardrail:**
- Add tests for rooted paths, `..`, separators, drive-qualified names, reserved filename characters, BOM/non-BOM text, and malformed NPC rows.
- Review name/path transformations for "sanitize at edge, preserve in model" behavior.

## Minor Pitfalls

### Pitfall 11: Release Trust Stops at Checksums

**What goes wrong:** Windows users see unsigned-executable warnings and cannot verify publisher identity through Windows trust UI, reducing adoption despite correct output.

**Warning signs:**
- Release notes rely only on ZIP hashes.
- Packaging changes do not mention unsigned-build guidance or signing plans.

**Prevention strategy:**
- Keep SHA256 generation and unsigned-build documentation current.
- Add Authenticode signing when a certificate is available; do not block parity/bugfix releases solely on signing.

**Future phase to address:** Release polish/signing phase.

**Test/review guardrail:**
- Packaging tests or script checks should verify checksums are emitted and documentation is packaged.

### Pitfall 12: Repository Noise Causes Wrong-Tree Edits

**What goes wrong:** Contributors edit Java reference, OpenJFX snapshot, generated artifacts, or old assets instead of the C# app/tests, or broad searches include irrelevant code.

**Warning signs:**
- PRs modify `src/com/asdasfa/jbs2bg`, `src/jfx-8u60-b08`, `assets`, `bin`, or `artifacts` without a reference/regeneration reason.
- Metrics or searches cite JavaFX/OpenJFX code as if it were active C# implementation.

**Prevention strategy:**
- Keep contributor docs explicit about active source roots.
- Scope searches and tooling to `src/BS2BG.*` and `tests/BS2BG.Tests` unless intentionally consulting the Java reference.

**Future phase to address:** Contributor/documentation hygiene phase.

**Test/review guardrail:**
- Review file lists first; flag unexpected legacy/artifact edits before code review.

## Phase-Specific Warnings

| Future Phase Topic | Likely Pitfall | Mitigation | Guardrail |
|--------------------|----------------|------------|-----------|
| Profile-system completion / FO4 correctness | Valid-looking output generated with wrong profile | Distinct FO4 profile, warnings for fallback, profile round-trip compatibility | FO4 fixtures + unknown-profile warning tests |
| Export/import/serialization changes | Silent byte-level output drift | Parity note, golden tests, no expected-file edits without Java regeneration | Byte diff review for INI/JSON/project outputs |
| UI refactor / new dialogs | Binding/runtime regressions from Avalonia/ReactiveUI drift | Compiled bindings, `x:DataType`, ReactiveUI command conventions | Build + headless UI tests + ViewModel pattern review |
| Full NPC filtering | Hidden/visible/selected scope confusion | Single filtering abstraction and explicit bulk-action scope | UI interaction tests for filters and bulk operations |
| Undo/redo hardening | Live-reference snapshots restore wrong state | Value snapshots and interleaving tests | Review each undo command for ownership semantics |
| Performance/stress | Bulk modding inputs freeze UI or consume memory | Large fixtures, virtualization, debounce/incremental filtering | Stress tests/benchmarks before release |
| Export reliability / CLI | Partial writes leave confusing output | Centralized atomic writer and recovery messages | Failure-injection tests |
| Preference polish | Settings reset changes generated output or workflow friction | Persist PRD-required preferences and generation-affecting toggles | Preference round-trip and malformed-file tests |
| Release polish/signing | Users distrust unsigned binaries | Checksums now, Authenticode when available | Packaging script assertions |

## Review Checklist for Future Planning

Before approving a phase that extends BS2BG after parity:

- Does it touch generated output, project files, profile selection, or parser behavior? If yes, require golden/parity tests and a Java-reference comparison plan.
- Does it add UI state or commands? If yes, require ReactiveUI convention compliance and headless/ViewModel tests.
- Does it alter filtering, selection, or bulk actions? If yes, define all/visible/selected semantics in the spec.
- Does it write files? If yes, require atomicity, encoding, line-ending, and recovery behavior review.
- Does it handle names or paths from user/modder data? If yes, preserve model values verbatim and sanitize only at filesystem boundaries.
- Does it improve workflow convenience? If yes, decide whether state belongs in `.jbs2bg` or user preferences and test persistence.

## Sources

- `J:\jBS2BG\.planning\PROJECT.md` — project constraints, active requirements, sacred compatibility guarantees.
- `J:\jBS2BG\PRD.md` — parity spec, UI flows, profile plan, implementation risks, testing strategy.
- `J:\jBS2BG\AGENTS.md` — current stack, sacred files, byte-identical output rules, ReactiveUI/Avalonia conventions.
- `J:\jBS2BG\.planning\codebase\CONCERNS.md` — codebase-specific tech debt, bugs, fragile areas, performance limits, test gaps.
- `J:\jBS2BG\.planning\codebase\TESTING.md` — established test conventions and guardrail patterns.
