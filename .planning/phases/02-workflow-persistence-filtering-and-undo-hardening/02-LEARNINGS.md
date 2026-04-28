---
phase: 02
phase_name: "workflow-persistence-filtering-and-undo-hardening"
project: "BS2BG (Bodyslide to Bodygen)"
generated: "2026-04-28T00:27:28-07:00"
counts:
  decisions: 10
  lessons: 9
  patterns: 9
  surprises: 7
missing_artifacts: []
---

# Phase 02 Learnings: workflow-persistence-filtering-and-undo-hardening

## Decisions

### Keep Workflow Preferences Local
`OmitRedundantSliders` persists only through local user preferences, not through `.jbs2bg` project serialization.

**Rationale:** The setting affects local workflow convenience and generation behavior, but project files must remain shareable and unchanged.
**Source:** 02-01-SUMMARY.md

---

### Treat Preference Persistence As Best Effort
Preference load/save failures return defaults, status messages, or false without blocking startup, import, generation, save, or export workflows.

**Rationale:** Local preference state is convenience state and must not prevent core workflows from completing.
**Source:** 02-01-SUMMARY.md

---

### Use Independent Folder Channels
Project files, BodyGen exports, BoS JSON exports, BodySlide XML imports, and NPC text imports each get separate local folder preference channels.

**Rationale:** Users navigate different filesystem areas for each workflow; collapsing them into one global last-used folder would lose useful context.
**Source:** 02-02-SUMMARY.md

---

### Keep Remembered Paths Advisory
Invalid or inaccessible remembered paths resolve to no picker hint and never cancel the underlying workflow.

**Rationale:** Stored paths are user-editable local data and can become stale; they should only influence `SuggestedStartLocation` when valid.
**Source:** 02-02-SUMMARY.md

---

### Keep NPC Row Identity In App Workflow Wrappers
Generated `RowId` values live in `NpcRowViewModel` rather than Core `Npc` or project serialization.

**Rationale:** Filtering, selection preservation, and undo targeting need stable UI identity, but Core output and `.jbs2bg` files must remain unchanged.
**Source:** 02-04-SUMMARY.md

---

### Preserve Public NPC Collections While Adding Stable Sidecars
`MorphsViewModel` preserves public `ObservableCollection<Npc>` surfaces while using `SourceCache<NpcRowViewModel, Guid>` internally.

**Rationale:** This avoided binding churn before the UI phase while allowing stable row identity, hidden selection preservation, and incremental filtering.
**Source:** 02-05-SUMMARY.md

---

### Keep Multi-Select Checklist Glue In The View
Checklist popup selection forwarding lives in `MainWindow.axaml.cs`, while filter business logic remains in `MorphsViewModel` and `NpcFilterState`.

**Rationale:** Avalonia `ListBox.SelectedItems` multi-select is control-owned state and was not naturally expressible as a simple compiled-bound command parameter.
**Source:** 02-06-SUMMARY.md

---

### Represent Bulk Scope As A Typed Enum
Bulk operation scope remains typed as `NpcBulkScope`, with a display converter used only for UI labels.

**Rationale:** Command logic stays compile-time checked while the UI can present the exact `All`, `Visible`, `Selected`, and `Visible Empty` labels.
**Source:** 02-07-SUMMARY.md

---

### Use Existing App Dialog Boundary For Destructive Bulk Confirmation
Destructive all-scope confirmation was added to `IAppDialogService` instead of creating Morphs-specific modal infrastructure.

**Rationale:** Modal UI belongs at the App service boundary, while `MorphsViewModel` should consume an abstraction.
**Source:** 02-07-SUMMARY.md

---

### Keep Undo Snapshots In The App Workflow Layer
Preset, target, NPC row, and assignment snapshots live in `BS2BG.App.ViewModels.Workflow`, not Core.

**Rationale:** Snapshot helpers serve App undo replay and should not affect Core model semantics, output formatting, or project serialization.
**Source:** 02-08-SUMMARY.md

---

## Lessons

### Machine-Local Preferences Can Pollute ViewModel Tests
Tests that constructed `TemplatesViewModel` without an explicit preferences service accidentally hydrated developer-machine AppData preferences.

**Context:** The test helper was updated to use deterministic default preferences unless a test explicitly supplies a preferences service.
**Source:** 02-01-SUMMARY.md

---

### Existing Enum JSON Shape Matters For Compatibility
Initial preference tests assumed string enum JSON, but the existing serializer wrote numeric enum values.

**Context:** Tests were corrected during GREEN work to preserve current theme preference serialization compatibility.
**Source:** 02-01-SUMMARY.md

---

### Adding Preference Fields Requires Preserving All Existing Fields
Theme and omit-redundant save paths initially rebuilt `UserPreferences` without newer folder fields, which would have wiped remembered folders.

**Context:** Plans 02 and 03 both had to update unrelated preference saves to copy every workflow preference field forward.
**Source:** 02-02-SUMMARY.md

---

### Avalonia Storage Interfaces Needed A Backend Seam
Direct fake implementations of Avalonia storage interfaces were not viable because the interfaces include non-user-implementable members.

**Context:** `WindowFileDialogService` and import pickers introduced small backend seams so start-folder behavior could be tested deterministically while production still uses `StorageProvider.SuggestedStartLocation`.
**Source:** 02-02-SUMMARY.md

---

### Concurrent Build And Test Runs Can Lock Outputs
Running `dotnet test` and `dotnet build` concurrently caused output assembly file locks during verification.

**Context:** Sequential reruns passed; this issue appeared in filtering contract and UI verification work.
**Source:** 02-04-SUMMARY.md

---

### ReactiveUI Scheduler APIs Differed From Older Assumptions
ReactiveUI 23 in this project did not expose the older `RxApp` static API expected by the plan.

**Context:** `MorphsViewModel` used an injectable `IScheduler` debounce seam for deterministic tests and compatibility with the current ReactiveUI setup.
**Source:** 02-05-SUMMARY.md

---

### Headless UI Tests Need Valid State Construction
An initial filtered-empty badge test selected a non-existent checklist value, so the test setup could not reach the intended UI state.

**Context:** The test was corrected to apply two valid conflicting filters through the ViewModel.
**Source:** 02-06-SUMMARY.md

---

### Existing Dialog Scope Was Too Narrow
The app dialog service only supported discard-changes confirmation before scoped bulk operations required destructive all-scope confirmation.

**Context:** `ConfirmBulkOperationAsync` was added to the existing dialog boundary and fakes were updated.
**Source:** 02-07-SUMMARY.md

---

### Live References Corrupt Undo Replay
RED tests showed detached custom targets, NPCs, and renamed live preset references could affect undo replay after removal or bulk operations.

**Context:** Morph/NPC undo paths were changed to recreate rows and assignments from captured scalar values, stable row IDs, and current preset names.
**Source:** 02-09-SUMMARY.md

## Patterns

### Best-Effort Local Preference DTO
Store restart convenience state in `UserPreferences`, preserve unrelated fields on every save, and keep project serialization unchanged.

**When to use:** Use for user-machine workflow preferences that should survive restart but should not be shared through `.jbs2bg` files.
**Source:** 02-01-SUMMARY.md

---

### Picker Backend Seam Over Avalonia StorageProvider
Delegate picker calls through small backend abstractions while production maps remembered paths to `SuggestedStartLocation`.

**When to use:** Use when Avalonia picker behavior must be unit-tested without implementing framework storage interfaces.
**Source:** 02-02-SUMMARY.md

---

### App-Layer Row Sidecars
Map mutable Core models to stable App-layer wrappers with generated row IDs, leaving Core models and serialization untouched.

**When to use:** Use when UI filtering, selection, or undo needs identity that should not become part of persisted domain data.
**Source:** 02-04-SUMMARY.md

---

### Pending And Applied Search Split
Separate pending typed search text from applied search text so free-text filtering can debounce while checklist predicates apply immediately.

**When to use:** Use for large-list search UIs where typing should not rebuild visible rows on every keystroke but non-text filters need immediate feedback.
**Source:** 02-04-SUMMARY.md

---

### SourceCache-Backed Visible Projections
Use `SourceCache<NpcRowViewModel, Guid>` sidecars internally, then project filtered results back to existing public `ObservableCollection<Npc>` surfaces.

**When to use:** Use when introducing stable keyed filtering into an existing ViewModel without breaking current bindings and command contracts.
**Source:** 02-05-SUMMARY.md

---

### Minimal Code-Behind For View-Owned Selection State
Keep Avalonia multi-select selection glue in code-behind and forward selected values to ViewModel methods, with business filtering logic elsewhere.

**When to use:** Use when the control owns state that compiled bindings cannot cleanly pass as a command parameter.
**Source:** 02-06-SUMMARY.md

---

### Materialize Scoped Target IDs Before Mutation
Resolve all/visible/selected/visible-empty scope into a stable row ID snapshot before changing assignments or row collections.

**When to use:** Use for bulk operations where filters, selection, or row contents may change during mutation or undo replay.
**Source:** 02-07-SUMMARY.md

---

### Bounded Undo With Separate Prune Notification
Limit undo history by operation count and emit `HistoryPruned` separately from normal undo state changes.

**When to use:** Use when long sessions need bounded memory and the shell needs non-blocking pruning feedback without changing command availability semantics.
**Source:** 02-08-SUMMARY.md

---

### Value Snapshot Undo Replay
Capture scalar values, collection indexes, row IDs, and preset names at operation time; recreate fresh model instances during replay instead of reusing detached live references.

**When to use:** Use for undo/redo of removed or bulk-mutated objects that may be mutated again after the operation is recorded.
**Source:** 02-09-SUMMARY.md

## Surprises

### Preference Tests Read Real AppData
Existing test construction unexpectedly read the developer machine's preference file.

**Impact:** Deterministic tests failed until the ViewModel test helper always supplied an explicit default preferences service.
**Source:** 02-01-SUMMARY.md

---

### Numeric Theme Enum Serialization Was Existing Behavior
The preference serializer wrote numeric enum values rather than string enum names.

**Impact:** Tests had to align with existing serialized compatibility instead of changing preference file shape.
**Source:** 02-01-SUMMARY.md

---

### Saving One Preference Could Erase Others
Adding folder fields revealed that unrelated preference save paths could drop newly added channels if they rebuilt the DTO partially.

**Impact:** Theme and omit-redundant save paths had to be hardened to copy all preference fields forward.
**Source:** 02-02-SUMMARY.md

---

### Avalonia Picker Interfaces Were Hard To Fake Directly
The storage interfaces were not practical to implement in tests.

**Impact:** A backend seam was required to keep picker behavior testable without changing production use of `StorageProvider`.
**Source:** 02-02-SUMMARY.md

---

### Parallel Verification Commands Locked Test Assemblies
Concurrent build/test execution caused `CS2012` and App project output file locks.

**Impact:** Verification needed sequential command execution; no code changes were required.
**Source:** 02-06-SUMMARY.md

---

### The Planned ReactiveUI Debounce API Was Not Available
The implementation could not rely on older static `RxApp` access.

**Impact:** A scheduler injection seam became necessary for reliable debounced-search tests.
**Source:** 02-05-SUMMARY.md

---

### Automated Coverage Still Left Three Human UAT Areas
Verification passed all automated truths, but filter popup interaction, large real-world responsiveness, and packaged restart persistence remained human-needed.

**Impact:** Phase status stayed `human_needed` with three pending UAT checks despite full automated coverage passing.
**Source:** 02-VERIFICATION.md
