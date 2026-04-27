---
phase: 02
slug: workflow-persistence-filtering-and-undo-hardening
status: verified
threats_open: 0
asvs_level: unspecified
created: 2026-04-26
audited_by: gsd-security-auditor
---

# Phase 02 - Security

Per-phase security contract: threat register, accepted risks, and audit trail.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| local preferences JSON -> App startup/ViewModels | Corrupt or user-edited local JSON crosses into workflow state. | Theme, workflow option, and remembered-folder preferences. |
| stored local path -> Avalonia picker hint | User-edited preference paths are used only as picker suggestions. | Project/export folder paths. |
| local path preference -> file picker | Stored user-editable paths are used as import picker hints. | BodySlide XML and NPC text import folders. |
| imported NPC data -> filter predicate/value lists | Untrusted local text data becomes searchable/filterable UI values. | Mod, name, editor ID, form ID, race, assignment state, preset text. |
| imported NPC rows -> ViewModel collection/filter pipeline | Local import data drives reactive filtering and selection state. | NPC row values, row IDs, visible collections, selected rows. |
| ViewModel filter values -> AXAML controls | Imported local data is displayed in popup value lists and badges. | Filter text and checklist values. |
| user-selected scope/filter state -> bulk mutation | UI state determines which NPC rows mutate. | Scope selection, visible rows, selected rows, empty-assignment state. |
| mutable ViewModel project state -> undo/redo replay | Stored undo callbacks replay after intervening mutations. | Preset, slider, profile, target, and NPC snapshots. |
| scoped row IDs/value snapshots -> undo replay | Undo replay applies historical user actions to current mutable project state. | Stable row IDs and before/after assignment snapshots. |

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-02-01-01 | D | UserPreferencesService.Load | mitigate | Catch `JsonException`, `IOException`, and `UnauthorizedAccessException` and return defaults per D-03. | closed |
| T-02-01-02 | I | UserPreferences | mitigate | Keep workflow preferences in AppData only; do not write local preference values into `.jbs2bg` project files per D-01. | closed |
| T-02-02-01 | D | WindowFileDialogService | mitigate | Treat `TryGetFolderFromPathAsync` failures/null as no suggested start location; continue picker workflow per D-03. | closed |
| T-02-02-02 | I | UserPreferences folder fields | mitigate | Store folder paths only in local AppData preferences; never write them into `.jbs2bg`, exports, logs, or shared artifacts. | closed |
| T-02-03-01 | D | WindowBodySlideXmlFilePicker / WindowNpcTextFilePicker | mitigate | Treat invalid/unresolvable paths as no start hint and keep import commands non-blocking per D-03. | closed |
| T-02-03-02 | I | UserPreferences import folders | mitigate | Do not serialize import folder paths or filter state into `.jbs2bg`; keep them local only per D-02/D-04. | closed |
| T-02-04-01 | D | NpcFilterState | mitigate | Keep predicate generation pure and side-effect free; add large-list unit coverage so malformed/large values do not mutate data. | closed |
| T-02-04-02 | T | NpcRowViewModel | mitigate | Generate row IDs independent of mutable display/export fields per D-05 and do not serialize them. | closed |
| T-02-05-01 | D | MorphsViewModel filtering | mitigate | Debounce free-text search and use keyed incremental filtering for large datasets per D-08/WORK-05. | closed |
| T-02-05-02 | T | SelectedNpcs / stable IDs | mitigate | Preserve selection by generated row ID instead of mutable fields or visible collection references per D-05/D-07. | closed |
| T-02-06-01 | I | MainWindow.axaml filter values | mitigate | Bind values as text through Avalonia controls; do not log/export filter values or paths. | closed |
| T-02-06-02 | D | MainWindow.axaml compiled bindings | mitigate | Add headless UI tests and run build to catch missing `x:DataType`/binding failures. | closed |
| T-02-07-01 | T | NpcBulkScopeResolver | mitigate | Materialize row IDs before mutation and test all/visible/selected/visible-empty exclusions. | closed |
| T-02-07-02 | R | destructive all-scope operations | mitigate | Require confirmation for destructive all-scope clear/remove operations and record one undoable operation. | closed |
| T-02-08-01 | T | TemplatesViewModel undo records | mitigate | Capture values/indexes in snapshot DTOs instead of mutable live object graphs per D-13. | closed |
| T-02-08-02 | D | UndoRedoService history | mitigate | Enforce bounded operation history and prune oldest entries with non-blocking status per D-15. | closed |
| T-02-09-01 | T | MorphsViewModel scoped undo | mitigate | Snapshot row IDs and before/after values before mutation; resolve current rows by ID at replay. | closed |
| T-02-09-02 | D | Large bulk undo records | mitigate | Keep one undo entry per bulk operation and rely on bounded history from Plan 08. | closed |

Status: open or closed.

Disposition: mitigate, accept, or transfer.

## Threat Verification

| Threat ID | Status | Evidence | Files/Tests |
|-----------|--------|----------|-------------|
| T-02-01-01 | CLOSED | `UserPreferencesService.Load` catches required exceptions and returns `new UserPreferences()` defaults. | `src/BS2BG.App/Services/UserPreferencesService.cs:54-77`; `tests/BS2BG.Tests/UserPreferencesServiceTests.cs` |
| T-02-01-02 | CLOSED | Workflow preference is loaded/saved through `IUserPreferencesService`; Core serialization contains no matching preference fields. | `src/BS2BG.App/ViewModels/TemplatesViewModel.cs:487-505`; Core grep |
| T-02-02-01 | CLOSED | Remembered project/export paths resolve to nullable start-folder hints; invalid/null hints continue picker calls. | `src/BS2BG.App/Services/WindowFileDialogService.cs:31-38,83-96,239-262`; tests |
| T-02-02-02 | CLOSED | Folder fields exist only on local `UserPreferences`; Core project serialization has no folder preference fields and App has no logging APIs. | `src/BS2BG.App/Services/UserPreferencesService.cs:21-29`; Core/App grep |
| T-02-03-01 | CLOSED | BodySlide XML and NPC text pickers resolve remembered folders to nullable hints and continue despite invalid hints or save failure. | `src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs`; `src/BS2BG.App/Services/WindowNpcTextFilePicker.cs`; tests |
| T-02-03-02 | CLOSED | Import folder paths are local `UserPreferences` fields only; NPC filter/search state is absent from preference JSON and Core serialization. | `src/BS2BG.App/Services/UserPreferencesService.cs`; `tests/BS2BG.Tests/MorphsViewModelTests.cs:874-888` |
| T-02-04-01 | CLOSED | `NpcFilterState.CreatePredicate` snapshots allowed values and returns a side-effect-free predicate; large-list test verifies no row mutation. | `src/BS2BG.App/ViewModels/Workflow/NpcFilterState.cs:117-145`; `tests/BS2BG.Tests/NpcFilterStateTests.cs:113-136` |
| T-02-04-02 | CLOSED | `NpcRowViewModel` generates App-layer `Guid RowId`, independent of mutable NPC fields; Core `Npc` has no `RowId`. | `src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs:16-30`; tests |
| T-02-05-01 | CLOSED | Debounce remains in place and visible NPC collections are now bound from keyed `SourceCache` rows through a DynamicData `.Connect().Filter().Sort().Transform().Bind()` pipeline. The removed `RefreshFilteredCollection`/`target.Clear()` path no longer exists, and regression coverage verifies checklist filtering updates visible rows without a collection reset. | `src/BS2BG.App/ViewModels/MorphsViewModel.cs:43-60,154-163,1328-1395`; `src/BS2BG.App/ViewModels/Workflow/NpcRowViewModel.cs:16-36,72-78`; `tests/BS2BG.Tests/MorphsViewModelTests.cs:580-629`; `dotnet test`; `dotnet build BS2BG.sln` |
| T-02-05-02 | CLOSED | Selection is tracked by generated row IDs and hidden selections are preserved when visible selection changes. | `src/BS2BG.App/ViewModels/MorphsViewModel.cs:54,1013-1045,1407-1428`; tests |
| T-02-06-01 | CLOSED | Filter values are bound as Avalonia text values; no App logging APIs were found. | `src/BS2BG.App/Views/MainWindow.axaml:742-913`; App grep |
| T-02-06-02 | CLOSED | MainWindow root and DataTemplates declare `x:DataType`; headless UI tests cover required filter controls and summary reports build passed. | `src/BS2BG.App/Views/MainWindow.axaml`; `tests/BS2BG.Tests/M6UxAppShellTests.cs`; `02-06-SUMMARY.md` |
| T-02-07-01 | CLOSED | `NpcBulkScopeResolver.Resolve` materializes `Guid[]` row IDs for all/visible/selected/visible-empty before mutation. | `src/BS2BG.App/ViewModels/Workflow/NpcBulkScopeResolver.cs:37-73`; tests |
| T-02-07-02 | CLOSED | Destructive all-scope clear/remove operations call confirmation before mutation and record one undoable operation. | `src/BS2BG.App/ViewModels/MorphsViewModel.cs:1236-1317`; dialog service/tests |
| T-02-08-01 | CLOSED | Template undo paths use value snapshots for preset/slider/profile state and restore from snapshot DTOs rather than removed/cleared live instances. | `src/BS2BG.App/ViewModels/Workflow/UndoSnapshots.cs`; `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`; tests |
| T-02-08-02 | CLOSED | `UndoRedoService` enforces bounded history, prunes oldest undo entries, and raises `HistoryPruned`; shell reports non-blocking status. | `src/BS2BG.App/Services/UndoRedoService.cs:5-16,82-88`; `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:154-157`; tests |
| T-02-09-01 | CLOSED | Morph scoped undo snapshots row IDs and before/after assignment values, then resolves current rows by stable ID at replay. | `src/BS2BG.App/ViewModels/Workflow/UndoSnapshots.cs:164-265`; `src/BS2BG.App/ViewModels/MorphsViewModel.cs:1763-1927`; tests |
| T-02-09-02 | CLOSED | Scoped bulk operations call `undoRedo.Record` once per bulk command and rely on bounded `UndoRedoService` history. | `src/BS2BG.App/ViewModels/MorphsViewModel.cs:1271-1317,1787-1796`; `src/BS2BG.App/Services/UndoRedoService.cs`; tests |

## Open Threats

None.

## Unregistered Flags

None. All plan summaries reported `## Threat Flags` as `None.`

## Accepted Risks Log

No accepted risks.

## Security Audit 2026-04-26

| Metric | Count |
|--------|-------|
| Threats found | 18 |
| Closed | 18 |
| Open | 0 |

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-26 | 18 | 17 | 1 | gsd-security-auditor |
| 2026-04-26 | 18 | 18 | 0 | Kilo remediation audit |
| 2026-04-26 | 18 | 18 | 0 | gsd-security-auditor remediation recheck |

Audit notes:

- Loaded all listed PLAN and SUMMARY artifacts for Phase 02 plans 01 through 09.
- All summaries reported `## Threat Flags` as `None.`
- Loaded primary implementation/test files listed in the audit prompt.
- Verified Core serialization isolation by grepping `src/BS2BG.Core/**/*.cs` for workflow preference, folder, and filter fields; no matches found.
- Verified absence of App logging APIs by grepping `src/BS2BG.App/**/*.cs` for common logging sinks; no matches found.
- Independently confirmed the open T-02-05-01 evidence by reading `MorphsViewModel.RefreshFilteredCollection`, which still calls `target.Clear()` and repopulates visible collections, and by searching for `.Connect(` in `MorphsViewModel.cs` with no matches.
- User gate decision: block advancement; no accepted risk recorded.
- Remediated T-02-05-01 by binding keyed `SourceCache<NpcRowViewModel, Guid>` caches into `VisibleNpcs` and `VisibleNpcDatabase` through incremental DynamicData filter/sort/transform bindings.
- Added `NpcRowViewModel.SortOrder` so incremental bindings preserve backing collection order across insert, remove, and undo restore operations.
- Added regression coverage proving checklist filtering does not emit a collection reset, which guards against the prior clear/rebuild behavior.
- Verified remediation with `dotnet test --filter FullyQualifiedName~MorphsViewModelTests`, `dotnet build BS2BG.sln`, `dotnet test`, and grep checks confirming `.Connect(` exists while `RefreshFilteredCollection`/`target.Clear()` no longer exist in `MorphsViewModel.cs`.
- Rechecked T-02-05-01 with `gsd-security-auditor`; result: `## SECURED`, correction needed: none.

## Sign-Off

- [x] All threats have a disposition: mitigate.
- [x] Accepted risks documented in Accepted Risks Log: none.
- [x] `threats_open: 0` confirmed.
- [x] `status: verified` set in frontmatter.

Approval: verified 2026-04-26 after remediation of T-02-05-01.
