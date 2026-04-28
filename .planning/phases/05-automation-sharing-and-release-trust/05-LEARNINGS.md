---
phase: 05
phase_name: "automation-sharing-and-release-trust"
project: "BS2BG (Bodyslide to Bodygen)"
generated: "2026-04-28"
counts:
  decisions: 15
  lessons: 10
  patterns: 14
  surprises: 8
missing_artifacts:
  - "05-UAT.md"
---

# Phase 05 Learnings: automation-sharing-and-release-trust

## Decisions

### Dedicated CLI Project Stays Core-Only
Use a dedicated `BS2BG.Cli` executable that references Core only, rather than adding a headless flag to the Avalonia app.

**Rationale:** Automation should not initialize Avalonia/App services, and Core-only composition preserves the same generation/export behavior without desktop startup coupling.
**Source:** 05-01-SUMMARY.md

---

### CLI Inputs Become Typed Core Contracts
Output intent, request data, result state, and exit codes are represented as Core enums/records instead of parser strings.

**Rationale:** Later generation and bundle services can consume typed requests consistently, avoiding stringly typed behavior across CLI parsing, Core services, and tests.
**Source:** 05-01-SUMMARY.md

---

### Headless Generation Reuses Existing Core Writers
CLI generation is a thin Core service composition layer that validates, generates, and writes through existing Core services and export writers.

**Rationale:** Command parsing must not fork formatter or writer semantics; byte-sensitive BodyGen/BoS output behavior stays centralized.
**Source:** 05-02-SUMMARY.md

---

### BoS Output Planning Is Writer-Owned
BoS JSON path planning is extracted beside the writer and consumed by overwrite preflight and the writer.

**Rationale:** Preflight and actual writes must use the same sanitized/deduplicated filenames so overwrite checks cannot drift from writer behavior.
**Source:** 05-02-SUMMARY.md

---

### AssignmentStrategy Is Optional Project Data
`AssignmentStrategy` remains nullable and is omitted from project JSON when absent.

**Rationale:** Legacy `.jbs2bg` files should not churn, and strategy configuration should be an optional extension like prior custom-profile sections.
**Source:** 05-03-SUMMARY.md

---

### Race Rules Use Imported NPC Race Only
Strategy rule race matching uses only imported `Npc.Race` text with `StringComparer.OrdinalIgnoreCase`; no plugin, game-data, ESP/ESM, or xEdit lookup is introduced.

**Rationale:** Shared strategies must remain portable, deterministic, and independent of external game/plugin state.
**Source:** 05-03-SUMMARY.md; 05-04-SUMMARY.md

---

### Seed Replay Uses A Pinned PRNG
Persisted deterministic replay uses a pinned Mulberry32 implementation instead of `System.Random(seed)`.

**Rationale:** Shared projects need seed replay that stays identical across runtimes and future framework changes.
**Source:** 05-04-SUMMARY.md

---

### Strategy Eligibility Is Single-Sourced
`ComputeEligibility` is the shared non-mutating source for both strategy application and diagnostics.

**Rationale:** No-eligible behavior must not diverge between what diagnostics warn about and what Apply actually blocks.
**Source:** 05-04-SUMMARY.md

---

### Strategy Apply Targets All Morphed NPCs
GUI strategy apply operates on the full `MorphedNpcs` collection, not the current visible/filter scope.

**Rationale:** GUI, CLI, and shared bundles must replay the same deterministic assignment behavior regardless of temporary UI filtering.
**Source:** 05-05-SUMMARY.md

---

### Bundle Outcomes Are Explicit Core States
Portable bundle failure states are modeled with `PortableProjectBundleOutcome` values rather than expected exceptions.

**Rationale:** CLI and GUI callers can map success, validation, overwrite, missing-profile, and I/O states without exception parsing.
**Source:** 05-06-SUMMARY.md

---

### Bundles Zip Exact Writer Bytes
Bundle generation stages outputs through existing BodyGen and BoS writers, then zips those exact bytes.

**Rationale:** Portable bundles should not contain writer-equivalent reimplementations that can drift from normal generation output.
**Source:** 05-06-SUMMARY.md

---

### Bundle Preview Happens Before Zip Creation
GUI bundle previews use `PortableProjectBundleService.Preview` rather than creating temporary zips.

**Rationale:** Users need layout, profile scope, privacy status, and overwrite intent before any archive write happens.
**Source:** 05-07-SUMMARY.md

---

### Signing Is Optional But Checksums Are Required
Unsigned release artifacts remain valid when SHA-256 sidecars, packaged checksums, and `UNSIGNED-BUILD.md` verification succeed.

**Rationale:** Release trust should not depend on SignTool or certificate availability; checksum-backed verification is the fallback trust path.
**Source:** 05-08-SUMMARY.md

---

### Setup Guidance Ships As Packaged Docs Only
BodyGen, BodySlide, and BodyTypes of Skyrim setup/troubleshooting guidance lives in release docs, not in an app wizard or Help-menu UI.

**Rationale:** Phase 5 should provide clear packaged guidance without expanding app UI scope, telemetry, game discovery, or plugin editing behavior.
**Source:** 05-09-SUMMARY.md

---

### Bundle Custom Profile Resolution Is Single-Sourced
Portable bundle generation resolves custom profiles once per request and reuses that set for missing-profile checks, zip profile entries, validation, templates.ini, and BoS JSON.

**Rationale:** Generated bundle bytes must align with the exact custom profile JSON copied into `profiles/`, and App singleton services must not capture stale custom-profile snapshots.
**Source:** 05-10-SUMMARY.md; 05-VERIFICATION.md

---

## Lessons

### Parallel Verification Can Lock App Build Files
Running final verification commands concurrently caused transient Avalonia/App build file locks.

**Context:** The issue appeared during CLI foundation/headless generation verification and was resolved by rerunning targeted checks sequentially.
**Source:** 05-01-SUMMARY.md; 05-02-SUMMARY.md

---

### Moving A Factory Into Core Can Create Name Ambiguity
Adding `BS2BG.Core.Generation.TemplateProfileCatalogFactory` made existing App/test references ambiguous where both App and Core namespaces were imported.

**Context:** App bootstrapper registration and tests had to qualify the App factory or use aliases.
**Source:** 05-02-SUMMARY.md

---

### Strategy Persistence Needed A Project Seam Early
The first assignment-strategy contract task already needed a minimal `ProjectModel.AssignmentStrategy` property to satisfy default/null behavior.

**Context:** The storage seam was added during Task 1 and completed with dirty tracking and clone preservation in Task 2.
**Source:** 05-03-SUMMARY.md

---

### Exact-Sequence Tests Must Match Formal Ordering
Initial deterministic strategy expectations assumed name ordering instead of the planned stable order by Mod, EditorId, FormId, Name, then original index.

**Context:** Tests were corrected to assert the formal algorithm order while keeping snapshots readable by NPC name.
**Source:** 05-04-SUMMARY.md

---

### Salvageable Invalid Strategy Data Should Be Editable
Loaded invalid-but-salvageable strategy data can hydrate editable row ViewModels with per-row validation rather than forcing users to retype the entire strategy.

**Context:** The Morphs strategy UI preserved repairable rows and disabled Apply until saved/repaired.
**Source:** 05-05-SUMMARY.md

---

### Privacy Scanning Should Favor False Positives
Bundle manifest/report privacy scanning treats drive roots, UNC prefixes, backslashes, and the current user name as leaks, accepting possible literal false positives.

**Context:** The bundle service prioritizes not leaking private local path data over accepting every possible harmless literal string.
**Source:** 05-06-SUMMARY.md; 05-06-PLAN.md

---

### Visual Verification Can Be Evidence Without Code Changes
When a checkpoint explicitly forbids file edits, approval evidence can be recorded in an empty task commit or a planning evidence file.

**Context:** Strategy UI approval used an empty evidence commit; bundle preview approval used `05-07-VISUAL-VERIFICATION.md`.
**Source:** 05-05-SUMMARY.md; 05-07-SUMMARY.md; 05-07-VISUAL-VERIFICATION.md

---

### Release Trust Tests Need Fast And Heavy Modes
Release package inspection and executable smoke tests should be gated behind `ReleaseSmoke`; default release-trust verification should stay fast.

**Context:** Release tests assert source/package contracts quickly while heavyweight publish/package/extracted executable checks are skipped unless explicitly requested.
**Source:** 05-08-SUMMARY.md; 05-09-SUMMARY.md

---

### Zip Creation Needed Explicit Normalization
`Compress-Archive` was not explicit enough for path-safety assertions around separators, duplicate entries, and rooted/unsafe paths.

**Context:** Release packaging moved to `ZipArchive` with sorted files, forward-slash entry names, duplicate detection, and rooted/unsafe segment rejection.
**Source:** 05-08-SUMMARY.md

---

### Phase Verification Found Bundle Trust Gaps
Phase 5 initially had a portable bundle trust gap around overwrite safety, custom-profile output bytes, and stable CLI expected-failure handling.

**Context:** Plan 05-10 closed the gap with temp final-commit replacement, request-scoped profile catalogs, and command-boundary failure mapping before re-verification passed.
**Source:** 05-10-SUMMARY.md; 05-VERIFICATION.md

---

## Patterns

### Thin CLI Over Core Services
CLI commands parse options with `System.CommandLine`, map inputs to typed Core requests, and delegate behavior to Core services.

**When to use:** Use for automation features where GUI parity matters and the command surface should not duplicate domain logic.
**Source:** 05-01-SUMMARY.md; 05-02-SUMMARY.md

---

### Validation-Before-Write Gate
Headless generation validates the project before any writer call and blocks on validation blockers.

**When to use:** Use whenever command-line or bundle workflows can create user-visible output files from project state.
**Source:** 05-02-SUMMARY.md

---

### Overwrite Preflight For All Selected Outputs
`OutputIntent.All` preflights all selected BodyGen and BoS targets before writing when overwrite is disabled.

**When to use:** Use for multi-output operations where partial writes should not occur if an existing target would be refused.
**Source:** 05-02-SUMMARY.md

---

### In-Process Serialized CLI Tests
CLI tests call `Program.Main` in-process under serialized console capture rather than shelling out.

**When to use:** Use for fast command behavior tests that need stdout/stderr assertions without parallel `Console` races.
**Source:** 05-02-SUMMARY.md

---

### Optional Versioned Project Sections
Optional project data uses schema version `1`, nullable-safe defaults, omission when absent, and recoverable diagnostics for invalid or future data.

**When to use:** Use when adding collaborator-shared project features without breaking legacy project load/save behavior.
**Source:** 05-03-SUMMARY.md

---

### Pinned Deterministic Provider Behind Existing Interface
Deterministic assignment uses a portable PRNG implementation behind `IRandomAssignmentProvider` rather than special-casing randomness outside the seam.

**When to use:** Use when persisted seeds must replay exactly while preserving existing test/provider abstractions.
**Source:** 05-04-SUMMARY.md

---

### Shared Eligibility For Apply And Diagnostics
Strategy algorithms expose `ComputeEligibility` so diagnostics and mutation paths consume the same eligibility result.

**When to use:** Use when validation/reporting must match what a later apply/mutation command will actually do.
**Source:** 05-04-SUMMARY.md

---

### Reactive Strategy Editor Rows
Strategy rule rows are small ReactiveUI row ViewModels with text token parsing and row-level validation.

**When to use:** Use when complex persisted config needs editable, repairable rows without bloating the parent ViewModel.
**Source:** 05-05-SUMMARY.md; 05-05-PLAN.md

---

### Deterministic Bundle Manifests
Bundle manifests store normalized bundle-relative paths, source filenames only, SHA-256 checksums, and request-pinned timestamps.

**When to use:** Use for shareable support/release artifacts where archive contents need reproducibility and privacy guarantees.
**Source:** 05-06-SUMMARY.md

---

### Zip From Staged Writer Outputs
Portable bundle creation writes outputs to an isolated temp directory through existing writers, then zips the exact output files.

**When to use:** Use when packaging generated artifacts that already have byte-sensitive writer implementations.
**Source:** 05-06-SUMMARY.md

---

### Preview-First Bundle Workflow
GUI bundle creation uses a preview command to show layout, referenced custom profiles, privacy status, and overwrite state before writing.

**When to use:** Use for workflows that package or share data and need user trust before filesystem mutation.
**Source:** 05-07-SUMMARY.md; 05-07-VISUAL-VERIFICATION.md

---

### Optional Signing With Checksum Fallback
Release packaging treats signing as opt-in and always produces SHA-256 verification artifacts.

**When to use:** Use for release pipelines where a signing certificate may not be available but users still need artifact integrity checks.
**Source:** 05-08-SUMMARY.md

---

### Release Docs Protected By Tests
Release docs tests assert source docs, packaging script inclusion, date-token format, and absence of setup wizard/help-menu patterns.

**When to use:** Use for packaged-only guidance that must ship in release zips but must not create app UI scope.
**Source:** 05-09-SUMMARY.md

---

### Atomic Final Commit For Bundle Replacement
Bundle overwrite writes to a same-directory temp zip and commits with `File.Replace` or `File.Move` only after the archive closes successfully.

**When to use:** Use for replacing user-visible archives where a failed final commit must preserve the previous file.
**Source:** 05-10-SUMMARY.md; 05-VERIFICATION.md

---

## Surprises

### App Build Locks Reappeared During CLI Work
Even Core/CLI-focused verification could trigger transient Avalonia/App file locks when tests and builds ran in parallel.

**Impact:** Sequential verification reruns were needed; no code changes were required.
**Source:** 05-01-SUMMARY.md; 05-02-SUMMARY.md

---

### Core Factory Move Broke Existing Namespaces
Introducing a Core `TemplateProfileCatalogFactory` unexpectedly conflicted with App factory references.

**Impact:** App and test references had to be explicitly qualified, but runtime behavior stayed unchanged.
**Source:** 05-02-SUMMARY.md

---

### Strategy Property Was Needed Before Serialization
The strategy contract tests exposed that default/null strategy compatibility already depended on project-level storage.

**Impact:** `ProjectModel.AssignmentStrategy` was added earlier than the full serialization task originally implied.
**Source:** 05-03-SUMMARY.md

---

### Human Checkpoint Could Be An Empty Commit
The visual checkpoint for the Morphs strategy UI prohibited file edits, so evidence was captured as an empty task commit.

**Impact:** The project retained auditable checkpoint evidence without violating the checkpoint's no-edit rule.
**Source:** 05-05-SUMMARY.md

---

### Zip Path Safety Required Replacing Compress-Archive
Release path-safety assertions required more control over entry names than the original `Compress-Archive` flow provided.

**Impact:** The release script switched to explicit `ZipArchive` creation with normalized entries and duplicate/rooted path rejection.
**Source:** 05-08-SUMMARY.md

---

### Release Trust RED Tests Initially Failed For Test-Code Reasons
The first RED draft used FluentAssertions chaining that inferred `object` for `Process.Start` rather than failing on intended release trust behavior.

**Impact:** The test was corrected so the RED gate represented missing product behavior, not a compile error.
**Source:** 05-08-SUMMARY.md

---

### Bundle Verification Found Generated-Output Profile Drift
Phase verification found that bundles needed generated outputs to use the same referenced custom profile definitions copied into the archive.

**Impact:** Plan 05-10 introduced single-source profile resolution and request-scoped catalogs for validation and output generation.
**Source:** 05-10-SUMMARY.md; 05-VERIFICATION.md

---

### Final Bundle Replacement Needed A Commit Seam
Testing overwrite-failure preservation deterministically required an injectable final-commit seam instead of relying on nondeterministic filesystem races.

**Impact:** Core bundle tests can force final commit failure while proving the previous bundle remains intact and temp files are cleaned up.
**Source:** 05-10-SUMMARY.md; 05-10-PLAN.md
