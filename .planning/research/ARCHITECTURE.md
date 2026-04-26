# Architecture Research: BS2BG Future Phases

**Domain:** C# / Avalonia desktop conversion utility for Skyrim SE / Fallout 4 modders  
**Researched:** 2026-04-26  
**Overall confidence:** HIGH — based on repository planning docs, current codebase map, PRD, and project conventions.

## Executive Summary

Future BS2BG phases should preserve the current architecture: a pure, portable `BS2BG.Core` containing every parity-sensitive model, parser, formatter, generator, serializer, and writer; an Avalonia/ReactiveUI `BS2BG.App` that owns UI workflows and platform services; and `BS2BG.Tests` as the safety net for byte-identical output, project round trips, ViewModel behavior, and headless UI smoke coverage.

The main architectural risk is not missing technology; it is boundary erosion. Profile improvements, export variants, richer filtering, workflow automation, and UI polish are all feasible if they flow through existing Core service seams and App service adapters. They become dangerous if ViewModels duplicate serialization/export logic, Core learns about Avalonia, or UI-only state mutates project data without undo/dirty/test coverage.

Roadmap phases should be ordered by dependency: first harden profile/catalog abstractions and compatibility rules, then add Core generation/export capabilities, then expose them in UI workflows, then polish high-density UX and release behaviors. Every phase that touches slider math, profile lookup, serialization, export ordering, line endings, or filename/content formatting must start with fixture updates or new golden assertions before UI work.

## Recommended Architecture

```text
Avalonia Views / Window code-behind
  - AXAML layout, compiled bindings, keyboard/DnD event forwarding only
  - No business rules, no direct file-format writes
        ↓
ReactiveUI ViewModels
  - Workflow orchestration, command gating, status, undo/redo, validation display
  - Calls Core services and App platform-service interfaces
        ↓
App Platform Services
  - StorageProvider, clipboard, dialogs, image windows, user preferences
  - Window-bound implementations stay in BS2BG.App/Services
        ↓
Core Domain + Services
  - ProjectModel, SliderPreset, Npc, CustomMorphTarget
  - XML/NPC import, profile catalog, generation, formatting, serialization, export writers
        ↓
Local Filesystem Artifacts
  - .jbs2bg, BodySlide XML, NPC text, templates.ini, morphs.ini, BoS JSON, profile JSON
```

The dependency direction must stay one-way: `BS2BG.App → BS2BG.Core`; `BS2BG.Core` must never reference Avalonia, ReactiveUI, App services, clipboard, dialogs, windows, or UI schedulers.

## Component Boundaries

| Component | Responsibility | Communicates With | Future-phase rule |
|-----------|----------------|-------------------|-------------------|
| `BS2BG.Core.Models` | In-memory project aggregate and domain objects. | Core services, App ViewModels, tests. | Add durable state here only when it is part of `.jbs2bg` or generated output semantics. Keep UI selection/filter state out. |
| `BS2BG.Core.Import` | Parse BodySlide XML and NPC text into domain/result DTOs with diagnostics. | ViewModels, tests. | New import sources should return result objects and diagnostics; do not show dialogs or status messages from Core. |
| `BS2BG.Core.Generation` | Convert domain state to template/morph/BoS text through profile-aware services. | ViewModels, export writers, tests. | New output modes should be thin services over the formatter/export contracts, not ViewModel string concatenation. |
| `BS2BG.Core.Formatting` | Byte-sensitive slider math, defaults, inversion, multipliers, rounding, float formatting. | Generation services and golden tests. | Treat as sacred. Changes require explicit parity rationale and fixture coverage before merge. |
| `BS2BG.Core.Export` / `IO` | Atomic writes and format-normalized file persistence. | Shell ViewModel, tests. | All export phases must use writers here. No direct `File.WriteAllText` from App. |
| `BS2BG.Core.Serialization` | `.jbs2bg` load/save compatibility and reference resolution. | Shell ViewModel, tests. | Schema additions must be optional/backward-compatible and round-trip legacy fields. |
| `BS2BG.App.ViewModels` | User workflow orchestration, ReactiveCommand state, undo/redo recording, busy/status display. | Views, Core services, App services. | Keep mutations centralized in command/helper methods that record undo and preserve dirty state. |
| `BS2BG.App.Services` | Avalonia/platform adapters: dialogs, storage provider, clipboard, windows, preferences, image lookup. | ViewModels and MainWindow attach hooks. | New OS/UI integrations need interfaces plus test/null implementations. |
| `BS2BG.App.Views` | AXAML, compiled bindings, minimal event bridges for selection, drag/drop, focus, window service attachment. | ViewModels and App services. | Code-behind may forward UI events; it must not own Core business decisions. |
| `BS2BG.Tests` | Golden parity, parser/export/serialization, ViewModel, App service, headless UI, release checks. | Core and App. | Any new feature gets tests at the lowest affected layer plus workflow coverage if user-visible. |

## Data Flow Rules

### Import and edit flow

1. View event invokes a `ReactiveCommand` on a ViewModel.
2. ViewModel obtains paths or user choices through an App service interface.
3. Core parser/service converts files into model/result DTOs.
4. ViewModel applies changes to the shared `ProjectModel` through focused mutation helpers.
5. User-facing mutations record undo/redo unless they are project load/reset replay operations.
6. `ProjectModel` dirty/version state changes via model subscriptions, not ad-hoc shell flags.

### Generation and export flow

1. ViewModel asks Core generation services for text/DTO output.
2. Generation services select profile data from `TemplateProfileCatalog` and delegate all slider math to `SliderMathFormatter`.
3. Export writers normalize exact output rules: INI CRLF, BoS LF/no trailing newline, filename sanitization, atomic writes.
4. ViewModel reports status and handles expected operational failures; it does not reformat generated content.

### Project save/load flow

1. Shell ViewModel owns open/save commands and unsaved-change prompts.
2. `ProjectFileService` owns JSON schema compatibility, optional new fields, legacy `isUUNP`, preset reference resolution, and atomic writes.
3. Load replaces the singleton `ProjectModel` via aggregate replacement rather than swapping random child collections in UI code.
4. After load, ViewModels refresh selection/filter/generated text from the model; serialized UI ephemera should remain outside `.jbs2bg` unless explicitly productized.

## Integration Rules for Future Work

### Profile improvements

Use Core-first design. Add or extend profile concepts in `BS2BG.Core.Generation` / `Models` and persist only compatibility-safe fields in `ProjectFileService`. UI profile selectors should bind to catalog entries exposed by ViewModels, but the selected profile must ultimately be a model property used by Core generation. Preserve legacy `isUUNP` on save and load it as a fallback when a named profile is absent.

Testing implications:
- Golden tests for each profile that affects defaults, multipliers, inversion, missing-default injection, and output formatting.
- Project round-trip tests for old `isUUNP`-only files and new `Profile`-aware files.
- ViewModel tests for profile switching invalidating previews/generated text without cross-profile state leakage.

### UI workflow improvements

Keep workflow enhancements in `BS2BG.App.ViewModels` and `BS2BG.App.Views`. Global search, command palette, multiselect, drag/drop, inspector improvements, and filtering are App concerns unless they produce persisted project semantics. Filtering must not mutate underlying `ProjectModel` order or generated output order.

Testing implications:
- ViewModel tests for command availability, selected item behavior, visible/filtered sets, undo/redo, and status messages.
- Headless UI smoke tests only for binding/wiring risks, not every domain branch.
- AXAML compiled-binding coverage through normal build.

### Export improvements

Any new export option should enter through Core generation/export services first, then get App commands. If an export is a true alternate representation, create a new writer or explicit method; do not add flags that make existing writers ambiguous unless tests prove legacy defaults stay byte-identical.

Testing implications:
- Snapshot/golden tests for exact bytes, line endings, ordering, and trailing newline behavior.
- Filesystem tests for atomic write behavior and partial-failure rollback where relevant.
- ViewModel tests for folder selection, cancellation/no-selection, and status/error handling.

### Workflow automation and batch actions

Bulk assignment, random fill, trimming, validation, and batch edits should use Core domain services when they encode domain rules, and ViewModel helpers when they are UI-only orchestration. Random behavior must remain injectable through `IRandomAssignmentProvider` or equivalent deterministic seams.

Testing implications:
- Deterministic fake random providers in Core/ViewModel tests.
- Undo/redo tests for each batch action as a single user operation unless product requirements say otherwise.
- Regression tests proving generated morph lines remain stable after filtering/multiselect operations.

## Patterns to Follow

### Pattern 1: Core service plus App adapter

**What:** Put durable/domain behavior in Core and UI/platform access behind App interfaces.

**When:** Adding profile file management, import sources, export destinations, clipboard/dialog/image behavior, or future CLI-friendly functionality.

**Example structure:**

```csharp
// Core: deterministic and testable.
public sealed class SomeGenerationService
{
    public SomeGenerationResult Generate(ProjectModel project, TemplateProfileCatalog profiles)
    {
        // Domain decisions only; no dialogs, windows, or clipboard.
    }
}

// App: platform interaction and workflow status.
public sealed partial class MainWindowViewModel : ReactiveObject
{
    public ReactiveCommand<Unit, Unit> ExportSomethingCommand { get; }
}
```

### Pattern 2: Compatibility-first schema evolution

**What:** New `.jbs2bg` fields are optional and old fields remain emitted when existing users/tools may depend on them.

**When:** Named profiles, future export settings, project metadata, or workflow preferences become persisted project data.

**Rule:** Load old → save new without data loss; load new in older app should degrade through existing fields when possible.

### Pattern 3: Generated output is a Core concern

**What:** All text that goes to `templates.ini`, `morphs.ini`, BoS JSON, or future BodyGen-compatible files is produced by Core and protected by tests.

**When:** Previews, copy buttons, export commands, and future batch generation.

**Rule:** App may display/copy generated strings, but not rebuild or normalize them.

### Pattern 4: Reactive orchestration at the ViewModel boundary

**What:** Commands use `ReactiveCommand.Create*`, observable `canExecute`, `[Reactive]`, and `[ObservableAsProperty]` for derived state.

**When:** All new ViewModels or substantial ViewModel edits.

**Rule:** No custom RelayCommand, manual dispatcher calls in ViewModels, or `Func<bool>` command gates.

## Anti-Patterns to Avoid

### Anti-Pattern 1: UI logic leaking into Core

**What:** Core references Avalonia, window services, clipboard, dialogs, ReactiveUI, or `Dispatcher.UIThread`.

**Consequence:** Breaks portability, complicates future CLI/headless use, and weakens parity testing.

**Instead:** Define Core inputs/outputs as models/results and adapt them in App services/ViewModels.

### Anti-Pattern 2: ViewModel string-formatting export output

**What:** ViewModels concatenate BodyGen/BoS lines, choose line endings, sanitize filenames, or write files directly.

**Consequence:** Bypasses byte-compatibility contracts and atomic writes.

**Instead:** Call `TemplateGenerationService`, `MorphGenerationService`, `BodyGenIniExportWriter`, and `BosJsonExportWriter` or add a new Core writer.

### Anti-Pattern 3: Profile state as UI-only selection

**What:** Profile choice lives only in a dropdown or preference and is not part of the model/generation input.

**Consequence:** Saved projects become ambiguous and exports can silently use the wrong game/body semantics.

**Instead:** Persist profile identity per preset while preserving legacy `isUUNP` compatibility.

### Anti-Pattern 4: Filtered views changing domain order

**What:** Search/filter/sort UI operations reorder or remove items from `ProjectModel` collections.

**Consequence:** Generated output, undo/redo, and saved project diffs become unpredictable.

**Instead:** Maintain filtered/visible projections in ViewModels and let Core generation apply its documented ordering.

## Suggested Build Order for Future Phases

1. **Profile foundation and compatibility hardening**
   - Add/verify model and serialization support for named profiles while preserving `isUUNP`.
   - Confirm profile catalog loading/migration rules before UI expansion.
   - Exit criteria: old/new project round trips, profile-specific golden tests, no App-only profile semantics.

2. **Core generation/export extensions**
   - Add any new profile-aware generation or export behaviors behind Core services/writers.
   - Keep legacy writer defaults byte-identical.
   - Exit criteria: golden/snapshot coverage for each output mode and atomic-write tests where applicable.

3. **ViewModel workflow integration**
   - Expose Core capabilities through ReactiveCommands, status messages, undo/redo, and validation.
   - Introduce App service interfaces for any new dialogs/platform operations.
   - Exit criteria: ViewModel tests for commands, cancellation/no-selection paths, busy state, dirty state, and undo/redo.

4. **Avalonia UI and interaction polish**
   - Add AXAML, compiled bindings, keyboard shortcuts, drag/drop, filtering, command palette, inspector refinements.
   - Keep code-behind limited to UI event forwarding and window service attachment.
   - Exit criteria: build catches binding errors; headless smoke tests cover high-risk wiring.

5. **Release and migration validation**
   - Validate portable package contents, profile files, preferences location, and upgrade path from existing artifacts.
   - Exit criteria: release script tests/pass, app launches with seeded profiles, existing projects open and export unchanged.

## Cross-Cutting Validation and Testing Implications

| Change type | Required validation |
|-------------|---------------------|
| Slider math, defaults, inversion, multipliers, float formatting | Unit tests from `MATH-WALKTHROUGH.md`, golden files, explicit review of sacred files. |
| `templates.ini` / `morphs.ini` / BoS JSON output | Byte snapshots for line endings, ordering, integer float formatting, trailing newline behavior, filename sanitization. |
| Project serialization | Backward/forward-ish round trips: legacy files, new optional fields, missing references, profile fallback. |
| Profile catalog changes | Tests for CBBE/UUNP/FO4 separation, missing-default injection, malformed/missing profile behavior, no shared mutable profile state. |
| UI workflow commands | ReactiveCommand `canExecute`, `IsExecuting`, cancellation token propagation, status/exception handling, undo/redo replay guards. |
| Filtering/search/multiselect | ViewModel projection tests proving model order and generated output are unaffected unless user commits an explicit mutation. |
| App platform services | Null/test service coverage plus headless smoke tests for StorageProvider/clipboard/dialog attachment seams where practical. |
| Release packaging | Package contains executable and required profile/settings artifacts; no reliance on dev working directory except documented `images/` behavior. |

## Roadmap Implications

- Phases that alter **profile semantics** should precede phases that add profile UI, because UI work without persisted model semantics risks wrong exports and project ambiguity.
- Phases that alter **output generation/export** should be Core/test-led before any App wiring, because byte compatibility is the primary trust contract.
- Phases that improve **workflow density** should be App/ViewModel-led and avoid Core changes unless they create durable domain rules.
- Phases that touch **filtering/multiselect/command palette** should be isolated from serialization/export phases; they are high UI complexity but should not affect generated bytes.
- Any phase touching sacred files should be marked for deeper research/review and should include an explicit parity checklist in its OpenSpec proposal.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Component boundaries | HIGH | Confirmed by project docs and codebase architecture map. |
| Data flow | HIGH | Existing import/generation/export/save flows are documented and consistent. |
| Integration rules | HIGH | Current App service adapter pattern and Core purity constraints are explicit. |
| Build order | HIGH | Dependency ordering follows parity and persistence risks in PRD/project docs. |
| Future FO4 profile correctness | MEDIUM | Architecture is clear, but authoritative FO4 multiplier/inversion data remains a product/domain gap. |

## Gaps to Address in Later Phase Research

- Authoritative Fallout 4 CBBE profile tuning remains unresolved; architecture can support it, but domain data needs validation.
- Advanced DataGrid/TableFilter behavior may need Avalonia-specific implementation research before a filtering-heavy phase.
- If a headless CLI is revived, verify whether current App-owned profile catalog factory/user preference behavior should move partly into a shared non-UI composition layer.

## Sources

- `.planning/PROJECT.md` — project status, constraints, active requirements.
- `PRD.md` — parity contract, architecture, risks, milestone history, profile extension design.
- `AGENTS.md` — current stack, sacred files, ReactiveUI/Avalonia conventions, testing rules.
- `.planning/codebase/ARCHITECTURE.md` — current component map and data flows.
- `.planning/codebase/INTEGRATIONS.md` — filesystem/platform integrations and storage boundaries.
- `.planning/codebase/CONVENTIONS.md` — coding, error handling, comments, module conventions.
