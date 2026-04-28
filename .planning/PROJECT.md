# BS2BG (Bodyslide to Bodygen)

## What This Is

BS2BG is a C# / Avalonia desktop utility for Skyrim SE and Fallout 4 modders that converts BodySlide preset XML into BodyGen `templates.ini`, `morphs.ini`, and BodyTypes of Skyrim JSON exports. It is a port and modernization of the Java/JavaFX `jBS2BG` v1.1.2 tool by Totiman / asdasfa, preserving file-format compatibility while improving maintainability, workflow clarity, and release packaging.

The current codebase has implemented the M0-M7 porting milestones plus GSD Phases 1-6: core import/generation/export, project serialization, Avalonia UI, template and morph workflows, undo/redo, NPC import and assignment, release packaging, profile correctness, workflow preference persistence, NPC filtering, scoped bulk operations, undo hardening, diagnostics, import/export previews, custom profile management, CLI automation, portable bundle creation, deterministic assignment strategies, release trust packaging, and request-scoped custom profile catalogs for headless generation. Future work continues through scoped GSD/OpenSpec phases, with byte-identical output parity and modder trust as the primary constraints.

## Core Value

Modders can reliably convert existing BodySlide presets into BodyGen and BoS outputs that match the Java tool's behavior byte-for-byte where compatibility matters.

## Requirements

### Validated

- ✓ Core slider math matches the Java reference for defaults, inversion, multipliers, half-up rounding, and float formatting — M0-M7
- ✓ BodySlide XML presets can be imported into project models without UI dependencies — M0-M7
- ✓ `templates.ini`, `morphs.ini`, and BoS JSON exports are generated with the required line endings and JSON formatting semantics — M0-M7
- ✓ `.jbs2bg` project files can be saved and loaded with backward-compatible project data — M0-M7
- ✓ Avalonia UI exposes template generation, morph assignment, slider inspection, NPC import, undo/redo, and release packaging workflows — M0-M7
- ✓ Golden-file, ViewModel, service, and headless UI tests protect core behavior — M0-M7
- ✓ Bundled profile handling distinguishes Skyrim CBBE, Skyrim UUNP, and Fallout 4 CBBE while preserving legacy/unbundled project profiles — Phase 1
- ✓ Neutral unresolved-profile fallback information, explicit bundled-profile adoption, and release-facing FO4 calibration context are validated — Phase 1
- ✓ Workflow preferences and file-picker folders persist outside project serialization while remaining best-effort and non-blocking — Phase 2
- ✓ NPC rows have stable App-layer identity with filter predicates and UI controls for mod, name, editor ID, form ID, race, assignment, and preset values — Phase 2
- ✓ NPC bulk operations support explicit all, visible, selected, and visible-empty scopes with hidden-row protections — Phase 2
- ✓ Template, profile, morph, NPC, and scoped bulk undo paths use bounded history and value snapshots to avoid mutable live-state corruption — Phase 2
- ✓ Diagnostics, import preview, export preview, overwrite/batch-risk confirmation, and atomic write outcome reporting are validated — Phase 3
- ✓ Custom profile creation, validation, local storage, project embedding, recovery diagnostics, profile sharing, and Profiles workspace editing/filtering are validated — Phase 4
- ✓ CLI generation, portable project bundles, deterministic assignment strategies, checksum-backed release artifacts, and packaged setup guidance are validated — Phase 5
- ✓ CLI/headless generation composes embedded project custom profiles through a shared request-scoped catalog and blocks unresolved custom profile fallback — Phase 6

### Active

- [ ] Preserve Java parity for every subsequent change touching slider math, import, serialization, or export behavior.
- [ ] Continue improving modder-facing workflows without breaking existing `.jbs2bg`, BodyGen INI, BoS JSON, or BodySlide XML compatibility.
- [ ] Maintain Avalonia 12 compiled-binding and ReactiveUI conventions across all new UI work.
- [ ] Keep release artifacts portable, self-contained, and straightforward for Windows-first modder distribution.

### Out of Scope

- Built-in BodySlide mesh editing or `.nif` rendering — BS2BG is a conversion and assignment tool, not a BodySlide replacement.
- Cloud sync, accounts, telemetry, or hosted services — local/offline modder tooling is the expected deployment model.
- Regenerating golden expected fixtures to hide failing tests — expected files are treated as authoritative Java-reference outputs.
- Replacing the Java parity contract with cleaner but incompatible formatting or output semantics — compatibility is load-bearing.

## Context

The authoritative behavior source is the Java/JavaFX 8 implementation under `src/com/asdasfa/jbs2bg/`, especially `MainController.java`, `data/SliderPreset.java`, `data/Settings.java`, `data/NPC.java`, and custom morph target classes. The C# solution is split into `BS2BG.Core` for pure domain/file-format logic, `BS2BG.App` for Avalonia 12 UI, and `BS2BG.Tests` for golden-file, unit, and headless UI coverage.

The repository already contains a codebase map under `.planning/codebase/`, OpenSpec archives for completed milestones, and current capability specs under `openspec/specs/`. The current GSD initialization should support future phases rather than re-litigating already archived milestones.

The primary users are Skyrim SE and Fallout 4 modders who are comfortable with BodySlide, BodyGen, RaceMenu/ECE/LooksMenu, xEdit-style NPC dumps, and INI-based mod configuration. Their most important trust signal is that generated output behaves the same as the original Java tool for existing projects and presets.

## Constraints

- **Compatibility**: `.jbs2bg`, BodyGen INI, BoS JSON, and BodySlide XML semantics must remain backward-compatible because modders share files and downstream tools parse the outputs.
- **Output parity**: Golden-file outputs compare byte-for-byte against Java reference output; line endings, trailing newlines, ordering, rounding, and float formatting are intentional behavior.
- **Sacred files**: `tests/fixtures/expected/**`, `JavaFloatFormatting.cs`, `SliderMathFormatter.cs`, `BodyGenIniExportWriter.cs`, and `BosJsonExportWriter.cs` require explicit caution before edits.
- **Architecture**: `BS2BG.Core` stays UI-free and portable; Avalonia and platform-specific concerns remain in `BS2BG.App`.
- **UI framework**: Avalonia 12 compiled bindings require `x:DataType` on roots and data templates; new ViewModels follow ReactiveUI 23.x conventions and source-generated `[Reactive]` properties.
- **Testing**: New tests use xUnit v3 and FluentAssertions; fixture expectations are not regenerated to silence failures.
- **Distribution**: Windows-first, self-contained portable executable remains the preferred release shape.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Keep `.jbs2bg` project extension indefinitely | Existing users and saved projects depend on it | ✓ Good |
| Preserve Java output semantics even when awkward in .NET | Byte-identical output is critical for trust and compatibility | ✓ Good |
| Split Core from Avalonia App | Keeps math, parsing, serialization, and export logic testable and portable | ✓ Good |
| Use Avalonia 12 with ReactiveUI conventions | Modern desktop UI while keeping MVVM state and command behavior explicit | ✓ Good |
| Treat Fallout 4 profile support as experimental until calibrated | FO4 slider defaults/inverts/multipliers lack an authoritative parity source | — Pending |
| Use neutral unresolved-profile fallback UI in Phase 1 | Locked context decisions forbid warning banners, mismatch heuristics, and in-app FO4 experimental labels for this phase | ✓ Good |
| Continue scoped future changes through GSD/OpenSpec workflows | Prevents broad regressions in a parity-sensitive tool | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? -> Move to Out of Scope with reason
2. Requirements validated? -> Move to Validated with phase reference
3. New requirements emerged? -> Add to Active
4. Decisions to log? -> Add to Key Decisions
5. "What This Is" still accurate? -> Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check -> still the right priority?
3. Audit Out of Scope -> reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-28 after Phase 6 completion*
