---
phase: 03
phase_name: "validation-and-diagnostics"
project: "BS2BG (Bodyslide to Bodygen)"
generated: "2026-04-28T00:29:22-07:00"
counts:
  decisions: 10
  lessons: 9
  patterns: 9
  surprises: 7
missing_artifacts:
  - "03-UAT.md"
---

# Phase 03 Learnings: validation-and-diagnostics

## Decisions

### Keep Diagnostics Read-Only And Core-First
Project and profile diagnostics were implemented as Core read-only services returning immutable DTOs before App presentation was added.

**Rationale:** Diagnostics must be reusable, UI-free, and safe to run without mutating `ProjectModel` dirty/version state.
**Source:** 03-01-SUMMARY.md

---

### Expose Profile Tables Instead Of Re-Parsing JSON
`SliderProfile` exposes multiplier and inversion table entries for diagnostics rather than duplicating profile JSON parsing or formatter logic.

**Rationale:** Profile diagnostics need to inspect the same loaded tables used by generation while preserving output formatting behavior.
**Source:** 03-01-SUMMARY.md

---

### Separate Parser Duplicates From Existing-State Duplicates
Within-file NPC duplicates are parser diagnostics, while duplicates against the existing database/project are classified by `NpcImportPreviewService`.

**Rationale:** Direct import keeps its first-row-kept skip policy, and preview can explain both skipped source rows and rows that would duplicate existing state.
**Source:** 03-02-SUMMARY.md

---

### Keep Export Preview Read-Only
Export preview duplicates writer filename/path rules and uses generation services for snippets without calling writer `Write` APIs.

**Rationale:** Users need exact target effects before disk writes, but preview must not create directories, write files, or change byte-sensitive writer output semantics.
**Source:** 03-03-SUMMARY.md

---

### Limit Atomic Ledgers To Commit And Rollback Failures
Setup and temp-write failures keep existing raw exception behavior, while commit/rollback failures throw `AtomicWriteException` with outcome ledgers.

**Rationale:** Existing tests and callers expected raw pre-commit exceptions before any target was touched, but commit/rollback phases need actionable target-state reporting.
**Source:** 03-03-SUMMARY.md

---

### Format Diagnostics Reports In The App Layer
`DiagnosticsReportFormatter` formats grouped plain text from display ViewModels and is registered as an injectable App service.

**Rationale:** Clipboard/report copy is presentation behavior, and keeping it injectable supports future report variants without changing Core diagnostics.
**Source:** 03-04-SUMMARY.md

---

### Preserve Direct NPC Import Beside Preview
NPC import preview is optional and no-mutation; direct import remains available through the existing command path.

**Rationale:** Preview adds user visibility before commit without replacing or changing established direct import workflow behavior.
**Source:** 03-05-SUMMARY.md

---

### Gate Risky Exports Without Create-New Friction
Shell export commands compute preview before writing and require confirmation for risky export cases while routine create-new exports remain frictionless.

**Rationale:** Users should confirm overwrites or batch-risk writes, but new-output exports should not gain unnecessary modal friction.
**Source:** 03-06-SUMMARY.md

---

### Route Project Saves Through Ledger-Producing Atomic Batch Writes
Project saves use `AtomicFileWriter.WriteAtomicBatch` so save commit failures expose the same ledger shape as export failures without adding save preview.

**Rationale:** Save failures need actionable outcome reporting, but normal project save UX and successful `.jbs2bg` serialization output should remain unchanged.
**Source:** 03-07-SUMMARY.md

---

### Make Diagnostics A First-Class Workspace
Diagnostics was added as a top-level workspace beside Templates and Morphs, not as a modal or text-only report.

**Rationale:** Phase 3's validation goal is user-visible only when diagnostics, previews, and ledgers are accessible in the main app shell.
**Source:** 03-08-SUMMARY.md

---

## Lessons

### Planned `dotnet test -x` Was Unsupported
The planned focused test commands using `-x` were rejected by the installed .NET/MSBuild toolchain.

**Context:** Multiple Phase 3 plans ran the same filters without `-x` and preserved verification coverage.
**Source:** 03-01-SUMMARY.md

---

### Concurrent Focused Tests Can Lock App Outputs
Parallel focused test runs contended for `BS2BG.App.dll` and caused transient output locks.

**Context:** Sequential reruns passed; no code changes were required.
**Source:** 03-02-SUMMARY.md

---

### Diagnostics Needed Profile Table Enumeration
Lookup methods alone were insufficient to count multiplier and inversion table entries for profile diagnostics.

**Context:** Read-only `Multipliers` and `InvertedNames` accessors were added to `SliderProfile` to avoid duplicating profile-loading logic.
**Source:** 03-01-SUMMARY.md

---

### Pre-Commit Exception Compatibility Matters
Wrapping setup/temp-write failures in `AtomicWriteException` broke existing tests expecting the original `DirectoryNotFoundException`.

**Context:** The ledger exception was limited to commit/rollback phases where target-state outcomes are meaningful.
**Source:** 03-03-SUMMARY.md

---

### Preview Tests Need Clean Mutation Baselines
The first NPC preview no-mutation test needed to mark its seeded project clean before asserting preview preserved dirty state.

**Context:** The implementation preserved the clean baseline; the test setup needed to reflect the intended invariant.
**Source:** 03-05-SUMMARY.md

---

### Expanding Dialog Interfaces Requires Updating All Fakes
Adding export overwrite confirmation to `IAppDialogService` required null/test implementations in other ViewModels and tests to compile.

**Context:** Default true-returning fake implementations were added where export confirmation was not the subject of the test.
**Source:** 03-06-SUMMARY.md

---

### Failure Presentation Must Clear Stale State
A successful operation after a prior file failure could otherwise leave old ledger rows visible.

**Context:** `ClearFileOperationLedger()` was added before save/export attempts that proceed to filesystem writes.
**Source:** 03-07-SUMMARY.md

---

### Requirement Wording May Need Explicit Overrides
DIAG-02 mentioned likely profile mismatch heuristics, but Phase 3 context D-06 narrowed diagnostics to concrete facts and excluded mismatch scoring.

**Context:** Verification recorded an applied override and accepted concrete profile coverage/default/multiplier/inversion/fallback diagnostics.
**Source:** 03-VERIFICATION.md

---

### Human Visual Verification Can Be Captured In Plan Summary
The blocking Diagnostics tab visual checkpoint was completed with user response `approved` inside the plan summary rather than a standalone UAT artifact.

**Context:** Verification found no outstanding human checks, but no separate `03-UAT.md` or `03-HUMAN-UAT.md` file exists.
**Source:** 03-08-SUMMARY.md

## Patterns

### Immutable Core Diagnostics Reports
Use immutable finding/report DTOs with severity counts and stable workflow-area labels.

**When to use:** Use for read-only validation that must be consumed by App UI, copy/report formatting, and later feature phases without mutating domain state.
**Source:** 03-01-SUMMARY.md

---

### Neutral Profile Fallback Diagnostics
Report unbundled saved profiles as informational fallback facts and explicitly avoid mismatch, scoring, heuristic, or experimental language.

**When to use:** Use when surfacing profile risk while preserving Phase 1 neutral fallback semantics.
**Source:** 03-01-SUMMARY.md

---

### Read-Only Import Preview Over Parser Results
Wrap `NpcTextParser` results in preview DTOs that distinguish parsed rows, rows to add, existing duplicates, parser diagnostics, and encoding facts.

**When to use:** Use when users need to inspect import effects before explicit commit without changing direct import behavior.
**Source:** 03-02-SUMMARY.md

---

### Read-Only Export Preview Over Generation Rules
Return exact target paths, create/overwrite state, snippets, and batch-risk flags without invoking file writers.

**When to use:** Use before risky writes where the preview should match output/path semantics but must not touch disk.
**Source:** 03-03-SUMMARY.md

---

### Exception-Carried File Outcome Ledgers
Carry immutable per-file outcomes on atomic write exceptions for commit and rollback failures.

**When to use:** Use when file operations can partially commit or rollback and users need actionable written/restored/skipped/untouched/incomplete details.
**Source:** 03-03-SUMMARY.md

---

### Reactive Diagnostics Presentation Layer
Expose refresh/copy commands, severity counts, grouped findings, selected-detail text, and navigation intent from a dedicated ViewModel.

**When to use:** Use when Core diagnostics need binding-ready App presentation without embedding UI concerns in Core.
**Source:** 03-04-SUMMARY.md

---

### Preview/Commit Split
Use separate preview and commit commands so temporary preview state cannot mutate project state until the user explicitly commits.

**When to use:** Use for NPC import preview, export preview, or any diagnostics flow that describes effects before mutation.
**Source:** 03-05-SUMMARY.md

---

### Risk-Gated Export Writes
Compute preview immediately before export, store preview rows for UI binding, and gate only risky writes through dialog confirmation.

**When to use:** Use when write workflows need preflight visibility without applying confirmation to safe create-new paths.
**Source:** 03-06-SUMMARY.md

---

### First-Class Diagnostics Workspace
Bind report, preview, export, and ledger surfaces through a top-level Diagnostics tab with compiled DataTemplates and automation names.

**When to use:** Use when diagnostics are central workflow functionality rather than ancillary modal output.
**Source:** 03-08-SUMMARY.md

## Surprises

### The `-x` Test Flag Failed Throughout Phase 3
The plan template repeatedly included `dotnet test ... -x`, but MSBuild rejected it as an unknown switch.

**Impact:** Verification commands were consistently adjusted to run the same filters without `-x`.
**Source:** 03-01-SUMMARY.md

---

### Concurrent Test Runs Caused Output Locks Again
Focused test commands started in parallel briefly locked App build outputs.

**Impact:** Verification had to be rerun sequentially; the issue did not require code changes.
**Source:** 03-03-SUMMARY.md

---

### Profile Diagnostics Needed A Core Formatting Type Change
Counting multipliers and inversions required exposing new read-only members on `SliderProfile`.

**Impact:** A Core formatting-adjacent type changed, but only to expose existing loaded tables and without altering formatter behavior.
**Source:** 03-01-SUMMARY.md

---

### Ledger Wrapping Broke Pre-Commit Failure Expectations
Early ledger wrapping of temp-write failures changed exception compatibility.

**Impact:** `AtomicWriteException` scope was narrowed to commit/rollback failures while raw setup/temp-write exceptions remained unchanged.
**Source:** 03-03-SUMMARY.md

---

### Batch-Risk Confirmation Needed Review Fixes
Verification recorded an advisory fix where export confirmation logic needed to honor batch risk, not just overwrite state.

**Impact:** `ExportPreviewService.HasRisk` and `MainWindowViewModel.RequiresExportConfirmation` were verified so multi-file or overwrite risk triggers confirmation.
**Source:** 03-VERIFICATION.md

---

### Stale Diagnostics And Preview State Needed Project Replacement Clearing
Verification recorded that diagnostics, export preview, file ledger, and NPC import preview state needed clearing on new/open project replacement.

**Impact:** `ClearProjectPresentationState` now clears these presentation states to avoid showing stale data for a new project.
**Source:** 03-VERIFICATION.md

---

### No Standalone UAT File Exists Despite Human Approval
Plan 03-08 completed a human visual checkpoint with `approved`, but there is no separate UAT artifact in the phase directory.

**Impact:** The learning extraction records `03-UAT.md` as a missing optional artifact while using the summary and verification report as the source of human-approval evidence.
**Source:** 03-08-SUMMARY.md
