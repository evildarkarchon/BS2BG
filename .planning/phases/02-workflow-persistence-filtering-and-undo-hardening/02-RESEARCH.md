# Phase 2: Workflow Persistence, Filtering, and Undo Hardening - Research

**Researched:** 2026-04-26
**Domain:** Avalonia 12 / ReactiveUI desktop workflow state, large collection filtering, scoped bulk operations, and undo/redo hardening
**Confidence:** HIGH for project constraints and existing code integration; MEDIUM for DynamicData adoption details because docs recommend it but the project does not yet reference it

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
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

### Deferred Ideas (OUT OF SCOPE)
## Deferred Ideas

None — discussion stayed within Phase 2 workflow persistence, filtering, bulk-scope, and undo-hardening scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WORK-01 | User can restart BS2BG and keep last-used folders and generation-affecting workflow preferences such as omit-redundant sliders. | Extend the existing local `UserPreferencesService` and Avalonia `StorageProvider` picker `SuggestedStartLocation`; keep failures non-blocking. [VERIFIED: src/BS2BG.App/Services/UserPreferencesService.cs] [CITED: docs.avaloniaui.net/docs/services/storage/file-picker-options] |
| WORK-02 | User can filter NPC rows by mod, name, editor ID, form ID, race, assignment state, and preset-related values without losing stable NPC identity. | Use generated row IDs plus a keyed filter source (`SourceCache<TObject,TKey>`) so identity is not derived from mutable/display fields. [VERIFIED: src/BS2BG.Core/Models/Npc.cs] [CITED: reactiveui.net/api/DynamicData.SourceCache-2.html] |
| WORK-03 | User can run bulk NPC operations with explicit all, visible, selected, and visible-empty scopes so filtered rows are not mutated accidentally. | Implement one centralized scope resolver that maps UI scope selections to target row IDs before mutation and records one undo action per bulk command. [VERIFIED: .planning/phases/02-workflow-persistence-filtering-and-undo-hardening/02-CONTEXT.md] |
| WORK-04 | User can undo and redo high-risk preset, target, NPC assignment, import, clear, and profile operations without mutable live-state corruption. | Replace risky closure/live-reference captures with targeted value snapshots; keep `UndoRedoService` lightweight but add bounded history. [VERIFIED: src/BS2BG.App/Services/UndoRedoService.cs] [VERIFIED: src/BS2BG.App/ViewModels/TemplatesViewModel.cs] [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs] |
| WORK-05 | User can work with large real-world preset and NPC datasets without UI freezes or unbounded filter/import delays. | Use DynamicData for large reactive collection transformations, debounce text filters, preserve Avalonia virtualization, and run import/filter work on ReactiveUI scheduler patterns. [CITED: github.com/avaloniaui/avalonia-docs/docs/app-development/performance.md] [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md] |
</phase_requirements>

## Summary

Phase 2 is primarily an App-layer hardening phase, not a Core output-format phase. The current project already separates `BS2BG.Core` domain/import/generation/export logic from Avalonia ViewModels and platform services, and Phase 2 should preserve that boundary: local preference persistence, picker seed folders, filter UI state, row selection, scope selectors, and undo status belong in `BS2BG.App`, while pure helper models for value snapshots or row identity may live in Core only if they stay UI-independent. [VERIFIED: .planning/codebase/ARCHITECTURE.md] [VERIFIED: AGENTS.md]

The established architecture pattern is ReactiveUI ViewModels over a mutable `ProjectModel`, with App service interfaces for window-bound/platform operations. This phase should standardize the fragile areas already identified in the codebase: preferences currently persist only theme, NPC column filtering is only partially surfaced, `VisibleNpcs` is rebuilt with full collection clears, and undo records can still capture mutable live model instances. [VERIFIED: src/BS2BG.App/Services/UserPreferencesService.cs] [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs] [VERIFIED: .planning/codebase/CONCERNS.md]

**Primary recommendation:** Add a small Phase 2 workflow infrastructure layer in `BS2BG.App`—`WorkflowPreferences`, remembered-folder-aware picker services, `NpcRowIdentity`/filter state, a centralized bulk-scope resolver, DynamicData-backed visible collections, and bounded value-snapshot undo records—while keeping `.jbs2bg`, BodyGen INI, BoS JSON, slider math, and profile fallback semantics unchanged. [VERIFIED: .planning/phases/02-workflow-persistence-filtering-and-undo-hardening/02-CONTEXT.md] [CITED: github.com/avaloniaui/avalonia-docs/docs/concepts/reactiveui/binding-to-sorted-filtered-list.md]

## Project Constraints (from AGENTS.md)

- Preserve byte-identical output semantics for formatter/export code; do not edit `tests/fixtures/expected/**`, `JavaFloatFormatting.cs`, `SliderMathFormatter.cs`, `BodyGenIniExportWriter.cs`, or `BosJsonExportWriter.cs` without explicit caution. [VERIFIED: AGENTS.md]
- `BS2BG.Core` must remain UI-free and target `netstandard2.1`; Avalonia/platform concerns belong in `BS2BG.App`. [VERIFIED: AGENTS.md] [VERIFIED: src/BS2BG.Core/BS2BG.Core.csproj]
- New Avalonia AXAML must use `.axaml`, declare `x:DataType` on roots and `DataTemplate`s, and honor compiled bindings enabled in `BS2BG.App.csproj`. [VERIFIED: AGENTS.md] [VERIFIED: src/BS2BG.App/BS2BG.App.csproj] [CITED: docs.avaloniaui.net/docs/xaml/compilation]
- App ViewModels must use ReactiveUI `ReactiveObject`, `[Reactive]`, `[ObservableAsProperty]`, `ReactiveCommand`, observable `canExecute`, and ReactiveUI schedulers; do not reintroduce RelayCommand or direct ViewModel dispatcher calls. [VERIFIED: AGENTS.md] [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md]
- `MainWindow` remains a plain `Avalonia.Controls.Window`, not `ReactiveWindow`. [VERIFIED: AGENTS.md] [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md]
- Tests use xUnit v3, Avalonia.Headless.XUnit, and FluentAssertions; new tests should use FluentAssertions style. [VERIFIED: AGENTS.md] [VERIFIED: tests/BS2BG.Tests/BS2BG.Tests.csproj]
- Use PowerShell commands on Windows and never redirect to `nul`; this affects any planner-generated validation commands. [VERIFIED: AGENTS.md]
- Comments and XML doc comments are expected for non-obvious logic and new/substantially rewritten methods; do not delete accurate comments as cleanup. [VERIFIED: C:/Users/evild/.config/kilo/AGENTS.md]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Local workflow preferences | Platform Adapter / App Service | ViewModel | Preferences are local machine convenience state stored by `UserPreferencesService`, not domain project data. [VERIFIED: src/BS2BG.App/Services/UserPreferencesService.cs] [VERIFIED: 02-CONTEXT.md D-01] |
| Remembered folder picker state | Platform Adapter / App Service | Avalonia View | Avalonia `StorageProvider` supports suggested start locations via picker options, and current window-bound picker services are the integration point. [CITED: docs.avaloniaui.net/docs/services/storage/file-picker-options] [VERIFIED: src/BS2BG.App/Services/WindowFileDialogService.cs] |
| NPC stable UI identity | Domain Model or App Workflow Model | ViewModel | Row identity must survive filtering/selection/undo, while existing `Mod`, `EditorId`, and `FormId` remain display/export data. [VERIFIED: 02-CONTEXT.md D-05] [VERIFIED: src/BS2BG.Core/Models/Npc.cs] |
| Per-column filtering | ViewModel / App Workflow Helper | Avalonia View | Filter predicates and source collections belong in testable App/ViewModel helpers; popups/checklists are view composition. [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs] [CITED: github.com/avaloniaui/avalonia-docs/docs/data-binding/collection-views.md] |
| Explicit bulk scopes | ViewModel / App Workflow Helper | Dialog Service | Scope resolution depends on visible/selected row state, while destructive all-scope confirmation uses App dialog services. [VERIFIED: 02-CONTEXT.md D-09-D-12] |
| Undo/redo hardening | App Service + ViewModel snapshot helpers | Core value DTOs if UI-free | Existing undo service is an App service; snapshots may be pure DTOs but should not serialize whole projects or capture mutable live references unsafely. [VERIFIED: src/BS2BG.App/Services/UndoRedoService.cs] [VERIFIED: 02-CONTEXT.md D-13] |
| Large dataset responsiveness | ViewModel pipeline | Core parser/import services | Parser results originate in Core, but UI freeze prevention is App scheduling, debouncing, virtualization, and collection update strategy. [VERIFIED: src/BS2BG.Core/Import/NpcTextParser.cs] [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md] |
| Tests | Test project | App/Core | Existing test project covers ViewModels, services, Core parity, and headless UI; Phase 2 should add focused unit/headless/stress tests there. [VERIFIED: tests/BS2BG.Tests/BS2BG.Tests.csproj] [VERIFIED: tests/BS2BG.Tests/*Tests.cs glob] |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| .NET SDK / runtime | 10.0.203 SDK, 10.0.7 host | Build and run App/tests targeting `net10.0`; Core remains `netstandard2.1`. | Installed locally and project targets .NET 10 for App/tests. [VERIFIED: dotnet --info] [VERIFIED: src/BS2BG.App/BS2BG.App.csproj] |
| Avalonia | 12.0.1 | Desktop UI, AXAML, controls, storage provider, compiled binding support. | Existing App stack; compiled bindings enabled project-wide. [VERIFIED: Directory.Packages.props] [VERIFIED: src/BS2BG.App/BS2BG.App.csproj] |
| ReactiveUI.Avalonia | 12.0.1 | Reactive ViewModel/UI integration for Avalonia. | Existing project convention and DI integration. [VERIFIED: Directory.Packages.props] [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md] |
| ReactiveUI.SourceGenerators | 2.6.1 requested; 2.6.30 latest | `[Reactive]` and `[ObservableAsProperty]` source generation. | Current project uses source generators and `dotnet list package --outdated` reports only this package as newer. [VERIFIED: Directory.Packages.props] [VERIFIED: dotnet list package --outdated] |
| DynamicData | 9.4.31 latest | Keyed reactive collection source, filtering, sorting, binding to `ReadOnlyObservableCollection<T>`. | Avalonia docs recommend DynamicData for complex/large reactive filtering; `SourceCache<TObject,TKey>` provides keyed identity. [VERIFIED: dotnet package search DynamicData] [CITED: github.com/avaloniaui/avalonia-docs/docs/concepts/reactiveui/binding-to-sorted-filtered-list.md] [CITED: reactiveui.net/api/DynamicData.SourceCache-2.html] |
| System.Text.Json | 10.0.7 | Local user preference JSON serialization/deserialization. | Existing service uses it; Microsoft docs document `JsonSerializer.Serialize` and `WriteIndented`. [VERIFIED: Directory.Packages.props] [VERIFIED: src/BS2BG.App/Services/UserPreferencesService.cs] [CITED: learn.microsoft.com/dotnet/standard/serialization/system-text-json/how-to] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Microsoft.Extensions.DependencyInjection | 10.0.7 | App service/ViewModel registration. | Register new preference, filtering, scope, and undo helper services if extracted. [VERIFIED: Directory.Packages.props] [VERIFIED: src/BS2BG.App/AppBootstrapper.cs] |
| Avalonia.Headless.XUnit | 12.0.1 | Headless UI/shell tests. | Use for AXAML/control wiring tests around filter popups, scope selector, and hidden selection preservation. [VERIFIED: Directory.Packages.props] [VERIFIED: tests/BS2BG.Tests/BS2BG.Tests.csproj] |
| xunit.v3 | 3.2.2 | Unit test framework. | Use for ViewModel/service/Core helper tests. [VERIFIED: Directory.Packages.props] |
| FluentAssertions | 8.9.0 | Assertion style. | Use for all new tests. [VERIFIED: Directory.Packages.props] [VERIFIED: AGENTS.md] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| DynamicData `SourceCache<NpcRow, Guid>` | Manual `ObservableCollection.Clear()` + repopulate | Manual filtering is documented as a simple pattern, but it is already the project's bottleneck and causes full UI churn on each search/filter/property change. [CITED: github.com/avaloniaui/avalonia-docs/docs/data-binding/collection-views.md] [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs] |
| Stable generated row ID | `(Mod, EditorId)` key | Existing import de-dupes by mod/editor ID, but Phase 2 explicitly says field values are domain/display/export data and not the sole UI identity. [VERIFIED: openspec/specs/morph-assignment-flow/spec.md] [VERIFIED: 02-CONTEXT.md D-05] |
| Targeted value snapshots | Full project snapshots / serialized history | User locked targeted snapshots; full snapshots would increase memory pressure and risk changing project serialization semantics. [VERIFIED: 02-CONTEXT.md D-13] |
| Preference JSON in AppData | `.jbs2bg` schema change | User locked local preference persistence for omit redundant sliders and folders, so project schema changes are out of scope. [VERIFIED: 02-CONTEXT.md D-01-D-03] |

**Installation:**
```powershell
# Add only if planner chooses DynamicData-backed filtering.
dotnet add src/BS2BG.App/BS2BG.App.csproj package DynamicData
```

**Version verification:** `dotnet list "BS2BG.sln" package --outdated` verified current packages against NuGet and found only `ReactiveUI.SourceGenerators` newer than requested; `dotnet package search DynamicData --take 5` verified DynamicData latest as `9.4.31`. Publish dates were not reported by the CLI output captured in this session. [VERIFIED: dotnet list package --outdated] [VERIFIED: dotnet package search DynamicData]

## Architecture Patterns

### System Architecture Diagram

```text
App startup
  |
  v
MainWindowViewModel loads UserPreferences ---------------------------+
  |                                                                  |
  +--> TemplatesViewModel hydrates OmitRedundantSliders              |
  +--> Window picker services receive remembered folder seeds        |
  |                                                                  |
User imports NPC / edits presets / changes filters                   |
  |                                                                  |
  v                                                                  |
Core parser/domain service returns mutable domain models              |
  |                                                                  |
  v                                                                  |
App workflow layer assigns StableRowId + updates SourceCache          |
  |                                                                  |
  +--> Filter state (checklists + debounced text) -> DynamicData view |
  |                                          |                       |
  |                                          v                       |
  |                                  Visible rows in UI              |
  |                                                                  |
User chooses bulk scope (All / Visible / Selected / Visible Empty)    |
  |                                                                  |
  v                                                                  |
Scope resolver materializes target StableRowIds before mutation       |
  |                                                                  |
  v                                                                  |
Domain mutation via MorphAssignmentService / ProjectModel             |
  |                                                                  |
  v                                                                  |
UndoRedoService records one bounded value-snapshot operation          |
  |                                                                  |
  +--> Status message if history pruning occurs                       |
  +--> Preferences saved best-effort on relevant changes <------------+
```

This diagram follows the existing App/ViewModel over Core service boundary and adds workflow helpers only where Phase 2 requires identity, scopes, and bounded undo behavior. [VERIFIED: .planning/codebase/ARCHITECTURE.md] [VERIFIED: 02-CONTEXT.md]

### Recommended Project Structure

```text
src/
├── BS2BG.App/
│   ├── Services/
│   │   ├── UserPreferencesService.cs          # extend current local preferences DTO/service
│   │   ├── WindowFileDialogService.cs         # add remembered project/export folder seeds
│   │   ├── WindowBodySlideXmlFilePicker.cs    # add remembered XML import folder seed
│   │   ├── WindowNpcTextFilePicker.cs         # add remembered NPC import folder seed
│   │   └── UndoRedoService.cs                 # add bounded history and pruning notification
│   ├── ViewModels/
│   │   ├── MorphsViewModel.cs                 # compose filter/scope/selection helpers
│   │   ├── TemplatesViewModel.cs              # hydrate/save omit redundant and profile undo snapshots
│   │   └── Workflow/                          # new focused App-layer helpers if extraction is needed
│   │       ├── NpcFilterState.cs
│   │       ├── NpcBulkScopeResolver.cs
│   │       └── UndoSnapshots.cs
│   └── Views/
│       └── MainWindow.axaml                   # add compiled-bound filter popups and scope selector
├── BS2BG.Core/
│   └── Models/
│       └── Npc.cs                             # add UI-neutral stable row ID only if chosen here
└── BS2BG.Tests/
    ├── UserPreferencesServiceTests.cs
    ├── MorphsViewModelTests.cs
    ├── TemplatesViewModelTests.cs
    ├── MainWindowViewModelTests.cs
    ├── M6UxViewModelTests.cs
    └── M6UxAppShellTests.cs
```

The App helper folder is recommended because `MorphsViewModel` and `TemplatesViewModel` are already large orchestration classes, and the codebase concern audit recommends extracting focused filtering, scope, import, and undo helpers while keeping UI-specific logic out of Core. [VERIFIED: .planning/codebase/CONCERNS.md]

### Pattern 1: Preferences Are Local, Best-Effort, and Backward-Compatible

**What:** Extend `UserPreferences` with nullable/string folder paths and `OmitRedundantSliders`, keep `Theme` compatible, and treat missing/corrupt/unauthorized preference data as defaults. [VERIFIED: src/BS2BG.App/Services/UserPreferencesService.cs] [VERIFIED: 02-CONTEXT.md D-01-D-03]

**When to use:** Use for local workflow state that should survive app restarts but should not affect shared `.jbs2bg` project semantics. [VERIFIED: 02-CONTEXT.md D-01]

**Example:**
```csharp
// Source: existing UserPreferencesService pattern + Microsoft System.Text.Json docs.
public sealed class UserPreferences
{
    public ThemePreference Theme { get; set; } = ThemePreference.System;
    public bool OmitRedundantSliders { get; set; }
    public string? ProjectFolder { get; set; }
    public string? BodySlideXmlFolder { get; set; }
    public string? NpcTextFolder { get; set; }
    public string? BodyGenExportFolder { get; set; }
    public string? BosJsonExportFolder { get; set; }
}
```

`System.Text.Json` serializes public properties by default, and `WriteIndented = true` pretty-prints output when configured. [CITED: learn.microsoft.com/dotnet/standard/serialization/system-text-json/how-to]

### Pattern 2: Remembered Folders Feed Avalonia Picker Options

**What:** Store only local folder paths/bookmarks in preferences, resolve them in window-bound picker services, and pass valid locations to `SuggestedStartLocation`. [CITED: docs.avaloniaui.net/docs/services/storage/file-picker-options] [VERIFIED: src/BS2BG.App/Services/WindowFileDialogService.cs]

**When to use:** Use for project open/save, XML import, NPC text import, BodyGen export folder, and BoS JSON export folder as separate channels. [VERIFIED: 02-CONTEXT.md D-02]

**Example:**
```csharp
// Source: Avalonia File Picker Options docs.
var start = string.IsNullOrWhiteSpace(preferences.BodySlideXmlFolder)
    ? null
    : await owner.StorageProvider.TryGetFolderFromPathAsync(preferences.BodySlideXmlFolder);

var files = await owner.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
{
    Title = "Add BodySlide XML Presets",
    AllowMultiple = true,
    SuggestedStartLocation = start,
    FileTypeFilter = new[] { CreateBodySlideXmlType() }
});
```

Avalonia documents that `SuggestedStartLocation` is a suggestion and can be ignored if unsupported, inaccessible, or nonexistent. [CITED: docs.avaloniaui.net/docs/services/storage/file-picker-options]

### Pattern 3: Keyed NPC Rows Drive Filtering and Selection

**What:** Give every NPC row a generated stable ID and feed a keyed source (`SourceCache<NpcRowViewModel, Guid>` or equivalent) into filtered visible collections. [VERIFIED: 02-CONTEXT.md D-05] [CITED: reactiveui.net/api/DynamicData.SourceCache-2.html]

**When to use:** Use for all UI identity, selection preservation, visible-row filtering, and undo targeting; do not use mutable display/export fields as the sole row key. [VERIFIED: 02-CONTEXT.md D-05-D-07]

**Example:**
```csharp
// Source: Avalonia docs binding to sorted/filtered data + DynamicData SourceCache API.
private readonly SourceCache<NpcRowViewModel, Guid> npcRows = new(row => row.RowId);
private readonly ReadOnlyObservableCollection<NpcRowViewModel> visibleNpcRows;

npcRows.Connect()
    .Filter(this.WhenAnyValue(x => x.NpcFilterState).Select(state => state.CreatePredicate()))
    .Sort(SortExpressionComparer<NpcRowViewModel>.Ascending(row => row.Npc.Name))
    .Bind(out visibleNpcRows)
    .Subscribe()
    .DisposeWith(disposables);
```

Avalonia docs state sorted/filtered data can be created by connecting a `SourceCache<TObject,TKey>` or `SourceList<T>` to a `ReadOnlyObservableCollection<T>` and binding to that collection. [CITED: github.com/avaloniaui/avalonia-docs/docs/concepts/reactiveui/binding-to-sorted-filtered-list.md]

### Pattern 4: Explicit Bulk Scope Resolver

**What:** Resolve `All`, `Visible`, `Selected`, and `VisibleEmpty` to a materialized target ID array before making any mutation. [VERIFIED: 02-CONTEXT.md D-09-D-11]

**When to use:** Use for random fill, clear assignments, remove/clear NPCs, import-then-add operations, and any future bulk NPC action. [VERIFIED: 02-CONTEXT.md D-14]

**Example:**
```csharp
// Source: Phase 2 locked scope decisions.
public IReadOnlyList<Npc> ResolveTargets(NpcBulkScope scope)
{
    return scope switch
    {
        NpcBulkScope.All => Npcs.ToArray(),
        NpcBulkScope.Visible => VisibleNpcs.ToArray(),
        NpcBulkScope.Selected => SelectedNpcs.ToArray(),
        NpcBulkScope.VisibleEmpty => VisibleNpcs.Where(npc => npc.SliderPresets.Count == 0).ToArray(),
        _ => Array.Empty<Npc>()
    };
}
```

The important implementation detail is that the target list is snapped before mutation so filter changes or selection changes during the operation cannot change the affected rows. [ASSUMED]

### Pattern 5: Targeted Value Snapshots for Undo

**What:** Capture scalar values, row IDs, preset names/profile names, collection indexes, and assignment preset names/IDs needed to restore the affected operation; do not capture whole serialized projects or rely on mutable live object graphs alone. [VERIFIED: 02-CONTEXT.md D-13] [VERIFIED: .planning/codebase/CONCERNS.md]

**When to use:** Use for preset import/rename/remove/clear/profile changes, target add/remove/assignment changes, NPC import/add/remove/clear/assignment changes, and bulk operations. [VERIFIED: 02-CONTEXT.md D-14-D-16]

**Example:**
```csharp
// Source: existing undo pattern, hardened with value snapshot fields.
private sealed record NpcAssignmentSnapshot(
    Guid RowId,
    int Index,
    string Mod,
    string Name,
    string EditorId,
    string Race,
    string FormId,
    string[] AssignedPresetNames);
```

Existing tests already validate some undo/redo flows, but the concern audit flags live object references as fragile for interleaved rename, assignment, removal, import, and clear operations. [VERIFIED: tests/BS2BG.Tests/MorphsViewModelTests.cs] [VERIFIED: .planning/codebase/CONCERNS.md]

### Pattern 6: Debounced Text Filters, Immediate Checklist Filters

**What:** Text filters should be debounced before updating the filter predicate, while checklist changes can apply immediately. [VERIFIED: 02-CONTEXT.md D-08]

**When to use:** Use for NPC global search and in-popup value-list search for long value lists; do not debounce checkbox toggles unless tests prove the UI needs it. [VERIFIED: 02-CONTEXT.md D-06-D-08]

**Example:**
```csharp
// Source: ReactiveUI WhenAnyValue pattern from project convention.
this.WhenAnyValue(x => x.NpcDatabaseSearchText)
    .Throttle(TimeSpan.FromMilliseconds(200), RxApp.MainThreadScheduler)
    .DistinctUntilChanged()
    .Subscribe(_ => RefreshNpcFilterPredicate())
    .DisposeWith(disposables);
```

The exact debounce interval is intentionally discretionary and must be validated with large-dataset tests. [VERIFIED: 02-CONTEXT.md lines 40-44]

### Anti-Patterns to Avoid

- **Persisting filters across restarts:** User locked filters as session-only because stale filters can make rows appear missing. [VERIFIED: 02-CONTEXT.md D-04]
- **Using `Mod`/`EditorId`/`FormId` as sole UI identity:** These are domain/display/export fields and can be duplicated or edited; generated stable row IDs are locked. [VERIFIED: 02-CONTEXT.md D-05]
- **Full collection clear/repopulate on every keystroke:** Current code does this and the concern audit identifies it as a performance bottleneck. [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs] [VERIFIED: .planning/codebase/CONCERNS.md]
- **One undo entry per affected row:** User locked one undoable user action per bulk operation. [VERIFIED: 02-CONTEXT.md D-16]
- **Full project or serialized snapshots for routine undo:** User locked targeted value snapshots rather than full project snapshots. [VERIFIED: 02-CONTEXT.md D-13]
- **Dispatcher calls inside ViewModels:** Project ReactiveUI convention forbids direct `Dispatcher.UIThread` ViewModel calls. [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md]
- **Changing `.jbs2bg` project schema for preferences:** `OmitRedundantSliders` and folders are local app preferences for this phase. [VERIFIED: 02-CONTEXT.md D-01-D-02]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Large reactive filtering/sorting | Custom full-scan `ObservableCollection.Clear()`/add loops for every change | DynamicData `SourceCache`/`SourceList` + `.Filter()` + `.Bind()` | Avalonia docs recommend DynamicData for complex/large reactive collections and current full rebuild code is a known bottleneck. [CITED: github.com/avaloniaui/avalonia-docs/docs/app-development/performance.md] [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs] |
| File/folder dialog seeding | Manual OS-specific dialog paths | Avalonia `StorageProvider` with `SuggestedStartLocation` and `TryGetFolderFromPathAsync` | Avalonia provides these picker options and documents platform caveats. [CITED: docs.avaloniaui.net/docs/services/storage/file-picker-options] [CITED: docs.avaloniaui.net/docs/services/storage/storage-provider] |
| Preference serialization | Custom JSON string concatenation or `.jbs2bg` schema changes | Existing `UserPreferencesService` + `System.Text.Json` | Existing service already implements safe fallback behavior and System.Text.Json supports POCO serialization. [VERIFIED: src/BS2BG.App/Services/UserPreferencesService.cs] [CITED: learn.microsoft.com/dotnet/standard/serialization/system-text-json/how-to] |
| Command/busy/canExecute wiring | Relay commands or manual raise-can-execute infrastructure | ReactiveUI `ReactiveCommand.Create*`, `WhenAnyValue`, `ToProperty` | Project spec requires ReactiveCommand and observable `canExecute`; RelayCommand types are retired. [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md] |
| Undo history | Serialized project history, Memento of whole aggregate, or unbounded closure stacks | Existing `UndoRedoService` shape with bounded value-snapshot entries | User locked targeted snapshots and bounded history with pruning status. [VERIFIED: 02-CONTEXT.md D-13-D-16] |
| UI virtualization | Non-virtualizing `ItemsControl` for large row lists | Avalonia virtualizing list/grid controls and existing ListBox/DataGrid patterns | Avalonia performance docs warn that non-virtualizing panels create all items upfront and recommend virtualizing controls for large lists. [CITED: docs.avaloniaui.net/docs/how-to/debugging-how-to] |
| Profile/output logic | New formatter/export path while hardening workflow | Existing Core services and writers | Output parity is load-bearing and Phase 2 must not change slider math/export semantics. [VERIFIED: AGENTS.md] [VERIFIED: 02-CONTEXT.md Phase Boundary] |

**Key insight:** The hard part is not implementing checkboxes or JSON fields; it is preventing identity, filter visibility, and undo target selection from depending on mutable live UI state at the moment undo/redo or bulk mutation replays. [VERIFIED: 02-CONTEXT.md D-05-D-16] [VERIFIED: .planning/codebase/CONCERNS.md]

## Common Pitfalls

### Pitfall 1: Hidden Rows Mutated by “Visible” Commands
**What goes wrong:** A command evaluates `VisibleNpcs` after filter/selection state has changed, or falls back to all rows when filtered collections are empty. [ASSUMED]
**Why it happens:** Scope logic is embedded separately in each command rather than centralized and tested. [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs]
**How to avoid:** Add one `NpcBulkScopeResolver` and tests for `All`, `Visible`, `Selected`, and `VisibleEmpty` with active filters. [VERIFIED: 02-CONTEXT.md D-09-D-12]
**Warning signs:** Multiple commands call `VisibleNpcs.ToArray()` directly, scope labels disagree with affected count, or filtered hidden rows change in tests. [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs]

### Pitfall 2: Selection Loss When Filters Change
**What goes wrong:** Selected hidden NPCs are cleared because selection is object/reference-based against visible collections. [VERIFIED: 02-CONTEXT.md D-07]
**Why it happens:** Current selected state is an `ObservableCollection<Npc>` and visible lists are cleared/repopulated on filter refresh. [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs]
**How to avoid:** Store selected stable row IDs separately from the visible collection and project them back to row objects when commands need them. [VERIFIED: 02-CONTEXT.md D-05-D-07]
**Warning signs:** Applying a filter causes `SelectedNpcs` to drop items that are still present in the backing model. [ASSUMED]

### Pitfall 3: Live-Reference Undo Corruption
**What goes wrong:** An old undo restores the current mutated state rather than the state at operation time because the snapshot points to mutable `Npc`, `SliderPreset`, or `CustomMorphTarget` objects. [VERIFIED: .planning/codebase/CONCERNS.md]
**Why it happens:** Existing undo operations often capture closures and model instances. [VERIFIED: src/BS2BG.App/Services/UndoRedoService.cs] [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs]
**How to avoid:** Snapshot values by stable ID/name/index and resolve current objects at replay time, with explicit behavior when a referenced preset/NPC no longer exists. [ASSUMED]
**Warning signs:** Undo tests pass for simple sequences but fail for rename -> assign -> remove -> undo interleavings. [VERIFIED: .planning/codebase/CONCERNS.md]

### Pitfall 4: Unbounded Undo Memory Growth
**What goes wrong:** Long bulk-edit sessions retain large closures, arrays, and object graphs indefinitely. [VERIFIED: .planning/codebase/CONCERNS.md]
**Why it happens:** `UndoRedoService` currently uses unbounded stacks. [VERIFIED: src/BS2BG.App/Services/UndoRedoService.cs]
**How to avoid:** Add a configurable operation limit, prune oldest entries, clear redo on new record as today, and surface a non-blocking status event when pruning occurs. [VERIFIED: 02-CONTEXT.md D-15]
**Warning signs:** Memory grows with repeated bulk imports/fills/clears and never drops after continuing to edit. [ASSUMED]

### Pitfall 5: Preference Failures Blocking Real Work
**What goes wrong:** Startup, import, generation, save, or export fails because preference load/save is corrupt, unauthorized, or on an unavailable path. [VERIFIED: 02-CONTEXT.md D-03]
**Why it happens:** Convenience-state persistence is treated like project persistence. [VERIFIED: 02-CONTEXT.md D-03]
**How to avoid:** Keep the existing `Load()` fallback and `Save()` false-return pattern; status messages may be non-blocking, but workflow commands must continue. [VERIFIED: src/BS2BG.App/Services/UserPreferencesService.cs]
**Warning signs:** Tests require preference save success before a file picker/import/export command proceeds. [ASSUMED]

### Pitfall 6: Avalonia Binding Breaks After Adding Filter UI
**What goes wrong:** AXAML compiles fail or runtime binding errors appear because new popup `DataTemplate`s lack `x:DataType` or bindings target the wrong DataContext. [CITED: docs.avaloniaui.net/docs/xaml/compilation]
**Why it happens:** Avalonia compiled bindings require a data type in scope when enabled project-wide. [VERIFIED: src/BS2BG.App/BS2BG.App.csproj] [CITED: docs.avaloniaui.net/docs/xaml/compilation]
**How to avoid:** Add `x:DataType` on new roots/templates and use `ReflectionBinding` only when justified. [CITED: docs.avaloniaui.net/docs/xaml/directives]
**Warning signs:** Build error “Cannot use compiled binding without a DataType” or missing filter values at runtime. [CITED: docs.avaloniaui.net/docs/xaml/compilation]

### Pitfall 7: `Task.Run` Bypasses ReactiveUI Scheduler Contracts
**What goes wrong:** Tests and app behavior diverge because background work uses `Task.Run` instead of `RxApp.TaskpoolScheduler`, while tests pin ReactiveUI schedulers. [VERIFIED: .planning/codebase/CONCERNS.md] [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md]
**Why it happens:** Current import paths call `Task.Run` in ViewModels. [VERIFIED: src/BS2BG.App/ViewModels/TemplatesViewModel.cs] [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs]
**How to avoid:** For new long-running/filter/import work, use ReactiveUI scheduler-aware pipelines or commands consistent with the OpenSpec convention. [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md]
**Warning signs:** Cancellation tests are flaky or command `IsExecuting` does not reflect work consistently. [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md]

## Code Examples

Verified patterns from official or project sources:

### Avalonia Picker With Suggested Start Folder
```csharp
// Source: https://docs.avaloniaui.net/docs/services/storage/file-picker-options
var folder = await owner.StorageProvider.TryGetFolderFromPathAsync(preferences.ProjectFolder);
var files = await owner.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
{
    Title = "Open jBS2BG File",
    AllowMultiple = false,
    SuggestedStartLocation = folder,
    FileTypeFilter = new[] { CreateProjectFileType() }
});
```

### DynamicData Keyed Filtered View
```csharp
// Source: https://github.com/avaloniaui/avalonia-docs/blob/main/docs/concepts/reactiveui/binding-to-sorted-filtered-list.md
private readonly SourceCache<NpcRowViewModel, Guid> rows = new(row => row.RowId);
private readonly ReadOnlyObservableCollection<NpcRowViewModel> visibleRows;

rows.Connect()
    .Filter(filterPredicateObservable)
    .Bind(out visibleRows)
    .Subscribe()
    .DisposeWith(disposables);
```

### ReactiveUI Debounced Search
```csharp
// Source: project ReactiveUI convention in openspec/specs/reactive-mvvm-conventions/spec.md
this.WhenAnyValue(x => x.SearchText)
    .Throttle(TimeSpan.FromMilliseconds(200), RxApp.MainThreadScheduler)
    .DistinctUntilChanged()
    .Subscribe(_ => RefreshVisibleRows())
    .DisposeWith(disposables);
```

### Bounded Undo Service Shape
```csharp
// Source: existing UndoRedoService shape + Phase 2 D-15.
public void Record(string name, Action undo, Action redo)
{
    if (IsReplaying) return;

    undoStack.Push(new UndoRedoOperation(name, undo, redo));
    redoStack.Clear();
    PruneOldestIfNeeded();
    StateChanged?.Invoke(this, EventArgs.Empty);
}
```

### Value Snapshot Instead of Live Target Snapshot
```csharp
// Source: Phase 2 D-13 targeted value snapshots.
private sealed record PresetValueSnapshot(
    string Name,
    string ProfileName,
    SetSliderValueSnapshot[] SetSliders,
    SetSliderValueSnapshot[] MissingDefaultSetSliders);
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual `ObservableCollection` filter rebuilds for simple lists | DynamicData-backed source collections for complex/large reactive filtering | Current Avalonia docs recommend DynamicData for complex scenarios; project has not adopted it yet. [CITED: github.com/avaloniaui/avalonia-docs/docs/data-binding/collection-views.md] | Use DynamicData in Phase 2 for NPC rows rather than expanding manual rebuild logic. [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs] |
| Field-based row identity | Generated stable row IDs | Locked in Phase 2 discussion. [VERIFIED: 02-CONTEXT.md D-05] | Selection, filtering, and undo must key by generated ID, not by mutable domain fields. |
| Theme-only preferences | Workflow preferences with separate remembered folders and omit redundant sliders | Locked in Phase 2 discussion. [VERIFIED: 02-CONTEXT.md D-01-D-03] | Extend current preferences without changing project serialization. |
| Unbounded closure undo | Bounded targeted value-snapshot undo | Locked in Phase 2 discussion. [VERIFIED: 02-CONTEXT.md D-13-D-16] | Add history limits and avoid live mutable snapshot corruption. |
| Direct `Task.Run` in ViewModels | ReactiveUI scheduler-aware background work | Captured as current convention and known concern. [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md] [VERIFIED: .planning/codebase/CONCERNS.md] | New Phase 2 long-running work should use `RxApp.TaskpoolScheduler` patterns. |

**Deprecated/outdated:**
- Custom RelayCommand / AsyncRelayCommand patterns are retired in this project; use ReactiveCommand. [VERIFIED: AGENTS.md] [VERIFIED: openspec/specs/reactive-mvvm-conventions/spec.md]
- WPF `DataTemplateSelector`, `DependencyProperty`, `Visibility`, and WPF XAML assumptions are not Avalonia patterns. [CITED: docs.avaloniaui.net Avalonia expert rules] [CITED: docs.avaloniaui.net/docs/migration/wpf/data-templates]
- Expanding the race-only filter path with more one-off column dictionaries is outdated for Phase 2; use a reusable filter state/helper. [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs] [VERIFIED: 02-CONTEXT.md D-06]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Materializing scope target lists before mutation is necessary to prevent filter/selection changes during command execution from changing affected rows. | Architecture Patterns / Pattern 4 | Bulk commands might still be safe through existing synchronous execution, but explicit snapshotting remains lower-risk. |
| A2 | Warning signs around selection loss and memory growth are inferred from current architecture and common UI behavior, not directly reproduced in this session. | Common Pitfalls | Planner should create tests to validate these scenarios before broad refactors. |
| A3 | Undo replay should resolve current objects by stable ID/name/index with explicit missing-reference behavior. | Common Pitfalls | If implementation instead stores object references safely in some cases, tests must prove no live-reference corruption remains. |

## Open Questions

1. **Where should stable row ID live?**
   - What we know: User locked generated stable NPC row IDs and left storage location discretionary. [VERIFIED: 02-CONTEXT.md D-05]
   - What's unclear: Whether the ID should be a non-serialized `Npc` property in Core, an App-layer wrapper `NpcRowViewModel`, or a sidecar identity map. [ASSUMED]
   - Recommendation: Prefer an App-layer `NpcRowViewModel`/identity map first unless Core-level identity significantly simplifies project round-trip and undo; do not serialize row IDs into `.jbs2bg` unless a compatibility decision is made. [VERIFIED: 02-CONTEXT.md D-01-D-05]

2. **Exact undo history limit**
   - What we know: History must be bounded and pruning must surface a non-blocking status message. [VERIFIED: 02-CONTEXT.md D-15]
   - What's unclear: Operation count vs approximate memory budget. [ASSUMED]
   - Recommendation: Use an operation-count limit first (for example 100 user actions) because it is testable and deterministic; add memory-aware limits only if profiling proves necessary. [ASSUMED]

3. **DynamicData package adoption**
   - What we know: Avalonia docs recommend DynamicData for complex/large reactive collections, and NuGet latest is 9.4.31. [CITED: github.com/avaloniaui/avalonia-docs/docs/app-development/performance.md] [VERIFIED: dotnet package search DynamicData]
   - What's unclear: Whether maintainers prefer avoiding a new dependency. [ASSUMED]
   - Recommendation: Add `DynamicData` to `BS2BG.App` for NPC rows because Phase 2 explicitly requires large-dataset responsiveness and stable keyed filtering. [CITED: reactiveui.net/api/DynamicData.SourceCache-2.html]

4. **Filter UI control structure**
   - What we know: Existing MainWindow AXAML is monolithic, race filter popup exists, and Phase 2 requires checklist filters with in-popup search for multiple columns. [VERIFIED: .planning/codebase/CONCERNS.md] [VERIFIED: src/BS2BG.App/Views/MainWindow.axaml]
   - What's unclear: Whether to split views in Phase 2 or keep changes localized in `MainWindow.axaml`. [ASSUMED]
   - Recommendation: Extract a small reusable `NpcColumnFilterView` only if compiled-binding and code-behind wiring stay straightforward; otherwise add helper resources in MainWindow and defer broad view splitting. [VERIFIED: .planning/codebase/CONCERNS.md]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| .NET SDK | Build/test App and tests | ✓ | 10.0.203 | None needed. [VERIFIED: dotnet --info] |
| NuGet.org access | Package currency and adding DynamicData | ✓ | Source `https://api.nuget.org/v3/index.json` reachable | Avoid new dependency and use manual filtering if package access fails, but this is lower confidence for WORK-05. [VERIFIED: dotnet list package --outdated] |
| Git | Optional planner/executor status checks | ✓ | Path `C:\Git\cmd\git.exe` | None needed. [VERIFIED: Get-Command git] |
| Java | Golden fixture regeneration only | ✓ | Path available; version not probed | Do not regenerate golden fixtures for Phase 2. [VERIFIED: Get-Command java] [VERIFIED: AGENTS.md] |
| Avalonia docs MCP | Avalonia research | ✓ | Tool available | Use official web docs/Ref fallback. [VERIFIED: avalonia-docs_search_avalonia_docs tool results] |

**Missing dependencies with no fallback:** None detected for Phase 2 planning. [VERIFIED: dotnet --info] [VERIFIED: Get-Command]

**Missing dependencies with fallback:** None detected. DynamicData is not currently referenced by the project, but NuGet.org is reachable and the package is available. [VERIFIED: Directory.Packages.props] [VERIFIED: dotnet package search DynamicData]

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | xUnit v3 `3.2.2` + FluentAssertions `8.9.0` + Avalonia.Headless.XUnit `12.0.1`. [VERIFIED: Directory.Packages.props] |
| Config file | `tests/BS2BG.Tests/BS2BG.Tests.csproj`; no separate xunit config found in this research. [VERIFIED: tests/BS2BG.Tests/BS2BG.Tests.csproj] |
| Quick run command | `dotnet test --filter "FullyQualifiedName~UserPreferencesServiceTests|FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~TemplatesViewModelTests"` [VERIFIED: tests glob] |
| Full suite command | `dotnet test` [VERIFIED: AGENTS.md] |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| WORK-01 | Preferences persist theme, omit-redundant sliders, and separate folder paths; corrupt/unauthorized saves do not block. | unit/service + ViewModel | `dotnet test --filter FullyQualifiedName~UserPreferencesServiceTests` | ✅ |
| WORK-01 | File picker services seed and update project/XML/NPC/INI/JSON folders independently. | unit/service with fake picker/storage abstraction | `dotnet test --filter FullyQualifiedName~MainWindowViewModelTests` | ✅ partial; likely Wave 0 additions |
| WORK-02 | NPC filters cover mod, name, editor ID, form ID, race, assignment state, and preset values while selection by row ID survives hidden rows. | ViewModel unit + headless UI | `dotnet test --filter "FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~M6UxAppShellTests"` | ✅ partial; Wave 0 additions needed |
| WORK-03 | Bulk scopes `All`, `Visible`, `Selected`, `VisibleEmpty` affect only intended target IDs and produce correct status counts. | ViewModel unit | `dotnet test --filter FullyQualifiedName~MorphsViewModelTests` | ✅ partial; Wave 0 additions needed |
| WORK-04 | Undo/redo covers preset, target, NPC assignment, import, clear, and profile operations with value snapshots across interleavings. | ViewModel unit | `dotnet test --filter "FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~TemplatesViewModelTests|FullyQualifiedName~M6UxViewModelTests"` | ✅ partial; Wave 0 additions needed |
| WORK-05 | Large NPC/preset datasets avoid unbounded filter/import delays and UI collection churn. | unit stress/performance smoke | `dotnet test --filter "FullyQualifiedName~MorphsViewModelTests&FullyQualifiedName~Large"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `dotnet test --filter "FullyQualifiedName~UserPreferencesServiceTests|FullyQualifiedName~MorphsViewModelTests|FullyQualifiedName~TemplatesViewModelTests"` [VERIFIED: tests glob]
- **Per wave merge:** `dotnet test` [VERIFIED: AGENTS.md]
- **Phase gate:** Full suite green before `/gsd-verify-work`; do not update golden expected fixtures to silence failures. [VERIFIED: AGENTS.md]

### Wave 0 Gaps
- [ ] `tests/BS2BG.Tests/UserPreferencesServiceTests.cs` — add omit-redundant and separate folder path persistence/compatibility cases for WORK-01. [VERIFIED: file exists]
- [ ] `tests/BS2BG.Tests/MorphsViewModelTests.cs` — add stable row ID, hidden-selection preservation, all/visible/selected/visible-empty scope, and large dataset filter debounce tests for WORK-02/WORK-03/WORK-05. [VERIFIED: file exists]
- [ ] `tests/BS2BG.Tests/TemplatesViewModelTests.cs` — add omit-redundant hydration/save and profile operation value-snapshot undo tests for WORK-01/WORK-04. [VERIFIED: file exists]
- [ ] `tests/BS2BG.Tests/M6UxAppShellTests.cs` — add headless UI smoke tests for new filter column popups and scope selector compiled bindings for WORK-02/WORK-03. [VERIFIED: file exists]
- [ ] If DynamicData is added, update `Directory.Packages.props` and `BS2BG.App.csproj`, then add a focused ViewModel test proving filtered view updates without full selected-row loss. [VERIFIED: Directory.Packages.props] [CITED: github.com/avaloniaui/avalonia-docs/docs/concepts/reactiveui/binding-to-sorted-filtered-list.md]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no | Offline desktop app; no accounts or auth provider present. [VERIFIED: .planning/codebase/ARCHITECTURE.md] |
| V3 Session Management | no | No sessions; local workflow preferences only. [VERIFIED: .planning/PROJECT.md] |
| V4 Access Control | no | Local user-selected files; no multi-user access control layer. [VERIFIED: .planning/PROJECT.md] |
| V5 Input Validation | yes | Keep parser diagnostics for XML/NPC input and validate preference paths before using them as picker hints. [VERIFIED: .planning/codebase/CONCERNS.md] [CITED: docs.avaloniaui.net/docs/services/storage/file-picker-options] |
| V6 Cryptography | no | No cryptographic feature in Phase 2; do not add custom crypto. [VERIFIED: .planning/PROJECT.md] |

### Known Threat Patterns for Avalonia Local File Workflow

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed or huge local import files causing memory/CPU pressure | Denial of Service | Add large-input guardrail tests; keep parse failures diagnostic/non-crashing; consider size limits or streaming later if tests show need. [VERIFIED: .planning/codebase/CONCERNS.md] |
| Stored private local paths in preferences | Information Disclosure | Store only local preferences under AppData; never write remembered folders into `.jbs2bg`, exports, logs, or shared project bundles in this phase. [VERIFIED: 02-CONTEXT.md D-01-D-02] |
| Path traversal through NPC image lookup while adding filter/identity fields | Tampering / Information Disclosure | Preserve existing containment checks in `NpcImageLookupService`; Phase 2 should not loosen image candidate rules. [VERIFIED: .planning/codebase/CONCERNS.md] |
| Corrupt preferences blocking app workflows | Denial of Service | Catch `JsonException`, `IOException`, and `UnauthorizedAccessException`, returning defaults or false. [VERIFIED: src/BS2BG.App/Services/UserPreferencesService.cs] |

## Sources

### Primary (HIGH confidence)
- `AGENTS.md` — project stack, sacred files, ReactiveUI conventions, build/test commands, Windows/PowerShell rules. [VERIFIED]
- `.planning/phases/02-workflow-persistence-filtering-and-undo-hardening/02-CONTEXT.md` — locked Phase 2 decisions D-01 through D-16. [VERIFIED]
- `.planning/REQUIREMENTS.md` — WORK-01 through WORK-05 requirement text. [VERIFIED]
- `.planning/PROJECT.md`, `.planning/ROADMAP.md`, `.planning/STATE.md` — project constraints, phase status, roadmap success criteria. [VERIFIED]
- `.planning/codebase/ARCHITECTURE.md`, `CONVENTIONS.md`, `CONCERNS.md` — internal architecture, conventions, known risks and bottlenecks. [VERIFIED]
- `src/BS2BG.App/Services/UserPreferencesService.cs`, `UndoRedoService.cs`, `WindowFileDialogService.cs`, `WindowBodySlideXmlFilePicker.cs` — current integration points. [VERIFIED]
- `src/BS2BG.App/ViewModels/MorphsViewModel.cs`, `TemplatesViewModel.cs`, `src/BS2BG.Core/Models/Npc.cs` — filtering, undo, import, and identity-relevant code. [VERIFIED]
- `openspec/specs/reactive-mvvm-conventions/spec.md`, `template-generation-flow/spec.md`, `morph-assignment-flow/spec.md` — capability contracts. [VERIFIED]
- Avalonia docs: File Picker Options, Storage Provider, XAML compilation, x: directives, data templates, debugging/performance. [CITED: docs.avaloniaui.net]
- Avalonia docs via Ref: binding to sorted/filtered data, collection views, DynamicData performance note. [CITED: github.com/avaloniaui/avalonia-docs]
- ReactiveUI DynamicData API: `SourceCache<TObject,TKey>`. [CITED: reactiveui.net/api/DynamicData.SourceCache-2.html]
- Microsoft Learn: System.Text.Json serialization. [CITED: learn.microsoft.com/dotnet/standard/serialization/system-text-json/how-to]
- CLI verification: `dotnet --info`, `dotnet list "BS2BG.sln" package --outdated`, `dotnet package search DynamicData`. [VERIFIED]

### Secondary (MEDIUM confidence)
- Exa code/documentation search result identifying ReactiveUI DynamicData API and Avalonia sorted/filtered docs before official pages were read. [VERIFIED via follow-up official docs]

### Tertiary (LOW confidence)
- Assumptions in the Assumptions Log about command race warning signs, exact undo replay resolution strategy, and operation-count history limit. [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH for existing packages and .NET/Avalonia/ReactiveUI stack; MEDIUM for adding DynamicData because it is docs-recommended and version-verified but not yet a project dependency. [VERIFIED: Directory.Packages.props] [CITED: github.com/avaloniaui/avalonia-docs/docs/app-development/performance.md]
- Architecture: HIGH because context, codebase maps, and source files identify concrete integration points. [VERIFIED: .planning/codebase/ARCHITECTURE.md] [VERIFIED: src/BS2BG.App/ViewModels/MorphsViewModel.cs]
- Pitfalls: HIGH for known project bottlenecks and undo risks; MEDIUM for inferred warning signs not reproduced in this session. [VERIFIED: .planning/codebase/CONCERNS.md] [ASSUMED]
- Validation: HIGH for current test framework and file locations; MEDIUM for exact large-dataset thresholds because no benchmark target was specified. [VERIFIED: tests/BS2BG.Tests/BS2BG.Tests.csproj] [ASSUMED]

**Research date:** 2026-04-26
**Valid until:** 2026-05-03 for package/version-sensitive guidance; project-specific constraints remain valid until AGENTS.md/OpenSpec changes.
