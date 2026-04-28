---
phase: 04
phase_name: "profile-extensibility-and-controlled-customization"
project: "BS2BG (Bodyslide to Bodygen)"
generated: "2026-04-28"
counts:
  decisions: 14
  lessons: 11
  patterns: 13
  surprises: 9
missing_artifacts:
  - "04-UAT.md"
---

# Phase 04 Learnings: profile-extensibility-and-controlled-customization

## Decisions

### Internal Profile Name Is Identity
Custom profile identity uses only the internal `Name` field. File paths and filenames remain source metadata and are ignored for display identity and recovery matching.

**Rationale:** This prevents filename-based spoofing and keeps imported/missing-profile resolution deterministic across local files, embedded project profiles, and recovery flows.
**Source:** 04-01-SUMMARY.md; 04-04-SUMMARY.md

---

### Normalize Conflict Equality Explicitly
Definition equality compares display names case-insensitively, ignores table order, preserves exact game metadata and slider-name casing, and uses exact finite float equality.

**Rationale:** Project-open embedded/local conflict handling needed a stable definition of "same profile" before deciding whether to prompt or silently accept equivalent data.
**Source:** 04-01-SUMMARY.md

---

### Deterministic Profile JSON Output
Standalone custom profile JSON is exported with stable key ordering and LF-only line endings, even when produced on Windows.

**Rationale:** Shareable profile files need byte-stable output and round-trip predictability, matching the project's existing emphasis on deterministic generated files.
**Source:** 04-01-SUMMARY.md

---

### Malformed User Profile Files Are Non-Fatal
Custom profile discovery failures and invalid files are represented as diagnostics while bundled app startup remains available.

**Rationale:** User-local editable data crosses an untrusted file boundary and must not prevent the tool from loading bundled profile behavior.
**Source:** 04-02-SUMMARY.md

---

### Runtime Catalog Has One Mutable Owner
The App-layer catalog service is the only mutable runtime catalog owner; refresh, project overlay, and clear operations are serialized and published through catalog-change notifications.

**Rationale:** Profile-aware ViewModels need current catalog state without stale singleton snapshots or competing mutation paths.
**Source:** 04-02-SUMMARY.md

---

### Embedded Profiles Are Project-Owned Mutable Data
Embedded project profiles are mutable project nodes rather than immutable records, and edits participate in dirty tracking.

**Rationale:** Project-local profile metadata and table replacement affect project sharing and need the same dirty-state semantics as presets and morph targets.
**Source:** 04-03-SUMMARY.md

---

### CustomProfiles Is Optional And Referenced-Only
Project serialization appends `CustomProfiles` only when referenced non-bundled custom profile definitions resolve from project or save context.

**Rationale:** This preserves legacy project shape when no custom profiles are involved and avoids disclosing unrelated local custom profile definitions.
**Source:** 04-03-SUMMARY.md; 04-11-SUMMARY.md

---

### Missing Profiles Surface As One Recovery Finding
Missing custom profiles are represented as one recovery-coded informational finding instead of separate generic unbundled-profile and fallback rows.

**Rationale:** Diagnostics should be actionable and non-duplicative, with stable code/category metadata for mechanical deduplication.
**Source:** 04-04-SUMMARY.md

---

### Profile Editor Validates In-Memory State
Profile editor validation is performed from in-memory row state, not JSON serialization round-trips during normal edit cadence.

**Rationale:** Editing large profile tables should stay responsive while JSON validation remains the boundary for import/export and persisted data.
**Source:** 04-05-SUMMARY.md

---

### Deleting Referenced Custom Profiles Keeps References
Deleting a referenced local custom profile preserves preset `ProfileName` values so recovery diagnostics reappear rather than silently remapping.

**Rationale:** Users must see unresolved fallback state explicitly; deleting profile data should not hide or mutate project intent.
**Source:** 04-05-SUMMARY.md

---

### Project-Open Conflicts Are Transactional
Project-open conflict prompts collect all decisions before any local profile save, project replacement, or project overlay mutation.

**Rationale:** Shared project profiles can affect local data and active generation state, so cancellation or write failure must leave the old project and overlay intact.
**Source:** 04-06-SUMMARY.md

---

### Diagnostics Delegate Recovery Mutations
Diagnostics exposes recovery action rows but delegates imports, project overlays, and remaps through an App-layer handler.

**Rationale:** Diagnostics remains read-only until an explicit command executes, preserving the established diagnostics boundary.
**Source:** 04-07-SUMMARY.md

---

### Profiles Navigation Is Shell-Owned
Profiles workspace navigation is owned by `INavigationService` so Templates can request navigation without depending on `MainWindowViewModel`.

**Rationale:** This avoids ViewModel cycles while making profile management discoverable from the template workflow.
**Source:** 04-08-SUMMARY.md

---

### Filtering Is A Visible Projection Only
Profile editor filtering remains a visible projection; validation and save continue reading complete source row collections.

**Rationale:** Search must improve editing usability without causing filtered-out slider rows to disappear from saved profile definitions.
**Source:** 04-13-SUMMARY.md; 04-VERIFICATION.md

---

## Lessons

### Record Contracts Need Netstandard Compatibility
Public record contracts in `BS2BG.Core` required an `IsExternalInit` shim because the project targets `netstandard2.1`.

**Context:** The first Core custom profile contract work could not compile until the compatibility shim was added.
**Source:** 04-01-SUMMARY.md

---

### Utf8JsonWriter Newlines Are Platform-Sensitive
`Utf8JsonWriter` emitted CRLF-indented JSON on Windows, so export output had to normalize to LF.

**Context:** The phase required byte-stable custom profile JSON sharing, and Windows defaults would otherwise create platform-dependent output.
**Source:** 04-01-SUMMARY.md

---

### FluentAssertions API Details Can Break RED Setup
A focused test compilation exposed a FluentAssertions collection API mismatch in new tests.

**Context:** The mismatch was corrected before committing the user profile store RED tests, showing that test code API drift can interrupt TDD gates.
**Source:** 04-02-SUMMARY.md

---

### Strongly Typed DTOs Can Preempt Diagnostics
Strongly typed embedded profile numeric DTO properties caused `System.Text.Json` to throw before embedded-profile diagnostics could be emitted.

**Context:** Raw `JsonElement` payload validation was needed so malformed embedded entries could produce diagnostics while legacy project data still loaded.
**Source:** 04-03-SUMMARY.md

---

### Parallel Test And Build Runs Cause File Locks
Several focused test/build attempts hit transient PDB or DLL file locks when commands ran concurrently.

**Context:** Re-running build or tests after the other process exited passed cleanly; this appeared in Plans 03, 04, 05, 06, and 09.
**Source:** 04-03-SUMMARY.md; 04-04-SUMMARY.md; 04-05-SUMMARY.md; 04-06-SUMMARY.md; 04-09-SUMMARY.md

---

### Window-Bound Services Need Attachment After DI Registration
Registering `ProfileManagementDialogService` was not enough; the service had to be attached to `MainWindow` for runtime picker and confirmation ownership.

**Context:** The Plan 05 DI task found that unattached window-bound services would otherwise return default/no-owner behavior.
**Source:** 04-05-SUMMARY.md

---

### DI Constructor Selection Can Be Surprising
Microsoft DI selected an unintended `TemplateProfileCatalogFactory` constructor with an empty enumerable, which failed bundled profile discovery in App DI tests.

**Context:** Explicit factory registration was needed to force the intended constructor.
**Source:** 04-07-SUMMARY.md

---

### Test Host Paths Need Bundled Profile Search Fallbacks
Test-host App DI can run from output folders that do not contain bundled profile JSON files beside the test assembly.

**Context:** Parent-directory search candidates were added while keeping production base-directory preference first.
**Source:** 04-07-SUMMARY.md

---

### Headless UI Tests Require Template Materialization
The headless profile-editor test had to show the window, select the Profiles tab, add sample rows, and inspect visual descendants before automation names existed.

**Context:** Avalonia `ContentTemplate` and row templates are materialized lazily, so tests must drive the UI far enough to create controls.
**Source:** 04-10-SUMMARY.md

---

### Failed Imports Should Not Refresh Catalog State
Initial import failure handling refreshed the catalog after a failed import path and replaced clean editor state.

**Context:** Refresh was changed to run only after at least one successful import save so expected read failures preserve selection and editor state.
**Source:** 04-12-SUMMARY.md

---

### Verification Gaps Needed Gap-Closure Plans
The phase originally had verification gaps around created/copied profile saves, unsaved selection rollback, dirty refresh preservation, I/O failure status, and all-table filtering.

**Context:** Gap-closure Plans 04-12 and 04-13 moved the phase from `needs_gap_closure` to `passed` with 5/5 must-haves verified.
**Source:** 04-VERIFICATION.md

---

## Patterns

### Result-First User Data Boundaries
Return validation diagnostics or status results for malformed user profile JSON, discovery failures, and recoverable file I/O instead of throwing for expected user-data problems.

**When to use:** Use for local files, imported profile JSON, embedded project profile payloads, and profile import/export read/write failures.
**Source:** 04-01-SUMMARY.md; 04-02-SUMMARY.md; 04-12-SUMMARY.md

---

### Source-Tagged Catalog Entries
Catalog rows carry `ProfileSourceKind`, path metadata, and editability while preserving existing lookup/fallback APIs.

**When to use:** Use whenever UI, diagnostics, save logic, or export gating needs to distinguish bundled, local custom, embedded project, and missing profile states.
**Source:** 04-02-SUMMARY.md

---

### Atomic AppData Writes With Deterministic Filenames
Local custom profiles are stored under AppData and persisted with atomic UTF-8 writes, sanitized internal-name filenames, and stable SHA-256 collision suffixes.

**When to use:** Use for user-editable profile saves where partial writes and filename collisions must not corrupt existing profile data.
**Source:** 04-02-SUMMARY.md

---

### Optional Legacy-Compatible Root Sections
Append optional project JSON sections after legacy fields and omit them when empty.

**When to use:** Use when extending `.jbs2bg` project serialization without breaking older readers or changing byte output for projects that do not use the new feature.
**Source:** 04-03-SUMMARY.md; 04-03-PLAN.md

---

### Raw JsonElement Validation For Recoverable Embedded Data
Store untrusted embedded profile payloads as raw JSON elements and validate each entry through the strict profile validator.

**When to use:** Use when malformed nested JSON should produce item-level diagnostics without aborting the parent document load.
**Source:** 04-03-SUMMARY.md

---

### Stable Diagnostic Codes And Categories
Diagnostic deduplication uses stable `Code` and `Category` metadata rather than title/detail text matching.

**When to use:** Use when multiple diagnostic services may describe the same condition and UI needs exactly one row per actionable issue.
**Source:** 04-04-SUMMARY.md

---

### Single-Editor Profile Manager Session
The profile manager owns one editor instance per single-shell workspace session and prompts before discarding unsaved buffers.

**When to use:** Use for workspace-scoped editors where row selection, search, and catalog refresh can otherwise replace user edits silently.
**Source:** 04-05-SUMMARY.md; 04-12-SUMMARY.md

---

### Snapshot-Then-Mutate Transactions
Project-open conflict handling snapshots local/project state, collects all user decisions, stages local writes, and mutates project/overlay only after validation succeeds.

**When to use:** Use when shared project data can replace local data or active lookup overlays and rollback must preserve the previous project on cancel or write failure.
**Source:** 04-06-SUMMARY.md

---

### App Dialog Boundaries For User Decisions
`IAppDialogService` and `IProfileManagementDialogService` isolate prompts, pickers, and confirmations from ViewModels and tests.

**When to use:** Use for file-picker, delete, discard, and profile conflict decisions so tests can fake decisions without Avalonia UI coupling.
**Source:** 04-05-SUMMARY.md; 04-06-SUMMARY.md

---

### Recovery Actions Through A Handler Boundary
Diagnostics recovery actions route through `IProfileRecoveryActionHandler` instead of mutating storage or projects directly.

**When to use:** Use for diagnostics-driven remediation that needs explicit user actions while keeping diagnostics itself read-only.
**Source:** 04-07-SUMMARY.md

---

### Undo-Recorded Recovery Remaps
Profile recovery remaps use undo/redo snapshots so undo restores unresolved profile names and fallback information.

**When to use:** Use when a recovery action mutates project preset profile references after project open.
**Source:** 04-07-SUMMARY.md

---

### Text-Visible Trust State
Profiles UI uses explicit text badges and neutral missing-profile copy rather than color-only state.

**When to use:** Use for trust/source/editability states where users need to distinguish bundled, custom, embedded, and missing references accessibly.
**Source:** 04-08-SUMMARY.md

---

### Visible Collections For UI Filtering
Each editable profile slider table has a source collection and a `Visible*Rows` projection bound by the Profiles workspace.

**When to use:** Use when filtering should affect only UI visibility while validation and serialization continue to consume full source data.
**Source:** 04-13-SUMMARY.md

---

## Surprises

### Public Records Needed A Shim
Using records in Core unexpectedly required an `IsExternalInit` shim under `netstandard2.1`.

**Impact:** Added a small compatibility file before Core custom profile contracts could compile.
**Source:** 04-01-SUMMARY.md

---

### JsonWriter Produced Windows Line Endings
Indented `Utf8JsonWriter` output was not LF-stable on Windows by default.

**Impact:** Custom profile export had to normalize CRLF to LF to meet deterministic sharing requirements.
**Source:** 04-01-SUMMARY.md

---

### Profile Entry Contract Did Not Include Game
The profile catalog entry contract requested bundled `Game` metadata, but the specified `ProfileCatalogEntry` contract did not include a `Game` field.

**Impact:** Implementation preserved the planned contract initially, then later Plan 09 closed metadata preservation gaps through row enrichment and editor/export paths.
**Source:** 04-02-SUMMARY.md; 04-09-SUMMARY.md

---

### Typed Embedded DTOs Blocked Diagnostic Recovery
Malformed numeric fields in embedded profile DTOs caused deserialization exceptions before diagnostics could be produced.

**Impact:** Embedded profile payloads moved to raw JSON element validation to keep legacy project load non-blocking.
**Source:** 04-03-SUMMARY.md

---

### Concurrent Verification Caused File Locks
Running focused tests and solution builds at the same time repeatedly caused transient `BS2BG.App` PDB/DLL locks.

**Impact:** Verification needed sequential reruns; the underlying code and tests passed after lock contention cleared.
**Source:** 04-03-SUMMARY.md; 04-04-SUMMARY.md; 04-05-SUMMARY.md; 04-06-SUMMARY.md; 04-09-SUMMARY.md

---

### Missing Rows Used The Wrong Source Label
Missing project profile rows initially reused `EmbeddedProject`, displaying `Embedded in project` instead of `Missing — using fallback`.

**Impact:** Added an explicit missing-row flag and tests to preserve neutral fallback copy.
**Source:** 04-05-SUMMARY.md

---

### DI Picked The Wrong Constructor
DI selected the factory constructor with an empty directory enumerable and could not find bundled `settings.json`.

**Impact:** App bootstrap now uses an explicit factory for deterministic catalog factory construction.
**Source:** 04-07-SUMMARY.md

---

### Initial Profiles UI Was Not Enough For Row Actions
The Profiles workspace rendered rows, but verification found row-targeting, copy-as-custom, and `Game` metadata preservation gaps.

**Impact:** Plan 04-09 converted source groups to selectable row targets and preserved metadata through copy/export paths.
**Source:** 04-09-SUMMARY.md; 04-VERIFICATION.md

---

### Final Verification Found Save And Refresh Blockers
Phase verification initially found blockers in manager save eligibility, declined selection rollback, dirty editor refresh, and expected I/O failure handling.

**Impact:** Plan 04-12 added active-editor save gating, committed selection tracking, dirty editor preservation, and recoverable import/export status messages before the phase passed.
**Source:** 04-12-SUMMARY.md; 04-VERIFICATION.md
