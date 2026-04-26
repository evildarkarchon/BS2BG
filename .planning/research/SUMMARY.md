# Project Research Summary

**Project:** BS2BG (Bodyslide to Bodygen)  
**Domain:** Windows-first C# / Avalonia desktop modder utility for BodySlide-to-BodyGen/BoS conversion  
**Researched:** 2026-04-26  
**Confidence:** HIGH overall; MEDIUM where Fallout 4 profile calibration and Avalonia filtering specifics remain unresolved

## Executive Summary

BS2BG is a parity-sensitive desktop conversion tool, not a greenfield product or broad modding platform. Experts should build it as a small, deterministic local utility: keep format-critical code in a UI-free Core library, expose workflows through Avalonia/ReactiveUI ViewModels, and protect every import/generation/export change with byte-level tests against Java-reference behavior.

The recommended approach is to keep the existing stack and architecture, then focus future phases on correctness and workflow trust. The highest-value next work is distinct Fallout 4 CBBE profile support, visible profile mismatch warnings, and diagnostics that prevent users from generating syntactically valid but semantically wrong BodyGen/BoS output. Workflow improvements such as preferences, full NPC filtering, validation reports, and export previews should follow after profile correctness is stable.

The main risks are silent output drift, wrong-profile exports, UI framework drift, monolithic ViewModel coupling, fragile undo/redo snapshots, and filtering semantics that accidentally mutate hidden NPCs. Mitigate these with Core-first design, compatibility-first schema evolution, mandatory golden/snapshot tests, ReactiveUI/Avalonia convention checks, explicit all/visible/selected bulk-action scopes, and phase-specific guardrails before touching sacred formatter/export files.

## Key Findings

### Recommended Stack

Keep the existing stack. BS2BG has already paid the cost of the C# port and Avalonia UI; future work should optimize for low dependency churn, deterministic release builds, and compatibility preservation rather than framework migration. Add dependencies only for concrete workflow gaps, and isolate package upgrades into maintenance changes.

**Core technologies:**
- **.NET 10 / C# 14 for App and Tests** — current LTS application/test target, suitable for self-contained Windows distribution.
- **`netstandard2.1` / C# 13 for `BS2BG.Core`** — preserves a portable, UI-free domain layer for parsers, formatters, generators, serializers, and writers.
- **Avalonia 12.0.1** — existing desktop UI framework; keep compiled bindings mandatory with `x:DataType` on roots and `DataTemplate`s.
- **ReactiveUI.Avalonia + ReactiveUI.SourceGenerators** — canonical MVVM stack for this repo; use `[Reactive]`, observable `canExecute`, `ReactiveCommand.Create*`, and scheduler-aware async commands.
- **System.Text.Json, XDocument, explicit Core writers** — keep JSON/XML/file-format handling boring, deterministic, and testable; avoid generic INI/JSON writers in byte-sensitive export paths.
- **xUnit v3, FluentAssertions, Avalonia.Headless.XUnit, golden fixtures** — keep these as the release gate for domain parity and UI wiring.
- **Central Package Management plus future NuGet lock files** — direct versions are centralized today; add lock files and locked-mode restore before the next release/reproducibility phase.

### Expected Features

M0-M7 already satisfy the original table stakes: BodySlide XML import, Java-compatible slider math, `templates.ini` / `morphs.ini` generation, BoS JSON export, `.jbs2bg` round trips, preset management, NPC import/assignment, custom targets, baseline search/filter/bulk operations, drag-and-drop, command palette, undo/redo, theme/accessibility baseline, and portable release packaging. Roadmap work should protect these, not rebuild them.

**Must have (remaining table stakes):**
- **Distinct Fallout 4 CBBE profile** — highest-priority correctness gap; FO4 must not reuse Skyrim CBBE defaults/multipliers/inverts.
- **Profile mismatch warnings** — surface unknown/missing/inferred profile behavior before generation/export.
- **User-editable/custom profile management** — required for body variants beyond bundled CBBE/UUNP/FO4 profiles, after diagnostics are stable.
- **Full per-column NPC filtering** — large NPC dumps need filtering by mod, name, editor ID, form ID, race, and assignment state.
- **Persist workflow preferences** — last-used folders and generation-affecting toggles such as omit-redundant state should survive restart.
- **Large-input guardrails and performance tests** — validate thousands of NPCs/presets before advertising richer bulk workflows.
- **Safer undo/redo snapshots** — move risky operations from live-reference captures to value snapshots.
- **Transparent save/export failures** — improve atomic-write failure reporting and recovery guidance.
- **Release trust improvements** — add signing when possible; otherwise keep checksum verification prominent.
- **In-app setup/troubleshooting guidance** — help users with BodyGen/BodySlide external setup without automating external tools.

**Should have (differentiators):**
- **Profile diagnostics dashboard** — show slider coverage, unknown sliders, injected defaults, multipliers, inversions, and likely profile mismatch.
- **Project validation report** — one-click readiness report for profiles, targets, assignments, references, and export safety.
- **NPC import mapping preview** — stage parsed rows, duplicates, invalid lines, and charset fallback before committing import.
- **Output folder advisor** — advise common BodyGen folder layouts without requiring game/mod-manager discovery.
- **Dry-run export diff/preview** — compare exact generated bytes and paths before writing.
- **Headless CLI** — viable later because Core is UI-free; should call the same services as the GUI.
- **Portable project bundle** — share `.jbs2bg`, outputs, profile JSON copies, and validation report without private paths.

**Defer / avoid:**
- BodySlide replacement, mesh/NIF rendering, 3D body preview, built-in xEdit/plugin editor, cloud/accounts/telemetry, hosted profile packs, automatic game/mod-manager discovery as required setup, formatter “cleanup”, silent profile fallback, installer-first distribution, equal-priority cross-platform packaging, and fixture regeneration to hide C# regressions.

### Architecture Approach

Preserve the current one-way dependency structure: Avalonia Views forward UI events; ReactiveUI ViewModels orchestrate workflows; App services adapt platform APIs; Core owns all deterministic domain/file-format behavior; tests protect byte parity, serialization, ViewModel behavior, and high-risk UI wiring. The major architectural risk is boundary erosion, especially ViewModels duplicating export formatting or Core learning about UI/platform services.

**Major components:**
1. **`BS2BG.Core.Models`** — durable project aggregate and domain objects; no UI selection/filter state.
2. **`BS2BG.Core.Import`** — BodySlide XML and NPC text parsers returning result DTOs and diagnostics.
3. **`BS2BG.Core.Generation`** — profile-aware template, morph, and BoS generation over Core models.
4. **`BS2BG.Core.Formatting`** — sacred Java-compatible math, defaults, inversion, multipliers, rounding, and float formatting.
5. **`BS2BG.Core.Export` / IO** — atomic, byte-normalized file persistence for generated artifacts.
6. **`BS2BG.Core.Serialization`** — `.jbs2bg` compatibility, optional new fields, legacy `isUUNP`, and reference resolution.
7. **`BS2BG.App.ViewModels`** — ReactiveCommand orchestration, status, validation display, dirty state, and undo/redo.
8. **`BS2BG.App.Services`** — StorageProvider, clipboard, dialogs, image windows, preferences, and other platform adapters.
9. **`BS2BG.App.Views`** — AXAML, compiled bindings, keyboard/DnD forwarding, and minimal code-behind.
10. **`BS2BG.Tests`** — golden parity, parser/export/serialization, ViewModel, service, headless UI, and release checks.

### Critical Pitfalls

1. **Treating generated output as “just text”** — avoid by keeping output generation in Core, preserving line endings/float formatting/order/trailing-newline semantics, and requiring golden/snapshot tests for every export-impacting change.
2. **Shipping valid-looking output with the wrong profile** — avoid by adding a distinct FO4 CBBE profile, visible fallback/mismatch warnings, profile round-trip tests, and honest experimental labeling until FO4 calibration is validated.
3. **Reintroducing Avalonia/ReactiveUI drift** — avoid by enforcing compiled bindings, `x:DataType`, ReactiveUI source-generator conventions, `ReactiveCommand`, observable `canExecute`, and headless/ViewModel tests.
4. **Letting monolithic ViewModels accumulate more workflow state** — avoid by extracting focused services/helpers before adding adjacent behavior and splitting large AXAML into user controls during substantial UI phases.
5. **Undo/redo captures mutable live state** — avoid by defining undo semantics per command, snapshotting value state for mutable operations, and adding interleaving tests for rename/assign/remove/import/clear/profile switch.
6. **Filtering UI breaks bulk morph workflows** — avoid by using stable NPC identity, a single filtering abstraction, explicit all/visible/selected/visible-empty operation scopes, and UI + ViewModel tests for filtered bulk actions.
7. **Large real-world inputs freeze workflows** — avoid by adding stress fixtures, benchmarks, virtualization/debounce where useful, and file-size warnings before richer bulk import/filtering/CLI work.
8. **Weak save/export failure handling** — avoid by centralizing atomic writers, adding failure-injection tests, and reporting which files were written/restored/left untouched.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Profile Correctness and Trust

**Rationale:** Wrong-profile output is the most serious remaining risk because it produces valid-looking files that users cannot easily diagnose. Profile semantics must be model/Core-level before UI polish or custom profile workflows.  
**Delivers:** Distinct `fallout4-cbbe.json`, FO4-specific catalog loading, unknown/missing profile warnings, profile round-trip compatibility, profile-specific golden fixtures, and release/docs language that labels FO4 confidence honestly.  
**Addresses:** Distinct FO4 profile, profile mismatch warnings, initial profile diagnostics.  
**Avoids:** Pitfall 2 (wrong profile), Pitfall 1 (output drift), Pitfall 3 (profile UI as App-only state).  
**Guardrails:** Core-first implementation, `Profile` + legacy `isUUNP` round-trip tests, FO4 fixture coverage, no silent fallback without diagnostics.

### Phase 2: Workflow Persistence, Filtering, and Undo Hardening

**Rationale:** Once profile correctness is protected, improve daily modder workflow without touching sacred formatting paths. Preferences, filtering, and undo are high-impact table stakes for long editing sessions.  
**Delivers:** Persisted last-used folders and omit-redundant state, reusable per-column NPC filtering, explicit bulk operation scopes, large-input tests/guardrails, and value-snapshot undo semantics for risky operations.  
**Addresses:** Persist workflow preferences, full per-column NPC filtering, large-input guardrails, safer undo/redo snapshots.  
**Avoids:** Pitfall 5 (live undo state), Pitfall 6 (filter/bulk scope confusion), Pitfall 7 (large input stalls), Pitfall 9 (preference drift).  
**Guardrails:** Stable NPC identity, visible/all/selected semantics in specs, ViewModel + headless tests for filtering and bulk actions, preference malformed-file tests, stress fixtures.

### Phase 3: Validation and Diagnostics

**Rationale:** Diagnostics should build on correct profiles and reliable workflow projections. This phase increases user trust by explaining what BS2BG will generate before users commit files.  
**Delivers:** Project validation report, profile diagnostics dashboard, NPC import mapping preview, export folder advisor, clearer export/save failure messages, and optional dry-run path/content preview.  
**Addresses:** Profile diagnostics, project validation report, NPC import preview, output folder advisor, save/export transparency.  
**Avoids:** Pitfall 1 (undetected output changes), Pitfall 8 (unclear partial writes), Pitfall 10 (charset/path assumptions).  
**Guardrails:** Diagnostics as read-only first, parser diagnostics DTOs in Core, no direct App file writes, failure-injection tests, sanitize-at-boundary path handling.

### Phase 4: Profile Extensibility and Controlled Customization

**Rationale:** Custom profile management is table stakes for wider body-variant support, but it should not precede diagnostics and FO4 correctness. User-authored profile data can produce plausible wrong output unless validation is strict.  
**Delivers:** Local JSON profile import/export/copy/edit/validate workflow, schema/version rules, compatibility handling for missing profiles, and optional calibration assistant only with explicit “best effort” labeling.  
**Addresses:** User-editable/custom profile management, profile calibration assistant, portable project profile copies.  
**Avoids:** Pitfall 2 (wrong profiles), Pitfall 1 (formatter/profile output drift), Pitfall 4 (profile logic scattered into ViewModels).  
**Guardrails:** Schema validation, malformed profile tests, no shared mutable profile state, Core catalog APIs, no hosted/auto-downloaded profile packs.

### Phase 5: Automation, Sharing, and Release Trust

**Rationale:** Automation and distribution amplify the tool after correctness, workflow safety, and diagnostics are stable. CLI and bundles should reuse Core services rather than invent alternate generation paths.  
**Delivers:** Headless CLI using Core services, portable project bundle with validation report/profile copies, optional assignment strategy presets, checksum/signing improvements, and packaged setup/troubleshooting guidance.  
**Addresses:** Headless CLI, portable project bundle, assignment strategy presets, release trust, support documentation.  
**Avoids:** Pitfall 1 (alternate generation drift), Pitfall 8 (batch export failures), Pitfall 11 (release trust), Pitfall 12 (wrong-tree/release noise).  
**Guardrails:** Same Core generation/export services as GUI, deterministic random provider seams, atomic writer tests, release package assertions, no installer/cloud/telemetry scope creep.

### Phase Ordering Rationale

- **Correctness before convenience:** FO4/profile mismatch can silently invalidate the core value proposition, so it precedes filtering, diagnostics, CLI, and profile editing.
- **Core/test-led before UI exposure:** Profile and export semantics must be represented in Core models/services and covered by fixtures before ViewModels/AXAML expose them.
- **Workflow safety before advanced automation:** Preferences, filtering scopes, stress tests, and undo snapshots reduce the risk that validation, CLI, and bulk assignment features mutate the wrong data.
- **Diagnostics before customization:** Users need visibility into profile coverage and project health before editable/custom profiles or calibration assistants can be trusted.
- **Automation last:** CLI, project bundles, assignment strategies, and release trust work should reuse proven services after output semantics and failure handling are stable.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1: Profile Correctness and Trust** — needs authoritative FO4 CBBE / LooksMenu BodyGen profile calibration data, real fixtures, and user/domain validation.
- **Phase 2: Workflow Persistence, Filtering, and Undo Hardening** — full per-column Avalonia DataGrid/TableFilter behavior needs prototype research and performance benchmarking.
- **Phase 4: Profile Extensibility and Controlled Customization** — needs profile schema/version/migration design and validation UX research.
- **Phase 5: Automation, Sharing, and Release Trust** — CLI composition and Authenticode/signing pipeline details may need focused research if included.

Phases with standard patterns (skip research-phase unless scope expands):
- **Preference persistence within Phase 2** — established App service pattern and straightforward JSON/preferences tests.
- **Validation report foundations in Phase 3** — standard Core diagnostics aggregation if kept read-only and local.
- **Release checksum/docs improvements in Phase 5** — existing PowerShell release process can be extended without broad research.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Repo configuration, project guidance, Microsoft/Avalonia docs, and existing implementation align; only Avalonia ecosystem caveats are medium. |
| Features | HIGH for current table stakes; MEDIUM for wider ecosystem differentiators | Satisfied and remaining table stakes are strongly supported by project docs; community workflow expectations are less formal. |
| Architecture | HIGH | Component boundaries, data flows, and testing responsibilities are documented and already implemented. |
| Pitfalls | HIGH for codebase-specific risks; MEDIUM for broader modding workflow risks | Sacred-file/output/profile/UI risks are explicit; large-input and ecosystem behavior need validation with real datasets. |

**Overall confidence:** HIGH for roadmap direction; MEDIUM for exact FO4 profile data, advanced filtering implementation details, and signing logistics.

### Gaps to Address

- **Authoritative Fallout 4 CBBE calibration:** Collect known-good FO4 BodySlide/LooksMenu BodyGen examples and compare fixtures before declaring FO4 support stable.
- **Avalonia per-column filtering design:** Prototype reusable filter controls and benchmark thousands of NPCs before committing to a full table-filter implementation.
- **Custom profile schema and migration rules:** Define strict validation, optional fields, compatibility behavior, and malformed-profile diagnostics before editable profile UI.
- **Undo/redo ownership model:** Audit current commands and decide snapshot vs identity-lookup semantics before adding advanced bulk operations.
- **Filesystem failure matrix:** Add failure-injection tests for locked files, rollback failures, cross-volume paths, and partial batch exports before CLI/bundle work.
- **Release trust path:** Decide whether signing is available; if not, make checksum verification first-class in package docs and release UX.

## Sources

### Primary (HIGH confidence)
- `.planning/PROJECT.md` — project status, active requirements, constraints, current decisions, and scope boundaries.
- `.planning/research/STACK.md` — stack/version recommendations and dependency policy.
- `.planning/research/FEATURES.md` — satisfied table stakes, remaining table stakes, differentiators, anti-features, and phase suggestions.
- `.planning/research/ARCHITECTURE.md` — component boundaries, data flow rules, patterns, anti-patterns, and build order.
- `.planning/research/PITFALLS.md` — critical/moderate/minor pitfalls, warning signs, guardrails, and phase-specific warnings.
- `AGENTS.md`, `PRD.md`, `.planning/codebase/*`, and `openspec/specs/*` as cited by the research files.

### Secondary (MEDIUM confidence)
- Microsoft Learn .NET / NuGet docs — .NET 10 LTS support and NuGet lock-file guidance.
- Avalonia docs — compiled bindings, `x:DataType`, StorageProvider APIs, and testing patterns.
- Nexus Mods Wiki and LoversLab BodyGen/jBS2BG/LooksMenu discussions — community workflow expectations, BodyGen file placement, and FO4 context.

### Tertiary (LOW confidence)
- None used as decisive roadmap inputs. Wider modding ecosystem expectations should still be validated with maintainer/user feedback during requirements definition.

---
*Research completed: 2026-04-26*  
*Ready for roadmap: yes*
